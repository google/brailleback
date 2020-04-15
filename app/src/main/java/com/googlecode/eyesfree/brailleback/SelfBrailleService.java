/*
 * Copyright (C) 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.googlecode.eyesfree.brailleback;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import com.googlecode.eyesfree.braille.selfbraille.ISelfBrailleService;
import com.googlecode.eyesfree.braille.selfbraille.WriteData;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;
import com.googlecode.eyesfree.utils.LogUtils;
import com.googlecode.eyesfree.utils.NodeFilter;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Allows clients to control what should be output on the braille display for parts of the
 * accessibility node tree.
 */
public class SelfBrailleService extends Service {
  private static WeakReference<SelfBrailleService> sInstance;
  private final ServiceImpl mServiceImpl = new ServiceImpl();
  private final Map<IBinder, ClientInfo> mClients = new HashMap<IBinder, ClientInfo>();
  private final Map<AccessibilityNodeInfo, NodeState> mNodeStates =
      new HashMap<AccessibilityNodeInfo, NodeState>();
  private final SelfBrailleHandler mHandler = new SelfBrailleHandler();
  private PackageManager mPackageManager;

  /*package*/ static SelfBrailleService getActiveInstance() {
    return sInstance != null ? sInstance.get() : null;
  }

  @Override
  public void onCreate() {
    sInstance = new WeakReference<SelfBrailleService>(this);
    mPackageManager = getPackageManager();
  }

  @Override
  public void onDestroy() {
    sInstance = null;
  }

  @Override
  public IBinder onBind(Intent intent) {
    return mServiceImpl;
  }

  private class ServiceImpl extends ISelfBrailleService.Stub {
    @Override
    public void write(IBinder clientToken, WriteData writeData) {
      if (clientToken == null) {
        LogUtils.log(SelfBrailleService.this, Log.ERROR, "null client token to write");
        return;
      }
      ServiceUtil serviceUtil = new ServiceUtil(mPackageManager);
      if (!serviceUtil.verifyCaller(Binder.getCallingUid())) {
        LogUtils.log(
            SelfBrailleService.this,
            Log.ERROR,
            "non-google signed package try to invoke service, rejected.");
        return;
      }

      if (writeData == null) {
        LogUtils.log(SelfBrailleService.this, Log.ERROR, "null writeData to write");
        return;
      }
      LogUtils.log(
          SelfBrailleService.this,
          Log.VERBOSE,
          "write %s, %s",
          writeData.getText(),
          writeData.getAccessibilityNodeInfo());
      try {
        writeData.validate();
      } catch (IllegalStateException ex) {
        LogUtils.log(SelfBrailleService.this, Log.ERROR, "Invalid write data: %s", ex);
        return;
      }
      NodeState state = new NodeState();
      state.mClientToken = clientToken;
      state.mWriteData = writeData;
      mHandler.setNodeState(state);
    }

    @Override
    public void disconnect(IBinder clientToken) {
      mHandler.clientDisconnected(clientToken);
    }
  }

  public DisplayManager.Content contentForNode(AccessibilityNodeInfoCompat node) {
    if (mNodeStates.isEmpty()) {
      return null;
    }
    AccessibilityNodeInfoCompat match =
        AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(this, node, mFilterHaveNodeState);
    if (match == null) {
      return null;
    }
    AccessibilityNodeInfo unwrappedMatch = (AccessibilityNodeInfo) match.getInfo();
    WriteData writeData = mNodeStates.get(unwrappedMatch).mWriteData;
    if (writeData == null) {
      return null;
    }
    SpannableStringBuilder sb = new SpannableStringBuilder(writeData.getText());
    // NOTE: it is important to use a node returned by the accessibility
    // framework and not a node from a client of this service.
    // The rest of BrailleBack will assume that the node we are adding
    // here is sealed, supports actions etc.
    DisplaySpans.setAccessibilityNode(sb, match);
    int selectionStart = writeData.getSelectionStart();
    if (selectionStart >= 0) {
      int selectionEnd = writeData.getSelectionEnd();
      if (selectionEnd < selectionStart) {
        selectionEnd = selectionStart;
      }
      DisplaySpans.addSelection(sb, selectionStart, selectionEnd);
    }
    return new DisplayManager.Content(sb)
        .setFirstNode(match)
        .setLastNode(match)
        .setPanStrategy(DisplayManager.Content.PAN_CURSOR);
  }

  private ClientInfo infoForClient(IBinder clientToken) {
    ClientInfo info = mClients.get(clientToken);
    if (info == null) {
      info = new ClientInfo(clientToken);
      try {
        clientToken.linkToDeath(info, 0);
      } catch (RemoteException ex) {
        return null;
      }
      mClients.put(clientToken, info);
    }
    return info;
  }

  private class ClientInfo implements IBinder.DeathRecipient {
    private IBinder mClientToken;
    private final Set<AccessibilityNodeInfo> mNodes = new HashSet<AccessibilityNodeInfo>();

    public ClientInfo(IBinder clientToken) {
      mClientToken = clientToken;
    }

    @Override
    public void binderDied() {
      mHandler.clientDisconnected(mClientToken);
    }
  }

  private static class NodeState {
    public IBinder mClientToken;
    public WriteData mWriteData;
  }

  private class SelfBrailleHandler extends Handler {
    private static final int MSG_SET_NODE_STATE = 1;
    private static final int MSG_INVALIDATE_AND_RECYCLE_NODE = 2;
    private static final int MSG_CLIENT_DISCONNECTED = 3;

    public void setNodeState(NodeState newState) {
      obtainMessage(MSG_SET_NODE_STATE, newState).sendToTarget();
    }

    public void invalidateAndRecycleNode(AccessibilityNodeInfo node) {
      obtainMessage(MSG_INVALIDATE_AND_RECYCLE_NODE, node).sendToTarget();
    }

    public void clientDisconnected(IBinder clientToken) {
      obtainMessage(MSG_CLIENT_DISCONNECTED, clientToken).sendToTarget();
    }

    @Override
    public void handleMessage(Message msg) {
      switch (msg.what) {
        case MSG_SET_NODE_STATE:
          NodeState state = (NodeState) msg.obj;
          handleSetNodeState(state);
          break;
        case MSG_INVALIDATE_AND_RECYCLE_NODE:
          AccessibilityNodeInfo node = (AccessibilityNodeInfo) msg.obj;
          invalidateAndRecycleNode(node);
          break;
        case MSG_CLIENT_DISCONNECTED:
          IBinder clientToken = (IBinder) msg.obj;
          handleClientDisconnected(clientToken);
          break;
      }
    }

    private void handleSetNodeState(NodeState newState) {
      AccessibilityNodeInfo newNode = newState.mWriteData.getAccessibilityNodeInfo();
      // We must remove and insert to get the node replaced.
      NodeState oldState = mNodeStates.remove(newNode);
      if (oldState != null) {
        AccessibilityNodeInfo oldNode = oldState.mWriteData.getAccessibilityNodeInfo();
        ClientInfo oldClientInfo = infoForClient(oldState.mClientToken);
        if (oldClientInfo != null) {
          oldClientInfo.mNodes.remove(oldNode);
        }
        oldNode.recycle();
      }
      boolean recycleNewNode = true;
      if (newState.mWriteData.getText() != null) {
        ClientInfo newClientInfo = infoForClient(newState.mClientToken);
        if (newClientInfo != null) {
          newClientInfo.mNodes.add(newNode);
          mNodeStates.put(newNode, newState);
          recycleNewNode = false;
        } else {
          // The client is already dead.
        }
      } else {
        // No new text, the client relinquishes control over this
        // node.
      }
      BrailleBackService brailleBack = BrailleBackService.getActiveInstance();
      if (brailleBack != null) {
        brailleBack.invalidateNode(newNode);
      }
      if (recycleNewNode) {
        newNode.recycle();
      }
    }

    private void handleClientDisconnected(IBinder clientToken) {
      ClientInfo clientInfo = mClients.remove(clientToken);
      if (clientInfo != null) {
        LogUtils.log(
            SelfBrailleService.this,
            Log.VERBOSE,
            "Disconnected %s, removing %d nodes",
            clientToken,
            clientInfo.mNodes.size());
        for (AccessibilityNodeInfo node : clientInfo.mNodes) {
          NodeState state = mNodeStates.get(node);
          if (state.mClientToken == clientToken) {
            mNodeStates.remove(node);
            invalidateAndRecycleNode(node);
          }
        }
      }
    }
  }

  private final NodeFilter mFilterHaveNodeState =
      new NodeFilter() {
        @Override
        public boolean accept(Context context, AccessibilityNodeInfoCompat node) {
          AccessibilityNodeInfo unwrappedNode = (AccessibilityNodeInfo) node.getInfo();
          return mNodeStates.containsKey(unwrappedNode);
        }
      };
}

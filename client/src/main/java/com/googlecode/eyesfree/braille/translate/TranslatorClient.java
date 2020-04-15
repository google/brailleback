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

package com.googlecode.eyesfree.braille.translate;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import java.util.Arrays;
import java.util.List;

/**
 * Client-side interface to the central braille translator service.
 *
 * <p>This class can be used to retrieve {@link BrailleTranslator} instances for performing
 * translation between text and braille cells.
 *
 * <p>Typically, an instance of this class is created at application initialization time and
 * destroyed using the {@link destroy()} method when the application is about to be destroyed. It is
 * recommended that the instance is destroyed and recreated if braille translation is not going to
 * be need for a long period of time.
 *
 * <p>Threading:<br>
 * The object must be destroyed on the same thread it was created. Other methods may be called from
 * any thread.
 */
public class TranslatorClient {
  private static final String LOG_TAG = TranslatorClient.class.getSimpleName();
  private static final String ACTION_TRANSLATOR_SERVICE =
      "com.googlecode.eyesfree.braille.service.ACTION_TRANSLATOR_SERVICE";
  /** Delay before the first rebind attempt on bind error or service disconnect. */
  private static final int REBIND_DELAY_MILLIS = 500;

  private static final int MAX_REBIND_ATTEMPTS = 5;
  public static final int ERROR = -1;
  public static final int SUCCESS = 0;

  /**
   * A callback interface to get notified when the translation manager is ready to be used, or an
   * error occurred during initialization.
   */
  public interface OnInitListener {
    /**
     * Called exactly once when it has been determined that the translation service is either ready
     * to be used ({@code SUCCESS}) or the service is not available {@code ERROR}.
     */
    public void onInit(int status);
  }

  private final Context mContext;
  private final TranslatorClientHandler mHandler = new TranslatorClientHandler();
  private final ServiceCallback mServiceCallback = new ServiceCallback();

  private OnInitListener mOnInitListener;
  private Connection mConnection;
  private int mNumFailedBinds = 0;

  /**
   * Constructs an instance. {@code context} is used to bind to the translator service. The other
   * methods of this class should not be called (they will fail) until {@code
   * onInitListener.onInit()} is called.
   */
  public TranslatorClient(Context context, OnInitListener onInitListener) {
    mContext = context;
    mOnInitListener = onInitListener;
    doBindService();
  }

  /**
   * Destroys this instance, deallocating any global resources it is using. Any {@link
   * BrailleTranslator} objects that were created using this object are invalid after this call.
   */
  public void destroy() {
    doUnbindService();
    mHandler.destroy();
  }

  /**
   * Returns a list of installed translation tables with meta-data describing them. Returns {@code
   * null} if the translation service has not been initialized, or an error occurs.
   */
  public List<TableInfo> getTables() {
    ITranslatorService localService = getTranslatorService();
    if (localService != null) {
      try {
        return Arrays.asList(localService.getTableInfos());
      } catch (RemoteException ex) {
        Log.e(LOG_TAG, "Error in getTables", ex);
      }
    }
    return null;
  }

  /**
   * Returns a new {@link BrailleTranslator} for the translation table specified by {@code tableId}.
   * Translation tables can be discovered using {@link #getTables}. @see {@link TableInfo#getId}.
   */
  public BrailleTranslator getTranslator(String tableId) {
    ITranslatorService localService = getTranslatorService();
    if (localService != null) {
      try {
        if (localService.checkTable(tableId)) {
          List<TableInfo> tables = getTables();
          for (TableInfo table : tables) {
            if (table.getId().equals(tableId)) {
              return new BrailleTranslatorImpl(table);
            }
          }
        }
      } catch (RemoteException ex) {
        Log.e(LOG_TAG, "Error in getTranslator", ex);
      }
    }
    return null;
  }

  private void doBindService() {
    Connection localConnection = new Connection();
    Intent serviceIntent = new Intent(ACTION_TRANSLATOR_SERVICE);
    PackageManager pm = mContext.getPackageManager();
    List<ResolveInfo> resolveInfo = pm.queryIntentServices(serviceIntent, 0);
    if (resolveInfo == null || resolveInfo.isEmpty()) {
      Log.e(LOG_TAG, "Unable to create intent");
      return;
    }

    // Get component info and create ComponentName
    ResolveInfo serviceInfo = resolveInfo.get(0);
    String packageName = serviceInfo.serviceInfo.packageName;
    String className = serviceInfo.serviceInfo.name;
    ComponentName component = new ComponentName(packageName, className);

    // Create a new intent. Use the old one for extras and such reuse
    Intent explicitIntent = new Intent(serviceIntent);

    // Set the component to be explicit
    explicitIntent.setComponent(component);

    if (!mContext.bindService(explicitIntent, localConnection, Context.BIND_AUTO_CREATE)) {
      Log.e(LOG_TAG, "Failed to bind to service");
      mHandler.scheduleRebind();
      return;
    }
    mConnection = localConnection;
    Log.i(LOG_TAG, "Bound to translator service");
  }

  private void doUnbindService() {
    if (mConnection != null) {
      mContext.unbindService(mConnection);
      mConnection = null;
    }
  }

  private ITranslatorService getTranslatorService() {
    Connection localConnection = mConnection;
    if (localConnection != null) {
      return localConnection.mService;
    }
    return null;
  }

  private class Connection implements ServiceConnection {
    // Read in application threads, written in main thread.
    private volatile ITranslatorService mService;

    @Override
    public void onServiceConnected(ComponentName className, IBinder binder) {
      Log.i(LOG_TAG, "Connected to translation service");
      ITranslatorService localService = ITranslatorService.Stub.asInterface(binder);
      try {
        localService.setCallback(mServiceCallback);
        mService = localService;
        synchronized (mHandler) {
          mNumFailedBinds = 0;
        }
      } catch (RemoteException ex) {
        // Service went away, rely on disconnect handler to
        // schedule a rebind.
        Log.e(LOG_TAG, "Error when setting callback", ex);
      }
    }

    @Override
    public void onServiceDisconnected(ComponentName className) {
      Log.e(LOG_TAG, "Disconnected from translator service");
      mService = null;
      // Retry by rebinding, and finally call the onInit if aplicable.
      mHandler.scheduleRebind();
    }
  }

  private class BrailleTranslatorImpl implements BrailleTranslator {
    private final TableInfo mTableInfo;

    public BrailleTranslatorImpl(TableInfo tableInfo) {
      mTableInfo = tableInfo;
    }

    @Override
    public TranslationResult translate(
        String text, int cursorPosition, boolean computerBrailleAtCursor) {
      ITranslatorService localService = getTranslatorService();
      if (localService != null) {
        try {
          return localService.translate(
              text, mTableInfo.getId(), cursorPosition, computerBrailleAtCursor);
        } catch (RemoteException ex) {
          Log.e(LOG_TAG, "Error in translate", ex);
        }
      }
      return null;
    }

    @Override
    public TranslationResult translate(String text, int cursorPosition) {
      return translate(text, cursorPosition, false);
    }

    @Override
    public String backTranslate(byte[] cells) {
      ITranslatorService localService = getTranslatorService();
      if (localService != null) {
        try {
          return localService.backTranslate(cells, mTableInfo.getId());
        } catch (RemoteException ex) {
          Log.e(LOG_TAG, "Error in backTranslate", ex);
        }
      }
      return null;
    }

    @Override
    public TableInfo getTableInfo() {
      return mTableInfo;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o instanceof BrailleTranslatorImpl) {
        BrailleTranslatorImpl other = (BrailleTranslatorImpl) o;
        return mTableInfo.getId().equals(other.mTableInfo.getId());
      }
      return false;
    }

    @Override
    public int hashCode() {
      return mTableInfo.getId().hashCode();
    }

    @Override
    public String toString() {
      return String.format("{BrailleTranslatorImpl %s}", mTableInfo.getId());
    }
  }

  private class ServiceCallback extends ITranslatorServiceCallback.Stub {
    @Override
    public void onInit(int status) {
      mHandler.onInit(status);
    }
  }

  private class TranslatorClientHandler extends Handler {
    private static final int MSG_ON_INIT = 1;
    private static final int MSG_REBIND_SERVICE = 2;

    public void onInit(int status) {
      obtainMessage(MSG_ON_INIT, status, 0).sendToTarget();
    }

    public void destroy() {
      mOnInitListener = null;
      // Cacnel outstanding messages, most importantly
      // scheduled rebinds.
      removeCallbacksAndMessages(null);
    }

    public void scheduleRebind() {
      synchronized (this) {
        if (mNumFailedBinds < MAX_REBIND_ATTEMPTS) {
          int delay = REBIND_DELAY_MILLIS << mNumFailedBinds;
          sendEmptyMessageDelayed(MSG_REBIND_SERVICE, delay);
          ++mNumFailedBinds;
        } else {
          onInit(ERROR);
        }
      }
    }

    @Override
    public void handleMessage(Message msg) {
      switch (msg.what) {
        case MSG_ON_INIT:
          handleOnInit(msg.arg1);
          break;
        case MSG_REBIND_SERVICE:
          handleRebindService();
          break;
      }
    }

    private void handleOnInit(int status) {
      if (mOnInitListener != null) {
        mOnInitListener.onInit(status);
        mOnInitListener = null;
      }
    }

    private void handleRebindService() {
      if (mConnection != null) {
        doUnbindService();
      }
      doBindService();
    }
  }
}

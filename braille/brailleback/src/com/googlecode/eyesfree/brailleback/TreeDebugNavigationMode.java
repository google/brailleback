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

import static com.googlecode.eyesfree.brailleback.BrailleBackService.DOT1;
import static com.googlecode.eyesfree.brailleback.BrailleBackService.DOT2;
import static com.googlecode.eyesfree.brailleback.BrailleBackService.DOT3;
import static com.googlecode.eyesfree.brailleback.BrailleBackService.DOT4;
import static com.googlecode.eyesfree.brailleback.BrailleBackService.DOT5;

import android.graphics.Rect;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.googlecode.eyesfree.braille.display.BrailleInputEvent;
import com.googlecode.eyesfree.utils.LogUtils;
import com.googlecode.eyesfree.utils.TreeDebug;

/**
 * A debugging navigation mode that allows navigating through the
 * currently active window's node tree.
 *
 */
public class TreeDebugNavigationMode implements NavigationMode {

    private final DisplayManager mDisplayManager;
    private final FeedbackManager mFeedbackManager;
    private final BrailleBackService mService;

    // TODO(kristianm): Could keep current nodes in inactive Windows in an LRU
    // cache to give a better user experience when switching
    // between windows.
    /**
     * The node that is currently shown on the display and which
     * navigation commands start at.
     */
    private AccessibilityNodeInfo mCurrentNode;
    /**
     * The node of the accessibility event that was last observed.
     * This may become the current node under certain circumstances.
     */
    private AccessibilityNodeInfo mPendingNode;

    public TreeDebugNavigationMode(DisplayManager displayManager,
            FeedbackManager feedbackManager,
            BrailleBackService service) {
        mDisplayManager = displayManager;
        mFeedbackManager = feedbackManager;
        mService = service;
    }

    @Override
    public boolean onPanLeftOverflow(DisplayManager.Content content) {
        return movePrevious();
    }

    @Override
    public boolean onPanRightOverflow(DisplayManager.Content content) {
        return moveNext();
    }

    @Override
    public boolean onMappedInputEvent(BrailleInputEvent event,
            DisplayManager.Content content) {
        switch (event.getCommand()) {
            case BrailleInputEvent.CMD_NAV_LINE_PREVIOUS:
                return movePreviousSibling();
            case BrailleInputEvent.CMD_NAV_LINE_NEXT:
                return moveNextSibling();
            case BrailleInputEvent.CMD_NAV_ITEM_PREVIOUS:
                return moveParent();
            case BrailleInputEvent.CMD_NAV_ITEM_NEXT:
                return moveFirstChild();
            case BrailleInputEvent.CMD_ROUTE:
                return activateCurrent();
            case BrailleInputEvent.CMD_BRAILLE_KEY:
                if (event.getArgument() == (DOT1 | DOT2)) {  // letter b
                    showRect();
                    return true;
                }
                if (event.getArgument() == (DOT1 | DOT4)) {  // letter c
                    // Undocumented way of clearing the node
                    // cache.
                    mService.setServiceInfo(mService.getServiceInfo());
                    return true;
                }
                if (event.getArgument()
                        == (DOT1 | DOT2 | DOT3 | DOT5)) {  // letter r
                    setPendingNode(
                        mService.getRootInActiveWindow());
                    if (mPendingNode == null) {
                        return false;
                    }
                    makePendingNodeCurrent();
                    displayCurrentNode();
                    return true;
                }
                if (event.getArgument()
                        == (DOT1 | DOT2 | DOT3 | DOT4)) {  // letter p
                    printNodes();
                    return true;
                }
                break;
        }
        return false;
    }

    @Override
    public void onActivate() {
        if (mPendingNode != null) {
            makePendingNodeCurrent();
        }
        displayCurrentNode();
    }

    @Override
    public void onDeactivate() {
        setCurrentNode(null);
    }

    @Override
    public void onObserveAccessibilityEvent(AccessibilityEvent event) {
        AccessibilityNodeInfo source = event.getSource();
        if (source == null) {
            return;
        }
        boolean isNewWindow = false;
        if (mCurrentNode == null ||
                mCurrentNode.getWindowId() != source.getWindowId()) {
            isNewWindow = true;
        }
        int t = event.getEventType();
        boolean isInterestingEventType = false;
        if (t == AccessibilityEvent.TYPE_VIEW_SELECTED
                || t == AccessibilityEvent.TYPE_VIEW_FOCUSED
                || t == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                || t != AccessibilityEvent.TYPE_VIEW_HOVER_ENTER) {
            isInterestingEventType = true;
        }
        if (isNewWindow || isInterestingEventType) {
            setPendingNode(source);
        }
    }

    @Override
    public boolean onAccessibilityEvent(AccessibilityEvent event) {
        if (mPendingNode == null) {
            return false;
        }
        if (mCurrentNode == null ||
                mCurrentNode.getWindowId() != mPendingNode.getWindowId()) {
            makePendingNodeCurrent();
            displayCurrentNode();
            return true;
        }
        return false;
    }

    @Override
    public void onInvalidateAccessibilityNode(
        AccessibilityNodeInfoCompat node) {
        // Nothing to do.
    }

    private void setPendingNode(AccessibilityNodeInfo newNode) {
        if (mPendingNode != null && mPendingNode != newNode) {
            mPendingNode.recycle();
        }
        mPendingNode = newNode;
    }

    private void setCurrentNode(AccessibilityNodeInfo newNode) {
        if (mCurrentNode != null && mCurrentNode != newNode) {
            mCurrentNode.recycle();
        }
        mCurrentNode = newNode;
    }

    private void makePendingNodeCurrent() {
        setCurrentNode(mPendingNode);
        mPendingNode = null;
    }

    private void displayCurrentNode() {
        if (mCurrentNode == null) {
            mDisplayManager.setContent(
                new DisplayManager.Content("No Node"));
        } else {
            mDisplayManager.setContent(new DisplayManager.Content(
                    TreeDebug.nodeDebugDescription(new AccessibilityNodeInfoCompat(mCurrentNode))));
        }
    }

    private boolean movePreviousSibling() {
        if (mCurrentNode == null) {
            return false;
        }
        return moveTo(getPreviousSibling(mCurrentNode));
    }

    private AccessibilityNodeInfo getPreviousSibling(
        AccessibilityNodeInfo from) {
        AccessibilityNodeInfo ret = null;
        AccessibilityNodeInfo parent = from.getParent();
        if (parent == null) {
            return null;
        }
        AccessibilityNodeInfo prev = null;
        AccessibilityNodeInfo cur = null;
        try {
            int childCount = parent.getChildCount();
            for (int i = 0; i < childCount; ++i) {
                cur = parent.getChild(i);
                if (cur == null) {
                    return null;
                }
                if (cur.equals(from)) {
                    ret = prev;
                    prev = null;
                    return ret;
                }
                if (prev != null) {
                    prev.recycle();
                }
                prev = cur;
                cur = null;
            }
        } finally {
            parent.recycle();
            if (prev != null) {
                prev.recycle();
            }
            if (cur != null) {
                cur.recycle();
            }
        }
        return ret;
    }

    private boolean moveNextSibling() {
        if (mCurrentNode == null) {
            return false;
        }
        return moveTo(getNextSibling(mCurrentNode));
    }

    private AccessibilityNodeInfo getNextSibling(
        AccessibilityNodeInfo from) {
        AccessibilityNodeInfo parent = from.getParent();
        if (parent == null) {
            return null;
        }
        AccessibilityNodeInfo cur = null;
        try {
            int childCount = parent.getChildCount();
            for (int i = 0; i < childCount - 1; ++i) {
                cur = parent.getChild(i);
                if (cur == null) {
                    return null;
                }
                if (cur.equals(from)) {
                    return parent.getChild(i + 1);
                }
                if (cur != null) {
                    cur.recycle();
                    cur = null;
                }
            }
        } finally {
            parent.recycle();
            if (cur != null) {
                cur.recycle();
            }
        }
        return null;
    }

    private boolean moveParent() {
        if (mCurrentNode == null) {
            return false;
        }
        AccessibilityNodeInfo parent = mCurrentNode.getParent();
        return moveTo(parent,
                FeedbackManager.TYPE_NAVIGATE_OUT_OF_HIERARCHY);
    }

    private boolean moveFirstChild() {
        if (mCurrentNode == null) {
            return false;
        }
        return moveTo(getFirstChild(mCurrentNode),
                FeedbackManager.TYPE_NAVIGATE_OUT_OF_HIERARCHY);
    }

    private AccessibilityNodeInfo getFirstChild(
        AccessibilityNodeInfo from) {
        if (from.getChildCount() < 1) {
            return null;
        }
        return from.getChild(0);
    }

    private AccessibilityNodeInfo getLastChild(
        AccessibilityNodeInfo from) {
        if (from.getChildCount() < 1) {
            return null;
        }
        return from.getChild(from.getChildCount() - 1);
    }

    private boolean movePrevious() {
        if (mCurrentNode == null) {
            return false;
        }
        AccessibilityNodeInfo target = null;
        int feedbackType = FeedbackManager.TYPE_NONE;
        AccessibilityNodeInfo prevSibling = getPreviousSibling(mCurrentNode);
        if (prevSibling != null) {
            target = getLastDescendantDfs(prevSibling);
            if (target != null) {
                feedbackType = FeedbackManager.TYPE_NAVIGATE_INTO_HIERARCHY;
                prevSibling.recycle();
                prevSibling = null;
            } else {
                target = prevSibling;
            }
        }
        if (target == null) {
            target = mCurrentNode.getParent();
            if (target != null) {
                feedbackType = FeedbackManager.TYPE_NAVIGATE_OUT_OF_HIERARCHY;
            }
        }
        return moveTo(target, feedbackType);
    }

    private AccessibilityNodeInfo getLastDescendantDfs(
        AccessibilityNodeInfo from) {
        AccessibilityNodeInfo lastChild = getLastChild(from);
        if (lastChild == null) {
            return null;
        }
        while (true) {
            AccessibilityNodeInfo lastGrandChild = getLastChild(lastChild);
            if (lastGrandChild != null) {
                lastChild.recycle();
                lastChild = lastGrandChild;
            } else {
                break;
            }
        }
        return lastChild;
    }

    private boolean moveNext() {
        if (mCurrentNode == null) {
            return false;
        }
        int feedbackType = FeedbackManager.TYPE_NONE;
        AccessibilityNodeInfo target = getFirstChild(mCurrentNode);
        if (target != null) {
            feedbackType = FeedbackManager.TYPE_NAVIGATE_INTO_HIERARCHY;
        } else {
            target = getNextSibling(mCurrentNode);
        }
        if (target == null) {
            AccessibilityNodeInfo ancestor = mCurrentNode.getParent();
            while (target == null && ancestor != null) {
                target = getNextSibling(ancestor);
                if (target == null) {
                    AccessibilityNodeInfo temp = ancestor.getParent();
                    ancestor.recycle();
                    ancestor = temp;
                } else {
                    ancestor.recycle();
                    ancestor = null;
                }
            }
            if (target != null) {
                feedbackType = FeedbackManager.TYPE_NAVIGATE_OUT_OF_HIERARCHY;
            }
        }
        return moveTo(target, feedbackType);
    }

    private boolean moveTo(AccessibilityNodeInfo node, int feedbackType) {
        if (moveTo(node)) {
            mFeedbackManager.emitFeedback(feedbackType);
            return true;
        }
        return false;
    }

    private boolean moveTo(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }
        setCurrentNode(node);
        displayCurrentNode();
        return true;
    }

    private boolean activateCurrent() {
        if (mCurrentNode == null) {
            return false;
        }
        boolean ret = mCurrentNode.performAction(
            AccessibilityNodeInfo.ACTION_FOCUS);
        return ret;
    }

    private void showRect() {
        if (mCurrentNode == null) {
            return;
        }
        Rect rect = new Rect();
        mCurrentNode.getBoundsInScreen(rect);
        mDisplayManager.setContent(
            new DisplayManager.Content("b: " + rect));
    }

    /**
     * Outputs the node tree from the current node using dfs preorder
     * traversal.
     */
    private void printNodes() {
        if (mCurrentNode == null) {
            LogUtils.log(this, Log.VERBOSE, "No current node");
            return;
        }

        LogUtils.log(this, Log.VERBOSE, "Printing nodes");
        TreeDebug.logNodeTree(new AccessibilityNodeInfoCompat(mCurrentNode));
    }
}

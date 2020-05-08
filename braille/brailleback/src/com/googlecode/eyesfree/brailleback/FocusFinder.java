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

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;
import com.googlecode.eyesfree.utils.LogUtils;
import com.googlecode.eyesfree.utils.NodeFocusFinder;
import java.util.HashSet;

/**
 * Functions to find focus.
 *
 * NOTE: To give a consistent behaviour, this code should be kept in sync
 * with the relevant subset of code in the {@code CursorController}
 * class in TalkBack.
 */
public class FocusFinder {

    public static final int SEARCH_FORWARD = NodeFocusFinder.SEARCH_FORWARD;
    public static final int SEARCH_BACKWARD = NodeFocusFinder.SEARCH_BACKWARD;

    private Context mContext;

    private final HashSet<AccessibilityNodeInfoCompat> mTmpNodeHash =
            new HashSet<AccessibilityNodeInfoCompat>();

    public FocusFinder(Context context) {
        mContext = context;
    }

    public AccessibilityNodeInfoCompat linear(
        AccessibilityNodeInfoCompat source,
        int direction) {
        if (source == null) {
            return null;
        }
        AccessibilityNodeInfoCompat next =
                NodeFocusFinder.focusSearch(source, direction);

        HashSet<AccessibilityNodeInfoCompat> seenNodes = mTmpNodeHash;
        seenNodes.clear();

        while ((next != null)
                && !AccessibilityNodeInfoUtils.shouldFocusNode(
                    mContext, next)) {
            if (seenNodes.contains(next)) {
                LogUtils.log(this, Log.ERROR,
                        "Found duplicate node during traversal: %s", next);
                break;
            }

            LogUtils.log(this, Log.VERBOSE,
                    "Search strategy rejected node: %s", next.getInfo());
            seenNodes.add(next);
            next = NodeFocusFinder.focusSearch(next, direction);
        }

        // Clear the list of seen nodes.
        AccessibilityNodeInfoUtils.recycleNodes(seenNodes);

        if (next == null) {
            LogUtils.log(this, Log.VERBOSE, "Failed to find the next node");
        }
        return next;
    }

    public static AccessibilityNodeInfoCompat findFirstFocusableDescendant(
            AccessibilityNodeInfoCompat root, Context context) {
        // null guard and shortcut for leaf nodes.
        if (root == null || root.getChildCount() <= 0) {
            return null;
        }
        HashSet<AccessibilityNodeInfoCompat> seenNodes =
                new HashSet<AccessibilityNodeInfoCompat>();
        seenNodes.add(root);
        try {
            return findFirstFocusableDescendantInternal(
                    root, context, seenNodes);
        } finally {
            seenNodes.remove(root);  // Not owned by us.
            AccessibilityNodeInfoUtils.recycleNodes(seenNodes);
        }
    }

    private static AccessibilityNodeInfoCompat
          findFirstFocusableDescendantInternal(
                  AccessibilityNodeInfoCompat root, Context context,
                  HashSet<AccessibilityNodeInfoCompat> seenNodes) {
        for (int i = 0, end = root.getChildCount(); i < end; ++i) {
            AccessibilityNodeInfoCompat child = root.getChild(i);
            if (child == null) {
                continue;
            }
            if (AccessibilityNodeInfoUtils.shouldFocusNode(
                            context, child)) {
                return child;
            }
            if (!seenNodes.add(child)) {
                LogUtils.log(FocusFinder.class, Log.ERROR,
                        "Cycle in node tree");
                child.recycle();
                return null;
            }
            AccessibilityNodeInfoCompat n =
                    findFirstFocusableDescendantInternal(
                            child, context, seenNodes);
            if (n != null) {
                return n;
            }
        }
        return null;
    }

    public static AccessibilityNodeInfoCompat findLastFocusableDescendant(
            AccessibilityNodeInfoCompat root, Context context) {
        // null guard and shortcut for leaf nodes.
        if (root == null || root.getChildCount() <= 0) {
            return null;
        }
        HashSet<AccessibilityNodeInfoCompat> seenNodes =
                new HashSet<AccessibilityNodeInfoCompat>();
        seenNodes.add(root);
        try {
            return findLastFocusableDescendantInternal(
                    root, context, seenNodes);
        } finally {
            seenNodes.remove(root);  // Not owned by us.
            AccessibilityNodeInfoUtils.recycleNodes(seenNodes);
        }
    }

    private static AccessibilityNodeInfoCompat
          findLastFocusableDescendantInternal(
                  AccessibilityNodeInfoCompat root, Context context,
                  HashSet<AccessibilityNodeInfoCompat> seenNodes) {
        for (int end = root.getChildCount(), i = end - 1; i >= 0; --i) {
            AccessibilityNodeInfoCompat child = root.getChild(i);
            if (child == null) {
                continue;
            }

            AccessibilityNodeInfoCompat n =
                    findLastFocusableDescendantInternal(
                            child, context, seenNodes);
            if (n != null) {
                return n;
            }

            if (AccessibilityNodeInfoUtils.shouldFocusNode(context, child)) {
                return child;
            }
            if (!seenNodes.add(child)) {
                LogUtils.log(FocusFinder.class, Log.ERROR,
                        "Cycle in node tree");
                child.recycle();
                return null;
            }
        }
        return null;
    }

    public static AccessibilityNodeInfoCompat getFocusedNode(
        AccessibilityService service, boolean fallbackOnRoot) {
        AccessibilityNodeInfo root =
                service.getRootInActiveWindow();
        AccessibilityNodeInfo focused = null;
        try {
            AccessibilityNodeInfo ret = null;
            if (root != null) {
                focused = root.findFocus(
                    AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
                if (focused != null && focused.isVisibleToUser()) {
                    ret = focused;
                    focused = null;
                } else if (fallbackOnRoot) {
                    ret = root;
                    root = null;
                }
            } else {
                LogUtils.log(service, Log.ERROR, "No current window root");
            }
            if (ret != null) {
                return new AccessibilityNodeInfoCompat(ret);
            }
        } finally {
            if (root != null) {
                root.recycle();
            }
            if (focused != null) {
                focused.recycle();
            }
        }
        return null;
    }
}

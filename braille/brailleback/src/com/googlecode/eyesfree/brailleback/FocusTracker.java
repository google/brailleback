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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import com.googlecode.eyesfree.braille.display.Display;
import com.googlecode.eyesfree.compat.accessibilityservice.AccessibilityServiceCompatUtils;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;

/**
 * Tracks various events and updates accessibility focus.
 *
 * Currently, this class moves accessibility focus to the node that has input
 * focus when the screen is turned on or a display gets connected.
 */
public class FocusTracker
        extends BroadcastReceiver
        implements Display.OnConnectionStateChangeListener {
    private final AccessibilityService mAccessibilityService;
    private boolean mDisplayConnected = false;

    public FocusTracker(AccessibilityService accessibilityService) {
        mAccessibilityService = accessibilityService;
    }

    public void register() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        mAccessibilityService.registerReceiver(this, filter);
    }

    public void unregister() {
        mAccessibilityService.unregisterReceiver(this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_SCREEN_ON.equals(action)
                && mDisplayConnected) {
            setFocusFromInput();
        }
    }

    @Override
    public void onConnectionStateChanged(int state) {
        mDisplayConnected = (state == Display.STATE_CONNECTED);
        if (mDisplayConnected) {
            setFocusFromInput();
        }
    }

    /**
     * Sets the accessibility focus to the node that currently has
     * input focus, if accessibility focus is not already set
     * in the currently focused window.
     */
    private void setFocusFromInput() {
        AccessibilityNodeInfoCompat root =
                AccessibilityServiceCompatUtils.getRootInActiveWindow(
                        mAccessibilityService);
        if (root == null) {
            return;
        }
        AccessibilityNodeInfoCompat accessibilityFocused = null;
        AccessibilityNodeInfoCompat inputFocused = null;
        try {
            accessibilityFocused = root.findFocus(
                    AccessibilityNodeInfoCompat.FOCUS_ACCESSIBILITY);
            if (accessibilityFocused != null) {
                return;
            }
            inputFocused = root.findFocus(
                    AccessibilityNodeInfoCompat.FOCUS_INPUT);
            if (inputFocused == null
                    || !AccessibilityNodeInfoUtils.shouldFocusNode(
                            mAccessibilityService, inputFocused)) {
                return;
            }
            inputFocused.performAction(
                    AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS);
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(root, inputFocused,
                    accessibilityFocused);
        }
    }
}

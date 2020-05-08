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

import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityEvent;
import com.googlecode.eyesfree.braille.display.BrailleInputEvent;

/**
 * A way of navigating the android user interface.
 *
 */
public interface NavigationMode {
    void onActivate();
    void onDeactivate();
    void onObserveAccessibilityEvent(AccessibilityEvent event);
    boolean onAccessibilityEvent(AccessibilityEvent event);
    void onInvalidateAccessibilityNode(AccessibilityNodeInfoCompat node);
    boolean onPanLeftOverflow(DisplayManager.Content content);
    boolean onPanRightOverflow(DisplayManager.Content content);
    boolean onMappedInputEvent(BrailleInputEvent event,
            DisplayManager.Content content);
}

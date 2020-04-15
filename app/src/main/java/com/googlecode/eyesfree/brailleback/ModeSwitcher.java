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
 * Keeps track of the current navigation mode and dispatches events to the navigation modes
 * depending on which one is active.
 */
public class ModeSwitcher implements NavigationMode {
  private final NavigationMode[] mModes;
  private int mModeIndex = 0;
  private NavigationMode mOverrideMode;
  private boolean mActive = false;

  public ModeSwitcher(NavigationMode... modes) {
    mModes = modes;
  }

  public NavigationMode getCurrentMode() {
    return mOverrideMode != null ? mOverrideMode : mModes[mModeIndex];
  }

  public void switchMode() {
    if (mActive) {
      getCurrentMode().onDeactivate();
    }
    mModeIndex = (mModeIndex + 1) % mModes.length;
    mOverrideMode = null;
    if (mActive) {
      getCurrentMode().onActivate();
    }
  }

  /**
   * Sets a mode, typically not one of the modes that were supplied during constructions, that
   * overrides the current mode. {@code newOverrideMode} is the overriding mode, or {@code null} to
   * go back to the mode that was active before overriding it.
   */
  public void overrideMode(NavigationMode newOverrideMode) {
    NavigationMode oldMode = getCurrentMode();
    NavigationMode newMode = newOverrideMode != null ? newOverrideMode : mModes[mModeIndex];
    if (newMode == oldMode) {
      return;
    }
    if (mActive) {
      oldMode.onDeactivate();
    }
    mOverrideMode = newOverrideMode;
    if (mActive) {
      newMode.onActivate();
    }
  }

  @Override
  public void onActivate() {
    mActive = true;
    getCurrentMode().onActivate();
  }

  @Override
  public void onDeactivate() {
    getCurrentMode().onDeactivate();
    mActive = false;
  }

  @Override
  public boolean onPanLeftOverflow(DisplayManager.Content content) {
    boolean ret = getCurrentMode().onPanLeftOverflow(content);
    if (!ret && mOverrideMode != null) {
      ret = mModes[mModeIndex].onPanLeftOverflow(content);
    }
    return ret;
  }

  @Override
  public boolean onPanRightOverflow(DisplayManager.Content content) {
    boolean ret = getCurrentMode().onPanRightOverflow(content);
    if (!ret && mOverrideMode != null) {
      ret = mModes[mModeIndex].onPanRightOverflow(content);
    }
    return ret;
  }

  @Override
  public boolean onMappedInputEvent(BrailleInputEvent event, DisplayManager.Content content) {
    boolean ret = getCurrentMode().onMappedInputEvent(event, content);
    if (!ret && mOverrideMode != null) {
      ret = mModes[mModeIndex].onMappedInputEvent(event, content);
    }
    return ret;
  }

  @Override
  public void onObserveAccessibilityEvent(AccessibilityEvent event) {
    for (NavigationMode mode : mModes) {
      mode.onObserveAccessibilityEvent(event);
    }
  }

  @Override
  public boolean onAccessibilityEvent(AccessibilityEvent event) {
    if (mOverrideMode == null || !mOverrideMode.onAccessibilityEvent(event)) {
      return mModes[mModeIndex].onAccessibilityEvent(event);
    }
    return true;
  }

  @Override
  public void onInvalidateAccessibilityNode(AccessibilityNodeInfoCompat node) {
    getCurrentMode().onInvalidateAccessibilityNode(node);
  }
}

/*
 * Copyright (C) 2013 Google Inc.
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

/**
 * Keeps track of the self-braille service and provides a means for querying the self-brailled
 * content.
 *
 * <p>This implementation assumes that the {@link SelfBrailleService} is running in the same
 * process, and accesses a static reference to it.
 */
public class SelfBrailleManager {
  private boolean mImeOpen;

  public DisplayManager.Content contentForNode(AccessibilityNodeInfoCompat node) {
    final SelfBrailleService service = getSelfBrailleService();
    DisplayManager.Content content = service != null ? service.contentForNode(node) : null;
    if (content != null) {
      content.setContractionMode(
          mImeOpen
              ? DisplayManager.Content.CONTRACT_DEFAULT
              : DisplayManager.Content.CONTRACT_ALWAYS_ALLOW);
    }
    return content;
  }

  public boolean hasContentForNode(AccessibilityNodeInfoCompat node) {
    // TODO: Tweak SelfBrailleService so that we don't need to actually
    // compute the content -- we know that it exists before then.
    return contentForNode(node) != null;
  }

  /**
   * Informs the self braille manager about the state of the IME input view. This is used to decide
   * on whether to allow contracted braille when generating the braille content.
   */
  public void setImeOpen(boolean open) {
    mImeOpen = open;
  }

  private SelfBrailleService getSelfBrailleService() {
    return SelfBrailleService.getActiveInstance();
  }
}

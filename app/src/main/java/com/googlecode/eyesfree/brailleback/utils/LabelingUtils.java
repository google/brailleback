/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.googlecode.eyesfree.brailleback.utils;

import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityNodeInfo;
import com.googlecode.eyesfree.labeling.CustomLabelManager;
import com.googlecode.eyesfree.labeling.Label;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;

/**
 * Utility class for getting node text, falling back to label manager. TODO: Put this into
 * AccessibilityNodeInfoUtils when support libs fixed so that we don't need to unwrap the node to
 * get view id resource name.
 */
public class LabelingUtils {
  /**
   * Gets the text of a <code>node</code> by returning the content description (if available) or by
   * returning the text. Will use the specified <code>CustomLabelManager</code> as a fall back if
   * both are null. If the label manager is null, does the same funciton as {@code getNodeText} in
   * {@code AccessibilityNodeInfoUtils}
   *
   * @param node The node.
   * @param labelManager The label manager.
   * @return The node text.
   */
  public static CharSequence getNodeText(
      AccessibilityNodeInfoCompat node, CustomLabelManager labelManager) {
    CharSequence text = AccessibilityNodeInfoUtils.getNodeText(node);
    if (!TextUtils.isEmpty(text)) {
      return text;
    }

    if (labelManager != null && labelManager.isInitialized()) {
      // TODO: Don't need to do this when support libs fixed.
      final AccessibilityNodeInfo unwrappedNode = (AccessibilityNodeInfo) node.getInfo();
      Label label = labelManager.getLabelForViewIdFromCache(unwrappedNode.getViewIdResourceName());
      if (label != null) {
        return label.getText();
      }
    }
    return null;
  }
}

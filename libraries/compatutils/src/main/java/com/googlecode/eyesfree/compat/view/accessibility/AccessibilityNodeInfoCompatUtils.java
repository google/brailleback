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

package com.googlecode.eyesfree.compat.view.accessibility;

import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import com.googlecode.eyesfree.compat.CompatUtils;
import java.lang.reflect.Method;

public class AccessibilityNodeInfoCompatUtils {

  private static final String TAG = AccessibilityNodeInfoCompatUtils.class.getSimpleName();

  private static final Class<?> CLASS_AccessibilityNodeInfo =
      CompatUtils.getClass("android.view.accessibility.AccessibilityNodeInfo");
  private static final Method METHOD_getLabelFor =
      CompatUtils.getMethod(CLASS_AccessibilityNodeInfo, "getLabelFor");
  private static final Method METHOD_getLabeledBy =
      CompatUtils.getMethod(CLASS_AccessibilityNodeInfo, "getLabeledBy");

  /**
   * Gets the node info for which the view represented by this info serves as a label for
   * accessibility purposes.
   *
   * <p><strong>Note:</strong> It is a client responsibility to recycle the received info by calling
   * {@link AccessibilityNodeInfoCompat#recycle()} to avoid creating of multiple instances.
   *
   * @return The labeled info.
   */
  public static AccessibilityNodeInfoCompat getLabelFor(AccessibilityNodeInfoCompat node) {
    final AccessibilityNodeInfo info = node.unwrap();
    if (info == null) {
      Log.e(TAG, "Compat node was missing internal node");
      return null;
    }

    final AccessibilityNodeInfo resultInfo =
        (AccessibilityNodeInfo) CompatUtils.invoke(info, null, METHOD_getLabelFor);
    if (resultInfo == null) {
      return null;
    }

    return AccessibilityNodeInfoCompat.wrap(resultInfo);
  }

  /**
   * Gets the node info which serves as the label of the view represented by this info for
   * accessibility purposes.
   *
   * <p><strong>Note:</strong> It is a client responsibility to recycle the received info by calling
   * {@link AccessibilityNodeInfoCompat#recycle()} to avoid creating of multiple instances.
   *
   * @return The label.
   */
  public static AccessibilityNodeInfoCompat getLabeledBy(AccessibilityNodeInfoCompat node) {
    final AccessibilityNodeInfo info = node.unwrap();
    if (info == null) {
      Log.e(TAG, "Compat node was missing internal node");
      return null;
    }

    final AccessibilityNodeInfo resultInfo =
        (AccessibilityNodeInfo) CompatUtils.invoke(info, null, METHOD_getLabeledBy);
    if (resultInfo == null) {
      return null;
    }

    return AccessibilityNodeInfoCompat.wrap(resultInfo);
  }
}

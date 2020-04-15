/*
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.googlecode.eyesfree.compat.view.accessibility;

import android.view.accessibility.AccessibilityManager;
import com.googlecode.eyesfree.compat.CompatUtils;
import java.lang.reflect.Method;

public class AccessibilityManagerCompatUtils {
  private static final Class<?> CLASS_AccessibilityManager = AccessibilityManager.class;
  private static final Method METHOD_isTouchExplorationEnabled =
      CompatUtils.getMethod(CLASS_AccessibilityManager, "isTouchExplorationEnabled");

  private AccessibilityManagerCompatUtils() {
    // This class is non-instantiable.
  }

  /**
   * Returns if the touch exploration in the system is enabled.
   *
   * <p><strong>Note:</strong> Added in API level 14, always returns false in earlier versions.
   *
   * @return True if touch exploration is enabled, false otherwise.
   */
  public static boolean isTouchExplorationEnabled(AccessibilityManager receiver) {
    return (Boolean) CompatUtils.invoke(receiver, false, METHOD_isTouchExplorationEnabled);
  }
}

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

package com.googlecode.eyesfree.compat.accessibilityservice;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityNodeInfo;
import com.googlecode.eyesfree.compat.CompatUtils;
import java.lang.reflect.Method;

public class AccessibilityServiceCompatUtils {
  private static final Method METHOD_performGlobalAction =
      CompatUtils.getMethod(AccessibilityService.class, "performGlobalAction", int.class);
  private static final Method METHOD_getServiceInfo =
      CompatUtils.getMethod(AccessibilityService.class, "getServiceInfo");
  private static final Method METHOD_getRootInActiveWindow =
      CompatUtils.getMethod(AccessibilityService.class, "getRootInActiveWindow");

  public static boolean performGlobalAction(AccessibilityService service, int action) {
    return (Boolean) CompatUtils.invoke(service, false, METHOD_performGlobalAction, action);
  }

  public static AccessibilityServiceInfo getServiceInfo(AccessibilityService service) {
    return (AccessibilityServiceInfo) CompatUtils.invoke(service, null, METHOD_getServiceInfo);
  }

  public static AccessibilityNodeInfoCompat getRootInActiveWindow(AccessibilityService service) {
    Object root = CompatUtils.invoke(service, null, METHOD_getRootInActiveWindow);
    if (root != null) {
      return AccessibilityNodeInfoCompat.wrap((AccessibilityNodeInfo) root);
    } else {
      return null;
    }
  }
}

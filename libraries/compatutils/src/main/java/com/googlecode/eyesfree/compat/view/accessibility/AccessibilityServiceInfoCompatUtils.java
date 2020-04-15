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

package com.googlecode.eyesfree.compat.view.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.os.Build;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

@SuppressWarnings("javadoc")
public class AccessibilityServiceInfoCompatUtils {
  /**
   * If this flag is set the system will regard views that are not important for accessibility in
   * addition to the ones that are important for accessibility. That is, views that are marked as
   * not important for accessibility via {@link View#IMPORTANT_FOR_ACCESSIBILITY_NO} and views that
   * are marked as potentially important for accessibility via {@link
   * View#IMPORTANT_FOR_ACCESSIBILITY_AUTO} for which the system has determined that are not
   * important for accessibility, are both reported while querying the window content and also the
   * accessibility service will receive accessibility events from them.
   *
   * <p><strong>Note:</strong> For accessibility services targeting API version {@link
   * Build.VERSION_CODES#JELLY_BEAN} or higher this flag has to be explicitly set for the system to
   * regard views that are not important for accessibility. For accessibility services targeting API
   * version lower than {@link Build.VERSION_CODES#JELLY_BEAN} this flag is ignored and all views
   * are regarded for accessibility purposes.
   *
   * <p>Usually views not important for accessibility are layout managers that do not react to user
   * actions, do not draw any content, and do not have any special semantics in the context of the
   * screen content. For example, a three by three grid can be implemented as three horizontal
   * linear layouts and one vertical, or three vertical linear layouts and one horizontal, or one
   * grid layout, etc. In this context the actual layout mangers used to achieve the grid
   * configuration are not important, rather it is important that there are nine evenly distributed
   * elements.
   */
  public static final int FLAG_INCLUDE_NOT_IMPORTANT_VIEWS = 0x0000002;

  /**
   * This flag requests that the system gets into touch exploration mode. In this mode a single
   * finger moving on the screen behaves as a mouse pointer hovering over the user interface. The
   * system will also detect certain gestures performed on the touch screen and notify this service.
   * The system will enable touch exploration mode if there is at least one accessibility service
   * that has this flag set. Hence, clearing this flag does not guarantee that the device will not
   * be in touch exploration mode since there may be another enabled service that requested it.
   *
   * <p>For accessibility services targeting API version higher than {@link
   * Build.VERSION_CODES#JELLY_BEAN_MR1} that want to set this flag have to request the {@link
   * android.Manifest.permission#CAN_REQUEST_TOUCH_EXPLORATION_MODE} permission or the flag will be
   * ignored.
   *
   * <p>Services targeting API version equal to or lower than {@link
   * Build.VERSION_CODES#JELLY_BEAN_MR1} will work normally, i.e. the first time they are run, if
   * this flag is specified, a dialog is shown to the user to confirm enabling explore by touch.
   */
  public static final int FLAG_REQUEST_TOUCH_EXPLORATION_MODE = 0x0000004;

  /**
   * This flag requests from the system to enable web accessibility enhancing extensions. Such
   * extensions aim to provide improved accessibility support for content presented in a {@link
   * android.webkit.WebView}. An example of such an extension is injecting JavaScript from Google.
   * The system will enable enhanced web accessibility if there is at least one accessibility
   * service that has this flag set. Hence, clearing this flag does not guarantee that the device
   * will not have enhanced web accessibility enabled since there may be another enabled service
   * that requested it.
   *
   * <p>Clients that want to set this flag have to request the {@link
   * android.Manifest.permission#CAN_REQUEST_ENHANCED_WEB_ACCESSIBILITY} permission or the flag will
   * be ignored.
   */
  public static final int FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY = 0x00000008;

  /**
   * This flag requests that the {@link AccessibilityNodeInfo}s obtained by an {@link
   * AccessibilityService} contain the id of the source view. The source view id will be a fully
   * qualified resource name of the form "package:id/name", for example "foo.bar:id/my_list", and it
   * is useful for UI test automation. This flag is not set by default.
   */
  public static final int FLAG_REPORT_VIEW_IDS = 0x00000010;

  /**
   * This flag requests from the system to filter key events. If this flag is set the accessibility
   * service will receive the key events before applications allowing it implement global shortcuts.
   * Setting this flag does not guarantee that this service will filter key events since only one
   * service can do so at any given time. This avoids user confusion due to behavior change in case
   * different key filtering services are enabled. If there is already another key filtering service
   * enabled, this one will not receive key events.
   *
   * <p>Services that want to set this flag have to declare this capability in their meta-data by
   * setting the attribute {@link android.R.attr #canRequestFilterKeyEvents
   * canRequestFilterKeyEvents} to true, otherwise this flag will be ignored. For how to declare the
   * meta-data of a service refer to {@value AccessibilityService#SERVICE_META_DATA}.
   */
  public static final int FLAG_REQUEST_FILTER_KEY_EVENTS = 0x00000020;
}

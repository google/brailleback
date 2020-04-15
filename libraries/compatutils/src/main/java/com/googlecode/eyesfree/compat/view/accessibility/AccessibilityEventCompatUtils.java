/*
 * Copyright (C) 2011 Google Inc.
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

import android.os.Parcel;
import android.view.accessibility.AccessibilityEvent;
import com.googlecode.eyesfree.compat.CompatUtils;
import java.lang.reflect.Method;

/** Provides backward-compatible access to method in {@link AccessibilityEvent}. */
public class AccessibilityEventCompatUtils {
  private static final Class<?> CLASS_AccessibilityEvent = AccessibilityEvent.class;
  private static final Class<?> CLASS_AccessibilityRecord =
      CompatUtils.getClass("android.view.accessibility.AccessibilityRecord");
  private static final Method METHOD_getToIndex =
      CompatUtils.getMethod(CLASS_AccessibilityRecord, "getToIndex");
  private static final Method METHOD_getMovementGranularity =
      CompatUtils.getMethod(CLASS_AccessibilityEvent, "getMovementGranularity");
  private static final Method METHOD_getAction =
      CompatUtils.getMethod(CLASS_AccessibilityEvent, "getAction");

  private AccessibilityEventCompatUtils() {
    // This class is non-instantiable.
  }

  /**
   * Returns a cached instance if such is available or a new one is created. The returned instance
   * is initialized from the given <code>event</code>.
   *
   * @param event The other event.
   * @return An instance.
   */
  public static AccessibilityEvent obtain(AccessibilityEvent event) {
    final Parcel parcel = Parcel.obtain();

    // Write the event to the parcel and reset the data pointer.
    event.writeToParcel(parcel, 0);
    parcel.setDataPosition(0);

    final AccessibilityEvent clone = AccessibilityEvent.CREATOR.createFromParcel(parcel);

    // Return the parcel to the global pool.
    parcel.recycle();

    return clone;
  }

  /**
   * Gets the index of text selection end or the index of the last visible item when scrolling.
   *
   * @return The index of selection end or last item index.
   */
  public static int getToIndex(AccessibilityEvent event) {
    return (Integer) CompatUtils.invoke(event, -1, METHOD_getToIndex);
  }

  /**
   * Gets the movement granularity that was traversed.
   *
   * @return The granularity.
   */
  public static int getMovementGranularity(AccessibilityEvent event) {
    return (Integer) CompatUtils.invoke(event, -1, METHOD_getMovementGranularity);
  }

  /**
   * Gets the performed action that triggered this event.
   *
   * @return The action.
   */
  public static int getAction(AccessibilityEvent event) {
    return (Integer) CompatUtils.invoke(event, -1, METHOD_getAction);
  }
}

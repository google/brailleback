/*
 * BRLTTY - A background process providing access to the console screen (when in
 *          text mode) for a blind person using a refreshable braille display.
 *
 * Copyright (C) 1995-2018 by The BRLTTY Developers.
 *
 * BRLTTY comes with ABSOLUTELY NO WARRANTY.
 *
 * This is free software, placed under the terms of the
 * GNU Lesser General Public License, as published by the Free Software
 * Foundation; either version 2.1 of the License, or (at your option) any
 * later version. Please see the file LICENSE-LGPL for details.
 *
 * Web Page: http://brltty.com/
 *
 * This software is maintained by Dave Mielke <dave@mielke.cc>.
 */

package org.a11y.brltty.android;

import android.view.accessibility.AccessibilityNodeInfo;
import java.util.HashMap;
import java.util.Map;

public class ScreenTextEditor {
  private static final Map<Integer, ScreenTextEditor> textEditors =
      new HashMap<Integer, ScreenTextEditor>();

  private int cursorOffset = 0;
  private int selectedFrom = 0;
  private int selectedTo = 0;

  public static ScreenTextEditor get(AccessibilityNodeInfo node, boolean canCreate) {
    Integer key = new Integer(node.hashCode());
    ScreenTextEditor value = textEditors.get(key);

    if (value == null) {
      if (canCreate) {
        value = new ScreenTextEditor();
        textEditors.put(key, value);
      }
    }

    return value;
  }

  public static ScreenTextEditor getIfFocused(AccessibilityNodeInfo node) {
    return node.isFocused() ? get(node, false) : null;
  }

  public final int getCursorOffset() {
    return cursorOffset;
  }

  public final void setCursorLocation(int offset) {
    cursorOffset = offset;
  }

  public final int getSelectedFrom() {
    return selectedFrom;
  }

  public final int getSelectedTo() {
    return selectedTo;
  }

  public final void setSelectedRegion(int from, int to) {
    selectedFrom = from;
    selectedTo = to;
  }

  public ScreenTextEditor() {}
}

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
import android.text.Spannable;
import android.text.Spanned;
import android.util.Log;
import com.googlecode.eyesfree.utils.LogUtils;
import java.nio.ByteBuffer;

/** Static utilities for text spans that control how text is displayed on the braille display. */
public class DisplaySpans {
  /** Marks a part of the content that has focus. */
  public static class FocusSpan {}

  /** Marks a text selection or cursor on the display. */
  public static class SelectionSpan {}

  /** Associates a span of text with a specific braille translation. */
  public static class BrailleSpan {
    public final byte[] braille;

    /**
     * Constructs a BrailleSpan whose {@code braille} member is populated from the contents of a
     * {@link ByteBuffer}, from index 0 to the current {@link ByteBuffer#position()} (exclusive).
     */
    public BrailleSpan(ByteBuffer buffer) {
      int len = buffer.position();
      this.braille = new byte[len];

      // Copy buffer from [0, position) and then restore buffer state.
      buffer.position(0);
      buffer.get(this.braille, 0, len);
      buffer.position(len);
    }
  }

  /** Marks a region of {@code spanned} as having focus. */
  public static void addFocus(Spannable spannable, int start, int end) {
    spannable.setSpan(new FocusSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  }

  /**
   * Marks a portion of {@code spannable} as containing text selection. If {@code start} and {@code
   * end} are equal, then then added span marks a cursor.
   */
  public static void addSelection(Spannable spannable, int start, int end) {
    int flags;
    if (start == end) {
      flags = Spanned.SPAN_EXCLUSIVE_INCLUSIVE;
    } else {
      flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
    }
    // If start and end are out of order... swap start and end. Required by setSpan().
    if (end < start) {
      int oldStart = start;
      start = end;
      end = oldStart;
    }
    spannable.setSpan(new SelectionSpan(), start, end, flags);
  }

  /**
   * Marks a portion of {@code spannable} as being represented by the cells in {@code buffer} from
   * [0, position). When rendering braille, this portion should not be translated, but should be
   * represented by the given cells.
   */
  public static void addBraille(Spannable spannable, int start, int end, ByteBuffer buffer) {
    spannable.setSpan(new BrailleSpan(buffer), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  }

  /**
   * Marks the whole of {@code spannable} as containing the content coming from {@code node}. A copy
   * of {@code node} is stored.
   *
   * @see #recycleSpans
   */
  public static void setAccessibilityNode(Spannable spannable, AccessibilityNodeInfoCompat node) {
    spannable.setSpan(
        AccessibilityNodeInfoCompat.obtain(node),
        0,
        spannable.length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  }

  /**
   * Finds the shortest accessibility node span that overlaps {@code position} in {@code chars}. If
   * a node is found, it is returned, otherwise {@code null} is returned. If a node is returned, it
   * is still owned by {@code chars} for the purpose of recycling.
   */
  public static AccessibilityNodeInfoCompat getAccessibilityNodeFromPosition(
      int position, CharSequence chars) {
    if (!(chars instanceof Spanned)) {
      return null;
    }
    Spanned spanned = (Spanned) chars;
    AccessibilityNodeInfoCompat[] spans =
        spanned.getSpans(position, position, AccessibilityNodeInfoCompat.class);
    if (spans.length == 0) {
      return null;
    }
    AccessibilityNodeInfoCompat found = spans[0];
    int foundLength = spanned.getSpanEnd(found) - spanned.getSpanStart(found);
    for (int i = 1; i < spans.length; ++i) {
      AccessibilityNodeInfoCompat span = spans[i];
      int length = spanned.getSpanEnd(span) - spanned.getSpanStart(span);
      if (length < foundLength) {
        found = span;
        foundLength = length;
      }
    }
    return found;
  }

  /**
   * Utility function to log what accessibiility nodes are attached to what parts of the character
   * sequence.
   */
  public static void logNodes(CharSequence chars) {
    if (!(chars instanceof Spanned)) {
      LogUtils.log(DisplaySpans.class, Log.VERBOSE, "Not a Spanned");
      return;
    }
    Spanned spanned = (Spanned) chars;
    AccessibilityNodeInfoCompat[] spans =
        spanned.getSpans(0, spanned.length(), AccessibilityNodeInfoCompat.class);
    for (AccessibilityNodeInfoCompat node : spans) {
      LogUtils.log(
          DisplaySpans.class,
          Log.VERBOSE,
          chars.subSequence(spanned.getSpanStart(node), spanned.getSpanEnd(node)).toString());
      LogUtils.log(DisplaySpans.class, Log.VERBOSE, node.getInfo().toString());
    }
  }

  /**
   * Recycles objects owned by the spannable. In particular, any accessibility nodes that have been
   * associated with {@code spannable} are recycled and removed.
   */
  public static void recycleSpans(CharSequence chars) {
    if (!(chars instanceof Spannable)) {
      return;
    }
    Spannable spannable = (Spannable) chars;
    AccessibilityNodeInfoCompat[] nodes =
        spannable.getSpans(0, spannable.length(), AccessibilityNodeInfoCompat.class);
    for (AccessibilityNodeInfoCompat node : nodes) {
      node.recycle();
      spannable.removeSpan(node);
    }
  }

  /** Returns a span in {@code spanned} that is {@link Object#equals} to {@code obj}. */
  public static Object getEqualSpan(Spanned spanned, Object obj) {
    Object[] spans = spanned.getSpans(0, spanned.length(), obj.getClass());
    for (Object span : spans) {
      if (obj.equals(span)) {
        return span;
      }
    }
    return null;
  }
}

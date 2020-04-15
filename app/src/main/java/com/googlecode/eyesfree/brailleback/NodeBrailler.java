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

import android.content.Context;
import android.graphics.Rect;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.Log;
import com.googlecode.eyesfree.brailleback.rule.BrailleRule;
import com.googlecode.eyesfree.brailleback.rule.BrailleRuleRepository;
import com.googlecode.eyesfree.brailleback.utils.StringUtils;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoRef;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;
import com.googlecode.eyesfree.utils.LogUtils;
import java.util.ArrayList;

/** Turns a subset of the node tree into braille. */
public class NodeBrailler {
  /**
   * Height difference between node rectangles under which they are put on the braille display at
   * the same time if adjacent and vertically overlapping.
   */
  private static final int HEIGHT_DIFF_THRESHOLD = 4;

  private final Context mContext;
  private final BrailleRuleRepository mRuleRepository;
  private final SelfBrailleManager mSelfBrailleManager;

  public NodeBrailler(
      Context context,
      BrailleRuleRepository ruleRepository,
      SelfBrailleManager selfBrailleManager) {
    mContext = context;
    mRuleRepository = ruleRepository;
    mSelfBrailleManager = selfBrailleManager;
  }

  /**
   * Converts the source of {@code event} and its surroundings to annotated text to put on the
   * braille display. Returns the new content, or {@code null} if the event doesn't have a source
   * node.
   */
  public DisplayManager.Content brailleNode(AccessibilityNodeInfoCompat node) {
    DisplayManager.Content content = mSelfBrailleManager.contentForNode(node);
    if (content == null) {
      ArrayList<AccessibilityNodeInfoCompat> toFormat =
          new ArrayList<AccessibilityNodeInfoCompat>();
      findNodesToFormat(node, toFormat);
      LogUtils.log(this, Log.VERBOSE, "Going to format %d nodes", toFormat.size());
      SpannableStringBuilder sb = new SpannableStringBuilder();
      for (AccessibilityNodeInfoCompat n : toFormat) {
        formatSubtree(n, sb);
      }
      content = new DisplayManager.Content(sb);
      content.setFirstNode(toFormat.get(0)).setLastNode(toFormat.get(toFormat.size() - 1));
      AccessibilityNodeInfoUtils.recycleNodes(toFormat);
    }
    return content;
  }

  /** Formats {@code node} and its descendants, appending the result to {@code sb}. */
  private void formatSubtree(AccessibilityNodeInfoCompat node, Editable result) {
    if (!node.isVisibleToUser()) {
      return;
    }

    BrailleRule rule = mRuleRepository.find(node);
    SpannableStringBuilder subtreeResult = new SpannableStringBuilder();
    rule.format(subtreeResult, mContext, node);
    if (rule.includeChildren(node, mContext)) {
      int childCount = node.getChildCount();
      for (int i = 0; i < childCount; ++i) {
        AccessibilityNodeInfoCompat child = node.getChild(i);
        if (child == null) {
          continue;
        }
        formatSubtree(child, subtreeResult);
        child.recycle();
      }
    }
    if (!TextUtils.isEmpty(subtreeResult)) {
      // If the node is accessibility focused, add the focus span
      // here to cover the node and its formatted children.
      // This is a fallback in case the formatting rule hasn't set
      // focus by itself.
      if (node.isAccessibilityFocused()
          && subtreeResult.getSpans(0, subtreeResult.length(), DisplaySpans.FocusSpan.class).length
              == 0) {
        DisplaySpans.addFocus(subtreeResult, 0, subtreeResult.length());
      }
      addNodeSpanForUncovered(node, subtreeResult);
      StringUtils.appendWithSpaces(result, subtreeResult);
    }
  }

  /**
   * Adds {@code node} as a span on {@code content} if not already fully covered by an accessibility
   * node info span.
   */
  private void addNodeSpanForUncovered(AccessibilityNodeInfoCompat node, Spannable spannable) {
    AccessibilityNodeInfoCompat[] spans =
        spannable.getSpans(0, spannable.length(), AccessibilityNodeInfoCompat.class);
    for (AccessibilityNodeInfoCompat span : spans) {
      if (spannable.getSpanStart(span) == 0 && spannable.getSpanEnd(span) == spannable.length()) {
        return;
      }
    }
    DisplaySpans.setAccessibilityNode(spannable, node);
  }

  /**
   * Finds the display extent for {@code node}, putting the result in {@code outLeft} and {@code
   * outRight}. The display extent is the first and last node that will contribute to the content of
   * the display when {@code node} has accessibility focus.
   */
  public void findDisplayExtentFromNode(
      AccessibilityNodeInfoCompat node,
      AccessibilityNodeInfoRef outLeft,
      AccessibilityNodeInfoRef outRight) {
    Rect nodeRect = new Rect();
    node.getBoundsInScreen(nodeRect);
    AccessibilityNodeInfoRef current = AccessibilityNodeInfoRef.unOwned(node);
    boolean currentIncludesChildren = includesChildren(current.get());
    // Walk up the node tree as long as all siblings overlap
    // the original node vertically on screen.
    do {
      if (currentIncludesChildren) {
        if (!findOverlappingRange(current.get(), nodeRect, outLeft, outRight)) {
          break;
        }
      } else {
        // If the start node doesn't include its children, don't
        // include any adjacent nodes, since that causes line-by-line
        // navigation to be confusing and get stuck in a cycle.
        outLeft.reset(current);
        outRight.reset(current);
        break;
      }
      if (!current.parent()) {
        break;
      }
      // TODO: why not format bottom-up and avoid having to lookup the
      // rule twice?  Consider that at some point.
      currentIncludesChildren = includesChildren(current.get());
      // Don't let any parent prevent us from getting onto the
      // display, *we* contain the focused node.
    } while (currentIncludesChildren);
  }

  /**
   * Finds the list of nodes that should be formatted and put on the braille display together with
   * {@code node}. The result, including a copy of {@code node} itself, is appended to {@code
   * outResult}.
   */
  private void findNodesToFormat(
      AccessibilityNodeInfoCompat node, ArrayList<AccessibilityNodeInfoCompat> outResult) {
    AccessibilityNodeInfoRef left = new AccessibilityNodeInfoRef();
    AccessibilityNodeInfoRef right = new AccessibilityNodeInfoRef();
    findDisplayExtentFromNode(node, left, right);
    do {
      outResult.add(left.makeOwned().release());
    } while (!left.get().equals(right.get()) && left.nextSibling());
  }

  /**
   * Finds the range of siblings adjacent to {@code startFrom} that overlap {@code rect} vertically.
   * Sets {@code outLeft} and {@code outRight} to the inclusive range that overlaps. If there are no
   * overlapping siblings in one of the direction, the corresponding parameter will be set to {@code
   * startFrom}. Returns {@code true} if the range covers all siblings of {@code startFrom}, {@code
   * false} otherwise.
   */
  private boolean findOverlappingRange(
      AccessibilityNodeInfoCompat startFrom,
      Rect rect,
      AccessibilityNodeInfoRef outLeft,
      AccessibilityNodeInfoRef outRight) {
    Rect tmpRect = new Rect();
    boolean ret = true;
    AccessibilityNodeInfoRef left = AccessibilityNodeInfoRef.unOwned(startFrom);
    outLeft.reset(left);
    while (left.previousSibling()) {
      left.get().getBoundsInScreen(tmpRect);
      if (!includesChildren(left.get()) || !shouldPutOnSameLine(tmpRect, rect)) {
        ret = false;
        break;
      }
      outLeft.reset(left);
    }
    AccessibilityNodeInfoRef right = AccessibilityNodeInfoRef.unOwned(startFrom);
    outRight.reset(right);
    while (right.nextSibling()) {
      right.get().getBoundsInScreen(tmpRect);
      if (!includesChildren(right.get()) || !shouldPutOnSameLine(tmpRect, rect)) {
        ret = false;
        break;
      }
      outRight.reset(right);
    }
    outLeft.makeOwned();
    outRight.makeOwned();
    left.recycle();
    right.recycle();
    return ret;
  }

  /**
   * Returns {@code true} if the two rectangles should be considered to be on the same 'line' for
   * the purposes of putting horizontally adjacent content on the braille display at the same time.
   */
  private static boolean shouldPutOnSameLine(Rect a, Rect b) {
    boolean ret = (a.top < b.bottom && b.top < a.bottom);
    if (ret) {
      int aHeight = a.bottom - a.top;
      int bHeight = b.bottom - b.top;
      if (aHeight > HEIGHT_DIFF_THRESHOLD * bHeight || bHeight > HEIGHT_DIFF_THRESHOLD * aHeight) {
        ret = false;
      }
    }
    return ret;
  }

  private boolean includesChildren(AccessibilityNodeInfoCompat node) {
    BrailleRule rule = mRuleRepository.find(node);
    return rule.includeChildren(node, mContext);
  }
}

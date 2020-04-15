/*
 * Copyright (C) 2015 Google Inc.
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

package com.googlecode.eyesfree.brailleback.wrapping;

import android.util.SparseIntArray;
import com.googlecode.eyesfree.braille.translate.TranslationResult;
import com.googlecode.eyesfree.brailleback.DisplayManager.Content;

/**
 * Handles the presentation of braille content that doesn't completely fit on the braille display.
 */
public abstract class WrapStrategy {

  /**
   * Indicates a position in braille that marks an acceptable place to start a new line, while
   * indicating that the cell at the given position should not be removed. For example, you can mark
   * the first braille position after a hyphen as an unremovable break point, since you wouldn't
   * want it to be truncated.
   *
   * <p><em>Note:</em> In the current line-breaking algorithm, there is an implicit unremovable
   * break point placed on the first braille position after a contiguous string of removable break
   * points (i.e. it is acceptable to break right before the first character after a string of
   * removable break points in order to elide the removable break points). It is acceptable, but not
   * necessary, to explicitly mark the first braille position after a string of removable break
   * points as an unremovable break point.
   */
  protected static final int UNREMOVABLE_BREAK_POINT = 1;

  /**
   * Indicates a position in braille that marks an acceptable place to start a new line, while
   * indicating that the cell at the given position can be removed if it occurs at the beginning or
   * end of a line. For example, you can mark whitespace as a removable break point because it is
   * acceptable to elide whitespace in most contexts.
   *
   * <p><em>Note:</em> The definition above implies that a line break might also be placed
   * <em>after</em> a removable break point, e.g. in this case with a display width of 12 cells:
   *
   * <pre>
   *   Input text:   Peter Piper picked
   *   Break pts:    -----R-----R------
   *   Display 1/2: [Peter Piper ]
   *   Display 2/2:             [picked%%%%%%]
   * </pre>
   *
   * In this case, the line break will occur after the second removable break point and before the
   * word "picked" in order to remove the space from the beginning of the line.
   */
  protected static final int REMOVABLE_BREAK_POINT = 2;

  /**
   * Used in {@link #mSplitPoints} to indicate a position in braille where a forced line break must
   * occur, e.g. due to a newline or paragraph break.
   */
  protected static final int SPLIT_POINT = 1;

  /**
   * Used in the computed array {@link #mLineBreaks} to indicate the positions in braille where
   * lines begin.
   */
  protected static final int LINE_BREAK = 1;

  private int mDisplayStart;
  private int mDisplayEnd;
  private int mDisplayWidth;

  private boolean mIsValid = false;

  protected Content mContent;
  protected TranslationResult mTranslation;

  /**
   * An array where the keys are translated positions that should always correspond to the left-most
   * position on the braille display if at all inclded. This is used to split the output at line
   * breaks. The values are not used and currently set to {@link #SPLIT_POINT}.
   */
  protected SparseIntArray mSplitPoints = new SparseIntArray();

  /**
   * An array where the keys are positions in braille at which we prefer new lines to begin. The
   * values are either {@link #UNREMOVABLE_BREAK_POINT} or {@link #REMOVABLE_BREAK_POINT}.
   */
  protected SparseIntArray mBreakPoints = new SparseIntArray();

  /**
   * The precomputed line breaks that are used during panning. Positions marked with {@link
   * #LINE_BREAK} mark the beginning of a line; thus the line break occurs right before such
   * positions. The offset from one line break to another is not necessarily less than or equal to
   * the width of the display; the line breaking algorithm may arrange the line breaks such that
   * removable break points are truncated at the end of a line.
   */
  private SparseIntArray mLineBreaks = new SparseIntArray();

  /**
   * Sets the current content context for the wrap strategy to calculate panning positions. If any
   * of these parameters change, you must call {@link #setContent} again; otherwise, the panning
   * calculations done in methods like {@link #panTo} or {@link #panLeft} will be incorrect.
   *
   * @param content The untranslated text content.
   * @param translation The translated braille content.
   * @param displayWidth The number of cells available on the display.
   */
  public void setContent(Content content, TranslationResult translation, int displayWidth) {
    if (content == null
        || translation == null
        || displayWidth <= 0
        || content.getText().length() == 0) {
      mContent = null;
      mTranslation = null;

      mDisplayStart = 0;
      mDisplayEnd = 0;
      mDisplayWidth = 0;

      mSplitPoints.clear();
      mBreakPoints.clear();
      mLineBreaks.clear();

      mIsValid = false;
      return;
    }

    mContent = content;
    mTranslation = translation;

    mDisplayStart = 0;
    mDisplayEnd = 0;
    mDisplayWidth = displayWidth;

    mSplitPoints.clear();
    calculateSplitPoints();

    mBreakPoints.clear();
    calculateBreakPoints();

    mLineBreaks.clear();
    calculateLineBreaks(0);

    mIsValid = true;
  }

  protected void calculateSplitPoints() {
    if (!mContent.isSplitParagraphs()) {
      return;
    }

    CharSequence text = mContent.getText();
    int[] textToCell = mTranslation.getTextToBraillePositions();
    int numCells = mTranslation.getCells().length;
    for (int i = 0; i < text.length() - 1; ++i) {
      if (text.charAt(i) == '\n') {
        int cell = (i + 1 < textToCell.length) ? textToCell[i + 1] : numCells;
        mSplitPoints.append(cell, SPLIT_POINT);
      }
    }
  }

  protected abstract void calculateBreakPoints();

  private int findPointIndex(SparseIntArray points, int displayPosition) {
    int index = points.indexOfKey(displayPosition);
    if (index >= 0) {
      // Exact match.
      return index;
    }
    // One's complement gives index where the element would be inserted
    // in sorted order.
    index = ~index;
    if (index > 0) {
      return index - 1;
    }
    return -1;
  }

  private int findLeftLimit(SparseIntArray points, int end) {
    int index = findPointIndex(points, end);
    if (index >= 0) {
      int limit = points.keyAt(index);
      if (limit < end) {
        return limit;
      }
      if (index > 0) {
        return points.keyAt(index - 1);
      }
    }
    return 0;
  }

  private int findRightLimit(SparseIntArray points, int start) {
    int index = findPointIndex(points, start) + 1;
    if (index >= points.size()) {
      return mTranslation.getCells().length;
    }
    return points.keyAt(index);
  }

  public int getDisplayStart() {
    return Math.max(0, mDisplayStart);
  }

  public int getDisplayEnd() {
    return Math.min(getDisplayStart() + mDisplayWidth, mDisplayEnd);
  }

  /**
   * Calculates the line breaks for the current content using the current pivot point. The line
   * breaks will occur such that the pivot point is guaranteed to be at the beginning of some line.
   */
  private void calculateLineBreaks(int pivot) {
    // Add pivot as line break.
    mLineBreaks.append(pivot, LINE_BREAK);

    // Add line breaks after pivot.
    int current = pivot;
    while (current < mTranslation.getCells().length) {
      current = calculateDisplayEnd(current);
      mLineBreaks.append(current, LINE_BREAK);
    }

    // Add line breaks before pivot.
    current = pivot;
    while (current > 0) {
      current = calculateDisplayStart(current);
      mLineBreaks.append(current, LINE_BREAK);
    }
  }

  /** Clamps the given position to the interval [0, length). */
  private int clampPosition(int position) {
    if (position <= 0) {
      return 0;
    } else if (position >= mTranslation.getCells().length - 1) {
      return mTranslation.getCells().length - 1;
    } else {
      return position;
    }
  }

  /**
   * Pans the display to the indicated position.
   *
   * @param position The position (in braille cells) to which to pan.
   * @param fix If {@code true}, then the display will pan to {@code position} as if the user had
   *     manually panned there. If {@code false}, then the display will pan such that {@code
   *     position} becomes the leftmost cell on the display.
   */
  public void panTo(int position, boolean fix) {
    if (!mIsValid) {
      return;
    }

    position = clampPosition(position);

    // If the position isn't one of the cached line breaks and we can't fix
    // the position, then we will need to recalculate all the line breaks
    // with the position as pivot.
    if (!fix && mLineBreaks.indexOfKey(position) < 0) {
      mLineBreaks.clear();
      calculateLineBreaks(position);
    }

    int index = findPointIndex(mLineBreaks, position);
    if (index >= mLineBreaks.size() - 1) {
      // We need index, index + 1 to be valid.
      return;
    }

    mDisplayStart = mLineBreaks.keyAt(index);
    mDisplayEnd = mLineBreaks.keyAt(index + 1);
  }

  /**
   * Moves the display starting and ending positions to the left of the current content.
   *
   * @return {@code true} if the display was panned, or {@code false} if it's at the left edge.
   */
  public boolean panLeft() {
    if (!mIsValid) {
      return false;
    }

    int index = mLineBreaks.indexOfKey(mDisplayStart);
    if (index <= 0 || index >= mLineBreaks.size()) {
      return false;
    }

    mDisplayStart = mLineBreaks.keyAt(index - 1);
    mDisplayEnd = mLineBreaks.keyAt(index);
    return true;
  }

  /**
   * Moves the display starting and ending positions to the right of the current content.
   *
   * @return {@code true} if the display was panned, or {@code false} if it's at the right edge.
   */
  public boolean panRight() {
    if (!mIsValid) {
      return false;
    }

    int index = mLineBreaks.indexOfKey(mDisplayEnd);
    if (index < 0 || index >= mLineBreaks.size() - 1) {
      return false;
    }

    mDisplayStart = mLineBreaks.keyAt(index);
    mDisplayEnd = mLineBreaks.keyAt(index + 1);
    return true;
  }

  private int calculateDisplayEnd(int start) {
    int displayLimit = start + mDisplayWidth;

    int splitLimit = findRightLimit(mSplitPoints, start);
    if (splitLimit <= displayLimit) {
      return splitLimit;
    }

    int breakLimit = findLeftLimit(mBreakPoints, displayLimit + 1);
    if (breakLimit > start) {
      // Extend the breakLimit until it reaches a character that is not a
      // removable break (whitespace padding is OK at end).
      while (breakLimit < mTranslation.getCells().length
          && mBreakPoints.get(breakLimit) == REMOVABLE_BREAK_POINT) {
        breakLimit++;
      }
      return breakLimit;
    }

    return displayLimit;
  }

  private int calculateDisplayStart(int end) {
    // Move end backwards until the character immediately preceding it is
    // not a removable break. In effect, we're "cancelling out" any
    // whitespace padding that occurred at the end.
    while (end > 0 && mBreakPoints.get(end - 1) == REMOVABLE_BREAK_POINT) {
      end--;
    }
    int displayLimit = end - mDisplayWidth;

    int splitLimit = findLeftLimit(mSplitPoints, end);
    if (splitLimit >= displayLimit) {
      return splitLimit;
    }

    int breakLimit = findRightLimit(mBreakPoints, displayLimit - 1);
    if (breakLimit < end) {
      // Extend the breakLimit until it reaches a character that is not a
      // removable break (whitespace padding is not OK at the start).
      while (breakLimit < end && mBreakPoints.get(breakLimit) == REMOVABLE_BREAK_POINT) {
        breakLimit++;
      }
      return breakLimit;
    }

    return displayLimit;
  }
}

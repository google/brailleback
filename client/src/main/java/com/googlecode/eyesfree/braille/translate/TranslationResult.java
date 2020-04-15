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

package com.googlecode.eyesfree.braille.translate;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * The result of translating text to braille, including character to cell mappings in both
 * directions.
 */
public class TranslationResult implements Parcelable {
  private byte[] mCells;
  private int[] mTextToBraillePositions;
  private int[] mBrailleToTextPositions;
  private int mCursorPosition;

  public TranslationResult(
      byte[] cells,
      int[] textToBraillePositions,
      int[] brailleToTextPositions,
      int cursorPosition) {
    mCells = cells;
    mTextToBraillePositions = textToBraillePositions;
    mBrailleToTextPositions = brailleToTextPositions;
    mCursorPosition = cursorPosition;
  }

  /** Returns the braille cells corresponding to the original text. */
  public byte[] getCells() {
    return mCells;
  }

  /** Maps a position in the original text to the corresponding position in the braille cells. */
  public int[] getTextToBraillePositions() {
    return mTextToBraillePositions;
  }

  /** Maps a position in the braille cells to the corresponding position in the original text. */
  public int[] getBrailleToTextPositions() {
    return mBrailleToTextPositions;
  }

  /**
   * Returns the cursor position corresponding to the cursor position specified when translating the
   * text, or -1, if there was no cursor position specified.
   */
  public int getCursorPosition() {
    return mCursorPosition;
  }

  // For Parcelable support.

  public static final Parcelable.Creator<TranslationResult> CREATOR =
      new Parcelable.Creator<TranslationResult>() {
        @Override
        public TranslationResult createFromParcel(Parcel in) {
          return new TranslationResult(in);
        }

        @Override
        public TranslationResult[] newArray(int size) {
          return new TranslationResult[size];
        }
      };

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel out, int flags) {
    out.writeByteArray(mCells);
    out.writeIntArray(mTextToBraillePositions);
    out.writeIntArray(mBrailleToTextPositions);
    out.writeInt(mCursorPosition);
  }

  private TranslationResult(Parcel in) {
    mCells = in.createByteArray();
    mTextToBraillePositions = in.createIntArray();
    mBrailleToTextPositions = in.createIntArray();
    mCursorPosition = in.readInt();
  }
}

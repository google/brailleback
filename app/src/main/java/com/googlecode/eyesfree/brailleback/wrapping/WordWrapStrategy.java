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

/**
 * A wrapping strategy where lines can break wherever there is an empty braille cell. Empty braille
 * cells are marked as "removable" break points, so they may be elided if they occur at the end of
 * the display line.
 */
public class WordWrapStrategy extends WrapStrategy {

  @Override
  protected void calculateBreakPoints() {
    byte[] cells = mTranslation.getCells();
    boolean lastCellEmpty = false;
    for (int i = 0; i < cells.length; ++i) {
      boolean currentCellEmpty = (cells[i] == 0);
      if (currentCellEmpty) {
        mBreakPoints.append(i, REMOVABLE_BREAK_POINT);
      } else if (lastCellEmpty) {
        mBreakPoints.append(i, UNREMOVABLE_BREAK_POINT);
      }
      lastCellEmpty = currentCellEmpty;
    }
  }
}

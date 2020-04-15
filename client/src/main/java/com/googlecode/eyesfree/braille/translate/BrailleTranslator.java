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

/**
 * Translates from text to braille and the other way according to a particular translation table.
 */
public interface BrailleTranslator {
  /**
   * Translates a string into the corresponding dot patterns and returns the resulting byte array.
   * Returns {@code null} on error. {@code cursorPosition}, if positive, will be mapped to the
   * corresponding position in the output. This is sometimes more accurate than the position maps in
   * the {@link TranslationResult}. If {@code computerBrailleAtCursor} is set, then the word
   * underneath the cursor will be expanded into computer braille (if using a literary braille
   * table).
   */
  TranslationResult translate(String text, int cursorPosition, boolean computerBrailleAtCursor);

  /**
   * Convenience overload for calling {@link #translate(String, int, boolean)} without expansion of
   * computer braille at the cursor.
   */
  TranslationResult translate(String text, int cursorPosition);

  /**
   * Translates the braille {@code cells} into the corresponding text, which is returned. Returns
   * {@code null} on error.
   */
  String backTranslate(byte[] cells);

  /** Returns information about the braille table used for translation. */
  TableInfo getTableInfo();
}

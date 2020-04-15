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

package com.googlecode.eyesfree.braille.service.translate;

import android.util.Log;
import com.googlecode.eyesfree.braille.translate.TranslationResult;

/**
 * Wraps the liblouis functions to translate to and from braille.
 *
 * <p>NOTE: Braille translation involves reading tables from disk and can therefore be blocking. In
 * addition, translation by all instances of this class is serialized because of the underlying
 * implementation, which increases the possibility of translations blocking on I/O if multiple
 * translators are used.
 */
public class LibLouisWrapper {
  private static final String LOG_TAG = LibLouisWrapper.class.getSimpleName();

  /**
   * This method should be called before any other method is called. {@code path} should point to a
   * location in the file system under which the liblouis translation tables can be found.
   */
  public static void setTablesDir(String path) {
    synchronized (LibLouisWrapper.class) {
      setTablesDirNative(path);
    }
  }

  /** Compiles the given table and makes sure it is valid. */
  public static boolean checkTable(String tableName) {
    synchronized (LibLouisWrapper.class) {
      if (!checkTableNative(tableName)) {
        Log.w(LOG_TAG, "Table not found or invalid: " + tableName);
        return false;
      }
      return true;
    }
  }

  /**
   * Translates a string into the corresponding dot patterns and returns the resulting byte array.
   */
  public static TranslationResult translate(
      String text, String tableName, int cursorPosition, boolean computerBrailleAtCursor) {
    synchronized (LibLouisWrapper.class) {
      return translateNative(text, tableName, cursorPosition, computerBrailleAtCursor);
    }
  }

  public static String backTranslate(byte[] cells, String tableName) {
    synchronized (LibLouisWrapper.class) {
      return backTranslateNative(cells, tableName);
    }
  }

  // Native methods.  Since liblouis is neither reentrant, nor
  // thread-safe, all native methods are called inside synchronized
  // blocks on the class object, allowing multiple translators
  // to exist.

  private static native TranslationResult translateNative(
      String text, String tableName, int cursorPosition, boolean computerBrailleAtCursor);

  private static native String backTranslateNative(byte[] dotPatterns, String tableName);

  private static native boolean checkTableNative(String tableName);

  private static native void setTablesDirNative(String path);

  private static native void classInitNative();

  static {
    System.loadLibrary("louiswrap");
    classInitNative();
  }
}

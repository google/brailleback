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

package com.googlecode.eyesfree.braille.utils;

import android.content.Context;
import android.os.AsyncTask;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extracts the contents of a raw resource, stored as a zip file, into a directory specified by the
 * caller.
 *
 * <p>Instantiate your own subclass of this on the maina pplication thread and override {@link
 * #onPostExecute} to be notified when the extraction is done.
 */
// TODO: Add versioning support.
public class ZipResourceExtractor extends AsyncTask<Void, Void, Integer> {
  public static final int RESULT_ERROR = -1;
  public static final int RESULT_OK = 0;

  private final LinkedList<File> mExtractedFiles = new LinkedList<File>();

  private final Context mContext;
  private final int mRawResId;
  private final File mOutput;

  public ZipResourceExtractor(Context context, int rawResId, File output) {
    mContext = context;
    mRawResId = rawResId;
    mOutput = output;
  }

  @Override
  protected Integer doInBackground(Void... params) {
    final InputStream stream = mContext.getResources().openRawResource(mRawResId);
    final ZipInputStream zipStream = new ZipInputStream(new BufferedInputStream(stream));

    boolean successful = false;

    try {
      extractEntries(zipStream);
      successful = true;
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      try {
        zipStream.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    if (isCancelled() || !successful) {
      removeExtractedFiles();
      return RESULT_ERROR;
    }

    return RESULT_OK;
  }

  private void extractEntries(ZipInputStream zipStream) throws IOException {
    final byte[] buffer = new byte[10240];

    int bytesRead;
    ZipEntry entry;

    while (!isCancelled() && ((entry = zipStream.getNextEntry()) != null)) {
      final File outputFile = new File(mOutput, entry.getName());

      mExtractedFiles.add(outputFile);

      if (entry.isDirectory()) {
        outputFile.mkdirs();
        makeReadable(outputFile);
        continue;
      }

      // Ensure the target path exists.
      outputFile.getParentFile().mkdirs();

      final FileOutputStream outputStream = new FileOutputStream(outputFile);

      while (!isCancelled() && (bytesRead = zipStream.read(buffer)) != -1) {
        outputStream.write(buffer, 0, bytesRead);
      }

      outputStream.close();
      zipStream.closeEntry();

      // Make sure the output file is readable.
      makeReadable(outputFile);
    }
  }

  private void removeExtractedFiles() {
    for (File extractedFile : mExtractedFiles) {
      if (!extractedFile.isDirectory()) {
        extractedFile.delete();
      }
    }

    mExtractedFiles.clear();
  }

  private static void makeReadable(File file) {
    if (!file.canRead()) {
      file.setReadable(true);
    }
  }

  /** Removes children in {@code directory}, but does not delete the directory itself. */
  private static void clearDirectory(File directory) {
    if (!directory.exists() || !directory.isDirectory()) {
      return;
    }

    final File[] children = directory.listFiles();

    for (File child : children) {
      if (child.isDirectory()) {
        clearDirectory(child);
      }

      child.delete();
    }
  }
}

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

#include <assert.h>
#include <stdlib.h>
#include <string.h>
#include <jni.h>
#include "alog.h"
#include "liblouis/liblouis.h"
#include "liblouis/internal.h"  // for MAXSTRING

#define LOG_TAG "LibLouisWrapper_Native"

#define TRANSLATE_PACKAGE "com/googlecode/eyesfree/braille/translate/"

#define MAX(a, b) ((a) > (b) ? (a) : (b))

static jclass class_TranslationResult;
static jmethodID method_TranslationResult_ctor;
static jclass class_OutOfMemoryError;

static jclass getGlobalClassRef(JNIEnv* env, const char* name);

jboolean
Java_com_googlecode_eyesfree_braille_service_translate_LibLouisWrapper_checkTableNative
    (JNIEnv* env, jclass clazz, jstring tableName) {
  jboolean ret = JNI_FALSE;
  const jbyte* tableNameUtf8 = (*env)->GetStringUTFChars(env, tableName, NULL);
  if (lou_getTable(tableNameUtf8) == NULL) {
    goto out;
  }
  ret = JNI_TRUE;
  out:
  (*env)->ReleaseStringUTFChars(env, tableName, tableNameUtf8);
  return ret;
}

jobject
Java_com_googlecode_eyesfree_braille_service_translate_LibLouisWrapper_translateNative
    (JNIEnv* env, jclass clazz, jstring text, jstring tableName,
     jint cursorPosition, jboolean computerBrailleAtCursor) {
  jobject ret = NULL;
  const jchar* textUtf16 = (*env)->GetStringChars(env, text, NULL);
  const jbyte* tableNameUtf8 = (*env)->GetStringUTFChars(env, tableName, NULL);

  int inlen = (*env)->GetStringLength(env, text);
  int* outputpos = malloc(sizeof(int) * inlen); // Maps char -> cell pos.

  int cursoroutpos;
  int* cursorposp = NULL;
  if (cursorPosition < 0) {
    cursoroutpos = -1;
  } else if (cursorPosition < inlen) {
    cursoroutpos = cursorPosition;
    cursorposp = &cursoroutpos;
  }

  // See <https://crrev.com/243251> for equivalent ChromeVox implementation.
  // Invoke liblouis.  Do this in a loop since we can't precalculate the
  // translated size.  We start with the min allocation size (8 jchars or 16
  // bytes); for a larger input length, we start at double the input length.
  // We also set an arbitrary upper bound for the allocation to make sure the
  // loop exits without running out of memory. For non-small input lengths, the
  // loop runs up to 4 times (inlen * 2, inlen * 4, inlen * 8, inlen * 16).
  int inused = 0;
  int outused = 0;
  jchar* outbuf = NULL;
  int* inputpos = NULL; // The oposite of outputpos: maps cell -> char pos.
  // Min buffer size is 8 jchars or 16 bytes.
  // For non-small values of inlen, the loop repeats up to 4 times (inlen * 2,
  // inlen * 4, inlen * 8, inlen * 16).
  for (int outlen = MAX(8, inlen * 2), maxoutlen = inlen * 16;
       outlen <= maxoutlen;
       outlen *= 2) {
    inused = inlen;
    outused = outlen;

    outbuf = realloc(outbuf, sizeof(jchar) * outlen);
    inputpos = realloc(inputpos, sizeof(int) * outlen);
    if (outbuf == NULL || outputpos == NULL || inputpos == NULL) {
      (*env)->ThrowNew(env, class_OutOfMemoryError, NULL);
    }

    int result = lou_translate(tableNameUtf8, textUtf16, &inused,
                               outbuf, &outused,
                               NULL /*typeform*/, NULL /*spacing*/,
                               outputpos, inputpos, cursorposp,
                               computerBrailleAtCursor ? compbrlAtCursor
                                   | dotsIO : dotsIO);
    if (result == 0) {
      LOGE("Translation failed.");
      goto freebufs;
    }

    // If all of inbuf was not consumed, the output buffer must be too small
    // and we have to retry with a larger buffer.
    // In addition, if all of outbuf was exhausted, there's no way to know if
    // more space was needed, so we'll have to retry the translation in that
    // corner case as well.
    if (inused == inlen && outused < outlen) {
      break;
    }
  }
  LOGV("Successfully translated %d characters to %d cells, "
       "consuming %d characters", (*env)->GetStringLength(env, text),
       outused, inused);
  jbyteArray cellsarray = (*env)->NewByteArray(env, outused);
  if (cellsarray == NULL) {
    goto freebufs;
  }
  jbyte* cells = (*env)->GetByteArrayElements(env, cellsarray, NULL);
  if (cells == NULL) {
    goto freebufs;
  }
  int i;
  for (i = 0; i < outused; ++i) {
    cells[i] = outbuf[i] & 0xff;
  }
  (*env)->ReleaseByteArrayElements(env, cellsarray, cells, 0);
  jintArray outputposarray = (*env)->NewIntArray(env, inlen);
  if (outputposarray == NULL) {
    goto freebufs;
  }
  (*env)->SetIntArrayRegion(env, outputposarray, 0, inlen, outputpos);
  jintArray inputposarray = (*env)->NewIntArray(env, outused);
  if (inputposarray == NULL) {
    goto freebufs;
  }
  (*env)->SetIntArrayRegion(env, inputposarray, 0, outused, inputpos);
  if (cursorposp == NULL && cursorPosition >= 0) {
    // The cursor position was past-the-end of the input, normalize to
    // past-the-end of the output.
    cursoroutpos = outused;
  }
  ret = (*env)->NewObject(
      env, class_TranslationResult, method_TranslationResult_ctor,
      cellsarray, outputposarray, inputposarray, cursoroutpos);

  freebufs:
  free(outbuf);
  free(inputpos);
  free(outputpos);
  (*env)->ReleaseStringChars(env, text, textUtf16);
  (*env)->ReleaseStringUTFChars(env, tableName, tableNameUtf8);
  return ret;
}

jstring
Java_com_googlecode_eyesfree_braille_service_translate_LibLouisWrapper_backTranslateNative
    (JNIEnv* env, jclass clazz, jbyteArray cells, jstring tableName) {
  jstring ret = NULL;
  const jbyte* tableNameUtf8 = (*env)->GetStringUTFChars(env, tableName, NULL);
  if (!tableNameUtf8) {
    goto out;
  }

  int inlen = (*env)->GetArrayLength(env, cells);
  jbyte* cellsBytes = (*env)->GetByteArrayElements(env, cells, NULL);
  widechar* inbuf = malloc(sizeof(widechar) * inlen);
  int i;
  for (i = 0; i < inlen; ++i) {
    // Cast to avoid sign extension.
    inbuf[i] = ((unsigned char) cellsBytes[i]) | 0x8000;
  }
  (*env)->ReleaseByteArrayElements(env, cells, cellsBytes, JNI_ABORT);

  // See <https://crrev.com/254023> for equivalent ChromeVox implementation.
  // Invoke liblouis.  Do this in a loop since we can't precalculate the
  // translated size.  We start with the min allocation size (8 jchars or 16
  // bytes); for a larger input length, we start at double the input length.
  // We also set an arbitrary upper bound for the allocation to make sure the
  // loop exits without running out of memory. For non-small input lengths, the
  // loop runs up to 4 times (inlen * 2, inlen * 4, inlen * 8, inlen * 16).
  int inused = 0;
  int outused = 0;
  jchar* outbuf = NULL;
  for (int outlen = MAX(8, inlen * 2), maxoutlen = inlen * 16;
       outlen <= maxoutlen;
       outlen *= 2) {
    inused = inlen;
    outused = outlen;

    outbuf = realloc(outbuf, sizeof(jchar) * outlen);
    if (outbuf == NULL) {
      (*env)->ThrowNew(env, class_OutOfMemoryError, NULL);
    }

    int result = lou_backTranslateString(tableNameUtf8,
                                         inbuf,
                                         &inused,
                                         outbuf,
                                         &outused,
                                         NULL /*typeform*/,
                                         NULL /*spacing*/,
                                         dotsIO);
    if (result == 0) {
      LOGE("Back translation failed.");
      goto freebufs;
    }

    // If all of inbuf was not consumed, the output buffer must be too small
    // and we have to retry with a larger buffer.
    // In addition, if all of outbuf was exhausted, there's no way to know if
    // more space was needed, so we'll have to retry the translation in that
    // corner case as well.
    // Example: 0x1f -> "quite"; we initially allocate space for 4 chars, but
    // we need 5. After lou_backTranslateString, inused = 1 and outused = 4.
    // So it appears that the translation finished, but we're missing a char.
    if (inused == inlen && outused < outlen) {
      break;
    }
  }
  LOGV("Successfully translated %d cells into %d characters, "
       "consuming %d cells", (*env)->GetArrayLength(env, cells),
       outused, inused);
  ret = (*env)->NewString(env, outbuf, outused);
  freebufs:
  free(inbuf);
  free(outbuf);
  (*env)->ReleaseStringUTFChars(env, tableName, tableNameUtf8);
  out:
  return ret;
}

void
Java_com_googlecode_eyesfree_braille_service_translate_LibLouisWrapper_setTablesDirNative
    (JNIEnv* env, jclass clazz, jstring path) {
  // liblouis has a static buffer, which we don't want to overflow.
  if ((*env)->GetStringUTFLength(env, path) >= MAXSTRING) {
    LOGE("Braille table path too long");
    return;
  }
  const jbyte* pathUtf8 = (*env)->GetStringUTFChars(env, path, NULL);
  if (!pathUtf8) {
    return;
  }
  // The path gets copied.
  // Cast needed to get rid of const.
  LOGV("Setting tables path to: %s", pathUtf8);
  lou_setDataPath((char*) pathUtf8);
  (*env)->ReleaseStringUTFChars(env, path, pathUtf8);
}

void
Java_com_googlecode_eyesfree_braille_service_translate_LibLouisWrapper_classInitNative(
    JNIEnv* env, jclass clazz) {
  if (!(class_TranslationResult = getGlobalClassRef(env,
                                                    TRANSLATE_PACKAGE "TranslationResult"))) {
    return;
  }
  if (!(method_TranslationResult_ctor = (*env)->GetMethodID(
      env, class_TranslationResult, "<init>", "([B[I[II)V"))) {
    return;
  }
  if (!(class_OutOfMemoryError =
            getGlobalClassRef(env, "java/lang/OutOfMemoryError"))) {
    return;
  }
}

static jclass
getGlobalClassRef(JNIEnv* env, const char* name) {
  jclass localRef = (*env)->FindClass(env, name);
  if (!localRef) {
    LOGE("Couldn't find class %s", name);
    return NULL;
  }
  jclass globalRef = (*env)->NewGlobalRef(env, localRef);
  if (globalRef == NULL) {
    LOGE("Couldn't create global ref for class %s", name);
  }
  return globalRef;
}

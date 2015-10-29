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
#include "liblouis/louis.h"  // for MAXSTRING

#define LOG_TAG "LibLouisWrapper_Native"

#define TRANSLATE_PACKAGE "com/googlecode/eyesfree/braille/translate/"

static jclass class_TranslationResult;
static jmethodID method_TranslationResult_ctor;
static jclass class_OutOfMemoryError;

static jclass getGlobalClassRef(JNIEnv* env, const char *name);

jboolean
Java_com_googlecode_eyesfree_braille_service_translate_LibLouisWrapper_checkTableNative
(JNIEnv* env, jclass clazz, jstring tableName) {
  jboolean ret = JNI_FALSE;
  const jbyte *tableNameUtf8 = (*env)->GetStringUTFChars(env, tableName, NULL);
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
 int cursorPosition) {
  jobject ret = NULL;
  const jchar* textUtf16 = (*env)->GetStringChars(env, text, NULL);
  const jbyte* tableNameUtf8 = (*env)->GetStringUTFChars(env, tableName, NULL);
  int inlen = (*env)->GetStringLength(env, text);
  // TODO: Need to do this in a buffer like usual character encoding
  // translations, but for now we assume that double size is good enough.
  int outlen = inlen * 2;
  jchar* outbuf = malloc(sizeof(jchar) * outlen);
  // Maps character position to braille cell position.
  int* outputpos = malloc(sizeof(int) * inlen);
  // The oposite of outputpos: maps braille cell position to
  // character position.
  int* inputpos = malloc(sizeof(int) * outlen);
  if (outbuf == NULL || outputpos == NULL || inputpos == NULL) {
    (*env)->ThrowNew(env, class_OutOfMemoryError, NULL);
  }
  int cursoroutpos;
  int* cursorposp = NULL;
  if (cursorPosition < 0) {
    cursoroutpos = -1;
  } else if (cursorPosition < inlen) {
    cursoroutpos = cursorPosition;
    cursorposp = &cursoroutpos;
  }
  int result = lou_translate(tableNameUtf8, textUtf16, &inlen,
                             outbuf, &outlen,
                             NULL/*typeform*/, NULL/*spacing*/,
                             outputpos, inputpos, cursorposp,
                             dotsIO/*mode*/);
  if (result == 0) {
    LOGE("Translation failed.");
    goto freebufs;
  }
  LOGV("Successfully translated %d characters to %d cells, "
       "consuming %d characters", (*env)->GetStringLength(env, text),
       outlen, inlen);
  jbyteArray cellsarray = (*env)->NewByteArray(env, outlen);
  if (cellsarray == NULL) {
    goto freebufs;
  }
  jbyte* cells = (*env)->GetByteArrayElements(env, cellsarray, NULL);
  if (cells == NULL) {
    goto freebufs;
  }
  int i;
  for (i = 0; i < outlen; ++i) {
    cells[i] = outbuf[i] & 0xff;
  }
  (*env)->ReleaseByteArrayElements(env, cellsarray, cells, 0);
  jintArray outputposarray = (*env)->NewIntArray(env, inlen);
  if (outputposarray == NULL) {
    goto freebufs;
  }
  (*env)->SetIntArrayRegion(env, outputposarray, 0, inlen, outputpos);
  jintArray inputposarray = (*env)->NewIntArray(env, outlen);
  if (inputposarray == NULL) {
    goto freebufs;
  }
  (*env)->SetIntArrayRegion(env, inputposarray, 0, outlen, inputpos);
  if (cursorposp == NULL && cursorPosition >= 0) {
    // The cursor position was past-the-end of the input, normalize to
    // past-the-end of the output.
    cursoroutpos = outlen;
  }
  ret = (*env)->NewObject(
      env, class_TranslationResult, method_TranslationResult_ctor,
      cellsarray, outputposarray, inputposarray, cursoroutpos);

 freebufs:
  free(outbuf);
  free(inputpos);
  free(outputpos);
 out:
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
  int outlen = inlen * 2;
  // TODO: Need to do this in a loop like usual character encoding
  // translations, but for now we assume that double size is good enough.
  jchar* outbuf = malloc(sizeof(jchar) * outlen);
  int result = lou_backTranslateString(tableNameUtf8, inbuf, &inlen,
				   outbuf, &outlen,
				   NULL/*typeform*/, NULL/*spacing*/,
				   dotsIO);
  free(inbuf);
  if (result == 0) {
    LOGE("Back translation failed.");
    goto freeoutbuf;
  }
  LOGV("Successfully translated %d cells into %d characters, "
       "consuming %d cells", (*env)->GetArrayLength(env, cells),
       outlen, inlen);
  ret = (*env)->NewString(env, outbuf, outlen);
 freeoutbuf:
  free(outbuf);
 releasetablename:
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
  lou_setDataPath((char*)pathUtf8);
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
getGlobalClassRef(JNIEnv* env, const char *name) {
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

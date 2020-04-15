/*
 * BRLTTY - A background process providing access to the console screen (when in
 *          text mode) for a blind person using a refreshable braille display.
 *
 * Copyright (C) 1995-2018 by The BRLTTY Developers.
 *
 * BRLTTY comes with ABSOLUTELY NO WARRANTY.
 *
 * This is free software, placed under the terms of the
 * GNU Lesser General Public License, as published by the Free Software
 * Foundation; either version 2.1 of the License, or (at your option) any
 * later version. Please see the file LICENSE-LGPL for details.
 *
 * Web Page: http://brltty.com/
 *
 * This software is maintained by Dave Mielke <dave@mielke.cc>.
 */

#ifndef BRLTTY_INCLUDED_SYSTEM_JAVA
#define BRLTTY_INCLUDED_SYSTEM_JAVA

#include <jni.h>

#include "prologue.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#ifdef __ANDROID__
#define JAVA_JNI_VERSION JNI_VERSION_1_6
#endif /* __ANDROID__ */

#define JAVA_METHOD(object, name, type, ...)                              \
  JNIEXPORT type JNICALL Java_##object##_##name(JNIEnv *env, jclass this, \
                                                ##__VA_ARGS__)

extern JavaVM *getJavaInvocationInterface(void);
extern JNIEnv *getJavaNativeInterface(void);
extern int clearJavaException(JNIEnv *env, int describe);

#define JAVA_SIG_VOID "V"
#define JAVA_SIG_BOOLEAN "Z"
#define JAVA_SIG_BYTE "B"
#define JAVA_SIG_CHAR "C"
#define JAVA_SIG_SHORT "S"
#define JAVA_SIG_INT "I"
#define JAVA_SIG_LONG "J"
#define JAVA_SIG_FLOAT "F"
#define JAVA_SIG_DOUBLE "D"
#define JAVA_SIG_OBJECT(path) "L" #path ";"
#define JAVA_SIG_ARRAY(element) "[" element
#define JAVA_SIG_METHOD(returns, arguments) "(" arguments ")" returns
#define JAVA_SIG_CONSTRUCTOR(arguments) \
  JAVA_SIG_METHOD(JAVA_SIG_VOID, arguments)

FUNCTION_DECLARE(setJavaClassLoader, int, (JNIEnv * env, jobject instance));
extern int findJavaClass(JNIEnv *env, jclass *class, const char *path);

extern int findJavaConstructor(JNIEnv *env, jmethodID *constructor,
                               jclass class, const char *signature);

extern int findJavaInstanceMethod(JNIEnv *env, jmethodID *method, jclass class,
                                  const char *name, const char *signature);

extern int findJavaStaticMethod(JNIEnv *env, jmethodID *method, jclass class,
                                const char *name, const char *signature);

extern int findJavaInstanceField(JNIEnv *env, jfieldID *field, jclass class,
                                 const char *name, const char *signature);

extern int findJavaStaticField(JNIEnv *env, jfieldID *field, jclass class,
                               const char *name, const char *signature);

extern char *getJavaLocaleName(void);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_SYSTEM_JAVA */

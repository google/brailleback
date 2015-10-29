/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.googlecode.eyesfree.utils;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;

import com.googlecode.eyesfree.utils.ClassLoadingManager;
import com.googlecode.eyesfree.utils.LogUtils;

public class ClassLoadingManagerTest extends AndroidTestCase {
    private static final String CLASS_STRING = "java.lang.String";

    private ClassLoadingManager mLoader;
    private int mCachedLogLevel;

    @Override
    public void setUp() {
        mCachedLogLevel = LogUtils.LOG_LEVEL;
        LogUtils.LOG_LEVEL = Log.VERBOSE;

        mLoader = ClassLoadingManager.getInstance();
        mLoader.init(mContext);
    }

    @Override
    public void tearDown() {
        mLoader.shutdown();
        mLoader = null;

        LogUtils.LOG_LEVEL = mCachedLogLevel;
    }

    @SmallTest
    public void testLoadOrGetCachedClass() {
        final Class<?> stringClass = mLoader.loadOrGetCachedClass(
                mContext, CLASS_STRING, null);
        assertTrue("Load java.lang.String from framework", String.class.equals(stringClass));

        final Class<?> emptyClass = mLoader.loadOrGetCachedClass(mContext, "x.y.Z", null);
        assertNull("Fail to load x.y.Z", emptyClass);

        final String packageClassName = getClass().getName();
        final Class<?> packageClass = mLoader.loadOrGetCachedClass(
                mContext, packageClassName, null);
        assertTrue("Load " + packageClassName + " from default package",
                getClass().equals(packageClass));

        final String systemClassName = "com.android.settings.Settings";
        final String systemPackage = "com.android.settings";
        final Class<?> systemClass = mLoader.loadOrGetCachedClass(
                mContext, systemClassName, systemPackage);
        assertNotNull("Load " + systemClassName + " from system package", systemClass);
    }

    @SmallTest
    public void testCheckInstanceOfClass() {
        assertTrue("String is instance of String",
                mLoader.checkInstanceOf(mContext, CLASS_STRING, null, String.class));
        assertTrue("String is instance of CharSequence",
                mLoader.checkInstanceOf(mContext, CLASS_STRING, null, CharSequence.class));
        assertFalse("String is not instance of StringBuffer",
                mLoader.checkInstanceOf(mContext, CLASS_STRING, null, StringBuffer.class));
    }

    @SmallTest
    public void testCheckInstanceOfString() {
        assertTrue("String is instance of String",
                mLoader.checkInstanceOf(mContext, CLASS_STRING, null, CLASS_STRING));
        assertTrue("String is instance of CharSequence", mLoader.checkInstanceOf(
                mContext, CLASS_STRING, null, "java.lang.CharSequence"));
        assertFalse("String is not instance of StringBuffer", mLoader.checkInstanceOf(
                mContext, CLASS_STRING, null, "java.lang.StringBuffer"));
    }
}

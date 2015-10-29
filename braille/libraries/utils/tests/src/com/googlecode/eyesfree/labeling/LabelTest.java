/*
 * Copyright (C) 2013 Google Inc.
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

package com.googlecode.eyesfree.labeling;

import android.test.suitebuilder.annotation.SmallTest;

import com.googlecode.eyesfree.labeling.Label;

import junit.framework.TestCase;

/**
 * Unit tests for the {@link Label} class.
 *
 * @author awdavis@google.com (Austin Davis)
 */
public class LabelTest extends TestCase {
    private static final long ID = 100L;
    private static final String PACKAGE_NAME = "packageName";
    private static final String PACKAGE_SIGNATURE = "packageSignature";
    private static final String VIEW_NAME = "viewName";
    private static final String TEXT = "text";
    private static final String LOCALE = "locale";
    private static final int VERSION = 101;
    private static final String SCREENSHOT_PATH = "screenshotPath";
    private static final long TIMESTAMP = 102L;

    /**
     * Test method for {@link Label#Label(long, String, String, String, String,
     * String, int, String, long)}.
     */
    @SmallTest
    public void testLabelConstructor() {
        final Label label = constructDefaultLabelWithId(ID);

        assertEquals(ID, label.getId());
        assertEquals(PACKAGE_NAME, label.getPackageName());
        assertEquals(PACKAGE_SIGNATURE, label.getPackageSignature());
        assertEquals(VIEW_NAME, label.getViewName());
        assertEquals(TEXT, label.getText());
        assertEquals(LOCALE, label.getLocale());
        assertEquals(VERSION, label.getPackageVersion());
        assertEquals(SCREENSHOT_PATH, label.getScreenshotPath());
        assertEquals(TIMESTAMP, label.getTimestamp());
    }

    /**
     * Test method for {@link Label#Label(String, String, String, String,
     * String, int, String, long)}.
     * <p>
     * Checks that the label ID is {@code Label#NO_ID} if no value is provided.
     */
    @SmallTest
    public void testLabelConstructorNoId() {
        final Label label = new Label(PACKAGE_NAME, PACKAGE_SIGNATURE, VIEW_NAME, TEXT, LOCALE,
                VERSION, SCREENSHOT_PATH, TIMESTAMP);

        assertEquals(Label.NO_ID, label.getId());
        assertEquals(PACKAGE_NAME, label.getPackageName());
        assertEquals(PACKAGE_SIGNATURE, label.getPackageSignature());
        assertEquals(VIEW_NAME, label.getViewName());
        assertEquals(TEXT, label.getText());
        assertEquals(LOCALE, label.getLocale());
        assertEquals(VERSION, label.getPackageVersion());
        assertEquals(SCREENSHOT_PATH, label.getScreenshotPath());
        assertEquals(TIMESTAMP, label.getTimestamp());
    }

    /**
     * Test method for {@link Label#Label(Label, long)}.
     */
    @SmallTest
    public void testLabelConstructorDeepCopy() {
        final Label label = new Label(PACKAGE_NAME, PACKAGE_SIGNATURE, VIEW_NAME, TEXT, LOCALE,
                VERSION, SCREENSHOT_PATH, TIMESTAMP);
        final long LABEL_ID = 7L;
        final Label copiedLabel = new Label(label, LABEL_ID);

        assertEquals(LABEL_ID, copiedLabel.getId());
        assertEquals(label, copiedLabel);
    }

    /**
     * Test method for {@link Label#equals}.
     * <p>
     * Tests that two otherwise identical Label objects with different IDs are
     * equal, but changes to other fields make the Label objects not equal.
     */
    @SmallTest
    public void testEquals() {
        final Label label1Same = constructDefaultLabelWithId(1L);
        final Label label2Same = constructDefaultLabelWithId(2L);
        final Label label1Different = constructDefaultLabelWithId(1L);
        label1Different.setText("differentText");

        assertTrue(label1Same.equals(label2Same));
        assertFalse(label1Same.equals(label1Different));
        assertFalse(label1Different.equals(label2Same));
    }

    /**
     * Verifies that {@link Label#hashCode} results are consistent with
     * {@link Label#equals} results.
     */
    @SmallTest
    public void testHashCode() {
        final Label label1Same = constructDefaultLabelWithId(1L);
        final Label label2Same = constructDefaultLabelWithId(2L);
        final Label label1Different = constructDefaultLabelWithId(1L);
        label1Different.setText("differentText");

        assertEquals(label1Same.equals(label2Same),
                label1Same.hashCode() == label2Same.hashCode());
        assertEquals(label1Same.equals(label1Different),
                label1Same.hashCode() == label1Different.hashCode());
        assertEquals(label1Different.equals(label2Same),
                label1Different.hashCode() == label2Same.hashCode());
    }

    /**
     * Tests that {@link Label#toString} contains the text of the label.
     */
    @SmallTest
    public void testToString() {
        final Label label = constructDefaultLabelWithId(ID);
        final String text = "exampleLabelText";
        label.setText(text);

        assertTrue(label.toString().contains(text));
    }

    private static Label constructDefaultLabelWithId(long labelId) {
        return new Label(labelId, PACKAGE_NAME, PACKAGE_SIGNATURE, VIEW_NAME, TEXT, LOCALE, VERSION,
                SCREENSHOT_PATH, TIMESTAMP);
    }
}

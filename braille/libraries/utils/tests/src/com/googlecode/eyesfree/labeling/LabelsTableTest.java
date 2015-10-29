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

import android.database.sqlite.SQLiteDatabase;
import android.test.suitebuilder.annotation.SmallTest;

import com.googlecode.eyesfree.labeling.LabelsTable;

import junit.framework.TestCase;

/**
 * Tests for {@link LabelsTable}.
 *
 * @author awdavis@google.com (Austin Davis)
 */
public class LabelsTableTest extends TestCase {

    /**
     * Tests {@link LabelsTable#onCreate}.
     */
    @SmallTest
    public void testOnCreate() {
        final SQLiteDatabase database = SQLiteDatabase.create(null);
        LabelsTable.onCreate(database);

        SQLiteDatabaseTestUtils.assertTableExists(database, LabelsTable.TABLE_NAME);
    }

    /**
     * Tests that an exception is raised when {@link LabelsTable#onUpgrade} is
     * called under certain conditions. Tests that it passes under others. This
     * test should be updated when upgrades are supported.
     */
    @SmallTest
    public void testOnUpgrade() {
        final SQLiteDatabase database = SQLiteDatabase.create(null);

        // Upgrades from 1 to 2 should succeed.
        try {
            LabelsTable.onUpgrade(database, 1, 2);
        } catch (Exception e) {
            fail("Database Upgrade failed from version 1 to 2.");
        }

        // Upgrades where oldVersion >= 2 should not currently be supported.
        try {
            LabelsTable.onUpgrade(database, 2, 3);
            fail("Expected exception.");
        } catch (UnsupportedOperationException expected) {
            // Expected
        }
    }

    /**
     * Tests that the keys and indices are consistent with the array of all
     * columns in the table.
     */
    @SmallTest
    public void testKeyAndIndexConsistency() {
        final String[] columns = LabelsTable.ALL_COLUMNS;

        assertEquals(9, columns.length);

        assertEquals(LabelsTable.KEY_ID, columns[LabelsTable.INDEX_ID]);
        assertEquals(LabelsTable.KEY_PACKAGE_NAME, columns[LabelsTable.INDEX_PACKAGE_NAME]);
        assertEquals(
                LabelsTable.KEY_PACKAGE_SIGNATURE, columns[LabelsTable.INDEX_PACKAGE_SIGNATURE]);
        assertEquals(LabelsTable.KEY_VIEW_NAME, columns[LabelsTable.INDEX_VIEW_NAME]);
        assertEquals(LabelsTable.KEY_TEXT, columns[LabelsTable.INDEX_TEXT]);
        assertEquals(LabelsTable.KEY_LOCALE, columns[LabelsTable.INDEX_LOCALE]);
        assertEquals(LabelsTable.KEY_PACKAGE_VERSION, columns[LabelsTable.INDEX_PACKAGE_VERSION]);
        assertEquals(LabelsTable.KEY_SCREENSHOT_PATH, columns[LabelsTable.INDEX_SCREENSHOT_PATH]);
        assertEquals(LabelsTable.KEY_TIMESTAMP, columns[LabelsTable.INDEX_TIMESTAMP]);
    }
}

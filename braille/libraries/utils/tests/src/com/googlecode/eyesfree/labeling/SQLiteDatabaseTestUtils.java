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

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import junit.framework.TestCase;

/**
 * Helper methods for assisting with database tests.
 *
 * @author awdavis@google.com (Austin Davis)
 */
public class SQLiteDatabaseTestUtils {

    /**
     * Asserts that the given table exists in the given database.
     * @param database The SQLite database to check for the table.
     * @param tableName The name of the table to verify.
     */
    public static void assertTableExists(SQLiteDatabase database, String tableName) {
        final Cursor cursor = database.query("sqlite_master", new String[] { "count(*)" },
                "type = ? AND name = ?", new String[] { "table", tableName }, null,
                null, null);

        TestCase.assertTrue(cursor.moveToFirst());
        TestCase.assertEquals(1, cursor.getInt(0));
    }
}

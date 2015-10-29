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

import com.googlecode.eyesfree.labeling.SQLiteTableBuilder;

import junit.framework.TestCase;

/**
 * Tests for {@link SQLiteTableBuilder}.
 *
 * @author awdavis@google.com (Austin Davis)
 */
public class SQLiteTableBuilderTest extends TestCase {
    private static final String TABLE_NAME = "tableName";
    private static final String COLUMN_NAME = "columnName";

    /**
     * Tests {@link SQLiteTableBuilder#SQLiteTableBuilder} with a {@code null}
     * database.
     */
    @SmallTest
    public void testConstructor_nullDatabase() {
        testConstructBuilderWithIllegalArgument(null, TABLE_NAME);
    }

    /**
     * Tests {@link SQLiteTableBuilder#SQLiteTableBuilder} with a table name of
     * {@code null} or the empty String.
     */
    @SmallTest
    public void testConstructor_emptyTableName() {
        final SQLiteDatabase database = SQLiteDatabase.create(null);

        try {
            testConstructBuilderWithIllegalArgument(database, null);
            testConstructBuilderWithIllegalArgument(database, "");
        } finally {
            database.close();
        }
    }

    /**
     * Tests {@link SQLiteTableBuilder#SQLiteTableBuilder} with an invalid SQL
     * identifier as the table name.
     */
    @SmallTest
    public void testConstructor_invalidTableName() {
        final SQLiteDatabase database = SQLiteDatabase.create(null);

        try {
            testConstructBuilderWithIllegalArgument(database, "table name");
            testConstructBuilderWithIllegalArgument(database, "1table");
            testConstructBuilderWithIllegalArgument(database, "table$Name");
            testConstructBuilderWithIllegalArgument(database, "tableName)");
            testConstructBuilderWithIllegalArgument(database, "tableName;");
        } finally {
            database.close();
        }
    }

    /**
     * Tests {@link SQLiteTableBuilder#addColumn} with a column name of
     * {@code null} or the empty String.
     */
    @SmallTest
    public void testaddColumn_emptyColumnName() {
        final SQLiteDatabase database = SQLiteDatabase.create(null);

        try {
            final SQLiteTableBuilder builder = new SQLiteTableBuilder(database, TABLE_NAME);

            testAddColumnWithIllegalArgument(builder, null, SQLiteTableBuilder.TYPE_INTEGER);
            testAddColumnWithIllegalArgument(builder, "", SQLiteTableBuilder.TYPE_INTEGER);
        } finally {
            database.close();
        }
    }

    /**
     * Tests {@link SQLiteTableBuilder#addColumn} with an invalid SQL
     * identifier as the column name.
     */
    @SmallTest
    public void testaddColumn_invalidColumnName() {
        final SQLiteDatabase database = SQLiteDatabase.create(null);

        try {
            final SQLiteTableBuilder builder = new SQLiteTableBuilder(database, TABLE_NAME);

            testAddColumnWithIllegalArgument(builder, "col name", SQLiteTableBuilder.TYPE_INTEGER);
            testAddColumnWithIllegalArgument(builder, "1column", SQLiteTableBuilder.TYPE_INTEGER);
            testAddColumnWithIllegalArgument(builder, "col$Name", SQLiteTableBuilder.TYPE_INTEGER);
            testAddColumnWithIllegalArgument(builder, "colName)", SQLiteTableBuilder.TYPE_INTEGER);
            testAddColumnWithIllegalArgument(builder, "colName;", SQLiteTableBuilder.TYPE_INTEGER);
        } finally {
            database.close();
        }
    }

    /**
     * Tests {@link SQLiteTableBuilder#addColumn} with an invalid type argument.
     */
    @SmallTest
    public void testaddColumn_invalidType() {
        final SQLiteDatabase database = SQLiteDatabase.create(null);
        final SQLiteTableBuilder builder = new SQLiteTableBuilder(database, TABLE_NAME);

        testAddColumnWithIllegalArgument(builder, COLUMN_NAME, -1);
    }

    /**
     * Tests {@link SQLiteTableBuilder#buildQueryString} and
     * {@link SQLiteTableBuilder#createTable}.
     */
    @SmallTest
    public void testCreateTable() {
        final String key1 = "column1";
        final String key2 = "column2";
        final String key3 = "column3";
        final String key4 = "column4";
        final String expectedQuery = String.format(
                "CREATE TABLE %s(%s INTEGER PRIMARY KEY, %s TEXT, %s REAL, %s BLOB)", TABLE_NAME,
                key1, key2, key3, key4);

        final SQLiteDatabase database = SQLiteDatabase.create(null);

        try {
            final SQLiteTableBuilder builder = new SQLiteTableBuilder(database, TABLE_NAME);
            builder.addColumn(key1, SQLiteTableBuilder.TYPE_INTEGER, true);
            builder.addColumn(key2, SQLiteTableBuilder.TYPE_TEXT, false);
            builder.addColumn(key3, SQLiteTableBuilder.TYPE_REAL);
            builder.addColumn(key4, SQLiteTableBuilder.TYPE_BLOB);

            final String actualQuery = builder.buildQueryString();
            builder.createTable();

            assertEquals(expectedQuery, actualQuery);
            SQLiteDatabaseTestUtils.assertTableExists(database, TABLE_NAME);
        } finally {
            database.close();
        }
    }

    private void testConstructBuilderWithIllegalArgument(SQLiteDatabase database,
            String tableName) {
        try {
            new SQLiteTableBuilder(database, tableName);

            fail("Expected exception.");
        } catch (IllegalArgumentException expected) {
            // Expected
        }
    }

    private void testAddColumnWithIllegalArgument(SQLiteTableBuilder builder, String columnName,
            int type) {
        try {
            builder.addColumn(columnName, type);

            fail("Expected exception.");
        } catch (IllegalArgumentException expected) {
            // Expected
        }
    }
}

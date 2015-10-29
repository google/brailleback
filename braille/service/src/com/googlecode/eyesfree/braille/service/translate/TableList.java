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

import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.text.TextUtils;
import android.util.Log;

import com.googlecode.eyesfree.braille.service.BuildConfig;
import com.googlecode.eyesfree.braille.service.R;
import com.googlecode.eyesfree.braille.translate.TableInfo;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Loads and keeps the list of supported braille tables from xml resources.
 */
public class TableList {
    private static final String LOG_TAG =
            TableList.class.getSimpleName();
    private static final boolean DBG = BuildConfig.DEBUG;
    private static final String TAG_TABLE_LIST =
            "table-list";
    private static final String TAG_TABLE = "table";
    private static final String ATTR_LOCALE = "locale";
    private static final Pattern LOCALE_SPLITTER =
            Pattern.compile("_", Pattern.LITERAL);
    private static final String ATTR_DOTS = "dots";
    private static final String ATTR_GRADE = "grade";
    private static final String ATTR_FILE_NAME = "fileName";
    private static final String ATTR_VARIANT = "variant";

    private final List<TableInfo> mTableInfos = new ArrayList<TableInfo>();
    private final Map<String, String> mTableFileNames =
            new HashMap<String, String>();

    public TableList(Resources res) {
        parseResource(res);
    }

    /** Returns all loaded tables. */
    public List<TableInfo> getTables() {
        return Collections.unmodifiableList(mTableInfos);
    }

    /**
     * Returns the file name to pass to liblouis for this table.
     */
    public String getFileName(String tableId) {
        return mTableFileNames.get(tableId);
    }

    private void parseResource(Resources res) {
        XmlResourceParser p = res.getXml(R.xml.tablelist);
        try {
            int elementType;
            while ((elementType = p.next()) != XmlPullParser.END_DOCUMENT) {
                if (elementType != XmlPullParser.START_TAG) {
                    continue;
                }
                if (p.getDepth() == 1) {
                    p.require(XmlPullParser.START_TAG, null, TAG_TABLE_LIST);
                    continue;
                } else if (p.getDepth() == 2) {
                    p.require(XmlPullParser.START_TAG, null, TAG_TABLE);
                    parseTable(p);
                } else {
                    throw new RuntimeException(p.getPositionDescription()
                            + ": Unexpected start tag");
                }
            }
        } catch (XmlPullParserException ex) {
            throw new RuntimeException(ex);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        } finally {
            p.close();
        }
    }

    private void parseTable(XmlResourceParser p) {
        Locale locale = parseLocale(p.getAttributeValue(null, ATTR_LOCALE));
        if (locale == null) {
            throw new RuntimeException(p.getPositionDescription()
                    + ": Locale must be specified");
        }
        int dots = p.getAttributeIntValue(null, ATTR_DOTS, -1);
        int grade = p.getAttributeIntValue(null, ATTR_GRADE, -1);
        if (dots < 0 && grade < 0) {
            throw new RuntimeException(p.getPositionDescription()
                    + ": neither dots nor grade was specified");
        }
        if (grade >= 0 && dots < 0) {
            dots = 6;
        }
        switch (dots) {
            case 6:
                if (grade < 0) {
                    grade = 1;
                }
                break;
            case 8:
                if (grade >= 0) {
                    throw new RuntimeException(p.getPositionDescription()
                            + ": grade must not be specified for 8 dot "
                            + "braille");
                }
                break;
            default:
                throw new RuntimeException(p.getPositionDescription()
                        + ": dots must be either 6 or 8");
        }
        String id = p.getIdAttribute();
        if (id == null) {
            throw new RuntimeException(p.getPositionDescription()
                    + ": missing id attribute");
        }
        String variant = p.getAttributeValue(null, ATTR_VARIANT);
        String fileName = p.getAttributeValue(null, ATTR_FILE_NAME);
        if (fileName == null) {
            throw new RuntimeException(p.getPositionDescription()
                    + ": missing fileName attribute");
        }
        mTableInfos.add(new TableInfo(locale, dots == 8, grade, id, variant));
        mTableFileNames.put(id, fileName);
        if (DBG) {
            Log.v(LOG_TAG, String.format("Table %s: locale=%s, dots=%d, "
                            + "grade=%d, variant=%s,fileName=%s",
                            id, locale.getDisplayName(), dots, grade,
                            variant, fileName));
        }
    }

    private static Locale parseLocale(String value) {
        if (TextUtils.isEmpty(value)) {
            return null;
        }
        String[] pieces = LOCALE_SPLITTER.split(value, 3);
        switch (pieces.length) {
            case 1: return new Locale(pieces[0]);
            case 2: return new Locale(pieces[0], pieces[1]);
            default: return new Locale(pieces[0], pieces[1], pieces[2]);
        }
    }
}

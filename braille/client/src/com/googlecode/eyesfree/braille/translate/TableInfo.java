/*
 * Copyright (C) 2012 Google Inc.
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

package com.googlecode.eyesfree.braille.translate;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Locale;

/**
 * Meta-data for a braille translation table.
 */
public class TableInfo implements Parcelable {
    private final Locale mLocale;
    private final boolean mIsEightDot;
    private final int mGrade;
    private final String mVariant;
    private final String mId;
    
    public TableInfo(Locale locale, boolean isEightDot, int grade, String id, String variant) {
        mLocale = locale;
        mIsEightDot = isEightDot;
        mGrade = grade;
        mId = id;
        mVariant = (variant == null ? "" : variant);
    }

    /**
     * Returns the locale for which this table is typically used.
     */
    public Locale getLocale() {
        return mLocale;
    }

    /**
     * Returns {@code true} if this is to be considered an 8 dot table,
     * a.aka. computer braille table.  If this returns {@code false}, this table
     * is a literary braille table, where prefixes and multi-cell characters
     * are commonly used.
     */
    public boolean isEightDot() {
        return mIsEightDot;
    }

    /**
     * Returns the contraction 'grade' for this table.  Lower grades
     * mean less contractions.  Depending on language, grade 0 or 1 is the
     * lowest grade.
     */
    public int getGrade() {
        return mGrade;
    }

    /**
     * Returns the unique identifier for this table, which is used when
     * requesting a table to be used for translation.  While the identifiers
     * are typically mnemonic, clients should consider them as internal and
     * opaque.  Identifiers are stable over releases and can be stored in user
     * settigns etc, however.
     */
    public String getId() {
        return mId;
    }

    /**
     * Returns the variant description of this table, which helps the user to 
     * distinguish table with other similar tables.
     */
    public String getVariant() {
        return mVariant;
    }

    // For Parcelable support.
    public static final Parcelable.Creator<TableInfo> CREATOR =
        new Parcelable.Creator<TableInfo>() {
            @Override
            public TableInfo createFromParcel(Parcel in) {
                return new TableInfo(in);
            }

            @Override
            public TableInfo[] newArray(int size) {
                return new TableInfo[size];
            }
        };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(mLocale.getLanguage());
        out.writeString(mLocale.getCountry());
        out.writeString(mLocale.getVariant());
        out.writeInt(mIsEightDot ? 1 : 0);
        out.writeInt(mGrade);
        out.writeString(mId);
        out.writeString(mVariant);
    }

    private TableInfo(Parcel in) {
        mLocale = new Locale(
                in.readString(),
                in.readString(),
                in.readString());
        mIsEightDot = in.readInt() != 0;
        mGrade = in.readInt();
        mId = in.readString();
        mVariant = in.readString();
    }
}

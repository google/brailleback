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

package com.googlecode.eyesfree.braille.display;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Represents a binding between a combination of braille device keys and a
 * command as declared in {@link BrailleInputEvent}.
 */
public class BrailleKeyBinding implements Parcelable {
    private static final int FLAG_LONG_PRESS = 0x00000001;

    private int mCommand;
    private String[] mKeyNames;
    private int mFlags;

    public BrailleKeyBinding() {
    }

    public BrailleKeyBinding(int command,
            String[] keyNames,
            boolean longPress) {
        mCommand = command;
        mKeyNames = keyNames;
        mFlags = longPress ? FLAG_LONG_PRESS : 0;
    }

    /**
     * Sets the command for this binding.
     */
    public BrailleKeyBinding setCommand(int command) {
        mCommand = command;
        return this;
    }

    /**
     * Sets the key names for this binding.
     */
    public BrailleKeyBinding setKeyNames(String[] keyNames) {
        mKeyNames = keyNames;
        return this;
    }

    /**
     * Sets whether this is a long press key binding.
     */
    public BrailleKeyBinding setLongPress(boolean longPress) {
        mFlags = (mFlags & ~FLAG_LONG_PRESS)
                | (longPress ? FLAG_LONG_PRESS : 0);
        return this;
    }

    /**
     * Returns the command for this key binding.
     * @see {@link BrailleInputEvent}.
     */
    public int getCommand() {
        return mCommand;
    }

    /**
     * Returns the list of device-specific keys that, when pressed
     * at the same time, will yield the command of this key binding.
     */
    public String[] getKeyNames() {
        return mKeyNames;
    }

    /**
     * Returns whether this is a long press key binding.
     */
    public boolean isLongPress() {
        return (mFlags & FLAG_LONG_PRESS) != 0;
    }

    // For Parcelable support.

    public static final Parcelable.Creator<BrailleKeyBinding> CREATOR =
        new Parcelable.Creator<BrailleKeyBinding>() {
            @Override
            public BrailleKeyBinding createFromParcel(Parcel in) {
                return new BrailleKeyBinding(in);
            }

            @Override
            public BrailleKeyBinding[] newArray(int size) {
                return new BrailleKeyBinding[size];
            }
        };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mCommand);
        out.writeStringArray(mKeyNames);
        out.writeInt(mFlags);
    }

    private BrailleKeyBinding(Parcel in) {
        mCommand = in.readInt();
        mKeyNames = in.createStringArray();
        mFlags = in.readInt();
    }
}

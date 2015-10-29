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

package com.googlecode.eyesfree.brailleback.utils;

import com.googlecode.eyesfree.braille.display.BrailleDisplayProperties;
import com.googlecode.eyesfree.braille.display.BrailleKeyBinding;
import com.googlecode.eyesfree.brailleback.R;

import android.content.Context;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;

/**
 * Contains utility methods for working with BrailleKeyBindings.
 *
 * @author careyzha@google.com (Carey Zhang)
 */
public class BrailleKeyBindingUtils {
    private BrailleKeyBindingUtils() {
        // Not instantiable.
    }

    /**
     * Returns a sorted list of bindings supported by the display.
     */
    public static ArrayList<BrailleKeyBinding> getSortedBindingsForDisplay(
            BrailleDisplayProperties props) {
        BrailleKeyBinding[] bindings = props.getKeyBindings();
        ArrayList<BrailleKeyBinding> sortedBindings =
                new ArrayList<BrailleKeyBinding>(Arrays.asList(bindings));
        Collections.sort(sortedBindings, COMPARE_BINDINGS);
        return sortedBindings;
    }

    /**
     * Returns the binding that matches the specified command in the sorted list
     * of BrailleKeyBindings. Returns null if not found.
     */
    public static BrailleKeyBinding getBrailleKeyBindingForCommand(
            int command, ArrayList<BrailleKeyBinding> sortedBindings) {
        BrailleKeyBinding dummyBinding = new BrailleKeyBinding();
        dummyBinding.setCommand(command);
        int index = Collections.binarySearch(sortedBindings, dummyBinding,
                COMPARE_BINDINGS_BY_COMMAND);
        if (index < 0) {
            return null;
        }
        while (index > 0
                && sortedBindings.get(index - 1).getCommand() == command) {
            index -= 1;
        }
        return sortedBindings.get(index);
    }

    /**
     * Returns the friendly name for the specified key binding.
     */
    public static String getFriendlyKeyNamesForCommand(
            BrailleKeyBinding binding,
            Map<String, String> friendlyKeyNames,
            Context context) {
        String delimiter = context.getString(R.string.help_keyBindingDelimiter);
        String keys = TextUtils.join(delimiter,
                getFriendlyKeyNames(binding.getKeyNames(), friendlyKeyNames));
        if (binding.isLongPress()) {
            keys = context.getString(R.string.help_longPressTemplate, keys);
        }
        return keys;
    }

    /**
     * Returns friendly key names (if available) based on the map.
     */
    public static String[] getFriendlyKeyNames(String[] unfriendlyNames,
            Map<String, String> friendlyNames) {
        String[] result = new String[unfriendlyNames.length];
        for (int i = 0; i < unfriendlyNames.length; ++i) {
            String friendlyName = friendlyNames.get(unfriendlyNames[i]);
            if (friendlyName != null) {
                result[i] = friendlyName;
            } else {
                result[i] = unfriendlyNames[i];
            }
        }
        return result;
    }

    /**
     * Compares key bindings by command number, then in an order that is
     * deterministic and that makes sure that the binding that should
     * appear on the help screen comes first.
     */
    public static final Comparator<BrailleKeyBinding> COMPARE_BINDINGS =
            new Comparator<BrailleKeyBinding>() {
            @Override
            public int compare(BrailleKeyBinding lhs, BrailleKeyBinding rhs) {
                int command1 = lhs.getCommand();
                int command2 = rhs.getCommand();
                if (command1 != command2) {
                    return command1 - command2;
                }
                // Prefer a binding without long press.
                boolean longPress1 = lhs.isLongPress();
                boolean longPress2 = rhs.isLongPress();
                if (longPress1 != longPress2) {
                    return longPress1 ? 1 : -1;
                }
                String[] names1 = lhs.getKeyNames();
                String[] names2 = rhs.getKeyNames();
                // Prefer fewer keys.
                if (names1.length != names2.length) {
                    return names1.length - names2.length;
                }
                // Compare key names for determinism.
                for (int i = 0; i < names1.length; ++i) {
                    String key1 = names1[i];
                    String key2 = names2[i];
                    int res = key1.compareTo(key2);
                    if (res != 0) {
                        return res;
                    }
                }
                return 0;
            }
    };

    /**
     * Compares key bindings by command number.  Used for search.
     */
    public static final Comparator<BrailleKeyBinding>
        COMPARE_BINDINGS_BY_COMMAND =
            new Comparator<BrailleKeyBinding>() {
            @Override
            public int compare(BrailleKeyBinding lhs, BrailleKeyBinding rhs) {
                return lhs.getCommand() - rhs.getCommand();
            }
    };
}

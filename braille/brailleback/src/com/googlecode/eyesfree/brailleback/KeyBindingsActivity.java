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

package com.googlecode.eyesfree.brailleback;

import com.googlecode.eyesfree.braille.display.BrailleDisplayProperties;
import com.googlecode.eyesfree.braille.display.BrailleInputEvent;
import com.googlecode.eyesfree.braille.display.BrailleKeyBinding;
import com.googlecode.eyesfree.braille.display.Display;
import com.googlecode.eyesfree.braille.display.DisplayClient;
import com.googlecode.eyesfree.brailleback.utils.BrailleKeyBindingUtils;
import com.googlecode.eyesfree.labeling.CustomLabelManager;
import com.googlecode.eyesfree.utils.LogUtils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Shows key bindings for the currently connected Braille display.
 */
public class KeyBindingsActivity extends Activity
        implements Display.OnConnectionStateChangeListener {
    DisplayClient mDisplay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(R.string.key_bindings_title);
        setContentView(R.layout.key_bindings);
        getActionBar().setHomeButtonEnabled(true);
        getActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    protected void onStart() {
        mDisplay = new DisplayClient(this);
        mDisplay.setOnConnectionStateChangeListener(this);
        super.onStart();
    }

    @Override
    protected void onStop() {
        if (mDisplay != null) {
            mDisplay.shutdown();
            mDisplay = null;
        }
        super.onStop();
    }

    @Override
    public void onConnectionStateChanged(int state) {
        if (state == Display.STATE_CONNECTED) {
            populateListView();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                Intent intent = new Intent(this,
                        BrailleBackPreferencesActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void populateListView() {
        BrailleDisplayProperties props = mDisplay.getDisplayProperties();
        if (props == null) {
            return;
        }
        ArrayList<BrailleKeyBinding> sortedBindings =
                BrailleKeyBindingUtils.getSortedBindingsForDisplay(props);

        ArrayList<KeyBinding> result = new ArrayList<KeyBinding>();

        String[] supportedCommands =
                getResources().getStringArray(R.array.help_supportedCommands);
        String[] descriptions =
                getResources().getStringArray(R.array.help_commandDescriptions);
        Map<String, String> friendlyKeyNames = props.getFriendlyKeyNames();
        BrailleKeyBinding dummyBinding = new BrailleKeyBinding();
        for (int i = 0; i < supportedCommands.length; ++i) {
            String name = supportedCommands[i];
            int command = BrailleInputEvent.stringToCommand(name);

            // Labeling menu command not supported in all versions.
            if (command == BrailleInputEvent.CMD_TOGGLE_BRAILLE_MENU
                && Build.VERSION.SDK_INT < CustomLabelManager.MIN_API_LEVEL) {
                continue;
            }

            BrailleKeyBinding binding =
                    BrailleKeyBindingUtils.getBrailleKeyBindingForCommand(
                        command, sortedBindings);
            if (binding == null) {
                // No supported binding for this display. That's normal.
                continue;
            }
            addBindingForCommand(binding, descriptions[i],
                    friendlyKeyNames, result);
        }

        KeyBindingsAdapter adapter = new KeyBindingsAdapter(
            KeyBindingsActivity.this, android.R.layout.simple_list_item_2,
            android.R.id.text1);
        adapter.addAll(result);

        ListView list = (ListView) findViewById(R.id.list);
        list.setAdapter(adapter);
    }

    private void addBindingForCommand(
            BrailleKeyBinding binding,
            String commandDescription,
            Map<String, String> friendlyKeyNames,
            List<KeyBinding> result) {
        String keys = BrailleKeyBindingUtils.getFriendlyKeyNamesForCommand(
                    binding, friendlyKeyNames, this);
        result.add(new KeyBinding(commandDescription, keys));
    }

    private static class KeyBindingsAdapter extends ArrayAdapter<KeyBinding> {
        public KeyBindingsAdapter(Context context, int layout, int textViewResourceId) {
            super(context, layout, textViewResourceId);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final View view = super.getView(position, convertView, parent);
            final KeyBinding item = getItem(position);

            ((TextView) view.findViewById(android.R.id.text2)).setText(item.binding);

            return view;
        }
    }

    private static class KeyBinding {
        private final String label;
        private final String binding;

        public KeyBinding(String label, String binding) {
            this.label = label;
            this.binding = binding;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}

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

import com.googlecode.eyesfree.braille.display.BrailleInputEvent;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.InputMethodManager;

import java.util.List;

/**
 * Helps coordinating between the accessibility service and input method.
 * Among other things, this class pops up the IME switcher if the user
 * tries to use the braille keyboard when the braille IME is not the current
 * one.
 */
public class IMEHelper {
    private static final String ACTION_WAIT_FOR_IME_PICKER =
            "com.googlecode.eyesfree.braille.brailleback."
            + "ACTION_WAIT_FOR_IME_PICKER";
    private static final String PREF_HAS_RUN_WIZARD = "IME_has_run_wizard";
    private final InputMethodManager mInputMethodManager;
    private final Context mContext;
    private final SharedPreferences mSharedPreferences;

    private boolean mWaitingForImePicker = false;

    /**
     * Initializes an object, registering a broadcast receiver to
     * open the IME picker.
     */
    public IMEHelper(Context context) {
        mInputMethodManager = (InputMethodManager)
                context.getSystemService(Context.INPUT_METHOD_SERVICE);
        mContext = context;
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(
            mContext);
        LocalBroadcastManager.getInstance(mContext).registerReceiver(mReceiver,
                new IntentFilter(ACTION_WAIT_FOR_IME_PICKER));
    }

    /**
     * Unregisters the broadcast receiver.
     */
    public void destroy() {
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(
            mReceiver);
    }

    /**
     * Processes incoming accessibility events, keeping the Braille IME
     * in sync with the currently focused node.  If the IME picker was
     * recently opened (by this class), this method will also try to focus
     * the item for the Braille IME.
     */
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (mWaitingForImePicker) {
            checkIMEPicker(event);
        }
    }

    /**
     * Listens for text input keystrokes.  If the user tries to enter text
     * and the Braille IME is not the default on the system, takes the user
     * through the {@link IMESetupWizardActivity}.
     */
    public boolean onInputEvent(BrailleInputEvent event) {
        int cmd = event.getCommand();
        if (cmd == BrailleInputEvent.CMD_BRAILLE_KEY
                || cmd == BrailleInputEvent.CMD_KEY_DEL
                || cmd == BrailleInputEvent.CMD_KEY_FORWARD_DEL) {
            if (!isInputMethodDefault(mContext, BrailleIME.class)) {
                tryIMESwitch();
                return true;
            }
        }
        return false;
    }

    /**
     * Sends a broadcast to open the IME picker and try to focus the
     * Braille IME, using {@code context}.  There must be an existing
     * instance of this class in the same process for this to work.
     */
    public static void sendWaitForIMEPicker(Context context) {
        LocalBroadcastManager.getInstance(context)
                .sendBroadcast(new Intent(ACTION_WAIT_FOR_IME_PICKER));
    }

    /**
     * Determines from system settings if {@code IMEClass} is an enabled input
     * method.
     */
    public static boolean isInputMethodEnabled(Context context,
            Class<?> IMEClass) {
        final ComponentName imeComponentName =
            new ComponentName(context, IMEClass);
        final String enabledIMEIds = Settings.Secure.getString(
            context.getContentResolver(),
            Settings.Secure.ENABLED_INPUT_METHODS);
        if (enabledIMEIds == null) {
            return false;
        }

        for (String enabledIMEId : enabledIMEIds.split(":")) {
            if (imeComponentName.equals(ComponentName.unflattenFromString(
                            enabledIMEId))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines, from system settings, if {@code IMEClass} is the default
     * input method.
     */
    public static boolean isInputMethodDefault(Context context,
            Class<?> IMEClass) {
        final ComponentName imeComponentName =
            new ComponentName(context, IMEClass);
        final String defaultIMEId = Settings.Secure.getString(
            context.getContentResolver(),
            Settings.Secure.DEFAULT_INPUT_METHOD);

        return defaultIMEId != null && imeComponentName.equals(
                ComponentName.unflattenFromString(defaultIMEId));
    }

    private void tryIMESwitch() {
        // If the user already saw the wizard and the IME is enabled,
        // then take the shortcut of dropping the user directly into the IME
        // picker.
        if (mSharedPreferences.getBoolean(PREF_HAS_RUN_WIZARD, false)
                && isInputMethodEnabled(mContext, BrailleIME.class)) {
            showIMEPicker();
        } else {
            Intent intent = new Intent(mContext, IMESetupWizardActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
            mSharedPreferences.edit()
                    .putBoolean(PREF_HAS_RUN_WIZARD, true)
                    .apply();
        }
    }

    private void showIMEPicker() {
        mInputMethodManager.showInputMethodPicker();
        waitForIMEPicker();
    }

    private void waitForIMEPicker() {
        mWaitingForImePicker = true;
        mHandler.scheduleStopWaitingForImePicker();
    }

    private void checkIMEPicker(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && "android".equals(event.getPackageName())
                && "android.app.AlertDialog".equals(event.getClassName())) {
            AccessibilityNodeInfo node = event.getSource();
            if (node == null) {
                return;
            }
            String IMETitle = mContext.getString(R.string.braille_ime_name);
            List<AccessibilityNodeInfo> found =
                    node.findAccessibilityNodeInfosByText(
                        IMETitle);
            if (found.size() == 0) {
                return;
            }
            AccessibilityNodeInfo firstFound = found.get(0);
            AccessibilityNodeInfo toFocus = firstFound.getParent();
            if (toFocus != null) {
                toFocus.performAction(
                    AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
            }
        }
    }

    private final IMEHelperHandler mHandler = new IMEHelperHandler();
    private class IMEHelperHandler extends Handler {
        private static final int WHAT_STOP_WAITING_FOR_IME_SWITCHER = 1;
        private static final long IME_SWITCHER_WAIT_MILLIS = 1000;

        public void scheduleStopWaitingForImePicker() {
            sendEmptyMessageDelayed(
                WHAT_STOP_WAITING_FOR_IME_SWITCHER,
                IME_SWITCHER_WAIT_MILLIS);
        }

        @Override
        public void handleMessage(Message message) {
            switch (message.what) {
                case WHAT_STOP_WAITING_FOR_IME_SWITCHER:
                    mWaitingForImePicker = false;
                    return;
            }
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_WAIT_FOR_IME_PICKER.equals(intent.getAction())) {
                waitForIMEPicker();
            }
        }
    };
}

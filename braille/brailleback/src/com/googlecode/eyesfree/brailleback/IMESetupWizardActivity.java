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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.inputmethod.InputMethodManager;

/**
 * Setup wizard that helps the user to enable the braille IME and make
 * it the default input method.
 */
public class IMESetupWizardActivity extends Activity {
    private static final int DIALOG_ENABLE_IME = 1;
    private static final int DIALOG_SET_DEFAULT_IME = 2;

    private static final int REQUEST_ENABLE_IME = 1;

    private InputMethodManager mInputMethodManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mInputMethodManager = (InputMethodManager) getSystemService(
            INPUT_METHOD_SERVICE);

        getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD),
                false, mContentObserver);

        checkStatus();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        checkStatus();
    }

    private void checkStatus() {
        if (!IMEHelper.isInputMethodEnabled(this, BrailleIME.class)) {
            showDialog(DIALOG_ENABLE_IME);
        } else if (!IMEHelper.isInputMethodDefault(this, BrailleIME.class)) {
            showDialog(DIALOG_SET_DEFAULT_IME);
        } else {
            finish();
        }
    }

    @Override
    public Dialog onCreateDialog(int id, Bundle args) {
        switch (id) {
            case DIALOG_ENABLE_IME:
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.ime_setup_need_enable_title)
                        .setMessage(R.string.ime_setup_need_enable_message)
                        .setPositiveButton(
                            R.string.ime_setup_need_enable_positive,
                            mNeedEnableListener)
                        .setNegativeButton(
                            R.string.ime_setup_skip,
                            mSkipListener)
                        .setOnCancelListener(mOnCancelListener)
                        .create();
            case DIALOG_SET_DEFAULT_IME: {
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.ime_setup_need_default_title)
                        .setMessage(R.string.ime_setup_need_default_message)
                        .setPositiveButton(
                            R.string.ime_setup_need_default_positive,
                            mNeedDefaultListener)
                        .setOnCancelListener(mOnCancelListener)
                        .create();
            }
        }

        return super.onCreateDialog(id, args);
    }

    private final OnClickListener mNeedEnableListener = new OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            final Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
            startActivityForResult(intent, REQUEST_ENABLE_IME);
        }
    };

    private final OnClickListener mNeedDefaultListener =
            new OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            IMEHelper.sendWaitForIMEPicker(IMESetupWizardActivity.this);
            mInputMethodManager.showInputMethodPicker();
            finish();
        }
    };

    private final OnClickListener mSkipListener = new OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            finish();
        }
    };

    private final OnCancelListener mOnCancelListener = new OnCancelListener() {
        @Override
        public void onCancel(DialogInterface dialog) {
            finish();
        }
    };

    private final Handler mHandler = new Handler();

    private final ContentObserver mContentObserver =
        new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            if (IMEHelper.isInputMethodDefault(IMESetupWizardActivity.this,
                            BrailleIME.class)) {
                removeDialog(DIALOG_SET_DEFAULT_IME);
            }
        }
    };
}

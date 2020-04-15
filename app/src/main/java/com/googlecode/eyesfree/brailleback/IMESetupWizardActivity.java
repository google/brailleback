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
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Setup wizard that helps the user to enable the braille IME and make it the default input method.
 */
public class IMESetupWizardActivity extends Activity {

  /** Result id for IME-enable activity. */
  private static final int REQUEST_ENABLE_IME = 1;

  private InputMethodManager inputMethodManager;

  /**
   * Screens to prompt for enabling and activating braille IME. Use screen directly in this
   * activity, not using dialog, because Android O does not allow dialog to call
   * showInputMethodPicker().
   */
  private EnableBrailleImeFragment enableBrailleImeFrag;

  private DefaultBrailleImeFragment defaultBrailleImeFrag;
  private static final int FRAGMENTS_LAYOUT_ID = View.generateViewId();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);

    getContentResolver()
        .registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD),
            false,
            contentObserver);

    // Create view-group containing fragments.
    LinearLayout layout = new LinearLayout(this);
    layout.setId(FRAGMENTS_LAYOUT_ID);
    enableBrailleImeFrag = new EnableBrailleImeFragment();
    defaultBrailleImeFrag = new DefaultBrailleImeFragment();
    defaultBrailleImeFrag.setInputMethodManager(inputMethodManager);
    setContentView(layout);

    checkStatus();
  }

  ////////////////////////////////////////////////////////////////////////////////////////
  // Screens

  /** Screen fragment that shows button to launch IME-enabling activity. */
  public static class EnableBrailleImeFragment extends Fragment {

    @Override
    public View onCreateView(
        LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

      // Create "enable braille IME" screen.
      final Activity context = getActivity();
      LinearLayout enableBrailleImeLayout = new LinearLayout(context);
      enableBrailleImeLayout.setOrientation(LinearLayout.VERTICAL);
      enableBrailleImeLayout.addView(
          createMessageText(context, R.string.ime_setup_need_enable_message));

      // Create "enable braille IME" button.
      Button button = new Button(context);
      button.setText(R.string.ime_setup_need_enable_positive);
      button.setOnClickListener(
          new View.OnClickListener() {
            @Override
            public void onClick(View view) {
              // Start IME-enable activity, and have the wizard activity handle the result.
              final Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
              context.startActivityForResult(intent, REQUEST_ENABLE_IME);
            }
          });
      enableBrailleImeLayout.addView(button);

      enableBrailleImeLayout.addView(createCancelButton(context));
      return enableBrailleImeLayout;
    }
  }

  /** Screen fragment that shows button to launch IME-choosing activity. */
  public static class DefaultBrailleImeFragment extends Fragment {

    private InputMethodManager inputMethodManager;

    public void setInputMethodManager(InputMethodManager inputMethodManagerArg) {
      inputMethodManager = inputMethodManagerArg;
    }

    @Override
    public View onCreateView(
        LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

      // Create "set default IME" screen.
      Activity context = getActivity();
      LinearLayout defaultBrailleImeLayout = new LinearLayout(context);
      defaultBrailleImeLayout.setOrientation(LinearLayout.VERTICAL);
      defaultBrailleImeLayout.addView(
          createMessageText(context, R.string.ime_setup_need_default_message));

      // Create "set default IME" button -- in case immediate call to showInputMethodPicker() fails,
      // or in case user exits IME-picker without selecting braille IME.
      Button button = new Button(context);
      button.setText(R.string.ime_setup_need_default_positive);
      button.setOnClickListener(
          new View.OnClickListener() {
            @Override
            public void onClick(View view) {
              inputMethodManager.showInputMethodPicker();
            }
          });
      defaultBrailleImeLayout.addView(button);

      defaultBrailleImeLayout.addView(createCancelButton(context));
      return defaultBrailleImeLayout;
    }

    @Override
    public void onResume() {
      super.onResume();
      // Try to call showInputMethodPicker() without button click.
      // Need delay to ensure showInputMethodPicker() receives correct UID, else it fails.
      Handler handler = new Handler();
      handler.postDelayed(
          new Runnable() {
            @Override
            public void run() {
              inputMethodManager.showInputMethodPicker();
            }
          },
          300);
    }
  }

  /** Creates a text-view with a little bit of formatting. */
  private static TextView createMessageText(Context context, int textResourceId) {
    TextView message = new TextView(context);
    message.setText(textResourceId);
    LinearLayout.LayoutParams params =
        new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    final int margin = 20;
    params.setMargins(margin, margin, margin, margin);
    message.setLayoutParams(params);
    message.setLineSpacing(0.0f, 1.25f);
    return message;
  }

  /** Creates a cancel button that ends enclosing activity. */
  private static Button createCancelButton(final Activity activity) {
    Button button = new Button(activity);
    button.setText(android.R.string.cancel);
    button.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View view) {
            activity.finish();
          }
        });
    return button;
  }
  ;

  ////////////////////////////////////////////////////////////////////////////////////////
  // Methods to handle completion of spawned IME setup sub-activities.

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    checkStatus();
  }

  private void checkStatus() {
    if (!IMEHelper.isInputMethodEnabled(this, BrailleIME.class)) {
      showFragment(enableBrailleImeFrag);
    } else if (!IMEHelper.isInputMethodDefault(this, BrailleIME.class)) {
      showFragment(defaultBrailleImeFrag);
    } else {
      finish();
    }
  }

  private void showFragment(Fragment newFragment) {
    FragmentManager fragmentManager = getFragmentManager();
    FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
    fragmentTransaction.replace(FRAGMENTS_LAYOUT_ID, newFragment);
    fragmentTransaction.commit();
  }

  private final Handler handler = new Handler();

  private final ContentObserver contentObserver =
      new ContentObserver(handler) {
        @Override
        public void onChange(boolean selfChange) {
          if (IMEHelper.isInputMethodDefault(IMESetupWizardActivity.this, BrailleIME.class)) {
            finish();
          }
        }
      };

  @Override
  public void finish() {
    getContentResolver().unregisterContentObserver(contentObserver);
    super.finish();
  }
}

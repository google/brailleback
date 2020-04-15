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

package com.googlecode.eyesfree.brailleback;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.View;
import com.googlecode.eyesfree.braille.display.BrailleDisplayProperties;
import com.googlecode.eyesfree.braille.display.BrailleKeyBinding;
import java.util.Collections;
import java.util.Random;

/** Activity which guides new users through the use of the on-screen overlay. */
public class OverlayTutorialActivity extends Activity {

  private static final String PREF_HAS_RUN = "overlay_tutorial_run";
  private static final int PAN_DEMO_INTERVAL = 500;
  private final Random mRandom = new Random();
  private View mPanDemo;
  private View mPanDemoLeftButton;
  private View mPanDemoRightButton;
  private boolean mPanDemoState;
  private final Runnable mPanDemoRunnable =
      new Runnable() {
        @Override
        public void run() {
          mPanDemoState = !mPanDemoState;
          mPanDemoLeftButton.setPressed(mPanDemoState);
          mPanDemoRightButton.setPressed(mPanDemoState);
          mPanDemo.postDelayed(this, PAN_DEMO_INTERVAL);
        }
      };

  private static final int ROUTING_KEY_DEMO_CELLS = 16;
  private static final BrailleDisplayProperties ROUTING_KEY_DEMO_DISPLAY_PROPERTIES =
      new BrailleDisplayProperties(
          ROUTING_KEY_DEMO_CELLS,
          0,
          new BrailleKeyBinding[0],
          Collections.<String, String>emptyMap());
  private static final int ROUTING_KEY_DEMO_INTERVAL = 1500;
  private BrailleView mRoutingKeyDemo;
  private final Runnable mRoutingKeyDemoRunnable =
      new Runnable() {
        @Override
        public void run() {
          mRoutingKeyDemo.cancelPendingTouches();
          mRoutingKeyDemo.highlightCell(mRandom.nextInt(ROUTING_KEY_DEMO_CELLS));
          mRoutingKeyDemo.postDelayed(this, ROUTING_KEY_DEMO_INTERVAL);
        }
      };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.overlay_tutorial_activity);
    mPanDemo = findViewById(R.id.pan_demo);
    mPanDemoLeftButton = mPanDemo.findViewById(R.id.pan_left_button);
    mPanDemoRightButton = mPanDemo.findViewById(R.id.pan_right_button);
    mRoutingKeyDemo =
        (BrailleView) findViewById(R.id.routing_key_demo).findViewById(R.id.braille_view);
    mRoutingKeyDemo.setDisplayProperties(ROUTING_KEY_DEMO_DISPLAY_PROPERTIES);
    mRoutingKeyDemo.displayDots(new byte[0], "", new int[0]);
  }

  @Override
  protected void onResume() {
    super.onResume();

    mPanDemo.post(mPanDemoRunnable);
    mRoutingKeyDemo.post(mRoutingKeyDemoRunnable);
  }

  @Override
  protected void onPause() {
    super.onPause();

    mPanDemo.removeCallbacks(mPanDemoRunnable);
    mRoutingKeyDemo.removeCallbacks(mRoutingKeyDemoRunnable);
  }

  public void onClickFinishButton(View v) {
    finish();
  }

  public void onClickAccessibilitySettingsButton(View v) {
    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
  }

  /*package*/ static void startIfFirstTime(final Context context) {
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    if (prefs.getBoolean(PREF_HAS_RUN, false)) {
      return;
    }
    prefs.edit().putBoolean(PREF_HAS_RUN, true).apply();

    DialogInterface.OnClickListener listener =
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            switch (which) {
              case DialogInterface.BUTTON_POSITIVE:
                context.startActivity(new Intent(context, OverlayTutorialActivity.class));
                break;
              case DialogInterface.BUTTON_NEGATIVE:
                // Do nothing.
                break;
            }
          }
        };
    Dialog dialog =
        new AlertDialog.Builder(context)
            .setTitle(R.string.overlay_tutorial_dialog_title)
            .setMessage(R.string.overlay_tutorial_dialog_message)
            .setPositiveButton(android.R.string.ok, listener)
            .setNegativeButton(android.R.string.cancel, listener)
            .create();
    dialog.show();
  }
}

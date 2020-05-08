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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import com.googlecode.eyesfree.braille.display.BrailleInputEvent;

/**
 * Helps coordinating between the accessibility service and input method. Among other things, this
 * class pops up the IMESetupWizardActivity if the user tries to use the braille keyboard when the
 * braille IME is not the current one.
 */
public class IMEHelper {
  private final Context context;

  /** Initializes an object, registering a broadcast receiver to open the IME picker. */
  public IMEHelper(Context contextArg) {
    context = contextArg;
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
      if (!isInputMethodDefault(context, BrailleIME.class)) {
                tryIMESwitch();
                return true;
            }
        }
        return false;
    }

  /** Determines from system settings if {@code imeClass} is an enabled input method. */
  @SuppressWarnings("StringSplitter") // Guava not used in project, so Splitter not available.
  public static boolean isInputMethodEnabled(Context contextArg, Class<?> imeClass) {
    final ComponentName imeComponentName = new ComponentName(contextArg, imeClass);
    final String enabledIMEIds =
        Settings.Secure.getString(
            contextArg.getContentResolver(), Settings.Secure.ENABLED_INPUT_METHODS);
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

  /** Determines, from system settings, if {@code imeClass} is the default input method. */
  public static boolean isInputMethodDefault(Context contextArg, Class<?> imeClass) {
    final ComponentName imeComponentName = new ComponentName(contextArg, imeClass);
    final String defaultIMEId =
        Settings.Secure.getString(
            contextArg.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);

        return defaultIMEId != null && imeComponentName.equals(
                ComponentName.unflattenFromString(defaultIMEId));
    }

    private void tryIMESwitch() {
      Intent intent = new Intent(context, IMESetupWizardActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      context.startActivity(intent);
    }
}

/*
 * BRLTTY - A background process providing access to the console screen (when in
 *          text mode) for a blind person using a refreshable braille display.
 *
 * Copyright (C) 1995-2018 by The BRLTTY Developers.
 *
 * BRLTTY comes with ABSOLUTELY NO WARRANTY.
 *
 * This is free software, placed under the terms of the
 * GNU Lesser General Public License, as published by the Free Software
 * Foundation; either version 2.1 of the License, or (at your option) any
 * later version. Please see the file LICENSE-LGPL for details.
 *
 * Web Page: http://brltty.com/
 *
 * This software is maintained by Dave Mielke <dave@mielke.cc>.
 */

package org.a11y.brltty.android;

import android.speech.tts.TextToSpeech;
import android.util.Log;
import java.util.HashMap;

public class SpeechDriver {
  private static final String LOG_TAG = SpeechDriver.class.getName();

  enum State {
    STOPPED,
    STARTED,
    FAILED
  }

  private static volatile State state = State.STOPPED;
  private static TextToSpeech tts = null;
  private static final HashMap<String, String> parameters = new HashMap<String, String>();

  public static boolean start() {
    TextToSpeech.OnInitListener listener =
        new TextToSpeech.OnInitListener() {
          @Override
          public void onInit(int status) {
            synchronized (this) {
              switch (status) {
                case TextToSpeech.SUCCESS:
                  state = state.STARTED;
                  break;

                default:
                  Log.w(LOG_TAG, "unknown text to speech engine startup status: " + status);
                case TextToSpeech.ERROR:
                  state = state.FAILED;
                  break;
              }

              notify();
            }
          }
        };

    synchronized (listener) {
      Log.d(LOG_TAG, "starting text to speech engine");
      state = state.STOPPED;
      tts = new TextToSpeech(BrailleService.getBrailleService(), listener);

      while (state == state.STOPPED) {
        try {
          listener.wait();
        } catch (InterruptedException exception) {
        }
      }
    }

    if (state == state.STARTED) {
      parameters.clear();
      Log.d(LOG_TAG, "text to speech engine started");
      return true;
    }

    Log.w(LOG_TAG, "text to speech engine failed");
    tts = null;
    return false;
  }

  public static void stop() {
    if (tts != null) {
      tts.shutdown();
      tts = null;
      state = state.STOPPED;
    }
  }

  public static boolean say(String text) {
    return tts.speak(text, TextToSpeech.QUEUE_ADD, parameters) == TextToSpeech.SUCCESS;
  }

  public static boolean mute() {
    return tts.stop() == TextToSpeech.SUCCESS;
  }

  public static boolean setVolume(float volume) {
    parameters.put(TextToSpeech.Engine.KEY_PARAM_VOLUME, Float.toString(volume));
    return true;
  }

  public static boolean setRate(float rate) {
    return tts.setSpeechRate(rate) == TextToSpeech.SUCCESS;
  }

  public static boolean setPitch(float pitch) {
    return tts.setPitch(pitch) == TextToSpeech.SUCCESS;
  }
}

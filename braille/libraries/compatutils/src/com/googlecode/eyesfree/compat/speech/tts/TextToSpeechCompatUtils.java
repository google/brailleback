/*
 * Copyright (C) 2011 Google Inc.
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

package com.googlecode.eyesfree.compat.speech.tts;

import android.content.Context;
import android.speech.tts.TextToSpeech;

import com.googlecode.eyesfree.compat.CompatUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Set;

public class TextToSpeechCompatUtils {
    private static final Constructor<?> CONSTRUCTOR_LLS = CompatUtils.getConstructor(
            TextToSpeech.class, Context.class, TextToSpeech.OnInitListener.class, String.class);
    private static final Method METHOD_setEngineByPackageName = CompatUtils.getMethod(
            TextToSpeech.class, "setEngineByPackageName", String.class);
    private static final Method METHOD_getFeatures = CompatUtils.getMethod(
            TextToSpeech.class, "getFeatures", Locale.class);
    private static final Method METHOD_getCurrentEngine = CompatUtils.getMethod(
            TextToSpeech.class, "getCurrentEngine");

    private TextToSpeechCompatUtils() {
        // This class is non-instantiable.
    }

    /**
     * Queries the engine for the set of features it supports for a given locale.
     * Features can either be framework defined, e.g.
     * {@link android.speech.tts.TextToSpeech.Engine#KEY_FEATURE_NETWORK_SYNTHESIS}
     * or engine specific. Engine specific keys must be prefixed by the name of
     * the engine they are intended for. These keys can be used as parameters
     * to {@link TextToSpeech#speak(String, int, java.util.HashMap)} and
     * {@link TextToSpeech#synthesizeToFile(String, java.util.HashMap, String)}.
     * <p>
     * Features are boolean flags, and their values in the synthesis parameters
     * must be behave as per {@link Boolean#parseBoolean(String)}.
     *
     * @param locale The locale to query features for.
     */
    @SuppressWarnings("unchecked")
    public static Set<String> getFeatures(TextToSpeech tts, Locale locale) {
        final Object result = CompatUtils.invoke(tts, null, METHOD_getFeatures, locale);
        if (result == null) {
            return Collections.emptySet();
        }

        return (Set<String>) result;
    }

    /**
     * The constructor for the TextToSpeech class, using the given TTS engine.
     * This will also initialize the associated TextToSpeech engine if it isn't
     * already running.
     *
     * @param context The context this instance is running in.
     * @param listener The
     *            {@link android.speech.tts.TextToSpeech.OnInitListener} that
     *            will be called when the TextToSpeech engine has initialized.
     * @param engine Package name of the TTS engine to use.
     */
    public static TextToSpeech newTextToSpeech(Context context,
            TextToSpeech.OnInitListener listener, String engine) {
        final TextToSpeech result = (TextToSpeech) CompatUtils.newInstance(CONSTRUCTOR_LLS,
                context, listener, engine);

        if (result != null) {
            return result;
        }

        return new TextToSpeech(context, listener);
    }

    public static int setEngineByPackageName(TextToSpeech receiver, String enginePackageName) {
        return (Integer) CompatUtils.invoke(
                receiver, TextToSpeech.ERROR, METHOD_setEngineByPackageName, enginePackageName);
    }

    /**
     * @return the engine currently in use by this TextToSpeech instance.
     */
    public static String getCurrentEngine(TextToSpeech receiver) {
        return (String) CompatUtils.invoke(receiver, null, METHOD_getCurrentEngine);
    }

    public static class EngineCompatUtils {
        /**
         * Intent for starting a TTS service. Services that handle this intent must
         * extend TextToSpeechService. Normal applications should not use this
         * intent directly, instead they should talk to the TTS service using the
         * the methods in this class.
         */
        public static final String INTENT_ACTION_TTS_SERVICE =
                "android.intent.action.TTS_SERVICE";

        /**
         * Parameter key to specify the speech volume relative to the current stream
         * type volume used when speaking text. Volume is specified as a float
         * ranging from 0 to 1 where 0 is silence, and 1 is the maximum volume (the
         * default behavior).
         *
         * @see TextToSpeech#speak(String, int, HashMap)
         * @see TextToSpeech#playEarcon(String, int, HashMap)
         */
        public static final String KEY_PARAM_VOLUME = "volume";

        /**
         * Parameter key to specify how the speech is panned from left to right when
         * speaking text. Pan is specified as a float ranging from -1 to +1 where -1
         * maps to a hard-left pan, 0 to center (the default behavior), and +1 to
         * hard-right.
         *
         * @see TextToSpeech#speak(String, int, HashMap)
         * @see TextToSpeech#playEarcon(String, int, HashMap)
         */
        public static final String KEY_PARAM_PAN = "pan";

        private EngineCompatUtils() {
            // This class is non-instantiable.
        }
    }
}

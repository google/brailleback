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

package com.googlecode.eyesfree.compat.media;

import android.media.AudioManager;

import com.googlecode.eyesfree.compat.CompatUtils;

import java.lang.reflect.Method;

public class AudioManagerCompatUtils {
    private static final Class<?> CLASS_OnAudioFocusChangeListener = CompatUtils.getClass(
            "android.media.AudioManager$OnAudioFocusChangeListener");

    private static final Method METHOD_requestAudioFocus = CompatUtils.getMethod(AudioManager.class,
            "requestAudioFocus", CLASS_OnAudioFocusChangeListener, int.class, int.class);
    private static final Method METHOD_abandonAudioFocus = CompatUtils.getMethod(
            AudioManager.class, "abandonAudioFocus", CLASS_OnAudioFocusChangeListener);
    private static final Method METHOD_forceVolumeControlStream = CompatUtils.getMethod(
            AudioManager.class, "forceVolumeControlStream", int.class);

    /** The audio stream for DTMF Tones */
    public static final int STREAM_DTMF = 8;

    /**
     * Broadcast intent when the volume for a particular stream type changes.
     * Includes the stream, the new volume and previous volumes
     *
     * @see #EXTRA_VOLUME_STREAM_TYPE
     * @see #EXTRA_VOLUME_STREAM_VALUE
     * @see #EXTRA_PREV_VOLUME_STREAM_VALUE
     */
    public static final String VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION";

    /**
     * @hide Broadcast intent when the master volume changes. Includes the new
     *       volume
     * @see #EXTRA_MASTER_VOLUME_VALUE
     * @see #EXTRA_PREV_MASTER_VOLUME_VALUE
     */
    public static final String MASTER_VOLUME_CHANGED_ACTION =
            "android.media.MASTER_VOLUME_CHANGED_ACTION";

    /**
     * The stream type for the volume changed intent.
     */
    public static final String EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE";

    /**
     * The volume associated with the stream for the volume changed intent.
     */
    public static final String EXTRA_VOLUME_STREAM_VALUE =
            "android.media.EXTRA_VOLUME_STREAM_VALUE";

    /**
     * The previous volume associated with the stream for the volume changed
     * intent.
     */
    public static final String EXTRA_PREV_VOLUME_STREAM_VALUE =
            "android.media.EXTRA_PREV_VOLUME_STREAM_VALUE";

    /**
     * The new master volume value for the master volume changed intent. Value
     * is integer between 0 and 100 inclusive.
     */
    public static final String EXTRA_MASTER_VOLUME_VALUE =
            "android.media.EXTRA_MASTER_VOLUME_VALUE";

    /**
     * The previous master volume value for the master volume changed intent.
     * Value is integer between 0 and 100 inclusive.
     */
    public static final String EXTRA_PREV_MASTER_VOLUME_VALUE =
            "android.media.EXTRA_PREV_MASTER_VOLUME_VALUE";

    /**
     * Used to indicate a gain of audio focus, or a request of audio focus, of
     * unknown duration.
     */
    public static final int AUDIOFOCUS_GAIN = 1;

    /**
     * Used to indicate a temporary gain or request of audio focus, anticipated
     * to last a short amount of time. Examples of temporary changes are the
     * playback of driving directions, or an event notification.
     */
    public static final int AUDIOFOCUS_GAIN_TRANSIENT = 2;

    /**
     * Used to indicate a temporary request of audio focus, anticipated to last
     * a short amount of time, and where it is acceptable for other audio
     * applications to keep playing after having lowered their output level
     * (also referred to as "ducking"). Examples of temporary changes are the
     * playback of driving directions where playback of music in the background
     * is acceptable.
     */
    public static final int AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK = 3;

    /**
     * A failed focus change request.
     */
    public static final int AUDIOFOCUS_REQUEST_FAILED = 0;
    /**
     * A successful focus change request.
     */
    public static final int AUDIOFOCUS_REQUEST_GRANTED = 1;

    private AudioManagerCompatUtils() {
        // This class is non-instantiable.
    }

    /**
     * Forces the stream controlled by hard volume keys specifying streamType ==
     * -1 releases control to the logic.
     * <p>
     * <b>Warning:</b> This is a private API, and it may not exist in API 16+.
     * </p>
     */
    public static void forceVolumeControlStream(AudioManager receiver, int streamType) {
        CompatUtils.invoke(receiver, null, METHOD_forceVolumeControlStream, streamType);
    }

    /**
     * Request audio focus. Send a request to obtain the audio focus
     *
     * @param l the listener to be notified of audio focus changes
     * @param streamType the main audio stream type affected by the focus
     *            request
     * @param durationHint use {@link #AUDIOFOCUS_GAIN_TRANSIENT} to indicate
     *            this focus request is temporary, and focus will be abandoned
     *            shortly. Examples of transient requests are for the playback
     *            of driving directions, or notifications sounds. Use
     *            {@link #AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK} to indicate also
     *            that it's ok for the previous focus owner to keep playing if
     *            it ducks its audio output. Use {@link #AUDIOFOCUS_GAIN} for a
     *            focus request of unknown duration such as the playback of a
     *            song or a video.
     * @return {@link #AUDIOFOCUS_REQUEST_FAILED} or
     *         {@link #AUDIOFOCUS_REQUEST_GRANTED}
     */
    public static int requestAudioFocus(
            AudioManager receiver, Object l, int streamType, int durationHint) {
        return (Integer) CompatUtils.invoke(receiver, AUDIOFOCUS_REQUEST_FAILED,
                METHOD_requestAudioFocus, l, streamType, durationHint);
    }

    /**
     * Abandon audio focus. Causes the previous focus owner, if any, to receive
     * focus.
     *
     * @param l the listener with which focus was requested.
     * @return {@link #AUDIOFOCUS_REQUEST_FAILED} or
     *         {@link #AUDIOFOCUS_REQUEST_GRANTED}
     */
    public static int abandonAudioFocus(AudioManager receiver, Object l) {
        return (Integer) CompatUtils.invoke(receiver, AUDIOFOCUS_REQUEST_FAILED,
                METHOD_abandonAudioFocus, l);
    }
}

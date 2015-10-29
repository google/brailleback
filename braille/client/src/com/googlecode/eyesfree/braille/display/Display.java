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

package com.googlecode.eyesfree.braille.display;

/**
 * An interface for accessing a Braille display.
 * @see DisplayClient
 */
public interface Display {

    /** Initial value, which is never reported to the listener. */
    public static final int STATE_UNKNOWN = -2;
    public static final int STATE_ERROR = -1;
    public static final int STATE_NOT_CONNECTED = 0;
    public static final int STATE_CONNECTED = 1;

    /**
     * A callback interface to get informed about connection state changes.
     */
    public interface OnConnectionStateChangeListener {
        void onConnectionStateChanged(int state);
    }

    /**
     * A callback interface to get informed about connection progress changes.
     */
    public interface OnConnectionChangeProgressListener {
        /**
         * The service is taking some action that might eventually lead
         * to a change in connection state, such as trying to connect.
         * The {@code description} is a human-readable (localized) string
         * describing the current progress.  There is no guarantee that this
         * method will be called before a change in connection state.
         */
        void onConnectionChangeProgress(String description);
    }

    /**
     * A callback interface for input from the braille display.
     */
    public interface OnInputEventListener {
        void onInputEvent(BrailleInputEvent inputEvent);
    }

    /**
     * Sets a {@code listener} for connection state changes.
     * {@code listener} can be {@code null} to remove a previously set
     * listener.
     */
    void setOnConnectionStateChangeListener(
            OnConnectionStateChangeListener listener);

    /**
     * Sets a {@code listener} for connection change progress.
     * {@code listener} can be {@code null} to remove a previously set
     * listener.
     */
    void setOnConnectionChangeProgressListener(
            OnConnectionChangeProgressListener listener);

    /**
     * Sets a {@code listener} for input events.  {@code listener} can be
     * {@code null} to remove a previously set listener.
     */
    void setOnInputEventListener(OnInputEventListener listener);

    /**
     * Returns the display properties, or {@code null} if not connected
     * to a display.
     */
    BrailleDisplayProperties getDisplayProperties();

    /**
     * Displays a given dots configuration on the braille display.
     * @param patterns Dots configuration to be displayed.
     * @param text Plain text equivalent of the displayed dots.
     * @param brailleToTextPositions Map from indices in {@text patterns}
     *        to the corresponding indices in {@text text}. Must be equal
     *        in length to {@text patterns}, and contain only valid
     *        indices in {@text text}.
     */
    void displayDots(byte[] patterns, CharSequence text,
            int[] brailleToTextPositions);

    /**
     * Asks the service to try to connect to a display.
     * @see IBrailleService#poll()
     */
    void poll();

    /**
     * Unbinds from the braille display service and deallocates any
     * resources.  This method should be called when the braille display
     * is no longer in use by this client.
     */
    void shutdown();

    /**
     * Returns {@code true} if this display is simulated.
     * Intended for user feedback purposes. This value may change, but only
     * while the display is disconnected.
     */
    boolean isSimulated();
}


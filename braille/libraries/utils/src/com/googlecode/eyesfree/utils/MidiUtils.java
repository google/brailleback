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

package com.googlecode.eyesfree.utils;

import android.content.Context;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;

import com.leff.midi.MidiFile;
import com.leff.midi.MidiTrack;
import com.leff.midi.event.Controller;
import com.leff.midi.event.ProgramChange;
import com.leff.midi.event.meta.Tempo;
import com.leff.midi.event.meta.TimeSignature;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Utilities for loading MIDI files and generating MIDI tracks.
 */
public class MidiUtils {
    public static class ControllerType {
        /**
         * Bank select allows switching between groups of 128 programs. 14-bit
         * coarse/fine resolution (0x0000 to 0x3FFF).
         */
        public static final int BANK_SELECT = 0;
        public static final int BANK_SELECT_LSB = 32;

        /**
         * The modulation wheel typically controls a vibrato effect. 14-bit
         * coarse/fine resolution (0x0000 to 0x3FFF). Use 0 for no modulation
         * effect.
         */
        public static final int MODULATION_WHEEL = 1;

        /**
         * The volume level for a single channel. 14-bit coarse/fine resolution
         * (0x0000 to 0x3FFF) but most devices only implement coarse (0x00 to
         * 0xFF). Use 0 to mute the channel.
         */
        public static final int VOLUME = 7;

        /**
         * The pan position controls where the channel's sound will be placed
         * within the stereo field. 14-bit coarse/fine resolution (0x0000 to
         * 0x3FFF) where 0x2000 is center position, 0x0000 is hard left, and
         * 0x3FFF is hard right.
         * <p>
         * Some devices only respond to coarse adjust where 0x40 is center, 0x00
         * is hard left, and 0xFF is hard right.
         */
        public static final int PAN = 10;

        /**
         * The sustain pedal sustains the currently playing notes, even if they
         * are released. 0x00 is off, 0xFF is on.
         */
        public static final int SUSTAIN_PEDAL = 64;

        /**
         * The effects level for the device. Typically controls reverb or delay.
         * 8-bit resolution (0x00 to 0xFF). Use 0 for no effect.
         */
        public static final int EFFECTS_LEVEL = 91;

        /**
         * The chorus effect level for the device. 8-bit resolution (0x00 to
         * 0xFF). Use 0 for no chorus effect.
         */
        public static final int CHORUS_LEVEL = 93;
    }

    /**
     * Scale types used with {@link #generateMidiScale}.
     */
    public static final int SCALE_TYPE_MAJOR = 1;
    public static final int SCALE_TYPE_NATURAL_MINOR = 2;
    public static final int SCALE_TYPE_HARMONIC_MINOR = 3;
    public static final int SCALE_TYPE_MELODIC_MINOR = 4;
    public static final int SCALE_TYPE_PENTATONIC = 5;

    /** The subdirectory name of the location used to store temporary files */
    public static final String MIDI_TEMP_DIR_NAME = "midi";

    /** Default beats-per-minute for MIDI tracks. You can dance to 95. */
    private static final int DEFAULT_BPM = 95;

    /** Default channel for MIDI tracks. This should be 0. */
    private static final int DEFAULT_CHANNEL = 0;

    /** Default volume for MIDI tracks. Range is [0,127]. */
    private static final int DEFAULT_VOLUME = 127;

    /** Default tempo track for MIDI compositions. Uses 4/4 signature. */
    private static final MidiTrack DEFAULT_TEMPO_TRACK = new MidiTrack();

    static {
        final TimeSignature timeSignature = new TimeSignature();
        final Tempo tempo = new Tempo();
        timeSignature.setTimeSignature(
                4, 4, TimeSignature.DEFAULT_METER, TimeSignature.DEFAULT_DIVISION);
        tempo.setBpm(DEFAULT_BPM);

        DEFAULT_TEMPO_TRACK.insertEvent(timeSignature);
        DEFAULT_TEMPO_TRACK.insertEvent(tempo);
    }

    /**
     * Writes an array of MIDI notes to a file.
     * <p>
     * The array must begin with a program ID followed by triplets of note
     * pitch, velocity, and duration.
     *
     * @param context The context used to write temporary files.
     * @param notes An array of MIDI notes.
     * @return A temporary MIDI file (.mid), or {@code null} on error
     */
    public static File generateMidiFileFromArray(Context context, int[] notes) {
        final MidiTrack track = MidiUtils.readMidiTrackFromArray(notes);
        if (track == null) {
            return null;
        }

        return MidiUtils.writeMidiTrackToTempFile(context, track);
    }

    /**
     * Attempts to remove any temporarily generated MIDI files from the MIDI
     * cache directory.
     *
     * @param context The application context to use for locating the cache
     *            directory.
     */
    public static void purgeMidiTempFiles(Context context) {
        final File midiDir = new File(context.getCacheDir(), MIDI_TEMP_DIR_NAME);
        if (midiDir.exists()) {
            final File[] tempMidiFiles = midiDir.listFiles();
            if (tempMidiFiles != null) {
                for (File f : tempMidiFiles) {
                    f.delete();
                }
            }
        }

        midiDir.delete();
    }

    /**
     * Generates and plays a MIDI scale.
     *
     * @param program The MIDI program ID to use
     * @param velocity The MIDI velocity to use for each note
     * @param duration The duration in milliseconds of each note
     * @param startingPitch The MIDI pitch value on which the scale should begin
     * @param pitchesToPlay The number of pitches to play. 7 pitches (or 5
     *            pentatonic) is a complete scale. 8 (or 6 pentatonic) for a
     *            resolved scale.
     * @param scaleType The MIDI_SCALE_TYPE_* constant associated with the type
     *            of scale to play as defined in {@link MidiUtils}.
     * @param context Application or service context to get resources and files.
     *
     * @return {@code true} if successful, {@code false} otherwise.
     */
    public static boolean playMidiScale(int program, int velocity, int duration, int startingPitch,
            int pitchesToPlay, int scaleType, Context context) {
        final int[] midiSequence = generateMidiScale(program, velocity, duration, startingPitch,
                pitchesToPlay, scaleType);
        if (midiSequence == null) {
            return false;
        }

        final File file = generateMidiFileFromArray(context, midiSequence);
        if (file == null) {
            return false;
        }

        final MediaPlayer scalePlayer = new MediaPlayer();
        scalePlayer.setOnPreparedListener(new OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                scalePlayer.start();
            }
        });

        scalePlayer.setOnCompletionListener(new OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                scalePlayer.release();
                file.delete();
            }
        });

        // Use the FD method of setting the MediaPlayer data source for
        // backwards compatibility.
        try {
            final FileInputStream in = new FileInputStream(file);

            try {
                scalePlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                scalePlayer.setDataSource(in.getFD());
                scalePlayer.prepareAsync();
            } finally {
                in.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    /**
     * Generates a MIDI scale using the specified parameters.
     *
     * @param program The MIDI program ID to use
     * @param velocity The MIDI velocity to use for each note
     * @param duration The duration in milliseconds of each note
     * @param startingPitch The MIDI pitch value on which the scale should begin
     * @param pitchesToPlay The number of pitches to play. 7 pitches (or 5
     *            pentatonic) is a complete scale. 8 (or 6 pentatonic) for a
     *            resolved scale.
     * @param scaleType The MIDI_SCALE_TYPE_* constant associated with the type
     *            of scale to play.
     * @return An array of MIDI properties compatible with
     *         {@link #generateMidiFileFromArray(Context, int[])}
     */
    private static int[] generateMidiScale(int program, int velocity, int duration,
            int startingPitch, int pitchesToPlay, int scaleType) {
        if (pitchesToPlay <= 0 || duration <= 0) {
            return null;
        }

        if (scaleType == SCALE_TYPE_PENTATONIC) {
            // Pentatonic are 5-note scales that drop the 4th and 7th notes in
            // each scale. To play the correct number of pitches, we must add
            // the number of notes to be dropped to the original major scale.
            int completeScales = pitchesToPlay / 5;
            int notesInPartialScale = pitchesToPlay % 5;

            pitchesToPlay += (completeScales * 2) + ((notesInPartialScale > 3) ? 1 : 0);
        }

        final ArrayList<Integer> notes = new ArrayList<Integer>();

        // Generate as much of a major scale is needed.
        int nextPitch = startingPitch;
        for (int i = 1; i <= pitchesToPlay; ++i) {
            notes.add(nextPitch);

            // Calculate the next pitch based on scale position.
            final int noteInScale = (i % 7);
            switch (noteInScale) {
                case 1:
                case 2:
                case 4:
                case 5:
                case 6:
                    nextPitch += 2;
                    break;
                case 0:
                case 3:
                    nextPitch += 1;
                    break;
            }
        }

        if (scaleType == SCALE_TYPE_NATURAL_MINOR
                || scaleType == SCALE_TYPE_HARMONIC_MINOR
                || scaleType == SCALE_TYPE_MELODIC_MINOR) {
            for (int i = 0; i < notes.size(); ++i) {
                final int noteInScale = (i + 1) % 7;

                // Lower the 3rd, 6th, and 7th of each scale by a half step
                // based on the type of minor scale.
                switch (noteInScale) {
                    case 3:
                        notes.add(i, (notes.remove(i) - 1));
                        break;
                    case 6:
                        if (scaleType == SCALE_TYPE_NATURAL_MINOR
                                || scaleType == SCALE_TYPE_HARMONIC_MINOR) {
                            notes.add(i, (notes.remove(i) - 1));
                        }
                        break;
                    case 0:
                        if (scaleType == SCALE_TYPE_NATURAL_MINOR) {
                            notes.add(i, (notes.remove(i) - 1));
                        }
                        break;
                }
            }
        } else if (scaleType == SCALE_TYPE_PENTATONIC) {
            ArrayList<Integer> indiciesToRemove = new ArrayList<Integer>();
            for (int i = 0; i < notes.size(); ++i) {
                final int noteInScale = (i + 1) % 7;

                // Petatonic scales are derived by removing the 4th and 7th from
                // each scale.
                switch (noteInScale) {
                    case 4:
                    case 0:
                        indiciesToRemove.add(i);
                }
            }

            for (int i = indiciesToRemove.size(); i > 0; --i) {
                notes.remove((int) indiciesToRemove.get(i - 1));
            }
        }

        // Generate the MIDI sequence array from the derived notes
        int[] midiSequence = new int[(notes.size() * 3) + 1];
        midiSequence[0] = program;
        for (int i = 1; i < midiSequence.length; i += 3) {
            midiSequence[i] = notes.remove(0);
            midiSequence[i + 1] = velocity;
            midiSequence[i + 2] = duration;
        }

        return midiSequence;
    }

    /**
     * Reads a MIDI track from an array of notes. The array format must be:
     * <ul>
     * <li>Program ID,
     * <li>Note pitch, velocity, duration,
     * <li>(additional notes)
     * </ul>
     *
     * @param notes The array to read as a MIDI track.
     * @return A MIDI track.
     */
    private static MidiTrack readMidiTrackFromArray(int[] notes) {
        final MidiTrack noteTrack = new MidiTrack();
        int tick = 0;

        final int program = notes[0];
        if ((program < 0) || (program > 127)) {
            throw new IllegalArgumentException("MIDI track program must be in the range [0,127]");
        }

        // Set the channel volume to maximum.
        noteTrack.insertEvent(new Controller(
                0, 0, DEFAULT_CHANNEL, ControllerType.VOLUME, DEFAULT_VOLUME));

        // Mute the reverb effect.
        noteTrack.insertEvent(new Controller(
                0, 0, DEFAULT_CHANNEL, ControllerType.EFFECTS_LEVEL, 0));

        // Mute the chorus effect.
        noteTrack.insertEvent(new Controller(
                0, 0, DEFAULT_CHANNEL, ControllerType.CHORUS_LEVEL, 0));

        noteTrack.insertEvent(new ProgramChange(0, 0, program));

        if ((notes.length % 3) != 1) {
            throw new IllegalArgumentException(
                    "MIDI note array must contain a single integer followed by triplets");
        }

        for (int i = 1; i < (notes.length - 2); i += 3) {
            final int pitch = notes[i];
            if ((pitch < 21) || (pitch > 108)) {
                throw new IllegalArgumentException("MIDI note pitch must be in the range [21,108]");
            }

            final int velocity = notes[i + 1];
            if ((velocity < 0) || (velocity > 127)) {
                throw new IllegalArgumentException(
                        "MIDI note velocity must be in the range [0,127]");
            }

            final int duration = notes[i + 2];

            noteTrack.insertNote(DEFAULT_CHANNEL, pitch, velocity, tick, duration);

            tick += duration;
        }

        return noteTrack;
    }

    private static File writeMidiTrackToTempFile(Context context, MidiTrack noteTrack) {
        // Always add the default tempo track first.
        final ArrayList<MidiTrack> tracks = new ArrayList<MidiTrack>();
        tracks.add(DEFAULT_TEMPO_TRACK);
        tracks.add(noteTrack);

        // Attempt to write the track to a file and return it.
        try {
            final File midiDir = new File(context.getCacheDir(), MIDI_TEMP_DIR_NAME);
            if (!midiDir.exists() && !midiDir.mkdirs()) {
               return null;
            }

            final MidiFile midi = new MidiFile(MidiFile.DEFAULT_RESOLUTION, tracks);
            final File output = File.createTempFile("talkback", ".mid", midiDir);
            midi.writeToFile(output);
            return output;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}

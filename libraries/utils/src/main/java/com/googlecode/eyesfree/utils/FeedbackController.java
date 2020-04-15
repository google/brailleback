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

package com.googlecode.eyesfree.utils;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.media.SoundPool;
import android.media.SoundPool.OnLoadCompleteListener;
import android.os.Handler;
import android.os.Vibrator;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Provides auditory and haptic feedback.
 *
 * @author alanv@google.com (Alan Viverette)
 */
public class FeedbackController {
  /** Default stream for audio feedback. */
  private static final int DEFAULT_STREAM = AudioManager.STREAM_MUSIC;

  /** Default volume for sound playback relative to current stream volume. */
  private static final float DEFAULT_VOLUME = 1.0f;

  /** Default rate for sound playback. Use 1.0f for normal speed playback. */
  private static final float DEFAULT_RATE = 1.0f;

  /** Number of channels to use in SoundPool for auditory icon feedback. */
  private static final int NUMBER_OF_CHANNELS = 10;

  /** Default delay time between repeated sounds. */
  private static final int DEFAULT_REPETITION_DELAY = 150;

  /** Map of resource IDs to vibration pattern arrays. */
  private final SparseArray<long[]> mResourceIdToVibrationPatternMap = new SparseArray<long[]>();

  /** Map of resource IDs to loaded sound stream IDs. */
  private final SparseIntArray mResourceIdToSoundMap = new SparseIntArray();

  /** Unloaded resources to play post-load */
  private final ArrayList<Integer> mPostLoadPlayables = new ArrayList<Integer>();

  /** Parent context. Required for mapping resource IDs to resources. */
  private final Context mContext;

  /** Parent resources. Used to distinguish raw and MIDI resources. */
  private final Resources mResources;

  /** Vibration service used to play vibration patterns. */
  private final Vibrator mVibrator;

  /** Sound pool used to play auditory icons. */
  private final SoundPool mSoundPool;

  /** Handler used for delaying feedback */
  private final Handler mHandler;

  /** Whether haptic feedback is enabled. */
  private boolean mHapticEnabled = true;

  /** Whether auditory feedback is enabled. */
  private boolean mAuditoryEnabled = true;

  /** Current volume (range 0..1). */
  private float mVolume = DEFAULT_VOLUME;

  /** Constructs and initializes a new feedback controller. */
  public FeedbackController(Context context) {
    mContext = context;
    mResources = context.getResources();
    mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
    mSoundPool = new SoundPool(NUMBER_OF_CHANNELS, DEFAULT_STREAM, 1);
    mSoundPool.setOnLoadCompleteListener(
        new OnLoadCompleteListener() {
          @Override
          public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
            if (status == 0) {
              synchronized (mPostLoadPlayables) {
                if (mPostLoadPlayables.contains(sampleId)) {
                  soundPool.play(sampleId, DEFAULT_VOLUME, DEFAULT_VOLUME, 1, 0, DEFAULT_RATE);
                  mPostLoadPlayables.remove(Integer.valueOf(sampleId));
                }
              }
            }
          }
        });
    mHandler = new Handler();

    mResourceIdToSoundMap.clear();
    mResourceIdToVibrationPatternMap.clear();
    MidiUtils.purgeMidiTempFiles(context);
  }

  /** @return The parent context. */
  protected final Context getContext() {
    return mContext;
  }

  /**
   * Sets the current volume for auditory feedback.
   *
   * @param volume Volume value (range 0..100).
   */
  public void setVolume(int volume) {
    mVolume = (Math.min(100, Math.max(0, volume)) / 100.0f);
  }

  /** @param enabled Whether haptic feedback should be enabled. */
  public void setHapticEnabled(boolean enabled) {
    mHapticEnabled = enabled;
  }

  /** @param enabled Whether auditory feedback should be enabled. */
  public void setAuditoryEnabled(boolean enabled) {
    mAuditoryEnabled = enabled;
  }

  /** Stops all active feedback. */
  public void interrupt() {
    mVibrator.cancel();
  }

  /**
   * Releases resources associated with this feedback controller. It is good practice to call this
   * method when you're done using the controller.
   */
  public void shutdown() {
    mVibrator.cancel();
    mSoundPool.release();
    MidiUtils.purgeMidiTempFiles(mContext);
  }

  /**
   * Asynchronously make a sound available for later use if audio feedback is enabled. Sounds should
   * be loaded using this function whenever audio feedback is enabled.
   *
   * @param resId Resource ID of the sound to be loaded.
   * @return The sound pool identifier for the resource.
   */
  public int preloadSound(int resId) {
    if (mResourceIdToSoundMap.indexOfKey(resId) >= 0) {
      return mResourceIdToSoundMap.get(resId);
    }

    final int soundPoolId;
    final String resType = mResources.getResourceTypeName(resId);

    if ("raw".equals(resType)) {
      soundPoolId = mSoundPool.load(mContext, resId, 1);
    } else if ("array".equals(resType)) {
      final int[] notes = mResources.getIntArray(resId);
      soundPoolId = loadMidiSoundFromArray(notes, false);
    } else {
      LogUtils.log(this, Log.ERROR, "Failed to load sound: Unknown resource type");
      return -1;
    }

    if (soundPoolId < 0) {
      LogUtils.log(this, Log.ERROR, "Failed to load sound: Invalid sound pool ID");
      return -1;
    }

    mResourceIdToSoundMap.put(resId, soundPoolId);
    return soundPoolId;
  }

  /**
   * Convenience method for playing different sounds based on a boolean result.
   *
   * @param result The conditional that controls which sound resource is played.
   * @param trueResId The resource to play if the result is {@code true}.
   * @param falseResId The resource to play if the result is {@code false}.
   * @see #playSound(int)
   */
  public void playSoundConditional(boolean result, int trueResId, int falseResId) {
    final int resId = (result ? trueResId : falseResId);

    if (resId > 0) {
      playSound(resId);
    }
  }

  /**
   * Plays the sound file specified by the given resource identifier at the default rate.
   *
   * @param resId The sound file's resource identifier.
   * @return {@code true} if successful
   */
  public boolean playSound(int resId) {
    return playSound(resId, DEFAULT_RATE, DEFAULT_VOLUME);
  }

  /**
   * Plays the sound file specified by the given resource identifier on the given stream
   *
   * @param resId The resource identifier of the sound to play
   * @param streamId The {@link AudioManager} identifier of the stream on which to play the sound
   * @return {@code true} if successful
   */
  public boolean playSoundOnStream(int resId, int streamId, float volume) {
    if (!mAuditoryEnabled) {
      return false;
    }

    final MediaPlayer mp = new MediaPlayer();
    mp.setOnPreparedListener(
        new OnPreparedListener() {
          @Override
          public void onPrepared(MediaPlayer mp) {
            mp.start();
          }
        });

    mp.setOnCompletionListener(
        new OnCompletionListener() {
          @Override
          public void onCompletion(MediaPlayer mp) {
            mp.release();
          }
        });

    final AssetFileDescriptor fd = mContext.getResources().openRawResourceFd(resId);
    mp.setAudioStreamType(streamId);
    try {
      mp.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
      mp.setVolume(volume, volume);
      mp.prepareAsync();
    } catch (IOException e) {
      e.printStackTrace();
      return false;
    }

    return true;
  }

  /**
   * Plays the sound file specified by the given resource identifier at the specified rate.
   *
   * @param resId The sound file's resource identifier.
   * @param rate The rate at which to play back the sound, where 1.0 is normal speed and 0.5 is
   *     half-speed.
   * @param volume The volume at which to play the sound, where 1.0 is normal volume and 0.5 is
   *     half-volume.
   * @return {@code true} if successful
   */
  public boolean playSound(int resId, float rate, float volume) {
    if (!mAuditoryEnabled) {
      return false;
    }

    if (mResourceIdToSoundMap.indexOfKey(resId) < 0) {
      final int soundPoolId = preloadSound(resId);
      mPostLoadPlayables.add(soundPoolId);

      // Since we'll play the sound immediately after it loads, just
      // assume it will play successfully.
      return true;
    }

    final int soundId = mResourceIdToSoundMap.get(resId);
    final float relativeVolume = mVolume * volume;
    final int stream = mSoundPool.play(soundId, relativeVolume, relativeVolume, 1, 0, rate);

    return (stream != 0);
  }

  /**
   * Plays the sound file specified by the given resource identifier repeatedly.
   *
   * @param resId The resource of the sound file to play
   * @param repetitions The number of times to repeat the sound file
   * @return {@code true} if successful
   */
  public boolean playRepeatedSound(int resId, int repetitions) {
    return playRepeatedSound(
        resId, DEFAULT_RATE, DEFAULT_VOLUME, repetitions, DEFAULT_REPETITION_DELAY);
  }

  /**
   * Plays the sound file specified by the given resource identifier repeatedly.
   *
   * @param resId The resource of the sound file to play
   * @param rate The rate at which to play the sound file
   * @param volume The volume at which to play the sound, where 1.0 is normal volume and 0.5 is
   *     half-volume.
   * @param repetitions The number of times to repeat the sound file
   * @param delay The amount of time between calls to start playback of the sound in miliseconds.
   *     Should be the sound's playback time plus some delay.
   * @return {@code true} if successful
   */
  public boolean playRepeatedSound(
      int resId, float rate, float volume, int repetitions, long delay) {
    if (!mAuditoryEnabled) {
      return false;
    }

    mHandler.post(new RepeatedSoundRunnable(resId, rate, volume, repetitions, delay));
    return true;
  }

  /**
   * Generates and plays a MIDI scale.
   *
   * @param program The MIDI program ID to use
   * @param velocity The MIDI velocity to use for each note
   * @param duration The duration in milliseconds of each note
   * @param startingPitch The MIDI pitch value on which the scale should begin
   * @param pitchesToPlay The number of pitches to play. 7 pitches (or 5 pentatonic) is a complete
   *     scale. 8 (or 6 pentatonic) for a resolved scale.
   * @param scaleType The MIDI_SCALE_TYPE_* constant associated with the type of scale to play as
   *     defined in {@link MidiUtils}.
   * @return {@code true} if successful, {@code false} otherwise.
   */
  public boolean playMidiScale(
      int program,
      int velocity,
      int duration,
      int startingPitch,
      int pitchesToPlay,
      int scaleType) {
    if (!mAuditoryEnabled) {
      return false;
    }

    return MidiUtils.playMidiScale(
        program, velocity, duration, startingPitch, pitchesToPlay, scaleType, mContext);
  }

  /**
   * Plays the vibration pattern specified by the given resource identifier.
   *
   * @param resId The vibration pattern's resource identifier.
   * @return {@code true} if successful
   */
  public boolean playVibration(int resId) {
    if (!mHapticEnabled || (mVibrator == null)) {
      return false;
    }

    long[] pattern = getVibrationPattern(resId);
    mVibrator.vibrate(pattern, -1);
    return true;
  }

  /**
   * Plays the vibration pattern specified by the given resource identifier and repeats it
   * indefinitely from the specified index.
   *
   * @see #cancelVibration()
   * @param resId The vibration pattern's resource identifier.
   * @param repeatIndex The index at which to loop vibration in the pattern.
   * @return {@code true} if successful
   */
  public boolean playRepeatedVibration(int resId, int repeatIndex) {
    if (!mHapticEnabled || (mVibrator == null)) {
      return false;
    }

    long[] pattern = getVibrationPattern(resId);

    if (repeatIndex < 0 || repeatIndex >= pattern.length) {
      throw new ArrayIndexOutOfBoundsException(repeatIndex);
    }
    mVibrator.vibrate(pattern, repeatIndex);
    return true;
  }

  /** Cancels vibration feedback if in progress. */
  public void cancelVibration() {
    if (mVibrator != null) {
      mVibrator.cancel();
    }
  }

  /**
   * Retrieves the vibration pattern associated with the array resource.
   *
   * @param resId The array resource id of the pattern to retrieve.
   * @return an array of {@code long} values from the pattern.
   */
  private long[] getVibrationPattern(int resId) {
    final long[] cachedPattern = mResourceIdToVibrationPatternMap.get(resId);
    if (cachedPattern != null) {
      return cachedPattern;
    }

    final int[] intPattern = mResources.getIntArray(resId);
    if (intPattern == null) {
      return new long[0];
    }

    final long[] pattern = new long[intPattern.length];
    for (int i = 0; i < pattern.length; i++) {
      pattern[i] = intPattern[i];
    }

    mResourceIdToVibrationPatternMap.put(resId, pattern);

    return pattern;
  }

  public void playMidiSoundFromPool(int soundID) {
    mSoundPool.play(soundID, DEFAULT_VOLUME, DEFAULT_VOLUME, 1, 0, DEFAULT_RATE);
  }

  /**
   * Generates a MIDI file and loads it into the sound pool.
   *
   * @param notes The MIDI track sequence to load
   * @param playOnLoad {@code true} to play the sound immediately after it loads.
   * @return the sound ID for this sound, or {@code -1} on error.
   */
  public int loadMidiSoundFromArray(int[] notes, boolean playOnLoad) {
    final File midiFile = MidiUtils.generateMidiFileFromArray(mContext, notes);

    if (midiFile == null) {
      return -1;
    }

    final int soundId = mSoundPool.load(midiFile.getPath(), 1);
    if (playOnLoad) {
      mPostLoadPlayables.add(soundId);
    }

    return soundId;
  }

  /** Class used for repeated playing of sound resources. */
  private class RepeatedSoundRunnable implements Runnable {
    private final int mResId;

    private final float mPlaybackRate;

    private final float mPlaybackVolume;

    private final int mRepetitions;

    private final long mDelay;

    private int mTimesPlayed;

    public RepeatedSoundRunnable(int resId, float rate, float volume, int repetitions, long delay) {
      mResId = resId;
      mPlaybackRate = rate;
      mPlaybackVolume = volume;
      mRepetitions = repetitions;
      mDelay = delay;

      mTimesPlayed = 0;
    }

    @Override
    public void run() {
      if (mTimesPlayed < mRepetitions) {
        playSound(mResId, mPlaybackRate, mPlaybackVolume);
        mTimesPlayed++;
        mHandler.postDelayed(this, mDelay);
      }
    }
  }
}

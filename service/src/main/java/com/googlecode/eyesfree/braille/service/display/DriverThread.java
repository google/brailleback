/*
 * Copyright (C) 2015 Google Inc.
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

package com.googlecode.eyesfree.braille.service.display;

import android.content.res.Resources;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import com.googlecode.eyesfree.braille.display.BrailleDisplayProperties;
import com.googlecode.eyesfree.braille.display.BrailleInputEvent;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

/**
 * A thread that manages an instance of a braille driver.
 *
 * <p>Threading model: The methods of this class can be called from any thread. Calls will be
 * forwarded to an internal thread and executed asynchronously. The methods of the nested callback
 * interfaces will be invoked in an internal thread and should therefore finish quickly without
 * blocking.
 */
public class DriverThread {
  private static final String LOG_TAG = DriverThread.class.getSimpleName();
  private static final long STOP_WAIT_MILLIS = 1000;
  /** Wake up the handler thread to poll for input from the device. */
  private static final int MSG_READ = 1;
  /** Wake up the thread to call writeWindow of the driver. */
  private static final int MSG_WRITE = 2;
  /** Gracefully stop driver thread. */
  private static final int MSG_STOP = 3;

  private static final int COMMAND_CODE_MASK = 0xffff;
  private static final int COMMAND_ARGUMENT_MASK = 0x7fff0000;
  private static final int COMMAND_ARGUMENT_SHIFT = 16;

  private final Handler mHandler;
  private final HandlerThread mHandlerThread;

  private byte[] writeBuffer;

  /** Stream for writing to the device. */
  private final OutputStream mOutputStream;

  private BrlttyWrapper mBrlttyWrapper;

  /**
   * Callback interface for getting notified when the driver initialization has either succeeded or
   * failed.
   */
  public interface OnInitListener {
    /**
     * Called when initialization either succeeded or failed. In the former case, {@code
     * displayProperties} is non-null and contains the properties of the connected display. If
     * initialization failed, {@code displayProperties} is {@code null}.
     */
    void onInit(BrailleDisplayProperties displayProperties);
  }

  /** Callback interface for input events from the display. */
  public interface OnInputEventListener {
    /**
     * Dispatch an input event to interested users. This is called from the driver thread, so any
     * lengthy processing should be deferred to a different thread.
     */
    void onInputEvent(BrailleInputEvent event);
  }

  private final OnInputEventListener mInputEventListener;

  public DriverThread(
      OutputStream outputStream,
      DeviceFinder.DeviceInfo deviceInfo,
      Resources resources,
      File tablesDir,
      final OnInitListener initListener,
      OnInputEventListener inputListener) {
    mOutputStream = outputStream;
    mInputEventListener = inputListener;
    mBrlttyWrapper = new BrlttyWrapper(deviceInfo, this, resources, tablesDir);
    mHandlerThread =
        new HandlerThread("DriverTrhead") {
          @Override
          protected void onLooperPrepared() {
            boolean success = mBrlttyWrapper.start();
            if (success) {
              initListener.onInit(mBrlttyWrapper.getDisplayProperties());
            } else {
              initListener.onInit(null);
              // Make sure we don't enter the event loop if the driver
              // couldn't initialize so we don't call the
              // driver further in that case.
              stopInternal();
            }
          }
        };
    mHandlerThread.start();
    mHandler = new DriverHandler(mHandlerThread.getLooper());
  }

  /**
   * Update the refreshable display with the given dot pattern. This method can be called from any
   * thread.
   */
  public void writeWindow(byte[] pattern) {
    synchronized (this) {
      writeBuffer = pattern;
    }
    mHandler.sendEmptyMessage(MSG_WRITE);
  }

  /**
   * Add bytes that have been read from the device and wake up the driver to read more input. This
   * can be called from any thread. The first {@code size} {@code bytes} are added.
   */
  public void addReadOperation(byte[] bytes, int size) throws IOException {
    mBrlttyWrapper.addBytesFromDevice(bytes, size);
    mHandler.sendEmptyMessage(MSG_READ);
  }

  /** Stop communication queue thread and cleanup. Called from any thread. */
  public void stop() {
    mHandler.sendEmptyMessage(MSG_STOP);
    Log.d(LOG_TAG, "Waiting for handler thread");
    try {
      // Wait for the handler thread for a bit to make it unlikely that
      // we have two driver threads running at the same time, which
      // would lead to all kinds of badness because of brltty's
      // single-threadedness.
      mHandlerThread.join(STOP_WAIT_MILLIS);
      if (mHandlerThread.isAlive()) {
        // Be deliberately verbose in the log.
        Log.e(
            LOG_TAG,
            "*** Driver thread takes very long to " + "terminate -- giving up waiting ***");
      }
    } catch (InterruptedException ex) {
      Log.e(LOG_TAG, "Joining with queueThread interrupted.");
    }
  }

  private void writeWindowInternal() {
    byte[] buffer = null;
    synchronized (this) {
      buffer = writeBuffer;
      writeBuffer = null;
    }
    if (buffer != null) {
      boolean result = mBrlttyWrapper.writeWindow(buffer);
    }
  }

  private void stopInternal() {
    mBrlttyWrapper.stop();
    mHandler.getLooper().quit();
  }

  /** Called from the driver to send raw bytes to the device. */
  public boolean sendBytesToDevice(byte[] bytes) {
    try {
      mOutputStream.write(bytes);
      return true;
    } catch (IOException ex) {
      Log.e(LOG_TAG, "Writing to braille device failed", ex);
    }
    return false;
  }

  /**
   * Called from the command logic in the driver to have the driver thread woken up to read another
   * command after {@code delayMillis} milliseconds.
   */
  public void readDelayed(long delayMillis) {
    mHandler.sendEmptyMessageDelayed(MSG_READ, delayMillis);
  }

  private class DriverHandler extends Handler {
    public DriverHandler(Looper looper) {
      super(looper);
    }

    @Override
    public void handleMessage(Message msg) {
      switch (msg.what) {
        case MSG_READ:
          // Note that tthere's no one to one mapping between these
          // messages (that is calls to addReadOperation) and actual
          // commands because data comes in arbitrary chunks from the
          // hardware.
          while (true) {
            int command = mBrlttyWrapper.readCommand();
            if (command < 0) {
              break;
            }
            // Command code is in the low 16 bits and the argument
            // in bits 16-30.
            mInputEventListener.onInputEvent(
                new BrailleInputEvent(
                    command & COMMAND_CODE_MASK,
                    ((command & COMMAND_ARGUMENT_MASK) >> COMMAND_ARGUMENT_SHIFT),
                    SystemClock.uptimeMillis()));
          }
          break;

        case MSG_WRITE:
          writeWindowInternal();
          break;

        case MSG_STOP:
          stopInternal();
          break;

        default:
          Log.i(LOG_TAG, "Incorrect type of operation.");
          return;
      }
    }
  }
}

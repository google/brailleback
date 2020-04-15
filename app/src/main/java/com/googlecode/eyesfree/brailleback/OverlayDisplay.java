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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import com.googlecode.eyesfree.braille.display.BrailleDisplayProperties;
import com.googlecode.eyesfree.braille.display.BrailleInputEvent;
import com.googlecode.eyesfree.braille.display.BrailleKeyBinding;
import com.googlecode.eyesfree.braille.display.Display;
import com.googlecode.eyesfree.utils.MotionEventUtils;
import com.googlecode.eyesfree.utils.SharedPreferencesUtils;
import com.googlecode.eyesfree.utils.WeakReferenceHandler;
import java.util.Collections;

/**
 * A display which may present an on-screen overlay which mirrors the content of the Braille
 * display.
 *
 * <p>Note: this display can connect very quickly. To avoid missing any connection state change
 * events, callers should set any necessary listeners before allowing control to return to the
 * {@link Looper} on the current thread.
 */
public final class OverlayDisplay
    implements Display,
        SharedPreferences.OnSharedPreferenceChangeListener,
        Display.OnConnectionStateChangeListener,
        Display.OnInputEventListener,
        BrailleView.OnBrailleCellClickListener,
        BrailleView.OnResizeListener {

  private final Display mBackingDisplay;
  private final MainThreadHandler mMainThreadHandler;
  private final DisplayThreadHandler mDisplayThreadHandler;
  private final Context mContext;
  private volatile OnConnectionStateChangeListener mConnectionStateChangeListener;
  private volatile OnInputEventListener mInputEventListener;
  private boolean mBackingDisplayConnected = false;
  private boolean mSimulateDisplay = false;
  private boolean mOverlayEnabled = false;
  private boolean mShutdown = false;

  // Used when the overlay is on, but no actual display is available.
  private BrailleDisplayProperties mSimulatedDisplayProperties =
      new BrailleDisplayProperties(
          0 /* numTextCells */,
          0 /* numStatusCells */,
          new BrailleKeyBinding[0] /* keyBindings */,
          Collections.<String, String>emptyMap() /* friendlyKeyNames */);

  public OverlayDisplay(Context context, Display backingDisplay) {
    mMainThreadHandler = new MainThreadHandler(context, this);
    mDisplayThreadHandler = new DisplayThreadHandler(this);
    mContext = context;
    mBackingDisplay = backingDisplay;
    mBackingDisplay.setOnConnectionStateChangeListener(this);
    mBackingDisplay.setOnInputEventListener(this);

    // Finish initializing based on user preferences later.
    // This gives the caller enough time to change the listeners, as long
    // as it is done before control is returned to the Looper.
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    prefs.registerOnSharedPreferenceChangeListener(this);
    mDisplayThreadHandler.reportPreferenceChange(prefs);
  }

  @Override
  public void setOnConnectionStateChangeListener(OnConnectionStateChangeListener listener) {
    mConnectionStateChangeListener = listener;
  }

  @Override
  public void setOnConnectionChangeProgressListener(OnConnectionChangeProgressListener listener) {
    mBackingDisplay.setOnConnectionChangeProgressListener(listener);
  }

  @Override
  public void setOnInputEventListener(OnInputEventListener listener) {
    mInputEventListener = listener;
  }

  @Override
  public BrailleDisplayProperties getDisplayProperties() {
    if (!mSimulateDisplay) {
      return mBackingDisplay.getDisplayProperties();
    } else {
      return mSimulatedDisplayProperties;
    }
  }

  @Override
  public void displayDots(byte[] patterns, CharSequence text, int[] brailleToTextPositions) {
    mBackingDisplay.displayDots(patterns, text, brailleToTextPositions);
    mMainThreadHandler.displayDots(getDisplayProperties(), patterns, text, brailleToTextPositions);
  }

  @Override
  public void poll() {
    mBackingDisplay.poll();
  }

  @Override
  public void shutdown() {
    mShutdown = true;
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
    prefs.unregisterOnSharedPreferenceChangeListener(this);
    mMainThreadHandler.hideOverlay();

    mBackingDisplay.shutdown();
  }

  @Override
  public boolean isSimulated() {
    return mSimulateDisplay;
  }

  @Override
  public void onConnectionStateChanged(int state) {
    mBackingDisplayConnected = (state == STATE_CONNECTED);
    if (mOverlayEnabled && state == STATE_CONNECTED) {
      disconnectSimulatedDisplay();
    }
    if (!mOverlayEnabled || !mSimulateDisplay) {
      // Don't forward disconnect events when the simulated display is
      // active. This prevents upstream disconnect messages from freezing
      // the simulated display.
      reportConnectionStateChange(state);
    }
    if (mOverlayEnabled && state != STATE_CONNECTED) {
      connectSimulatedDisplay();
    }
  }

  @Override
  public void onInputEvent(BrailleInputEvent inputEvent) {
    sendInputEvent(inputEvent);
    mMainThreadHandler.reportInputEvent(inputEvent);
  }

  private void sendInputEvent(BrailleInputEvent inputEvent) {
    OnInputEventListener localListener = mInputEventListener;
    if (localListener != null) {
      localListener.onInputEvent(inputEvent);
    }
  }

  private void updateFromSharedPreferences(SharedPreferences prefs) {
    mOverlayEnabled =
        SharedPreferencesUtils.getBooleanPref(
            prefs,
            mContext.getResources(),
            R.string.pref_braille_overlay_key,
            R.bool.pref_braille_overlay_default);
    if (mOverlayEnabled) {
      mMainThreadHandler.showOverlay();

      // Don't connect display yet. We'll connect when the overlay
      // has been shown.
    } else {
      mMainThreadHandler.hideOverlay();
      disconnectSimulatedDisplay();
    }
  }

  private void connectSimulatedDisplay() {
    if (!mSimulateDisplay && !mBackingDisplayConnected) {
      mSimulateDisplay = true;
      reportConnectionStateChange(STATE_CONNECTED);
    }
  }

  private void disconnectSimulatedDisplay() {
    if (mSimulateDisplay) {
      reportConnectionStateChange(STATE_NOT_CONNECTED);
      mSimulateDisplay = false;
    }
  }

  private void reportConnectionStateChange(int state) {
    OnConnectionStateChangeListener localListener = mConnectionStateChangeListener;
    if (localListener != null) {
      localListener.onConnectionStateChanged(state);
    }
  }

  private void resizeDisplay(int newSize) {
    boolean simulatedDisplayWasConnected = mSimulateDisplay;
    disconnectSimulatedDisplay();
    mSimulatedDisplayProperties =
        new BrailleDisplayProperties(
            newSize /* numTextCells */,
            mSimulatedDisplayProperties.getNumStatusCells(),
            mSimulatedDisplayProperties.getKeyBindings(),
            mSimulatedDisplayProperties.getFriendlyKeyNames());
    if (simulatedDisplayWasConnected) {
      connectSimulatedDisplay();
    }
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
    if (mContext.getString(R.string.pref_braille_overlay_key).equals(key)) {
      mDisplayThreadHandler.reportPreferenceChange(prefs);
    }
  }

  @Override
  public void onBrailleCellClick(BrailleView view, int cellIndex) {
    mDisplayThreadHandler.sendInputEvent(
        new BrailleInputEvent(BrailleInputEvent.CMD_ROUTE, cellIndex, SystemClock.uptimeMillis()));
  }

  @Override
  public void onResize(int newSize) {
    mDisplayThreadHandler.resizeDisplay(newSize);
  }

  /**
   * Main handler for the overlay display. All UI operations must occur through this handler. To
   * enforce this, the overlay is intentionally private.
   */
  private static class MainThreadHandler extends WeakReferenceHandler<OverlayDisplay> {
    private static final int MSG_SHOW = 1;
    private static final int MSG_HIDE = 2;
    private static final int MSG_DISPLAY_DOTS = 3;
    private static final int MSG_INPUT_EVENT = 4;

    private Context mContext;
    private BrailleOverlay mOverlay;

    // Used for passing screen content between threads.
    // Access should be synchronized.
    private BrailleDisplayProperties mDisplayProperties;
    private byte[] mBraille;
    private CharSequence mText;
    private int[] mBrailleToTextPositions;

    public MainThreadHandler(Context context, OverlayDisplay parent) {
      super(parent, Looper.getMainLooper());
      mContext = context;
    }

    public void showOverlay() {
      sendEmptyMessage(MSG_SHOW);
    }

    public void hideOverlay() {
      sendEmptyMessage(MSG_HIDE);
    }

    public void displayDots(
        BrailleDisplayProperties displayProperties,
        byte[] patterns,
        CharSequence text,
        int[] brailleToTextPositions) {
      synchronized (this) {
        mDisplayProperties = displayProperties;
        mBraille = patterns;
        mText = text;
        mBrailleToTextPositions = brailleToTextPositions;
      }
      sendEmptyMessage(MSG_DISPLAY_DOTS);
    }

    public void reportInputEvent(BrailleInputEvent event) {
      obtainMessage(MSG_INPUT_EVENT, event).sendToTarget();
    }

    @Override
    public void handleMessage(Message msg, OverlayDisplay parent) {
      switch (msg.what) {
        case MSG_SHOW:
          handleShow(parent);
          break;
        case MSG_HIDE:
          handleHide();
          break;
        case MSG_DISPLAY_DOTS:
          handleDisplayDots();
          break;
        case MSG_INPUT_EVENT:
          handleInputEvent((BrailleInputEvent) msg.obj);
          break;
      }
    }

    private void handleShow(OverlayDisplay parent) {
      if (mOverlay == null) {
        mOverlay = new BrailleOverlay(mContext, parent);
      }
      mOverlay.show();
      parent.mDisplayThreadHandler.connectSimulatedDisplay();
    }

    private void handleHide() {
      if (mOverlay != null) {
        mOverlay.hide();
        mOverlay = null;
      }
    }

    private void handleDisplayDots() {
      if (mOverlay == null) {
        return;
      }

      BrailleDisplayProperties displayProperties;
      byte[] braille;
      CharSequence text;
      int[] brailleToTextPositions;
      synchronized (this) {
        displayProperties = mDisplayProperties;
        braille = mBraille;
        text = mText;
        brailleToTextPositions = mBrailleToTextPositions;
      }

      BrailleView view = mOverlay.getBrailleView();
      view.setDisplayProperties(displayProperties);
      view.displayDots(braille, text, brailleToTextPositions);
    }

    private void handleInputEvent(BrailleInputEvent event) {
      if (mOverlay == null) {
        return;
      }

      if (BrailleInputEvent.argumentType(event.getCommand())
          == BrailleInputEvent.ARGUMENT_POSITION) {
        BrailleView view = mOverlay.getBrailleView();
        view.highlightCell(event.getArgument());
      }
    }
  }

  /**
   * Handler which runs on the display thread. This is necessary to handle events from the main
   * thread.
   */
  private static class DisplayThreadHandler extends WeakReferenceHandler<OverlayDisplay> {
    private static final int MSG_PREFERENCE_CHANGE = 1;
    private static final int MSG_RESIZE_DISPLAY = 2;
    private static final int MSG_SEND_INPUT_EVENT = 3;
    private static final int MSG_CONNECT_SIMULATED_DISPLAY = 4;

    public DisplayThreadHandler(OverlayDisplay parent) {
      super(parent);
    }

    public void reportPreferenceChange(SharedPreferences prefs) {
      obtainMessage(MSG_PREFERENCE_CHANGE, prefs).sendToTarget();
    }

    public void resizeDisplay(int newSize) {
      obtainMessage(MSG_RESIZE_DISPLAY, newSize, 0).sendToTarget();
    }

    public void sendInputEvent(BrailleInputEvent event) {
      obtainMessage(MSG_SEND_INPUT_EVENT, event).sendToTarget();
    }

    public void connectSimulatedDisplay() {
      sendEmptyMessage(MSG_CONNECT_SIMULATED_DISPLAY);
    }

    @Override
    protected void handleMessage(Message msg, OverlayDisplay parent) {
      if (parent.mShutdown) {
        return;
      }
      switch (msg.what) {
        case MSG_PREFERENCE_CHANGE:
          parent.updateFromSharedPreferences((SharedPreferences) msg.obj);
          break;
        case MSG_RESIZE_DISPLAY:
          parent.resizeDisplay(msg.arg1);
          break;
        case MSG_SEND_INPUT_EVENT:
          parent.sendInputEvent((BrailleInputEvent) msg.obj);
          break;
        case MSG_CONNECT_SIMULATED_DISPLAY:
          parent.connectSimulatedDisplay();
      }
    }
  }

  private static class BrailleOverlay extends DraggableOverlay {
    private final BrailleView mBrailleView;

    private final View.OnHoverListener mHoverForwarder =
        new View.OnHoverListener() {
          @Override
          public boolean onHover(View view, MotionEvent event) {
            MotionEvent touchEvent = MotionEventUtils.convertHoverToTouch(event);
            try {
              return view.dispatchTouchEvent(touchEvent);
            } finally {
              touchEvent.recycle();
            }
          }
        };

    public BrailleOverlay(Context context, final OverlayDisplay parent) {
      super(context);

      setContentView(R.layout.overlay);
      mBrailleView = (BrailleView) findViewById(R.id.braille_view);
      mBrailleView.setOnHoverListener(mHoverForwarder);
      mBrailleView.setOnBrailleCellClickListener(parent);
      mBrailleView.setOnResizeListener(parent);

      ImageButton panLeftButton = (ImageButton) findViewById(R.id.pan_left_button);
      panLeftButton.setOnHoverListener(mHoverForwarder);
      panLeftButton.setOnClickListener(
          new View.OnClickListener() {
            @Override
            public void onClick(View view) {
              parent.mDisplayThreadHandler.sendInputEvent(
                  new BrailleInputEvent(
                      BrailleInputEvent.CMD_NAV_PAN_LEFT,
                      0 /* argument */,
                      SystemClock.uptimeMillis()));
            }
          });

      ImageButton panRightButton = (ImageButton) findViewById(R.id.pan_right_button);
      panRightButton.setOnHoverListener(mHoverForwarder);
      panRightButton.setOnClickListener(
          new View.OnClickListener() {
            @Override
            public void onClick(View view) {
              parent.mDisplayThreadHandler.sendInputEvent(
                  new BrailleInputEvent(
                      BrailleInputEvent.CMD_NAV_PAN_RIGHT,
                      0 /* argument */,
                      SystemClock.uptimeMillis()));
            }
          });
    }

    public BrailleView getBrailleView() {
      return mBrailleView;
    }

    @Override
    protected void onStartDragging() {
      mBrailleView.cancelPendingTouches();
    }
  }
}

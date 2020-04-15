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
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import com.googlecode.eyesfree.utils.MotionEventUtils;
import com.googlecode.eyesfree.widget.SimpleOverlay;

/**
 * Overlay that can be long-pressed, and then dragged to the top or bottom of the screen.
 * Intermediate positions are not supported due to the complexity that arises from screen
 * orientation changes.
 */
public class DraggableOverlay extends SimpleOverlay {
  private static final int DEFAULT_GRAVITY = Gravity.BOTTOM;
  private static final int LONG_PRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout();
  private static final int TAP_TIMEOUT = ViewConfiguration.getTapTimeout();
  private final int mTouchSlopSquare;
  private final WindowManager mWindowManager;
  private final WindowManager.LayoutParams mWindowParams;
  private final View mTouchStealingView;
  private final WindowManager.LayoutParams mTouchStealingLayoutParams;
  private final InternalListener mInternalListener;
  private boolean mDragging = false;
  private float mDragOrigin;

  public DraggableOverlay(Context context) {
    super(context);
    mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

    // Compute touch slop.
    ViewConfiguration viewConfiguration = ViewConfiguration.get(context);
    int touchSlop = viewConfiguration.getScaledTouchSlop();
    mTouchSlopSquare = touchSlop * touchSlop;

    // Configure the overlay window.
    mWindowParams = createOverlayParams();
    mWindowParams.width = WindowManager.LayoutParams.MATCH_PARENT;
    mWindowParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
    mWindowParams.gravity = DEFAULT_GRAVITY;
    mWindowParams.windowAnimations = android.R.style.Animation_Translucent;
    setParams(mWindowParams);

    // Listen to touch events.
    mInternalListener = new InternalListener();
    getRootView().setOnHoverListener(mInternalListener);
    getRootView().setOnTouchListener(mInternalListener);

    // Prepare another view which can grab touch events for the entire
    // screen during dragging.
    mTouchStealingView = new View(context);
    mTouchStealingView.setOnHoverListener(mInternalListener);
    mTouchStealingView.setOnTouchListener(mInternalListener);
    WindowManager.LayoutParams lp = createOverlayParams();
    lp.width = WindowManager.LayoutParams.MATCH_PARENT;
    lp.height = WindowManager.LayoutParams.MATCH_PARENT;
    lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
    mTouchStealingLayoutParams = lp;
  }

  private static WindowManager.LayoutParams createOverlayParams() {
    WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
      lp.type = WindowManager.LayoutParams.TYPE_SYSTEM_ERROR;
    } else {
      lp.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
    }
    lp.format = PixelFormat.TRANSPARENT;
    lp.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
    lp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
    return lp;
  }

  private void startDragging(MotionEvent event) {
    if (mDragging) {
      return;
    }

    mDragging = true;
    mDragOrigin = event.getRawY();
    onStartDragging();
    mWindowParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
    setParams(mWindowParams);
    mWindowManager.addView(mTouchStealingView, mTouchStealingLayoutParams);
    getRootView().performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
  }

  private void stopDragging() {
    if (!mDragging) {
      return;
    }

    mDragging = false;
    mWindowParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
    if (mWindowParams.y > mTouchStealingView.getHeight() / 2) {
      switch (mWindowParams.gravity & Gravity.VERTICAL_GRAVITY_MASK) {
        case Gravity.BOTTOM:
          mWindowParams.gravity &= ~Gravity.VERTICAL_GRAVITY_MASK;
          mWindowParams.gravity |= Gravity.TOP;
          break;
        case Gravity.TOP:
          mWindowParams.gravity &= ~Gravity.VERTICAL_GRAVITY_MASK;
          mWindowParams.gravity |= Gravity.BOTTOM;
          break;
      }
    }
    mWindowParams.y = 0;
    setParams(mWindowParams);
    mWindowManager.removeViewImmediate(mTouchStealingView);
  }

  private void cancelDragging() {
    if (!mDragging) {
      return;
    }

    mDragging = false;
    mWindowParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
    mWindowParams.y = 0;
    setParams(mWindowParams);
    mWindowManager.removeViewImmediate(mTouchStealingView);
  }

  private void drag(MotionEvent event) {
    if (!mDragging) {
      return;
    }

    switch (mWindowParams.gravity & Gravity.VERTICAL_GRAVITY_MASK) {
      case Gravity.BOTTOM:
        mWindowParams.y = (int) (mDragOrigin - event.getRawY());
        break;
      case Gravity.TOP:
        mWindowParams.y = (int) (event.getRawY() - mDragOrigin);
        break;
    }
    setParams(mWindowParams);
  }

  protected void onStartDragging() {
    // Intentionally left blank.
  }

  private final class InternalListener
      implements View.OnTouchListener, View.OnHoverListener, Handler.Callback {

    private static final int MSG_LONG_PRESS = 1;
    private final Handler mHandler = new Handler(this);
    private float touchStartX;
    private float touchStartY;

    @Override
    public boolean onHover(View view, MotionEvent event) {
      MotionEvent touchEvent = MotionEventUtils.convertHoverToTouch(event);
      try {
        return onTouch(view, touchEvent);
      } finally {
        touchEvent.recycle();
      }
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
      switch (event.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          if (view != mTouchStealingView) {
            touchStartX = event.getRawX();
            touchStartY = event.getRawY();
            long timeout = (long) TAP_TIMEOUT + LONG_PRESS_TIMEOUT;
            mHandler.sendMessageAtTime(
                mHandler.obtainMessage(MSG_LONG_PRESS, event), event.getEventTime() + timeout);
          }
          break;

        case MotionEvent.ACTION_UP:
          mHandler.removeMessages(MSG_LONG_PRESS);
          if (view == mTouchStealingView) {
            stopDragging();
          }
          break;

        case MotionEvent.ACTION_CANCEL:
          mHandler.removeMessages(MSG_LONG_PRESS);
          cancelDragging();
          break;

        case MotionEvent.ACTION_MOVE:
          float distanceX = event.getRawX() - touchStartX;
          float distanceY = event.getRawY() - touchStartY;
          float distanceSquare = distanceX * distanceX + distanceY * distanceY;
          if (distanceSquare > mTouchSlopSquare) {
            mHandler.removeMessages(MSG_LONG_PRESS);
          }
          drag(event);
          break;
      }

      return false;
    }

    @Override
    public boolean handleMessage(Message msg) {
      switch (msg.what) {
        case MSG_LONG_PRESS:
          startDragging((MotionEvent) msg.obj);
          break;
      }
      return true;
    }
  }
}

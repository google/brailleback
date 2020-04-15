/*
 * Copyright (C) 2010 Google Inc.
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

package com.googlecode.eyesfree.widget;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import com.googlecode.eyesfree.compat.view.MotionEventCompatUtils;
import java.lang.reflect.Method;

/**
 * A transparent overlay which catches all touch events and uses a call back to return the gesture
 * that the user performed. Includes edge events.
 *
 * @author clchen@google.com (Charles L. Chen), credo@google.com (Tim Credo)
 */
public class GestureOverlay extends View {
  public static class Gesture {
    public static final int UPLEFT = 1;

    public static final int UP = 2;

    public static final int UPRIGHT = 3;

    public static final int LEFT = 4;

    public static final int CENTER = 5;

    public static final int RIGHT = 6;

    public static final int DOWNLEFT = 7;

    public static final int DOWN = 8;

    public static final int DOWNRIGHT = 9;

    public static final int EDGELEFT = 10;

    public static final int EDGERIGHT = 11;

    public static final int DOUBLE_TAP = 12;
  }

  /** The callback interface to be used when a gesture is detected. */
  public interface GestureListener {
    public void onGestureStart(int g);

    public void onGestureChange(int g);

    public void onGestureFinish(int g);
  }

  private final double left = 0;

  private final double upleft = Math.PI * .25;

  private final double up = Math.PI * .5;

  private final double upright = Math.PI * .75;

  private final double downright = -Math.PI * .75;

  private final double down = -Math.PI * .5;

  private final double downleft = -Math.PI * .25;

  private final double right = Math.PI;

  private final double rightWrap = -Math.PI;

  private GestureListener cb = null;

  private double downX;

  private double downY;

  private int currentGesture;

  private int radiusThreshold = 30;

  private boolean edgeGesture = false;

  int leftEdge;
  int rightEdge;

  private DoubleTapHandler tapHandler;

  private long doubleTapWindow = 700;

  private boolean isTap;

  private long lastTapTime;

  private boolean touchExploring = false;

  /** Handlers for touch exploration */
  private AccessibilityManager accessibilityManager;

  private static Method AccessibilityManager_isTouchExplorationEnabled;

  static {
    initCompatibility();
  }

  private static void initCompatibility() {
    try {
      AccessibilityManager_isTouchExplorationEnabled =
          AccessibilityManager.class.getMethod("isTouchExplorationEnabled");
      /* success, this is a newer device */
    } catch (NoSuchMethodException nsme) {
      /* failure, must be older device */
    }
  }

  public GestureOverlay(Context context, GestureListener callback) {
    super(context);
    setGestureListener(callback);
    initAccessibilitySettings(context);
  }

  public GestureOverlay(Context context) {
    super(context);
    initAccessibilitySettings(context);
  }

  public void setGestureListener(GestureListener callback) {
    cb = callback;
    tapHandler = new DoubleTapHandler();
  }

  private void initAccessibilitySettings(Context context) {
    // Check is touch exploration is on
    accessibilityManager =
        (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);

    try {
      if (AccessibilityManager_isTouchExplorationEnabled != null) {
        Object retobj = AccessibilityManager_isTouchExplorationEnabled.invoke(accessibilityManager);
        touchExploring = (Boolean) retobj;
      }
    } catch (Exception e) {
      Log.e("GestureOverlay", "Failed to get Accessibility Manager " + e.toString());
    }
  }

  public void setMinimumRadius(int minRadius) {
    radiusThreshold = minRadius;
  }

  @Override
  public boolean onHoverEvent(MotionEvent event) {
    return onTouchEvent(event);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    int action = event.getAction();
    float x = event.getX();
    float y = event.getY();
    int prevGesture = -1;
    switch (action) {
      case MotionEventCompatUtils.ACTION_HOVER_ENTER:
      case MotionEvent.ACTION_DOWN:
        downX = x;
        downY = y;
        isTap = true;
        leftEdge = getWidth() / 7;
        rightEdge = getWidth() - getWidth() / 7;
        if (x < leftEdge) {
          currentGesture = Gesture.EDGELEFT;
          edgeGesture = true;
        } else if (x > rightEdge) {
          currentGesture = Gesture.EDGERIGHT;
          edgeGesture = true;
        } else {
          currentGesture = Gesture.CENTER;
          edgeGesture = false;
        }
        if (cb != null) cb.onGestureStart(currentGesture);
        break;
      case MotionEventCompatUtils.ACTION_HOVER_EXIT:
      case MotionEvent.ACTION_UP:
        prevGesture = currentGesture;
        currentGesture = evalMotion(x, y);
        // Do some correction if the user lifts up on deadspace
        if (currentGesture == -1) {
          currentGesture = prevGesture;
        }
        if (cb != null) {
          if (touchExploring && action == MotionEvent.ACTION_UP) {
            currentGesture = Gesture.DOUBLE_TAP;
            cb.onGestureFinish(currentGesture);
            return true;
          } else if (!touchExploring) {
            // Handle double-tap vs. tap
            if (isTap) {
              if (lastTapTime + doubleTapWindow > System.currentTimeMillis()) {
                currentGesture = Gesture.DOUBLE_TAP;
                tapHandler.cancelDoubleTapWindow();
              } else {
                lastTapTime = System.currentTimeMillis();
                tapHandler.startDoubleTapWindow();
                return true;
              }
            }
          }
          cb.onGestureFinish(currentGesture);
        }
        break;
      default:
        prevGesture = currentGesture;
        currentGesture = evalMotion(x, y);
        // Do some correction if the user lifts up on deadspace
        if (currentGesture == -1) {
          currentGesture = prevGesture;
          break;
        }
        if (prevGesture != currentGesture) {
          if (cb != null) {
            cb.onGestureChange(currentGesture);
          }
        }
        break;
    }
    return true;
  }

  public int evalMotion(double x, double y) {
    float rTolerance = radiusThreshold;
    double thetaTolerance = (Math.PI / 12);

    if (edgeGesture) {
      if (x < leftEdge && downX < leftEdge) {
        return Gesture.EDGELEFT;
      } else if (x > rightEdge && downX > rightEdge) {
        return Gesture.EDGERIGHT;
      } else {
        edgeGesture = false;
      }
    }

    double r = Math.sqrt(((downX - x) * (downX - x)) + ((downY - y) * (downY - y)));

    // Check if gesture is close to down point
    if (r < rTolerance) {
      return Gesture.CENTER;
    }

    // We have moved: tap not possible
    isTap = false;

    double theta = Math.atan2(downY - y, downX - x);
    if (Math.abs(theta - left) < thetaTolerance) {
      return Gesture.LEFT;
    } else if (Math.abs(theta - upleft) < thetaTolerance) {
      return Gesture.UPLEFT;
    } else if (Math.abs(theta - up) < thetaTolerance) {
      return Gesture.UP;
    } else if (Math.abs(theta - upright) < thetaTolerance) {
      return Gesture.UPRIGHT;
    } else if (Math.abs(theta - downright) < thetaTolerance) {
      return Gesture.DOWNRIGHT;
    } else if (Math.abs(theta - down) < thetaTolerance) {
      return Gesture.DOWN;
    } else if (Math.abs(theta - downleft) < thetaTolerance) {
      return Gesture.DOWNLEFT;
    } else if ((theta > right - thetaTolerance) || (theta < rightWrap + thetaTolerance)) {
      return Gesture.RIGHT;
    }
    // Off by more than the threshold, so it doesn't count
    return -1;
  }

  private class DoubleTapHandler extends Handler {
    // Double-tap window handler
    private static final int MSG_DOUBLE_TAP_TIMEOUT = 0;

    /** Handles a double-tap timeout message by calling onGestureFinish */
    @Override
    public void handleMessage(Message msg) {
      if (msg.what == MSG_DOUBLE_TAP_TIMEOUT) {
        cb.onGestureFinish(currentGesture);
      }
    }

    /** Sends the delayed double-tap timeout message */
    public void startDoubleTapWindow() {
      sendEmptyMessageDelayed(MSG_DOUBLE_TAP_TIMEOUT, doubleTapWindow);
    }

    /** Cancels the double-tap message */
    public void cancelDoubleTapWindow() {
      removeMessages(MSG_DOUBLE_TAP_TIMEOUT);
    }
  }
}

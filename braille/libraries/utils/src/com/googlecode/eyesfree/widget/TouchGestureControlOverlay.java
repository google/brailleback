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

package com.googlecode.eyesfree.widget;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;

/**
 * A transparent overlay which catches all touch events and uses a call back to
 * return the gesture that the user performed.
 * 
 * @author clchen@google.com (Charles L. Chen)
 * 
 */

public class TouchGestureControlOverlay extends View {

  /**
   * An enumeration of the possible gestures.
   */
  public enum Gesture {
    UPLEFT, UP, UPRIGHT, LEFT, CENTER, RIGHT, DOWNLEFT, DOWN, DOWNRIGHT
  }

  /**
   * The callback interface to be used when a gesture is detected.
   */
  public interface GestureListener {
    public void onGestureStart(Gesture g);

    public void onGestureChange(Gesture g);

    public void onGestureFinish(Gesture g);
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
  private Gesture currentGesture;

  public TouchGestureControlOverlay(Context context, GestureListener callback) {
    super(context);
    cb = callback;
  }

  public TouchGestureControlOverlay(Context context) {
    super(context);
  }

  public void setGestureListener(GestureListener callback) {
    cb = callback;
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    int action = event.getAction();
    float x = event.getX();
    float y = event.getY();
    Gesture prevGesture = null;
    switch (action) {
      case MotionEvent.ACTION_DOWN:
        downX = x;
        downY = y;
        currentGesture = Gesture.CENTER;
        if (cb != null) {
          cb.onGestureStart(currentGesture);
        }
        break;
      case MotionEvent.ACTION_UP:
        prevGesture = currentGesture;
        currentGesture = evalMotion(x, y);
        // Do some correction if the user lifts up on deadspace
        if (currentGesture == null) {
          currentGesture = prevGesture;
        }
        if (cb != null) {
          cb.onGestureFinish(currentGesture);
        }
        break;
      default:
        prevGesture = currentGesture;
        currentGesture = evalMotion(x, y);
        // Do some correction if the user lifts up on deadspace
        if (currentGesture == null) {
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

  public Gesture evalMotion(double x, double y) {
    float rTolerance = 25;
    double thetaTolerance = (Math.PI / 12);

    double r = Math.sqrt(((downX - x) * (downX - x)) + ((downY - y) * (downY - y)));

    if (r < rTolerance) {
      return Gesture.CENTER;
    }

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
    return null;
  }

}

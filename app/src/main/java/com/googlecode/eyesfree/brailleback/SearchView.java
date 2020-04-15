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
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.GradientDrawable.Orientation;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

/** View handling drawing of incremental search overlay. */
public class SearchView extends SurfaceView {

  /** The paint used to draw this view. */
  private final Paint mPaint;

  /** The surface holder onto which the view is drawn. */
  private SurfaceHolder mHolder;

  /** The background. */
  private GradientDrawable mGradientBackground;

  /**
   * The actual text that will be shown. Synced to the StringBuilder in SearchNavigationMode so we
   * shouldn't modify it here.
   */
  private final StringBuilder mQueryText;

  private Context mContext;

  private final SurfaceHolder.Callback mSurfaceCallback =
      new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
          mHolder = holder;
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
          mHolder = null;
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
          invalidate();
        }
      };

  public SearchView(Context context, StringBuilder queryText) {
    super(context);

    mContext = context;
    mQueryText = queryText;

    mPaint = new Paint();
    mPaint.setAntiAlias(true);

    final SurfaceHolder holder = getHolder();
    holder.setFormat(PixelFormat.TRANSLUCENT);
    holder.addCallback(mSurfaceCallback);

    final Resources res = context.getResources();

    int mExtremeRadius = 128;

    // Gradient colors.
    final int gradientInnerColor = res.getColor(R.color.search_overlay);
    final int gradientOuterColor = res.getColor(R.color.search_overlay);
    final int[] colors = new int[] {gradientInnerColor, gradientOuterColor};
    mGradientBackground = new GradientDrawable(Orientation.TOP_BOTTOM, colors);
    mGradientBackground.setGradientType(GradientDrawable.LINEAR_GRADIENT);
  }

  public void show() {
    invalidate();
  }

  @Override
  public void invalidate() {
    super.invalidate();

    final SurfaceHolder holder = mHolder;
    if (holder == null) {
      return;
    }

    final Canvas canvas = holder.lockCanvas();
    if (canvas == null) {
      return;
    }

    // Clear the canvas.
    canvas.drawColor(Color.TRANSPARENT, Mode.CLEAR);

    if (getVisibility() != View.VISIBLE) {
      holder.unlockCanvasAndPost(canvas);
      return;
    }

    final int width = getWidth();
    final int height = getHeight();

    // Draw the pretty gradient background.
    mGradientBackground.setBounds(0, 0, width, height);
    mGradientBackground.draw(canvas);

    Paint paint = new Paint();
    paint.setColor(Color.WHITE);
    paint.setStyle(Style.FILL);
    paint.setTextAlign(Align.CENTER);
    paint.setTextSize(mContext.getResources().getDimensionPixelSize(R.dimen.search_text_font_size));
    canvas.drawText(
        mContext.getString(R.string.search_dialog_label, mQueryText),
        width / 2.0f,
        height / 2.0f,
        paint);

    holder.unlockCanvasAndPost(canvas);
  }
}

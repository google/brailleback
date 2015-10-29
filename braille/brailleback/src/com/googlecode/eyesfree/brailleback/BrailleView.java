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

import com.googlecode.eyesfree.braille.display.BrailleDisplayProperties;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * View which displays Braille dot patterns and corresponding text.
 * Requires a mapping between the two to provide proper alignment.
 */
public class BrailleView extends View {

    /**
     * Interface for listening to taps on Braille cells.
     */
    public interface OnBrailleCellClickListener {
        void onBrailleCellClick(BrailleView view, int cellIndex);
    }

    /**
     * Interface for listening to width changes, expressed in terms of the
     * number of Braille cells that can be displayed.
     */
    public interface OnResizeListener {
        void onResize(int maxNumTextCells);
    }

    private static final int HIGHLIGHT_TIME_MS = 300;
    private static final int DIMMED_ALPHA = 0x40;
    private static final float[] DOT_POSITIONS = {
        0.0f, 0.00f, /* dot 1 */
        0.0f, 0.33f, /* dot 2 */
        0.0f, 0.67f, /* dot 3 */
        1.0f, 0.00f, /* dot 4 */
        1.0f, 0.33f, /* dot 5 */
        1.0f, 0.67f, /* dot 6 */
        0.0f, 1.00f, /* dot 7 */
        1.0f, 1.00f  /* dot 8 */
    };

    private final Runnable mClearHighlightedCell = new Runnable() {
        @Override
        public void run() {
            mHighlightedCell = -1;
            invalidate();
        }
    };
    private final Paint mPrimaryPaint;
    private final Paint mSecondaryPaint;
    private final Drawable mHighlightDrawable;
    private final float mDotRadius;
    private final float mCellPadding;
    private final float mCellWidth;
    private final float mCellHeight;
    private final float mOuterWidth;
    private final float mOuterHeight;
    private final int mTouchSlop;

    private volatile OnResizeListener mResizeListener;
    private volatile OnBrailleCellClickListener mBrailleCellClickListener;
    private int mNumTextCells = 0;
    private byte[] mBraille = new byte[0];
    private CharSequence mText = "";
    private int[] mBrailleToTextPositions = new int[0];
    private int mMaxNumTextCells = 0;
    private int mHighlightedCell = -1;
    private int mPressedCell = -1;

    public BrailleView(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs, R.styleable.BrailleView, 0, 0);
        try {
            mPrimaryPaint = new Paint();
            mPrimaryPaint.setAntiAlias(true);
            mPrimaryPaint.setColor(a.getColor(
                    R.styleable.BrailleView_android_textColor, 0xFFFFFFFF));
            mPrimaryPaint.setTextSize(a.getDimension(
                    R.styleable.BrailleView_android_textSize, 20.0f));
            mPrimaryPaint.setTextAlign(Paint.Align.CENTER);
            mSecondaryPaint = new Paint(mPrimaryPaint);
            mSecondaryPaint.setAlpha(DIMMED_ALPHA);
            mHighlightDrawable = a.getDrawable(
                    R.styleable.BrailleView_highlightDrawable);
            mDotRadius = a.getDimension(
                    R.styleable.BrailleView_dotRadius, 4.0f);
            mCellWidth = a.getDimension(
                    R.styleable.BrailleView_cellWidth, 10.0f);
            mCellHeight = a.getDimension(
                    R.styleable.BrailleView_cellHeight, 30.0f);
            mCellPadding = a.getDimension(
                    R.styleable.BrailleView_cellPadding, 13.0f);
            mOuterWidth = mCellWidth + 2 * mCellPadding;
            mOuterHeight = mCellHeight + 2 * mCellPadding;
            mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        } finally {
            a.recycle();
        }
    }

    public void displayDots(byte[] braille, CharSequence text,
            int[] brailleToTextPositions) {
        mBraille = braille;
        mText = text;
        mBrailleToTextPositions = brailleToTextPositions;
        invalidate();
    }

    public void setDisplayProperties(
            BrailleDisplayProperties displayProperties) {
        if (displayProperties.getNumTextCells() != mNumTextCells) {
            mNumTextCells = displayProperties.getNumTextCells();
            requestLayout();
        }
    }

    public void setOnResizeListener(OnResizeListener listener) {
        mResizeListener = listener;
    }

    public void highlightCell(int cellIndex) {
        mHighlightedCell = cellIndex;
        removeCallbacks(mClearHighlightedCell);
        postDelayed(mClearHighlightedCell, HIGHLIGHT_TIME_MS);
        invalidate();
    }

    public void setOnBrailleCellClickListener(
            OnBrailleCellClickListener listener) {
        mBrailleCellClickListener = listener;
    }

    public void cancelPendingTouches() {
        if (mPressedCell != -1) {
            mPressedCell = -1;
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        Paint.FontMetrics metrics = mPrimaryPaint.getFontMetrics();

        int width = MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED
                ? Math.round(mNumTextCells * mOuterWidth)
                : MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(width,
            Math.round(mOuterHeight + mCellPadding
                    - metrics.ascent + metrics.descent));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Determine the actual rectangle on which the dots will be rendered.
        // We assume 8-dot braille; 6-dot braille will simply have empty bottom
        // dots. So this rectangle must be 3 times as tall as it is wide.
        float innerWidth;
        float innerHeight;
        float offsetX;
        float offsetY;
        if (3 * mCellWidth >= mCellHeight) {
            // Add more horizontal padding.
            innerWidth = mCellHeight / 3;
            innerHeight = mCellHeight;
            offsetX = mCellPadding + (mCellWidth - innerWidth) / 2;
            offsetY = mCellPadding;
        } else {
            // Add more vertical padding.
            innerWidth = mCellWidth;
            innerHeight = mCellWidth * 3;
            offsetX = mCellPadding;
            offsetY = mCellPadding + (mCellHeight - innerHeight) / 2;
        }

        // Draw the highlighted cell.
        if (mHighlightedCell >= 0 && mHighlightedCell < mNumTextCells
                && mHighlightDrawable != null) {
            mHighlightDrawable.setBounds(
                    Math.round(mHighlightedCell * mOuterWidth), 0,
                    Math.round((mHighlightedCell + 1) * mOuterWidth),
                    Math.round(mOuterHeight));
            mHighlightDrawable.draw(canvas);
        }

        // Draw the pressed cell, if different.
        if (mPressedCell >= 0 && mPressedCell < mNumTextCells
                && mPressedCell != mHighlightedCell
                && mHighlightDrawable != null) {
            mHighlightDrawable.setBounds(
                    Math.round(mPressedCell * mOuterWidth), 0,
                    Math.round((mPressedCell + 1) * mOuterWidth),
                    Math.round(mOuterHeight));
            mHighlightDrawable.draw(canvas);
        }

        // Draw dot patterns.
        // Note that mBraille.length may not match mNumTextCells.
        for (int i = 0; i < mNumTextCells; i++) {
            canvas.save();
            canvas.translate(i * mOuterWidth, 0);
            canvas.clipRect(0, 0, mOuterWidth, mOuterHeight);
            int pattern = i < mBraille.length ? (mBraille[i] & 0xFF) : 0x00;
            for (int j = 0; j < DOT_POSITIONS.length; j += 2) {
                float x = offsetX + DOT_POSITIONS[j] * innerWidth;
                float y = offsetY + DOT_POSITIONS[j + 1] * innerHeight;
                Paint paint = (pattern & 1) != 0
                        ? mPrimaryPaint : mSecondaryPaint;
                canvas.drawCircle(x, y, mDotRadius, paint);
                pattern = pattern >> 1;
            }
            canvas.restore();
        }

        // Draw corresponding text.
        Paint.FontMetrics metrics = mPrimaryPaint.getFontMetrics();
        int brailleIndex = 0;
        while (brailleIndex < mNumTextCells
                && brailleIndex < mBrailleToTextPositions.length) {
            int brailleStart = brailleIndex;
            int textStart = mBrailleToTextPositions[brailleStart];
            do {
                brailleIndex++;
            } while (brailleIndex < mBrailleToTextPositions.length
                    && mBrailleToTextPositions[brailleIndex] <= textStart);
            int brailleEnd = brailleIndex;
            int textEnd = brailleEnd < mBrailleToTextPositions.length
                    ? mBrailleToTextPositions[brailleEnd] : mText.length();

            float clipLeft = mOuterWidth * brailleStart;
            float clipRight = mOuterWidth * brailleEnd;
            float clipTop = mOuterHeight;
            float clipBottom = clipTop + mCellPadding
                    - metrics.ascent + metrics.descent + mCellPadding;
            float x = (clipLeft + clipRight) / 2;
            float y = clipTop - metrics.ascent;
            float measuredWidth = mPrimaryPaint.measureText(
                    mText, textStart, textEnd);
            if (measuredWidth > clipRight - clipLeft) {
                mPrimaryPaint.setTextScaleX(
                        (clipRight - clipLeft) / measuredWidth);
            }
            canvas.save(Canvas.CLIP_SAVE_FLAG);
            canvas.clipRect(clipLeft, clipTop, clipRight, clipBottom);
            canvas.drawText(mText, textStart, textEnd, x, y, mPrimaryPaint);
            canvas.restore();
            mPrimaryPaint.setTextScaleX(1.0f);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        int newMaxNumTextCells = (int) (w / mOuterWidth);
        if (newMaxNumTextCells == mMaxNumTextCells) {
            return;
        }

        mMaxNumTextCells = newMaxNumTextCells;
        OnResizeListener localListener = mResizeListener;
        if (localListener != null) {
            localListener.onResize(newMaxNumTextCells);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                int cellIndex = (int) (event.getX() / mOuterWidth);
                if (0 <= cellIndex && cellIndex < mNumTextCells) {
                    mPressedCell = cellIndex;
                    invalidate();
                } else {
                    cancelPendingTouches();
                }
                break;

            case MotionEvent.ACTION_UP:
                if (withinTouchSlopOfCell(event, mPressedCell)) {
                    reportBrailleCellClick(mPressedCell);
                }
                cancelPendingTouches();
                break;

            case MotionEvent.ACTION_CANCEL:
                cancelPendingTouches();
                break;

            case MotionEvent.ACTION_MOVE:
                if (!withinTouchSlopOfCell(event, mPressedCell)) {
                    cancelPendingTouches();
                }
                break;
        }
        return false;
    }

    private boolean withinTouchSlopOfCell(MotionEvent event, int cellIndex) {
        if (0 <= cellIndex && cellIndex < mNumTextCells) {
            float x = event.getX();
            return (cellIndex * mOuterWidth - mTouchSlop) <= x
                && x <= ((cellIndex + 1) * mOuterWidth + mTouchSlop);
        } else {
            return false;
        }
    }

    private void reportBrailleCellClick(int cellIndex) {
        OnBrailleCellClickListener localListener = mBrailleCellClickListener;
        if (localListener != null) {
            localListener.onBrailleCellClick(this, cellIndex);
        }
    }
}


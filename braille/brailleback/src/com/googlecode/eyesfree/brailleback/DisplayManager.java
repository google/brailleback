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

package com.googlecode.eyesfree.brailleback;

import com.googlecode.eyesfree.braille.display.BrailleInputEvent;
import com.googlecode.eyesfree.braille.display.Display;
import com.googlecode.eyesfree.braille.display.DisplayClient;
import com.googlecode.eyesfree.braille.translate.BrailleTranslator;
import com.googlecode.eyesfree.braille.translate.TranslationResult;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;
import com.googlecode.eyesfree.utils.LogUtils;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.Spanned;
import android.util.Log;
import android.util.SparseIntArray;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Keeps track of the current display content and handles panning.
 */
public class DisplayManager
        implements Display.OnConnectionStateChangeListener,
                   Display.OnInputEventListener,
                   TranslatorManager.OnTablesChangedListener {

    /** Dot pattern used to overlay characters under a selection. */
    // TODO: Make customizable.
    private static final int SELECTION_DOTS = 0xC0;
    /** Dot pattern used to overlay characters in a focused element. */
    // TODO: Make customizable.
    private static final int FOCUS_DOTS = 0xC0;

    private static final long BLINK_OFF_MILLIS = 800;
    private static final long BLINK_ON_MILLIS = 600;

    /**
     * Callback interface for notifying interested callers when the display is
     * panned out of the available content.  A typical reaction to such an
     * event would be to move focus to a different area of the screen and
     * display it.
     */
    public interface OnPanOverflowListener {
        void onPanLeftOverflow(Content content);
        void onPanRightOverflow(Content content);
    }

    /**
     * Listener for input events that also get information about the current
     * display content and position mapping for commands with a positional
     * argument.
     */
    public interface OnMappedInputEventListener {
        /**
         * Handles an input {@code event} that was received when
         * {@code content} was present on the display.
         *
         * If the input event has a positional argument, it is mapped
         * according to the display pan position in the content so that
         * it corresponds to the character that the user touched.
         *
         * {@code event} and {@code content} are owned by the caller and may
         * not be referenced after this method returns.
         *
         * NOTE: Since the display is updated asynchronously, there is a chance
         * that the actual content on the display when the user invoked
         * the command is different from {@code content}.
         */
        void onMappedInputEvent(BrailleInputEvent event, Content content);
    }

    /**
     * Builder-like class used to construct the content to put on the display.
     *
     * This object contains a {@link CharSequence} that represents what
     * characters to put on the display.  This sequence can be a
     * {@link Spannable} so that the characters can be annotated with
     * information about cursors and focus which will affect how the content
     * is presented on the display.  Arbitrary java objects may also be
     * included in the {@link Spannable} which can be used to determine what
     * action to take when the user invokes key commands related to a
     * particular position on the display (i.e. involving a cursor routing
     * key).  In particular, {@link AccessibilityNodeInfoCompat}s may be
     * included, in which case they will be recycled by the
     * {@link Content#recycle} method.  To facilitate movement outside the
     * bounds of the current {@link Content},
     * {@link AccessibilityNodeInfoCompat}s that represent the extent of the
     * content can also be added, but in that case, they are not included in
     * the {@link Spannable}.
     */
    public static class Content {
        /**
         * Pan strategy that moves the display to the leftmost position.
         * This is the default panning strategy.
         */
        public static final int PAN_RESET = 0;

        /**
         * Pan strategy that positions the display so that it overlaps the
         * start of a selection or focus mark.  Falls back on {@code PAN_RESET}
         * if there is no selection or focus.
         */
        public static final int PAN_CURSOR = 1;

        /**
         * Pan strategy that tries to position the display close to the
         * position that corresponds to the panning position in the previously
         * displayed content.  Spans of type
         * {@link AccessibilityNodeInfoCompat} are used to identify the
         * corresponding content in the old and new display content.
         * Falls back on {@code SPAN_CURSOR} if a corresponding position can't
         * be found.
         */
        public static final int PAN_KEEP = 2;

        /**
         * Default contraction behaviour, allow contractions unless there is a
         * selection span in the content.
         */
        public static final int CONTRACT_DEFAULT = 0;

        /**
         * Allow contraction, regardless of the presence of a selection
         * span.
         */
        public static final int CONTRACT_ALWAYS_ALLOW = 1;

        private CharSequence mText;
        private AccessibilityNodeInfoCompat mFirstNode;
        private AccessibilityNodeInfoCompat mLastNode;
        private int mPanStrategy;
        private int mContractionMode;
        private boolean mSplitParagraphs;

        public Content() {
        }

        /**
         * Shortcut to just set text for a one-off use.
         */
        public Content(CharSequence text) {
            mText = text;
        }

        public Content setText(CharSequence text) {
            mText = text;
            return this;
        }

        public CharSequence getText() {
            return mText;
        }

        public Spanned getSpanned() {
            if (mText instanceof Spanned) {
                return (Spanned) mText;
            }
            return null;
        }

        public Content setFirstNode(AccessibilityNodeInfoCompat node) {
            AccessibilityNodeInfoUtils.recycleNodes(mFirstNode);
            mFirstNode = AccessibilityNodeInfoCompat.obtain(node);
            return this;
        }

        public AccessibilityNodeInfoCompat getFirstNode() {
            return mFirstNode;
        }

        public Content setLastNode(AccessibilityNodeInfoCompat node) {
            AccessibilityNodeInfoUtils.recycleNodes(mLastNode);
            mLastNode = AccessibilityNodeInfoCompat.obtain(node);
            return this;
        }

        public AccessibilityNodeInfoCompat getLastNode() {
            return mLastNode;
        }

        public Content setPanStrategy(int strategy) {
            mPanStrategy = strategy;
            return this;
        }

        public int getPanStrategy() {
            return mPanStrategy;
        }

        public Content setContractionMode(int mode) {
            mContractionMode = mode;
            return this;
        }

        public int getContractionMode() {
            return mContractionMode;
        }

        public Content setSplitParagraphs(boolean value) {
            mSplitParagraphs = value;
            return this;
        }

        public boolean isSplitParagraphs() {
            return mSplitParagraphs;
        }

        public void recycle() {
            AccessibilityNodeInfoUtils.recycleNodes(
                mFirstNode, mLastNode);
            mFirstNode = mLastNode = null;
            DisplaySpans.recycleSpans(mText);
            mText = null;
        }

        @Override
        public String toString() {
            return String.format("DisplayManager.Content {text=%s}", getText());
        }
    }

    private final TranslatorManager mTranslatorManager;
    private final Context mContext;
    // Not final, because it is initialized in the handler thread.
    private Display mDisplay;
    private final OnPanOverflowListener mPanOverflowListener;
    private final Display.OnConnectionStateChangeListener
            mConnectionStateChangeListener;
    private final OnMappedInputEventListener mMappedInputEventListener;
    private final DisplayHandler mDisplayHandler;
    private final CallbackHandler mCallbackHandler;
    private final HandlerThread mHandlerThread;
    private final PowerManager.WakeLock mWakeLock;

    // Read and written in display handler thread only.

    private boolean mConnected = false;
    private volatile boolean mIsSimulatedDisplay = false;
    /** Cursor position last passed to the translate method of the translator.
     * We use this because it is more reliable than the position maps inside
     * contracted words.  In the common case where there is just one
     * selection/focus on the display at the same time, this gives better
     * results.  Otherwise, we fall back on the position map, whic is also
     * used for keeping the pan position.
     */
    private int mCursorPosition = 0;
    private TranslationResult mTranslationResult = new TranslationResult(
        new byte[0], new int[0], new int[0], 0);
    /** Display content without overlays for cursors, focus etc. */
    private byte[] mBrailleContent = new byte[0];
    /**
     * Braille content, potentially with dots overlaid for cursors and focus.
     */
    private byte[] mOverlaidBrailleContent = mBrailleContent;
    private boolean mOverlaysOn;
    // Position in cells of the leftmost cell of the dipslay.
    private int mDisplayPosition = 0;
    private Content mCurrentContent = new Content("");
    /**
     * An array where the keys are translated positions that should always
     * correspond to the left-most position on the braille display if at all
     * inclded.  This is used to split the output at line breaks.  The values
     * are not used and currently set to 1.
    */
    private final SparseIntArray mSplitPoints = new SparseIntArray();

    // Displayed content, already trimmed based on the display position.
    // Updated in updateDisplayedContent() and used in refresh().
    private byte[] mDisplayedBraille = new byte[0];
    private byte[] mDisplayedOverlaidBraille = new byte[0];
    private CharSequence mDisplayedText = "";
    private int[] mDisplayedBrailleToTextPositions = new int[0];
    private boolean mBlinkNeeded = false;

    /**
     * Creates an instance of this class and starts the internal thread to
     * connect to the braille display service.  {@code context} is used to
     * connect to the display service.  {@code translator} is used for braille
     * translation.  The various listeners will be called as appropriate and
     * on the same thread that was used to create this object.  The current
     * thread must have a prepared looper.
     */
    public DisplayManager(TranslatorManager translatorManager,
            Context context,
            OnPanOverflowListener panOverflowListener,
            Display.OnConnectionStateChangeListener
                connectionStateChangeListener,
            OnMappedInputEventListener mappedInputEventListener) {
        mTranslatorManager = translatorManager;
        mTranslatorManager.addOnTablesChangedListener(this);
        mContext = context;
        mPanOverflowListener = panOverflowListener;
        mConnectionStateChangeListener = connectionStateChangeListener;
        mMappedInputEventListener = mappedInputEventListener;
        PowerManager pm = (PowerManager) context.getSystemService(
            Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(
            PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE,
            "BrailleBack");
        mHandlerThread = new HandlerThread("DisplayManager") {
            @Override
            public void onLooperPrepared() {
                mDisplay = new OverlayDisplay(mContext,
                        new DisplayClient(mContext));
                mDisplay.setOnConnectionStateChangeListener(
                        DisplayManager.this);
                mDisplay.setOnInputEventListener(DisplayManager.this);
            }
        };
        mHandlerThread.start();
        mDisplayHandler = new DisplayHandler(mHandlerThread.getLooper());
        mCallbackHandler = new CallbackHandler();
    }

    public void shutdown() {
        mDisplayHandler.stop();
        // Block on display shutdown. We need to make sure this finishes before
        // we can consider DisplayManager to be shut down.
        try {
            mHandlerThread.join(1000 /*milis*/);
        } catch (InterruptedException e) {
            LogUtils.log(this, Log.WARN,
                    "Display handler shutdown interrupted");
        }
        mTranslatorManager.removeOnTablesChangedListener(this);
    }

    /**
     * Asynchronously updates the display to reflect {@code content}.
     * {@code content} must not be modified after this function is called, and
     * will eventually be recycled by the display manager.
     */
    public void setContent(Content content) {
        if (content == null) {
            throw new NullPointerException("content can't be null");
        }
        if (content.mText == null) {
            throw new NullPointerException("content text is null");
        }
        mDisplayHandler.setContent(content);
    }

    /** Returns true if the current display is simulated. */
    public boolean isSimulatedDisplay() {
        return mIsSimulatedDisplay;
    }

    private boolean markSelection(Spanned spanned) {
        DisplaySpans.SelectionSpan[] spans =
                spanned.getSpans(0, spanned.length(),
                        DisplaySpans.SelectionSpan.class);
        for (DisplaySpans.SelectionSpan span : spans) {
            int start = textToDisplayPosition(mTranslationResult,
                    mCursorPosition, spanned.getSpanStart(span));
            int end = textToDisplayPosition(mTranslationResult,
                    mCursorPosition, spanned.getSpanEnd(span));
            if (start == end) {
                end = start + 1;
            }
            if (end > mBrailleContent.length) {
                extendContentForCursor();
            }
            copyOverlaidContent();
            for (int i = start;
                 i < end && i < mOverlaidBrailleContent.length;
                 ++i) {
                mOverlaidBrailleContent[i] |= SELECTION_DOTS;
            }
            if (mDisplayPosition < 0) {
                mDisplayPosition = fixDisplayPosition(start);
            }
        }
        return spans.length > 0;
    }

    /**
     * Makes sure that the overlaid content has its own copy.  Call before
     * adding overlay dots.
     */
    private void copyOverlaidContent() {
        if (mOverlaidBrailleContent == mBrailleContent) {
            mOverlaidBrailleContent = mBrailleContent.clone();
        }
    }

    private void extendContentForCursor() {
        mBrailleContent = Arrays.copyOf(mBrailleContent,
                mBrailleContent.length + 1);
        // Always create a new copy of the overlaid content because there will
        // be a cursor, so we will need a copy anyway.
        mOverlaidBrailleContent = Arrays.copyOf(mOverlaidBrailleContent,
                mOverlaidBrailleContent.length + 1);
    }

    private void markFocus(Spanned spanned) {
        DisplaySpans.FocusSpan[] spans =
                spanned.getSpans(0, spanned.length(),
                        DisplaySpans.FocusSpan.class);
        for (DisplaySpans.FocusSpan span : spans) {
            int start = textToDisplayPosition(mTranslationResult,
                    mCursorPosition, spanned.getSpanStart(span));
            if (start >= 0 && start < mOverlaidBrailleContent.length) {
                copyOverlaidContent();
                mOverlaidBrailleContent[start] |= FOCUS_DOTS;
                if (mDisplayPosition < 0) {
                    mDisplayPosition = fixDisplayPosition(start);
                }
            }
        }
    }

    /**
     * Adjust {@code position} so that it is the largest multiple of the
     * current display size that is {@code <= position}, counting from the
     * largest split point that is before or at {@code position}.
     *
     * This is used when panning the display according to a cursor position so
     * that the display keeps its position in the text when the cursor moves
     * within the area covered by the display.
     */
    private int fixDisplayPosition(int position) {
        int numCells = getNumTextCells();
        int splitIndex = findSplitPointIndex(position);
        int splitLimit = splitIndex < 0 ? 0 : mSplitPoints.keyAt(splitIndex);
        return ((position - splitLimit) / numCells * numCells) + splitLimit;
    }

    @Override
    public void onConnectionStateChanged(int state) {
        if (state == Display.STATE_CONNECTED) {
            mConnected = true;
            updateDisplayedContent();
        } else {
            mConnected = false;
        }
        mIsSimulatedDisplay = mDisplay.isSimulated();
        mCallbackHandler.onConnectionStateChanged(state);
    }

    @Override
    public void onInputEvent(BrailleInputEvent event) {
        keepAwake();
        LogUtils.log(this, Log.VERBOSE, "InputEvent: %s", event);
        // We're called from within the handler thread, so we forward
        // the call only if we are going to invoke the user's callback.
        switch (event.getCommand()) {
            case BrailleInputEvent.CMD_NAV_PAN_LEFT:
                panLeft();
                break;
            case BrailleInputEvent.CMD_NAV_PAN_RIGHT:
                panRight();
                break;
            default:
                sendMappedEvent(event);
                break;
        }
    }

    @Override
    public void onTablesChanged() {
        mDisplayHandler.retranslate();
    }

    private void sendMappedEvent(BrailleInputEvent event) {
        if (BrailleInputEvent.argumentType(event.getCommand())
                == BrailleInputEvent.ARGUMENT_POSITION) {
            int oldArgument = event.getArgument();
            // Offset argument by pan position and make sure it is less than
            // the next split position.
            int offsetArgument = oldArgument + mDisplayPosition;
            if (offsetArgument >= findRightSplitLimit()) {
                // The event is outisde the currently displayed
                // content, drop the event.
                return;
            }
            // The mapped event argument is the translated offset argument.
            int newArgument = displayToTextPosition(
                    mTranslationResult, mCursorPosition,
                    offsetArgument);
            // Create a new event if the argument actually differs.
            if (newArgument != oldArgument) {
                event = new BrailleInputEvent(event.getCommand(),
                        newArgument, event.getEventTime());
            }
        }
        mCallbackHandler.onMappedInputEvent(event);
    }

    private void panLeft() {
        if (mDisplayPosition <= 0) {
            mCallbackHandler.onPanLeftOverflow();
            return;
        }
        mDisplayPosition = Math.max(
            findLeftSplitLimit(),
            mDisplayPosition - getNumTextCells());
        updateDisplayedContent();
    }

    private void panRight() {
        int newPosition = Math.min(mDisplayPosition + getNumTextCells(),
                findRightSplitLimit());
        if (newPosition >= mBrailleContent.length) {
            mCallbackHandler.onPanRightOverflow();
            return;
        }
        mDisplayPosition = newPosition;
        updateDisplayedContent();
    }

    private class DisplayHandler extends Handler {
        private static final int MSG_SET_CONTENT = 1;
        private static final int MSG_RETRANSLATE = 2;
        private static final int MSG_PULSE = 3;
        private static final int MSG_STOP = 4;

        public DisplayHandler(Looper looper) {
            super(looper);
        }

        public void setContent(Content content) {
            obtainMessage(MSG_SET_CONTENT, content).sendToTarget();
        }

        public void retranslate() {
            sendEmptyMessage(MSG_RETRANSLATE);
        }

        public void schedulePulse() {
            if (hasMessages(MSG_PULSE)) {
                return;
            }
            sendEmptyMessageDelayed(MSG_PULSE,
                    mOverlaysOn ? BLINK_ON_MILLIS : BLINK_OFF_MILLIS);
        }

        public void cancelPulse() {
            removeMessages(MSG_PULSE);
            mOverlaysOn = true;
        }

        public void stop() {
            sendEmptyMessage(MSG_STOP);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_SET_CONTENT:
                    handleSetContent((Content) msg.obj);
                    break;
                case MSG_RETRANSLATE:
                    handleRetranslate();
                    break;
                case MSG_PULSE:
                    handlePulse();
                    break;
                case MSG_STOP:
                    handleStop();
                    break;
            }
        }

        private void handleSetContent(Content content) {
            Content oldContent = mCurrentContent;
            mCurrentContent = content;
            mCursorPosition = findCursorPosition(content);
            TranslationResult oldTranslationResult = mTranslationResult;
            translateCurrentContent();
            cancelPulse();
            // Adjust the pan position according to the panning strategy.
            // Setting the position to -1 below has the effect that the
            // the calls to markSelection() and markFocus() below will adjust
            // panning according to the cursor if there is one, or resetting it
            // to the beginning of the line if there is no selection or focus.
            switch (content.mPanStrategy) {
                default:
                    LogUtils.log(this, Log.ERROR,
                            "Unknown pan strategy: %d", content.mPanStrategy);
                    // Fall through.
                case Content.PAN_RESET:
                    mDisplayPosition = 0;
                    break;
                case Content.PAN_KEEP:
                    if (oldContent != null) {
                        // We don't align the display position to the size of
                        // the display in this case so that content doesn't
                        // jump around on the dipslay if content before the
                        // current display position changes size.
                        mDisplayPosition = findMatchingPanPosition(
                            oldContent, content,
                            oldTranslationResult, mTranslationResult,
                            mDisplayPosition);
                    } else {
                        mDisplayPosition = -1;
                    }
                    break;
                case Content.PAN_CURSOR:
                    mDisplayPosition = -1;
                    break;
            }
            markCursor();
            clampDisplayPosition();
            updateDisplayedContent();
            if (oldContent != null) {
                // Have the callback handler recycle the old content so that
                // the thread in which the callbck handler is running is the
                // only thread modifying it.  It is safe for the callback
                // thread to recycle the event when it receives this message
                // because the display handler thread will not send any more
                // input event containing this content and the events that
                // have already been sent will be processed by trhe callback
                // thread before the recycle message arrives because of the
                // guaranteed ordering of message handling.
                mCallbackHandler.recycleContent(oldContent);
            }
        }

        private void handleRetranslate() {
            if (mCurrentContent == null) {
                return;
            }
            TranslationResult oldTranslationResult = mTranslationResult;
            translateCurrentContent();
            mDisplayPosition = textToDisplayPosition(
                    mTranslationResult,
                    mCursorPosition,
                    displayToTextPosition(
                            mTranslationResult,
                            mCursorPosition,
                            mDisplayPosition));
            markCursor();
            clampDisplayPosition();
            cancelPulse();
            updateDisplayedContent();
        }

        private void handlePulse() {
            mOverlaysOn = !mOverlaysOn;
            refresh();
        }

        private void handleStop() {
            mDisplay.shutdown();
            mHandlerThread.quit();
        }
    }

    private class OnMappedInputEventArgs {
        public BrailleInputEvent mEvent;
        public Content mContent;

        public OnMappedInputEventArgs(BrailleInputEvent event,
                Content content) {
            mEvent = event;
            mContent = content;
        }
    }

    private class CallbackHandler extends Handler {
        private static final int MSG_ON_CONNECTION_STATE_CHANGED = 1;
        private static final int MSG_ON_MAPPED_INPUT_EVENT = 2;
        private static final int MSG_ON_PAN_LEFT_OVERFLOW = 3;
        private static final int MSG_ON_PAN_RIGHT_OVERFLOW = 4;
        private static final int MSG_RECYCLE_CONTENT = 5;

        public void onConnectionStateChanged(int state) {
            obtainMessage(MSG_ON_CONNECTION_STATE_CHANGED, state, 0)
                    .sendToTarget();
        }

        public void onMappedInputEvent(BrailleInputEvent event) {
            OnMappedInputEventArgs args = new OnMappedInputEventArgs(
                event, mCurrentContent);
            obtainMessage(MSG_ON_MAPPED_INPUT_EVENT, args).sendToTarget();
        }

        public void onPanLeftOverflow() {
            obtainMessage(MSG_ON_PAN_LEFT_OVERFLOW, mCurrentContent)
                    .sendToTarget();
        }

        public void onPanRightOverflow() {
            obtainMessage(MSG_ON_PAN_RIGHT_OVERFLOW, mCurrentContent)
                    .sendToTarget();
        }

        public void recycleContent(Content content) {
            obtainMessage(MSG_RECYCLE_CONTENT, content).sendToTarget();
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_ON_CONNECTION_STATE_CHANGED:
                    handleOnConnectionStateChanged(msg.arg1);
                    break;
                case MSG_ON_MAPPED_INPUT_EVENT:
                    OnMappedInputEventArgs args =
                            (OnMappedInputEventArgs) msg.obj;
                    handleOnMappedInputEvent(args.mEvent, args.mContent);
                    break;
                case MSG_ON_PAN_LEFT_OVERFLOW:
                    handleOnPanLeftOverflow((Content) msg.obj);
                    break;
                case MSG_ON_PAN_RIGHT_OVERFLOW:
                    handleOnPanRightOverflow((Content) msg.obj);
                    break;
                case MSG_RECYCLE_CONTENT:
                    handleRecycleContent((Content) msg.obj);
                    break;
            }
        }

        private void handleOnConnectionStateChanged(int state) {
            mConnectionStateChangeListener.onConnectionStateChanged(state);
        }

        private void handleOnMappedInputEvent(BrailleInputEvent event,
                                              Content content) {
            mMappedInputEventListener.onMappedInputEvent(event, content);
        }

        private void handleOnPanLeftOverflow(Content content) {
            mPanOverflowListener.onPanLeftOverflow(content);
        }

        private void handleOnPanRightOverflow(Content content) {
            mPanOverflowListener.onPanRightOverflow(content);
        }

        private void handleRecycleContent(Content content) {
            content.recycle();
        }
    }

    private void translateCurrentContent() {
        // Use an uncontracted translator if there is a editing cursor
        // because editing doesn't work in contracted braille.
        // TODO: Refine to only use the uncontracted translator for the current
        // word.
        BrailleTranslator translator =
                allowContractedBraille(mCurrentContent)
                ? mTranslatorManager.getTranslator()
                : mTranslatorManager.getUncontractedTranslator();
        String textContent = mCurrentContent.mText.toString();
        if (translator != null) {
            mTranslationResult = translator.translate(textContent,
                    mCursorPosition);
        } else {
            mTranslationResult = new TranslationResult(
                    new byte[0], new int[textContent.length()], new int[0],
                    0);
        }
        calculateSplitPoints();
        mBrailleContent = mTranslationResult.getCells();
        mOverlaidBrailleContent = mBrailleContent;
    }

    private void markCursor() {
        Spanned spanned = mCurrentContent.getSpanned();
        if (spanned == null) {
            return;
        }
        if (!markSelection(spanned)) {
            markFocus(spanned);
        }
    }

    private void clampDisplayPosition() {
        if (mDisplayPosition < 0) {
            mDisplayPosition = 0;
        } else if (mDisplayPosition >= mBrailleContent.length) {
            // If we've fallen outside of the content, align the display
            // so that it gets filled with the rightmost part
            // of the content.
            mDisplayPosition = Math.max(0,
                    mBrailleContent.length - getNumTextCells());
        }
    }

    private void updateDisplayedContent() {
        if (!mConnected || mCurrentContent == null) {
            return;
        }
        int rightEdge = Math.min(
            findRightSplitLimit(),
            mDisplayPosition + getNumTextCells());

        // Compute equivalent text and mapping.
        int[] brailleToTextPositions =
                mTranslationResult.getBrailleToTextPositions();
        int textLeft = mDisplayPosition >= brailleToTextPositions.length
                ? 0
                : brailleToTextPositions[mDisplayPosition];
        int textRight = rightEdge >= brailleToTextPositions.length
                ? mCurrentContent.mText.length()
                : brailleToTextPositions[rightEdge];
        StringBuilder text = new StringBuilder(
                mCurrentContent.mText.subSequence(textLeft, textRight));
        int[] trimmedBrailleToTextPositions =
                new int[rightEdge - mDisplayPosition];
        for (int i = 0; i < trimmedBrailleToTextPositions.length; i++) {
            if (mDisplayPosition + i < brailleToTextPositions.length) {
                trimmedBrailleToTextPositions[i] =
                        brailleToTextPositions[mDisplayPosition + i] - textLeft;
            } else {
                trimmedBrailleToTextPositions[i] = text.length();
                text.append(' ');
            }
        }

        // Store all data needed by refresh().
        mDisplayedBraille = Arrays.copyOfRange(mBrailleContent,
                mDisplayPosition, rightEdge);
        if (mBrailleContent != mOverlaidBrailleContent) {
            mDisplayedOverlaidBraille = Arrays.copyOfRange(
                    mOverlaidBrailleContent, mDisplayPosition, rightEdge);
        } else {
            mDisplayedOverlaidBraille = mDisplayedBraille;
        }
        mDisplayedText = text.toString();
        mDisplayedBrailleToTextPositions = trimmedBrailleToTextPositions;
        mBlinkNeeded = blinkNeeded(rightEdge);

        refresh();
    }

    private void refresh() {
        if (!mConnected) {
            return;
        }
        byte[] toDisplay = mOverlaysOn
                ? mDisplayedOverlaidBraille
                : mDisplayedBraille;
        mDisplay.displayDots(toDisplay, mDisplayedText,
                mDisplayedBrailleToTextPositions);
        if (mBlinkNeeded) {
            mDisplayHandler.schedulePulse();
        } else {
            mDisplayHandler.cancelPulse();
        }
    }

    /**
     * Returns {@code true} if the current display content is such that it
     * requires blinking.  {@code rightEdge} is the end position of currently
     * displayed content.  This is
     * {@code mDisplayPosition + getNumTextCells()}, or a smaller number if
     * there is a split point that causes part of the display to not be
     * populated.
     */
    private boolean blinkNeeded(int rightEdge) {
        if (mBrailleContent == mOverlaidBrailleContent) {
            return false;
        }
        for (int i = mDisplayPosition; i < rightEdge; ++i) {
            if (mBrailleContent[i] != mOverlaidBrailleContent[i]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Keeps the phone awake as if there was a 'user activity' registered
     * by the system.
     */
    private void keepAwake() {
        // Acquiring the lock and immediately releasing it keesp the phone
        // awake.  We don't use aqcuire() with a timeout because it just
        // adds an unnecessary context switch.
        mWakeLock.acquire();
        mWakeLock.release();
    }

    /**
     * Returns the size of the connected display, or {@code 1} if
     * no display is connected.
     */
    private int getNumTextCells() {
        if (!mConnected) {
            return 1;
        }
        return mDisplay.getDisplayProperties().getNumTextCells();
    }

    private int findMatchingPanPosition(
            Content oldContent, Content newContent,
            TranslationResult oldTranslationResult,
            TranslationResult newTranslationResult,
            int oldDisplayPosition) {
        Spanned oldSpanned = oldContent.getSpanned();
        Spanned newSpanned = newContent.getSpanned();
        if (oldSpanned == null || newSpanned == null) {
            return -1;
        }
        // Map the current display start and past-the-end positions
        // to the corresponding input positions.
        int oldTextStart = displayToTextPosition(oldTranslationResult,
                -1 /*cursorPosition*/, oldDisplayPosition);
        int oldTextEnd = displayToTextPosition(oldTranslationResult,
                -1 /*cursorPosition*/, oldDisplayPosition + getNumTextCells());
        // Find the nodes that overlap with the display.
        AccessibilityNodeInfoCompat[] displayedNodes =
                oldSpanned.getSpans(oldTextStart, oldTextEnd,
                        AccessibilityNodeInfoCompat.class);
        Arrays.sort(displayedNodes,
                new ByDistanceComparator(oldSpanned, oldTextStart));
        // Find corresponding node in new content.
        for (AccessibilityNodeInfoCompat oldNode : displayedNodes) {
            AccessibilityNodeInfoCompat newNode = (AccessibilityNodeInfoCompat)
                    DisplaySpans.getEqualSpan(newSpanned, oldNode);
            if (newNode == null) {
                continue;
            }
            int oldDisplayStart = textToDisplayPosition(oldTranslationResult,
                    -1 /*cursorPosition*/, oldSpanned.getSpanStart(oldNode));
            int newDisplayStart = textToDisplayPosition(newTranslationResult,
                    -1 /*cursorPosition*/, newSpanned.getSpanStart(newNode));
            // Offset position according to diff in node position.
            int newDisplayPosition = oldDisplayPosition
                    + (newDisplayStart - oldDisplayStart);
            return newDisplayPosition;
        }
        return -1;
    }

    private static class ByDistanceComparator
            implements Comparator<AccessibilityNodeInfoCompat> {
        private final Spanned mSpanned;
        private final int mStart;
        public ByDistanceComparator(Spanned spanned, int start) {
            mSpanned = spanned;
            mStart = start;
        }

        @Override
        public int compare(
            AccessibilityNodeInfoCompat a,
            AccessibilityNodeInfoCompat b) {
            int aStart = mSpanned.getSpanStart(a);
            int bStart = mSpanned.getSpanStart(b);
            int aDist = Math.abs(mStart - aStart);
            int bDist = Math.abs(mStart - bStart);
            if (aDist != bDist) {
                return aDist - bDist;
            }
            // They are on the same distance, compare by length.
            int aLength = aStart + mSpanned.getSpanEnd(a);
            int bLength = bStart + mSpanned.getSpanEnd(b);
            return aLength - bLength;
        }
    }

    private static int textToDisplayPosition(
            TranslationResult translationResult,
            int cursorPosition,
            int textPosition) {
        if (textPosition == cursorPosition) {
            return translationResult.getCursorPosition();
        }
        int[] posMap = translationResult.getTextToBraillePositions();
        // Any position past-the-end of the position map maps to the
        // corresponding past-the-end position in the braille.
        if (textPosition >= posMap.length) {
            return translationResult.getBrailleToTextPositions().length;
        }
        return posMap[textPosition];
    }

    private static int displayToTextPosition(
            TranslationResult translationResult,
            int cursorPosition,
            int displayPosition) {
        if (displayPosition == translationResult.getCursorPosition()) {
            return cursorPosition;
        }
        int[] posMap = translationResult.getBrailleToTextPositions();
        // Any position past-the-end of the position map maps to the
        // corresponding past-the-end position in the braille.
        if (displayPosition >= posMap.length) {
            return translationResult.getTextToBraillePositions().length;
        }
        return posMap[displayPosition];
    }

    private static int findCursorPosition(Content content) {
        Spanned spanned = content.getSpanned();
        if (spanned == null) {
            return -1;
        }
        DisplaySpans.SelectionSpan[] selectionSpans =
                spanned.getSpans(0, spanned.length(),
                        DisplaySpans.SelectionSpan.class);
        if (selectionSpans.length > 0) {
            return spanned.getSpanStart(selectionSpans[0]);
        }
        DisplaySpans.FocusSpan[] focusSpans =
                spanned.getSpans(0, spanned.length(),
                        DisplaySpans.FocusSpan.class);
        if (focusSpans.length > 0) {
            return spanned.getSpanStart(focusSpans[0]);
        }
        return -1;
    }

    private boolean allowContractedBraille(Content content) {
        if (content.getContractionMode() == Content.CONTRACT_ALWAYS_ALLOW) {
            return true;
        }
        Spanned spanned = content.getSpanned();
        if (spanned == null) {
            return true;
        }
        DisplaySpans.SelectionSpan[] selectionSpans =
                spanned.getSpans(0, spanned.length(),
                        DisplaySpans.SelectionSpan.class);
        return selectionSpans.length == 0;
    }

    private void calculateSplitPoints() {
        mSplitPoints.clear();
        if (!mCurrentContent.isSplitParagraphs()) {
            return;
        }
        CharSequence text = mCurrentContent.mText;
        for (int i = 0; i < text.length() - 1; ++i) {
            if (text.charAt(i) == '\n') {
                mSplitPoints.append(textToDisplayPosition(mTranslationResult,
                                mCursorPosition, i + 1), 1);
            }
        }
    }

    private int findSplitPointIndex(int displayPosition) {
        int index = mSplitPoints.indexOfKey(displayPosition);
        if (index >= 0) {
            // Exact match.
            return index;
        }
        // One's complement gives index where the element would be inserted
        // in sorted order.
        index = ~index;
        if (index > 0) {
            return index - 1;
        }
        return -1;
    }

    private int findLeftSplitLimit() {
        int index = findSplitPointIndex(mDisplayPosition);
        if (index >= 0) {
            int limit = mSplitPoints.keyAt(index);
            if (limit < mDisplayPosition) {
                return limit;
            }
            if (index > 0) {
                return mSplitPoints.keyAt(index - 1);
            }
        }
        return 0;
    }

    private int findRightSplitLimit() {
        int index = findSplitPointIndex(mDisplayPosition) + 1;
        if (index >= mSplitPoints.size()) {
            return mBrailleContent.length;
        }
        return mSplitPoints.keyAt(index);
    }
}

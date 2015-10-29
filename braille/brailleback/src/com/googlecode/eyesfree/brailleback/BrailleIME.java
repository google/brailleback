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

import com.googlecode.eyesfree.braille.translate.BrailleTranslator;
import com.googlecode.eyesfree.utils.LogUtils;

import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import java.lang.ref.WeakReference;

/**
 * Input method service for keys from the connected braille display.
 */
public class BrailleIME extends InputMethodService {

    /** Interface between the IME and a "host" in the accessibility service. */
    public interface Host {
        /**
         * Returns a translator for input, if available.
         * The IME will ignore text input if no translator is available.
         */
        BrailleTranslator getBrailleTranslator();

        /**
         * Returns a display manager for output, if available.
         * The IME will not generate output if no display manager is available.
         */
        DisplayManager getDisplayManager();

        /**
         * Returns a feedback manager, if available.
         * No feedback will be emitted if no feedback manager is available.
         */
        FeedbackManager getFeedbackManager();

        /**
         * Called when the IME has been created by the system.
         * @see InputMethodService#onCreate()
         */
        void onCreateIME();

        /**
         * Called when the IME is being destroyed by the system.
         * @see InputMethodService#onDestroy()
         */
        void onDestroyIME();

        /**
         * Called when the IME has associated with an input connection.
         * @see InputMethodService#onBindInput()
         */
        void onBindInput();

        /**
         * Called when the IME has disassociated from an input connection.
         * @see InputMethodService#onUnbindInput()
         */
        void onUnbindInput();

        /**
         * Called when the IME has started an input session.
         * @see InputMethodService#onStartInput(EditorInfo, boolean)
         */
        void onStartInput(EditorInfo attribute, boolean restarting);

        /**
         * Called when the IME has finished an input session.
         * @see InputMethodService#onFinishInput()
         */
        void onFinishInput();

        /**
         * Called when the IME opens the input view.
         * @see InputMethodService#onStartInputView(EditorInfo, boolean)
         */
        void onStartInputView(EditorInfo info, boolean restarting);

        /**
         * Called when the IME closes the input view.
         * @see InputMethodService#onFinishInputView(boolean)
         */
        void onFinishInputView(boolean finishingInput);
    }

    public static final int DIRECTION_FORWARD = 1;
    public static final int DIRECTION_BACKWARD = -1;
    private static final int MAX_REQUEST_CHARS = 1000;
    /** Marks the extent of the editable text. */
    private static final MarkingSpan EDIT_TEXT_SPAN = new MarkingSpan();
    /** Marks the extent of the action button. */
    private static final MarkingSpan ACTION_LABEL_SPAN = new MarkingSpan();

    private static WeakReference<BrailleIME> sInstance;
    private static WeakReference<Host> sHost;
    private final Host mHost;  // for testing
    private InputMethodManager mInputMethodManager;

    private ExtractedText mExtractedText;
    private int mExtractedTextToken = 0;
    /**
     * Start of current selection, relative to the start of the extracted
     * text.
     */
    private int mSelectionStart;
    /**
     * End (inclusive) of current selection, relative to the start of the
     * extracted text.
     */
    private int mSelectionEnd;
    /**
     * The text that is shown on the display.  Might be only part of the
     * full edit field if it is larger than {@code MAX_REQUEST_CHARS}.
     */
    private StringBuilder mCurrentText = new StringBuilder();

    public static BrailleIME getActiveInstance() {
        return sInstance != null ? sInstance.get() : null;
    }

    public static void setSingletonHost(Host host) {
        sHost = host != null ? new WeakReference<Host>(host) : null;
    }

    /** Constructor for general use. */
    public BrailleIME() {
        mHost = null;
    }

    /** Constructor for testing. Allows using a non-global host object. */
    /*package*/ BrailleIME(Host host, Context baseContext) {
        mHost = host;
        attachBaseContext(baseContext);
    }

    /* InputMethodService implementation */

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = new WeakReference<BrailleIME>(this);
        mInputMethodManager = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        LogUtils.log(this, Log.VERBOSE, "Created Braille IME");

        Host host = getHost();
        if (host != null) {
            host.onCreateIME();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // onFinishInput is not called when switching away from this IME
        // to another one, so clear the state here as well.
        mExtractedText = null;
        updateCurrentText();
        sInstance = null;

        Host host = getHost();
        if (host != null) {
            host.onDestroyIME();
        }
    }

    @Override
    public void onBindInput() {
        super.onBindInput();

        Host host = getHost();
        if (host != null) {
            host.onBindInput();
        }
    }

    @Override
    public void onUnbindInput() {
        super.onUnbindInput();

        Host host = getHost();
        if (host != null) {
            host.onUnbindInput();
        }
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        LogUtils.log(this, Log.VERBOSE,
                "onStartInput: inputType: %x, imeOption: %x, " +
                ", label: %s, hint: %s, package: %s, ",
                attribute.inputType, attribute.imeOptions, attribute.label,
                attribute.hintText, attribute.packageName);
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ExtractedTextRequest req = new ExtractedTextRequest();
            req.token = ++mExtractedTextToken;
            req.hintMaxChars = MAX_REQUEST_CHARS;
            mExtractedText = getCurrentInputConnection().getExtractedText(req,
                    InputConnection.GET_EXTRACTED_TEXT_MONITOR);
        } else {
            mExtractedText = null;
        }
        updateCurrentText();
        updateDisplay();

        Host host = getHost();
        if (host != null) {
            host.onStartInput(attribute, restarting);
        }
    }

    @Override
    public void onFinishInput() {
        super.onFinishInput();
        LogUtils.log(this, Log.VERBOSE, "onFinishInput");
        mExtractedText = null;
        updateCurrentText();

        Host host = getHost();
        if (host != null) {
            host.onFinishInput();
        }
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        LogUtils.log(this, Log.VERBOSE, "onStartInputView");

        Host host = getHost();
        if (host != null) {
            host.onStartInputView(info, restarting);
        }
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        LogUtils.log(this, Log.VERBOSE, "onFinishInputView");

        Host host = getHost();
        if (host != null) {
            host.onFinishInputView(finishingInput);
        }
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;
    }

    @Override
    public View onCreateInputView() {
        final LayoutInflater inflater = getLayoutInflater();
        final View inputView = inflater.inflate(R.layout.braille_ime, null);
        inputView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchAwayFromThisIme();
            }
        });
        inputView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switchAwayFromThisIme();
                return true;
            }
        });
        return inputView;
    }

    @Override
    public void onUpdateExtractedText(int token, ExtractedText text) {
        // The superclass only deals with fullscreen support, which we've
        // disabled, so don't call it here.
        if (mExtractedText == null || token != mExtractedTextToken) {
            return;
        }
        mExtractedText = text;
        updateCurrentText();
        updateDisplay();
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd,
            int newSelStart, int newSelEnd,
            int candidatesStart, int candidatesEnd) {
        if (mExtractedText != null) {
            int off = mExtractedText.startOffset;
            int len = mCurrentText.length();
            newSelStart -= off;
            newSelEnd -= off;
            newSelStart = newSelStart < 0 ? 0 :
                    (newSelStart > len ? len : newSelStart);
            newSelEnd = newSelEnd < 0 ? 0 :
                    (newSelEnd > len ? len : newSelEnd);
            mSelectionStart = newSelStart;
            mSelectionEnd = newSelEnd;
            updateDisplay();
        }
    }

    /* Exposed for use by the host. */

    public boolean route(int position, DisplayManager.Content content) {
        InputConnection ic = getCurrentInputConnection();
        Spanned text = content.getSpanned();
        if (ic != null && text != null) {
            MarkingSpan[] spans = text.getSpans(position, position,
                    MarkingSpan.class);
            if (spans.length == 1) {
                if (spans[0] == ACTION_LABEL_SPAN) {
                    return emitFeedbackOnFailure(
                        sendDefaultAction(),
                        FeedbackManager.TYPE_COMMAND_FAILED);
                } else if (spans[0] == EDIT_TEXT_SPAN) {
                    return emitFeedbackOnFailure(
                        setCursor(ic,
                                position - text.getSpanStart(EDIT_TEXT_SPAN)),
                        FeedbackManager.TYPE_COMMAND_FAILED);
                }
            } else if (spans.length == 0) {
                // Most likely, the user clicked on the label/hint part of the
                // content.
                emitFeedback(FeedbackManager.TYPE_NAVIGATE_OUT_OF_BOUNDS);
                return true;
            } else if (spans.length > 1) {
                LogUtils.log(this, Log.ERROR,
                        "Conflicting spans in Braille IME");
            }
        }
        emitFeedback(FeedbackManager.TYPE_COMMAND_FAILED);
        return true;
    }

    public boolean sendDefaultAction() {
        if (!allowsDefaultAction()) {
            return false;
        }
        EditorInfo ei = getCurrentInputEditorInfo();
        InputConnection ic = getCurrentInputConnection();
        if (ei == null || ic == null) {
            return false;
        }

        int actionId = ei.actionId;
        if (actionId != 0) {
            return ic.performEditorAction(actionId);
        } else {
            return sendDefaultEditorAction(false);
        }
    }

    public boolean moveCursor(int direction, int granularity) {
        if (mCurrentText == null) {
            return false;
        }
        switch (granularity) {
            case AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_CHARACTER:
                int keyCode = (direction == DIRECTION_BACKWARD)
                    ? KeyEvent.KEYCODE_DPAD_LEFT
                    : KeyEvent.KEYCODE_DPAD_RIGHT;
                return sendAndroidKey(keyCode);
            case AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PARAGRAPH:
                if (!isMultiLineField()) {
                    return false;
                }
                int newPos = (direction == DIRECTION_BACKWARD)
                    ? findParagraphBreakBackward()
                    : findParagraphBreakForward();
                // newPos == the length means having the insertion point after
                // the last character, so the below comparison is correct.
                if (newPos < 0 || newPos > mCurrentText.length()) {
                    return false;
                }
                return setCursor(getCurrentInputConnection(), newPos);
        }
        return false;
    }

    /**
     * Attempts to send down and up key events for a raw {@code keyCode}
     * through an input connection.
     */
    public boolean sendAndroidKey(int keyCode) {
        return emitFeedbackOnFailure(
            sendAndroidKeyInternal(keyCode),
            FeedbackManager.TYPE_COMMAND_FAILED);
    }

    public boolean handleBrailleKey(int dots) {
        return emitFeedbackOnFailure(
            handleBrailleKeyInternal(dots),
            FeedbackManager.TYPE_COMMAND_FAILED);
    }

    public void updateDisplay() {
        if (mExtractedText == null) {
            return;
        }
        DisplayManager displayManager = getCurrentDisplayManager();
        if (displayManager == null) {
            return;
        }
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ei == null) {
            LogUtils.log(this, Log.WARN, "No input editor info");
            return;
        }
        CharSequence label = ei.label;
        CharSequence hint = ei.hintText;
        if (TextUtils.isEmpty(label)) {
            label = hint;
            hint = null;
        }
        SpannableStringBuilder text = new SpannableStringBuilder();
        if (!TextUtils.isEmpty(label)) {
            text.append(label);
            text.append(": ");  // TODO: Put in a resource.
        }
        int editStart = text.length();
        text.append(mCurrentText);
        addMarkingSpan(text, EDIT_TEXT_SPAN, editStart);
        CharSequence actionLabel = getActionLabel();
        if (actionLabel != null) {
            text.append(" [");
            text.append(actionLabel);
            text.append("]");
            addMarkingSpan(text, ACTION_LABEL_SPAN,
                    text.length() - (actionLabel.length() + 2));
        }
        DisplaySpans.addSelection(text,
            editStart + mSelectionStart, editStart + mSelectionEnd);
        displayManager.setContent(
            new DisplayManager.Content(text)
                .setPanStrategy(DisplayManager.Content.PAN_CURSOR)
                .setSplitParagraphs(isMultiLineField()));
    }

    /* Private */

    private void updateCurrentText() {
        if (mExtractedText == null) {
            mCurrentText.setLength(0);
            mSelectionStart = mSelectionEnd = 0;
            return;
        }
        if (mExtractedText.text != null) {
            int len = mCurrentText.length();
            if (mExtractedText.partialStartOffset < 0) {
                // Complete update.
                mCurrentText.replace(0, len, mExtractedText.text.toString());
            } else {
                int start = Math.min(mExtractedText.partialStartOffset, len);
                int end = Math.min(mExtractedText.partialEndOffset, len);
                mCurrentText.replace(start, end,
                        mExtractedText.text.toString());
            }
        }

        // Update selection, keeping it within the text range even if the
        // client messed up.
        int len = mCurrentText.length();
        int start = mExtractedText.selectionStart;
        start = start < 0 ? 0 : (start > len ? len : start);
        int end = mExtractedText.selectionEnd;
        end = end < 0 ? 0 : (end > len ? len : end);
        mSelectionStart = start;
        mSelectionEnd = end;
    }


    private int findParagraphBreakBackward() {
        if (mSelectionStart <= 0) {
            return -1;
        }
        return mCurrentText.lastIndexOf("\n", mSelectionStart - 2) + 1;
    }

    private int findParagraphBreakForward() {
        if (mSelectionEnd >= mCurrentText.length()) {
            return -1;
        }
        int index = mCurrentText.indexOf("\n", mSelectionEnd);
        if (index >= 0 && index < mCurrentText.length()) {
            return index + 1;
        } else {
            return mCurrentText.length();
        }
    }

    private boolean setCursor(InputConnection ic, int pos) {
        if (mCurrentText == null) {
            return false;
        }
        int textLen = mCurrentText.length();
        pos = (pos < 0) ? 0 : ((pos <= textLen) ? pos : textLen);
        return ic.setSelection(pos, pos);
    }

    private boolean sendAndroidKeyInternal(int keyCode) {
        LogUtils.log(this, Log.VERBOSE, "sendAndroidKey: %d", keyCode);
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return false;
        }
        long eventTime = SystemClock.uptimeMillis();
        if (!ic.sendKeyEvent(new KeyEvent(eventTime, eventTime,
                                KeyEvent.ACTION_DOWN, keyCode, 0 /*repeat*/))) {
            return false;
        }
        return ic.sendKeyEvent(
            new KeyEvent(eventTime, eventTime,
                    KeyEvent.ACTION_UP, keyCode, 0 /*repeat*/));
    }

    private boolean handleBrailleKeyInternal(int dots) {
        // TODO: Support more than computer braille.  This means that
        // there's not a 1:1 correspondence between a cell and a character,
        // so requires more book-keeping.
        BrailleTranslator translator = getCurrentBrailleTranslator();
        InputConnection ic = getCurrentInputConnection();
        if (translator == null || ic == null) {
            LogUtils.log(this, Log.WARN, "missing translator %s or IC %s",
                    translator, ic);
            return false;
        }
        CharSequence text = translator.backTranslate(
            new byte[] {(byte) dots});
        if (!TextUtils.isEmpty(text)) {
            return ic.commitText(text, 1);
        }
        return true;
    }

    private Host getHost() {
        if (mHost != null) {
            return mHost;
        } else if (sHost != null) {
            return sHost.get();
        } else {
            return null;
        }
    }

    private BrailleTranslator getCurrentBrailleTranslator() {
        Host host = getHost();
        return host != null ? host.getBrailleTranslator() : null;
    }

    private DisplayManager getCurrentDisplayManager() {
        Host host = getHost();
        return host != null ? host.getDisplayManager() : null;
    }

    private FeedbackManager getCurrentFeedbackManager() {
        Host host = getHost();
        return host != null ? host.getFeedbackManager() : null;
    }

    private CharSequence getActionLabel() {
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ei == null) {
            return null;
        }
        if (!allowsDefaultAction()) {
            // The edit field asks us to not show this inline in the
            // editor.
            return null;
        }
        if (ei.actionLabel != null) {
            return ei.actionLabel;
        }
        return getTextForImeAction(ei.imeOptions);
    }

    private boolean allowsDefaultAction() {
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ei != null
                && (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0) {
            return true;
        }
        return false;
    }

    private boolean isMultiLineField() {
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ei == null) {
            return false;
        }
        int type = ei.inputType;
        final int mask = EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE
            | EditorInfo.TYPE_TEXT_FLAG_IME_MULTI_LINE;
        // Consider this a multiline field if it is multiline in the main
        // text field, and not multiline only in the ime fullscreen mode.
        return ((type & mask)
                == EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE);
    }

    private void addMarkingSpan(Spannable spannable, MarkingSpan span,
            int position) {
        if (position < spannable.length()) {
            spannable.setSpan(span, position, spannable.length(),
               Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    // Visible for testing.
    /*package*/ void switchAwayFromThisIme() {
        LogUtils.log(this, Log.DEBUG, "Switching to last IME");
        IBinder binder = getWindow().getWindow().getAttributes().token;
        if (!mInputMethodManager.switchToNextInputMethod(binder, false)) {
            LogUtils.log(this, Log.DEBUG, "Failed to switch to next IME, show IME picker instead.");
            mInputMethodManager.showInputMethodPicker();
        }
    }

    private void emitFeedback(int type) {
        FeedbackManager feedbackManager = getCurrentFeedbackManager();
        if (feedbackManager != null) {
            feedbackManager.emitFeedback(type);
        }
    }

    private boolean emitFeedbackOnFailure(boolean result, int type) {
        if (!result) {
            emitFeedback(type);
        }
        return true;
    }

    private static class MarkingSpan {}

}

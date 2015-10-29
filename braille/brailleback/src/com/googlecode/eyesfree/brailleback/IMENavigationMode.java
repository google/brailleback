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

import com.googlecode.eyesfree.braille.display.BrailleInputEvent;
import com.googlecode.eyesfree.braille.translate.BrailleTranslator;
import com.googlecode.eyesfree.compat.accessibilityservice.AccessibilityServiceCompatUtils;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;
import com.googlecode.eyesfree.utils.LogUtils;

import android.accessibilityservice.AccessibilityService;
import android.os.Bundle;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

/**
 * Implements a navigation mode which interacts with the input method service.
 * Forwards calls not handled by the hosted IME to another NavigationMode.
 */
public class IMENavigationMode implements NavigationMode, BrailleIME.Host {
    /** Accessibility event types that warrant rechecking the current state. */
    private final static int UPDATE_STATE_EVENT_MASK = (
            AccessibilityEvent.TYPE_VIEW_FOCUSED |
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED |
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED |
            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED |
            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
    private final NavigationMode mNext;
    private final AccessibilityService mAccessibilityService;
    private final DisplayManager mDisplayManager;
    private final FeedbackManager mFeedbackManager;
    private final SelfBrailleManager mSelfBrailleManager;
    private final TranslatorManager mTranslatorManager;
    private final BrailleIME mIME;  // for testing

    private enum State {
        // Disregard all input.
        INACTIVE,
        // Accept text input, but leave navigation alone.
        TEXT_ONLY,
        // Accept text and override navigation controls.
        TEXT_AND_NAVIGATION,
        // Deactivate the underlying mode and present an editor.
        MODAL_EDITOR;

        public boolean acceptsText() {
            return this != INACTIVE;
        }

        public boolean navigatesViaIME() {
            return this == MODAL_EDITOR;
        }

        public boolean navigatesByTextGranularity() {
            return this == TEXT_AND_NAVIGATION;
        }

        public boolean controlsDisplay() {
            return this == MODAL_EDITOR;
        }
    }
    private State mState = State.INACTIVE;
    private boolean mActive = false;
    private boolean mNextActive = false;
    private boolean mInputBound = false;
    private boolean mInputStarted = false;
    private boolean mInputViewStarted = false;

    /** Public constructor for general use. */
    public IMENavigationMode(NavigationMode next, AccessibilityService service,
            DisplayManager displayManager, FeedbackManager feedbackManager,
            SelfBrailleManager selfBrailleManager,
            TranslatorManager translatorManager) {
        mNext = next;
        mAccessibilityService = service;
        mDisplayManager = displayManager;
        mFeedbackManager = feedbackManager;
        mSelfBrailleManager = selfBrailleManager;
        mTranslatorManager = translatorManager;
        mIME = null;
    }

    /** Package-private constructor for testing. */
    /*package*/ IMENavigationMode(NavigationMode next,
            AccessibilityService service, DisplayManager displayManager,
            FeedbackManager feedbackManager,
            SelfBrailleManager selfBrailleManager,
            TranslatorManager translatorManager, BrailleIME ime) {
        mNext = next;
        mAccessibilityService = service;
        mDisplayManager = displayManager;
        mFeedbackManager = feedbackManager;
        mSelfBrailleManager = selfBrailleManager;
        mTranslatorManager = translatorManager;
        mIME = ime;
    }

    /* NavigationMode implementation */

    @Override
    public void onActivate() {
        mActive = true;
        updateState();
        BrailleIME ime = getIME();
        if (ime != null && mState.controlsDisplay()) {
            ime.updateDisplay();
            return;
        }
        activateNext();
    }

    @Override
    public void onDeactivate() {
        mActive = false;
        mSelfBrailleManager.setImeOpen(false);
        BrailleIME ime = getIME();
        if (ime != null && mState.controlsDisplay()) {
            return;
        }
        deactivateNext();
    }

    @Override
    public void onObserveAccessibilityEvent(AccessibilityEvent event) {
        if ((event.getEventType() & UPDATE_STATE_EVENT_MASK) != 0) {
            updateState();
        }
        mNext.onObserveAccessibilityEvent(event);
    }

    @Override
    public boolean onAccessibilityEvent(AccessibilityEvent event) {
        BrailleIME ime = getIME();
        if (ime != null && mState.controlsDisplay()) {
            return true;
        }
        return mNext.onAccessibilityEvent(event);
    }

    @Override
    public void onInvalidateAccessibilityNode(
            AccessibilityNodeInfoCompat node) {
        BrailleIME ime = getIME();
        if (ime != null && mState.controlsDisplay()) {
            return;
        }
        mNext.onInvalidateAccessibilityNode(node);
    }

    @Override
    public boolean onPanLeftOverflow(DisplayManager.Content content) {
        return mNext.onPanLeftOverflow(content);
    }

    @Override
    public boolean onPanRightOverflow(DisplayManager.Content content) {
        return mNext.onPanRightOverflow(content);
    }

    @Override
    public boolean onMappedInputEvent(BrailleInputEvent event,
            DisplayManager.Content content) {
        BrailleIME ime = getIME();

        // These commands are handled by the IME whenever it is accepting
        // input, even if it has not taken control of the display.
        if (ime != null && mState.acceptsText()) {
            switch (event.getCommand()) {
                case BrailleInputEvent.CMD_KEY_DEL:
                    return ime.sendAndroidKey(KeyEvent.KEYCODE_DEL);
                case BrailleInputEvent.CMD_KEY_ENTER:
                    return ime.sendAndroidKey(KeyEvent.KEYCODE_ENTER);
                case BrailleInputEvent.CMD_KEY_FORWARD_DEL:
                    return ime.sendAndroidKey(KeyEvent.KEYCODE_FORWARD_DEL);
                case BrailleInputEvent.CMD_BRAILLE_KEY:
                    return ime.handleBrailleKey(event.getArgument());
            }
        }

        // If navigation commands are handled by the IME in this state, then
        // move the cursor by the appropriate granularity.
        if (ime != null && mState.navigatesViaIME()) {
            switch (event.getCommand()) {
                case BrailleInputEvent.CMD_NAV_ITEM_PREVIOUS:
                    return ime.moveCursor(BrailleIME.DIRECTION_BACKWARD,
                            AccessibilityNodeInfoCompat
                            .MOVEMENT_GRANULARITY_CHARACTER);
                case BrailleInputEvent.CMD_NAV_ITEM_NEXT:
                    return ime.moveCursor(BrailleIME.DIRECTION_FORWARD,
                            AccessibilityNodeInfoCompat
                            .MOVEMENT_GRANULARITY_CHARACTER);
                case BrailleInputEvent.CMD_NAV_LINE_PREVIOUS:
                    // Line navigation moves by paragraph since there's no way
                    // of knowing the line extents in the edit text.
                    return ime.moveCursor(BrailleIME.DIRECTION_BACKWARD,
                            AccessibilityNodeInfoCompat
                            .MOVEMENT_GRANULARITY_PARAGRAPH);
                case BrailleInputEvent.CMD_NAV_LINE_NEXT:
                    return ime.moveCursor(BrailleIME.DIRECTION_FORWARD,
                            AccessibilityNodeInfoCompat
                            .MOVEMENT_GRANULARITY_PARAGRAPH);
            }
        }

        // Alternatively, we may handle these navigation commands by sending
        // "move by granularity" through the accessibility system.
        if (ime != null && mState.navigatesByTextGranularity()) {
            AccessibilityNodeInfoCompat focusedNode = getFocusedNode();
            switch (event.getCommand()) {
                case BrailleInputEvent.CMD_NAV_ITEM_PREVIOUS:
                    return previousAtMovementGranularity(
                            AccessibilityNodeInfoCompat
                            .MOVEMENT_GRANULARITY_CHARACTER);
                case BrailleInputEvent.CMD_NAV_ITEM_NEXT:
                    return nextAtMovementGranularity(
                            AccessibilityNodeInfoCompat
                            .MOVEMENT_GRANULARITY_CHARACTER);
                case BrailleInputEvent.CMD_NAV_LINE_PREVIOUS:
                    return previousAtMovementGranularity(
                            AccessibilityNodeInfoCompat
                            .MOVEMENT_GRANULARITY_LINE);
                case BrailleInputEvent.CMD_NAV_LINE_NEXT:
                    return nextAtMovementGranularity(
                            AccessibilityNodeInfoCompat
                            .MOVEMENT_GRANULARITY_LINE);
            }
        }

        // These commands are handled by the IME only when it has taken
        // control of the display. Otherwise, they are delegated.
        if (ime != null && mState.controlsDisplay()) {
            switch (event.getCommand()) {
                case BrailleInputEvent.CMD_ACTIVATE_CURRENT:
                    return ime.sendDefaultAction();
                case BrailleInputEvent.CMD_ROUTE:
                    return ime.route(event.getArgument(), content);
            }
        }

        // If all else fails, delegate the event.
        return mNext.onMappedInputEvent(event, content);
    }

    /* BrailleIME.Host implementation */

    @Override
    public BrailleTranslator getBrailleTranslator() {
        return mTranslatorManager.getUncontractedTranslator();
    }

    @Override
    public DisplayManager getDisplayManager() {
        // Only allow the IME to reach the display manager if the display
        // is controlled by the IME in the current state.
        return mState.controlsDisplay() ? mDisplayManager : null;
    }

    @Override
    public FeedbackManager getFeedbackManager() {
        return mFeedbackManager;
    }

    @Override
    public void onCreateIME() {
    }

    @Override
    public void onDestroyIME() {
        mInputBound = false;
        mInputStarted = false;
        updateState();
    }

    @Override
    public void onBindInput() {
        mInputBound = true;
        updateState();
    }

    @Override
    public void onUnbindInput() {
        mInputBound = false;
        updateState();
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        mInputStarted = true;
        updateState();
    }

    @Override
    public void onFinishInput() {
        mInputStarted = false;
        updateState();
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        mInputViewStarted = true;
        updateState();
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        mInputViewStarted = false;
        updateState();
    }

    /* Private */

    /** Returns the hosted IME, if any. */
    private BrailleIME getIME() {
        return mIME != null ? mIME : BrailleIME.getActiveInstance();
    }

    /** Changes state, possibly notifying the next navigation mode. */
    private void setState(State newState) {
        if (mState == newState) {
            return;
        }

        State oldState = mState;
        mState = newState;
        if (oldState.controlsDisplay() && !newState.controlsDisplay()) {
            activateNext();
        } else if (!oldState.controlsDisplay() && newState.controlsDisplay()) {
            deactivateNext();
            BrailleIME ime = getIME();
            if (ime != null) {
                ime.updateDisplay();
            }
        }

        LogUtils.log(this, Log.VERBOSE, "state change: %s -> %s",
                oldState, newState);
    }

    /** Changes states based on new information. */
    private void updateState() {
        boolean imeOpen = isImeOpen();
        if (mActive) {
            mSelfBrailleManager.setImeOpen(imeOpen);
        }
        if (!mInputBound) {
            setState(State.INACTIVE);
        } else if (isModalFieldFocused()) {
            setState(State.MODAL_EDITOR);
        } else if (!isImeOpen()) {
            setState(State.TEXT_ONLY);
        } else if (isSelfBrailledFieldFocused()) {
            setState(State.TEXT_AND_NAVIGATION);
        } else {
            setState(State.TEXT_ONLY);
        }
    }

    private boolean isImeOpen() {
        return mInputStarted && mInputViewStarted;
    }

    /** Activates the next navigation mode, suppressing spurious calls. */
    private void activateNext() {
        if (!mNextActive) {
            mNextActive = true;
            mNext.onActivate();
        }
    }

    /** Deactivates the next navigation mode, suppressing spurious calls. */
    private void deactivateNext() {
        if (mNextActive) {
            mNextActive = false;
            mNext.onDeactivate();
        }
    }

    /** Returns true if a field suitable for modal editing is focused. */
    private boolean isModalFieldFocused() {
        // Only instances of EditText with both input and accessibility focus
        // should be edited modally.
        AccessibilityNodeInfoCompat root = null;
        AccessibilityNodeInfoCompat accessibilityFocused = null;
        try {
            root = AccessibilityServiceCompatUtils.getRootInActiveWindow(
                    mAccessibilityService);
            if (root == null) {
                return false;
            }
            accessibilityFocused = root.findFocus(
                AccessibilityNodeInfoCompat.FOCUS_ACCESSIBILITY);
            return (accessibilityFocused != null &&
                    accessibilityFocused.isFocused() &&
                    AccessibilityNodeInfoUtils.nodeMatchesClassByType(
                            mAccessibilityService, accessibilityFocused,
                            EditText.class));
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(
                root, accessibilityFocused);
        }
    }

    /** Returns true if a self-brailled node is focused. */
    private boolean isSelfBrailledFieldFocused() {
        AccessibilityNodeInfoCompat root = null;
        AccessibilityNodeInfoCompat inputFocused = null;
        AccessibilityNodeInfoCompat accessibilityFocused = null;
        try {
            root = AccessibilityServiceCompatUtils.getRootInActiveWindow(
                    mAccessibilityService);
            if (root == null) {
                return false;
            }
            inputFocused = root.findFocus(
                AccessibilityNodeInfoCompat.FOCUS_INPUT);
            if (!mSelfBrailleManager.hasContentForNode(inputFocused)) {
                return false;
            }
            accessibilityFocused = root.findFocus(
                AccessibilityNodeInfoCompat.FOCUS_ACCESSIBILITY);
            return inputFocused.equals(accessibilityFocused);
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(
                root, inputFocused, accessibilityFocused);
        }
    }

    /** Returns the node with both accessibility and input focus, if any. */
    private AccessibilityNodeInfoCompat getFocusedNode() {
        AccessibilityNodeInfoCompat root = null;
        AccessibilityNodeInfoCompat inputFocused = null;
        AccessibilityNodeInfoCompat accessibilityFocused = null;
        try {
            root = AccessibilityServiceCompatUtils.getRootInActiveWindow(
                    mAccessibilityService);
            if (root == null) {
                return null;
            }
            inputFocused = root.findFocus(
                AccessibilityNodeInfoCompat.FOCUS_INPUT);
            if (inputFocused == null) {
                return null;
            }
            accessibilityFocused = root.findFocus(
                AccessibilityNodeInfoCompat.FOCUS_ACCESSIBILITY);
            return inputFocused.equals(accessibilityFocused)
                ? inputFocused : null;
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(
                root, accessibilityFocused);
        }
    }

    /** Asks the focused node to navigate at the requested granularity. */
    private boolean navigateAtMovementGranularity(int action,
            int granularity) {
        Bundle arguments = new Bundle();
        arguments.putInt(AccessibilityNodeInfoCompat
                .ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT, granularity);
        AccessibilityNodeInfoCompat node = getFocusedNode();
        if (node == null) {
            return false;
        }
        try {
            return node.performAction(action, arguments);
        } finally {
            node.recycle();
        }
    }

    /** Asks the focused node to navigate at the requested granularity. */
    private boolean nextAtMovementGranularity(int granularity) {
        return navigateAtMovementGranularity(
            AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY,
            granularity);
    }

    /** Asks the focused node to navigate at the requested granularity. */
    private boolean previousAtMovementGranularity(int granularity) {
        return navigateAtMovementGranularity(
            AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY,
            granularity);
    }
}

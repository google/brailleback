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

import static android.support.v4.view.accessibility.AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY;
import static android.support.v4.view.accessibility.AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY;
import static android.support.v4.view.accessibility.AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_CHARACTER;
import static android.support.v4.view.accessibility.AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_LINE;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.googlecode.eyesfree.braille.display.BrailleInputEvent;
import com.googlecode.eyesfree.braille.translate.BrailleTranslator;

import android.accessibilityservice.AccessibilityService;
import android.os.Bundle;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.test.suitebuilder.annotation.MediumTest;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import junit.framework.TestCase;

import org.hamcrest.Description;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/**
 * Tests for the IME navigation mode.
 *
 * The bulk of this class is a large switch statement.
 * Only particular cases are chosen to test because otherwise the test will
 * simply become a very verbose copy of the switch.
 */
@MediumTest
public class IMENavigationModeTest extends TestCase {

    private IMENavigationMode mIMENavMode;
    @Mock private NavigationMode mNext;
    @Mock private AccessibilityService mAccessibilityService;
    @Mock private DisplayManager mDisplayManager;
    @Mock private FeedbackManager mFeedbackManager;
    @Mock private SelfBrailleManager mSelfBrailleManager;
    @Mock private TranslatorManager mTranslatorManager;
    @Mock private BrailleIME mIME;
    @Mock private BrailleTranslator mBrailleTranslator;

    @Override
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mIMENavMode = new IMENavigationMode(mNext, mAccessibilityService,
                mDisplayManager, mFeedbackManager, mSelfBrailleManager,
                mTranslatorManager, mIME);
        when(mTranslatorManager.getUncontractedTranslator())
            .thenReturn(mBrailleTranslator);
    }

    /**
     * Tests the behaviour of the "inactive" mode.
     * This should be the mode used as long as input is not bound.
     */
    public void testInactiveMode() {
        mIMENavMode.onActivate();
        verify(mSelfBrailleManager).setImeOpen(false);
        mIMENavMode.onCreateIME();
        verify(mNext).onActivate();

        assertEquals(mBrailleTranslator, mIMENavMode.getBrailleTranslator());
        assertNull(mIMENavMode.getDisplayManager());
        assertEquals(mFeedbackManager, mIMENavMode.getFeedbackManager());

        AccessibilityEvent accessibilityEvent = AccessibilityEvent.obtain();
        try {
            mIMENavMode.onObserveAccessibilityEvent(accessibilityEvent);
            verify(mNext).onObserveAccessibilityEvent(accessibilityEvent);
        } finally {
            accessibilityEvent.recycle();
        }

        accessibilityEvent = AccessibilityEvent.obtain();
        try {
            mIMENavMode.onAccessibilityEvent(accessibilityEvent);
            verify(mNext).onAccessibilityEvent(accessibilityEvent);
        } finally {
            accessibilityEvent.recycle();
        }

        AccessibilityNodeInfoCompat node =
            AccessibilityNodeInfoCompat.obtain();
        try {
            mIMENavMode.onInvalidateAccessibilityNode(node);
            verify(mNext).onInvalidateAccessibilityNode(node);
        } finally {
            node.recycle();
        }

        DisplayManager.Content content = new DisplayManager.Content("");
        mIMENavMode.onPanLeftOverflow(content);
        verify(mNext).onPanLeftOverflow(content);
        mIMENavMode.onPanRightOverflow(content);
        verify(mNext).onPanRightOverflow(content);

        BrailleInputEvent inputEvent = new BrailleInputEvent(
                BrailleInputEvent.CMD_KEY_ENTER, 0, 0);
        mIMENavMode.onMappedInputEvent(inputEvent, content);
        verify(mNext).onMappedInputEvent(inputEvent, content);
        verify(mIME, never()).sendAndroidKey(anyInt());

        inputEvent = new BrailleInputEvent(
                BrailleInputEvent.CMD_NAV_ITEM_NEXT, 0, 0);
        mIMENavMode.onMappedInputEvent(inputEvent, content);
        verify(mNext).onMappedInputEvent(inputEvent, content);
        verify(mIME, never()).moveCursor(anyInt(), anyInt());

        inputEvent = new BrailleInputEvent(
                BrailleInputEvent.CMD_ACTIVATE_CURRENT, 0, 0);
        mIMENavMode.onMappedInputEvent(inputEvent, content);
        verify(mNext).onMappedInputEvent(inputEvent, content);
        verify(mIME, never()).sendDefaultAction();

        inputEvent = new BrailleInputEvent(
                BrailleInputEvent.CMD_ROUTE, 0, 0);
        mIMENavMode.onMappedInputEvent(inputEvent, content);
        verify(mNext).onMappedInputEvent(inputEvent, content);
        verify(mIME, never()).route(anyInt(),
                any(DisplayManager.Content.class));

        mIMENavMode.onDestroyIME();
        verify(mSelfBrailleManager, never()).setImeOpen(true);
        Mockito.reset(mSelfBrailleManager);

        // Deactivate, but make sure it didn't happen too early.
        verify(mNext, never()).onDeactivate();
        mIMENavMode.onDeactivate();
        verify(mNext).onDeactivate();
        verify(mSelfBrailleManager).setImeOpen(false);
    }

    /**
     * Tests the behaviour of the "text only" mode.
     * Used when there's an input connection, but input is not started.
     */
    public void testTextOnlyMode() {
        mIMENavMode.onActivate();
        mIMENavMode.onCreateIME();
        verify(mNext).onActivate();
        mIMENavMode.onBindInput();

        assertEquals(mBrailleTranslator, mIMENavMode.getBrailleTranslator());
        assertNull(mIMENavMode.getDisplayManager());
        assertEquals(mFeedbackManager, mIMENavMode.getFeedbackManager());

        AccessibilityEvent accessibilityEvent = AccessibilityEvent.obtain();
        try {
            mIMENavMode.onObserveAccessibilityEvent(accessibilityEvent);
            verify(mNext).onObserveAccessibilityEvent(accessibilityEvent);
        } finally {
            accessibilityEvent.recycle();
        }

        accessibilityEvent = AccessibilityEvent.obtain();
        try {
            mIMENavMode.onAccessibilityEvent(accessibilityEvent);
            verify(mNext).onAccessibilityEvent(accessibilityEvent);
        } finally {
            accessibilityEvent.recycle();
        }

        AccessibilityNodeInfoCompat node =
            AccessibilityNodeInfoCompat.obtain();
        try {
            mIMENavMode.onInvalidateAccessibilityNode(node);
            verify(mNext).onInvalidateAccessibilityNode(node);
        } finally {
            node.recycle();
        }

        DisplayManager.Content content = new DisplayManager.Content("");
        mIMENavMode.onPanLeftOverflow(content);
        verify(mNext).onPanLeftOverflow(content);
        mIMENavMode.onPanRightOverflow(content);
        verify(mNext).onPanRightOverflow(content);

        BrailleInputEvent inputEvent = new BrailleInputEvent(
                BrailleInputEvent.CMD_KEY_ENTER, 0, 0);
        mIMENavMode.onMappedInputEvent(inputEvent, content);
        verify(mNext, never()).onMappedInputEvent(inputEvent, content);
        verify(mIME).sendAndroidKey(KeyEvent.KEYCODE_ENTER);

        inputEvent = new BrailleInputEvent(
                BrailleInputEvent.CMD_KEY_DEL, 0, 0);
        mIMENavMode.onMappedInputEvent(inputEvent, content);
        verify(mNext, never()).onMappedInputEvent(inputEvent, content);
        verify(mIME).sendAndroidKey(KeyEvent.KEYCODE_DEL);

        inputEvent = new BrailleInputEvent(
                BrailleInputEvent.CMD_BRAILLE_KEY, 0x1b, 0);
        mIMENavMode.onMappedInputEvent(inputEvent, content);
        verify(mNext, never()).onMappedInputEvent(inputEvent, content);
        verify(mIME).handleBrailleKey(0x1b);

        inputEvent = new BrailleInputEvent(
                BrailleInputEvent.CMD_NAV_ITEM_NEXT, 0, 0);
        mIMENavMode.onMappedInputEvent(inputEvent, content);
        verify(mNext).onMappedInputEvent(inputEvent, content);
        verify(mIME, never()).moveCursor(anyInt(), anyInt());

        inputEvent = new BrailleInputEvent(
                BrailleInputEvent.CMD_ACTIVATE_CURRENT, 0, 0);
        mIMENavMode.onMappedInputEvent(inputEvent, content);
        verify(mNext).onMappedInputEvent(inputEvent, content);
        verify(mIME, never()).sendDefaultAction();

        inputEvent = new BrailleInputEvent(
                BrailleInputEvent.CMD_ROUTE, 0, 0);
        mIMENavMode.onMappedInputEvent(inputEvent, content);
        verify(mNext).onMappedInputEvent(inputEvent, content);
        verify(mIME, never()).route(anyInt(),
                any(DisplayManager.Content.class));

        mIMENavMode.onUnbindInput();
        mIMENavMode.onDestroyIME();
        verify(mSelfBrailleManager, atLeastOnce()).setImeOpen(false);
        verify(mSelfBrailleManager, never()).setImeOpen(true);

        // Deactivate, but make sure it didn't happen too early.
        verify(mNext, never()).onDeactivate();
        mIMENavMode.onDeactivate();
        verify(mNext).onDeactivate();
    }

    /**
     * Tests the behaviour of the "text only" mode.
     * Also used when input is started, but the field is not handled
     * specially (as with, for example, a focused EditText).
     */
    public void testTextOnlyModeInputStarted() {
        EditorInfo ei = new EditorInfo();

        // Mock out the AccessibilityNodeInfo.
        // The class actually uses the compat variant, but on recent API
        // releases (including the test environment) they should call through.
        AccessibilityNodeInfo rawNode = mock(AccessibilityNodeInfo.class);
        when(mAccessibilityService.getRootInActiveWindow())
            .thenReturn(rawNode);
        when(rawNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT))
            .thenReturn(rawNode);
        when(rawNode.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY))
            .thenReturn(rawNode);
        when(rawNode.getClassName()).thenReturn("com.example.UnknownWidget");

        mIMENavMode.onActivate();
        mIMENavMode.onCreateIME();
        verify(mNext).onActivate();
        mIMENavMode.onBindInput();
        mIMENavMode.onStartInput(ei, false /* restarting */);
        Mockito.reset(mSelfBrailleManager);
        mIMENavMode.onStartInputView(ei, false /* restarting */);
        verify(mSelfBrailleManager).setImeOpen(true);

        assertEquals(mBrailleTranslator, mIMENavMode.getBrailleTranslator());
        assertNull(mIMENavMode.getDisplayManager());
        assertEquals(mFeedbackManager, mIMENavMode.getFeedbackManager());

        AccessibilityEvent accessibilityEvent = AccessibilityEvent.obtain();
        try {
            mIMENavMode.onObserveAccessibilityEvent(accessibilityEvent);
            verify(mNext).onObserveAccessibilityEvent(accessibilityEvent);
        } finally {
            accessibilityEvent.recycle();
        }

        accessibilityEvent = AccessibilityEvent.obtain();
        try {
            mIMENavMode.onAccessibilityEvent(accessibilityEvent);
            verify(mNext).onAccessibilityEvent(accessibilityEvent);
        } finally {
            accessibilityEvent.recycle();
        }

        AccessibilityNodeInfoCompat node =
            AccessibilityNodeInfoCompat.obtain();
        try {
            mIMENavMode.onInvalidateAccessibilityNode(node);
            verify(mNext).onInvalidateAccessibilityNode(node);
        } finally {
            node.recycle();
        }

        DisplayManager.Content content = new DisplayManager.Content("");
        mIMENavMode.onPanLeftOverflow(content);
        verify(mNext).onPanLeftOverflow(content);
        mIMENavMode.onPanRightOverflow(content);
        verify(mNext).onPanRightOverflow(content);

        BrailleInputEvent inputEvent = new BrailleInputEvent(
                BrailleInputEvent.CMD_KEY_ENTER, 0, 0);
        mIMENavMode.onMappedInputEvent(inputEvent, content);
        verify(mNext, never()).onMappedInputEvent(inputEvent, content);
        verify(mIME).sendAndroidKey(KeyEvent.KEYCODE_ENTER);

        inputEvent = new BrailleInputEvent(
                BrailleInputEvent.CMD_KEY_DEL, 0, 0);
        mIMENavMode.onMappedInputEvent(inputEvent, content);
        verify(mNext, never()).onMappedInputEvent(inputEvent, content);
        verify(mIME).sendAndroidKey(KeyEvent.KEYCODE_DEL);

        inputEvent = new BrailleInputEvent(
                BrailleInputEvent.CMD_BRAILLE_KEY, 0x1b, 0);
        mIMENavMode.onMappedInputEvent(inputEvent, content);
        verify(mNext, never()).onMappedInputEvent(inputEvent, content);
        verify(mIME).handleBrailleKey(0x1b);

        inputEvent = new BrailleInputEvent(
                BrailleInputEvent.CMD_NAV_ITEM_NEXT, 0, 0);
        mIMENavMode.onMappedInputEvent(inputEvent, content);
        verify(mNext).onMappedInputEvent(inputEvent, content);
        verify(mIME, never()).moveCursor(anyInt(), anyInt());

        inputEvent = new BrailleInputEvent(
                BrailleInputEvent.CMD_ACTIVATE_CURRENT, 0, 0);
        mIMENavMode.onMappedInputEvent(inputEvent, content);
        verify(mNext).onMappedInputEvent(inputEvent, content);
        verify(mIME, never()).sendDefaultAction();

        inputEvent = new BrailleInputEvent(
                BrailleInputEvent.CMD_ROUTE, 0, 0);
        mIMENavMode.onMappedInputEvent(inputEvent, content);
        verify(mNext).onMappedInputEvent(inputEvent, content);
        verify(mIME, never()).route(anyInt(),
                any(DisplayManager.Content.class));

        verify(mSelfBrailleManager, never()).setImeOpen(false);
        Mockito.reset(mSelfBrailleManager);

        mIMENavMode.onFinishInputView(true);
        mIMENavMode.onFinishInput();
        mIMENavMode.onUnbindInput();
        mIMENavMode.onDestroyIME();

        verify(mSelfBrailleManager, never()).setImeOpen(true);
        verify(mSelfBrailleManager, atLeastOnce()).setImeOpen(false);

        // Deactivate, but make sure it didn't happen too early.
        verify(mNext, never()).onDeactivate();
        mIMENavMode.onDeactivate();
        verify(mNext).onDeactivate();
    }

    /**
     * Tests that text and navigation mode is not triggered when the input view
     * is not shown, even if input is started. This distinction is needed to
     * handle Chrome correctly.
     */
    public void testTextAndNavigationModeRequiresStartInputView() {
        EditorInfo ei = new EditorInfo();

        // Mock out the AccessibilityNodeInfo.
        // The class actually uses the compat variant, but on recent API
        // releases (including the test environment) they should call through.
        AccessibilityNodeInfo rawNode = mock(AccessibilityNodeInfo.class);
        when(mAccessibilityService.getRootInActiveWindow())
            .thenReturn(rawNode);
        when(rawNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT))
            .thenReturn(rawNode);
        when(rawNode.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY))
            .thenReturn(rawNode);
        when(rawNode.getClassName()).thenReturn("com.example.ExampleWebView");
        when(mSelfBrailleManager.hasContentForNode(
                    compatWrapperForNode(rawNode))).thenReturn(true);

        mIMENavMode.onActivate();
        mIMENavMode.onCreateIME();
        verify(mNext).onActivate();
        mIMENavMode.onBindInput();
        mIMENavMode.onStartInput(ei, false /* restarting */);

        assertEquals(mBrailleTranslator, mIMENavMode.getBrailleTranslator());
        assertNull(mIMENavMode.getDisplayManager());
        assertEquals(mFeedbackManager, mIMENavMode.getFeedbackManager());
        verify(mSelfBrailleManager, atLeastOnce()).setImeOpen(false);

        AccessibilityEvent accessibilityEvent = AccessibilityEvent.obtain();
        try {
            mIMENavMode.onObserveAccessibilityEvent(accessibilityEvent);
            verify(mNext).onObserveAccessibilityEvent(accessibilityEvent);
        } finally {
            accessibilityEvent.recycle();
        }

        accessibilityEvent = AccessibilityEvent.obtain();
        try {
            mIMENavMode.onAccessibilityEvent(accessibilityEvent);
            verify(mNext).onAccessibilityEvent(accessibilityEvent);
        } finally {
            accessibilityEvent.recycle();
        }

        AccessibilityNodeInfoCompat node =
            AccessibilityNodeInfoCompat.obtain();
        try {
            mIMENavMode.onInvalidateAccessibilityNode(node);
            verify(mNext).onInvalidateAccessibilityNode(node);
        } finally {
            node.recycle();
        }

        DisplayManager.Content content = new DisplayManager.Content("");
        mIMENavMode.onPanLeftOverflow(content);
        verify(mNext).onPanLeftOverflow(content);
        mIMENavMode.onPanRightOverflow(content);
        verify(mNext).onPanRightOverflow(content);

        BrailleInputEvent inputEvent = new BrailleInputEvent(
                BrailleInputEvent.CMD_KEY_ENTER, 0, 0);
        mIMENavMode.onMappedInputEvent(inputEvent, content);
        verify(mNext, never()).onMappedInputEvent(inputEvent, content);
        verify(mIME).sendAndroidKey(KeyEvent.KEYCODE_ENTER);

        inputEvent = new BrailleInputEvent(
                BrailleInputEvent.CMD_KEY_DEL, 0, 0);
        mIMENavMode.onMappedInputEvent(inputEvent, content);
        verify(mNext, never()).onMappedInputEvent(inputEvent, content);
        verify(mIME).sendAndroidKey(KeyEvent.KEYCODE_DEL);

        inputEvent = new BrailleInputEvent(
                BrailleInputEvent.CMD_BRAILLE_KEY, 0x1b, 0);
        mIMENavMode.onMappedInputEvent(inputEvent, content);
        verify(mNext, never()).onMappedInputEvent(inputEvent, content);
        verify(mIME).handleBrailleKey(0x1b);

        inputEvent = new BrailleInputEvent(
                BrailleInputEvent.CMD_NAV_ITEM_NEXT, 0, 0);
        mIMENavMode.onMappedInputEvent(inputEvent, content);
        verify(mNext).onMappedInputEvent(inputEvent, content);
        verify(mIME, never()).moveCursor(anyInt(), anyInt());

        inputEvent = new BrailleInputEvent(
                BrailleInputEvent.CMD_ACTIVATE_CURRENT, 0, 0);
        mIMENavMode.onMappedInputEvent(inputEvent, content);
        verify(mNext).onMappedInputEvent(inputEvent, content);
        verify(mIME, never()).sendDefaultAction();

        inputEvent = new BrailleInputEvent(
                BrailleInputEvent.CMD_ROUTE, 0, 0);
        mIMENavMode.onMappedInputEvent(inputEvent, content);
        verify(mNext).onMappedInputEvent(inputEvent, content);
        verify(mIME, never()).route(anyInt(),
                any(DisplayManager.Content.class));

        mIMENavMode.onFinishInput();
        mIMENavMode.onUnbindInput();
        mIMENavMode.onDestroyIME();

        verify(mSelfBrailleManager, never()).setImeOpen(true);

        // Deactivate, but make sure it didn't happen too early.
        verify(mNext, never()).onDeactivate();
        mIMENavMode.onDeactivate();
        verify(mNext).onDeactivate();
    }

    /**
     * Tests the behaviour of the "text and navigation" mode.
     */
    public void testTextAndNavigationMode() {
        EditorInfo ei = new EditorInfo();

        // Mock out the AccessibilityNodeInfo.
        // The class actually uses the compat variant, but on recent API
        // releases (including the test environment) they should call through.
        AccessibilityNodeInfo rawNode = mock(AccessibilityNodeInfo.class);
        when(mAccessibilityService.getRootInActiveWindow())
            .thenReturn(rawNode);
        when(rawNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT))
            .thenReturn(rawNode);
        when(rawNode.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY))
            .thenReturn(rawNode);
        when(rawNode.getClassName()).thenReturn("com.example.ExampleWebView");
        when(mSelfBrailleManager.hasContentForNode(
                    compatWrapperForNode(rawNode))).thenReturn(true);

        mIMENavMode.onActivate();
        mIMENavMode.onCreateIME();
        verify(mNext).onActivate();
        mIMENavMode.onBindInput();
        mIMENavMode.onStartInput(ei, false /* restarting */);
        verify(mSelfBrailleManager, atLeastOnce()).setImeOpen(false);
        verify(mSelfBrailleManager, never()).setImeOpen(true);
        mIMENavMode.onStartInputView(ei, false /* restarting */);

        assertEquals(mBrailleTranslator, mIMENavMode.getBrailleTranslator());
        assertNull(mIMENavMode.getDisplayManager());
        assertEquals(mFeedbackManager, mIMENavMode.getFeedbackManager());
        verify(mSelfBrailleManager, atLeastOnce()).setImeOpen(true);

        AccessibilityEvent accessibilityEvent = AccessibilityEvent.obtain();
        try {
            mIMENavMode.onObserveAccessibilityEvent(accessibilityEvent);
            verify(mNext).onObserveAccessibilityEvent(accessibilityEvent);
        } finally {
            accessibilityEvent.recycle();
        }

        accessibilityEvent = AccessibilityEvent.obtain();
        try {
            mIMENavMode.onAccessibilityEvent(accessibilityEvent);
            verify(mNext).onAccessibilityEvent(accessibilityEvent);
        } finally {
            accessibilityEvent.recycle();
        }

        AccessibilityNodeInfoCompat node =
            AccessibilityNodeInfoCompat.obtain();
        try {
            mIMENavMode.onInvalidateAccessibilityNode(node);
            verify(mNext).onInvalidateAccessibilityNode(node);
        } finally {
            node.recycle();
        }

        DisplayManager.Content content = new DisplayManager.Content("");
        mIMENavMode.onPanLeftOverflow(content);
        verify(mNext).onPanLeftOverflow(content);
        mIMENavMode.onPanRightOverflow(content);
        verify(mNext).onPanRightOverflow(content);

        BrailleInputEvent inputEvent = new BrailleInputEvent(
                BrailleInputEvent.CMD_KEY_ENTER, 0, 0);
        mIMENavMode.onMappedInputEvent(inputEvent, content);
        verify(mNext, never()).onMappedInputEvent(inputEvent, content);
        verify(mIME).sendAndroidKey(KeyEvent.KEYCODE_ENTER);

        inputEvent = new BrailleInputEvent(
                BrailleInputEvent.CMD_KEY_DEL, 0, 0);
        mIMENavMode.onMappedInputEvent(inputEvent, content);
        verify(mNext, never()).onMappedInputEvent(inputEvent, content);
        verify(mIME).sendAndroidKey(KeyEvent.KEYCODE_DEL);

        inputEvent = new BrailleInputEvent(
                BrailleInputEvent.CMD_BRAILLE_KEY, 0x1b, 0);
        mIMENavMode.onMappedInputEvent(inputEvent, content);
        verify(mNext, never()).onMappedInputEvent(inputEvent, content);
        verify(mIME).handleBrailleKey(0x1b);

        inputEvent = new BrailleInputEvent(
                BrailleInputEvent.CMD_ACTIVATE_CURRENT, 0, 0);
        mIMENavMode.onMappedInputEvent(inputEvent, content);
        verify(mNext).onMappedInputEvent(inputEvent, content);
        verify(mIME, never()).sendDefaultAction();

        inputEvent = new BrailleInputEvent(
                BrailleInputEvent.CMD_ROUTE, 0, 0);
        mIMENavMode.onMappedInputEvent(inputEvent, content);
        verify(mNext).onMappedInputEvent(inputEvent, content);
        verify(mIME, never()).route(anyInt(),
                any(DisplayManager.Content.class));

        // Nothing above this point should have triggered an action on the
        // accessibility node.
        verify(rawNode, never()).performAction(anyInt(), any(Bundle.class));
        verifyEventCausesNavigation(BrailleInputEvent.CMD_NAV_ITEM_NEXT,
                ACTION_NEXT_AT_MOVEMENT_GRANULARITY,
                MOVEMENT_GRANULARITY_CHARACTER, rawNode, content);
        verifyEventCausesNavigation(BrailleInputEvent.CMD_NAV_ITEM_PREVIOUS,
                ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY,
                MOVEMENT_GRANULARITY_CHARACTER, rawNode, content);
        verifyEventCausesNavigation(BrailleInputEvent.CMD_NAV_LINE_NEXT,
                ACTION_NEXT_AT_MOVEMENT_GRANULARITY,
                MOVEMENT_GRANULARITY_LINE, rawNode, content);
        verifyEventCausesNavigation(BrailleInputEvent.CMD_NAV_LINE_PREVIOUS,
                ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY,
                MOVEMENT_GRANULARITY_LINE, rawNode, content);

        Mockito.reset(mSelfBrailleManager);

        mIMENavMode.onFinishInputView(true);
        verify(mSelfBrailleManager).setImeOpen(false);
        mIMENavMode.onFinishInput();
        mIMENavMode.onUnbindInput();
        mIMENavMode.onDestroyIME();
        verify(mSelfBrailleManager, never()).setImeOpen(true);

        // Deactivate, but make sure it didn't happen too early.
        verify(mNext, never()).onDeactivate();
        mIMENavMode.onDeactivate();
        verify(mNext).onDeactivate();
    }

    /** Helper for {@link #testTextAndNavigationMode}. */
    private void verifyEventCausesNavigation(int command, int action,
            int granularity, AccessibilityNodeInfo rawNode,
            DisplayManager.Content content) {
        BrailleInputEvent inputEvent = new BrailleInputEvent(command, 0, 0);
        mIMENavMode.onMappedInputEvent(inputEvent, content);
        verify(mNext, never()).onMappedInputEvent(inputEvent, content);
        verify(mIME, never()).moveCursor(anyInt(), anyInt());
        verify(rawNode).performAction(eq(action),
                argumentsBundleHasGranularity(granularity));
    }

    /**
     * Tests the behaviour of the "modal editor" mode.
     */
    public void testModalEditorMode() {
        mIMENavMode.onActivate();
        verify(mNext).onActivate();
        EditorInfo ei = new EditorInfo();

        // Mock out the AccessibilityNodeInfo.
        // The class actually uses the compat variant, but on recent API
        // releases (including the test environment) they should call through.
        AccessibilityNodeInfo rawNode = mock(AccessibilityNodeInfo.class);
        when(mAccessibilityService.getRootInActiveWindow())
            .thenReturn(rawNode);
        when(rawNode.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY))
            .thenReturn(rawNode);
        when(rawNode.getClassName()).thenReturn(EditText.class.getName());
        when(rawNode.isFocused()).thenReturn(true);

        mIMENavMode.onCreateIME();
        mIMENavMode.onBindInput();

        mIMENavMode.onStartInput(ei, false /* restarting */);
        mIMENavMode.onStartInputView(ei, false /* restarting */);
        verify(mNext, times(1)).onDeactivate();

        assertEquals(mBrailleTranslator, mIMENavMode.getBrailleTranslator());
        assertEquals(mDisplayManager, mIMENavMode.getDisplayManager());
        assertEquals(mFeedbackManager, mIMENavMode.getFeedbackManager());

        AccessibilityEvent accessibilityEvent = AccessibilityEvent.obtain();
        try {
            mIMENavMode.onObserveAccessibilityEvent(accessibilityEvent);
            verify(mNext).onObserveAccessibilityEvent(accessibilityEvent);
        } finally {
            accessibilityEvent.recycle();
        }

        accessibilityEvent = AccessibilityEvent.obtain();
        try {
            mIMENavMode.onAccessibilityEvent(accessibilityEvent);
            verify(mNext, never()).onAccessibilityEvent(accessibilityEvent);
        } finally {
            accessibilityEvent.recycle();
        }

        AccessibilityNodeInfoCompat node =
            AccessibilityNodeInfoCompat.obtain();
        try {
            mIMENavMode.onInvalidateAccessibilityNode(node);
            verify(mNext, never()).onInvalidateAccessibilityNode(node);
        } finally {
            node.recycle();
        }

        // Move input focus away and back again.
        when(rawNode.isFocused()).thenReturn(false);
        accessibilityEvent = AccessibilityEvent.obtain();
        accessibilityEvent.setEventType(AccessibilityEvent.TYPE_VIEW_FOCUSED);
        try {
            mIMENavMode.onObserveAccessibilityEvent(accessibilityEvent);
            verify(mNext, times(2)).onActivate();
            when(rawNode.isFocused()).thenReturn(true);
            mIMENavMode.onObserveAccessibilityEvent(accessibilityEvent);
            verify(mNext, times(2)).onDeactivate();
        } finally {
            accessibilityEvent.recycle();
        }

        DisplayManager.Content content = new DisplayManager.Content("");
        mIMENavMode.onPanLeftOverflow(content);
        verify(mNext).onPanLeftOverflow(content);
        mIMENavMode.onPanRightOverflow(content);
        verify(mNext).onPanRightOverflow(content);

        BrailleInputEvent inputEvent = new BrailleInputEvent(
                BrailleInputEvent.CMD_KEY_ENTER, 0, 0);
        mIMENavMode.onMappedInputEvent(inputEvent, content);
        verify(mNext, never()).onMappedInputEvent(inputEvent, content);
        verify(mIME).sendAndroidKey(KeyEvent.KEYCODE_ENTER);

        inputEvent = new BrailleInputEvent(
                BrailleInputEvent.CMD_KEY_DEL, 0, 0);
        mIMENavMode.onMappedInputEvent(inputEvent, content);
        verify(mNext, never()).onMappedInputEvent(inputEvent, content);
        verify(mIME).sendAndroidKey(KeyEvent.KEYCODE_DEL);

        inputEvent = new BrailleInputEvent(
                BrailleInputEvent.CMD_BRAILLE_KEY, 0x1b, 0);
        mIMENavMode.onMappedInputEvent(inputEvent, content);
        verify(mNext, never()).onMappedInputEvent(inputEvent, content);
        verify(mIME).handleBrailleKey(0x1b);

        inputEvent = new BrailleInputEvent(
                BrailleInputEvent.CMD_NAV_ITEM_NEXT, 0, 0);
        mIMENavMode.onMappedInputEvent(inputEvent, content);
        verify(mNext, never()).onMappedInputEvent(inputEvent, content);
        verify(mIME).moveCursor(BrailleIME.DIRECTION_FORWARD,
                AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_CHARACTER);

        inputEvent = new BrailleInputEvent(
                BrailleInputEvent.CMD_ACTIVATE_CURRENT, 0, 0);
        mIMENavMode.onMappedInputEvent(inputEvent, content);
        verify(mNext, never()).onMappedInputEvent(inputEvent, content);
        verify(mIME).sendDefaultAction();

        inputEvent = new BrailleInputEvent(
                BrailleInputEvent.CMD_ROUTE, 0, 0);
        mIMENavMode.onMappedInputEvent(inputEvent, content);
        verify(mNext, never()).onMappedInputEvent(inputEvent, content);
        verify(mIME).route(0, content);

        // Finishing and unbinding the input should activate the next nav mode
        // again.
        mIMENavMode.onFinishInputView(true);
        mIMENavMode.onFinishInput();
        mIMENavMode.onUnbindInput();
        verify(mNext, times(3)).onActivate();

        mIMENavMode.onDestroyIME();

        // Deactivate, but make sure it didn't happen too early.
        verify(mNext, times(2)).onDeactivate();
        mIMENavMode.onDeactivate();
        verify(mNext, times(3)).onDeactivate();
    }

    private Bundle argumentsBundleHasGranularity(int granularity) {
        return Mockito.argThat(new ArgumentsBundleHasGranularity(granularity));
    }

    private AccessibilityNodeInfoCompat compatWrapperForNode(
            AccessibilityNodeInfo node) {
        return Mockito.argThat(new CompatWrapperForNode(node));
    }

    private static class ArgumentsBundleHasGranularity
            extends ArgumentMatcher<Bundle> {
        private final int mGranularity;

        public ArgumentsBundleHasGranularity(int granularity) {
            mGranularity = granularity;
        }

        @Override
        public boolean matches(Object argument) {
            if (!(argument instanceof Bundle)) {
                return false;
            }
            Bundle bundle = (Bundle) argument;
            return mGranularity == bundle.getInt(AccessibilityNodeInfoCompat
                    .ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT);
        }

        @Override
        public void describeTo(Description description) {
            description.appendText(String.format(
                    "arguments bundle with granularity %d", mGranularity));
        }
    }

    private static class CompatWrapperForNode
            extends ArgumentMatcher<AccessibilityNodeInfoCompat> {
        private final AccessibilityNodeInfo mNode;

        public CompatWrapperForNode(AccessibilityNodeInfo node) {
            mNode = node;
        }

        @Override
        public boolean matches(Object argument) {
            if (!(argument instanceof AccessibilityNodeInfoCompat)) {
                return false;
            }
            return ((AccessibilityNodeInfoCompat) argument).getInfo() == mNode;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText(String.format(
                    "compat wrapper for node %s", mNode));
        }
    }
}

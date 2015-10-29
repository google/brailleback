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

import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.googlecode.eyesfree.braille.translate.BrailleTranslator;

import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.LargeTest;
import android.text.Spanned;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;

import org.hamcrest.Description;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/**
 * Tests for the Braille input method service.
 *
 * Doesn't use {@link android.test.ServiceTestCase} because the AIDL interface
 * it implements, {@link com.android.internal.view.IInputMethod}, is private to
 * the Android framework and subject to change.
 */
@LargeTest
public class BrailleIMETest extends AndroidTestCase {

    private BrailleIME mIME;
    @Mock private BrailleIME.Host mHost;
    @Mock private InputConnection mInputConnection;
    @Mock private BrailleTranslator mBrailleTranslator;
    @Mock private DisplayManager mDisplayManager;
    @Mock private FeedbackManager mFeedbackManager;

    @Override
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mIME = spy(new BrailleIME(mHost, getContext()));
    }

    /** Tests that lifecycle events are passed to the host. */
    public void testLifecycle() {
        EditorInfo editorInfo = getSampleEditorInfo();

        doReturn(null).when(mIME).getCurrentInputConnection();
        mIME.onCreate();
        verify(mHost).onCreateIME();

        doReturn(mInputConnection).when(mIME).getCurrentInputConnection();
        mIME.onBindInput();
        verify(mHost).onBindInput();

        doReturn(getSampleExtractedText()).when(mInputConnection)
            .getExtractedText(isA(ExtractedTextRequest.class), anyInt());
        mIME.onStartInput(editorInfo, false);
        verify(mHost).onStartInput(editorInfo, false);

        mIME.onStartInputView(editorInfo, false);
        verify(mHost).onStartInputView(editorInfo, false);

        mIME.onFinishInputView(true);
        verify(mHost).onFinishInputView(true);

        doReturn(null).when(mInputConnection)
            .getExtractedText(isA(ExtractedTextRequest.class), anyInt());
        mIME.onFinishInput();
        verify(mHost).onFinishInput();

        doReturn(null).when(mIME).getCurrentInputConnection();
        mIME.onUnbindInput();
        verify(mHost).onUnbindInput();

        mIME.onDestroy();
        verify(mHost).onDestroyIME();
    }

    /**
     * Tests that clicking the input view switches away from this IME.
     *
     * Ideally, we would mock out the
     * {@link android.view.inputmethod.InputMethodManager}. Unfortunately,
     * it is a final class which implements no interfaces, so we cannot mock
     * it. Instead, we settle for ensuring the internal method is called.
     */
    public void testSwitchIme() {
        doNothing().when(mIME).switchAwayFromThisIme();
        mIME.onCreate();
        View view = mIME.onCreateInputView();
        assertTrue(view.callOnClick());
        verify(mIME).switchAwayFromThisIme();
        mIME.onDestroy();
    }

    /** Tests that the IME monitors the extracted text. */
    public void testMonitorsExtractedText() {
        EditorInfo editorInfo = getSampleEditorInfo();
        ExtractedText et = getSampleExtractedText();
        ArgumentCaptor<ExtractedTextRequest> etr =
            ArgumentCaptor.forClass(ExtractedTextRequest.class);
        ArgumentCaptor<Integer> etFlags =
            ArgumentCaptor.forClass(Integer.class);

        autoStub(mDisplayManager, mInputConnection, editorInfo);
        when(mInputConnection.getExtractedText(
                    etr.capture(), etFlags.capture())).thenReturn(et);
        createBindAndStart(editorInfo);

        // Check the extracted text request.
        assertNotNull(etr.getValue());
        assertEquals(InputConnection.GET_EXTRACTED_TEXT_MONITOR,
                etFlags.getValue().intValue());
        int etToken = etr.getValue().token;

        // Check the provided display content.
        verifyDisplayContentMatches("Hello world! [Execute]");

        // Change the content and verify that the display manager is updated.
        et.text = "Hello Canada!";
        mIME.onUpdateExtractedText(etToken, et);
        verifyDisplayContentMatches("Hello Canada! [Execute]");

        finishUnbindAndDestroy();
    }

    /** Simple case of content formatting. */
    public void testContentFormattingSimple() {
        EditorInfo ei = getSampleEditorInfo();
        ExtractedText et = getSampleExtractedText();
        doTestContentFormatting(ei, et, "Hello world! [Execute]");
    }

    /** With no action. */
    public void testContentFormattingNoAction() {
        EditorInfo ei = getSampleEditorInfo();
        ei.imeOptions |= EditorInfo.IME_ACTION_NONE;
        ExtractedText et = getSampleExtractedText();
        doTestContentFormatting(ei, et, "Hello world!");
    }

    /** With a generic action. */
    public void testContentFormattingGenericAction() {
        EditorInfo ei = getSampleEditorInfo();
        ei.imeOptions |= EditorInfo.IME_ACTION_SEND;
        ExtractedText et = getSampleExtractedText();
        doTestContentFormatting(ei, et, "Hello world! [Send]");
    }

    /** With a custom action. */
    public void testContentFormattingCustomAction() {
        EditorInfo ei = getSampleEditorInfo();
        ei.actionLabel = "Action";
        ExtractedText et = getSampleExtractedText();
        doTestContentFormatting(ei, et, "Hello world! [Action]");
    }

    /** With a field label. */
    public void testContentFormattingLabel() {
        EditorInfo ei = getSampleEditorInfo();
        ei.label = "Label";
        ExtractedText et = getSampleExtractedText();
        doTestContentFormatting(ei, et, "Label: Hello world! [Execute]");
    }

    /** With a hint. */
    public void testContentFormattingHint() {
        EditorInfo ei = getSampleEditorInfo();
        ei.hintText = "Hint";
        ExtractedText et = getSampleExtractedText();
        doTestContentFormatting(ei, et, "Hint: Hello world! [Execute]");
    }

    /** With a label and a hint. The label should be preferred. */
    public void testContentFormattingLabelAndHint() {
        EditorInfo ei = getSampleEditorInfo();
        ei.label = "Label";
        ei.hintText = "Hint";
        ExtractedText et = getSampleExtractedText();
        doTestContentFormatting(ei, et, "Label: Hello world! [Execute]");
    }

    /** Selection is a cursor. */
    public void testSelectionCursor() {
        EditorInfo ei = getSampleEditorInfo();
        ei.imeOptions |= EditorInfo.IME_ACTION_NONE;
        ExtractedText et = getSampleExtractedText();
        et.selectionStart = 3;
        et.selectionEnd = 3;
        doTestContentSelection(ei, et, "Hello world!", 3, 3);
    }

    /** Selection is a region. */
    public void testSelectionRegion() {
        EditorInfo ei = getSampleEditorInfo();
        ei.imeOptions |= EditorInfo.IME_ACTION_NONE;
        ExtractedText et = getSampleExtractedText();
        et.selectionStart = 3;
        et.selectionEnd = 6;
        doTestContentSelection(ei, et, "Hello world!", 3, 6);
    }

    /** Tests that selection is correct, even considering the leading label. */
    public void testSelectionWithLabel() {
        EditorInfo ei = getSampleEditorInfo();
        ei.imeOptions |= EditorInfo.IME_ACTION_NONE;
        ei.label = "Label";
        ExtractedText et = getSampleExtractedText();
        et.selectionStart = 3;
        et.selectionEnd = 6;
        doTestContentSelection(ei, et, "Label: Hello world!", 10, 13);
    }

    /** Selection should change after onUpdateSelection. */
    public void testSelectionUpdate(){
        EditorInfo ei = getSampleEditorInfo();
        ei.imeOptions |= EditorInfo.IME_ACTION_NONE;
        ExtractedText et = getSampleExtractedText();
        et.selectionStart = 3;
        et.selectionEnd = 3;

        autoStub(mDisplayManager, mInputConnection, ei, et);
        createBindAndStart(ei);

        // Check selection, update and check again.
        verifyDisplayContentMatches("Hello world!", 3, 3);
        mIME.onUpdateSelection(3, 3, 3, 6, 0, 0);
        verifyDisplayContentMatches("Hello world!", 3, 6);

        finishUnbindAndDestroy();
    }

    /** A routing key press on the button should invoke the default action. */
    public void testRouteActionLabel() {
        EditorInfo ei = getSampleEditorInfo();
        ExtractedText et = getSampleExtractedText();
        ArgumentCaptor<DisplayManager.Content> content =
            ArgumentCaptor.forClass(DisplayManager.Content.class);

        autoStub(mDisplayManager, mFeedbackManager, mInputConnection, ei, et);
        doReturn(true).when(mIME).sendDefaultEditorAction(anyBoolean());
        createBindAndStart(ei);

        // Grab and verify the populated content.
        verify(mDisplayManager).setContent(content.capture());
        assertEquals("Hello world! [Execute]",
                content.getValue().getText().toString());

        // Send a routing event back.
        // Default action should be sent, with no feedback from the IME.
        Mockito.reset(mFeedbackManager);
        mIME.route(16, content.getValue());
        verify(mIME).sendDefaultEditorAction(anyBoolean());
        verify(mFeedbackManager, never()).emitFeedback(anyInt());
        verify(mFeedbackManager, never()).emitOnFailure(eq(false), anyInt());

        finishUnbindAndDestroy();
    }

    /**
     * A routing key press on the button should invoke a custom action, if
     * requested.
     */
    public void testRouteActionLabelCustom() {
        EditorInfo ei = getSampleEditorInfo();
        ei.actionId = 1337;
        ExtractedText et = getSampleExtractedText();
        ArgumentCaptor<DisplayManager.Content> content =
            ArgumentCaptor.forClass(DisplayManager.Content.class);

        autoStub(mDisplayManager, mFeedbackManager, mInputConnection, ei, et);
        when(mInputConnection.performEditorAction(1337)).thenReturn(true);
        createBindAndStart(ei);

        // Grab and verify the populated content.
        verify(mDisplayManager).setContent(content.capture());
        assertEquals("Hello world! [Execute]",
                content.getValue().getText().toString());

        // Send a routing event back.
        // Custom action should be sent, with no feedback from the IME.
        Mockito.reset(mFeedbackManager);
        mIME.route(16, content.getValue());
        verify(mInputConnection).performEditorAction(1337);
        verify(mFeedbackManager, never()).emitFeedback(anyInt());
        verify(mFeedbackManager, never()).emitOnFailure(eq(false), anyInt());

        finishUnbindAndDestroy();
    }

    /**
     * If the default action cannot be invoked, the IME should emit failure
     * feedback.
     */
    public void testRouteActionLabelFail() {
        EditorInfo ei = getSampleEditorInfo();
        ExtractedText et = getSampleExtractedText();
        ArgumentCaptor<DisplayManager.Content> content =
            ArgumentCaptor.forClass(DisplayManager.Content.class);

        autoStub(mDisplayManager, mFeedbackManager, mInputConnection, ei, et);
        doReturn(false).when(mIME).sendDefaultEditorAction(anyBoolean());
        createBindAndStart(ei);

        // Grab and verify the populated content.
        verify(mDisplayManager).setContent(content.capture());
        assertEquals("Hello world! [Execute]",
                content.getValue().getText().toString());

        // Send a routing event back.
        // Default action should be sent, and feedback should be emitted.
        Mockito.reset(mFeedbackManager);
        mIME.route(16, content.getValue());
        verify(mIME).sendDefaultEditorAction(anyBoolean());
        verify(mFeedbackManager).emitFeedback(
                FeedbackManager.TYPE_COMMAND_FAILED);

        finishUnbindAndDestroy();
    }

    /** If the routing key is within the text, the cursor should move. */
    public void testRouteMoveCursor() {
        EditorInfo ei = getSampleEditorInfo();
        ei.label = "Label";
        ExtractedText et = getSampleExtractedText();
        ArgumentCaptor<DisplayManager.Content> content =
            ArgumentCaptor.forClass(DisplayManager.Content.class);

        autoStub(mDisplayManager, mFeedbackManager, mInputConnection, ei, et);
        when(mInputConnection.setSelection(3, 3)).thenReturn(true);
        createBindAndStart(ei);

        // Grab and verify the populated content.
        verify(mDisplayManager).setContent(content.capture());
        assertEquals("Label: Hello world! [Execute]",
                content.getValue().getText().toString());

        // Send a routing event back.
        // The selection should change, and no feedback should be emitted.
        Mockito.reset(mFeedbackManager);
        mIME.route(10, content.getValue());
        verify(mFeedbackManager, never()).emitFeedback(anyInt());
        verify(mFeedbackManager, never()).emitOnFailure(eq(false), anyInt());

        finishUnbindAndDestroy();
    }

    /**
     * If the routing key is within the text, but moving fails, feedback
     * should be emitted.
     */
    public void testRouteMoveCursorFail() {
        EditorInfo ei = getSampleEditorInfo();
        ei.label = "Label";
        ExtractedText et = getSampleExtractedText();
        ArgumentCaptor<DisplayManager.Content> content =
            ArgumentCaptor.forClass(DisplayManager.Content.class);

        autoStub(mDisplayManager, mFeedbackManager, mInputConnection, ei, et);
        when(mInputConnection.setSelection(3, 3)).thenReturn(false);
        createBindAndStart(ei);

        // Grab and verify the populated content.
        verify(mDisplayManager).setContent(content.capture());
        assertEquals("Label: Hello world! [Execute]",
                content.getValue().getText().toString());

        // Send a routing event back.
        // The selection should change, and no feedback should be emitted.
        Mockito.reset(mFeedbackManager);
        mIME.route(10, content.getValue());
        verify(mFeedbackManager).emitFeedback(
                FeedbackManager.TYPE_COMMAND_FAILED);

        finishUnbindAndDestroy();
    }

    /**
     * If a routing key press occurs anywhere else, the IME should emit
     * feedback.
     */
    public void testRouteOutOfBounds() {
        EditorInfo ei = getSampleEditorInfo();
        ei.label = "Label";
        ExtractedText et = getSampleExtractedText();
        ArgumentCaptor<DisplayManager.Content> content =
            ArgumentCaptor.forClass(DisplayManager.Content.class);

        autoStub(mDisplayManager, mFeedbackManager, mInputConnection, ei, et);
        createBindAndStart(ei);

        // Grab and verify the populated content.
        verify(mDisplayManager).setContent(content.capture());
        assertEquals("Label: Hello world! [Execute]",
                content.getValue().getText().toString());

        // Send a routing event back.
        // The selection should change, and no feedback should be emitted.
        Mockito.reset(mFeedbackManager);
        mIME.route(1, content.getValue());
        verify(mFeedbackManager).emitFeedback(
                FeedbackManager.TYPE_NAVIGATE_OUT_OF_BOUNDS);

        finishUnbindAndDestroy();
    }

    /** Default action when no custom action is specified. */
    public void testSendDefaultAction() {
        EditorInfo ei = getSampleEditorInfo();
        ExtractedText et = getSampleExtractedText();

        autoStub(mFeedbackManager, mInputConnection, ei, et);
        doReturn(true).when(mIME).sendDefaultEditorAction(anyBoolean());
        createBindAndStart(ei);

        Mockito.reset(mFeedbackManager);
        assertTrue(mIME.sendDefaultAction());
        verify(mFeedbackManager, never()).emitFeedback(anyInt());
        verify(mFeedbackManager, never()).emitOnFailure(eq(false), anyInt());

        finishUnbindAndDestroy();
    }

    /** Default action when a custom action is specified. */
    public void testSendDefaultActionCustom() {
        EditorInfo ei = getSampleEditorInfo();
        ei.actionId = 1337;
        ExtractedText et = getSampleExtractedText();

        autoStub(mFeedbackManager, mInputConnection, ei, et);
        when(mInputConnection.performEditorAction(1337)).thenReturn(true);
        createBindAndStart(ei);

        Mockito.reset(mFeedbackManager);
        assertTrue(mIME.sendDefaultAction());
        verify(mFeedbackManager, never()).emitFeedback(anyInt());
        verify(mFeedbackManager, never()).emitOnFailure(eq(false), anyInt());

        finishUnbindAndDestroy();
    }

    /**
     * Tests that {@link #sendDefaultAction} returns false on failure.
     * At the moment, no feedback is emitted in this method.
     */
    public void testSendDefaultActionFail() {
        EditorInfo ei = getSampleEditorInfo();
        ExtractedText et = getSampleExtractedText();

        autoStub(mFeedbackManager, mInputConnection, ei, et);
        doReturn(false).when(mIME).sendDefaultEditorAction(anyBoolean());
        createBindAndStart(ei);

        Mockito.reset(mFeedbackManager);
        assertFalse(mIME.sendDefaultAction());
        verify(mFeedbackManager, never()).emitFeedback(anyInt());
        verify(mFeedbackManager, never()).emitOnFailure(eq(false), anyInt());

        finishUnbindAndDestroy();
    }

    /** Tests moving by character granularity. */
    public void testMoveCursorCharacterGranularity() {
        EditorInfo ei = getSampleEditorInfo();
        ExtractedText et = getSampleExtractedText();

        autoStub(mFeedbackManager, mInputConnection, ei, et);
        when(mInputConnection.sendKeyEvent(isA(KeyEvent.class)))
            .thenReturn(true);
        createBindAndStart(ei);
        Mockito.reset(mFeedbackManager);

        mIME.moveCursor(BrailleIME.DIRECTION_FORWARD,
                AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_CHARACTER);
        verify(mInputConnection).sendKeyEvent(keyEventMatches(
                    KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT));
        verify(mInputConnection).sendKeyEvent(keyEventMatches(
                    KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT));

        mIME.moveCursor(BrailleIME.DIRECTION_BACKWARD,
                AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_CHARACTER);
        verify(mInputConnection).sendKeyEvent(keyEventMatches(
                    KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT));
        verify(mInputConnection).sendKeyEvent(keyEventMatches(
                    KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT));

        verify(mFeedbackManager, never()).emitFeedback(anyInt());
        verify(mFeedbackManager, never()).emitOnFailure(eq(false), anyInt());
        finishUnbindAndDestroy();
    }

    /** Tests when moving by character granularity fails. */
    public void testMoveCursorCharacterGranularityFail() {
        EditorInfo ei = getSampleEditorInfo();
        ExtractedText et = getSampleExtractedText();

        autoStub(mFeedbackManager, mInputConnection, ei, et);
        when(mInputConnection.sendKeyEvent(isA(KeyEvent.class)))
            .thenReturn(false);
        createBindAndStart(ei);
        Mockito.reset(mFeedbackManager);

        mIME.moveCursor(BrailleIME.DIRECTION_FORWARD,
                AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_CHARACTER);
        verify(mInputConnection).sendKeyEvent(keyEventMatches(
                    KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT));
        verify(mFeedbackManager).emitFeedback(
                FeedbackManager.TYPE_COMMAND_FAILED);

        finishUnbindAndDestroy();
    }

    /** Tests moving by paragraph granularity. This needs multiline text. */
    public void testMoveCursorParagraphGranularity() {
        EditorInfo ei = getSampleEditorInfo();
        ei.inputType |= EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE;
        ExtractedText et = getSampleExtractedText();
        et.text = "Paragraph 1\nParagraph 2";
        et.selectionStart = 0;
        et.selectionEnd = 0;

        autoStub(mFeedbackManager, mInputConnection, ei, et);
        when(mInputConnection.setSelection(anyInt(), anyInt()))
            .thenReturn(true);
        createBindAndStart(ei);
        Mockito.reset(mFeedbackManager);

        mIME.moveCursor(BrailleIME.DIRECTION_FORWARD,
                AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PARAGRAPH);
        verify(mInputConnection).setSelection(12, 12);
        mIME.onUpdateSelection(0, 0, 12, 12, 0, 0);

        mIME.moveCursor(BrailleIME.DIRECTION_FORWARD,
                AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PARAGRAPH);
        verify(mInputConnection).setSelection(23, 23);
        mIME.onUpdateSelection(12, 12, 23, 23, 0, 0);

        mIME.moveCursor(BrailleIME.DIRECTION_BACKWARD,
                AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PARAGRAPH);
        verify(mInputConnection, times(2)).setSelection(12, 12);
        mIME.onUpdateSelection(23, 23, 12, 12, 0, 0);

        mIME.moveCursor(BrailleIME.DIRECTION_BACKWARD,
                AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PARAGRAPH);
        verify(mInputConnection).setSelection(0, 0);
        mIME.onUpdateSelection(12, 12, 0, 0, 0, 0);

        verify(mFeedbackManager, never()).emitFeedback(anyInt());
        verify(mFeedbackManager, never()).emitOnFailure(eq(false), anyInt());
        finishUnbindAndDestroy();
    }

    /** Tests sending an Android key code. */
    public void testSendAndroidKey() {
        EditorInfo ei = getSampleEditorInfo();
        ExtractedText et = getSampleExtractedText();

        autoStub(mFeedbackManager, mInputConnection, ei, et);
        when(mInputConnection.sendKeyEvent(isA(KeyEvent.class)))
            .thenReturn(true);
        createBindAndStart(ei);
        Mockito.reset(mFeedbackManager);

        mIME.sendAndroidKey(KeyEvent.KEYCODE_ENTER);
        verify(mInputConnection).sendKeyEvent(keyEventMatches(
                    KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
        verify(mInputConnection).sendKeyEvent(keyEventMatches(
                    KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));

        verify(mFeedbackManager, never()).emitFeedback(anyInt());
        verify(mFeedbackManager, never()).emitOnFailure(eq(false), anyInt());
        finishUnbindAndDestroy();
    }

    /** Tests feedback when sending an Android key code fails. */
    public void testSendAndroidKeyFail() {
        EditorInfo ei = getSampleEditorInfo();
        ExtractedText et = getSampleExtractedText();

        autoStub(mFeedbackManager, mInputConnection, ei, et);
        when(mInputConnection.sendKeyEvent(isA(KeyEvent.class)))
            .thenReturn(false);
        createBindAndStart(ei);
        Mockito.reset(mFeedbackManager);

        mIME.sendAndroidKey(KeyEvent.KEYCODE_ENTER);
        verify(mInputConnection).sendKeyEvent(keyEventMatches(
                    KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
        verify(mFeedbackManager).emitFeedback(
                FeedbackManager.TYPE_COMMAND_FAILED);

        finishUnbindAndDestroy();
    }

    /** Tests that text is committed when a Braille key is entered. */
    public void testHandleBrailleKey() {
        EditorInfo ei = getSampleEditorInfo();
        ExtractedText et = getSampleExtractedText();

        autoStub(mBrailleTranslator, mFeedbackManager, mInputConnection, ei,
                et);
        when(mBrailleTranslator.backTranslate(new byte[] {0x1b}))
            .thenReturn("g");
        when(mInputConnection.commitText("g", 1)).thenReturn(true);
        createBindAndStart(ei);
        Mockito.reset(mFeedbackManager);

        assertTrue(mIME.handleBrailleKey(0x1b));

        verify(mFeedbackManager, never()).emitFeedback(anyInt());
        verify(mFeedbackManager, never()).emitOnFailure(eq(false), anyInt());
        finishUnbindAndDestroy();
    }

    /** Tests feedback when committing a Braille key fails. */
    public void testHandleBrailleKeyFail() {
        EditorInfo ei = getSampleEditorInfo();
        ExtractedText et = getSampleExtractedText();

        autoStub(mBrailleTranslator, mFeedbackManager, mInputConnection, ei,
                et);
        when(mBrailleTranslator.backTranslate(new byte[] {0x1b}))
            .thenReturn("g");
        when(mInputConnection.commitText("g", 1)).thenReturn(false);
        createBindAndStart(ei);
        Mockito.reset(mFeedbackManager);

        assertTrue(mIME.handleBrailleKey(0x1b));
        verify(mFeedbackManager).emitFeedback(
                FeedbackManager.TYPE_COMMAND_FAILED);

        finishUnbindAndDestroy();
    }

    private EditorInfo getSampleEditorInfo() {
        return new EditorInfo();
    }

    private ExtractedText getSampleExtractedText() {
        ExtractedText et = new ExtractedText();
        et.flags = ExtractedText.FLAG_SINGLE_LINE;
        et.text = "Hello world!";
        et.startOffset = 0;
        et.partialStartOffset = -1;
        et.selectionStart = et.text.length();
        et.selectionEnd = et.text.length();
        return et;
    }

    private void doTestContentFormatting(EditorInfo ei, ExtractedText et,
            CharSequence expected) {
        autoStub(mDisplayManager, mInputConnection, ei, et);
        createBindAndStart(ei);
        verifyDisplayContentMatches(expected);
        finishUnbindAndDestroy();
    }

    private void doTestContentSelection(EditorInfo ei, ExtractedText et,
            CharSequence expected, int selectionStart, int selectionEnd) {
        autoStub(mDisplayManager, mInputConnection, ei, et);
        createBindAndStart(ei);
        verifyDisplayContentMatches(expected, selectionStart, selectionEnd);
        finishUnbindAndDestroy();
    }

    /**
     * Injects objects into their usual places in the mocks and stubs.
     * Simplifies boilerplate stubbing.
     */
    private void autoStub(Object... objects) {
        for (Object o : objects) {
            if (o == mBrailleTranslator) {
                when(mHost.getBrailleTranslator())
                    .thenReturn(mBrailleTranslator);
            } else if (o == mDisplayManager) {
                when(mHost.getDisplayManager()).thenReturn(mDisplayManager);
            } else if (o == mFeedbackManager) {
                when(mHost.getFeedbackManager()).thenReturn(mFeedbackManager);
            } else if (o == mInputConnection) {
                when(mIME.getCurrentInputConnection())
                    .thenReturn(mInputConnection);
            } else if (o instanceof EditorInfo) {
                when(mIME.getCurrentInputEditorInfo())
                    .thenReturn((EditorInfo) o);
            } else if (o instanceof ExtractedText) {
                when(mInputConnection.getExtractedText(
                        isA(ExtractedTextRequest.class), anyInt()))
                    .thenReturn((ExtractedText) o);
            } else {
                throw new UnsupportedOperationException(
                        "can't auto-stub " + o.toString());
            }
        }
    }

    private void createBindAndStart(EditorInfo ei) {
        mIME.onCreate();
        mIME.onBindInput();
        mIME.onStartInput(ei, false /* restarting */);
    }

    private void finishUnbindAndDestroy() {
        mIME.onFinishInput();
        mIME.onUnbindInput();
        mIME.onDestroy();
    }

    private void verifyDisplayContentMatches(CharSequence expected) {
        verify(mDisplayManager).setContent(displayContentMatches(expected));
    }

    private void verifyDisplayContentMatches(CharSequence expected,
            int selectionStart, int selectionEnd) {
        verify(mDisplayManager).setContent(
                displayContentMatches(expected, selectionStart, selectionEnd));
    }

    private DisplayManager.Content displayContentMatches(CharSequence text) {
        return Mockito.argThat(new DisplayContentMatches(text));
    }

    private DisplayManager.Content displayContentMatches(CharSequence text,
            int selectionStart, int selectionEnd) {
        return Mockito.argThat(
                new DisplayContentMatches(text, selectionStart, selectionEnd));
    }

    private KeyEvent keyEventMatches(int action, int code) {
        return Mockito.argThat(new KeyEventMatches(action, code));
    }

    private static class DisplayContentMatches
            extends ArgumentMatcher<DisplayManager.Content> {
        private final String mString;
        private final boolean mChecksSelection;
        private final int mSelectionStart;
        private final int mSelectionEnd;

        public DisplayContentMatches(CharSequence text) {
            mString = text.toString();
            mChecksSelection = false;
            mSelectionStart = mSelectionEnd = -1;
        }

        public DisplayContentMatches(CharSequence text,
                int selectionStart, int selectionEnd) {
            mString = text.toString();
            mChecksSelection = true;
            mSelectionStart = selectionStart;
            mSelectionEnd = selectionEnd;
        }

        @Override
        public boolean matches(Object argument) {
            if (!(argument instanceof DisplayManager.Content)) {
                return false;
            }
            DisplayManager.Content content = (DisplayManager.Content) argument;
            if (!mString.equals(content.getText().toString())) {
                return false;
            }
            if (mChecksSelection && content.getText() instanceof Spanned) {
                Spanned spanned = (Spanned) content.getText();
                DisplaySpans.SelectionSpan[] spans = spanned.getSpans(
                        0, spanned.length(), DisplaySpans.SelectionSpan.class);
                if (spans.length != 1) {
                    return false;
                }
                int selectionStart = spanned.getSpanStart(spans[0]);
                int selectionEnd = spanned.getSpanEnd(spans[0]);
                if (selectionStart != mSelectionStart
                        || selectionEnd != mSelectionEnd) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText(String.format(
                        "display content matching \"%s\"", mString));
            if (mChecksSelection) {
                description.appendText(String.format(" with selection %d-%d",
                            mSelectionStart, mSelectionEnd));
            }
        }
    }

    private static class KeyEventMatches extends ArgumentMatcher<KeyEvent> {
        private final int mAction;
        private final int mKeyCode;

        public KeyEventMatches(int action, int keyCode) {
            mAction = action;
            mKeyCode = keyCode;
        }

        @Override
        public boolean matches(Object argument) {
            if (!(argument instanceof KeyEvent)) {
                return false;
            }
            KeyEvent keyEvent = (KeyEvent) argument;
            return keyEvent.getAction() == mAction
                && keyEvent.getKeyCode() == mKeyCode;
        }
    }
}

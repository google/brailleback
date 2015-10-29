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

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.reset;

import com.googlecode.eyesfree.braille.display.BrailleInputEvent;
import com.googlecode.eyesfree.brailleback.tests.R;
import com.googlecode.eyesfree.testing.AccessibilityInstrumentationTestCase;

import android.content.Context;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Log;

import android.view.accessibility.AccessibilityNodeInfo;
import android.accessibilityservice.AccessibilityService;
import android.view.LayoutInflater;
import android.view.View;

import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/**
 * Tests for the SearchNavigationMode.
 */
@MediumTest
public class SearchNavigationModeTest extends AccessibilityInstrumentationTestCase {

    private static final DisplayManager.Content EMPTY_CONTENT =
            new DisplayManager.Content("");

    private SearchNavigationMode mSearchNavMode;
    @Mock private DisplayManager mDisplayManager;
    @Mock private FeedbackManager mFeedbackManager;
    @Mock private SelfBrailleManager mSelfBrailleManager;
    @Mock private TranslatorManager mTranslatorManager;
    @Mock private NodeBrailler mNodeBrailler;
    @Mock private SearchOverlay mSearchOverlay;

    private boolean mSearchActive;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        MockitoAnnotations.initMocks(this);
        mSearchNavMode = new SearchNavigationMode(
            mDisplayManager,
            getService(),
            mFeedbackManager,
            mTranslatorManager,
            mSelfBrailleManager,
            mNodeBrailler,
            new SearchNavigationMode.SearchStateListener() {
                public void onSearchStarted()
                {
                    mSearchActive = true;
                }
                public void onSearchFinished()
                {
                    mSearchActive = false;
                }
            },
            mSearchOverlay);

        setContentView(R.layout.search_mode_tree);
    }

    public void testActivateDeactivate() throws Exception {
        mSearchNavMode.onActivate();
        assertTrue(mSearchActive);
        verify(mSearchOverlay).show();

        mSearchNavMode.onDeactivate();
        // Can't verify mSearchActive is false since onDeactivate doesn't
        // call the listener.
        verify(mSearchOverlay).hide();
    }

    public void testNextPreviousResult() throws Exception {
        mSearchNavMode.onActivate();

        AccessibilityNodeInfoCompat root = getNodeForId(R.id.top);
        assertNotNull(root);

        // Test next result
        mSearchNavMode.setQueryTextForTest("xy");

        sendInputEvent(BrailleInputEvent.CMD_NAV_ITEM_NEXT);
        assertFocusedNodeText("xyy", root);

        // Test again.
        sendInputEvent(BrailleInputEvent.CMD_NAV_ITEM_NEXT);
        assertFocusedNodeText("aabxyz", root);

        // Test that when no more results nothing changes.
        sendInputEvent(BrailleInputEvent.CMD_NAV_ITEM_NEXT);
        assertFocusedNodeText("aabxyz", root);
        verify(mFeedbackManager).emitOnFailure(false,
                FeedbackManager.TYPE_NAVIGATE_OUT_OF_BOUNDS);

        reset(mFeedbackManager);

        // Test previous.
        sendInputEvent(BrailleInputEvent.CMD_NAV_ITEM_PREVIOUS);
        assertFocusedNodeText("xyy", root);

        // Test that no more previous results doesn't change focus.
        sendInputEvent(BrailleInputEvent.CMD_NAV_ITEM_PREVIOUS);
        assertFocusedNodeText("xyy", root);
        verify(mFeedbackManager).emitOnFailure(false,
                FeedbackManager.TYPE_NAVIGATE_OUT_OF_BOUNDS);

        // Test that focus stays when deactivate with enter key.
        sendInputEvent(BrailleInputEvent.CMD_KEY_ENTER);
        assertFalse(mSearchActive);
        assertFocusedNodeText("xyy", root);
    }

    public void testClearQuery() throws Exception {
        mSearchNavMode.onActivate();

        AccessibilityNodeInfoCompat root = getNodeForId(R.id.top);
        assertNotNull(root);

        mSearchNavMode.setQueryTextForTest("xy");

        // Deactivate and reactivate and make sure query text has cleared.
        mSearchNavMode.onDeactivate();
        mSearchNavMode.onActivate();
        assertEquals("", mSearchNavMode.getQueryTextForTest());
    }

    public void testDeleteKey() throws Exception {
        // Set initial focus.
        AccessibilityNodeInfoCompat initial = getNodeForId(R.id.button1);
        initial.performAction(
                AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS);

        mSearchNavMode.setInitialNodeToCurrent();
        mSearchNavMode.onActivate();

        AccessibilityNodeInfoCompat root = getNodeForId(R.id.top);
        assertNotNull(root);

        // Search and focus a result.
        mSearchNavMode.setQueryTextForTest("xy");
        sendInputEvent(BrailleInputEvent.CMD_NAV_ITEM_NEXT);
        assertFocusedNodeText("xyy", root);

        // Test delete.
        sendInputEvent(BrailleInputEvent.CMD_KEY_DEL);
        assertEquals("x", mSearchNavMode.getQueryTextForTest());
        assertFocusedNodeText("xyy", root);

        // Test a second delete.
        sendInputEvent(BrailleInputEvent.CMD_KEY_DEL);
        assertEquals("", mSearchNavMode.getQueryTextForTest());
        assertFocusedNodeText("xyy", root);

        // Test a third delete. Since the query is empty now, this should escape
        // and bring focus back to the initial node.
        sendInputEvent(BrailleInputEvent.CMD_KEY_DEL);
        assertFalse(mSearchActive);
        AccessibilityNodeInfoCompat focused = getFocusedNode(root);
        assertEquals(initial, focused);
    }

    public void testToggleOff() throws Exception {
        // Set initial focus.
        AccessibilityNodeInfoCompat initial = getNodeForId(R.id.button1);
        assertTrue(initial.performAction(
                AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS));

        mSearchNavMode.setInitialNodeToCurrent();
        mSearchNavMode.onActivate();

        AccessibilityNodeInfoCompat root = getNodeForId(R.id.top);
        assertNotNull(root);

        // Search for a node.
        mSearchNavMode.setQueryTextForTest("xy");
        sendInputEvent(BrailleInputEvent.CMD_NAV_ITEM_NEXT);
        assertFocusedNodeText("xyy", root);

        // Test toggling off. Should reset focus back to the initial.
        sendInputEvent(BrailleInputEvent.CMD_TOGGLE_INCREMENTAL_SEARCH);
        assertFalse(mSearchActive);
        AccessibilityNodeInfoCompat focused = getFocusedNode(root);
        assertEquals(initial, focused);
    }

    /**
     * Sends the specified input type to mSearchNavigationMode.
     */
    private void sendInputEvent(int command) {
        BrailleInputEvent inputEvent = new BrailleInputEvent(command, 0, 0);
        mSearchNavMode.onMappedInputEvent(inputEvent, EMPTY_CONTENT);
    }

    private void assertFocusedNodeText(String text,
            AccessibilityNodeInfoCompat root) {
        AccessibilityNodeInfoCompat focused = getFocusedNode(root);
        assertNotNull(focused);
        assertEquals(text, focused.getText());
    }

    /**
     * Returns the focused node or the specified root if none found.
     */
    private AccessibilityNodeInfoCompat getFocusedNode(
        AccessibilityNodeInfoCompat root) {
        AccessibilityNodeInfoCompat focused = null;
        if (root != null) {
            focused = root.findFocus(
                AccessibilityNodeInfoCompat.FOCUS_ACCESSIBILITY);
            if (focused != null && focused.isVisibleToUser()) {
                return focused;
            }
            return root;

        }
        return null;
    }
}

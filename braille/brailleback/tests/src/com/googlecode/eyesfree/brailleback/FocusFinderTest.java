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

import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;

import android.util.Log;

import android.view.LayoutInflater;
import android.view.View;

import com.googlecode.eyesfree.brailleback.tests.R;

import com.googlecode.eyesfree.testing.AccessibilityInstrumentationTestCase;

/**
 * Tests for the FocusFinder
 */
public class FocusFinderTest extends AccessibilityInstrumentationTestCase {

    public void testFindLastFocusableDescendant() throws Exception {
        // Try simple layout.
    	setContentView(R.layout.simple_tree);
        AccessibilityNodeInfoCompat rootNode = getNodeForId(R.id.top);

        assertNotNull(rootNode);
        assertTrue(rootNode.getChildCount() > 0);

        AccessibilityNodeInfoCompat lastNode =
                FocusFinder.findLastFocusableDescendant(rootNode, getActivity());

        assertNotNull(lastNode);
        assertEquals("button4", lastNode.getContentDescription());

        // Try more complicated layout, with more levels and unfocusable items.
        setContentView(R.layout.complex_tree);
        rootNode = getNodeForId(R.id.top);

        assertNotNull(rootNode);
        assertTrue(rootNode.getChildCount() > 0);

        lastNode = FocusFinder.findLastFocusableDescendant(rootNode,
            getActivity());

        assertNotNull(lastNode);
        assertEquals("button4", lastNode.getContentDescription());

        // Test when only the root is focusable.
        setContentView(R.layout.no_focusable_nodes);
        rootNode = getNodeForId(R.id.top);

        assertNotNull(rootNode);

        lastNode = FocusFinder.findLastFocusableDescendant(rootNode,
            getActivity());

        assertNull(lastNode);
    }
}
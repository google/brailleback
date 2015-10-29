/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.googlecode.eyesfree.utils;

import android.annotation.TargetApi;
import android.content.Context;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.googlecode.eyesfree.testing.AccessibilityInstrumentationTestCase;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;
import com.googlecode.eyesfree.utils.test.R;

/**
 * Tests for {@link AccessibilityNodeInfoUtils} that rely on activity
 * instrumentation.
 * <p>
 * Running the tests in a full-blown activity is necessary to obtain correct and
 * functional {@link AccessibilityNodeInfoCompat}s from {@link View}s.
 */
@TargetApi(14)
public class AccessibilityNodeInfoUtilsTest extends AccessibilityInstrumentationTestCase {
    public void testInstrumentation() {
        setContentView(R.layout.non_speaking_container);

        final AccessibilityNodeInfoCompat container = getNodeForId(R.id.container);
        assertNotNull("Obtained container", container);

        final AccessibilityNodeInfoCompat textView = getNodeForId(R.id.textView);
        assertNotNull("Obtained text view", textView);

        final AccessibilityNodeInfoCompat parent = textView.getParent();
        assertNotNull("Text view has a parent node", parent);

        final AccessibilityNodeInfoCompat child = container.getChild(0);
        assertNotNull("Container has a child node", child);

        assertEquals("Container is text view's parent", container, parent);
        assertEquals("Text view is container's child", textView, child);

        AccessibilityNodeInfoUtils.recycleNodes(container, textView, parent, child);
    }

    public void testShouldFocusNode() throws Exception {
        setContentView(R.layout.non_speaking_container);

        assertShouldFocusNode("Container is not focusable", R.id.container, false);
        assertShouldFocusNode("Text is focusable", R.id.textView, true);
        assertShouldFocusNode("Button is focusable", R.id.button, true);
    }

    public void testListWithSingleItem() throws Throwable {
        setContentView(R.layout.list_with_single_item);

        final Context context = getActivity();
        final ListView list = (ListView) getViewForId(R.id.list);

        // Add an adapter with a single item.
        final ArrayAdapter<String> adapter =
                new ArrayAdapter<String>(context, android.R.layout.simple_list_item_1);
        adapter.add(context.getString(R.string.single_list_item));

        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                list.setAdapter(adapter);
            }
        });

        waitForAccessibilityIdleSync();

        final View firstItem = list.getChildAt(0);
        assertNotNull("Obtain first item of " + list.getChildCount(), firstItem);

        assertShouldFocusNode("List is not focusable", R.id.list, false);
        assertShouldFocusNode("First item is focusable", firstItem, true);
    }

    public void testListWithHeaderOnly() throws Throwable {
        setContentView(R.layout.list_with_single_item);

        final Context context = getActivity();
        final ListView list = (ListView) getViewForId(R.id.list);

        // Add a single list header.
        final LayoutInflater inflater = LayoutInflater.from(context);
        final View headerView = inflater.inflate(R.layout.list_header_row, list, false);

        // Add an adapter with no items.
        final ArrayAdapter<String> adapter =
                new ArrayAdapter<String>(context, android.R.layout.simple_list_item_1);

        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                list.addHeaderView(headerView, context.getString(R.string.list_header), false);
                list.setAdapter(adapter);
            }
        });

        waitForAccessibilityIdleSync();

        assertShouldFocusNode("Non-selectable header is focusable", R.id.header_item, true);
        assertShouldFocusNode("List is not focusable", R.id.list, false);

        assertFocusFromHover("Non-selectable header receives focus",
                R.id.header_item, R.id.header_item);
        assertFocusFromHover("List does not receive focus", R.id.list, -1);
    }

    public void testListWithNonSelectableHeader() throws Throwable {
        setContentView(R.layout.list_with_single_item);

        final Context context = getActivity();
        final ListView list = (ListView) getViewForId(R.id.list);

        // Add a single list header.
        final LayoutInflater inflater = LayoutInflater.from(context);
        final View headerView = inflater.inflate(R.layout.list_header_row, list, false);

        // Add an adapter with two items.
        final ArrayAdapter<String> adapter =
                new ArrayAdapter<String>(context, android.R.layout.simple_list_item_1);
        adapter.add(context.getString(R.string.first_list_item));
        adapter.add(context.getString(R.string.second_list_item));

        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                list.addHeaderView(headerView, mInsCtx.getString(R.string.list_header), false);
                list.setAdapter(adapter);
            }
        });

        waitForAccessibilityIdleSync();

        final View firstItem = list.getChildAt(1);
        final View secondItem = list.getChildAt(2);

        assertNotNull("Obtain first item", firstItem);
        assertNotNull("Obtain second item", secondItem);

        assertShouldFocusNode("Non-selectable header is focusable", R.id.header_item, true);
        assertShouldFocusNode("First item is focusable", firstItem, true);
        assertShouldFocusNode("Second item is focusable", secondItem, true);
        assertShouldFocusNode("List is not focusable", R.id.list, false);

        assertFocusFromHover("Non-selectable header receives focus",
                R.id.header_item, R.id.header_item);
        assertFocusFromHover("First item receives focus", firstItem, firstItem);
        assertFocusFromHover("Second item receives focus", secondItem, secondItem);
        assertFocusFromHover("List does not receive focus", R.id.list, -1);
    }

    public void testFindFocusFromHover() throws Throwable {
        setContentView(R.layout.non_speaking_container);

        assertFocusFromHover("Container does not receive focus", R.id.container, -1);
        assertFocusFromHover("Text receives focus", R.id.textView, R.id.textView);
        assertFocusFromHover("Button receives focus", R.id.button, R.id.button);
    }

    public void testRefreshNodeNull() {
        assertNull("Refreshing a null node should return null",
                AccessibilityNodeInfoUtils.refreshNode(null));
    }

    public void testRefreshNodeLeafNode() throws Throwable{
        final String someOtherText = "someothertext";
        setContentView(R.layout.non_speaking_container);
        final AccessibilityNodeInfoCompat textViewNode =
                getNodeForId(R.id.textView);
        final TextView textView = (TextView) getViewForId(R.id.textView);
        assertEquals("Text view text doesn't match",
                getActivity().getString(R.string.dummy_text),
                textViewNode.getText());
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                textView.setText(someOtherText);
            }
        });
        waitForAccessibilityIdleSync();
        final AccessibilityNodeInfoCompat refreshedNode =
                AccessibilityNodeInfoUtils.refreshNode(textViewNode);
        assertEquals("Refreshed node should keep its identity",
                textViewNode, refreshedNode);
        assertEquals("Refreshed node should have new text",
                someOtherText,
                refreshedNode.getText());
    }

    public void testRefreshNodeContainerNode() throws Throwable {
        final String description = "Nice view";
        setContentView(R.layout.non_speaking_container);
        final AccessibilityNodeInfoCompat containerNode =
                getNodeForId(R.id.container);
        final View containerView = getViewForId(R.id.container);
        assertNull("Container should have no content description",
                containerNode.getContentDescription());
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                containerView.setContentDescription(description);
            }
        });
        waitForAccessibilityIdleSync();
        final AccessibilityNodeInfoCompat refreshedNode =
                AccessibilityNodeInfoUtils.refreshNode(containerNode);
        assertEquals("Refreshed node should keep its identity",
                containerNode, refreshedNode);
        assertEquals("Refreshed node should have new content description",
                description,
                refreshedNode.getContentDescription());
    }

    /**
     * Assert that passing the node for the {@link View} with id {@code viewId}
     * to {@link AccessibilityNodeInfoUtils#shouldFocusNode} returns
     * {@code expectedValue}.
     */
    private void assertShouldFocusNode(String message, int viewId, boolean expectedValue) {
        final AccessibilityNodeInfoCompat node = getNodeForId(viewId);
        assertNotNull("Obtain node from view ID", node);
        assertShouldFocusNodeInternal(message, node, expectedValue);
        AccessibilityNodeInfoUtils.recycleNodes(node);
    }

    /**
     * Assert that passing the node for {@code view} to
     * {@link AccessibilityNodeInfoUtils#shouldFocusNode} returns
     * {@code expectedValue}.
     */
    private void assertShouldFocusNode(String message, View view, boolean expectedValue) {
        final AccessibilityNodeInfoCompat node = getNodeForView(view);
        assertNotNull("Obtain node from view", node);
        assertShouldFocusNodeInternal(message, node, expectedValue);
        AccessibilityNodeInfoUtils.recycleNodes(node);
    }

    /**
     * Assert that passing the node for the {@link View} with id {@code viewId}
     * to {@link AccessibilityNodeInfoUtils#findFocusFromHover} returns the node
     * for the {@link View} with id {@code expectedId}.
     */
    private void assertFocusFromHover(String message, int viewId, int expectedId) {
        final AccessibilityNodeInfoCompat node = getNodeForId(viewId);
        assertNotNull("Obtain node from view ID", node);
        final AccessibilityNodeInfoCompat expectedNode = getNodeForId(expectedId);
        assertFocusFromHoverInternal(message, node, expectedNode);
        AccessibilityNodeInfoUtils.recycleNodes(node, expectedNode);
    }

    /**
     * Assert that passing the node for {@code view} to
     * {@link AccessibilityNodeInfoUtils#findFocusFromHover} returns the node
     * for {@code expectedView}.
     */
    private void assertFocusFromHover(String message, View view, View expectedView) {
        final AccessibilityNodeInfoCompat node = getNodeForView(view);
        assertNotNull("Obtain node from view", node);
        final AccessibilityNodeInfoCompat expectedNode = getNodeForView(expectedView);
        assertFocusFromHoverInternal(message, node, expectedNode);
        AccessibilityNodeInfoUtils.recycleNodes(node, expectedNode);
    }

    private void assertFocusFromHoverInternal(
            String message, final AccessibilityNodeInfoCompat node,
            final AccessibilityNodeInfoCompat expectedNode) {
        final AccessibilityNodeInfoCompat actualNode =
                AccessibilityNodeInfoUtils.findFocusFromHover(mInsCtx, node);
        assertEquals(message, expectedNode, actualNode);
        AccessibilityNodeInfoUtils.recycleNodes(actualNode);
    }

    private void assertShouldFocusNodeInternal(
            String message, AccessibilityNodeInfoCompat node, boolean expectedValue) {
        final boolean actualValue = AccessibilityNodeInfoUtils.shouldFocusNode(mInsCtx, node);
        assertEquals(message, expectedValue, actualValue);
    }
}

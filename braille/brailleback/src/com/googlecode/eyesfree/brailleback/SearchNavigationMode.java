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

import android.accessibilityservice.AccessibilityService;
import android.os.Bundle;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.googlecode.eyesfree.braille.display.BrailleInputEvent;
import com.googlecode.eyesfree.braille.translate.BrailleTranslator;
import com.googlecode.eyesfree.brailleback.rule.BrailleRuleRepository;
import com.googlecode.eyesfree.brailleback.utils.AccessibilityEventUtils;
import com.googlecode.eyesfree.brailleback.utils.LabelingUtils;
import com.googlecode.eyesfree.labeling.CustomLabelManager;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoRef;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;
import com.googlecode.eyesfree.utils.LogUtils;
import com.googlecode.eyesfree.utils.NodeFocusFinder;
import java.util.List;

/**
 * Navigation mode that is based on incremental search.
 * Used as a separate navigation mode in the BrailleBackService.
 */
class SearchNavigationMode implements NavigationMode {
    /**
     * Listener for when search has started or finished.
     */
    public static interface SearchStateListener {
        public void onSearchStarted();
        public void onSearchFinished();
    }

    private final DisplayManager mDisplayManager;
    private final AccessibilityService mAccessibilityService;
    private final FeedbackManager mFeedbackManager;
    private final TranslatorManager mTranslatorManager;
    private final SelfBrailleManager mSelfBrailleManager;
    private final NodeBrailler mNodeBrailler;
    private final SearchStateListener mSearchStateListener;
    private final StringBuilder mQueryText = new StringBuilder();
    private final SearchOverlay mSearchOverlay;
    private final CustomLabelManager mLabelManager;
    private boolean mActive = false;

    private final AccessibilityNodeInfoRef mInitialNode =
            new AccessibilityNodeInfoRef();

    // Track node we last matched since it may not be the same as
    // the currently focused node.
    private final AccessibilityNodeInfoRef mMatchedNode =
            new AccessibilityNodeInfoRef();

    public SearchNavigationMode(
            DisplayManager displayManager,
            AccessibilityService accessibilityService,
            FeedbackManager feedbackManager,
            TranslatorManager translatorManager,
            SelfBrailleManager selfBrailleManager,
            NodeBrailler nodeBrailler,
            SearchStateListener searchStateListener,
            CustomLabelManager labelManager) {
        mDisplayManager = displayManager;
        mAccessibilityService = accessibilityService;
        mFeedbackManager = feedbackManager;
        mTranslatorManager = translatorManager;
        mSelfBrailleManager = selfBrailleManager;
        mNodeBrailler = nodeBrailler;
        mSearchStateListener = searchStateListener;
        mLabelManager = labelManager;
        mSearchOverlay = new SearchOverlay(mAccessibilityService, mQueryText);
    }

    @Override
    public void onActivate() {
        mActive = true;

        // In case the initial node is no longer focused (such as exiting the
        // tutorial), we want to try to focus it here.
        if (!AccessibilityNodeInfoRef.isNull(mInitialNode)) {
            // Intentionally ignoring return value.
            mInitialNode.get().performAction(
                    AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
        }

        // Save the currently focused node.
        mSearchStateListener.onSearchStarted();

        // Update display.
        brailleMatchedOrFocusedNode();
        mSearchOverlay.show();
    }

    @Override
    public void onDeactivate() {
        mActive = false;
        mInitialNode.clear();
        mMatchedNode.clear();
        mQueryText.setLength(0);
        mSearchOverlay.hide();
    }

    /**
     * Intended to be called before {@link onActivate} so that an initial node
     * is set and can be re-focused if search mode is escaped.
     */
    public void setInitialNodeToCurrent() {
        AccessibilityNodeInfoCompat focused = FocusFinder.getFocusedNode(
                mAccessibilityService, false);
        mInitialNode.reset(focused);
    }

    public boolean isActive() {
        return mActive;
    }

    @Override
    public void onObserveAccessibilityEvent(AccessibilityEvent event) {
        // Nothing to do here.
    }

    @Override
    public boolean onAccessibilityEvent(AccessibilityEvent event) {
        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED:
                DisplayManager.Content content = formatEventToBraille(event);
                if (content != null) {
                    mDisplayManager.setContent(content);
                }
                return true;
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                finishSearch();
                // Let it fall through so other navigation mode can
                // receive the window_state_changed event.
                return false;
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                // This will re-evaluate the search and refocus if necessary.
                mMatchedNode.reset(AccessibilityNodeInfoUtils.refreshNode(
                        mMatchedNode.get()));
                evaluateSearch();
                return true;
        }
        // Don't let fall through.
        return true;
    }

    @Override
    public void onInvalidateAccessibilityNode(
            AccessibilityNodeInfoCompat node) {
        mMatchedNode.reset(AccessibilityNodeInfoUtils.refreshNode(
                mMatchedNode.get()));
        evaluateSearch();
    }

    @Override
    public boolean onPanLeftOverflow(DisplayManager.Content content) {
        // When we overflow, want to move to the previous result, if it exists.
        return mFeedbackManager.emitOnFailure(
            nextResult(NodeFocusFinder.SEARCH_BACKWARD),
            FeedbackManager.TYPE_NAVIGATE_OUT_OF_BOUNDS);
    }

    @Override
    public boolean onPanRightOverflow(DisplayManager.Content content) {
        // When we overflow, want to move to the next result, if it exists.
        return mFeedbackManager.emitOnFailure(
            nextResult(NodeFocusFinder.SEARCH_FORWARD),
            FeedbackManager.TYPE_NAVIGATE_OUT_OF_BOUNDS);
    }

    @Override
    public boolean onMappedInputEvent(BrailleInputEvent event,
            DisplayManager.Content content) {
        switch (event.getCommand()) {
            case BrailleInputEvent.CMD_TOGGLE_INCREMENTAL_SEARCH:
                syncBackToInitial();
                finishSearch();
                return true;
            case BrailleInputEvent.CMD_NAV_ITEM_NEXT:
            case BrailleInputEvent.CMD_NAV_LINE_NEXT:
                return mFeedbackManager.emitOnFailure(
                    nextResult(NodeFocusFinder.SEARCH_FORWARD),
                    FeedbackManager.TYPE_NAVIGATE_OUT_OF_BOUNDS);
            case BrailleInputEvent.CMD_NAV_ITEM_PREVIOUS:
            case BrailleInputEvent.CMD_NAV_LINE_PREVIOUS:
                return mFeedbackManager.emitOnFailure(
                    nextResult(NodeFocusFinder.SEARCH_BACKWARD),
                    FeedbackManager.TYPE_NAVIGATE_OUT_OF_BOUNDS);
            case BrailleInputEvent.CMD_KEY_DEL:
                return handleDelete();
            case BrailleInputEvent.CMD_ACTIVATE_CURRENT:
            case BrailleInputEvent.CMD_KEY_ENTER:
                finishSearch();
                return true;
            case BrailleInputEvent.CMD_BRAILLE_KEY:
                return handleBrailleKey(event.getArgument());
            default:
                // Don't let other commands fall through.
                return true;
        }
    }

    /**
     * Syncs accessibility focus back to the node focused when search mode
     * first activated.
     */
    private void syncBackToInitial() {
        AccessibilityNodeInfoCompat focused = FocusFinder.getFocusedNode(
                        mAccessibilityService, false);
        if (focused == null) {
            return;
        }
        try {
            mInitialNode.reset(AccessibilityNodeInfoUtils.refreshNode(
                        mInitialNode.get()));
            if (!AccessibilityNodeInfoRef.isNull(mInitialNode)) {
                if (mInitialNode.get().isAccessibilityFocused()) {
                    return;
                }
                mFeedbackManager.emitOnFailure(
                    mInitialNode.get().performAction(
                        AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS),
                        FeedbackManager.TYPE_COMMAND_FAILED);
            } else {
                mFeedbackManager.emitOnFailure(
                    focused.performAction(AccessibilityNodeInfoCompat.
                            ACTION_CLEAR_ACCESSIBILITY_FOCUS),
                    FeedbackManager.TYPE_COMMAND_FAILED);
            }
        } finally {
            focused.recycle();
        }
    }

    /**
     * Calls the listener.
     * Leaves accessibility focus where it is (on the result).
     */
    private void finishSearch() {
        mSearchStateListener.onSearchFinished();
    }

    /**
     * Translates braille dots into a character sequence and appends it
     * to the current query text.
     */
    private boolean handleBrailleKey(int dots) {
        // TODO: handle non-computer braille.
        BrailleTranslator translator =
                mTranslatorManager.getUncontractedTranslator();

        if (translator == null) {
            LogUtils.log(this, Log.WARN, "missing translator %s",
                    translator);
            mFeedbackManager.emitFeedback(FeedbackManager.TYPE_COMMAND_FAILED);
            return true;
        }

        CharSequence text = translator.backTranslate(
            new byte[] {(byte) dots});
        if (!TextUtils.isEmpty(text)) {
            return mFeedbackManager.emitOnFailure(tryAddQueryText(text),
                FeedbackManager.TYPE_NAVIGATE_OUT_OF_BOUNDS);
        }

        return true;
    }

    private boolean tryAddQueryText(CharSequence newText) {
        int initLength = mQueryText.length();
        mQueryText.append(newText);
        if (evaluateSearch()) {
            mSearchOverlay.refreshOverlay();
            return true;
        }
        // Search failed, go back to old text.
        mQueryText.delete(initLength, mQueryText.length());
        return false;
    }

    /**
     * Evaluates the search with the current query and returns whether a match
     * was found searching forward. Tries to match with last matched item.
     */
    private boolean evaluateSearch() {
        // First check if current selected result still matches.
        if (nodeMatchesQuery(mMatchedNode.get())) {
            // Make sure cursor is updated if search text changed.
            brailleMatchedOrFocusedNode();
            return true;
        }
        return nextResult(NodeFocusFinder.SEARCH_FORWARD);
    }

    /**
     * Searches for the next result matching the current search query in the
     * specified direction. Ordering of results taken from linear navigation.
     * Returns whether there is another result in that direction.
     */
    private boolean nextResult(int direction) {
        AccessibilityNodeInfoRef next = new AccessibilityNodeInfoRef();
        next.reset(NodeFocusFinder.focusSearch(
                getCurrentNode(), direction));
        AccessibilityNodeInfoCompat focusableNext = null;
        try {
            while (next.get() != null) {
                if (nodeMatchesQuery(next.get())) {
                    // Even if the text matches, we need to make sure the node
                    // should be focused or has a parent that should be focused.
                    focusableNext =
                            AccessibilityNodeInfoUtils.findFocusFromHover(
                                mAccessibilityService, next.get());

                    // Only count this as a match if it doesn't lead to the same
                    // parent.
                    if (focusableNext != null &&
                        !focusableNext.isAccessibilityFocused()) {
                        break;
                    }
                }
                next.reset(NodeFocusFinder.focusSearch(next.get(), direction));
            }

            if (focusableNext == null) {
                return false;
            }

            mMatchedNode.reset(next);
            return focusableNext.performAction(
                    AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(focusableNext);
            next.recycle();
        }
    }

    /**
     * Will delete the last entered character if it exists. If not, will exit
     * incremental search mode without syncing to the selected result.
     */
    private boolean handleDelete() {
        int length = mQueryText.length();

        if (length > 0) {
            mQueryText.deleteCharAt(mQueryText.length() - 1);
            brailleMatchedOrFocusedNode();
            mSearchOverlay.refreshOverlay();
        } else {
            syncBackToInitial();
            finishSearch();
        }
        return true;
    }

    /**
     * Returns whether the specified node's description matches the current
     * query text (case insensitive).
     */
    private boolean nodeMatchesQuery(AccessibilityNodeInfoCompat node) {
        // When no query text, consider everything a match.
        if (TextUtils.isEmpty(mQueryText)) {
            return AccessibilityNodeInfoUtils.shouldFocusNode(
                                mAccessibilityService, node);
        }

        if (node == null ||
            mSelfBrailleManager.hasContentForNode(node)) {
            return false;
        }

        CharSequence nodeText =
                LabelingUtils.getNodeText(node, mLabelManager);
        if (nodeText == null) {
            return false;
        }
        String queryText = mQueryText.toString().toLowerCase();

        return nodeText.toString().toLowerCase().contains(queryText);
    }

    /**
     * Updates the display to show the current search result, with cursor
     * at the end of the matching text. The text will correspond to
     * the text of matched node, which may not be the text of the focused node.
     */
    private void brailleMatchedOrFocusedNode() {
        if (getCurrentNode() != null) {
            DisplayManager.Content content =
                    formatNodeToBraille(getCurrentNode());
            if (content != null) {
                mDisplayManager.setContent(content);
            }
        }
    }

    /**
     * Returns the last matching node if available. Otherwise falls back to
     * the currently focused node.
     */
    private AccessibilityNodeInfoCompat getCurrentNode() {
        return AccessibilityNodeInfoRef.isNull(mMatchedNode) ?
                    FocusFinder.getFocusedNode(mAccessibilityService, true) :
                    mMatchedNode.get();
    }

    /**
     * Formats some braille content from an {@link AccessibilityEvent}.
     */
    private DisplayManager.Content formatEventToBraille(
            AccessibilityEvent event) {
        AccessibilityNodeInfoCompat eventNode = getNodeFromEvent(event);
        if (eventNode != null) {
            DisplayManager.Content content = formatNodeToBraille(eventNode);
            if (content != null) {
                return content.setPanStrategy(DisplayManager.Content.PAN_CURSOR);
            }
        }

        // This should never happen, print out an error if it does.
        LogUtils.log(this, Log.WARN, "No node on event!");
        return null;
    }

    /**
     * Formats some braille content from an {@link AccessibilityNodeInfoCompat}.
     * Marks the description with a cursor right after the last character
     * matching the search query. Returns content with keep pan strategy
     * by default, or reset pan strategy if there is no matched node stored yet.
     */
    private DisplayManager.Content formatNodeToBraille(
            AccessibilityNodeInfoCompat node) {
        if (node == null) {
            return null;
        }
        DisplayManager.Content content = mNodeBrailler.brailleNode(node);
        if (content == null) {
            return null;
        }

        Spanned spanned = content.getSpanned();
        if (spanned == null) {
            LogUtils.log(this, Log.ERROR, "No text for node");
            return null;
        }

        // Find index of match and the index corresponding to the
        // end of the matched text so we know where to place the cursor.
        String lowerCase = spanned.toString().toLowerCase();
        String matchText = mQueryText.toString().toLowerCase();
        AccessibilityNodeInfoCompat spanNode =
                (AccessibilityNodeInfoCompat) DisplaySpans.getEqualSpan(
                        spanned, node);
        int cursorIndex = -1;
        if (spanNode != null) {
            int nodeIndex = spanned.getSpanStart(spanNode);
            if (nodeIndex >= 0 && nodeIndex < lowerCase.length()) {
                int matchIndex = lowerCase.indexOf(matchText, nodeIndex);
                if (matchIndex >= 0 && matchIndex <= lowerCase.length()) {
                    cursorIndex = matchIndex + matchText.length();
                }
            }
        }

        // Add prefix to what's displayed to mark this as a search result.
        String prefix = mAccessibilityService.getString(
                R.string.search_result_prefix);
        int lengthDiff = prefix.length();
        SpannableStringBuilder sb = (SpannableStringBuilder) spanned;
        sb.insert(0, prefix);

        // If match in this node, add cursor at end of match.
        if (cursorIndex != -1) {
            DisplaySpans.addSelection(sb, cursorIndex + lengthDiff,
                   cursorIndex + lengthDiff);
        }

        // No matched node stored, means we have just activated search mode,
        // so we want to reset the panning frame so we can see the search
        // prefix.
        int panStrategy = AccessibilityNodeInfoRef.isNull(mMatchedNode) ?
                DisplayManager.Content.PAN_RESET :
                DisplayManager.Content.PAN_KEEP;

        return content.setPanStrategy(panStrategy);
    }

    private AccessibilityNodeInfoCompat getNodeFromEvent(
            AccessibilityEvent event) {
        AccessibilityNodeInfo node = event.getSource();
        if (node != null) {
            return new AccessibilityNodeInfoCompat(node);
        } else {
            return null;
        }
    }


    /* Package private functions for testing. */

    /*package*/ SearchNavigationMode(
            DisplayManager displayManager,
            AccessibilityService accessibilityService,
            FeedbackManager feedbackManager,
            TranslatorManager translatorManager,
            SelfBrailleManager selfBrailleManager,
            NodeBrailler nodeBrailler,
            SearchStateListener searchStateListener,
            SearchOverlay searchOverlay) {
        mDisplayManager = displayManager;
        mAccessibilityService = accessibilityService;
        mFeedbackManager = feedbackManager;
        mTranslatorManager = translatorManager;
        mSelfBrailleManager = selfBrailleManager;
        mNodeBrailler = nodeBrailler;
        mSearchStateListener = searchStateListener;
        mSearchOverlay = searchOverlay;
        mLabelManager = null;
    }

    /*package*/ void setQueryTextForTest(String text) {
        mQueryText.setLength(0);
        mQueryText.append(text);
    }

    /*package*/ String getQueryTextForTest() {
        return mQueryText.toString();
    }

}

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
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Build;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import android.util.Pair;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.googlecode.eyesfree.braille.display.BrailleInputEvent;
import com.googlecode.eyesfree.labeling.CustomLabelManager;
import com.googlecode.eyesfree.labeling.Label;
import com.googlecode.eyesfree.labeling.LabelOperationUtils;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoRef;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * Navigation mode for going through menu items shown specially on the Braille display. Not to be
 * confused with navigation through normal on screen options menu items. This is a menu that is
 * shown only on the Braille display for unlabeled items for adding, editing, or removing labels.
 */
public class BrailleMenuNavigationMode implements NavigationMode {
  private static final String TALKBACK_URL_PLAYSTORE =
      "market://details?id=com.google.android.marvin.talkback";
  private static final String TALKBACK_URL_WEB =
      "https://play.google.com/store/apps/details?id=com.google.android.marvin.talkback&hl=en";
  private static final int SCROLL_MENU_FORWARD = 1;
  private static final int SCROLL_MENU_BACKWARD = -1;

  /** Listener for menu state. */
  public static interface BrailleMenuListener {
    public void onMenuClosed();
  }

  private final DisplayManager mDisplayManager;
  private final AccessibilityService mAccessibilityService;
  private final FeedbackManager mFeedbackManager;
  private final CustomLabelManager mLabelManager;
  private final BrailleMenuListener mBrailleMenuListener;

  private final AccessibilityNodeInfoRef mInitialNode = new AccessibilityNodeInfoRef();

  private boolean mActive;
  private List<MenuItem> mMenuItems;
  private int mCurrentIndex;

  public BrailleMenuNavigationMode(
      DisplayManager displayManager,
      AccessibilityService accessibilityService,
      FeedbackManager feedbackManager,
      CustomLabelManager labelManager,
      BrailleMenuListener brailleMenuListener) {
    mDisplayManager = displayManager;
    mAccessibilityService = accessibilityService;
    mFeedbackManager = feedbackManager;
    mLabelManager = labelManager;
    mBrailleMenuListener = brailleMenuListener;
    mCurrentIndex = 0;
  }

  @Override
  public void onActivate() {
    // No point in activating if labeling isn't supported on the OS.
    if (Build.VERSION.SDK_INT < CustomLabelManager.MIN_API_LEVEL) {
      closeMenu();
      return;
    }

    mActive = true;

    // If the currently focused node doesn't warrant a menu, deactivate.
    AccessibilityNodeInfoCompat focused = FocusFinder.getFocusedNode(mAccessibilityService, false);
    if (!showMenuForNode(focused)) {
      mFeedbackManager.emitFeedback(FeedbackManager.TYPE_COMMAND_FAILED);
      closeMenu();
      return;
    }

    // Save the node.
    mInitialNode.reset(focused);

    brailleDisplayWithCurrentItem();
  }

  @Override
  public void onDeactivate() {
    mActive = false;
    mCurrentIndex = 0;
    mMenuItems = null;
    mInitialNode.clear();
  }

  @Override
  public void onObserveAccessibilityEvent(AccessibilityEvent event) {
    // Nothing to do here.
  }

  @Override
  public boolean onAccessibilityEvent(AccessibilityEvent event) {
    switch (event.getEventType()) {
      case AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED:
      case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
        closeMenu();
        return true;
    }
    // Don't let fall through.
    return true;
  }

  @Override
  public void onInvalidateAccessibilityNode(AccessibilityNodeInfoCompat node) {
    // Nothing to do here.
  }

  @Override
  public boolean onPanLeftOverflow(DisplayManager.Content content) {
    // When we overflow, want to move to the previous item, if it exists.
    return mFeedbackManager.emitOnFailure(
        scrollMenu(SCROLL_MENU_BACKWARD), FeedbackManager.TYPE_NAVIGATE_OUT_OF_BOUNDS);
  }

  @Override
  public boolean onPanRightOverflow(DisplayManager.Content content) {
    // When we overflow, want to move to the next item, if it exists.
    return mFeedbackManager.emitOnFailure(
        scrollMenu(SCROLL_MENU_FORWARD), FeedbackManager.TYPE_NAVIGATE_OUT_OF_BOUNDS);
  }

  @Override
  public boolean onMappedInputEvent(BrailleInputEvent event, DisplayManager.Content content) {
    switch (event.getCommand()) {
      case BrailleInputEvent.CMD_NAV_ITEM_NEXT:
      case BrailleInputEvent.CMD_NAV_LINE_NEXT:
        return mFeedbackManager.emitOnFailure(
            scrollMenu(SCROLL_MENU_FORWARD), FeedbackManager.TYPE_NAVIGATE_OUT_OF_BOUNDS);
      case BrailleInputEvent.CMD_NAV_ITEM_PREVIOUS:
      case BrailleInputEvent.CMD_NAV_LINE_PREVIOUS:
        return mFeedbackManager.emitOnFailure(
            scrollMenu(SCROLL_MENU_BACKWARD), FeedbackManager.TYPE_NAVIGATE_OUT_OF_BOUNDS);
      case BrailleInputEvent.CMD_ACTIVATE_CURRENT:
      case BrailleInputEvent.CMD_ROUTE:
      case BrailleInputEvent.CMD_KEY_ENTER:
        return mFeedbackManager.emitOnFailure(
            activateCurrentMenuItem(), FeedbackManager.TYPE_COMMAND_FAILED);
      default:
        // Don't let other commands fall through.
        return true;
    }
  }

  /** Return whether navigation mode is active. */
  public boolean isActive() {
    return mActive;
  }

  /** Activates the currently selected menu item. */
  public boolean activateCurrentMenuItem() {
    // Grab the state we need before closing the menu, since that
    // clears out all state.
    final int itemId = mMenuItems.get(mCurrentIndex).getId();
    AccessibilityNodeInfoCompat node = mInitialNode.release();
    try {
      closeMenu();

      if (node == null) {
        return false;
      }

      // This must be checked before we try to grab a label.
      if (itemId == R.string.menu_item_update_talkback) {
        return launchIntentToPlayStoreTalkBack();
      }

      AccessibilityNodeInfo unwrapped = (AccessibilityNodeInfo) node.getInfo();
      final Label existingLabel =
          mLabelManager.getLabelForViewIdFromCache(unwrapped.getViewIdResourceName());

      if (itemId == R.string.menu_item_label_add) {
        return LabelOperationUtils.startActivityAddLabelForNode(mAccessibilityService, unwrapped);
      } else if (itemId == R.string.menu_item_label_edit) {
        return LabelOperationUtils.startActivityEditLabel(mAccessibilityService, existingLabel);
      } else if (itemId == R.string.menu_item_label_remove) {
        return LabelOperationUtils.startActivityRemoveLabel(mAccessibilityService, existingLabel);
      }
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(node);
    }
    return false;
  }

  /** Fires an intent to open TalkBack in the Play store. */
  private boolean launchIntentToPlayStoreTalkBack() {
    try {
      mAccessibilityService.startActivity(
          new Intent(Intent.ACTION_VIEW, Uri.parse(TALKBACK_URL_PLAYSTORE))
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    } catch (ActivityNotFoundException e) {
      mAccessibilityService.startActivity(
          new Intent(Intent.ACTION_VIEW, Uri.parse(TALKBACK_URL_WEB))
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
    return true;
  }

  /**
   * Displays a menu for the node and populates {@code mMenuItems}. If the node doesn't warrant a
   * menu does nothing. Returns whether a menu was shown.
   */
  private boolean showMenuForNode(AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return false;
    }

    mMenuItems = getItemsForNode(node);
    if (mMenuItems.isEmpty()) {
      return false;
    }
    return true;
  }

  /**
   * Calls the listener signaling that the menu has been closed and the navigation mode should be
   * exited.
   */
  private void closeMenu() {
    mBrailleMenuListener.onMenuClosed();
  }

  /**
   * Scrolls the menu to the next item in the specified direction. Returns whether the menu
   * successfully scrolled. {@code direction} should be either SCROLL_MENU_FORWARD or
   * SCROLL_MENU_BACKWARD.
   */
  private boolean scrollMenu(int direction) {
    if (direction == SCROLL_MENU_FORWARD) {
      if (mCurrentIndex + 1 >= mMenuItems.size()) {
        return false;
      }
      mCurrentIndex++;
      brailleDisplayWithCurrentItem();
      return true;
    } else if (direction == SCROLL_MENU_BACKWARD) {
      if (mCurrentIndex == 0) {
        return false;
      }
      mCurrentIndex--;
      brailleDisplayWithCurrentItem();
      return true;
    }
    return false;
  }

  /** Updates the text on the braille display to match the currently selected menu item. */
  private void brailleDisplayWithCurrentItem() {
    MenuItem current = mMenuItems.get(mCurrentIndex);
    DisplayManager.Content content = new DisplayManager.Content(current.getName());
    content.setPanStrategy(DisplayManager.Content.PAN_RESET);
    mDisplayManager.setContent(content);
  }

  /**
   * Returns a list of menu item strings to be shown for the specified node. May be empty if no
   * items needed (already labeled by developer).
   */
  private List<MenuItem> getItemsForNode(AccessibilityNodeInfoCompat node) {
    List<MenuItem> items = new ArrayList<MenuItem>();
    AccessibilityNodeInfo unwrapped = (AccessibilityNodeInfo) node.getInfo();
    boolean hasDescription = !TextUtils.isEmpty(AccessibilityNodeInfoUtils.getNodeText(node));
    final Pair<String, String> parsedId =
        CustomLabelManager.splitResourceName(unwrapped.getViewIdResourceName());
    boolean hasParseableId = (parsedId != null);

    // TODO: There are a number of views that have a
    // different resource namespace than their parent application. It's
    // likely we'll need to refine the database structure to accommodate
    // these while also allowing the user to modify them through TalkBack
    // settings. For now, we'll simply not allow labeling of such views.
    boolean isFromKnownApp = false;
    if (hasParseableId) {
      try {
        mAccessibilityService.getPackageManager().getPackageInfo(parsedId.first, 0);
        isFromKnownApp = true;
      } catch (NameNotFoundException e) {
        // Do nothing.
      }
    }

    // Return empty list if it has a description, has no parseable id since
    // we don't support those in the label manager right now, or if it's id
    // is in a different namespace than a known package.
    if (hasDescription || !hasParseableId || !isFromKnownApp) {
      return items;
    }

    // If label manager is not initialized, it is because user has a
    // version of TalkBack that doesn't support labeling.
    // Tell the user to update.
    if (!mLabelManager.isInitialized()) {
      items.add(new MenuItem(R.string.menu_item_update_talkback, mAccessibilityService));
      return items;
    }

    final Label viewLabel =
        mLabelManager.getLabelForViewIdFromCache(unwrapped.getViewIdResourceName());
    // If no custom label, only have "add" option. If there is already a
    // label we have the "edit" and "remove" options.
    if (viewLabel == null) {
      items.add(new MenuItem(R.string.menu_item_label_add, mAccessibilityService));
    } else {
      items.add(new MenuItem(R.string.menu_item_label_edit, mAccessibilityService));
      items.add(new MenuItem(R.string.menu_item_label_remove, mAccessibilityService));
    }
    return items;
  }

  /** Class containing menu item info. */
  private class MenuItem {
    private String mName;
    private int mId;

    public MenuItem(int resId, Context context) {
      mName = context.getString(resId);
      mId = resId;
    }

    public int getId() {
      return mId;
    }

    public String getName() {
      return mName;
    }
  }
}

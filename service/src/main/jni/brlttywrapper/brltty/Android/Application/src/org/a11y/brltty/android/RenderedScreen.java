/*
 * BRLTTY - A background process providing access to the console screen (when in
 *          text mode) for a blind person using a refreshable braille display.
 *
 * Copyright (C) 1995-2018 by The BRLTTY Developers.
 *
 * BRLTTY comes with ABSOLUTELY NO WARRANTY.
 *
 * This is free software, placed under the terms of the
 * GNU Lesser General Public License, as published by the Free Software
 * Foundation; either version 2.1 of the License, or (at your option) any
 * later version. Please see the file LICENSE-LGPL for details.
 *
 * Web Page: http://brltty.com/
 *
 * This software is maintained by Dave Mielke <dave@mielke.cc>.
 */

package org.a11y.brltty.android;


import android.graphics.Rect;
import android.os.Build;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;

public class RenderedScreen {
  private static final String LOG_TAG = RenderedScreen.class.getName();

  private final AccessibilityNodeInfo eventNode;
  private final AccessibilityNodeInfo rootNode;

  private final ScreenElementList screenElements = new ScreenElementList();
  private final List<CharSequence> screenRows = new ArrayList<CharSequence>();

  private final int screenWidth;
  private final AccessibilityNodeInfo cursorNode;

  private final AccessibilityNodeInfo getNode(AccessibilityNodeInfo node) {
    if (node == null) return null;
    return AccessibilityNodeInfo.obtain(node);
  }

  public final AccessibilityNodeInfo getRootNode() {
    return getNode(rootNode);
  }

  public final int getScreenWidth() {
    return screenWidth;
  }

  public final int getScreenHeight() {
    return screenRows.size();
  }

  public final CharSequence getScreenRow(int index) {
    return screenRows.get(index);
  }

  public final AccessibilityNodeInfo getCursorNode() {
    return getNode(cursorNode);
  }

  public final ScreenElement findScreenElement(AccessibilityNodeInfo node) {
    if (node == null) return null;
    Rect location = new Rect();
    node.getBoundsInScreen(location);
    return screenElements.findByVisualLocation(location);
  }

  public final ScreenElement findRenderedScreenElement(AccessibilityNodeInfo node) {
    ScreenElement element = findScreenElement(node);

    if (element != null) {
      if (element.getBrailleLocation() != null) {
        return element;
      }
    }

    {
      int childCount = node.getChildCount();

      for (int childIndex = 0; childIndex < childCount; childIndex += 1) {
        AccessibilityNodeInfo child = node.getChild(childIndex);

        if (child != null) {
          element = findRenderedScreenElement(child);

          child.recycle();
          child = null;

          if (element != null) return element;
        }
      }
    }

    return null;
  }

  public enum ChangeFocusDirection {
    FORWARD,
    BACKWARD
  }

  public boolean changeFocus(ChangeFocusDirection direction) {
    AccessibilityNodeInfo node = getCursorNode();

    if (node != null) {
      ScreenElement element = findScreenElement(node);

      node.recycle();
      node = null;

      if (element != null) {
        int index = screenElements.indexOf(element);

        switch (direction) {
          case FORWARD:
            {
              int size = screenElements.size();

              while (++index < size) {
                if (screenElements.get(index).setAccessibilityFocus()) return true;
              }

              break;
            }

          case BACKWARD:
            {
              while (--index >= 0) {
                if (screenElements.get(index).setAccessibilityFocus()) return true;
              }

              break;
            }
        }
      }
    }

    return false;
  }

  public final boolean performAction(int column, int row) {
    ScreenElement element = screenElements.findByBrailleLocation(column, row);

    if (element != null) {
      Rect location = element.getBrailleLocation();

      if (element.performAction((column - location.left), (row - location.top))) {
        return true;
      }
    }

    return false;
  }

  static final int SIGNIFICANT_NODE_ACTIONS =
      AccessibilityNodeInfo.ACTION_CLICK
          | AccessibilityNodeInfo.ACTION_LONG_CLICK
          | AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
          | AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD;

  private static int getSignificantActions(AccessibilityNodeInfo node) {
    return node.getActions() & SIGNIFICANT_NODE_ACTIONS;
  }

  private boolean hasInnerText(AccessibilityNodeInfo root) {
    int childCount = root.getChildCount();

    for (int childIndex = 0; childIndex < childCount; childIndex += 1) {
      AccessibilityNodeInfo child = root.getChild(childIndex);

      if (child != null) {
        boolean found;

        if (getSignificantActions(child) != 0) {
          found = false;
        } else if (child.getText() != null) {
          found = true;
        } else {
          found = hasInnerText(child);
        }

        child.recycle();
        if (found) return true;
      }
    }

    return false;
  }

  private final int addScreenElements(AccessibilityNodeInfo root) {
    int propagatedActions = SIGNIFICANT_NODE_ACTIONS;

    if (root != null) {
      int actions = getSignificantActions(root);
      int childCount = root.getChildCount();

      if (childCount > 0) {
        propagatedActions = 0;

        for (int childIndex = 0; childIndex < childCount; childIndex += 1) {
          AccessibilityNodeInfo child = root.getChild(childIndex);

          if (child != null) {
            propagatedActions |= addScreenElements(child);

            child.recycle();
            child = null;
          }
        }
      }

      if (ScreenUtilities.isVisible(root)) {
        String text = null;

        {
          CharSequence actualText = root.getText();

          if (actualText != null) {
            text = actualText.toString();
            if (!ScreenUtilities.isEditable(root)) text = text.trim();
          }
        }

        if (text == null) {
          if ((actions != 0) && !hasInnerText(root)) {
            if ((text = ScreenUtilities.normalizeText(root.getContentDescription())) == null) {
              text = root.getClassName().toString();
              int index = text.lastIndexOf('.');
              if (index >= 0) text = text.substring(index + 1);
              text = "(" + text + ")";
            }
          }
        }

        if (text != null) screenElements.add(text, root);
      }

      propagatedActions &= ~actions;
    }

    return propagatedActions;
  }

  private final int findScreenWidth() {
    int width = 1;

    if (screenRows.isEmpty()) {
      screenRows.add("waiting for screen update");
    }

    for (CharSequence row : screenRows) {
      int length = row.length();

      if (length > width) {
        width = length;
      }
    }

    return width;
  }

  private final AccessibilityNodeInfo findCursorNode() {
    AccessibilityNodeInfo root = getRootNode();

    if (root != null) {
      if (ApplicationUtilities.haveSdkVersion(Build.VERSION_CODES.JELLY_BEAN)) {
        AccessibilityNodeInfo node = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);

        if (node != null) {
          root.recycle();
          root = null;
          return node;
        }
      }

      {
        AccessibilityNodeInfo node;

        if (ApplicationUtilities.haveSdkVersion(Build.VERSION_CODES.JELLY_BEAN)) {
          node = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        } else if (ApplicationUtilities.haveSdkVersion(Build.VERSION_CODES.ICE_CREAM_SANDWICH)) {
          node = ScreenUtilities.findFocusedNode(root);
        } else {
          node = null;
        }

        if (!ApplicationUtilities.haveSdkVersion(Build.VERSION_CODES.JELLY_BEAN)) {
          if (node == null) {
            if (ApplicationUtilities.haveSdkVersion(Build.VERSION_CODES.ICE_CREAM_SANDWICH)) {
              if ((node = ScreenUtilities.findFocusableNode(root)) != null) {
                if (!node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) {
                  node.recycle();
                  node = null;
                }
              }
            }
          }
        }

        if (node != null) {
          root.recycle();
          root = node;
          node = ScreenUtilities.findSelectedNode(root);

          if (node != null) {
            root.recycle();
            root = node;
            node = null;
          }

          if ((node = ScreenUtilities.findTextNode(root)) == null) {
            node = ScreenUtilities.findDescribedNode(root);
          }

          if (node != null) {
            root.recycle();
            root = node;
            node = null;
          }

          return root;
        }
      }
    }

    return root;
  }

  public void logRenderedScreen() {
    ScreenLogger logger = ScreenDriver.getLogger();
    logger.log("begin rendered screen");

    logger.log("screen element count: " + screenElements.size());

    for (ScreenElement element : screenElements) {
      logger.log("screen element: " + element.getElementText());
    }

    logger.log("screen row count: " + screenRows.size());
    logger.log("screen width: " + screenWidth);

    for (CharSequence row : screenRows) {
      logger.log("screen row: " + row.toString());
    }

    logger.log("end rendered screen");
  }

  public RenderedScreen(AccessibilityNodeInfo node) {
    if (node != null) node = AccessibilityNodeInfo.obtain(node);

    eventNode = node;
    rootNode = ScreenUtilities.findRootNode(node);

    addScreenElements(rootNode);
    BrailleRenderer.getBrailleRenderer().renderScreenElements(screenRows, screenElements);
    screenWidth = findScreenWidth();
    cursorNode = findCursorNode();

    if (ApplicationParameters.LOG_RENDERED_SCREEN) {
      logRenderedScreen();
    }
  }
}

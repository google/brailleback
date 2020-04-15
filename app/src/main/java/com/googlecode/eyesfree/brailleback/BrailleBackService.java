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

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.googlecode.eyesfree.braille.display.BrailleInputEvent;
import com.googlecode.eyesfree.braille.display.Display;
import com.googlecode.eyesfree.brailleback.rule.BrailleRuleRepository;
import com.googlecode.eyesfree.brailleback.utils.PreferenceUtils;
import com.googlecode.eyesfree.labeling.CustomLabelManager;
import com.googlecode.eyesfree.labeling.PackageRemovalReceiver;
import com.googlecode.eyesfree.utils.LogUtils;

/** An accessibility service that provides feedback through a braille display. */
public class BrailleBackService extends AccessibilityService
    implements Display.OnConnectionStateChangeListener,
        DisplayManager.OnMappedInputEventListener,
        DisplayManager.OnPanOverflowListener,
        SearchNavigationMode.SearchStateListener,
        BrailleMenuNavigationMode.BrailleMenuListener {

  /** Start the service, initializing a few components. */
  private static final int WHAT_START = 2;

  // Braille dot bit pattern constants.
  public static final int DOT1 = 0x01;
  public static final int DOT2 = 0x02;
  public static final int DOT3 = 0x04;
  public static final int DOT4 = 0x08;
  public static final int DOT5 = 0x10;
  public static final int DOT6 = 0x20;
  public static final int DOT7 = 0x40;
  public static final int DOT8 = 0x80;

  public static final boolean LABELING_ENABLED = false;

  private static BrailleBackService instance;

  private FeedbackManager feedbackManager;
  private TranslatorManager translatorManager;
  private DisplayManager displayManager;
  private SelfBrailleManager selfBrailleManager;
  private NodeBrailler nodeBrailler;
  private BrailleRuleRepository ruleRepository;
  private FocusTracker focusTracker;
  private IMEHelper imeHelper;
  private ModeSwitcher modeSwitcher;
  private SearchNavigationMode searchNavigationMode;
  private BrailleMenuNavigationMode brailleMenuNavigationMode;
  protected IMENavigationMode imeNavigationMode;
  private CustomLabelManager labelManager;
  private PackageRemovalReceiver packageReceiver;

  /** Set if the infrastructure is initialized. */
  private boolean isInfrastructureInitialized;

  /**
   * Dot combination for switching navigation mode. At this time, this is only used for debugging.
   */
  private static final int SWITCH_NAVIGATION_MODE_DOTS = DOT7 | DOT8;

  /** {@link Handler} for executing messages on the service main thread. */
  private final Handler handler =
      new Handler() {
        @Override
        public void handleMessage(Message message) {
          switch (message.what) {
            case WHAT_START:
              updateServiceInfo();
              initializeDependencies();
              return;
            default: // fall out
          }
        }
      };

  @Override
  public void onConnectionStateChanged(int state) {
    if (!displayManager.isSimulatedDisplay()) {
      if (state == Display.STATE_NOT_CONNECTED) {
        feedbackManager.emitFeedback(FeedbackManager.TYPE_DISPLAY_DISCONNECTED);
      } else if (state == Display.STATE_CONNECTED) {
        feedbackManager.emitFeedback(FeedbackManager.TYPE_DISPLAY_CONNECTED);
      }
    }
    if (focusTracker != null) {
      focusTracker.onConnectionStateChanged(state);
    }
  }

  @Override
  public void onMappedInputEvent(BrailleInputEvent event, DisplayManager.Content content) {
    if (modeSwitcher == null) {
      return;
    }
    // Global commands can't be overriden.
    if (handleGlobalCommands(event)) {
      return;
    }
    if (BuildConfig.DEBUG
        && event.getCommand() == BrailleInputEvent.CMD_BRAILLE_KEY
        && event.getArgument() == SWITCH_NAVIGATION_MODE_DOTS) {
      modeSwitcher.switchMode();
      return;
    }
    if (event.getCommand() == BrailleInputEvent.CMD_TOGGLE_BRAILLE_MENU) {
      if (!LABELING_ENABLED) {
        return;
      }
      if (brailleMenuNavigationMode.isActive()) {
        modeSwitcher.overrideMode(null);
      } else {
        modeSwitcher.overrideMode(brailleMenuNavigationMode);
      }
      return;
    }
    if (modeSwitcher.onMappedInputEvent(event, content)) {
      return;
    }
    // Check native case after navigation mode handler to allow navigation
    // mode chance to deal with webview incremental search.
    if (event.getCommand() == BrailleInputEvent.CMD_TOGGLE_INCREMENTAL_SEARCH) {
      // Search Navigation mode will handle disabling the mode.
      startSearchWithTutorial();
      return;
    }
    if (imeHelper.onInputEvent(event)) {
      return;
    }
    if (event.getCommand() == BrailleInputEvent.CMD_TOGGLE_BRAILLE_GRADE) {
      // Read current grade preference.
      Context context = this;
      SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
      final String prefKey = context.getString(R.string.pref_braille_type_key);
      final String prefValueDefault = "";
      final String prefValueOld = sharedPreferences.getString(prefKey, prefValueDefault);
      final String prefValue6Dot = context.getString(R.string.pref_braille_type_six_dot_value);
      final String prefValue8Dot = context.getString(R.string.pref_braille_type_eight_dot_value);

      // Toggle and store new computer/literary grade preference.
      final String prefValueNew =
          TextUtils.equals(prefValueOld, prefValue6Dot) ? prefValue8Dot : prefValue6Dot;
      SharedPreferences.Editor prefEditor = sharedPreferences.edit();
      prefEditor.putString(prefKey, prefValueNew);
      prefEditor.apply();

      // Play audio indicator of grade.
      final int audioId =
          TextUtils.equals(prefValueNew, prefValue6Dot)
              ? FeedbackManager.TYPE_GRADE_6_DOT
              : FeedbackManager.TYPE_GRADE_8_DOT;
      feedbackManager.emitFeedback(audioId);

      // If showing preference screen... update displayed preference.
      Intent refreshPrefIntent = new Intent(BrailleBackPreferencesActivity.INTENT_REFRESH_DISPLAY);
      sendBroadcast(refreshPrefIntent);

      return;
    }
    feedbackManager.emitFeedback(FeedbackManager.TYPE_UNKNOWN_COMMAND);
  }

  private boolean handleGlobalCommands(BrailleInputEvent event) {
    boolean success;
    switch (event.getCommand()) {
      case BrailleInputEvent.CMD_GLOBAL_HOME:
        success = performGlobalAction(GLOBAL_ACTION_HOME);
        break;
      case BrailleInputEvent.CMD_GLOBAL_BACK:
        success = performGlobalAction(GLOBAL_ACTION_BACK);
        break;
      case BrailleInputEvent.CMD_GLOBAL_RECENTS:
        success = performGlobalAction(GLOBAL_ACTION_RECENTS);
        break;
      case BrailleInputEvent.CMD_GLOBAL_NOTIFICATIONS:
        success = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
        break;
      case BrailleInputEvent.CMD_HELP:
        success = runHelp();
        break;
      default:
        return false;
    }
    if (!success) {
      feedbackManager.emitFeedback(FeedbackManager.TYPE_COMMAND_FAILED);
    }
    // Don't fall through even if the command failed, we own these
    // commands.
    return true;
  }

  @Override
  public void onPanLeftOverflow(DisplayManager.Content content) {
    if (modeSwitcher != null && !modeSwitcher.onPanLeftOverflow(content)) {
      feedbackManager.emitFeedback(FeedbackManager.TYPE_NAVIGATE_OUT_OF_BOUNDS);
    }
  }

  @Override
  public void onPanRightOverflow(DisplayManager.Content content) {
    if (modeSwitcher != null && !modeSwitcher.onPanRightOverflow(content)) {
      feedbackManager.emitFeedback(FeedbackManager.TYPE_NAVIGATE_OUT_OF_BOUNDS);
    }
  }

  @Override
  public void onServiceConnected() {
    instance = this;
    PreferenceUtils.initLogLevel(this);
    if (isInfrastructureInitialized) {
      return;
    }

    handler.sendEmptyMessage(WHAT_START);
    // We are in an initialized state now.
    isInfrastructureInitialized = true;
  }

  @Override
  public void onDestroy() {
    instance = null;
    super.onDestroy();
    if (displayManager != null) {
      displayManager.setContent(new DisplayManager.Content(getString(R.string.shutting_down)));

      // We are not in an initialized state anymore.
      isInfrastructureInitialized = false;
    }
    shutdownDependencies();
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event) {
    LogUtils.log(this, Log.VERBOSE, "Event: %s", event.toString());
    LogUtils.log(this, Log.VERBOSE, "Node: %s", event.getSource());
    if (modeSwitcher != null) {
      modeSwitcher.onObserveAccessibilityEvent(event);
      modeSwitcher.onAccessibilityEvent(event);
    }
    if (labelManager != null) {
      labelManager.onAccessibilityEvent(event);
    }
  }

  @Override
  public void onInterrupt() {
    // Nothing to interrupt.
  }

  @Override
  public void onConfigurationChanged(Configuration newConfiguration) {
    if (translatorManager != null) {
      translatorManager.onConfigurationChanged(newConfiguration);
    }
  }

  @Override
  public void onSearchStarted() {
    // Nothing to do here.
  }

  @Override
  public void onSearchFinished() {
    modeSwitcher.overrideMode(null);
  }

  @Override
  public void onMenuClosed() {
    modeSwitcher.overrideMode(null);
  }

  /** Starts search mode in BrailleBack. Optionally tries to start the tutorial first. */
  public void startSearchMode() {
    modeSwitcher.overrideMode(searchNavigationMode);
  }

  private void startSearchWithTutorial() {
    searchNavigationMode.setInitialNodeToCurrent();
    // Try to start the search tutorial activity first. Only if it
    // doesn't start do we activate search mode.
    if (SearchTutorialActivity.tryStartActivity(this)) {
      return;
    }
    startSearchMode();
  }

  private void initializeDependencies() {
    // Must initialize label manager before navigation modes.
    initializeLabelManager();
    feedbackManager = new FeedbackManager(this);
    translatorManager = new TranslatorManager(this);
    selfBrailleManager = new SelfBrailleManager();
    ruleRepository = new BrailleRuleRepository(this);
    nodeBrailler = new NodeBrailler(this, ruleRepository, selfBrailleManager);
    initializeDisplayManager();
    initializeNavigationMode();
    imeHelper = new IMEHelper(this);
    focusTracker = new FocusTracker(this);
    focusTracker.register();
  }

  private void updateServiceInfo() {
    // Publicize service meta-data stating that this service does braille. Would be done in
    // accessibility service config if it supported accessibilityFeedbackType FEEDBACK_BRAILLE.
    AccessibilityServiceInfo info = getServiceInfo();
    if (info == null) {
      LogUtils.log(this, Log.ERROR, "getServiceInfo() returned null");
      return;
    }
    info.feedbackType |= AccessibilityServiceInfo.FEEDBACK_BRAILLE;
    setServiceInfo(info);
  }

  private void initializeLabelManager() {
    if (Build.VERSION.SDK_INT >= CustomLabelManager.MIN_API_LEVEL) {
      try {
        labelManager = new CustomLabelManager(this);
      } catch (SecurityException e) {
        // Don't use labeling features if there's a permission denial
        // due to a key mismatch
        LogUtils.log(this, Log.ERROR, "Not using labeling due to permission denial.");
      }

      if (labelManager != null) {
        packageReceiver = new PackageRemovalReceiver();
        registerReceiver(packageReceiver, packageReceiver.getFilter());
        labelManager.ensureDataConsistency();
      }
    }
  }

  private void initializeDisplayManager() {
    displayManager =
        new DisplayManager(
            translatorManager,
            this /*context*/,
            this /*panOverflowListener*/,
            this /*connectionStateChangeListener*/,
            this /*inputEventListener*/);
    displayManager.setContent(new DisplayManager.Content(getString(R.string.display_connected)));
  }

  private void initializeNavigationMode() {
    DefaultNavigationMode defaultNavigationMode =
        new DefaultNavigationMode(
            displayManager,
            this,
            feedbackManager,
            selfBrailleManager,
            nodeBrailler,
            ruleRepository);
    IMENavigationMode newImeNavMode =
        new IMENavigationMode(
            defaultNavigationMode,
            this,
            displayManager,
            feedbackManager,
            selfBrailleManager,
            translatorManager);
    imeNavigationMode = newImeNavMode;
    BrailleIME.setSingletonHost(newImeNavMode);
    modeSwitcher =
        new ModeSwitcher(
            newImeNavMode, new TreeDebugNavigationMode(displayManager, feedbackManager, this));
    modeSwitcher.onActivate();

    // Create separate SearchNavigationMode.
    searchNavigationMode =
        new SearchNavigationMode(
            displayManager,
            this /*accessibilityService*/,
            feedbackManager,
            translatorManager,
            selfBrailleManager,
            nodeBrailler,
            this /*searchStateListener*/,
            labelManager);

    // Create separate BrailleMenuNavigationMode.
    brailleMenuNavigationMode =
        new BrailleMenuNavigationMode(
            displayManager,
            this /*accessibilityService*/,
            feedbackManager,
            labelManager,
            this /*brailleMenuListener*/);
  }

  private void shutdownDependencies() {
    if (displayManager != null) {
      displayManager.shutdown();
      displayManager = null;
    }
    if (translatorManager != null) {
      translatorManager.shutdown();
      translatorManager = null;
    }
    // TODO: Shut down feedback manager and braille translator
    // when those classes have shutdown methods.
    if (focusTracker != null) {
      focusTracker.unregister();
      focusTracker = null;
    }
    if (labelManager != null) {
      labelManager.shutdown();
      labelManager = null;
    }
    if (packageReceiver != null) {
      unregisterReceiver(packageReceiver);
      packageReceiver = null;
    }

    BrailleIME.setSingletonHost(null);
  }

  public boolean runHelp() {
    Intent intent = new Intent(this, KeyBindingsActivity.class);
    intent.addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK
            | Intent.FLAG_ACTIVITY_CLEAR_TOP
            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    startActivity(intent);
    return true;
  }

  public static BrailleBackService getActiveInstance() {
    return instance;
  }

  public CustomLabelManager getLabelManager() {
    return labelManager;
  }

  /**
   * Signal that this node, or one of its descendants, has been changed in a way that could affect
   * how it is displayed. If the node is contributing to the content of the display, the display
   * content will be updated accordingly.
   */
  public void invalidateNode(AccessibilityNodeInfo node) {
    AccessibilityNodeInfoCompat wrapped = new AccessibilityNodeInfoCompat(node);
    modeSwitcher.onInvalidateAccessibilityNode(wrapped);
  }
}

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

import com.googlecode.eyesfree.braille.display.BrailleInputEvent;
import com.googlecode.eyesfree.braille.display.Display;
import com.googlecode.eyesfree.brailleback.rule.BrailleRuleRepository;
import com.googlecode.eyesfree.brailleback.utils.PreferenceUtils;
import com.googlecode.eyesfree.labeling.CustomLabelManager;
import com.googlecode.eyesfree.labeling.PackageRemovalReceiver;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;
import com.googlecode.eyesfree.utils.LogUtils;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * An accessibility service that provides feedback through a braille
 * display.
 */
public class BrailleBackService
        extends AccessibilityService
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

    private static BrailleBackService sInstance;

    private FeedbackManager mFeedbackManager;
    private TranslatorManager mTranslatorManager;
    private DisplayManager mDisplayManager;
    private SelfBrailleManager mSelfBrailleManager;
    private NodeBrailler mNodeBrailler;
    private BrailleRuleRepository mRuleRepository;
    private FocusTracker mFocusTracker;
    private IMEHelper mIMEHelper;
    private ModeSwitcher mModeSwitcher;
    private SearchNavigationMode mSearchNavigationMode;
    private BrailleMenuNavigationMode mBrailleMenuNavigationMode;
    private CustomLabelManager mLabelManager;
    private PackageRemovalReceiver mPackageReceiver;

    /** Set if the infrastructure is initialized. */
    private boolean isInfrastructureInitialized;

    /**
     * Dot combination for switching navigation mode.
     * At this time, this is only used for debugging.
     */
    private static final int SWITCH_NAVIGATION_MODE_DOTS = DOT7 | DOT8;

    /** {@link Handler} for executing messages on the service main thread. */
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message message) {
            switch (message.what) {
                case WHAT_START:
                    updateServiceInfo();
                    initializeDependencies();
                    return;
            }
        }
    };

    @Override
    public void onConnectionStateChanged(int state) {
        if (!mDisplayManager.isSimulatedDisplay()) {
            if (state == Display.STATE_NOT_CONNECTED) {
                mFeedbackManager.emitFeedback(
                    FeedbackManager.TYPE_DISPLAY_DISCONNECTED);
            } else if (state == Display.STATE_CONNECTED) {
                mFeedbackManager.emitFeedback(
                    FeedbackManager.TYPE_DISPLAY_CONNECTED);
            }
        }
        if (mFocusTracker != null) {
            mFocusTracker.onConnectionStateChanged(state);
        }
    }

    @Override
    public void onMappedInputEvent(BrailleInputEvent event,
            DisplayManager.Content content) {
        if (mModeSwitcher == null) {
            return;
        }
        // Global commands can't be overriden.
        if (handleGlobalCommands(event)) {
            return;
        }
        if (BuildConfig.DEBUG
                && event.getCommand() == BrailleInputEvent.CMD_BRAILLE_KEY
                && event.getArgument() == SWITCH_NAVIGATION_MODE_DOTS) {
            mModeSwitcher.switchMode();
            return;
        }
        if (event.getCommand() == BrailleInputEvent.CMD_TOGGLE_BRAILLE_MENU) {
            if (mBrailleMenuNavigationMode.isActive()) {
                mModeSwitcher.overrideMode(null);
            } else {
                mModeSwitcher.overrideMode(mBrailleMenuNavigationMode);
            }
            return;
        }
        if (mModeSwitcher.onMappedInputEvent(event, content)) {
            return;
        }
        // Check native case after navigation mode handler to allow navigation
        // mode chance to deal with webview incremental search.
        if (event.getCommand() ==
                BrailleInputEvent.CMD_TOGGLE_INCREMENTAL_SEARCH) {
            // Search Navigation mode will handle disabling the mode.
            startSearchWithTutorial();
            return;
        }
        if (mIMEHelper.onInputEvent(event)) {
            return;
        }
        mFeedbackManager.emitFeedback(FeedbackManager.TYPE_UNKNOWN_COMMAND);
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
            mFeedbackManager.emitFeedback(FeedbackManager.TYPE_COMMAND_FAILED);
        }
        // Don't fall through even if the command failed, we own these
        // commands.
        return true;
    }

    @Override
    public void onPanLeftOverflow(DisplayManager.Content content) {
        if (mModeSwitcher != null
                && !mModeSwitcher.onPanLeftOverflow(content)) {
            mFeedbackManager.emitFeedback(
                FeedbackManager.TYPE_NAVIGATE_OUT_OF_BOUNDS);
        }
    }

    @Override
    public void onPanRightOverflow(DisplayManager.Content content) {
        if (mModeSwitcher != null
                && !mModeSwitcher.onPanRightOverflow(content)) {
            mFeedbackManager.emitFeedback(
                FeedbackManager.TYPE_NAVIGATE_OUT_OF_BOUNDS);
        }
    }

    @Override
    public void onServiceConnected() {
        sInstance = this;
        PreferenceUtils.initLogLevel(this);
        if (isInfrastructureInitialized) {
            return;
        }

        mHandler.sendEmptyMessage(WHAT_START);
        // We are in an initialized state now.
        isInfrastructureInitialized = true;
    }

    @Override
    public void onDestroy() {
        sInstance = null;
        super.onDestroy();
        if (mDisplayManager != null) {
            mDisplayManager.setContent(
                new DisplayManager.Content(getString(R.string.shutting_down)));

            // We are not in an initialized state anymore.
            isInfrastructureInitialized = false;
        }
        shutdownDependencies();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        LogUtils.log(this, Log.VERBOSE, "Event: %s", event.toString());
        LogUtils.log(this, Log.VERBOSE, "Node: %s", event.getSource());
        if (mIMEHelper != null) {
            mIMEHelper.onAccessibilityEvent(event);
        }
        if (mModeSwitcher != null) {
            mModeSwitcher.onObserveAccessibilityEvent(event);
            mModeSwitcher.onAccessibilityEvent(event);
        }
        if (mLabelManager != null) {
            mLabelManager.onAccessibilityEvent(event);
        }
    }

    @Override
    public void onInterrupt() {
        // Nothing to interrupt.
    }

    @Override
    public void onConfigurationChanged(Configuration newConfiguration) {
        if (mTranslatorManager != null) {
            mTranslatorManager.onConfigurationChanged(newConfiguration);
        }
    }

    @Override
    public void onSearchStarted() {
        // Nothing to do here.
    }

    @Override
    public void onSearchFinished() {
        mModeSwitcher.overrideMode(null);
    }

    @Override
    public void onMenuClosed() {
        mModeSwitcher.overrideMode(null);
    }

    /**
     * Starts search mode in BrailleBack. Optionally tries to start the tutorial
     * first.
     */
    public void startSearchMode() {
        mModeSwitcher.overrideMode(mSearchNavigationMode);
    }

    private void startSearchWithTutorial() {
        mSearchNavigationMode.setInitialNodeToCurrent();
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
        mFeedbackManager = new FeedbackManager(this);
        mTranslatorManager = new TranslatorManager(this);
        mSelfBrailleManager = new SelfBrailleManager();
        mRuleRepository = new BrailleRuleRepository(this);
        mNodeBrailler = new NodeBrailler(this,
                mRuleRepository, mSelfBrailleManager);
        initializeDisplayManager();
        initializeNavigationMode();
        mIMEHelper = new IMEHelper(this);
        mFocusTracker = new FocusTracker(this);
        mFocusTracker.register();
    }

    private void updateServiceInfo() {
        AccessibilityServiceInfo info = getServiceInfo();
        info.feedbackType |= AccessibilityServiceInfo.FEEDBACK_BRAILLE;
        setServiceInfo(info);
    }

    private void initializeLabelManager() {
        if (Build.VERSION.SDK_INT >= CustomLabelManager.MIN_API_LEVEL) {
            try {
                mLabelManager = new CustomLabelManager(this);
            } catch (SecurityException e) {
                // Don't use labeling features if there's a permission denial
                // due to a key mismatch
                LogUtils.log(this, Log.ERROR,
                        "Not using labeling due to permission denial.");
            }

            if (mLabelManager != null) {
                mPackageReceiver = new PackageRemovalReceiver();
                registerReceiver(
                        mPackageReceiver, mPackageReceiver.getFilter());
                mLabelManager.ensureDataConsistency();
            }
        }
    }

    private void initializeDisplayManager() {
        mDisplayManager = new DisplayManager(mTranslatorManager,
                this /*context*/,
                this /*panOverflowListener*/,
                this /*connectionStateChangeListener*/,
                this /*inputEventListener*/);
        mDisplayManager.setContent(
            new DisplayManager.Content(getString(R.string.display_connected)));
    }

    private void initializeNavigationMode() {
        DefaultNavigationMode defaultNavigationMode =
                new DefaultNavigationMode(
                        mDisplayManager,
                        this,
                        mFeedbackManager,
                        mSelfBrailleManager,
                        mNodeBrailler,
                        mRuleRepository);
        IMENavigationMode imeNavigationMode = new IMENavigationMode(
                defaultNavigationMode, this, mDisplayManager, mFeedbackManager,
                mSelfBrailleManager, mTranslatorManager);
        BrailleIME.setSingletonHost(imeNavigationMode);
        mModeSwitcher = new ModeSwitcher(
            imeNavigationMode,
            new TreeDebugNavigationMode(
                mDisplayManager,
                mFeedbackManager,
                this));
        mModeSwitcher.onActivate();

        // Create separate SearchNavigationMode.
        mSearchNavigationMode = new SearchNavigationMode(
            mDisplayManager,
            this /*accessibilityService*/,
            mFeedbackManager,
            mTranslatorManager,
            mSelfBrailleManager,
            mNodeBrailler,
            this /*searchStateListener*/,
            mLabelManager);

        // Create separate BrailleMenuNavigationMode.
        mBrailleMenuNavigationMode = new BrailleMenuNavigationMode(
            mDisplayManager,
            this /*accessibilityService*/,
            mFeedbackManager,
            mLabelManager,
            this /*brailleMenuListener*/);
    }

    private void shutdownDependencies() {
        if (mDisplayManager != null) {
            mDisplayManager.shutdown();
            mDisplayManager = null;
        }
        if (mTranslatorManager != null) {
            mTranslatorManager.shutdown();
            mTranslatorManager = null;
        }
        // TODO: Shut down feedback manager and braille translator
        // when those classes have shutdown methods.
        if (mIMEHelper != null) {
            mIMEHelper.destroy();
            mIMEHelper = null;
        }
        if (mFocusTracker != null) {
            mFocusTracker.unregister();
            mFocusTracker = null;
        }
        if (mLabelManager != null) {
            mLabelManager.shutdown();
            mLabelManager = null;
        }
        if (mPackageReceiver != null) {
            unregisterReceiver(mPackageReceiver);
            mPackageReceiver = null;
        }

        BrailleIME.setSingletonHost(null);
    }

    public boolean runHelp() {
        Intent intent = new Intent(this, KeyBindingsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        return true;
    }

    public static BrailleBackService getActiveInstance() {
        return sInstance;
    }

    public CustomLabelManager getLabelManager() {
        return mLabelManager;
    }

    /**
     * Signal that this node, or one of its descendants, has been changed in a
     * way that could affect how it is displayed.  If the node is contributing
     * to the content of the display, the display content will be updated
     * accordingly.
     */
    public void invalidateNode(AccessibilityNodeInfo node) {
        AccessibilityNodeInfoCompat wrapped =
                new AccessibilityNodeInfoCompat(node);
        mModeSwitcher.onInvalidateAccessibilityNode(wrapped);
    }
}

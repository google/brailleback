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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.Spanned;
import android.util.Log;
import com.googlecode.eyesfree.braille.display.BrailleInputEvent;
import com.googlecode.eyesfree.braille.display.Display;
import com.googlecode.eyesfree.braille.display.DisplayClient;
import com.googlecode.eyesfree.braille.translate.BrailleTranslator;
import com.googlecode.eyesfree.braille.translate.TranslationResult;
import com.googlecode.eyesfree.brailleback.wrapping.SimpleWrapStrategy;
import com.googlecode.eyesfree.brailleback.wrapping.WordWrapStrategy;
import com.googlecode.eyesfree.brailleback.wrapping.WrapStrategy;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;
import com.googlecode.eyesfree.utils.LogUtils;
import com.googlecode.eyesfree.utils.SharedPreferencesUtils;
import java.util.Arrays;
import java.util.Comparator;

/** Keeps track of the current display content and handles panning. */
public class DisplayManager
    implements Display.OnConnectionStateChangeListener,
        Display.OnInputEventListener,
        TranslatorManager.OnTablesChangedListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

  /** Dot pattern used to overlay characters under a selection. */
  // TODO: Make customizable.
  private static final int SELECTION_DOTS = 0xC0;
  /** Dot pattern used to overlay characters in a focused element. */
  // TODO: Make customizable.
  private static final int FOCUS_DOTS = 0xC0;

  private static final long BLINK_OFF_MILLIS = 800;
  private static final long BLINK_ON_MILLIS = 600;

  /**
   * Callback interface for notifying interested callers when the display is panned out of the
   * available content. A typical reaction to such an event would be to move focus to a different
   * area of the screen and display it.
   */
  public interface OnPanOverflowListener {
    void onPanLeftOverflow(Content content);

    void onPanRightOverflow(Content content);
  }

  /**
   * Listener for input events that also get information about the current display content and
   * position mapping for commands with a positional argument.
   */
  public interface OnMappedInputEventListener {
    /**
     * Handles an input {@code event} that was received when {@code content} was present on the
     * display.
     *
     * <p>If the input event has a positional argument, it is mapped according to the display pan
     * position in the content so that it corresponds to the character that the user touched.
     *
     * <p>{@code event} and {@code content} are owned by the caller and may not be referenced after
     * this method returns.
     *
     * <p>NOTE: Since the display is updated asynchronously, there is a chance that the actual
     * content on the display when the user invoked the command is different from {@code content}.
     */
    void onMappedInputEvent(BrailleInputEvent event, Content content);
  }

  /**
   * Builder-like class used to construct the content to put on the display.
   *
   * <p>This object contains a {@link CharSequence} that represents what characters to put on the
   * display. This sequence can be a {@link Spannable} so that the characters can be annotated with
   * information about cursors and focus which will affect how the content is presented on the
   * display. Arbitrary java objects may also be included in the {@link Spannable} which can be used
   * to determine what action to take when the user invokes key commands related to a particular
   * position on the display (i.e. involving a cursor routing key). In particular, {@link
   * AccessibilityNodeInfoCompat}s may be included, in which case they will be recycled by the
   * {@link Content#recycle} method. To facilitate movement outside the bounds of the current {@link
   * Content}, {@link AccessibilityNodeInfoCompat}s that represent the extent of the content can
   * also be added, but in that case, they are not included in the {@link Spannable}.
   */
  public static class Content {
    /**
     * Pan strategy that moves the display to the leftmost position. This is the default panning
     * strategy.
     */
    public static final int PAN_RESET = 0;

    /**
     * Pan strategy that positions the display so that it overlaps the start of a selection or focus
     * mark. Falls back on {@code PAN_RESET} if there is no selection or focus.
     */
    public static final int PAN_CURSOR = 1;

    /**
     * Pan strategy that tries to position the display close to the position that corresponds to the
     * panning position in the previously displayed content. Spans of type {@link
     * AccessibilityNodeInfoCompat} are used to identify the corresponding content in the old and
     * new display content. Falls back on {@code SPAN_CURSOR} if a corresponding position can't be
     * found.
     */
    public static final int PAN_KEEP = 2;

    /**
     * Default contraction behaviour, allow contractions unless there is a selection span in the
     * content.
     */
    public static final int CONTRACT_DEFAULT = 0;

    /** Allow contraction, regardless of the presence of a selection span. */
    public static final int CONTRACT_ALWAYS_ALLOW = 1;

    private CharSequence text;
    private AccessibilityNodeInfoCompat firstNode;
    private AccessibilityNodeInfoCompat lastNode;
    private int panStrategy;
    private int contractionMode;
    private boolean splitParagraphs;
    private boolean editable = false;

    public Content() {}

    /** Shortcut to just set text for a one-off use. */
    public Content(CharSequence textArg) {
      text = textArg;
    }

    public Content setText(CharSequence textArg) {
      text = textArg;
      return this;
    }

    public CharSequence getText() {
      return text;
    }

    public Spanned getSpanned() {
      if (text instanceof Spanned) {
        return (Spanned) text;
      }
      return null;
    }

    public Content setFirstNode(AccessibilityNodeInfoCompat node) {
      AccessibilityNodeInfoUtils.recycleNodes(firstNode);
      firstNode = AccessibilityNodeInfoCompat.obtain(node);
      return this;
    }

    public AccessibilityNodeInfoCompat getFirstNode() {
      return firstNode;
    }

    public Content setLastNode(AccessibilityNodeInfoCompat node) {
      AccessibilityNodeInfoUtils.recycleNodes(lastNode);
      lastNode = AccessibilityNodeInfoCompat.obtain(node);
      return this;
    }

    public AccessibilityNodeInfoCompat getLastNode() {
      return lastNode;
    }

    public Content setPanStrategy(int strategy) {
      panStrategy = strategy;
      return this;
    }

    public int getPanStrategy() {
      return panStrategy;
    }

    public Content setContractionMode(int mode) {
      contractionMode = mode;
      return this;
    }

    public int getContractionMode() {
      return contractionMode;
    }

    public Content setSplitParagraphs(boolean value) {
      splitParagraphs = value;
      return this;
    }

    public boolean isSplitParagraphs() {
      return splitParagraphs;
    }

    public Content setEditable(boolean value) {
      editable = value;
      return this;
    }

    public boolean isEditable() {
      return editable;
    }

    /**
     * Translates the text content, preserving any verbatim braille that is embedded in a
     * BrailleSpan. The current implementation of this method only handles the first BrailleSpan;
     * all subsequent BrailleSpans are ignored.
     *
     * @param translator The translator used for translating the subparts of the text without
     *     embedded BrailleSpans.
     * @param cursorPosition The position of the cursor; if it occurs in a section of the text
     *     without BrailleSpans, then the final cursor position in the output braille by the
     *     translator. Otherwise, if the cursor occurs within a BrailleSpan section, the final
     *     cursor position in the output braille is set to the first braille cell of the
     *     BrailleSpan.
     * @param computerBrailleAtCursor This parameter is passed through to the translator; if
     *     true,then contracted translators are instructed to translate the word under the cursor
     *     using computer braille (instead of contracted braille) to make editing easier.
     * @return The result of translation, possibly empty, not null.
     */
    public TranslationResult translateWithVerbatimBraille(
        BrailleTranslator translator, int cursorPosition, boolean computerBrailleAtCursor) {
      if (translator == null) {
        return createEmptyTranslation(text);
      }

      // Assume that we have at most one BrailleSpan since we currently
      // never add more than one BrailleSpan.
      // Also ignore BrailleSpans with zero-length span or no braille for
      // now because we don't currently add such BrailleSpans.
      DisplaySpans.BrailleSpan brailleSpan = null;
      int start = -1;
      int end = -1;
      if (text instanceof Spanned) {
        Spanned spanned = (Spanned) text;
        DisplaySpans.BrailleSpan[] spans =
            spanned.getSpans(0, spanned.length(), DisplaySpans.BrailleSpan.class);
        if (spans.length > 1) {
          LogUtils.log(this, Log.WARN, "More than one BrailleSpan, handling first only");
        }
        if (spans.length != 0) {
          int spanStart = spanned.getSpanStart(spans[0]);
          int spanEnd = spanned.getSpanEnd(spans[0]);
          if (spans[0].braille != null && spans[0].braille.length != 0 && spanStart < spanEnd) {
            brailleSpan = spans[0];
            start = spanStart;
            end = spanEnd;
          }
        }
      }

      if (brailleSpan != null) {
        // Chunk the text into three sections:
        // left: [0, start) - needs translation
        // mid: [start, end) - use the literal braille provided
        // right: [end, length) - needs translation
        CharSequence left = text.subSequence(0, start);
        TranslationResult leftTrans =
            translator.translate(
                left.toString(),
                cursorPosition < start ? cursorPosition : -1,
                cursorPosition < start && computerBrailleAtCursor);

        CharSequence right = text.subSequence(end, text.length());
        TranslationResult rightTrans =
            translator.translate(
                right.toString(),
                cursorPosition >= end ? cursorPosition - end : -1,
                cursorPosition >= end && computerBrailleAtCursor);

        // If one of the left or right translations is not valid, then
        // we will fall back by ignoring the BrailleSpan and
        // translating everything normally. (Chances are that
        // translating the whole text will fail also, but it wouldn't
        // hurt to try.)
        if (leftTrans == null || rightTrans == null) {
          LogUtils.log(
              this,
              Log.ERROR,
              "Could not translate left or right subtranslation, "
                  + "falling back on default translation");
          return translateOrDefault(translator, cursorPosition, computerBrailleAtCursor);
        }

        int startBraille = leftTrans.getCells().length;
        int endBraille = startBraille + brailleSpan.braille.length;
        int totalBraille = endBraille + rightTrans.getCells().length;

        // Copy braille cells.
        byte[] cells = new byte[totalBraille];
        System.arraycopy(leftTrans.getCells(), 0, cells, 0, leftTrans.getCells().length);
        System.arraycopy(brailleSpan.braille, 0, cells, startBraille, brailleSpan.braille.length);
        System.arraycopy(rightTrans.getCells(), 0, cells, endBraille, rightTrans.getCells().length);

        // Copy text-to-braille indices.
        int[] leftTtb = leftTrans.getTextToBraillePositions();
        int[] rightTtb = rightTrans.getTextToBraillePositions();
        int[] textToBraille = new int[text.length()];

        System.arraycopy(leftTtb, 0, textToBraille, 0, start);
        for (int i = start; i < end; ++i) {
          textToBraille[i] = startBraille;
        }
        for (int i = end; i < textToBraille.length; ++i) {
          textToBraille[i] = endBraille + rightTtb[i - end];
        }

        // Copy braille-to-text indices.
        int[] leftBtt = leftTrans.getBrailleToTextPositions();
        int[] rightBtt = rightTrans.getBrailleToTextPositions();
        int[] brailleToText = new int[cells.length];

        System.arraycopy(leftBtt, 0, brailleToText, 0, startBraille);
        for (int i = startBraille; i < endBraille; ++i) {
          brailleToText[i] = start;
        }
        for (int i = endBraille; i < totalBraille; ++i) {
          brailleToText[i] = end + rightBtt[i - endBraille];
        }

        // Get cursor.
        int cursor;
        if (cursorPosition < 0) {
          cursor = -1;
        } else if (cursorPosition < start) {
          cursor = leftTrans.getCursorPosition();
        } else if (cursorPosition < end) {
          cursor = startBraille;
        } else {
          cursor = endBraille + rightTrans.getCursorPosition();
        }

        return new TranslationResult(cells, textToBraille, brailleToText, cursor);
      }

      return translateOrDefault(translator, cursorPosition, computerBrailleAtCursor);
    }

    private TranslationResult translateOrDefault(
        @NonNull BrailleTranslator translator,
        int cursorPosition,
        boolean computerBrailleAtCursor) {
      TranslationResult translation =
          translator.translate(text.toString(), cursorPosition, computerBrailleAtCursor);
      if (translation != null) {
        return translation;
      }

      return createEmptyTranslation(text);
    }

    public void recycle() {
      AccessibilityNodeInfoUtils.recycleNodes(firstNode, lastNode);
      firstNode = lastNode = null;
      DisplaySpans.recycleSpans(text);
      text = null;
    }

    @Override
    public String toString() {
      return String.format("DisplayManager.Content {text=%s}", getText());
    }
  }

  private final TranslatorManager translatorManager;
  private final BrailleBackService context;
  // Not final, because it is initialized in the handler thread.
  private Display display;
  private final OnPanOverflowListener panOverflowListener;
  private final Display.OnConnectionStateChangeListener connectionStateChangeListener;
  private final OnMappedInputEventListener mappedInputEventListener;
  private final DisplayHandler displayHandler;
  private final CallbackHandler callbackHandler;
  private final HandlerThread handlerThread;
  private final PowerManager.WakeLock wakeLock;
  private final SharedPreferences sharedPreferences;

  // Read and written in display handler thread only.

  private boolean connected = false;
  private volatile boolean isSimulatedDisplay = false;
  /**
   * Cursor position last passed to the translate method of the translator. We use this because it
   * is more reliable than the position maps inside contracted words. In the common case where there
   * is just one selection/focus on the display at the same time, this gives better results.
   * Otherwise, we fall back on the position map, whic is also used for keeping the pan position.
   */
  private int cursorPositionToTranslate = 0;

  private TranslationResult currentTranslationResult = createEmptyTranslation(null);
  /** Display content without overlays for cursors, focus etc. */
  private byte[] brailleContent = new byte[0];
  /** Braille content, potentially with dots overlaid for cursors and focus. */
  private byte[] overlaidBrailleContent = brailleContent;

  private boolean overlaysOn;
  private WrapStrategy wrapStrategy;
  private final WrapStrategy editingWrapStrategy = new SimpleWrapStrategy();
  private WrapStrategy preferredWrapStrategy = new SimpleWrapStrategy();
  private Content currentContent = new Content("");

  // Displayed content, already trimmed based on the display position.
  // Updated in updateDisplayedContent() and used in refresh().
  private byte[] displayedBraille = new byte[0];
  private byte[] displayedOverlaidBraille = new byte[0];
  private CharSequence displayedText = "";
  private int[] displayedBrailleToTextPositions = new int[0];
  private boolean blinkNeeded = false;

  /**
   * Creates an instance of this class and starts the internal thread to connect to the braille
   * display service. {@code contextArg} is used to connect to the display service. {@code
   * translator} is used for braille translation. The various listeners will be called as
   * appropriate and on the same thread that was used to create this object. The current thread must
   * have a prepared looper.
   */
  public DisplayManager(
      TranslatorManager translatorManagerArg,
      BrailleBackService contextArg,
      OnPanOverflowListener panOverflowListenerArg,
      Display.OnConnectionStateChangeListener connectionStateChangeListenerArg,
      OnMappedInputEventListener mappedInputEventListenerArg) {
    translatorManager = translatorManagerArg;
    translatorManager.addOnTablesChangedListener(this);
    context = contextArg;
    panOverflowListener = panOverflowListenerArg;
    connectionStateChangeListener = connectionStateChangeListenerArg;
    mappedInputEventListener = mappedInputEventListenerArg;
    PowerManager pm = (PowerManager) contextArg.getSystemService(Context.POWER_SERVICE);
    wakeLock =
        pm.newWakeLock(
            PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "BrailleBack");
    handlerThread =
        new HandlerThread("DisplayManager") {
          @Override
          public void onLooperPrepared() {
            display = new OverlayDisplay(context, new DisplayClient(context));
            display.setOnConnectionStateChangeListener(DisplayManager.this);
            display.setOnInputEventListener(DisplayManager.this);
          }
        };
    handlerThread.start();
    displayHandler = new DisplayHandler(handlerThread.getLooper());
    callbackHandler = new CallbackHandler();

    sharedPreferences = PreferenceManager.getDefaultSharedPreferences(contextArg);
    sharedPreferences.registerOnSharedPreferenceChangeListener(this);
    updateWrapStrategyFromPreferences();
  }

  public void shutdown() {
    sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
    displayHandler.stop();
    // Block on display shutdown. We need to make sure this finishes before
    // we can consider DisplayManager to be shut down.
    try {
      handlerThread.join(1000 /*milis*/);
    } catch (InterruptedException e) {
      LogUtils.log(this, Log.WARN, "Display handler shutdown interrupted");
    }
    translatorManager.removeOnTablesChangedListener(this);
  }

  /**
   * Asynchronously updates the display to reflect {@code content}. {@code content} must not be
   * modified after this function is called, and will eventually be recycled by the display manager.
   */
  public void setContent(Content content) {
    if (content == null) {
      throw new NullPointerException("content can't be null");
    }
    if (content.text == null) {
      throw new NullPointerException("content text is null");
    }
    displayHandler.setContent(content);
  }

  /** Returns true if the current display is simulated. */
  public boolean isSimulatedDisplay() {
    return isSimulatedDisplay;
  }

  /**
   * Marks selection spans in the overlaid braille, and returns the position in braille where the
   * first selection begins. If there are no selection spans, returns -1.
   */
  private int markSelection(Spanned spanned) {
    DisplaySpans.SelectionSpan[] spans =
        spanned.getSpans(0, spanned.length(), DisplaySpans.SelectionSpan.class);
    int selectionStart = -1;
    for (DisplaySpans.SelectionSpan span : spans) {
      int start =
          textToDisplayPosition(
              currentTranslationResult, cursorPositionToTranslate, spanned.getSpanStart(span));
      int end =
          textToDisplayPosition(
              currentTranslationResult, cursorPositionToTranslate, spanned.getSpanEnd(span));
      if (start == -1 || end == -1) {
        return -1;
      }
      if (start == end) {
        end = start + 1;
      }
      if (end > brailleContent.length) {
        extendContentForCursor();
      }
      copyOverlaidContent();
      for (int i = start; i < end && i < overlaidBrailleContent.length; ++i) {
        overlaidBrailleContent[i] |= (byte) SELECTION_DOTS;
      }
      if (selectionStart == -1) {
        selectionStart = start;
      }
    }
    return selectionStart;
  }

  /** Makes sure that the overlaid content has its own copy. Call before adding overlay dots. */
  private void copyOverlaidContent() {
    if (overlaidBrailleContent == brailleContent) {
      overlaidBrailleContent = brailleContent.clone();
    }
  }

  private void extendContentForCursor() {
    brailleContent = Arrays.copyOf(brailleContent, brailleContent.length + 1);
    // Always create a new copy of the overlaid content because there will
    // be a cursor, so we will need a copy anyway.
    overlaidBrailleContent =
        Arrays.copyOf(overlaidBrailleContent, overlaidBrailleContent.length + 1);
  }

  /**
   * Marks focus spans in the overlaid braille, and returns the position in braille where the first
   * focus begins. If there are no focus spans, returns -1.
   */
  private int markFocus(Spanned spanned) {
    DisplaySpans.FocusSpan[] spans =
        spanned.getSpans(0, spanned.length(), DisplaySpans.FocusSpan.class);
    int focusStart = -1;
    for (DisplaySpans.FocusSpan span : spans) {
      int start =
          textToDisplayPosition(
              currentTranslationResult, cursorPositionToTranslate, spanned.getSpanStart(span));
      if (start >= 0 && start < overlaidBrailleContent.length) {
        copyOverlaidContent();
        overlaidBrailleContent[start] |= (byte) FOCUS_DOTS;
        if (focusStart == -1) {
          focusStart = start;
        }
      }
    }
    return focusStart;
  }

  @Override
  public void onConnectionStateChanged(int state) {
    if (state == Display.STATE_CONNECTED) {
      connected = true;
      displayHandler.retranslate();
    } else {
      connected = false;
    }
    isSimulatedDisplay = display.isSimulated();
    callbackHandler.onConnectionStateChanged(state);
  }

  @Override
  public void onInputEvent(BrailleInputEvent event) {
    keepAwake();
    LogUtils.log(this, Log.VERBOSE, "InputEvent: %s", event);
    // We're called from within the handler thread, so we forward
    // the call only if we are going to invoke the user's callback.
    switch (event.getCommand()) {
      case BrailleInputEvent.CMD_NAV_PAN_LEFT:
        panLeft();
        break;
      case BrailleInputEvent.CMD_NAV_PAN_RIGHT:
        panRight();
        break;
      default:
        sendMappedEvent(event);
        break;
    }
  }

  @Override
  public void onTablesChanged() {
    displayHandler.retranslate();
  }

  private void sendMappedEvent(BrailleInputEvent event) {
    if (BrailleInputEvent.argumentType(event.getCommand()) == BrailleInputEvent.ARGUMENT_POSITION) {
      int oldArgument = event.getArgument();
      // Offset argument by pan position and make sure it is less than
      // the next split position.
      int offsetArgument = oldArgument + wrapStrategy.getDisplayStart();
      if (offsetArgument >= wrapStrategy.getDisplayEnd()) {
        // The event is outisde the currently displayed
        // content, drop the event.
        return;
      }
      // The mapped event argument is the translated offset argument.
      int newArgument =
          displayToTextPosition(
              currentTranslationResult, cursorPositionToTranslate, offsetArgument);
      // Create a new event if the argument actually differs.
      if (newArgument != oldArgument) {
        event = new BrailleInputEvent(event.getCommand(), newArgument, event.getEventTime());
      }
    }
    callbackHandler.onMappedInputEvent(event);
  }

  private void panLeft() {
    if (wrapStrategy.panLeft()) {
      updateDisplayedContent();
    } else {
      callbackHandler.onPanLeftOverflow();
    }
  }

  private void panRight() {
    if (wrapStrategy.panRight()) {
      updateDisplayedContent();
    } else {
      callbackHandler.onPanRightOverflow();
    }
  }

  private class DisplayHandler extends Handler {
    private static final int MSG_SET_CONTENT = 1;
    private static final int MSG_RETRANSLATE = 2;
    private static final int MSG_PULSE = 3;
    private static final int MSG_STOP = 4;

    public DisplayHandler(Looper looper) {
      super(looper);
    }

    public void setContent(Content content) {
      obtainMessage(MSG_SET_CONTENT, content).sendToTarget();
    }

    public void retranslate() {
      sendEmptyMessage(MSG_RETRANSLATE);
    }

    public void schedulePulse() {
      if (hasMessages(MSG_PULSE)) {
        return;
      }
      sendEmptyMessageDelayed(MSG_PULSE, overlaysOn ? BLINK_ON_MILLIS : BLINK_OFF_MILLIS);
    }

    public void cancelPulse() {
      removeMessages(MSG_PULSE);
      overlaysOn = true;
    }

    public void stop() {
      sendEmptyMessage(MSG_STOP);
    }

    @Override
    public void handleMessage(Message msg) {
      switch (msg.what) {
        case MSG_SET_CONTENT:
          handleSetContent((Content) msg.obj);
          break;
        case MSG_RETRANSLATE:
          handleRetranslate();
          break;
        case MSG_PULSE:
          handlePulse();
          break;
        case MSG_STOP:
          handleStop();
          break;
        default:
          // Fall out.
      }
    }

    private void handleSetContent(Content content) {
      Content oldContent = currentContent;
      currentContent = content;
      updateWrapStrategy();

      cursorPositionToTranslate = findCursorPosition(content);
      TranslationResult oldTranslationResult = currentTranslationResult;
      int oldDisplayStart = wrapStrategy.getDisplayStart();
      translateCurrentContent();
      cancelPulse();
      // Adjust the pan position according to the panning strategy.
      // Setting the position to -1 below means that the cursor position
      // returned by markCursor() will be used instead; if the pan
      // position is >= 0, then the cursor position will be ignored.
      // If the pan position is -1 and the cursor position is also -1
      // (no cursor), then the wrap strategy will reset the display to the
      // beginning of the line.
      int panPosition = -1;
      switch (content.panStrategy) {
        case Content.PAN_RESET:
          panPosition = 0;
          break;
        case Content.PAN_KEEP:
          if (oldContent != null) {
            // We don't align the display position to the size of
            // the display in this case so that content doesn't
            // jump around on the dipslay if content before the
            // current display position changes size.
            panPosition =
                findMatchingPanPosition(
                    oldContent,
                    content,
                    oldTranslationResult,
                    currentTranslationResult,
                    oldDisplayStart);
          }
          break;
        case Content.PAN_CURSOR:
          break;
        default:
          LogUtils.log(this, Log.ERROR, "Unknown pan strategy: %d", content.panStrategy);
      }
      int cursorPosition = markCursor();
      if (panPosition >= 0) {
        wrapStrategy.panTo(panPosition, false);
      } else {
        wrapStrategy.panTo(cursorPosition, true);
      }
      updateDisplayedContent();
      if (oldContent != null) {
        // Have the callback handler recycle the old content so that
        // the thread in which the callbck handler is running is the
        // only thread modifying it.  It is safe for the callback
        // thread to recycle the event when it receives this message
        // because the display handler thread will not send any more
        // input event containing this content and the events that
        // have already been sent will be processed by trhe callback
        // thread before the recycle message arrives because of the
        // guaranteed ordering of message handling.
        callbackHandler.recycleContent(oldContent);
      }
    }

    private void handleRetranslate() {
      if (currentContent == null) {
        return;
      }
      int oldTextPosition =
          displayToTextPosition(
              currentTranslationResult, cursorPositionToTranslate, wrapStrategy.getDisplayStart());
      translateCurrentContent();
      int panPosition =
          textToDisplayPosition(
              currentTranslationResult, cursorPositionToTranslate, oldTextPosition);
      int cursorPosition = markCursor();
      if (panPosition >= 0) {
        wrapStrategy.panTo(panPosition, false);
      } else {
        wrapStrategy.panTo(cursorPosition, true);
      }
      cancelPulse();
      updateDisplayedContent();
    }

    private void handlePulse() {
      overlaysOn = !overlaysOn;
      refresh();
    }

    private void handleStop() {
      display.shutdown();
      handlerThread.quit();
    }
  }

  private static class OnMappedInputEventArgs {
    public BrailleInputEvent event;
    public Content content;

    public OnMappedInputEventArgs(BrailleInputEvent eventArg, Content contentArg) {
      event = eventArg;
      content = contentArg;
    }
  }

  private class CallbackHandler extends Handler {
    private static final int MSG_ON_CONNECTION_STATE_CHANGED = 1;
    private static final int MSG_ON_MAPPED_INPUT_EVENT = 2;
    private static final int MSG_ON_PAN_LEFT_OVERFLOW = 3;
    private static final int MSG_ON_PAN_RIGHT_OVERFLOW = 4;
    private static final int MSG_RECYCLE_CONTENT = 5;

    public void onConnectionStateChanged(int state) {
      obtainMessage(MSG_ON_CONNECTION_STATE_CHANGED, state, 0).sendToTarget();
    }

    public void onMappedInputEvent(BrailleInputEvent event) {
      OnMappedInputEventArgs args = new OnMappedInputEventArgs(event, currentContent);
      obtainMessage(MSG_ON_MAPPED_INPUT_EVENT, args).sendToTarget();
    }

    public void onPanLeftOverflow() {
      obtainMessage(MSG_ON_PAN_LEFT_OVERFLOW, currentContent).sendToTarget();
    }

    public void onPanRightOverflow() {
      obtainMessage(MSG_ON_PAN_RIGHT_OVERFLOW, currentContent).sendToTarget();
    }

    public void recycleContent(Content content) {
      obtainMessage(MSG_RECYCLE_CONTENT, content).sendToTarget();
    }

    @Override
    public void handleMessage(Message msg) {
      switch (msg.what) {
        case MSG_ON_CONNECTION_STATE_CHANGED:
          handleOnConnectionStateChanged(msg.arg1);
          break;
        case MSG_ON_MAPPED_INPUT_EVENT:
          OnMappedInputEventArgs args = (OnMappedInputEventArgs) msg.obj;
          handleOnMappedInputEvent(args.event, args.content);
          break;
        case MSG_ON_PAN_LEFT_OVERFLOW:
          handleOnPanLeftOverflow((Content) msg.obj);
          break;
        case MSG_ON_PAN_RIGHT_OVERFLOW:
          handleOnPanRightOverflow((Content) msg.obj);
          break;
        case MSG_RECYCLE_CONTENT:
          handleRecycleContent((Content) msg.obj);
          break;
        default:
          // Fall out.
      }
    }

    private void handleOnConnectionStateChanged(int state) {
      connectionStateChangeListener.onConnectionStateChanged(state);
    }

    private void handleOnMappedInputEvent(BrailleInputEvent event, Content content) {
      mappedInputEventListener.onMappedInputEvent(event, content);
    }

    private void handleOnPanLeftOverflow(Content content) {
      panOverflowListener.onPanLeftOverflow(content);
    }

    private void handleOnPanRightOverflow(Content content) {
      panOverflowListener.onPanRightOverflow(content);
    }

    private void handleRecycleContent(Content content) {
      content.recycle();
    }
  }

  private void translateCurrentContent() {
    // Use the current translator, whether contracted or uncontracted, for
    // editing text, but instruct contracted translaters to uncontract
    // the braille for the word under the cursor.
    BrailleTranslator translator = translatorManager.getTranslator();
    currentTranslationResult =
        currentContent.translateWithVerbatimBraille(
            translator, cursorPositionToTranslate, uncontractBrailleAtCursor(currentContent));

    // Make very sure we do not call getCells() on a null translation.
    // translateWithVerbatimBraille() currently should never return null.
    if (currentTranslationResult == null) {
      LogUtils.log(this, Log.ERROR, "currentTranslationResult is null");
      currentTranslationResult = createEmptyTranslation(currentContent.getText());
    }

    wrapStrategy.setContent(currentContent, currentTranslationResult, getNumTextCells());
    brailleContent = currentTranslationResult.getCells();
    overlaidBrailleContent = brailleContent;
  }

  private static TranslationResult createEmptyTranslation(CharSequence text) {
    int textLength = (text == null) ? 0 : text.length();
    return new TranslationResult(new byte[0], new int[textLength], new int[0], 0);
  }

  /**
   * Marks the selection or focus cursor (in that priority), and returns the position in braille of
   * the selection or focus cursor if one exists. If no selection or focus cursor exists, then
   * returns -1.
   */
  private int markCursor() {
    Spanned spanned = currentContent.getSpanned();
    if (spanned != null) {
      int selectionPosition = markSelection(spanned);
      if (selectionPosition != -1) {
        return selectionPosition;
      }

      int focusPosition = markFocus(spanned);
      if (focusPosition != -1) {
        return focusPosition;
      }
    }

    return -1;
  }

  private void updateDisplayedContent() {
    if (!connected || currentContent == null) {
      return;
    }

    int displayStart = wrapStrategy.getDisplayStart();
    int displayEnd = wrapStrategy.getDisplayEnd();
    if (displayEnd < displayStart) {
      return;
    }

    // Compute equivalent text and mapping.
    int[] brailleToTextPositions = currentTranslationResult.getBrailleToTextPositions();
    int textLeft =
        displayStart >= brailleToTextPositions.length ? 0 : brailleToTextPositions[displayStart];
    int textRight =
        displayEnd >= brailleToTextPositions.length
            ? currentContent.text.length()
            : brailleToTextPositions[displayEnd];
    // TODO: Prevent out of order brailleToTextPositions.
    if (textRight < textLeft) {
      textRight = textLeft;
    }
    StringBuilder newText = new StringBuilder(currentContent.text.subSequence(textLeft, textRight));
    int[] trimmedBrailleToTextPositions = new int[displayEnd - displayStart];
    for (int i = 0; i < trimmedBrailleToTextPositions.length; i++) {
      if (displayStart + i < brailleToTextPositions.length) {
        trimmedBrailleToTextPositions[i] = brailleToTextPositions[displayStart + i] - textLeft;
      } else {
        trimmedBrailleToTextPositions[i] = newText.length();
        newText.append(' ');
      }
    }

    // Store all data needed by refresh().
    displayedBraille = Arrays.copyOfRange(brailleContent, displayStart, displayEnd);
    if (brailleContent != overlaidBrailleContent) {
      displayedOverlaidBraille =
          Arrays.copyOfRange(overlaidBrailleContent, displayStart, displayEnd);
    } else {
      displayedOverlaidBraille = displayedBraille;
    }
    displayedText = newText.toString();
    displayedBrailleToTextPositions = trimmedBrailleToTextPositions;
    blinkNeeded = blinkNeeded();

    refresh();
  }

  private void refresh() {
    if (!connected) {
      return;
    }
    byte[] toDisplay = overlaysOn ? displayedOverlaidBraille : displayedBraille;
    display.displayDots(toDisplay, displayedText, displayedBrailleToTextPositions);
    if (blinkNeeded) {
      displayHandler.schedulePulse();
    } else {
      displayHandler.cancelPulse();
    }
  }

  /** Returns {@code true} if the current display content is such that it requires blinking. */
  private boolean blinkNeeded() {
    if (brailleContent == overlaidBrailleContent) {
      return false;
    }
    int start = wrapStrategy.getDisplayStart();
    int end = wrapStrategy.getDisplayEnd();
    for (int i = start; i < end; ++i) {
      if (brailleContent[i] != overlaidBrailleContent[i]) {
        return true;
      }
    }
    return false;
  }

  /** Keeps the phone awake as if there was a 'user activity' registered by the system. */
  private void keepAwake() {
    // Acquiring the lock and immediately releasing it keesp the phone
    // awake.  We don't use aqcuire() with a timeout because it just
    // adds an unnecessary context switch.
    wakeLock.acquire();
    wakeLock.release();
  }

  /** Returns the size of the connected display, or {@code 1} if no display is connected. */
  private int getNumTextCells() {
    if (!connected) {
      return 1;
    }
    return display.getDisplayProperties().getNumTextCells();
  }

  private int findMatchingPanPosition(
      Content oldContent,
      Content newContent,
      TranslationResult oldTranslationResult,
      TranslationResult newTranslationResult,
      int oldDisplayPosition) {
    Spanned oldSpanned = oldContent.getSpanned();
    Spanned newSpanned = newContent.getSpanned();
    if (oldSpanned == null || newSpanned == null) {
      return -1;
    }
    // Map the current display start and past-the-end positions
    // to the corresponding input positions.
    int oldTextStart =
        displayToTextPosition(oldTranslationResult, -1 /*cursorPosition*/, oldDisplayPosition);
    int oldTextEnd =
        displayToTextPosition(
            oldTranslationResult, -1 /*cursorPosition*/, oldDisplayPosition + getNumTextCells());
    // Find the nodes that overlap with the display.
    AccessibilityNodeInfoCompat[] displayedNodes =
        oldSpanned.getSpans(oldTextStart, oldTextEnd, AccessibilityNodeInfoCompat.class);
    Arrays.sort(displayedNodes, new ByDistanceComparator(oldSpanned, oldTextStart));
    // Find corresponding node in new content.
    for (AccessibilityNodeInfoCompat oldNode : displayedNodes) {
      AccessibilityNodeInfoCompat newNode =
          (AccessibilityNodeInfoCompat) DisplaySpans.getEqualSpan(newSpanned, oldNode);
      if (newNode == null) {
        continue;
      }
      int oldDisplayStart =
          textToDisplayPosition(
              oldTranslationResult, -1 /*cursorPosition*/, oldSpanned.getSpanStart(oldNode));
      int newDisplayStart =
          textToDisplayPosition(
              newTranslationResult, -1 /*cursorPosition*/, newSpanned.getSpanStart(newNode));
      // TODO: If crashes happen here, return -1 when *DisplayStart == -1.
      // Offset position according to diff in node position.
      int newDisplayPosition = oldDisplayPosition + (newDisplayStart - oldDisplayStart);
      return newDisplayPosition;
    }
    return -1;
  }

  private static class ByDistanceComparator implements Comparator<AccessibilityNodeInfoCompat> {
    private final Spanned spanned;
    private final int start;

    public ByDistanceComparator(Spanned spannedArg, int startArg) {
      spanned = spannedArg;
      start = startArg;
    }

    @Override
    public int compare(AccessibilityNodeInfoCompat a, AccessibilityNodeInfoCompat b) {
      int aStart = spanned.getSpanStart(a);
      int bStart = spanned.getSpanStart(b);
      int aDist = Math.abs(start - aStart);
      int bDist = Math.abs(start - bStart);
      if (aDist != bDist) {
        return aDist - bDist;
      }
      // They are on the same distance, compare by length.
      int aLength = aStart + spanned.getSpanEnd(a);
      int bLength = bStart + spanned.getSpanEnd(b);
      return aLength - bLength;
    }
  }

  /** Returns braille character index of a text character index. May return -1. */
  private static int textToDisplayPosition(
      TranslationResult translationResult, int cursorPosition, int textPosition) {
    if (textPosition == cursorPosition) {
      return translationResult.getCursorPosition(); // May return -1?
    }
    int[] posMap = translationResult.getTextToBraillePositions(); // May include -1?
    // Any position past-the-end of the position map maps to the
    // corresponding past-the-end position in the braille.
    if (textPosition >= posMap.length) {
      return translationResult.getBrailleToTextPositions().length;
    }
    return posMap[textPosition];
  }

  private static int displayToTextPosition(
      TranslationResult translationResult, int cursorPosition, int displayPosition) {
    if (displayPosition == translationResult.getCursorPosition()) {
      return cursorPosition;
    }
    int[] posMap = translationResult.getBrailleToTextPositions();
    // Any position past-the-end of the position map maps to the
    // corresponding past-the-end position in the braille.
    if (displayPosition >= posMap.length) {
      return translationResult.getTextToBraillePositions().length;
    }
    return posMap[displayPosition];
  }

  private static int findCursorPosition(Content content) {
    Spanned spanned = content.getSpanned();
    if (spanned == null) {
      return -1;
    }
    DisplaySpans.SelectionSpan[] selectionSpans =
        spanned.getSpans(0, spanned.length(), DisplaySpans.SelectionSpan.class);
    if (selectionSpans.length > 0) {
      return spanned.getSpanStart(selectionSpans[0]);
    }
    DisplaySpans.FocusSpan[] focusSpans =
        spanned.getSpans(0, spanned.length(), DisplaySpans.FocusSpan.class);
    if (focusSpans.length > 0) {
      return spanned.getSpanStart(focusSpans[0]);
    }
    return -1;
  }

  private boolean uncontractBrailleAtCursor(Content content) {
    if (content.getContractionMode() == Content.CONTRACT_ALWAYS_ALLOW) {
      return false;
    }
    Spanned spanned = content.getSpanned();
    if (spanned == null) {
      return false;
    }
    DisplaySpans.SelectionSpan[] selectionSpans =
        spanned.getSpans(0, spanned.length(), DisplaySpans.SelectionSpan.class);
    return selectionSpans.length != 0;
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferencesArg, String s) {
    String wordWrapPrefKey = context.getString(R.string.pref_braille_word_wrap_key);
    if (s != null && s.equals(wordWrapPrefKey)) {
      updateWrapStrategyFromPreferences();
    }
  }

  private void updateWrapStrategyFromPreferences() {
    boolean wrap =
        SharedPreferencesUtils.getBooleanPref(
            sharedPreferences,
            context.getResources(),
            R.string.pref_braille_word_wrap_key,
            R.bool.pref_braille_word_wrap_default);

    preferredWrapStrategy = wrap ? new WordWrapStrategy() : new SimpleWrapStrategy();
    updateWrapStrategy();
    displayHandler.retranslate();
  }

  private void updateWrapStrategy() {
    boolean contentEditable = currentContent != null && currentContent.isEditable();
    boolean imeOpen =
        context != null
            && context.imeNavigationMode != null
            && context.imeNavigationMode.isImeOpen();
    boolean editing = contentEditable && imeOpen;
    wrapStrategy = editing ? editingWrapStrategy : preferredWrapStrategy;
  }
}

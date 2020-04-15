/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.googlecode.eyesfree.labeling;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;
import android.os.AsyncTask;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.googlecode.eyesfree.labeling.AllLabelsFetchRequest.OnAllLabelsFetchedListener;
import com.googlecode.eyesfree.labeling.DirectLabelFetchRequest.OnLabelFetchedListener;
import com.googlecode.eyesfree.labeling.PackageLabelsFetchRequest.OnLabelsFetchedListener;
import com.googlecode.eyesfree.utils.AccessibilityEventListener;
import com.googlecode.eyesfree.utils.LogUtils;
import com.googlecode.eyesfree.utils.StringBuilderUtils;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Manages logic for prefetching, retrieval, addition, updating, and removal of custom view labels
 * and their associated resources.
 *
 * <p>This class ties together an underlying label database with a LRU label cache. It provides
 * convenience methods for accessing and changing the state of labels, both persisted and in memory.
 * Methods in this class will often return nothing, and may expose asynchronous callbacks wrapped by
 * request classes to return results from processing activities on different threads.
 *
 * <p>This class also serves as an {@link AccessibilityEventListener} for purposes of automatically
 * prefetching labels into the managed cache based on the {@link AccessibilityEvent}s delivered to
 * the client application. In order for this prefetching to occur, clients should send {@link
 * AccessibilityEvent}s through {@link #onAccessibilityEvent(AccessibilityEvent)}.
 *
 * @author caseyburkhardt@google.com (Casey Burkhardt)
 */
// TODO: Most public methods in this class should support
// optional callbacks.
@TargetApi(18)
public class CustomLabelManager implements AccessibilityEventListener {
  /** The minimum API level supported by the manager. */
  public static final int MIN_API_LEVEL = Build.VERSION_CODES.JELLY_BEAN_MR2;

  /** The maximum number of package sets of labels to keep in memory. */
  private static final int MAX_CACHE_SIZE = 10;

  public static final String AUTHORITY =
      "com.google.android.marvin.talkback.providers.LabelProvider";

  /**
   * The substring separating a label's package and view ID name in a fully-qualified resource
   * identifier.
   */
  private static final Pattern RESOURCE_NAME_SPLIT_PATTERN = Pattern.compile(":id/");

  private static final IntentFilter REFRESH_INTENT_FILTER =
      new IntentFilter(LabelOperationUtils.ACTION_REFRESH_LABEL_CACHE);

  private final Map<String, Map<String, Label>> mLabelCache =
      new LruCache<String, Map<String, Label>>(MAX_CACHE_SIZE);

  private final CacheRefreshReceiver mRefreshReceiver = new CacheRefreshReceiver();

  private final Context mContext;
  private final PackageManager mPackageManager;
  private final LabelProviderClient mClient;

  private Locale mLastLocale;

  // Used to manage release of resources based on task completion
  private final Object mLock;
  private boolean mShouldShutdownClient;
  private int mRunningTasks;

  public CustomLabelManager(Context context) {
    mContext = context;
    mPackageManager = context.getPackageManager();
    mLastLocale = Locale.getDefault();
    mLock = new Object();
    mShouldShutdownClient = false;
    mRunningTasks = 0;
    mClient = new LabelProviderClient(context, AUTHORITY);
    mContext.registerReceiver(mRefreshReceiver, REFRESH_INTENT_FILTER);
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event) {
    final Locale currentLocale = Locale.getDefault();
    if (!currentLocale.equals(mLastLocale)) {
      // Refresh cache if device locale has changed since the last event
      mLastLocale = currentLocale;
      refreshCacheInternal(null);
    }

    switch (event.getEventType()) {
      case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
      case AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED:
      case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
        prefetchLabelsFromEvent(event);
    }
  }

  /**
   * Performs various tasks to ensure the underlying Label database is in a state consistent with
   * the installed applications on the device. May alter the database state by pruning labels in
   * cases where the containing applications are no longer present on the system or those
   * applications don't match stored signature data.
   *
   * <p>NOTE: This should be invoked by higher level service entities using this class on startup
   * after registering a {@link PackageRemovalReceiver}. It is generally unnecessary to invoke this
   * operation for disposable instances of this class.
   */
  public void ensureDataConsistency() {
    if (!isInitialized()) {
      return;
    }

    new DataConsistencyCheckTask().execute();
  }

  /**
   * Retrieves a {@link Label} from the label cache given a fully-qualified resource identifier
   * name.
   *
   * @param resourceName The fully-qualified resource identifier, such as
   *     "com.android.deskclock:id/analog_appwidget", as provided by {@link
   *     AccessibilityNodeInfo#getViewIdResourceName()}
   * @return The {@link Label} matching the provided identifier, or {@code null} if no such label
   *     exists or has not yet been fetched from storage
   */
  public Label getLabelForViewIdFromCache(String resourceName) {
    if (!isInitialized()) {
      return null;
    }

    Pair<String, String> parsedId = splitResourceName(resourceName);
    if (parsedId == null) {
      return null;
    }

    if (!mLabelCache.containsKey(parsedId.first)) {
      // Cache miss or no labels for package
      return null;
    }

    final Map<String, Label> packageLabels = mLabelCache.get(parsedId.first);
    return packageLabels.get(parsedId.second);
  }

  /**
   * Retrieves a {@link Label} directly through the database and returns it through a callback
   * interface.
   *
   * @param labelId The id of the label to retrieve from the database as provided by {@link
   *     Label#getId()}
   * @param callback The {@link OnLabelFetchedListener} to return the label though
   */
  public void getLabelForLabelIdFromDatabase(Long labelId, OnLabelFetchedListener callback) {
    if (!isInitialized()) {
      return;
    }

    final DirectLabelFetchRequest request = new DirectLabelFetchRequest(labelId, callback);
    final DirectLabelFetchTask task = new DirectLabelFetchTask();
    task.execute(request);
  }

  /**
   * Retrieves a {@link Map} of view ID resource names to {@link Label}s for labels in the given
   * package name and returns it through a callback interface.
   *
   * @param packageName The package name of the labels to retrieve
   * @param callback The {@link OnLabelsFetchedListener} to return the labels through
   */
  public void getLabelsForPackageFromDatabase(
      String packageName, OnLabelsFetchedListener callback) {
    if (!isInitialized()) {
      return;
    }

    final PackageLabelsFetchRequest request = new PackageLabelsFetchRequest(packageName, callback);
    final PackageLabelsFetchTask task = new PackageLabelsFetchTask();
    task.execute(request);
  }

  public void getAllLabelsFromDatabase(OnAllLabelsFetchedListener callback) {
    if (!isInitialized()) {
      return;
    }

    final AllLabelsFetchRequest request = new AllLabelsFetchRequest(callback);
    final AllLabelsFetchTask task = new AllLabelsFetchTask();
    task.execute(request);
  }

  /**
   * Creates a {@link Label} and persists it to the label database, and refreshes the label cache.
   *
   * <p>NOTE: This method will not attempt to recycle {@code node}.
   *
   * @param node The node to label
   * @param userLabel The label provided for the node by the user
   */
  public void addLabel(AccessibilityNodeInfo node, String userLabel) {
    if (node == null) {
      throw new IllegalArgumentException("Attempted to add a label for a null node.");
    }

    final AccessibilityNodeInfo internalNodeCopy = AccessibilityNodeInfo.obtain(node);
    addLabel(internalNodeCopy.getViewIdResourceName(), userLabel);
    internalNodeCopy.recycle();
  }

  /**
   * Creates a {@link Label} and persists it to the label database, and refreshes the label cache.
   *
   * @param resourceName The fully-qualified resource identifier, such as
   *     "com.android.deskclock:id/analog_appwidget", as provided by {@link
   *     AccessibilityNodeInfo#getViewIdResourceName()}
   * @param userLabel The label provided for the node by the user
   */
  public void addLabel(String resourceName, String userLabel) {
    if (!isInitialized()) {
      return;
    }

    final String finalLabel;
    if (userLabel == null) {
      throw new IllegalArgumentException("Attempted to add a label with a null userLabel value");
    } else {
      finalLabel = userLabel.trim();
      if (TextUtils.isEmpty(finalLabel)) {
        throw new IllegalArgumentException(
            "Attempted to add a label with an empty userLabel value");
      }
    }

    Pair<String, String> parsedId = splitResourceName(resourceName);
    if (parsedId == null) {
      LogUtils.log(
          this, Log.WARN, "Attempted to add a label with an invalid or poorly formed view ID.");
      return;
    }

    final PackageInfo packageInfo;
    try {
      packageInfo = mPackageManager.getPackageInfo(parsedId.first, PackageManager.GET_SIGNATURES);
    } catch (NameNotFoundException e) {
      LogUtils.log(this, Log.WARN, "Attempted to add a label for an unknown package.");
      return;
    }

    final String locale = Locale.getDefault().toString();
    final int version = packageInfo.versionCode;
    final long timestamp = System.currentTimeMillis();
    String signatureHash = "";

    final Signature[] sigs = packageInfo.signatures;
    try {
      final MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
      for (Signature s : sigs) {
        messageDigest.update(s.toByteArray());
      }

      signatureHash = StringBuilderUtils.bytesToHexString(messageDigest.digest());
    } catch (NoSuchAlgorithmException e) {
      LogUtils.log(this, Log.WARN, "Unable to create SHA-1 MessageDigest");
    }

    // For the current implementation, screenshots are disabled
    final String screenshotPath = "";

    final Label label =
        new Label(
            parsedId.first,
            signatureHash,
            parsedId.second,
            finalLabel,
            locale,
            version,
            screenshotPath,
            timestamp);
    final LabelAddRequest request = new LabelAddRequest(label, null);
    final LabelAddTask task = new LabelAddTask();
    task.execute(request);
  }

  /**
   * Updates {@link Label}s in the label database and refreshes the label cache.
   *
   * <p>NOTE: This method relies on the id field of the {@link Label}s being populated, so callers
   * must obtain fully populated objects from {@link #getLabelForViewIdFromCache(String)} or {@link
   * #getLabelForLabelIdFromDatabase(Long, OnLabelFetchedListener)} in order to update them.
   *
   * @param labels The {@link Label}s to remove
   */
  public void updateLabel(Label... labels) {
    if (!isInitialized()) {
      return;
    }

    if (labels == null || labels.length == 0) {
      LogUtils.log(this, Log.WARN, "Attempted to update a null or empty array of labels.");
      return;
    }

    for (Label l : labels) {
      if (l == null) {
        throw new IllegalArgumentException("Attempted to update a null label.");
      }

      if (TextUtils.isEmpty(l.getText())) {
        throw new IllegalArgumentException("Attempted to update a label with an empty text value");
      }

      final LabelUpdateRequest request = new LabelUpdateRequest(l, null);
      final LabelUpdateTask task = new LabelUpdateTask();
      task.execute(request);
    }
  }

  /**
   * Removes {@link Label}s from the label database and refreshes the label cache.
   *
   * <p>NOTE: This method relies on the id field of the {@link Label}s being populated, so callers
   * must obtain fully populated objects from {@link #getLabelForViewIdFromCache(String)} or {@link
   * #getLabelForLabelIdFromDatabase(Long, OnLabelFetchedListener)} in order to remove them.
   *
   * @param labels The {@link Label}s to remove
   */
  public void removeLabel(Label... labels) {
    if (!isInitialized()) {
      return;
    }

    if (labels == null || labels.length == 0) {
      LogUtils.log(this, Log.WARN, "Attempted to delete a null or empty array of labels.");
      return;
    }

    for (Label l : labels) {
      final LabelRemoveRequest request = new LabelRemoveRequest(l, null);
      final LabelRemoveTask task = new LabelRemoveTask();
      task.execute(request);
    }
  }

  /**
   * Invalidates and rebuilds the cache of labels managed by this class.
   *
   * @param packageNames specific package names to refresh, or {@code null} to refresh all existing.
   *     If a package name provided in this parameter does not exist in the cache, it will be
   *     prefetched
   */
  public void refreshCache(String... packageNames) {
    HashSet<String> packageSet = null;
    if (packageNames != null && packageNames.length > 0) {
      // Strip any duplicates
      packageSet = new HashSet<String>();
      for (String p : packageNames) {
        packageSet.add(p);
      }
    }

    refreshCacheInternal(packageSet);
  }

  /**
   * Splits a fully-qualified resource identifier name into its package and ID name.
   *
   * @param resourceName The fully-qualified resource identifier, such as
   *     "com.android.deskclock:id/analog_appwidget", as provided by {@link
   *     AccessibilityNodeInfo#getViewIdResourceName()}
   * @return A {@link Pair} where the first value is the package name and second is the id name
   */
  public static Pair<String, String> splitResourceName(String resourceName) {
    if (TextUtils.isEmpty(resourceName)) {
      return null;
    }

    final String[] splitId = RESOURCE_NAME_SPLIT_PATTERN.split(resourceName, 2);
    if (splitId.length != 2 || TextUtils.isEmpty(splitId[0]) || TextUtils.isEmpty(splitId[1])) {
      // Invalid input
      LogUtils.log(
          CustomLabelManager.class, Log.WARN, "Failed to parse resource: %s", resourceName);
      return null;
    }

    return new Pair<String, String>(splitId[0], splitId[1]);
  }

  private void refreshCacheInternal(Set<String> packageNames) {
    if (packageNames == null || packageNames.size() == 0) {
      // Since there aren't specific packages to reload, copy all the
      // keys and invalidate the entire cache.
      // TODO: Test this. We shouldn't need a deep copy,
      // but double check.
      packageNames = Collections.unmodifiableSet(mLabelCache.keySet());
      mLabelCache.clear();
    } else {
      // Since there are targeted packages to refresh, prune just those
      // from the cache.
      for (String p : packageNames) {
        mLabelCache.remove(p);
      }
    }

    // Rebuild relevant parts of the cache.
    for (String packageName : packageNames) {
      if (!TextUtils.isEmpty(packageName)) {
        prefetchLabelsForPackage(packageName);
      }
    }
  }

  /** Shuts down the manager and releases resources. */
  public void shutdown() {
    LogUtils.log(this, Log.VERBOSE, "Shutdown requested.");

    // We must immediately destroy registered receivers to prevent a leak,
    // as the context backing this registration is to be invalidated.
    mContext.unregisterReceiver(mRefreshReceiver);

    // We cannot shutdown resources related to the database until all tasks
    // have completed. Flip the flag to indicate a client of this manager
    // requested a shutdown and attempt the operation.
    mShouldShutdownClient = true;
    maybeShutdownClient();
  }

  /**
   * Returns whether the labeling client is properly initialized.
   *
   * @return {@code true} if client is ready, or {@code false} otherwise.
   */
  public boolean isInitialized() {
    return mClient.isInitialized();
  }

  /**
   * Shuts down the database resources held by an instance of this manager if certain conditions are
   * met. The database resource is released if and only if a client has requested a shutdown
   * operation and there are no asynchronous operations running. To ensure completeness, this method
   * is invoked when a client of this manager requests a shutdown and when any asynchronous
   * operation completes.
   */
  private void maybeShutdownClient() {
    synchronized (mLock) {
      if ((mRunningTasks == 0) && mShouldShutdownClient) {
        LogUtils.log(
            this, Log.VERBOSE, "All tasks completed and shutdown requested.  Releasing database.");
        mClient.shutdown();
      }
    }
  }

  /**
   * Updates the internals of the manager to track this task, keeping database resources from being
   * shutdown until all tasks complete.
   *
   * @param task The task that's starting
   */
  private void taskStarting(TrackedAsyncTask<?, ?, ?> task) {
    synchronized (mLock) {
      LogUtils.log(this, Log.VERBOSE, "Task %s starting.", task);
      mRunningTasks++;
    }
  }

  /**
   * Updates the internals of the manager to stop tracking this task. May dispose of database
   * resources if a shutdown requested by this classes's client was requested prior to {@code
   * task}'s completion.
   *
   * @param task The task that is ending
   */
  private void taskEnding(TrackedAsyncTask<?, ?, ?> task) {
    synchronized (mLock) {
      LogUtils.log(this, Log.VERBOSE, "Task %s ending.", task);
      mRunningTasks--;
    }

    maybeShutdownClient();
  }

  private void prefetchLabelsFromEvent(AccessibilityEvent event) {
    if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
      // Use TYPE_WINDOW_CONTENT_CHANGED events to trigger a search
      // through the node's children to prefetch all relevant package
      // names. This prefetches labels for remote views.
      final AccessibilityNodeInfo source = event.getSource();
      final HashSet<CharSequence> packages = new HashSet<CharSequence>();
      final LinkedList<AccessibilityNodeInfo> seenNodes = new LinkedList<AccessibilityNodeInfo>();
      seenNodes.add(source);

      // Breadth first traversal for populating the package set
      while (!seenNodes.isEmpty()) {
        final AccessibilityNodeInfo currentNode = seenNodes.removeFirst();
        if (currentNode == null) {
          continue;
        }

        final Pair<String, String> resId = splitResourceName(currentNode.getViewIdResourceName());
        if (resId != null) {
          packages.add(resId.first);
        }

        final int childCount = currentNode.getChildCount();
        for (int i = 0; i < childCount; ++i) {
          seenNodes.add(currentNode.getChild(i));
        }

        currentNode.recycle();
      }

      for (CharSequence packageName : packages) {
        prefetchLabelsForPackage(packageName.toString());
      }
    } else {
      // Other AccessibilityEvent types should use the package name from its source.
      final AccessibilityNodeInfo node = event.getSource();
      if (node != null) {
        final Pair<String, String> resId = splitResourceName(node.getViewIdResourceName());
        if (resId != null) {
          prefetchLabelsForPackage(resId.first);
        }
      }
    }
  }

  private void prefetchLabelsForPackage(final String packageName) {
    if (TextUtils.isEmpty(packageName)) {
      return;
    }

    // TODO: Is it worth optimizing further by keeping a
    // complete list of packages for which labels exist, and short
    // circuiting on the execution of the task if no labels exist for the
    // package?
    if (!mLabelCache.containsKey(packageName)) {
      final OnLabelsFetchedListener callback =
          new OnLabelsFetchedListener() {
            @Override
            public void onLabelsFetched(Map<String, Label> results) {
              if (results != null) {
                mLabelCache.put(packageName, results);
              }
            }
          };

      getLabelsForPackageFromDatabase(packageName, callback);
    }
  }

  private static String computePackageSignatureHash(PackageInfo packageInfo) {
    String signatureHash = "";

    final Signature[] sigs = packageInfo.signatures;
    try {
      final MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
      for (Signature s : sigs) {
        messageDigest.update(s.toByteArray());
      }

      signatureHash = StringBuilderUtils.bytesToHexString(messageDigest.digest());
    } catch (NoSuchAlgorithmException e) {
      LogUtils.log(CustomLabelManager.class, Log.WARN, "Unable to create SHA-1 MessageDigest");
    }

    return signatureHash;
  }

  private void sendCacheRefreshIntent(String... packageNames) {
    final Intent refreshIntent = new Intent(LabelOperationUtils.ACTION_REFRESH_LABEL_CACHE);
    refreshIntent.putExtra(LabelOperationUtils.EXTRA_STRING_ARRAY_PACKAGES, packageNames);
    mContext.sendBroadcast(refreshIntent);
  }

  private class CacheRefreshReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
      String[] packages = null;
      if (intent.hasExtra(LabelOperationUtils.EXTRA_STRING_ARRAY_PACKAGES)) {
        packages = intent.getStringArrayExtra(LabelOperationUtils.EXTRA_STRING_ARRAY_PACKAGES);
      }

      refreshCache(packages);
    }
  }

  /**
   * An AsyncTask intermediate that tracks task completion for purposes of releasing resources
   * within this manager.
   */
  private abstract class TrackedAsyncTask<Params, Progress, Result>
      extends AsyncTask<Params, Progress, Result> {

    @Override
    protected void onPreExecute() {
      taskStarting(this);
      super.onPreExecute();
    }

    /**
     * See {@link AsyncTask#onPostExecute}.
     *
     * <p>If overridden in a child class, this method should be invoked after any processing by the
     * child is complete. Failing to do so, or doing so out of order may result in failure to
     * release or premature release of resources.
     */
    @Override
    protected void onPostExecute(Result result) {
      taskEnding(this);
      super.onPostExecute(result);
    }

    @Override
    protected abstract Result doInBackground(Params... params);
  }

  private class DirectLabelFetchTask
      extends TrackedAsyncTask<DirectLabelFetchRequest, Void, Label> {

    private DirectLabelFetchRequest mRequest;

    @Override
    protected Label doInBackground(DirectLabelFetchRequest... requests) {
      if (requests == null || requests.length != 1) {
        throw new IllegalArgumentException(
            "Direct label fetch task supports only single requests.");
      }

      mRequest = requests[0];
      LogUtils.log(this, Log.VERBOSE, "Spawning new DirectLabelFetchTask(%d)", hashCode());

      final long labelId = mRequest.getLabelId();
      return mClient.getLabelById(labelId);
    }

    @Override
    protected void onPostExecute(Label result) {
      LogUtils.log(
          this,
          Log.VERBOSE,
          "DirectLabelFetchTask(%d) complete.  Obtained label %s",
          hashCode(),
          result);

      mRequest.invokeCallback(result);
      super.onPostExecute(result);
    }
  }

  private class AllLabelsFetchTask
      extends TrackedAsyncTask<AllLabelsFetchRequest, Void, List<Label>> {

    private AllLabelsFetchRequest mRequest;

    @Override
    protected List<Label> doInBackground(AllLabelsFetchRequest... requests) {
      if (requests == null || requests.length != 1) {
        throw new IllegalArgumentException("Fetch all labels task supports only single requests.");
      }

      mRequest = requests[0];

      LogUtils.log(
          this, Log.VERBOSE, "Spawning new AllLabelsFetchTask(%d) for %s", hashCode(), mRequest);

      return mClient.getAllLabels();
    }

    @Override
    protected void onPostExecute(List<Label> result) {
      LogUtils.log(this, Log.VERBOSE, "AllLabelsFetchTask(%d) complete", hashCode());
      mRequest.invokeCallback(result);
      super.onPostExecute(result);
    }
  }

  private class PackageLabelsFetchTask
      extends TrackedAsyncTask<PackageLabelsFetchRequest, Void, Map<String, Label>> {

    private PackageLabelsFetchRequest mRequest;

    @Override
    protected Map<String, Label> doInBackground(PackageLabelsFetchRequest... requests) {
      if (requests == null || requests.length != 1) {
        throw new IllegalArgumentException(
            "Fetch labels for package task supports only single package lookups.");
      }

      mRequest = requests[0];

      LogUtils.log(
          this,
          Log.VERBOSE,
          "Spawning new PackageLabelsFetchTask(%d) for %s",
          hashCode(),
          mRequest);

      int versionCode = Integer.MAX_VALUE;
      try {
        final PackageInfo packageInfo =
            mPackageManager.getPackageInfo(mRequest.getPackageName(), 0);
        versionCode = packageInfo.versionCode;
      } catch (NameNotFoundException e) {
        LogUtils.log(
            this,
            Log.WARN,
            "Unable to resolve package info during prefetch for %s",
            mRequest.getPackageName());
      }

      return mClient.getLabelsForPackage(
          mRequest.getPackageName(), Locale.getDefault().toString(), versionCode);
    }

    @Override
    protected void onPostExecute(Map<String, Label> result) {
      LogUtils.log(this, Log.VERBOSE, "LabelPrefetchTask(%d) complete", hashCode());
      mRequest.invokeCallback(result);
      super.onPostExecute(result);
    }
  }

  private class LabelAddTask extends TrackedAsyncTask<LabelAddRequest, Void, Label> {

    private LabelAddRequest mRequest;

    @Override
    protected Label doInBackground(LabelAddRequest... requests) {
      if (requests == null || requests.length != 1) {
        throw new IllegalArgumentException("Add task supports only single Label additions.");
      }

      mRequest = requests[0];
      LogUtils.log(
          this,
          Log.VERBOSE,
          "Spawning new LabelAddTask(%d) for %s",
          hashCode(),
          mRequest.getLabel());

      return mClient.insertLabel(mRequest.getLabel());
    }

    @Override
    protected void onPostExecute(Label result) {
      LogUtils.log(
          this, Log.VERBOSE, "LabelAddTask(%d) complete, stored as %s", hashCode(), result);
      mRequest.invokeCallback(result);

      if (result != null) {
        sendCacheRefreshIntent(result.getPackageName());
      }

      super.onPostExecute(result);
    }
  }

  private class LabelUpdateTask extends TrackedAsyncTask<LabelUpdateRequest, Void, Boolean> {

    private LabelUpdateRequest mRequest;

    @Override
    protected Boolean doInBackground(LabelUpdateRequest... requests) {
      if (requests == null || requests.length != 1) {
        throw new IllegalArgumentException("Update task supports only single Label updates.");
      }

      mRequest = requests[0];

      LogUtils.log(
          this,
          Log.VERBOSE,
          "Spawning new LabelUpdateTask(%d) for label: %s",
          hashCode(),
          mRequest.getLabel());

      Label label = mRequest.getLabel();
      if (label != null && label.getId() != Label.NO_ID) {
        return mClient.updateLabel(label);
      }

      return false;
    }

    @Override
    protected void onPostExecute(Boolean result) {
      LogUtils.log(
          this, Log.VERBOSE, "LabelUpdateTask(%d) complete. Result: %s", hashCode(), result);

      if (result) {
        sendCacheRefreshIntent(mRequest.getLabel().getPackageName());
      }

      super.onPostExecute(result);
    }
  }

  private class LabelRemoveTask extends TrackedAsyncTask<LabelRemoveRequest, Void, Boolean> {

    private LabelRemoveRequest mRequest;

    @Override
    protected Boolean doInBackground(LabelRemoveRequest... requests) {
      if (requests == null || requests.length != 1) {
        throw new IllegalArgumentException("Remove task supports only single Label removals.");
      }

      mRequest = requests[0];

      LogUtils.log(
          this,
          Log.VERBOSE,
          "Spawning new LabelRemoveTask(%d) for label: %s",
          hashCode(),
          mRequest.getLabel());

      final Label label = mRequest.getLabel();
      if (label != null && label.getId() != Label.NO_ID) {
        return mClient.deleteLabel(label);
      }

      return false;
    }

    @Override
    protected void onPostExecute(Boolean result) {
      LogUtils.log(
          this, Log.VERBOSE, "LabelRemoveTask(%d) complete.  Removed: %s.", hashCode(), result);

      if (result) {
        sendCacheRefreshIntent(mRequest.getLabel().getPackageName());
      }

      super.onPostExecute(result);
    }
  }

  private class DataConsistencyCheckTask extends TrackedAsyncTask<Void, Void, List<Label>> {

    @Override
    protected List<Label> doInBackground(Void... params) {
      final List<Label> allLabels = mClient.getAllLabels();

      if ((allLabels == null) || allLabels.isEmpty()) {
        return null;
      }

      final PackageManager pm = mContext.getPackageManager();

      final List<Label> candidates = new ArrayList<Label>(allLabels);
      ListIterator<Label> i = candidates.listIterator();

      // Iterate through the labels database, and prune labels that belong
      // to valid packages.
      while (i.hasNext()) {
        final Label l = i.next();

        // Ensure the label has a matching installed package.
        final String packageName = l.getPackageName();
        PackageInfo packageInfo = null;
        try {
          packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
        } catch (NameNotFoundException e) {
          // If there's no installed package, leave the label in the
          // list for removal.
          LogUtils.log(
              CustomLabelManager.class,
              Log.VERBOSE,
              "Consistency check removing label for unknown package %s.",
              packageName);
          continue;
        }

        // Ensure the signature hash of the application matches
        // the hash of the package when the label was stored.
        final String expectedHash = l.getPackageSignature();
        final String actualHash = computePackageSignatureHash(packageInfo);
        if (TextUtils.isEmpty(expectedHash)
            || TextUtils.isEmpty(actualHash)
            || !expectedHash.equals(actualHash)) {
          // If the expected or actual signature hashes aren't
          // valid, or they don't match, leave the label in the list
          // for removal.
          LogUtils.log(
              CustomLabelManager.class,
              Log.WARN,
              "Consistency check removing label due to signature mismatch " + "for package %s.",
              packageName);
          continue;
        }

        // If the label has passed all consistency checks, prune the
        // label from the list of potentials for removal.
        i.remove();
      }

      return candidates; // now containing only labels for removal
    }

    @Override
    protected void onPostExecute(List<Label> labelsToRemove) {
      if (labelsToRemove == null || labelsToRemove.isEmpty()) {
        return;
      }

      LogUtils.log(
          this,
          Log.VERBOSE,
          "Found %d labels to remove during consistency check",
          labelsToRemove.size());
      for (Label l : labelsToRemove) {
        removeLabel(l);
      }

      super.onPostExecute(labelsToRemove);
    }
  }
}

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
import android.content.DialogInterface;
import android.os.Build;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import com.googlecode.eyesfree.widget.SimpleOverlay;

/** Controls the view that shows search overlay content. */
public class SearchOverlay extends SimpleOverlay implements DialogInterface {

  /** The search view. */
  private SearchView mSearchView;

  /** Creates the overlay with it initially invisible. */
  public SearchOverlay(Context context, StringBuilder queryText) {
    super(context);

    mSearchView = new SearchView(context, queryText);

    // Make overlay appear on everything it can.
    LayoutParams params = getParams();
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
      params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ERROR;
    } else {
      params.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
    }
    setParams(params);

    setContentView(mSearchView);
  }

  /** Called when the the overlay is shown. */
  @Override
  protected void onShow() {
    mSearchView.show();
  }

  /** Refreshes the overlaid text display. */
  public void refreshOverlay() {
    mSearchView.invalidate();
  }

  @Override
  public void cancel() {
    dismiss();
  }

  @Override
  public void dismiss() {
    // This also effectively hides the search view.
    hide();
  }
}

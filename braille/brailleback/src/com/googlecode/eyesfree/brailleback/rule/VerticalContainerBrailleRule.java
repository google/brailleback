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

package com.googlecode.eyesfree.brailleback.rule;

import com.googlecode.eyesfree.brailleback.R;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;

import android.content.Context;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.Editable;
import android.widget.AbsListView;
import android.widget.GridView;
import android.widget.ScrollView;

/**
 * Rule for formatting certain large vertical containers (such as lists
 * and grids).  A generic description of the view is added and child nodes are not
 * included.
 */
class VerticalContainerBrailleRule implements BrailleRule {
    @Override
    public boolean accept(Context context, AccessibilityNodeInfoCompat node) {
        return AccessibilityNodeInfoUtils.nodeMatchesAnyClassByType(
            context, node,
            AbsListView.class,
            ScrollView.class);
    }

    @Override
    public void format(Editable result,
            Context context,
            AccessibilityNodeInfoCompat node) {
        boolean empty = (node.getChildCount() == 0);
        int res;
        if (AccessibilityNodeInfoUtils.nodeMatchesClassByType(context, node,
                        GridView.class)) {
            res = empty ? R.string.type_emptygridview : R.string.type_gridview;
        } else if (AccessibilityNodeInfoUtils.nodeMatchesClassByType(
            context, node, ScrollView.class)) {
            res = empty ? R.string.type_emptyscrollview
                    : R.string.type_scrollview;
        } else {
            res = empty ? R.string.type_emptylistview : R.string.type_listview;
        }
        result.append(context.getString(res));
    }

    public boolean includeChildren(AccessibilityNodeInfoCompat node,
            Context context) {
        return false;
    }
}

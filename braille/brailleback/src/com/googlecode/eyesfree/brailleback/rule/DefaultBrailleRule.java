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

import com.googlecode.eyesfree.brailleback.BrailleBackService;
import com.googlecode.eyesfree.brailleback.FocusFinder;
import com.googlecode.eyesfree.brailleback.R;
import com.googlecode.eyesfree.brailleback.utils.LabelingUtils;
import com.googlecode.eyesfree.brailleback.utils.StringUtils;
import com.googlecode.eyesfree.labeling.CustomLabelManager;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;

import android.content.Context;
import android.os.Build;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.Editable;
import android.text.TextUtils;
import android.widget.AbsSeekBar;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.QuickContactBadge;

/**
 * Default rule that adds the text of the node and a check mark if
 * the node is checkable.
 */
class DefaultBrailleRule implements BrailleRule {
    private CustomLabelManager mLabelManager = null;

    @Override
    public boolean accept(Context context, AccessibilityNodeInfoCompat node) {
        return true;
    }

    @Override
    public void format(Editable result,
            Context context,
            AccessibilityNodeInfoCompat node) {
        int oldLength = result.length();
        CharSequence text =
                LabelingUtils.getNodeText(node, getLabelManager());
        if (text == null) {
            text = "";
        }

        result.append(text);

        if (node.isCheckable() || node.isChecked()) {
            CharSequence mark;
            if (node.isChecked()) {
                mark = context.getString(R.string.checkmark_checked);
            } else {
                mark = context.getString(R.string.checkmark_not_checked);
            }
            StringUtils.appendWithSpaces(result, mark);
        }

        if (oldLength == result.length()
                && AccessibilityNodeInfoUtils.isActionableForAccessibility(
                    node)) {
            result.append(getFallbackText(context, node));
        }
    }

    @Override
    public boolean includeChildren(AccessibilityNodeInfoCompat node,
            Context context) {
        // This is intended to deal with containers that have content
        // descriptions (such as in the launcher).  This might need to be
        // refined if it takes away text that won't get accessibility focus by
        // itself.
        if (!TextUtils.isEmpty(node.getContentDescription())) {
            AccessibilityNodeInfoCompat firstDescendant =
                    FocusFinder.findFirstFocusableDescendant(node, context);

            AccessibilityNodeInfoUtils.recycleNodes(firstDescendant);
            return firstDescendant == null;
        }
        return true;
    }

    private CharSequence getFallbackText(
        Context context,
        AccessibilityNodeInfoCompat node) {
        // Order is important below because of class inheritance.
        if (matchesAny(context, node, Button.class, ImageButton.class)) {
            return context.getString(R.string.type_button);
        }
        if (matchesAny(context, node, QuickContactBadge.class)) {
            return context.getString(R.string.type_quickcontact);
        }
        if (matchesAny(context, node, ImageView.class)) {
            return context.getString(R.string.type_image);
        }
        if (matchesAny(context, node, EditText.class)) {
            return context.getString(R.string.type_edittext);
        }
        if (matchesAny(context, node, AbsSeekBar.class)) {
            return context.getString(R.string.type_seekbar);
        }
        return "";
    }

    private boolean matchesAny(Context context,
            AccessibilityNodeInfoCompat node,
            Class<?>... classes) {
        for (Class<?> clazz : classes) {
            if (AccessibilityNodeInfoUtils.nodeMatchesClassByType(
                    context, node, clazz)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Retrieves a handle to the correct {@link CustomLabelManager}.
     */
    private CustomLabelManager getLabelManager() {
        if (Build.VERSION.SDK_INT >= CustomLabelManager.MIN_API_LEVEL) {
            final BrailleBackService service =
                    BrailleBackService.getActiveInstance();
            if (service != null) {
                return service.getLabelManager();
            }
        }
        return null;
    }
}

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

import android.content.Context;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository of braille rules, responsible for choosing the desired rule for
 * a node.
 */
public class BrailleRuleRepository {
    private static final List<BrailleRule> RULES;
    static {
        RULES = new ArrayList<BrailleRule>();
        RULES.add(new VerticalContainerBrailleRule());
        RULES.add(new DefaultBrailleRule());
    }

    private final Context mContext;

    public BrailleRuleRepository(Context context) {
        mContext = context;
    }

    public BrailleRule find(AccessibilityNodeInfoCompat node) {
        for (BrailleRule rule : RULES) {
            if (rule.accept(mContext, node)) {
                return rule;
            }
        }
        return null;
    }
}

/*
 * Copyright (C) 2015 Google Inc.
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

package com.googlecode.eyesfree.brailleback.wrapping;

/**
 * A simple wrap strategy in which lines are split by character if they are too long to fit on a
 * single line of the braille display. In this strategy, words can be split across lines.
 */
public class SimpleWrapStrategy extends WrapStrategy {

  @Override
  protected void calculateBreakPoints() {}
}

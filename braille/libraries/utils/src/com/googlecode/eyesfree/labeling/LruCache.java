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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LRU-like cache used for in-memory representation of custom labels.
 *
 * @author caseyburkhardt@google.com (Casey Burkhardt)
 */
public class LruCache<K, V> extends LinkedHashMap<K, V> {

    /**
     * The maximum number of mappings this cache may maintain before eldest
     * entries are pruned.
     */
    private final int mMaxEntries;

    public LruCache(int maxEntries) {
        // + 1 to accommodate storage of an element to be pruned after insertion
        super(maxEntries + 1, 1.0f /* loadFactor */, true /* accessOrder */);
        mMaxEntries = maxEntries;
    }

    /**
     * This method does not modify the underlying structure.
     *
     * @see java.util.LinkedHashMap#removeEldestEntry(java.util.Map.Entry)
     */
    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > mMaxEntries;
    }
}

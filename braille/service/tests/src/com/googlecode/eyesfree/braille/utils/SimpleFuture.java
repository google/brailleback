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

package com.googlecode.eyesfree.braille.utils;

import android.os.SystemClock;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Trivial future implementation.
 * @param <V> Type of the future value.
 */
public class SimpleFuture<V> implements Future<V> {
    private boolean mDone = false;
    private boolean mCancelled = false;
    private V mResult;
    private Throwable mException;

    @Override
    public synchronized boolean isDone() {
        return mDone;
    }

    @Override
    public synchronized boolean isCancelled() {
        return mCancelled;
    }

    @Override
    public synchronized V get() throws
            InterruptedException, ExecutionException {
        while (!mDone) {
            wait();
        }
        return internalGet();
    }

    @Override
    public synchronized V get(long timeout, TimeUnit unit) throws
            InterruptedException, ExecutionException, TimeoutException {
        long endTime = SystemClock.uptimeMillis() + unit.toMillis(timeout);
        while (!mDone) {
            long timeRemaining = endTime - SystemClock.uptimeMillis();
            if (timeRemaining < 0) {
                throw new TimeoutException("future was not set in time");
            }
            wait(timeRemaining);
        }
        return internalGet();
    }

    @Override
    public synchronized boolean cancel(boolean mayInterruptIfRunning) {
        if (!mDone) {
            mCancelled = true;
            mDone = true;
            notifyAll();
            return true;
        }
        return false;
    }

    public synchronized void set(V result) {
        if (!mDone) {
            mResult = result;
            mDone = true;
            notifyAll();
        }
    }

    public synchronized void setException(Throwable exception) {
        if (!mDone) {
            mException = exception;
            mDone = true;
            notifyAll();
        }
    }

    private V internalGet() throws ExecutionException {
        if (!mDone) {
            throw new IllegalStateException("future not done yet");
        } else if (mException != null) {
            throw new ExecutionException(mException);
        } else if (mCancelled) {
            throw new CancellationException();
        } else {
            return mResult;
        }
    }
}

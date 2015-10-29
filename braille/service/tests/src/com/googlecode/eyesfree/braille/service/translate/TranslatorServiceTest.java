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

package com.googlecode.eyesfree.braille.service.translate;

import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.test.MoreAsserts;
import android.test.ServiceTestCase;
import android.test.suitebuilder.annotation.MediumTest;

import com.googlecode.eyesfree.braille.translate.ITranslatorService;
import com.googlecode.eyesfree.braille.translate.ITranslatorServiceCallback;
import com.googlecode.eyesfree.braille.translate.TableInfo;
import com.googlecode.eyesfree.braille.translate.TranslationResult;
import com.googlecode.eyesfree.braille.translate.TranslatorClient;
import com.googlecode.eyesfree.braille.utils.SimpleFuture;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Tests basic functionality of the translator service.
 */
@MediumTest
public class TranslatorServiceTest extends ServiceTestCase<TranslatorService> {

    private static final String ACTION_TRANSLATOR_SERVICE =
            "com.googlecode.eyesfree.braille.service.ACTION_TRANSLATOR_SERVICE";
    private static final String TEST_BRAILLE_TABLE_ID = "en-US-comp8";
    private static final String TEST_BRAILLE_TABLE_WITH_VARIANT_ID = "en-UEB-g1";
    private static final int INIT_TIMEOUT_MILLIS = 5000;
    private ITranslatorService mServiceInterface;

    public TranslatorServiceTest() {
        super(TranslatorService.class);
    }

    @Override
    public void tearDown() throws Exception {
        mServiceInterface = null;
    }

    /** Tests that the translator service initializes successfully. */
    public void testInitCallback() throws Exception {
        assertNotNull(getServiceInterface());
    }
 
    /** Tests that all braille tables compile successfully. */
    public void testCheckAllTables() throws Exception {
        ITranslatorService service = getServiceInterface();
        for (TableInfo info : service.getTableInfos()) {
            assertTrue("failed braille table check: " + info.getId(),
                    service.checkTable(info.getId()));
            if (TEST_BRAILLE_TABLE_WITH_VARIANT_ID.equals(info.getId())) {
              assertEquals("Expected proper variant value", "UEB", info.getVariant());
            }            
        }
    }

    /** Tests that the translator service can translate computer braille. */
    public void testTranslateComputerBraille() throws Exception {
        ITranslatorService service = getServiceInterface();
        assertTrue("expected braille table check to succeed",
                service.checkTable(TEST_BRAILLE_TABLE_ID));
        TranslationResult result = service.translate("Hello!",
                TEST_BRAILLE_TABLE_ID, -1);
        MoreAsserts.assertEquals(
                new byte[] { 0x53, 0x11, 0x07, 0x07, 0x15, 0x2e },
                result.getCells());
        MoreAsserts.assertEquals(new int[] { 0, 1, 2, 3, 4, 5 },
                result.getTextToBraillePositions());
        MoreAsserts.assertEquals(new int[] { 0, 1, 2, 3, 4, 5 },
                result.getBrailleToTextPositions());
        assertEquals(-1, result.getCursorPosition());
    }

    /** Tests that the translator service can back-translate as well. */
    public void testBackTranslateComputerBraille() throws Exception {
        ITranslatorService service = getServiceInterface();
        assertTrue("expected braille table check to succeed",
                service.checkTable(TEST_BRAILLE_TABLE_ID));
        String result = service.backTranslate(
                new byte[] { 0x53, 0x11, 0x07, 0x07, 0x15, 0x2e },
                TEST_BRAILLE_TABLE_ID);
        assertEquals("Hello!", result);
    }

    /** Waits for the service to initialize, and returns an interface to it. */
    private ITranslatorService getServiceInterface() throws ExecutionException,
            InterruptedException, RemoteException, TimeoutException {
        if (mServiceInterface == null) {
            Intent serviceIntent = new Intent(ACTION_TRANSLATOR_SERVICE);
            IBinder binder = bindService(serviceIntent);
            final SimpleFuture<Integer> initStatusFuture =
                    new SimpleFuture<Integer>();
            mServiceInterface = ITranslatorService.Stub.asInterface(binder);
            mServiceInterface.setCallback(
                    new ITranslatorServiceCallback.Stub() {
                        @Override
                        public void onInit(int status) {
                            initStatusFuture.set(status);
                        }
                    });

            int status = initStatusFuture.get(INIT_TIMEOUT_MILLIS,
                    TimeUnit.MILLISECONDS);
            assertEquals(TranslatorClient.SUCCESS, status);
        }
        return mServiceInterface;
    }
}

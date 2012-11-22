/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 */

/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.camera.stress;

import com.android.camera.VideoCamera;

import android.app.Instrumentation;
import android.content.Intent;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.LargeTest;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.FileWriter;

/**
 * Junit / Instrumentation test case for camera test
 *
 * Running the test suite:
 *
 * adb shell am instrument \
 *    -e class com.android.camera.stress.SwitchPreview \
 *    -w com.android.camera.tests/com.android.camera.CameraStressTestRunner
 *
 */
public class SwitchPreview extends ActivityInstrumentationTestCase2 <VideoCamera>{
    private String TAG = "SwitchPreview";
    private static final int TOTAL_NUMBER_OF_SWITCHING = 200;
    private static final long WAIT_FOR_PREVIEW = 4000;

    private static final String CAMERA_TEST_OUTPUT_FILE =
            Environment.getExternalStorageDirectory().toString() + "/mediaStressOut.txt";
    private BufferedWriter mOut;
    private FileWriter mfstream;

    public SwitchPreview() {
        super("com.google.android.camera", VideoCamera.class);
    }

    @Override
    protected void setUp() throws Exception {
        getActivity();
        prepareOutputFile();
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        getActivity().finish();
        closeOutputFile();
        super.tearDown();
    }

    private void prepareOutputFile(){
        try{
            mfstream = new FileWriter(CAMERA_TEST_OUTPUT_FILE, true);
            mOut = new BufferedWriter(mfstream);
        } catch (Exception e){
            assertTrue("Camera Switch Mode",false);
        }
    }

    private void closeOutputFile() {
        try {
            mOut.write("\n");
            mOut.close();
            mfstream.close();
        } catch (Exception e) {
            assertTrue("CameraSwitchMode close output", false);
        }
    }

    @LargeTest
    public void testSwitchMode() {
        //Switching the video and the video recorder mode
        Instrumentation inst = getInstrumentation();
        try{
            mOut.write("Camera Switch Mode:\n");
            mOut.write("No of loops :" + TOTAL_NUMBER_OF_SWITCHING + "\n");
            mOut.write("loop: ");
            for (int i=0; i< TOTAL_NUMBER_OF_SWITCHING; i++) {
                Thread.sleep(WAIT_FOR_PREVIEW);
                Intent intent = new Intent();
                intent.setClassName("com.google.android.camera",
                        "com.android.camera.VideoCamera");
                getActivity().startActivity(intent);
                Thread.sleep(WAIT_FOR_PREVIEW);
                intent.setClassName("com.google.android.camera",
                "com.android.camera.Camera");
                getActivity().startActivity(intent);
                mOut.write(" ," + i);
                mOut.flush();
            }
        } catch (Exception e){
            Log.v(TAG, e.toString());
        }
            assertTrue("testSwitchMode",true);
    }
}

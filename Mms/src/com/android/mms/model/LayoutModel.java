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
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

/*
 * Copyright (C) 2008 Esmertec AG.
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.mms.model;

import com.android.mms.layout.LayoutManager;
import com.android.mms.layout.LayoutParameters;

import android.util.Config;
import android.util.Log;

import java.util.ArrayList;

public class LayoutModel extends Model {
    private static final String TAG = SlideModel.TAG;
    private static final boolean DEBUG = false;
    private static final boolean LOCAL_LOGV = DEBUG ? Config.LOGD : Config.LOGV;

    public static final String IMAGE_REGION_ID = "Image";
    public static final String TEXT_REGION_ID  = "Text";

    public static final int LAYOUT_BOTTOM_TEXT = 0;
    public static final int LAYOUT_TOP_TEXT    = 1;
    public static final int DEFAULT_LAYOUT_TYPE = LAYOUT_BOTTOM_TEXT;

    private static int sLayoutType = DEFAULT_LAYOUT_TYPE;
    private RegionModel mRootLayout;
    private RegionModel mImageRegion;
    private RegionModel mTextRegion;
    private ArrayList<RegionModel> mNonStdRegions;
    private LayoutParameters mLayoutParams;

    public LayoutModel() {
        mLayoutParams = LayoutManager.getInstance().getLayoutParameters();
        // Create default root-layout and regions.
        createDefaultRootLayout();
        createDefaultImageRegion();
        createDefaultTextRegion();
    }

    public LayoutModel(RegionModel rootLayout, ArrayList<RegionModel> regions) {
        mLayoutParams = LayoutManager.getInstance().getLayoutParameters();
        mRootLayout = rootLayout;
        mNonStdRegions = new ArrayList<RegionModel>();

        for (RegionModel r : regions) {
            String rId = r.getRegionId();
            if (rId.equals(IMAGE_REGION_ID)) {
                mImageRegion = r;
            } else if (rId.equals(TEXT_REGION_ID)) {
                mTextRegion = r;
            } else {
                if (LOCAL_LOGV) {
                    Log.v(TAG, "Found non-standard region: " + rId);
                }
                mNonStdRegions.add(r);
            }
        }

        validateLayouts();
    }

    private void createDefaultRootLayout() {
        mRootLayout = new RegionModel(null, 0, 0, mLayoutParams.getWidth(),
                                                  mLayoutParams.getHeight());
    }

    private void createDefaultImageRegion() {
        if (mRootLayout == null) {
            throw new IllegalStateException("Root-Layout uninitialized.");
        }

        mImageRegion = new RegionModel(IMAGE_REGION_ID, 0, 0,
                mRootLayout.getWidth(), mLayoutParams.getImageHeight());
    }

    private void createDefaultTextRegion() {
        if (mRootLayout == null) {
            throw new IllegalStateException("Root-Layout uninitialized.");
        }

        mTextRegion = new RegionModel(
                TEXT_REGION_ID, 0, mLayoutParams.getImageHeight(),
                mRootLayout.getWidth(), mLayoutParams.getTextHeight());
    }

    private void validateLayouts() {
        if (mRootLayout == null) {
            createDefaultRootLayout();
        }

        if (mImageRegion == null) {
            createDefaultImageRegion();
        }

        if (mTextRegion == null) {
            createDefaultTextRegion();
        }

        if((mImageRegion != null) && (mTextRegion != null)) {
            if(mImageRegion.getTop() > mTextRegion.getTop()) {
                if(sLayoutType == LAYOUT_BOTTOM_TEXT) {
                    changeTo(LAYOUT_TOP_TEXT);
                }
            } else {
                if(sLayoutType == LAYOUT_TOP_TEXT) {
                    changeTo(LAYOUT_BOTTOM_TEXT);
                }
            }
        }
    }

    public RegionModel getRootLayout() {
        return mRootLayout;
    }

    public void setRootLayout(RegionModel rootLayout) {
        mRootLayout = rootLayout;
    }

    public RegionModel getImageRegion() {
        return mImageRegion;
    }

    public void setImageRegion(RegionModel imageRegion) {
        mImageRegion = imageRegion;
    }

    public RegionModel getTextRegion() {
        return mTextRegion;
    }

    public void setTextRegion(RegionModel textRegion) {
        mTextRegion = textRegion;
    }

    /**
     * Get all regions except root-layout. The result is READ-ONLY.
     */
    public ArrayList<RegionModel> getRegions() {
        ArrayList<RegionModel> regions = new ArrayList<RegionModel>();
        if (mImageRegion != null) {
            regions.add(mImageRegion);
        }
        if (mTextRegion != null) {
            regions.add(mTextRegion);
        }
        return regions;
    }

    public RegionModel findRegionById(String rId) {
        if (IMAGE_REGION_ID.equals(rId)) {
            return mImageRegion;
        } else if (TEXT_REGION_ID.equals(rId)) {
            return mTextRegion;
        } else {
            for (RegionModel r : mNonStdRegions) {
                if (r.getRegionId().equals(rId)) {
                    return r;
                }
            }

            if (LOCAL_LOGV) {
                Log.v(TAG, "Region not found: " + rId);
            }
            return null;
        }
    }

    public int getLayoutWidth() {
        return mRootLayout.getWidth();
    }

    public int getLayoutHeight() {
        return mRootLayout.getHeight();
    }

    public String getBackgroundColor() {
        return mRootLayout.getBackgroundColor();
    }

    public void changeTo(int layout) {
        if (mRootLayout == null) {
            throw new IllegalStateException("Root-Layout uninitialized.");
        }

        if (mLayoutParams == null) {
            mLayoutParams = LayoutManager.getInstance().getLayoutParameters();
        }
        if (LayoutModel.sLayoutType != layout) {
            switch (layout) {
                case LAYOUT_BOTTOM_TEXT: {
                    mImageRegion.setTop(0);
                    mImageRegion.setHeight(mLayoutParams.getImageHeight());
                    mTextRegion.setTop(mLayoutParams.getImageHeight());
                    LayoutModel.sLayoutType = layout;
                    notifyModelChanged(true);
                }
                break;
                case LAYOUT_TOP_TEXT: {
                    mImageRegion.setTop(mLayoutParams.getTextHeight());
                    mTextRegion.setTop(0);
                    LayoutModel.sLayoutType = layout;
                    notifyModelChanged(true);
                }
                break;
                default: {
                    Log.w(TAG, "Unknown layout type: " + layout);
                }
            }
        } else {
            if (LOCAL_LOGV) {
                Log.v(TAG, "Skip changing layout.");
            }
        }
    }

    public static int getLayoutType() {
    	return LayoutModel.sLayoutType;
    }

    @Override
    protected void registerModelChangedObserverInDescendants(
            IModelChangedObserver observer) {
        if (mRootLayout != null) {
            mRootLayout.registerModelChangedObserver(observer);
        }

        if (mImageRegion != null) {
            mImageRegion.registerModelChangedObserver(observer);
        }

        if (mTextRegion != null) {
            mTextRegion.registerModelChangedObserver(observer);
        }
    }

    @Override
    protected void unregisterModelChangedObserverInDescendants(
            IModelChangedObserver observer) {
        if (mRootLayout != null) {
            mRootLayout.unregisterModelChangedObserver(observer);
        }

        if (mImageRegion != null) {
            mImageRegion.unregisterModelChangedObserver(observer);
        }

        if (mTextRegion != null) {
            mTextRegion.unregisterModelChangedObserver(observer);
        }
    }

    @Override
    protected void unregisterAllModelChangedObserversInDescendants() {
        if (mRootLayout != null) {
            mRootLayout.unregisterAllModelChangedObservers();
        }

        if (mImageRegion != null) {
            mImageRegion.unregisterAllModelChangedObservers();
        }

        if (mTextRegion != null) {
            mTextRegion.unregisterAllModelChangedObservers();
        }
    }
}
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

package com.android.mms.ui;

import com.android.mms.R;
import android.content.Context;
import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.google.android.mms.ContentType;
import com.google.android.mms.pdu.PduBody;
import com.google.android.mms.pdu.PduPart;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import com.mediatek.xlog.Xlog;

/**
 * The data structure describing each item in the Preview List.
 */
public class PreviewListItemData {
    private static final String TAG = "Mms/PreviewListItemData";
    private PduPart mPduPart;
    private String mSize;
    private String mName;
    private Bitmap mThumbnail;
    private long mMessageId;
    private String mFallbackName;
    private Uri mDataUri;
    private int mSlideNum = -1;
    private static Bitmap sThumbDefaultImage;
    private static Bitmap sThumbDefaultAudio;
    private static Bitmap sThumbDefaultVideo;

    public PreviewListItemData(Context context, PduPart part, long msgid, int slideNum) {
        mPduPart = part;
        mMessageId = msgid;
        mFallbackName = Long.toHexString(msgid);
        mDataUri = part.getDataUri();
        mName = getNameFromPart(part);
        mSize = getSizeFromPart(part, context.getContentResolver());
        final float density = context.getResources().getDisplayMetrics().density;
        mThumbnail = getThumbnailFromPart(part, context, 
                getDesiredThumbnailWidth(density), getDesiredThumbnailHeight(density));
        mSlideNum = slideNum;
        
    }

    public Uri getDataUri(){
        return mDataUri;
    }
    
    public Bitmap getThumbnail() {
        return mThumbnail;
    }

    public String getName() {
        return mName;
    }

    public String getSize() {
        return mSize;
    }

    public PduPart getPduPart() {
        return mPduPart;
    }
    
    public int getSlideNum() {
        return mSlideNum;
    }

    @Override
    public String toString() {
        return "[PreviewListItemData from:" + getName() + " subject:" + getSize() + "]";
    }

    private String getNameFromPart(PduPart part) {
        byte[] location = part.getName();
        byte[] filename = part.getFilename();
        byte[] mylocation = part.getContentLocation();
        if (location == null) {
            location = part.getFilename();
        }
        if (location == null) {
            location = part.getContentLocation();
        }
        String fileName;
        if (location == null) {
            // Use fallback name, which is based on Message ID
            fileName = mFallbackName;
        } else {
            fileName = new String(location);
        }
        String extension;
        int index;
        if ((index = fileName.indexOf(".")) == -1) {
            String type = new String(part.getContentType());
            extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(type);
            // MimeTypeMap does not recognize 'audio/amr', Yuk!
            if (extension == null && type.equals("audio/amr")) {
                extension = "amr";
            }
            
        } else {
            extension = fileName.substring(index + 1, fileName.length());
            fileName = fileName.substring(0, index);
        }
        // Get rid of illegal characters in filename
        final String regex = "[:\\/?,. ]";
        fileName = fileName.replaceAll(regex, "_");
        Xlog.i(TAG, "getNameFromPart, fileName is " + fileName + ", extension is " + extension);
        return fileName + "." + extension;
    }

    private String getSizeFromPart(PduPart part, ContentResolver cr) {
        long size = 0;
        ParcelFileDescriptor pfd = null;
        try {
            try {
                pfd = cr.openFileDescriptor(mDataUri, "r");
                size = pfd.getStatSize();
            } finally {
                if (pfd != null) {
                    pfd.close();
                }
            }
        } catch (FileNotFoundException e) {
            Xlog.e(TAG, "getSizeFromPart, " + e.getMessage(), e);
        } catch (IOException e) {
            Xlog.e(TAG, "getSizeFromPart, " + e.getMessage(), e);
        }
        return (size / 1024) + "K";
    }

    private int getDesiredThumbnailWidth(float density) {
        return (int) (50 * density);
    }

    private int getDesiredThumbnailHeight(float density) {
        return (int) (50 * density);
    }

    private Bitmap getThumbnailFromPart(PduPart part, Context context, int width, int height) {
        final String type = new String(part.getContentType());
        if (ContentType.isImageType(type)) {
            InputStream input = null;
            Bitmap raw = null;;
            try {
                try {
                    input = context.getContentResolver().openInputStream(mDataUri);
                    raw = BitmapFactory.decodeStream(input, null, null);
                } finally {
                    if (input != null) {
                        input.close();
                    }
                }
            } catch (FileNotFoundException e) {
                Xlog.e(TAG, e.getMessage(), e);
            } catch (IOException e) {
                Xlog.e(TAG, e.getMessage());
            } catch (OutOfMemoryError ex) {
                Xlog.e(TAG, ex.getMessage());
                MessageUtils.writeHprofDataToFile();
                throw ex;
            }
            Bitmap thumb;
            if (raw == null) {
                if (sThumbDefaultImage == null) {
                    sThumbDefaultImage = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_multi_save_thumb_image);
                }
                thumb = sThumbDefaultImage;
            } else {
                thumb = Bitmap.createScaledBitmap(raw, width, height, true);
                if (thumb != raw) {
                    raw.recycle();
                }
            }
            return thumb;
        } else if (ContentType.isVideoType(type)) {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            Bitmap raw = null;
            try {
                try {
                    retriever.setDataSource(context, mDataUri);
                    raw = retriever.getFrameAtTime(-1);
                } finally {
                    retriever.release();
                }
            } catch (IllegalArgumentException e) {
                // corrupted video
            } catch (RuntimeException e) {
                // corrupted video
            }
            Bitmap thumb;
            if (raw == null) {
                if (sThumbDefaultVideo == null) {
                    sThumbDefaultVideo = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_multi_save_thumb_video);
                }
                thumb = sThumbDefaultVideo;
            } else {
                thumb = Bitmap.createScaledBitmap(raw, width, height, true);
                if (thumb != raw) {
                    raw.recycle();
                }
            }
            return thumb;
        } else if (ContentType.isAudioType(type)) {
            if (sThumbDefaultAudio == null) {
                sThumbDefaultAudio = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_multi_save_thumb_audio);
            }
            return sThumbDefaultAudio;
        }
        return null;
    }
}

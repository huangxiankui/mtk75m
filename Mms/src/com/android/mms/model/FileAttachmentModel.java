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

import com.android.mms.R;
import com.android.mms.ContentRestrictionException;
import com.google.android.mms.ContentType;
import com.google.android.mms.pdu.PduPart;
import com.google.android.mms.MmsException;

import android.content.ContentResolver;
import android.content.Context;
import android.database.sqlite.SqliteWrapper;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony.Mms.Part;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public abstract class FileAttachmentModel extends Model {
    protected static final String TAG = "Mms/file_attach";
    public static final String UNKNOWN_TYPE = "unknown_type";

    protected Context mContext;
    protected String mFileName;
    protected String mContentType;
    protected Uri mUri;
    protected byte[] mData;
    protected int mSize;

    public FileAttachmentModel() {}

    public FileAttachmentModel(Context context, Uri uri, String contentType) throws MmsException {
        mContext = context;
        mContentType = contentType;
        mUri = uri;
        initModelFromUri(context, uri);
        initAttachmentSize();
        checkContentRestriction();
    }

    public FileAttachmentModel(Context context, String contentType,
            String fileName, Uri uri) throws MmsException {
        mContext = context;
        mContentType = contentType;
        mFileName = fileName;
        mUri = uri;
        initAttachmentSize();
        checkContentRestriction();
    }

    public FileAttachmentModel(Context context, String contentType,
            String fileName, byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("data may not be null.");
        }
        mContext = context;
        mContentType = contentType;
        mFileName = fileName;
        mData = data;
        mSize = data.length;
        checkContentRestriction();
    }

    public String getContentType() {
        return mContentType;
    }

    /**
     * Get the URI of the media without checking DRM rights. Use this method
     * only if the media is NOT DRM protected.
     *
     * @return The URI of the media.
     */
    public Uri getUri() {
        return mUri;
    }

    public byte[] getData() {
        if (mData != null) {
            byte[] data = new byte[mData.length];
            System.arraycopy(mData, 0, data, 0, mData.length);
            return data;
        }
        return null;
    }

    /**
     * @param uri the mUri to set
     */
    void setUri(Uri uri) {
        mUri = uri;
    }
    
    /**
     * @param uri the mUri to set
     */
    void setData(byte[] data) {
        mData = data;
    }


    /**
     * @return the mSrc
     */
    public String getSrc() {
        return mFileName;
    }

    /**
     * @return the size of the attached media
     */
    public int getAttachSize() {
        return mSize;
    }

    protected void checkContentRestriction() throws ContentRestrictionException {
        ContentRestriction cr = ContentRestrictionFactory.getContentRestriction();
        cr.checkFileAttachmentContentType(mContentType);
    }

    public boolean isVCard() {
        if (mContentType == null) {
            return mFileName.toLowerCase().endsWith(".vcf");
        }
        return mContentType.equalsIgnoreCase(ContentType.TEXT_VCARD);
    }
    
    public boolean isVCalendar() {
        if (mContentType == null) {
            return mFileName.toLowerCase().endsWith(".vcs");
        }
        return mContentType.equalsIgnoreCase(ContentType.TEXT_VCALENDAR);
    }

    // some MMSCs changed content type from 'text/x-vCard' to 'application/oct-stream'
    public static boolean isVCard(final PduPart part) {
    	String filename = null;
        final String type = new String(part.getContentType());
        byte[] cl = part.getContentLocation();
        byte[] name = part.getName();
        byte[] ci = part.getContentId();
        byte[] fn = part.getFilename();
        
        if (cl != null) {
        	filename = new String(cl);
        	return (ContentType.TEXT_VCARD.equalsIgnoreCase(type) || 
                    ((type.equals("application/oct-stream") || type.equals("application/octet-stream")) &&
                    filename.endsWith(".vcf")));
        } else if (name != null){
        	filename = new String(name);
        	return (ContentType.TEXT_VCARD.equalsIgnoreCase(type) || 
                    ((type.equals("application/oct-stream") || type.equals("application/octet-stream")) &&
                    filename.endsWith(".vcf")));
        } else if (ci != null){
        	filename = new String(ci);
        	return (ContentType.TEXT_VCARD.equalsIgnoreCase(type) || 
                    ((type.equals("application/oct-stream") || type.equals("application/octet-stream")) &&
                    filename.endsWith(".vcf")));
        } else if (fn != null){
        	filename = new String(fn);
        	return (ContentType.TEXT_VCARD.equalsIgnoreCase(type) || 
                    ((type.equals("application/oct-stream") || type.equals("application/octet-stream")) &&
                    filename.endsWith(".vcf")));
        } else {
        	return false;
        }     
    }

    // some MMSCs changed content type from 'text/x-vCalendar' to 'application/oct-stream'
    public static boolean isVCalendar(final PduPart part) {
    	String filename = null;
        final String type = new String(part.getContentType());
        byte[] cl = part.getContentLocation();
        byte[] name = part.getName();
        byte[] ci = part.getContentId();
        byte[] fn = part.getFilename();
        
        if (cl != null) {
        	filename = new String(cl);
        	return (ContentType.TEXT_VCALENDAR.equalsIgnoreCase(type) || 
                    ((type.equals("application/oct-stream") || type.equals("application/octet-stream")) &&
                    filename.endsWith(".vcs")));
        } else if (name != null){
        	filename = new String(name);
        	return (ContentType.TEXT_VCALENDAR.equalsIgnoreCase(type) || 
                    ((type.equals("application/oct-stream") || type.equals("application/octet-stream")) &&
                    filename.endsWith(".vcs")));
        } else if (ci != null){
        	filename = new String(ci);
        	return (ContentType.TEXT_VCALENDAR.equalsIgnoreCase(type) || 
                    ((type.equals("application/oct-stream") || type.equals("application/octet-stream")) &&
                    filename.endsWith(".vcs")));
        } else if (fn != null){
        	filename = new String(fn);
        	return (ContentType.TEXT_VCALENDAR.equalsIgnoreCase(type) || 
                    ((type.equals("application/oct-stream") || type.equals("application/octet-stream")) &&
                    filename.endsWith(".vcs")));
        } else {
        	return false;
        }     
    }

    /*
     * Supported files are defined as all the files we know.
     */
    public boolean isSupportedFile() {
        return isSupportedFile(mContentType);
    }

    /* general utility */
    public static boolean isSupportedFile(final String contentType) {
        if (TextUtils.isEmpty(contentType)) {
            return false;
        }
        return contentType.equalsIgnoreCase(ContentType.TEXT_VCARD) ||
            contentType.equalsIgnoreCase(ContentType.TEXT_VCALENDAR);
    }

    // some MMSCs changed content type from 'text/x-vCard' to 'application/oct-stream'
    public static boolean isSupportedFile(final PduPart part) {
        return isVCard(part) || isVCalendar(part);
    }
    
    public boolean isUnknownAttachment() {
        return (TextUtils.isEmpty(mContentType) || UNKNOWN_TYPE.equals(mContentType));
    }

    public static boolean isMmsUri(Uri uri) {
        return uri.getAuthority().startsWith("mms");
    }

    @Override
    public String toString() {
        return "FileAttachmentModel [mContentType=" + mContentType + ", mSrc=" + mFileName + ", mUri=" + mUri + "]";
    }

    private void initModelFromUri(Context context, Uri uri) throws MmsException {
        try {
            final String scheme = uri.getScheme();
            if (scheme.equals("content")) {
                initFromContentUri(context, uri);
            } else if (uri.getScheme().equals("file")) {
                initFromFile(context, uri);
            }

            mFileName = mFileName.substring(mFileName.lastIndexOf('/') + 1);

            if (mFileName.startsWith(".") && mFileName.length() > 1) {
                mFileName = mFileName.substring(1);
            }
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "IllegalArgumentException caught while opening or reading stream", e);
            throw new MmsException("Type of vcard is unknown.");
        }
    }

    private void initFromFile(Context context, Uri uri) {
        mFileName = uri.getPath();
        if (TextUtils.isEmpty(mContentType)) {
            // parse content type via file extension
            int index;
            if ((index = mFileName.lastIndexOf(".")) != -1) {
                final String extension = mFileName.substring(index + 1, mFileName.length());
                mContentType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
            }
            if (TextUtils.isEmpty(mContentType)) {
                mContentType = UNKNOWN_TYPE;
            }
        }
    }

    private void initFromContentUri(Context context, Uri uri) {
        Cursor c = SqliteWrapper.query(context, context.getContentResolver(), uri, null, null, null, null);

        if (c == null) {
            throw new IllegalArgumentException("Query on " + uri + " returns null result.");
        }

        try {
            if ((c.getCount() != 1) || !c.moveToFirst()) {
                throw new IllegalArgumentException("Query on " + uri + " returns 0 or multiple rows.");
            }

            String filePath;
            if (isMmsUri(uri)) {
                filePath = c.getString(c.getColumnIndexOrThrow(Part.FILENAME));
                if (TextUtils.isEmpty(filePath)) {
                    filePath = c.getString(c.getColumnIndexOrThrow(Part._DATA));
                }
                mContentType = c.getString(c.getColumnIndexOrThrow(Part.CONTENT_TYPE));
            } else {
                filePath = c.getString(c.getColumnIndexOrThrow("_data"));
                mContentType = c.getString(c.getColumnIndexOrThrow("mime_type"));
            }
            mFileName = filePath;
        } finally {
            c.close();
        }
    }

    private void initAttachmentSize() throws MmsException {
        final ContentResolver cr = mContext.getContentResolver();
        InputStream input = null;
        try {
            try {
                input = cr.openInputStream(mUri);
                if (input instanceof FileInputStream) {
                    // avoid reading the whole stream to get its length
                    FileInputStream f = (FileInputStream) input;
                    mSize = (int) f.getChannel().size();
                } else {
                    while (-1 != input.read()) {
                        mSize++;
                    }
                }
            } finally {
                if (input != null) {
                    input.close();
                }
            }
        } catch (FileNotFoundException e) {
            Log.e(TAG, "initAttachmentSize, file is not found??");
            throw new MmsException("FileAttachmentModel#initAttachmentSize() " + e.getMessage());
        } catch (IOException e) {
            Log.e(TAG, "initAttachmentSize, other exceptions");
            throw new MmsException("FileAttachmentModel#initAttachmentSize() " + e.getMessage());
        }
    }
}

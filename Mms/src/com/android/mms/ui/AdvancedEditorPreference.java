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

package com.android.mms.ui;

import com.android.mms.R;
import android.R.color;
import android.content.Context;
import android.content.res.TypedArray;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.gemini.GeminiPhone;
import com.android.internal.telephony.Phone;
import android.provider.Telephony;
import android.os.SystemProperties;

public class AdvancedEditorPreference extends Preference{

	public interface GetSimInfo {

		CharSequence getSimNumber(int i);

		CharSequence getSimName(int i);

		int getSimColor(int i);
		
		int getNumberFormat(int i);
		
		int getSimStatus(int i);
		
		boolean is3G(int i);
	}

	private int currentId = 0; // for object reference count;
	private static final String TAG = "AdvancedEditorPreference";
	private static TextView simName;
	private static TextView simNumber;
	private static TextView simNumberShort;
	private static TextView sim3G;
	private static ImageView simStatus;
	private static ImageView simColor;
	
	private GetSimInfo simInfo;
	private Context mContext;
    public AdvancedEditorPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        TypedArray a = context.obtainStyledAttributes(attrs,
                com.android.internal.R.styleable.Preference, defStyle, 0);
        a.recycle();
    }
    
    public AdvancedEditorPreference(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }
    
    public AdvancedEditorPreference(Context context) {
        this(context, null);
    }
    
    public void init(Context context, int id) {
    	simInfo = (GetSimInfo) context;
		mContext = context;
    	currentId = id;
    }
    
    @Override
    protected View onCreateView(ViewGroup parent) {
        final LayoutInflater layoutInflater =
            (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View layout = layoutInflater.inflate(R.layout.advanced_editor_preference, parent, false); 

        simName = new TextView(mContext);
		simNumber = new TextView(mContext);
		simNumberShort = new TextView(mContext);
		simStatus = new ImageView(mContext);
        simColor = new ImageView(mContext);
		sim3G = new TextView(mContext);
		

        return layout;
    }
    
    @Override
    //called when we're binding the view to the preference.
    protected void onBindView(View view) {
        super.onBindView(view);
        simName = (TextView) view.findViewById(R.id.simName);
        simNumber = (TextView) view.findViewById(R.id.simNumber);
        simNumberShort = (TextView) view.findViewById(R.id.simNumberShort);
        simStatus = (ImageView) view.findViewById(R.id.simStatus);
        simColor = (ImageView) view.findViewById(R.id.simIcon);
        sim3G = (TextView) view.findViewById(R.id.sim3g);
        // here need change to common usage
        simName.setText(simInfo.getSimName(currentId));
        simNumber.setText(simInfo.getSimNumber(currentId));
        String numShow = (String) simInfo.getSimNumber(currentId);
        
        if (simInfo.getNumberFormat(currentId) == android.provider.Telephony.SimInfo.DISPLAY_NUMBER_FIRST) {
            if (numShow != null && numShow.length()>4) {
                simNumberShort.setText(numShow.substring(0, 4));
            } else {
            	simNumberShort.setText(numShow);
            }
        } else if (simInfo.getNumberFormat(currentId) == android.provider.Telephony.SimInfo.DISPLAY_NUMBER_LAST) {
        	if (numShow != null && numShow.length()>4) {
        	    simNumberShort.setText(numShow.substring(numShow.length() - 4));
            } else {
            	simNumberShort.setText(numShow);
            }
        } else {
        	simNumberShort.setText("");
        }
        
        int simStatusResourceId = MessageUtils.getSimStatusResource(simInfo.getSimStatus(currentId));
        if (-1 != simStatusResourceId) {
            simStatus.setImageResource(simStatusResourceId);
        }
        simColor.setBackgroundResource(simInfo.getSimColor(currentId));
        // show the first 3G slot
        if (simInfo.is3G(currentId)) {
        	String optr = SystemProperties.get("ro.operator.optr");
        	//MTK_OP02_PROTECT_START
            if (optr.equals("OP02")) {
        	    sim3G.setVisibility(View.VISIBLE);
            } else 
            //MTK_OP02_PROTECT_END
            {
            	sim3G.setVisibility(View.GONE);
            }
        }
    }
}

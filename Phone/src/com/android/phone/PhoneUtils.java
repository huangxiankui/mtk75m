/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2010. All rights reserved.
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
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.phone;

import android.app.ActivityManagerNative;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.telephony.PhoneNumberUtils;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.os.ServiceManager;
import com.mediatek.dmagent.DMAgent;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CallerInfo;
import com.android.internal.telephony.CallerInfoAsyncQuery;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.IExtendedNetworkService;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.cdma.CdmaConnection;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.sip.SipPhone;


import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

import com.android.internal.telephony.gemini.*;
import com.mediatek.featureoption.FeatureOption;
import android.os.ServiceManager;

/**
 * Misc utilities for the Phone app.
 */
public class PhoneUtils {
    private static final String LOG_TAG = "PhoneUtils";
//Google: private static final boolean DBG = (PhoneApp.DBG_LEVEL >= 2);
    private static final boolean DBG = true;

    /** Control stack trace for Audio Mode settings */
    private static final boolean DBG_SETAUDIOMODE_STACK = false;

    /** Identifier for the "Add Call" intent extra. */
    static final String ADD_CALL_MODE_KEY = "add_call_mode";

    // Return codes from placeCall()
    static final int CALL_STATUS_DIALED = 0;  // The number was successfully dialed
    static final int CALL_STATUS_DIALED_MMI = 1;  // The specified number was an MMI code
    static final int CALL_STATUS_FAILED = 2;  // The call failed

    // State of the Phone's audio modes
    // Each state can move to the other states, but within the state only certain
    //  transitions for AudioManager.setMode() are allowed.
    static final int AUDIO_IDLE = 0;  /** audio behaviour at phone idle */
    static final int AUDIO_RINGING = 1;  /** audio behaviour while ringing */
    static final int AUDIO_OFFHOOK = 2;  /** audio behaviour while in call. */
    private static int sAudioBehaviourState = AUDIO_IDLE;

    /** Speaker state, persisting between wired headset connection events */
    private static boolean sIsSpeakerEnabled = false;

    /** Hash table to store mute (Boolean) values based upon the connection.*/
    private static Hashtable<Connection, Boolean> sConnectionMuteTable =
        new Hashtable<Connection, Boolean>();

    /** Static handler for the connection/mute tracking */
    private static ConnectionHandler mConnectionHandler;

    /** Phone state changed event*/
    private static final int PHONE_STATE_CHANGED = -1;

//MTK begin
    private static final int PHONE_SPEECH_INFO = -2;
    private static final int PHONE_STATE_CHANGED2 = -3;
    private static final int PHONE_SPEECH_INFO2 = -4;
//MTK end


    /** Define for not a special CNAP string */
    private static final int CNAP_SPECIAL_CASE_NO = -1;

    // Extended network service interface instance
    private static IExtendedNetworkService mNwService = null;
    // used to cancel MMI command after 15 seconds timeout for NWService requirement
    private static Message mMmiTimeoutCbMsg = null;

    /** Noise suppression status as selected by user */
    private static boolean sIsNoiseSuppressionEnabled = true;
    private static final int MIN_LENGTH = 6;
    private static final int MIN_WIDTH = 270;

     /** For TTY usage */
    private static String ttyMode = "tty_off";
    private static boolean isOpen = false;
    
    public static void setTtyMode (String mode)
    {
        ttyMode = mode;
    }
    
    public static void openTTY() {
        if (!PhoneApp.getInstance().isEnableTTY()) {
            return;
        }
        Context context = PhoneApp.getInstance();
        AudioManager audioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        if (!ttyMode.equals("tty_off") && !isOpen)
        {
            audioManager.setParameters("tty_mode="+ ttyMode);
            isOpen = true;
        }
    }
    
    public static String DUALMIC_MODE = "Enable_Dual_Mic_Setting";

    //Add for recording the USSD dialog, and use to dismiss the dialog when enter airplane mode
    static Dialog mDialog = null;
    static UssdAlertActivity mUssdActivity = null;
    private static boolean mbMmiFinished = false;
    private static MmiCode curCode = null;

    // MTK_OP01_PROTECT_START
    // MTK_OP02_PROTECT_START
    public static String mOptr = null;
    public static String mSpec = null;
    public static String mSeg = null;
    // MTK_OP02_PROTECT_END
    // MTK_OP01_PROTECT_END	

    /**
     * Handler that tracks the connections and updates the value of the
     * Mute settings for each connection as needed.
     */
    private static class ConnectionHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;
            switch (msg.what) {
                case PHONE_STATE_CHANGED:
//MTK add below one line
                case PHONE_STATE_CHANGED2:
                    if (DBG) log("ConnectionHandler: updating mute state for each connection");

                    CallManager cm = (CallManager) ar.userObj;

                    // update the foreground connections, if there are new connections.
                    // Have to get all foreground calls instead of the active one
                    // because there may two foreground calls co-exist in shore period
                    // (a racing condition based on which phone changes firstly)
                    // Otherwise the connection may get deleted.
                    List<Connection> fgConnections = new ArrayList<Connection>();
                    for (Call fgCall : cm.getForegroundCalls()) {
                        if (!fgCall.isIdle()) {
                            fgConnections.addAll(fgCall.getConnections());
                        }
                    }
                    for (Connection cn : fgConnections) {
                        if (sConnectionMuteTable.get(cn) == null) {
                            sConnectionMuteTable.put(cn, Boolean.FALSE);
                        }
                    }

                    // mute is connection based operation, we need loop over
                    // all background calls instead of the first one to update
                    // the background connections, if there are new connections.
                    List<Connection> bgConnections = new ArrayList<Connection>();
                    for (Call bgCall : cm.getBackgroundCalls()) {
                        if (!bgCall.isIdle()) {
                            bgConnections.addAll(bgCall.getConnections());
                        }
                    }
                    for (Connection cn : bgConnections) {
                        if (sConnectionMuteTable.get(cn) == null) {
                          sConnectionMuteTable.put(cn, Boolean.FALSE);
                        }
                    }

                    // Check to see if there are any lingering connections here
                    // (disconnected connections), use old-school iterators to avoid
                    // concurrent modification exceptions.
                    Connection cn;
                    for (Iterator<Connection> cnlist = sConnectionMuteTable.keySet().iterator();
                            cnlist.hasNext();) {
                        cn = cnlist.next();
                        if (!fgConnections.contains(cn) && !bgConnections.contains(cn)) {
                            if (DBG) log("connection: " + cn + "not accounted for, removing.");
                            cnlist.remove();
                        }
                    }

                    // Restore the mute state of the foreground call if we're not IDLE,
                    // otherwise just clear the mute state. This is really saying that
                    // as long as there is one or more connections, we should update
                    // the mute state with the earliest connection on the foreground
                    // call, and that with no connections, we should be back to a
                    // non-mute state.
                    if (cm.getState() != Phone.State.IDLE) {
                        restoreMuteState();
                    } else {
                        setMuteInternal(cm.getFgPhone(), false);
                    }

                    break;
//MTK begin:
                case PHONE_SPEECH_INFO2:
                     if (DBG) log("ConnectionHandler: PHONE_SPEECH_INFO2");
                     MTKCallManager cm4 = (MTKCallManager) ar.userObj;
                     setAudioMode();
                     cm4.unregisterForSpeechInfoGemini(mConnectionHandler, Phone.GEMINI_SIM_2);
                     break;
                case PHONE_SPEECH_INFO:
                    if (DBG) log("ConnectionHandler: PHONE_SPEECH_INFO");
                     //This is ugly, but now no more elegant way to resolved this.
                    boolean isMTKCm = ar.userObj instanceof MTKCallManager;
                    if(FeatureOption.MTK_GEMINI_SUPPORT && isMTKCm){
                    	MTKCallManager cm3 = (MTKCallManager) ar.userObj;
                    	setAudioMode();
                        cm3.unregisterForSpeechInfoGemini(mConnectionHandler, Phone.GEMINI_SIM_1);
                    } else {
                        CallManager cm2 = (CallManager) ar.userObj;
                        setAudioMode();
                        cm2.unregisterForSpeechInfo(mConnectionHandler);
                    }
                    break;
//MTK end
            }
        }
    }


    private static ServiceConnection ExtendedNetworkServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder iBinder) {
            if (DBG) log("Extended NW onServiceConnected");
            mNwService = IExtendedNetworkService.Stub.asInterface(iBinder);
        }

        public void onServiceDisconnected(ComponentName arg0) {
            if (DBG) log("Extended NW onServiceDisconnected");
            mNwService = null;
        }
    };

    /**
     * Register the ConnectionHandler with the phone, to receive connection events
     */
    public static void initializeConnectionHandler(CallManager cm) {
        if (mConnectionHandler == null) {
            mConnectionHandler = new ConnectionHandler();
        }

        // pass over cm as user.obj
//Google code: cm.registerForPreciseCallStateChanged(mConnectionHandler, PHONE_STATE_CHANGED, cm);
//MTK begin:
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            MTKCallManager cmGemini = PhoneApp.getInstance().mCMGemini;
            cmGemini.registerForPreciseCallStateChangedGemini(mConnectionHandler, PHONE_STATE_CHANGED, cm, Phone.GEMINI_SIM_1); 
            cmGemini.registerForPreciseCallStateChangedGemini(mConnectionHandler, PHONE_STATE_CHANGED2, cm, Phone.GEMINI_SIM_2);
        } else {			
            cm.registerForPreciseCallStateChanged(mConnectionHandler, PHONE_STATE_CHANGED, cm);
        }
//MTK end
        // Extended NW service
        Intent intent = new Intent("com.android.ussd.IExtendedNetworkService");
        cm.getDefaultPhone().getContext().bindService(intent,
                ExtendedNetworkServiceConnection, Context.BIND_AUTO_CREATE);
        if (DBG) log("Extended NW bindService IExtendedNetworkService");

    }

    /** This class is never instantiated. */
    private PhoneUtils() {
    }
//MTK begin:
    //static method to set the audio control state.
    static void setAudioControlState(int newState) {
        sAudioBehaviourState = newState;
    }

    static int getAudioControlState() {
        return sAudioBehaviourState;
    }
//MTK end
    /**
     * Answer the currently-ringing call.
     *
     * @return true if we answered the call, or false if there wasn't
     *         actually a ringing incoming call, or some other error occurred.
     *
     * @see answerAndEndHolding()
     * @see answerAndEndActive()
     */
//MTK: Call type(not phone type) parameter should consider Gemini situation when using API: static boolean answerCall(Call ringing)
    static boolean answerCall(Call ringing) {
        if (DBG) log("answerCall()...");

        // If the ringer is currently ringing and/or vibrating, stop it
        // right now (before actually answering the call.)
        PhoneApp.getInstance().getRinger().stopRing();
//MTK add below one line:
        PhoneUtils.setAudioControlState(PhoneUtils.AUDIO_OFFHOOK);

        boolean answered = false;
/*MTK add but remove when merge;
        Call call;
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            call = ((GeminiPhone)phone).getRingingCall();
        } else {
            call = phone.getRingingCall();
        }
*/
        PhoneApp app = PhoneApp.getInstance();
        Phone phone = ringing.getPhone();
        boolean phoneIsCdma = (phone.getPhoneType() == Phone.PHONE_TYPE_CDMA);
        BluetoothHandsfree bthf = null;

        if (phoneIsCdma) {
            // Stop any signalInfo tone being played when a Call waiting gets answered
            if (ringing.getState() == Call.State.WAITING) {
                final CallNotifier notifier = app.notifier;
                notifier.stopSignalInfoTone();
            }
        }

        if (ringing != null && ringing.isRinging()) {
            if (DBG) log("answerCall: call state = " + ringing.getState());
            try {
                if (phoneIsCdma) {
                    if (app.cdmaPhoneCallState.getCurrentCallState()
                            == CdmaPhoneCallState.PhoneCallState.IDLE) {
                        // This is the FIRST incoming call being answered.
                        // Set the Phone Call State to SINGLE_ACTIVE
                        app.cdmaPhoneCallState.setCurrentCallState(
                                CdmaPhoneCallState.PhoneCallState.SINGLE_ACTIVE);
                    } else {
                        // This is the CALL WAITING call being answered.
                        // Set the Phone Call State to CONF_CALL
                        app.cdmaPhoneCallState.setCurrentCallState(
                                CdmaPhoneCallState.PhoneCallState.CONF_CALL);
                        // Enable "Add Call" option after answering a Call Waiting as the user
                        // should be allowed to add another call in case one of the parties
                        // drops off
                        app.cdmaPhoneCallState.setAddCallMenuStateAfterCallWaiting(true);

                        // If a BluetoothHandsfree is valid we need to set the second call state
                        // so that the Bluetooth client can update the Call state correctly when
                        // a call waiting is answered from the Phone.
                        bthf = app.getBluetoothHandsfree();
                        if (bthf != null) {
                            bthf.cdmaSetSecondCallState(true);
                        }
                    }
                }

                //if (DBG) log("sPhone.acceptCall");
                PhoneApp.getInstance().mCM.acceptCall(ringing);
                answered = true;

                // Always reset to "unmuted" for a freshly-answered call
                //setMute(false);

                setAudioMode();

                // Check is phone in any dock, and turn on speaker accordingly
                activateSpeakerIfDocked(phone);
            } catch (CallStateException ex) {
                Log.w(LOG_TAG, "answerCall: caught " + ex, ex);

                if (phoneIsCdma) {
                    // restore the cdmaPhoneCallState and bthf.cdmaSetSecondCallState:
                    app.cdmaPhoneCallState.setCurrentCallState(
                            app.cdmaPhoneCallState.getPreviousCallState());
                    if (bthf != null) {
                        bthf.cdmaSetSecondCallState(false);
                    }
                }
            }
        }
        return answered;
    }

    /**
     * Smart "hang up" helper method which hangs up exactly one connection,
     * based on the current Phone state, as follows:
     * <ul>
     * <li>If there's a ringing call, hang that up.
     * <li>Else if there's a foreground call, hang that up.
     * <li>Else if there's a background call, hang that up.
     * <li>Otherwise do nothing.
     * </ul>
     * @return true if we successfully hung up, or false
     *              if there were no active calls at all.
     */
    static boolean hangup(CallManager cm) {
        boolean hungup = false;
        Call ringing = cm.getFirstActiveRingingCall();
        Call fg = cm.getActiveFgCall();
        Call bg = cm.getFirstActiveBgCall();

        if (!ringing.isIdle()) {
            if (DBG) log("HANGUP ringing call");
            hungup = hangupRingingCall(ringing);
        } else if (!fg.isIdle() || fg.state == Call.State.DISCONNECTING) {
            if (DBG) log("HANGUP foreground call");
            hungup = hangup(fg);
        } else if (!bg.isIdle() || bg.state == Call.State.DISCONNECTING) {
            if (DBG) log("HANGUP background call");
            hungup = hangup(bg);
        }

        if (DBG) log("hungup=" + hungup);

        return hungup;
    }

//MTK: Call type(not phone type) parameter should consider Gemini situation when using API: static boolean hangupRingingCall(Call ringing)
    static boolean hangupRingingCall(Call ringing) {
        if (DBG) log("hangup ringing call");
/*MTK add but remove when merge:
        Call ringing;
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            ringing = ((GeminiPhone)phone).getRingingCall();
        } else {		
            ringing = phone.getRingingCall();
        }
*/
        int phoneType = ringing.getPhone().getPhoneType();

        if (phoneType == Phone.PHONE_TYPE_CDMA) {
            // CDMA: Ringing call and Call waiting hangup is handled differently.
            // For Call waiting we DO NOT call the conventional hangup(call) function
            // as in CDMA we just want to hungup the Call waiting connection.
            Call.State state = ringing.getState();
            if (state == Call.State.INCOMING) {
                if (DBG) log("hangup ringing call");
                return hangup(ringing);
            } else if (state == Call.State.WAITING) {
                if (DBG) log("hangup Call waiting call");
                final CallNotifier notifier = PhoneApp.getInstance().notifier;
                notifier.sendCdmaCallWaitingReject();
                return true;
            } else {
                // This should never happen cause hangupRingingCall should always be called
                // if the call.isRinging() returns TRUE, which basically means that the call
                // should either be in INCOMING or WAITING state
                if (DBG) log("No Ringing call to hangup");
                return false;
            }
        } else if ((phoneType == Phone.PHONE_TYPE_GSM)
                || (phoneType == Phone.PHONE_TYPE_SIP)) {
            // GSM:  Ringing Call and Call waiting, both are hungup by calling
            // hangup(call) function.
            if (DBG) log("hangup ringing call");
            return hangup(ringing);
        } else {
            throw new IllegalStateException("Unexpected phone type: " + phoneType);
        }
    }
//MTK: Call type(not phone type) parameter should consider Gemini situation when using API: static boolean hangupActiveCall(Call foreground)
    static boolean hangupActiveCall(Call foreground) {
        if (DBG) log("hangup active call");
/*MTK add but remove when merge
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            return hangup(((GeminiPhone)phone).getForegroundCall());        
        } else {
            return hangup(phone.getForegroundCall());
        }
*/
        return hangup(foreground);
    }

//MTK: Call type(not phone type) parameter should consider Gemini situation when using API: static boolean hangupHoldingCall(Call background)
    static boolean hangupHoldingCall(Call background) {
        if (DBG) log("hangup holding call");
/*MTK add but remove when merge
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            return hangup(((GeminiPhone)phone).getBackgroundCall());
        } else {
            return hangup(phone.getBackgroundCall());
        }
*/
        return hangup(background);
    }

    /**
     * Used in CDMA phones to end the complete Call session
     * @param phone the Phone object.
     * @return true if *any* call was successfully hung up
     */
    static boolean hangupRingingAndActive(Phone phone) {
        boolean hungUpRingingCall = false;
        boolean hungUpFgCall = false;
/*Google code
        Call ringingCall = phone.getRingingCall();
        Call fgCall = phone.getForegroundCall();
*/
//MTK begin:
        Call ringingCall , fgCall ;
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            ringingCall = ((GeminiPhone)phone).getRingingCall();
            fgCall = ((GeminiPhone)phone).getForegroundCall();
        } else {
            ringingCall = phone.getRingingCall();
            fgCall = phone.getForegroundCall();
        }
//MTK end

        // Hang up any Ringing Call
        if (!ringingCall.isIdle()) {
            if (DBG) log("endCallInternal: Hang up Ringing Call");
            hungUpRingingCall = hangupRingingCall(ringingCall);
        }

        // Hang up any Active Call
        if (!fgCall.isIdle()) {
            if (DBG) log("endCallInternal: Hang up Foreground Call");
            hungUpFgCall = hangupActiveCall(fgCall);
        }

        return hungUpRingingCall || hungUpFgCall;
    }

    /**
     * Trivial wrapper around Call.hangup(), except that we return a
     * boolean success code rather than throwing CallStateException on
     * failure.
     *
     * @return true if the call was successfully hung up, or false
     *         if the call wasn't actually active.
     */
    static boolean hangup(Call call) {
        try {
            CallManager cm = PhoneApp.getInstance().mCM;
            //1A1H1R : Resolved for ALPS00036146
            if (call.getState() == Call.State.ACTIVE && cm.hasActiveBgCall() && !cm.hasActiveRingingCall()) {
                // handle foreground call hangup while there is background call
                cm.hangupForegroundResumeBackground(cm.getFirstActiveBgCall());
            } else if (call.getState() == Call.State.ACTIVE && cm.hasActiveBgCall() && cm.hasActiveRingingCall()) {
                Call fg = cm.getActiveFgCall();
                Call bg = cm.getFirstActiveBgCall();
                if (fg.getPhone() == bg.getPhone() 
                        && fg.getPhone().getPhoneType() == Phone.PHONE_TYPE_GSM
                        && cm.getRingingPhone().getPhoneType() == Phone.PHONE_TYPE_SIP) {
                    cm.hangupActiveCall(call);
                } else {
                    call.hangup();
                }
            } else {
                call.hangup();
            }
            return true;
        } catch (CallStateException ex) {
            Log.e(LOG_TAG, "Call hangup: caught " + ex, ex);
        }

        return false;
    }

    /**
     * Trivial wrapper around Connection.hangup(), except that we silently
     * do nothing (rather than throwing CallStateException) if the
     * connection wasn't actually active.
     */
    static void hangup(Connection c) {
        try {
            if (c != null) {
                c.hangup();
            }
        } catch (CallStateException ex) {
            Log.w(LOG_TAG, "Connection hangup: caught " + ex, ex);
        }
    }

    static boolean answerAndEndHolding(CallManager cm, Call ringing) {
        if (DBG) log("end holding & answer waiting: 1");
        if (!hangupHoldingCall(cm.getFirstActiveBgCall())) {
            Log.e(LOG_TAG, "end holding failed!");
            return false;
        }

        if (DBG) log("end holding & answer waiting: 2");
        return answerCall(ringing);

    }

    static boolean answerAndEndActive(CallManager cm, Call ringing) {
        if (DBG) log("answerAndEndActive()...");

        Phone fgPhone = cm.getActiveFgCall().getPhone();
        Phone ringingPhone = ringing.getPhone();

        // Unlike the answerCall() method, we *don't* need to stop the
        // ringer or change audio modes here since the user is already
        // in-call, which means that the audio mode is already set
        // correctly, and that we wouldn't have started the ringer in the
        // first place.

        // hanging up the active call also accepts the waiting call
        // while active call and waiting call are from the same phone
        // i.e. both from GSM phone
        if ( !hangupActiveCall(cm.getActiveFgCall())) {
            Log.w(LOG_TAG, "end active call failed!");
            return false;
        }

        // since hangupActiveCall() also accepts the ringing call
        // check if the ringing call was already answered or not
        // only answer it when the call still is ringing
        /*if (ringing.isRinging()) {
            return answerCall(ringing);
        }*/
        if (fgPhone != ringingPhone
            || (fgPhone == ringingPhone && (fgPhone instanceof SipPhone))) {
            return answerCall(ringing);
        }

        return true;
    }

    /**
     * For a CDMA phone, advance the call state upon making a new
     * outgoing call.
     *
     * <pre>
     *   IDLE -> SINGLE_ACTIVE
     * or
     *   SINGLE_ACTIVE -> THRWAY_ACTIVE
     * </pre>
     * @param app The phone instance.
     */
    private static void updateCdmaCallStateOnNewOutgoingCall(PhoneApp app) {
        if (app.cdmaPhoneCallState.getCurrentCallState() ==
            CdmaPhoneCallState.PhoneCallState.IDLE) {
            // This is the first outgoing call. Set the Phone Call State to ACTIVE
            app.cdmaPhoneCallState.setCurrentCallState(
                CdmaPhoneCallState.PhoneCallState.SINGLE_ACTIVE);
        } else {
            // This is the second outgoing call. Set the Phone Call State to 3WAY
            app.cdmaPhoneCallState.setCurrentCallState(
                CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE);
        }
    }
//MTK begin:
    static void placeCallRegister(Phone phone) {
    	boolean isSipCall = phone.getPhoneType() == Phone.PHONE_TYPE_SIP;
        if (DBG) log("placeCallRegister: ");
        if (FeatureOption.MTK_GEMINI_SUPPORT && !isSipCall) {
            MTKCallManager cmGemini = PhoneApp.getInstance().mCMGemini;    	
            cmGemini.registerForSpeechInfoGemini(mConnectionHandler, PHONE_SPEECH_INFO, cmGemini, Phone.GEMINI_SIM_1);
            cmGemini.registerForSpeechInfoGemini(mConnectionHandler, PHONE_SPEECH_INFO2, cmGemini, Phone.GEMINI_SIM_2);
        } else {
            CallManager cm = PhoneApp.getInstance().mCM;		    
            cm.registerForSpeechInfo(mConnectionHandler, PHONE_SPEECH_INFO, cm);
        }		
    }
//MTK end

    /**
     * Dial the number using the phone passed in.
     *
     * @param phone the Phone object.
     * @param number to be dialed as requested by the user.
     * @param contactRef that triggered the call. Either a 'tel:' or a
     * 'content://contacts' uri depending on how the call was
     * initiated (dialpad vs contact).
     * @return either CALL_STATUS_DIALED, CALL_STATUS_DIALED_MMI, or CALL_STATUS_FAILED
     */
//MTK modified below one line:
    static int placeCallExt(Phone phone, String number, Uri contactRef, int simId) {
        int status = CALL_STATUS_DIALED;
        boolean isSipCall = phone.getPhoneType() == Phone.PHONE_TYPE_SIP;
        try {
            if (DBG) log("placeCall: '" + number + "'...");

//Google code: Connection cn = PhoneApp.getInstance().mCM.dial(phone, number);
//MTK begin:
			placeCallRegister(phone);
            Connection cn;
            if (FeatureOption.MTK_GEMINI_SUPPORT && !isSipCall) {
                if (simId==Phone.GEMINI_SIM_1 || simId == Phone.GEMINI_SIM_2) {
                log("placeCallExt , simId " + simId);
                    cn = PhoneApp.getInstance().mCMGemini.dialGemini(phone, number, simId);
                } else {
                    int defaultSimId = SystemProperties.getInt(Phone.GEMINI_DEFAULT_SIM_PROP, -1);
                    log("placeCallExt , default simId " + simId);
                    cn = PhoneApp.getInstance().mCMGemini.dialGemini(phone, number, defaultSimId);
                }
            } else {
                cn = PhoneApp.getInstance().mCM.dial(phone, number);
            }
//MTK end
            if (DBG) log("===> phone.dial() returned: " + cn);

            int phoneType = phone.getPhoneType();

            // On GSM phones, null is returned for MMI codes
            if (cn == null) {
                if (phoneType == Phone.PHONE_TYPE_GSM) {
                    if (DBG) log("dialed MMI code: " + number);
                    status = CALL_STATUS_DIALED_MMI;
                    // Set dialed MMI command to service
                    if (mNwService != null) {
                        try {
                            mNwService.setMmiString(number);
                            if (DBG) log("Extended NW bindService setUssdString (" + number + ")");
                        } catch (RemoteException e) {
                            mNwService = null;
                        }
                    }
                } else {
                    status = PhoneUtils.CALL_STATUS_FAILED;
                }
            } else {
                PhoneApp app = PhoneApp.getInstance();

                if (phoneType == Phone.PHONE_TYPE_CDMA) {
                    updateCdmaCallStateOnNewOutgoingCall(app);
                }
//MTk add below one line:
                PhoneUtils.setAudioControlState(PhoneUtils.AUDIO_OFFHOOK);

                // phone.dial() succeeded: we're now in a normal phone call.
                // attach the URI to the CallerInfo Object if it is there,
                // otherwise just attach the Uri Reference.
                // if the uri does not have a "content" scheme, then we treat
                // it as if it does NOT have a unique reference.
                String content = phone.getContext().getContentResolver().SCHEME_CONTENT;
                if ((contactRef != null) && (contactRef.getScheme().equals(content))) {
                    Object userDataObject = cn.getUserData();
                    if (userDataObject == null) {
                        cn.setUserData(contactRef);
                    } else {
                        // TODO: This branch is dead code, we have
                        // just created the connection 'cn' which has
                        // no user data (null) by default.
                        if (userDataObject instanceof CallerInfo) {
                            ((CallerInfo) userDataObject).contactRefUri = contactRef;
                        } else {
                            ((CallerInfoToken) userDataObject).currentInfo.contactRefUri =
                                contactRef;
                        }
                    }
                }
//Google code:  setAudioMode();
//MTK code:     //setAudioMode(phone.getContext(), AudioManager.MODE_IN_CALL);
//Merge code, may be not right
                //setAudioMode();

                // Check is phone in any dock, and turn on speaker accordingly
                activateSpeakerIfDocked(phone);
            }
        } catch (CallStateException ex) {
            Log.w(LOG_TAG, "Exception from phone.dial()", ex);
            status = CALL_STATUS_FAILED;
        }

        return status;
    }
//MTK begin:
    /**
     * Dial the number using the phone passed in.
     *
     * @param phone the Phone object.
     * @param number to be dialed as requested by the user.
     * @param contactRef that triggered the call. Either a 'tel:' or a
     * 'content://contacts' uri depending on how the call was
     * initiated (dialpad vs contact).
     * @return either CALL_STATUS_DIALED, CALL_STATUS_DIALED_MMI, or CALL_STATUS_FAILED
     */
    static int placeCall(Phone phone, String number, Uri contactRef) {
        return placeCallExt(phone, number, contactRef, -1);
    }

    static int placeCallGemini(Phone phone, String number, Uri contactRef, int simId) {
        return placeCallExt(phone, number, contactRef, simId);
    }
//MTK end

    /**
     * Dial the number using a 3rd party provider gateway.  Should
     * *NOT* be called if the number is either:
     * . An emergency one
     * . A GSM MMI code
     * . A CDMA feature code
     * None of the above is  checked in this method, it's the caller's
     * responsability to make sure the number is 'valid'.
     *
     * If the connection is establised, this method issues a sync call
     * that may block to query the caller info.
     * TODO: Change the logic to use the async query.
     *
     * @param phone the Phone object.
     * @param context To perform the CallerInfo query.
     * @param number to be dialed as requested by the user. This is
     * NOT the phone number to connect to. It is used only to build the
     * call card and to update the call log. See above for restrictions.
     * @param contactRef that triggered the call. Typically a 'tel:'
     * uri but can also be a 'content://contacts' one.
     * @param gatewayUri Is the address used to setup the connection.
     * @return either CALL_STATUS_DIALED or CALL_STATUS_FAILED
     */
//MTK modified below one line:
    static int placeCallViaExt(Context context, Phone phone,
                            String number, Uri contactRef, Uri gatewayUri, int simId) {
        if (DBG) log("placeCallVia: '" + number + "' GW:'" + gatewayUri + "'");

        // TODO: 'tel' should be a contant defined in framework base
        // somewhere (it is in webkit.)
        if (null == gatewayUri || !"tel".equals(gatewayUri.getScheme())) {
            Log.e(LOG_TAG, "Unsupported URL:" + gatewayUri);
            return CALL_STATUS_FAILED;
        }

        // We can use getSchemeSpecificPart because we don't allow #
        // in the gateway numbers (treated a fragment delim.) However
        // if we allow more complex gateway numbers sequence (with
        // passwords or whatnot) that use #, this may break.
        // TODO: Need to support MMI codes.
        String gatewayNumber = gatewayUri.getSchemeSpecificPart();
        Connection connection;
        try {
//Google code: connection = PhoneApp.getInstance().mCM.dial(phone, gatewayNumber);
            if (FeatureOption.MTK_GEMINI_SUPPORT) {
                if (simId==Phone.GEMINI_SIM_1 || simId == Phone.GEMINI_SIM_2) {
                    log("placeCallExt , simId " + simId);
                    connection = PhoneApp.getInstance().mCMGemini.dialGemini(phone, gatewayNumber, simId);
                } else {
                    int defaultSimId = SystemProperties.getInt(Phone.GEMINI_DEFAULT_SIM_PROP, -1);
                    log("placeCallExt , default simId " + simId);
                    connection = PhoneApp.getInstance().mCMGemini.dialGemini(phone, gatewayNumber, defaultSimId);
                }
            } else {
                connection = PhoneApp.getInstance().mCM.dial(phone, gatewayNumber);
            }
/*MTK begin:
            if (FeatureOption.MTK_GEMINI_SUPPORT) {
                if (simId==Phone.GEMINI_SIM_1 || simId == Phone.GEMINI_SIM_2) {
                    log("placeCallViaExt , simId " + simId);            
                    connection =  ((GeminiPhone)phone).dialGemini(gatewayNumber, simId);                    
                } else {
                    log("placeCallViaExt , default simId " + simId);            
                    connection =  ((GeminiPhone)phone).dial(gatewayNumber);                    
                }
            } else {
                connection = phone.dial(gatewayNumber);
            }
MTK end*/
        } catch (CallStateException ex) {
            Log.e(LOG_TAG, "Exception dialing gateway", ex);
            connection = null;
        }

        if (null == connection) {
            Log.e(LOG_TAG, "Got null connection.");
            return CALL_STATUS_FAILED;
        }

        PhoneApp app = PhoneApp.getInstance();
        boolean phoneIsCdma = (phone.getPhoneType() == Phone.PHONE_TYPE_CDMA);

        if (phoneIsCdma) {
            updateCdmaCallStateOnNewOutgoingCall(app);
        }
//MTK add below one line:
        PhoneUtils.setAudioControlState(PhoneUtils.AUDIO_OFFHOOK);

        // Clean up the number to be displayed.
        if (phoneIsCdma) {
            number = CdmaConnection.formatDialString(number);
        }
        number = PhoneNumberUtils.extractNetworkPortion(number);
        number = PhoneNumberUtils.convertKeypadLettersToDigits(number);
        number = PhoneNumberUtils.formatNumber(number);

        // Get the caller info synchronously because we need the final
        // CallerInfo object to update the dialed number with the one
        // requested by the user (and not the provider's gateway number).
        CallerInfo info = null;

        if (ContentResolver.SCHEME_CONTENT.equals(contactRef.getScheme())) {
            info = CallerInfo.getCallerInfo(context, contactRef);
        }

        // Fallback, lookup contact using the phone number if the
        // contact's URI scheme was not content:// or if is was but
        // the lookup failed.
        if (null == info) {
//Google code: info = CallerInfo.getCallerInfo(context, number);
//MTK begin:
            if (FeatureOption.MTK_GEMINI_SUPPORT) {
                info = CallerInfo.getCallerInfoGemini(context, number, simId);
            } else {
                info = CallerInfo.getCallerInfo(context, number);
            }
//MTK end
        }
        if(null != info) {
            info.phoneNumber = number;
            connection.setUserData(info);
        }

        setAudioMode();
        return CALL_STATUS_DIALED;
    }
//MTK begin:
    static int placeCallVia(Context context, Phone phone,
                            String number, Uri contactRef, Uri gatewayUri) {
        if (DBG) log("placeCallVia: '" + number + "' GW:'" + gatewayUri + "'");

        // TODO: 'tel' should be a contant defined in framework base
        // somewhere (it is in webkit.)
        if (null == gatewayUri || !"tel".equals(gatewayUri.getScheme())) {
            Log.e(LOG_TAG, "Unsupported URL:" + gatewayUri);
            return CALL_STATUS_FAILED;
        }
        return placeCallViaExt(context, phone, number, contactRef, gatewayUri, -1);
    }
   

    static int placeCallViaGemini(Context context, Phone phone,
                            String number, Uri contactRef, Uri gatewayUri, int simId) {
        if (DBG) log("placeCallViaGemini: '" + number + "' GW:'" + gatewayUri + "'"+simId);

        // TODO: 'tel' should be a contant defined in framework base
        // somewhere (it is in webkit.)
        if (null == gatewayUri || !"tel".equals(gatewayUri.getScheme())) {
            Log.e(LOG_TAG, "Unsupported URL:" + gatewayUri);
            return CALL_STATUS_FAILED;
        }
        return placeCallViaExt(context, phone, number, contactRef, gatewayUri, simId);
    }   
//MTK end

    /**
     * Wrapper function to control when to send an empty Flash command to the network.
     * Mainly needed for CDMA networks, such as scenarios when we need to send a blank flash
     * to the network prior to placing a 3-way call for it to be successful.
     */
    static void sendEmptyFlash(Phone phone) {
        if (phone.getPhoneType() == Phone.PHONE_TYPE_CDMA) {
            Call fgCall = phone.getForegroundCall();
            if (fgCall.getState() == Call.State.ACTIVE) {
                // Send the empty flash
                if (DBG) Log.d(LOG_TAG, "onReceive: (CDMA) sending empty flash to network");
                switchHoldingAndActive(phone.getBackgroundCall());
            }
        }
    }

    /**
     * @param heldCall is the background call want to be swapped
     */
/* MTK add but remove when merge:
    static void switchHoldingAndActive(Phone phone) {
        try {
            if (DBG) log("switchHoldingAndActive");
            if (FeatureOption.MTK_GEMINI_SUPPORT) {
                if (((GeminiPhone)phone).getStateGemini(Phone.GEMINI_SIM_2) != Phone.State.IDLE) {                    
                    Log.w(LOG_TAG, "switchHoldingAndActive -- Phone.switchHoldingAndActive sim2");
                    ((GeminiPhone)phone).switchHoldingAndActiveGemini(Phone.GEMINI_SIM_2);
                } else {
                    Log.w(LOG_TAG, "switchHoldingAndActive -- Phone.switchHoldingAndActive sim1");
                    ((GeminiPhone)phone).switchHoldingAndActiveGemini(Phone.GEMINI_SIM_1);
                }            			
            } else {
                phone.switchHoldingAndActive();
            }			
        } catch (CallStateException ex) {
            Log.w(LOG_TAG, "switchHoldingAndActive: caught " + ex, ex);
        }
    }
*/
//MTK: should consider Gemini situation when using this API
    static void switchHoldingAndActive(Call heldCall) {
        try {
            if (DBG) log("switchHoldingAndActive");
            CallManager cm = PhoneApp.getInstance().mCM;
            if (heldCall.isIdle()) {
                // no heldCall, so it is to hold active call
                cm.switchHoldingAndActive(cm.getFgPhone().getBackgroundCall());
            } else {
                // has particular heldCall, so to switch
                cm.switchHoldingAndActive(heldCall);
            }
            setAudioMode(cm);
        } catch (CallStateException ex) {
            Log.w(LOG_TAG, "switchHoldingAndActive: caught " + ex, ex);
        }
    }

    /**
     * Restore the mute setting from the earliest connection of the
     * foreground call.
     */
    static Boolean restoreMuteState() {
        Phone phone = PhoneApp.getInstance().mCM.getFgPhone();

        //get the earliest connection
//Google: Connection c = phone.getForegroundCall().getEarliestConnection();

//below is an work around for framework, Wenqi supply method, need Wenqi supply right API
/*MTK 2.2:
        Connection c;
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            c = ((GeminiPhone)phone).getForegroundCall().getEarliestConnection();
        } else {
            c = phone.getForegroundCall().getEarliestConnection();
        }
*/
        Connection c;
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            c = PhoneApp.getInstance().mCM.getActiveFgCall().getEarliestConnection();
        } else {
            c = phone.getForegroundCall().getEarliestConnection();
        }
        // only do this if connection is not null.
        if (c != null) {

            int phoneType = phone.getPhoneType();

            // retrieve the mute value.
            Boolean shouldMute = null;

            // In CDMA, mute is not maintained per Connection. Single mute apply for
            // a call where  call can have multiple connections such as
            // Three way and Call Waiting.  Therefore retrieving Mute state for
            // latest connection can apply for all connection in that call
            if (phoneType == Phone.PHONE_TYPE_CDMA) {
                shouldMute = sConnectionMuteTable.get(
                        phone.getForegroundCall().getLatestConnection());
            } else if ((phoneType == Phone.PHONE_TYPE_GSM)
                    || (phoneType == Phone.PHONE_TYPE_SIP)) {
                shouldMute = sConnectionMuteTable.get(c);

/*MTK add but remove when merge. I think it's right to remove, but just in case.
                if (FeatureOption.MTK_GEMINI_SUPPORT) {
                    shouldMute = sConnectionMuteTable.get(
                            ((GeminiPhone)phone).getForegroundCall().getEarliestConnection());
                } else {
                    shouldMute = sConnectionMuteTable.get(
                            phone.getForegroundCall().getEarliestConnection());
                }
*/
            }
            if (shouldMute == null) {
                if (DBG) log("problem retrieving mute value for this connection.");
                shouldMute = Boolean.FALSE;
            }

            // set the mute value and return the result.
            setMute (shouldMute.booleanValue());
            return shouldMute;
        }
        return Boolean.valueOf(getMute());
    }

    static void mergeCalls() {
        mergeCalls(PhoneApp.getInstance().mCM);
    }

    static void mergeCalls(CallManager cm) {
        int phoneType = cm.getFgPhone().getPhoneType();
        if (phoneType == Phone.PHONE_TYPE_CDMA) {
            if (DBG) log("mergeCalls");
            PhoneApp app = PhoneApp.getInstance();
            if (app.cdmaPhoneCallState.getCurrentCallState()
                    == CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE) {
                // Set the Phone Call State to conference
                app.cdmaPhoneCallState.setCurrentCallState(
                        CdmaPhoneCallState.PhoneCallState.CONF_CALL);

                // Send flash cmd
                // TODO: Need to change the call from switchHoldingAndActive to
                // something meaningful as we are not actually trying to swap calls but
                // instead are merging two calls by sending a Flash command.
                switchHoldingAndActive(cm.getFirstActiveBgCall());
            }
        } else {
            try {
                if (DBG) log("mergeCalls");
                cm.conference(cm.getFirstActiveBgCall());
            } catch (CallStateException ex) {
                Log.w(LOG_TAG, "mergeCalls: caught " + ex, ex);
                if (phoneType == Phone.PHONE_TYPE_SIP)
                {
                	InCallScreen incall = PhoneApp.getInstance().getInCallScreenInstance();
                	AsyncResult ar = new AsyncResult(null,  Phone.SuppService.CONFERENCE, null);
                	if (incall != null) incall.onSuppServiceFailed(ar);
                }
            }
        }
    }

    static void separateCall(Connection c) {
        try {
            if (DBG) log("separateCall: " + c.getAddress());
            c.separate();
        } catch (CallStateException ex) {
            Log.w(LOG_TAG, "separateCall: caught " + ex, ex);
        }
    }

    /**
     * Handle the MMIInitiate message and put up an alert that lets
     * the user cancel the operation, if applicable.
     *
     * @param context context to get strings.
     * @param mmiCode the MmiCode object being started.
     * @param buttonCallbackMessage message to post when button is clicked.
     * @param previousAlert a previous alert used in this activity.
     * @return the dialog handle
     */
    static Dialog displayMMIInitiate(Context context,
                                          MmiCode mmiCode,
                                          Message buttonCallbackMessage,
                                          Dialog previousAlert) {
        if (DBG) log("displayMMIInitiate: " + mmiCode);
        if (previousAlert != null) {
            previousAlert.dismiss();
        }

        // The UI paradigm we are using now requests that all dialogs have
        // user interaction, and that any other messages to the user should
        // be by way of Toasts.
        //
        // In adhering to this request, all MMI initiating "OK" dialogs
        // (non-cancelable MMIs) that end up being closed when the MMI
        // completes (thereby showing a completion dialog) are being
        // replaced with Toasts.
        //
        // As a side effect, moving to Toasts for the non-cancelable MMIs
        // also means that buttonCallbackMessage (which was tied into "OK")
        // is no longer invokable for these dialogs.  This is not a problem
        // since the only callback messages we supported were for cancelable
        // MMIs anyway.
        //
        // A cancelable MMI is really just a USSD request. The term
        // "cancelable" here means that we can cancel the request when the
        // system prompts us for a response, NOT while the network is
        // processing the MMI request.  Any request to cancel a USSD while
        // the network is NOT ready for a response may be ignored.
        //
        // With this in mind, we replace the cancelable alert dialog with
        // a progress dialog, displayed until we receive a request from
        // the the network.  For more information, please see the comments
        // in the displayMMIComplete() method below.
        //
        // Anything that is NOT a USSD request is a normal MMI request,
        // which will bring up a toast (desribed above).
        // Optional code for Extended USSD running prompt
        if (mNwService != null) {
            if (DBG) log("running USSD code, displaying indeterminate progress.");
            // create the indeterminate progress dialog and display it.
            ProgressDialog pd = new ProgressDialog(context);
            CharSequence textmsg = "";
            try {
                textmsg = mNwService.getMmiRunningText();

            } catch (RemoteException e) {
                mNwService = null;
                textmsg = context.getText(R.string.ussdRunning);
            }
            if (DBG) log("Extended NW displayMMIInitiate (" + textmsg+ ")");
            pd.setMessage(textmsg);
            pd.setCancelable(false);
            pd.setIndeterminate(true);
            pd.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            pd.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG);
            pd.show();
            // trigger a 15 seconds timeout to clear this progress dialog
            mMmiTimeoutCbMsg = buttonCallbackMessage;
            try {
                mMmiTimeoutCbMsg.getTarget().sendMessageDelayed(buttonCallbackMessage, 15000);
            } catch(NullPointerException e) {
                mMmiTimeoutCbMsg = null;
            }
            return pd;
        }

        boolean isCancelable = (mmiCode != null) && mmiCode.isCancelable();

        if (!isCancelable) {
            if (DBG) log("not a USSD code, displaying status toast.");
            CharSequence text = context.getText(R.string.mmiStarted);
            Toast.makeText(context, text, Toast.LENGTH_SHORT)
                .show();
            return null;
        } else {
            if (DBG) log("running USSD code, displaying indeterminate progress.");

            // create the indeterminate progress dialog and display it.
            ProgressDialog pd = new ProgressDialog(context);
            pd.setMessage(context.getText(R.string.ussdRunning));
            pd.setCancelable(false);
            pd.setIndeterminate(true);
            pd.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

            pd.show();

            return pd;
        }

    }

    /**
     * Handle the MMIComplete message and fire off an intent to display
     * the message.
     *
     * @param context context to get strings.
     * @param mmiCode MMI result.
     * @param previousAlert a previous alert used in this activity.
     */
//MTK begin:
    static void displayMMIComplete(final Phone phone, Context context, final MmiCode mmiCode,
            Message dismissCallbackMessage,
            AlertDialog previousAlert) {
            displayMMICompleteExt(phone, context, mmiCode, dismissCallbackMessage, previousAlert, Phone.GEMINI_SIM_1);
    }
	
    static void displayMMICompleteExt(final Phone phone, Context context, final MmiCode mmiCode,
            Message dismissCallbackMessage, AlertDialog previousAlert, final int simId) {
//MTK end
        CharSequence text;
        curCode = mmiCode;
        int title = 0;  // title for the progress dialog, if needed.
        MmiCode.State state = mmiCode.getState();

        if (DBG) log("displayMMIComplete: state=" + state);
        // Clear timeout trigger message
        if(mMmiTimeoutCbMsg != null) {
            try{
                mMmiTimeoutCbMsg.getTarget().removeMessages(mMmiTimeoutCbMsg.what);
                if (DBG) log("Extended NW displayMMIComplete removeMsg");
            } catch (NullPointerException e) {
            }
            mMmiTimeoutCbMsg = null;
        }


        switch (state) {
            case PENDING:
                // USSD code asking for feedback from user.
                text = mmiCode.getMessage();
                if (DBG) log("- using text from PENDING MMI message: '" + text + "'");
                break;
            case CANCELLED:
                text = context.getText(R.string.mmiCancelled);
                break;
            case COMPLETE:
            	mbMmiFinished = true;
                if (PhoneApp.getInstance().getPUKEntryActivity() != null) {
                    // if an attempt to unPUK the device was made, we specify
                    // the title and the message here.
                    title = com.android.internal.R.string.PinMmi;
                    text = context.getText(R.string.puk_unlocked);
                    break;
                }
                // All other conditions for the COMPLETE mmi state will cause
                // the case to fall through to message logic in common with
                // the FAILED case.

            case FAILED:
                text = mmiCode.getMessage();
                if (DBG) log("- using text from MMI message: '" + text + "'");
//MTK begin:
                if (mDialog != null) {
                    //DismissMMIDialog();
                    mDialog = null;
                    if (mUssdActivity != null) {
                        mUssdActivity.dismiss();
                    }
                    return ;
                }
//MTK end
                break;
            default:
                throw new IllegalStateException("Unexpected MmiCode state: " + state);
        }

        if (previousAlert != null) {
            previousAlert.dismiss();
        }

        // Check to see if a UI exists for the PUK activation.  If it does
        // exist, then it indicates that we're trying to unblock the PUK.
        PhoneApp app = PhoneApp.getInstance();
        if ((app.getPUKEntryActivity() != null) && (state == MmiCode.State.COMPLETE)) {
            if (DBG) log("displaying PUK unblocking progress dialog.");

            // create the progress dialog, make sure the flags and type are
            // set correctly.
            ProgressDialog pd = new ProgressDialog(app);
            pd.setTitle(title);
            pd.setMessage(text);
            pd.setCancelable(false);
            pd.setIndeterminate(true);
            pd.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG);
            pd.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

            // display the dialog
            pd.show();

            // indicate to the Phone app that the progress dialog has
            // been assigned for the PUK unlock / SIM READY process.
            app.setPukEntryProgressDialog(pd);

        } else {
            // In case of failure to unlock, we'll need to reset the
            // PUK unlock activity, so that the user may try again.
            if (app.getPUKEntryActivity() != null) {
                app.setPukEntryActivity(null);
            }

            // A USSD in a pending state means that it is still
            // interacting with the user.
            if (state != MmiCode.State.PENDING) {
                if (DBG) log("MMI code has finished running.");

                // Replace response message with Extended Mmi wording
                if (mNwService != null) {
                    try {
                        text = mNwService.getUserMessage(text);
                    } catch (RemoteException e) {
                        mNwService = null;
                    }
                    if (DBG) log("Extended NW displayMMIInitiate (" + text + ")");
                    if (text == null || text.length() == 0)
                        return;
                }

/*Google code:  // displaying system alert dialog on the screen instead of
                // using another activity to display the message.  This
                // places the message at the forefront of the UI.
                AlertDialog newDialog = new AlertDialog.Builder(context)
                        .setMessage(text)
                        .setPositiveButton(R.string.ok, null)
                        .setCancelable(true)
                        .create();

                newDialog.getWindow().setType(
                        WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG);
                newDialog.getWindow().addFlags(
                        WindowManager.LayoutParams.FLAG_DIM_BEHIND);

                newDialog.show();
*/
//MTK begin:
                // inflate the layout with the scrolling text area for the dialog.
                LayoutInflater inflater = (LayoutInflater) context.getSystemService(
                        Context.LAYOUT_INFLATER_SERVICE);
                View dialogView = inflater.inflate(R.layout.dialog_ussd_response, null);
                TextView msg = (TextView) dialogView.findViewById(R.id.msg);
                msg.setText(text);    
                TextView ussdUpdateView = (TextView) dialogView.findViewById(R.id.ussd_update);
                ussdUpdateView.setVisibility(View.GONE);
                EditText inputText = (EditText) dialogView.findViewById(R.id.input_field);
                inputText.setVisibility(View.GONE);
                /*
                 * auto update the UI,because some App change the Phone mode that will cause 
                 * some USSD response information lost.
                 * For example ,when Camera first init.
                 */
                autoUpdateUssdReponseUi(dialogView);
                // displaying system alert dialog on the screen instead of
                // using another activity to display the message.  This
                // places the message at the forefront of the UI.
//                AlertDialog newDialog = new AlertDialog.Builder(context)
//                        .setView(dialogView)
//                        .setPositiveButton(R.string.ok, null)
//                        .setCancelable(true)
//                        .create();
//
//                newDialog.getWindow().setType(
//                        WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG);
//                newDialog.getWindow().addFlags(
//                        WindowManager.LayoutParams.FLAG_DIM_BEHIND);

                //newDialog.show();
                
                displayMmiDialog(context, text, UssdAlertActivity.USSD_DIALOG_NOTIFICATION, simId);
                
//MTk end
            } else {
                if (DBG) log("USSD code has requested user input. Constructing input dialog.");

                // USSD MMI code that is interacting with the user.  The
                // basic set of steps is this:
                //   1. User enters a USSD request
                //   2. We recognize the request and displayMMIInitiate
                //      (above) creates a progress dialog.
                //   3. Request returns and we get a PENDING or COMPLETE
                //      message.
                //   4. These MMI messages are caught in the PhoneApp
                //      (onMMIComplete) and the InCallScreen
                //      (mHandler.handleMessage) which bring up this dialog
                //      and closes the original progress dialog,
                //      respectively.
                //   5. If the message is anything other than PENDING,
                //      we are done, and the alert dialog (directly above)
                //      displays the outcome.
                //   6. If the network is requesting more information from
                //      the user, the MMI will be in a PENDING state, and
                //      we display this dialog with the message.
                //   7. User input, or cancel requests result in a return
                //      to step 1.  Keep in mind that this is the only
                //      time that a USSD should be canceled.

                // inflate the layout with the scrolling text area for the dialog.
                LayoutInflater inflater = (LayoutInflater) context.getSystemService(
                        Context.LAYOUT_INFLATER_SERVICE);
                View dialogView = inflater.inflate(R.layout.dialog_ussd_response, null);
//MTK begin:
                TextView msg = (TextView) dialogView.findViewById(R.id.msg);
                msg.setText(text);
                msg.setWidth(MIN_WIDTH);
//MTK end
                // get the input field.
                final EditText inputText = (EditText) dialogView.findViewById(R.id.input_field);
//MTK add below line:
                inputText.setFilters(new InputFilter[] {new InputFilter.LengthFilter(182)});
                // specify the dialog's click listener, with SEND and CANCEL logic.
                final DialogInterface.OnClickListener mUSSDDialogListener =
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            switch (whichButton) {
                                case DialogInterface.BUTTON_POSITIVE:
//Google code:                       phone.sendUssdResponse(inputText.getText().toString());
//MTK begin:
                                     if (FeatureOption.MTK_GEMINI_SUPPORT) {
                                         Log.w(LOG_TAG, "displayMMIcomplete sendUssdResponseGemini button, simId:"+simId); 
                                         Log.w(LOG_TAG, "displayMMIcomplete USSR string :"+inputText.getText().toString()); 										
                                         ((GeminiPhone)phone).sendUssdResponseGemini(inputText.getText().toString(), simId);
                                     } else {
                                         phone.sendUssdResponse(inputText.getText().toString());
                                     }									
                                     mDialog = null;
                                     mUssdActivity = null;
//MTK end
                                     break;
                                case DialogInterface.BUTTON_NEGATIVE:
                                    if (mmiCode.isCancelable()) {
                                        mmiCode.cancel();
                                    }
//MTK add below line:
                                    mDialog = null;
                                    mUssdActivity = null;
                                    break;
                            }
                        }
                    };
///MTK begin:
             final DialogInterface.OnCancelListener mUSSDDialogCancelListener =
                        new DialogInterface.OnCancelListener() {
                            
                            public void onCancel(DialogInterface dialog) {
                                // TODO Auto-generated method stub
                                if (mmiCode.isCancelable()) {
                                    mmiCode.cancel();
                                }
                            }
                        };
///MTK end
                // build the dialog
                final AlertDialog newDialog = new AlertDialog.Builder(context)
                        .setView(dialogView)
                        .setPositiveButton(R.string.send_button, mUSSDDialogListener)
                        .setNegativeButton(R.string.cancel, mUSSDDialogListener)
                        .setCancelable(false)
                        .create();
//MTK begin:
                newDialog.setOnCancelListener(mUSSDDialogCancelListener);
                mDialog = newDialog;
//MTK end
                // attach the key listener to the dialog's input field and make
                // sure focus is set.
                final View.OnKeyListener mUSSDDialogInputListener =
                    new View.OnKeyListener() {
                        public boolean onKey(View v, int keyCode, KeyEvent event) {
                            switch (keyCode) {
                                case KeyEvent.KEYCODE_CALL:
//MTK add below one line:
                                    return true;
                                case KeyEvent.KEYCODE_ENTER:
//Google code:                      phone.sendUssdResponse(inputText.getText().toString());
//MTK begin:
                                    if(event.getAction() == event.ACTION_UP) {
                                        if (FeatureOption.MTK_GEMINI_SUPPORT) {
                                            Log.w(LOG_TAG, "sdisplayMMIcomplete sendUssdResponseGemini key");
                                            if (inputText.getText().toString().length() == 0) {
                                                return false;
                                            }
                                            ((GeminiPhone)phone).sendUssdResponseGemini(inputText.getText().toString(), simId);
                                        } else {
                                            phone.sendUssdResponse(inputText.getText().toString());
                                        }						 
                                        newDialog.dismiss();
                                        return true;
                                    } else {
                                        // do not process "enter" key when keydown, avoid "enter" key in inputText
                                        return true;
                                    }
//MTK end
                            }
                            return false;
                        }
                    };
                inputText.setOnKeyListener(mUSSDDialogInputListener);
                inputText.requestFocus();

                // set the window properties of the dialog
                newDialog.getWindow().setType(
                        WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG);
                newDialog.getWindow().addFlags(
                        WindowManager.LayoutParams.FLAG_DIM_BEHIND);

                // now show the dialog!
                //newDialog.show();
                displayMmiDialog(context, text, UssdAlertActivity.USSD_DIALOG_REQUEST, simId);
                
//MTK begin:
//                if (newDialog != null) {
//                    Button bt = newDialog.getButton(DialogInterface.BUTTON_POSITIVE);
//                    bt.setEnabled(false);
//                }
//                inputText.addTextChangedListener(new TextWatcher(){
//                    public void beforeTextChanged(CharSequence s, int start,
//                            int count, int after) {
//                    }
//                    
//                    public void onTextChanged(CharSequence s, int start, int before, int count) {
//                    }
//                    
//                    public void afterTextChanged(Editable s) {
//                        int count = s == null ? 0 : s.length();
//                        if (count > 0) {
//                            newDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
//                        } else {
//                            newDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
//                        }
//                    }
//                });
//MTK end
            }
        }
    }

    /**
     * Cancels the current pending MMI operation, if applicable.
     * @return true if we canceled an MMI operation, or false
     *         if the current pending MMI wasn't cancelable
     *         or if there was no current pending MMI at all.
     *
     * @see displayMMIInitiate
     */
/* Google static boolean cancelMmiCode(Phone phone) {
        List<? extends MmiCode> pendingMmis = phone.getPendingMmiCodes(); */
//MTK begin:
    static boolean cancelMmiCodeExt(Phone phone, int simId) {
        log("cancelMmiCode....");
        List<? extends MmiCode> pendingMmis=null;
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            pendingMmis = ((GeminiPhone)phone).getPendingMmiCodesGemini(simId);
        } else {
            pendingMmis = phone.getPendingMmiCodes();
        }
//MTk end
        int count = pendingMmis.size();
        if (DBG) log("cancelMmiCode: num pending MMIs = " + count);

        boolean canceled = false;
        if (count > 0) {
            // assume that we only have one pending MMI operation active at a time.
            // I don't think it's possible to enter multiple MMI codes concurrently
            // in the phone UI, because during the MMI operation, an Alert panel
            // is displayed, which prevents more MMI code from being entered.
            MmiCode mmiCode = pendingMmis.get(0);
            if (mmiCode.isCancelable()) {
                mmiCode.cancel();
                canceled = true;
            }
        }

        //clear timeout message and pre-set MMI command
        if (mNwService != null) {
            try {
                mNwService.clearMmiString();
            } catch (RemoteException e) {
                mNwService = null;
            }
        }
        if (mMmiTimeoutCbMsg != null) {
            mMmiTimeoutCbMsg = null;
        }
        return canceled;
    }

    public static class VoiceMailNumberMissingException extends Exception {
        VoiceMailNumberMissingException() {
            super();
        }

        VoiceMailNumberMissingException(String msg) {
            super(msg);
        }
    }

    /**
     * Gets the phone number to be called from an intent.  Requires a Context
     * to access the contacts database, and a Phone to access the voicemail
     * number.
     *
     * <p>If <code>phone</code> is <code>null</code>, the function will return
     * <code>null</code> for <code>voicemail:</code> URIs;
     * if <code>context</code> is <code>null</code>, the function will return
     * <code>null</code> for person/phone URIs.</p>
     *
     * <p>If the intent contains a <code>sip:</code> URI, the returned
     * "number" is actually the SIP address.
     *
     * @param context a context to use (or
     * @param intent the intent
     *
     * @throws VoiceMailNumberMissingException if <code>intent</code> contains
     *         a <code>voicemail:</code> URI, but <code>phone</code> does not
     *         have a voicemail number set.
     *
     * @return the phone number (or SIP address) that would be called by the intent,
     *         or <code>null</code> if the number cannot be found.
     */
    static String getNumberFromIntent(Context context, Intent intent)
            throws VoiceMailNumberMissingException {
        Uri uri = intent.getData();
        String scheme = uri.getScheme();

        // The sip: scheme is simple: just treat the rest of the URI as a
        // SIP address.
        if (scheme.equals("sip")) {
            return uri.getSchemeSpecificPart();
        }

        // Otherwise, let PhoneNumberUtils.getNumberFromIntent() handle
        // the other cases (i.e. tel: and voicemail: and contact: URIs.)

        final String number = PhoneNumberUtils.getNumberFromIntent(intent, context);

        // Check for a voicemail-dialing request.  If the voicemail number is
        // empty, throw a VoiceMailNumberMissingException.
        if (scheme.equals("voicemail") &&
                (number == null || TextUtils.isEmpty(number)))
            throw new VoiceMailNumberMissingException();

        return number;
    }

    /**
     * Returns the caller-id info corresponding to the specified Connection.
     * (This is just a simple wrapper around CallerInfo.getCallerInfo(): we
     * extract a phone number from the specified Connection, and feed that
     * number into CallerInfo.getCallerInfo().)
     *
     * The returned CallerInfo may be null in certain error cases, like if the
     * specified Connection was null, or if we weren't able to get a valid
     * phone number from the Connection.
     *
     * Finally, if the getCallerInfo() call did succeed, we save the resulting
     * CallerInfo object in the "userData" field of the Connection.
     *
     * NOTE: This API should be avoided, with preference given to the
     * asynchronous startGetCallerInfo API.
     */
    static CallerInfo getCallerInfo(Context context, Connection c) {
        CallerInfo info = null;

        if (c != null) {
            //See if there is a URI attached.  If there is, this means
            //that there is no CallerInfo queried yet, so we'll need to
            //replace the URI with a full CallerInfo object.
            Object userDataObject = c.getUserData();
            if (userDataObject instanceof Uri) {
                info = CallerInfo.getCallerInfo(context, (Uri) userDataObject);
                if (info != null) {
                    c.setUserData(info);
                }
            } else {
                if (userDataObject instanceof CallerInfoToken) {
                    //temporary result, while query is running
                    info = ((CallerInfoToken) userDataObject).currentInfo;
                } else {
                    //final query result
                    info = (CallerInfo) userDataObject;
                }
                if (info == null) {
                    // No URI, or Existing CallerInfo, so we'll have to make do with
                    // querying a new CallerInfo using the connection's phone number.
                    String number = c.getAddress();

                    if (DBG) log("getCallerInfo: number = " + number);

                    if (!TextUtils.isEmpty(number)) {
//Google code:          info = CallerInfo.getCallerInfo(context, number);
//MTK begin:
                        if (FeatureOption.MTK_GEMINI_SUPPORT) {            
                            PhoneApp app = PhoneApp.getInstance();
                            int simId = Phone.GEMINI_SIM_1;
                            if (((GeminiPhone)app.phone).getStateGemini(Phone.GEMINI_SIM_2) != Phone.State.IDLE) {
                                simId = Phone.GEMINI_SIM_2;
                            }
                            info = CallerInfo.getCallerInfoGemini(context, number, simId);
                        } else {
                            info = CallerInfo.getCallerInfo(context, number);
                        }
//MTk end
                        if (info != null) {
                            c.setUserData(info);
                        }
                    }
                }
            }
        }
        return info;
    }

    /**
     * Class returned by the startGetCallerInfo call to package a temporary
     * CallerInfo Object, to be superceded by the CallerInfo Object passed
     * into the listener when the query with token mAsyncQueryToken is complete.
     */
    public static class CallerInfoToken {
        /**indicates that there will no longer be updates to this request.*/
        public boolean isFinal;

        public CallerInfo currentInfo;
        public CallerInfoAsyncQuery asyncQuery;
    }

    /**
     * Start a CallerInfo Query based on the earliest connection in the call.
     */
    static CallerInfoToken startGetCallerInfo(Context context, Call call,
            CallerInfoAsyncQuery.OnQueryCompleteListener listener, Object cookie) {
        PhoneApp app = PhoneApp.getInstance();
        Connection conn = null;
        int phoneType = call.getPhone().getPhoneType();
        if (phoneType == Phone.PHONE_TYPE_CDMA) {
            conn = call.getLatestConnection();
        } else if ((phoneType == Phone.PHONE_TYPE_GSM)
                || (phoneType == Phone.PHONE_TYPE_SIP)) {
            conn = call.getEarliestConnection();
        } else {
            throw new IllegalStateException("Unexpected phone type: " + phoneType);
        }

        return startGetCallerInfo(context, conn, listener, cookie);
    }

    /**
     * place a temporary callerinfo object in the hands of the caller and notify
     * caller when the actual query is done.
     */
    static CallerInfoToken startGetCallerInfo(Context context, Connection c,
            CallerInfoAsyncQuery.OnQueryCompleteListener listener, Object cookie) {
        CallerInfoToken cit;

        boolean isSipConn = false;
        if (c == null) {
            //TODO: perhaps throw an exception here.
            cit = new CallerInfoToken();
            cit.asyncQuery = null;
            return cit;
        }
        
        isSipConn = c.getCall().getPhone().getPhoneType() == Phone.PHONE_TYPE_SIP;

        // There are now 3 states for the userdata.
        //   1. Uri - query has not been executed yet
        //   2. CallerInfoToken - query is executing, but has not completed.
        //   3. CallerInfo - query has executed.
        // In each case we have slightly different behaviour:
        //   1. If the query has not been executed yet (Uri or null), we start
        //      query execution asynchronously, and note it by attaching a
        //      CallerInfoToken as the userData.
        //   2. If the query is executing (CallerInfoToken), we've essentially
        //      reached a state where we've received multiple requests for the
        //      same callerInfo.  That means that once the query is complete,
        //      we'll need to execute the additional listener requested.
        //   3. If the query has already been executed (CallerInfo), we just
        //      return the CallerInfo object as expected.
        //   4. Regarding isFinal - there are cases where the CallerInfo object
        //      will not be attached, like when the number is empty (caller id
        //      blocking).  This flag is used to indicate that the
        //      CallerInfoToken object is going to be permanent since no
        //      query results will be returned.  In the case where a query
        //      has been completed, this flag is used to indicate to the caller
        //      that the data will not be updated since it is valid.
        //
        //      Note: For the case where a number is NOT retrievable, we leave
        //      the CallerInfo as null in the CallerInfoToken.  This is
        //      something of a departure from the original code, since the old
        //      code manufactured a CallerInfo object regardless of the query
        //      outcome.  From now on, we will append an empty CallerInfo
        //      object, to mirror previous behaviour, and to avoid Null Pointer
        //      Exceptions.
//MTK begin:
        int simId = Phone.GEMINI_SIM_1;
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            PhoneApp app = PhoneApp.getInstance();
            if (((GeminiPhone)app.phone).getStateGemini(Phone.GEMINI_SIM_2) != Phone.State.IDLE) {
                simId = Phone.GEMINI_SIM_2;
            }
        }
//MTK end			
        Object userDataObject = c.getUserData();
        if (userDataObject instanceof Uri) {
            //create a dummy callerinfo, populate with what we know from URI.
            cit = new CallerInfoToken();
            cit.currentInfo = new CallerInfo();
            cit.asyncQuery = CallerInfoAsyncQuery.startQuery(QUERY_TOKEN, context,
                    (Uri) userDataObject, sCallerInfoQueryListener, c);
            cit.asyncQuery.addQueryListener(QUERY_TOKEN, listener, cookie);
            cit.isFinal = false;

            c.setUserData(cit);

            if (DBG) log("startGetCallerInfo: query based on Uri: " + userDataObject);

        } else if (userDataObject == null) {
            // No URI, or Existing CallerInfo, so we'll have to make do with
            // querying a new CallerInfo using the connection's phone number.
            String number = c.getAddress();

            if (DBG) {
                log("###### PhoneUtils.startGetCallerInfo: new query for phone number #####");
                log("- number (address): " + number);
                log("- c: " + c);
                log("- phone: " + c.getCall().getPhone());
                int phoneType = c.getCall().getPhone().getPhoneType();
                log("- phoneType: " + phoneType);
                switch (phoneType) {
                    case Phone.PHONE_TYPE_NONE: log("  ==> PHONE_TYPE_NONE"); break;
                    case Phone.PHONE_TYPE_GSM: log("  ==> PHONE_TYPE_GSM"); break;
                    case Phone.PHONE_TYPE_CDMA: log("  ==> PHONE_TYPE_CDMA"); break;
                    case Phone.PHONE_TYPE_SIP: log("  ==> PHONE_TYPE_SIP"); break;
                    default: log("  ==> Unknown phone type"); break;
                }
            }

            cit = new CallerInfoToken();
            cit.currentInfo = new CallerInfo();

            // Store CNAP information retrieved from the Connection (we want to do this
            // here regardless of whether the number is empty or not).
            cit.currentInfo.cnapName =  c.getCnapName();
            cit.currentInfo.name = cit.currentInfo.cnapName; // This can still get overwritten
                                                             // by ContactInfo later
            cit.currentInfo.numberPresentation = c.getNumberPresentation();
            cit.currentInfo.namePresentation = c.getCnapNamePresentation();

            if (DBG) {
                log("startGetCallerInfo: number = " + number);
                log("startGetCallerInfo: CNAP Info from FW(1): name="
                    + cit.currentInfo.cnapName
                    + ", Name/Number Pres=" + cit.currentInfo.numberPresentation);
            }

            // handling case where number is null (caller id hidden) as well.
            if (!TextUtils.isEmpty(number)) {
                // Check for special CNAP cases and modify the CallerInfo accordingly
                // to be sure we keep the right information to display/log later
                number = modifyForSpecialCnapCases(context, cit.currentInfo, number,
                        cit.currentInfo.numberPresentation);

                cit.currentInfo.phoneNumber = number;
                // For scenarios where we may receive a valid number from the network but a
                // restricted/unavailable presentation, we do not want to perform a contact query
                // (see note on isFinal above). So we set isFinal to true here as well.
                if (cit.currentInfo.numberPresentation != Connection.PRESENTATION_ALLOWED) {
                    cit.isFinal = true;
                } else {
                    if (DBG) log("==> Actually starting CallerInfoAsyncQuery.startQuery()...");
/*Google code:      cit.asyncQuery = CallerInfoAsyncQuery.startQuery(QUERY_TOKEN, context,
                            number, sCallerInfoQueryListener, c); */
//MTK begin:
                if (FeatureOption.MTK_GEMINI_SUPPORT && !isSipConn) {
                    cit.asyncQuery = CallerInfoAsyncQuery.startQueryGemini(QUERY_TOKEN, context,
                            number, sCallerInfoQueryListener, c, simId);
                } else {
                    cit.asyncQuery = CallerInfoAsyncQuery.startQuery(QUERY_TOKEN, context,
                            number, sCallerInfoQueryListener, c);
                }
//MTK end
                    cit.asyncQuery.addQueryListener(QUERY_TOKEN, listener, cookie);
                    cit.isFinal = false;
                }
            } else {
                // This is the case where we are querying on a number that
                // is null or empty, like a caller whose caller id is
                // blocked or empty (CLIR).  The previous behaviour was to
                // throw a null CallerInfo object back to the user, but
                // this departure is somewhat cleaner.
                if (DBG) log("startGetCallerInfo: No query to start, send trivial reply.");
                cit.isFinal = true; // please see note on isFinal, above.
            }

            c.setUserData(cit);

            if (DBG) log("startGetCallerInfo: query based on number: " + number);

        } else if (userDataObject instanceof CallerInfoToken) {
            // query is running, just tack on this listener to the queue.
            cit = (CallerInfoToken) userDataObject;

            // handling case where number is null (caller id hidden) as well.
            if (cit.asyncQuery != null) {
                cit.asyncQuery.addQueryListener(QUERY_TOKEN, listener, cookie);

                if (DBG) log("startGetCallerInfo: query already running, adding listener: " +
                        listener.getClass().toString());
            } else {
                // handling case where number/name gets updated later on by the network
                String updatedNumber = c.getAddress();
                if (DBG) log("startGetCallerInfo: updatedNumber initially = " + updatedNumber);
                if (!TextUtils.isEmpty(updatedNumber)) {
                    // Store CNAP information retrieved from the Connection
                    cit.currentInfo.cnapName =  c.getCnapName();
                    // This can still get overwritten by ContactInfo
                    cit.currentInfo.name = cit.currentInfo.cnapName;
                    cit.currentInfo.numberPresentation = c.getNumberPresentation();
                    cit.currentInfo.namePresentation = c.getCnapNamePresentation();

                    updatedNumber = modifyForSpecialCnapCases(context, cit.currentInfo,
                            updatedNumber, cit.currentInfo.numberPresentation);

                    cit.currentInfo.phoneNumber = updatedNumber;
                    if (DBG) log("startGetCallerInfo: updatedNumber=" + updatedNumber);
                    if (DBG) log("startGetCallerInfo: CNAP Info from FW(2): name="
                            + cit.currentInfo.cnapName
                            + ", Name/Number Pres=" + cit.currentInfo.numberPresentation);
                    // For scenarios where we may receive a valid number from the network but a
                    // restricted/unavailable presentation, we do not want to perform a contact query
                    // (see note on isFinal above). So we set isFinal to true here as well.
                    if (cit.currentInfo.numberPresentation != Connection.PRESENTATION_ALLOWED) {
                        cit.isFinal = true;
                    } else {
/*Google code:          cit.asyncQuery = CallerInfoAsyncQuery.startQuery(QUERY_TOKEN, context,
                                updatedNumber, sCallerInfoQueryListener, c); */
//MTK begin:
                        if (FeatureOption.MTK_GEMINI_SUPPORT && !isSipConn) {
                            cit.asyncQuery = CallerInfoAsyncQuery.startQueryGemini(QUERY_TOKEN, context,
                                    updatedNumber, sCallerInfoQueryListener, c, simId);

                        } else {
                            cit.asyncQuery = CallerInfoAsyncQuery.startQuery(QUERY_TOKEN, context,
                                    updatedNumber, sCallerInfoQueryListener, c);
                        }
//MTK end
                        cit.asyncQuery.addQueryListener(QUERY_TOKEN, listener, cookie);
                        cit.isFinal = false;
                    }
                } else {
                    if (DBG) log("startGetCallerInfo: No query to attach to, send trivial reply.");
                    if (cit.currentInfo == null) {
                        cit.currentInfo = new CallerInfo();
                    }
                    // Store CNAP information retrieved from the Connection
                    cit.currentInfo.cnapName = c.getCnapName();  // This can still get
                                                                 // overwritten by ContactInfo
                    cit.currentInfo.name = cit.currentInfo.cnapName;
                    cit.currentInfo.numberPresentation = c.getNumberPresentation();
                    cit.currentInfo.namePresentation = c.getCnapNamePresentation();

                    if (DBG) log("startGetCallerInfo: CNAP Info from FW(3): name="
                            + cit.currentInfo.cnapName
                            + ", Name/Number Pres=" + cit.currentInfo.numberPresentation);
                    cit.isFinal = true; // please see note on isFinal, above.
                }
            }
        } else {
            cit = new CallerInfoToken();
            cit.currentInfo = (CallerInfo) userDataObject;
            cit.asyncQuery = null;
            cit.isFinal = true;
            // since the query is already done, call the listener.
            if (DBG) log("startGetCallerInfo: query already done, returning CallerInfo");
        }
        return cit;
    }

    /**
     * Implemented for CallerInfo.OnCallerInfoQueryCompleteListener interface.
     * Updates the connection's userData when called.
     */
    private static final int QUERY_TOKEN = -1;
    static CallerInfoAsyncQuery.OnQueryCompleteListener sCallerInfoQueryListener =
        new CallerInfoAsyncQuery.OnQueryCompleteListener () {
            public void onQueryComplete(int token, Object cookie, CallerInfo ci) {
                if (DBG) log("query complete, updating connection.userdata");
                Connection conn = (Connection) cookie;
                boolean isSipConn = conn.getCall().getPhone().getPhoneType() == Phone.PHONE_TYPE_SIP;
                // Added a check if CallerInfo is coming from ContactInfo or from Connection.
                // If no ContactInfo, then we want to use CNAP information coming from network
                if (DBG) log("- onQueryComplete: CallerInfo:" + ci);
                if (ci.contactExists || ci.isEmergencyNumber() || ci.isVoiceMailNumber()) {
                    // If the number presentation has not been set by
                    // the ContactInfo, use the one from the
                    // connection.

                    // TODO: Need a new util method to merge the info
                    // from the Connection in a CallerInfo object.
                    // Here 'ci' is a new CallerInfo instance read
                    // from the DB. It has lost all the connection
                    // info preset before the query (see PhoneUtils
                    // line 1334). We should have a method to merge
                    // back into this new instance the info from the
                    // connection object not set by the DB. If the
                    // Connection already has a CallerInfo instance in
                    // userData, then we could use this instance to
                    // fill 'ci' in. The same routine could be used in
                    // PhoneUtils.
                    if (0 == ci.numberPresentation) {
                        ci.numberPresentation = conn.getNumberPresentation();
                    }
                } else {
                    CallerInfo newCi = getCallerInfo(null, conn);
                    if (newCi != null) {
                        newCi.phoneNumber = ci.phoneNumber; // To get formatted phone number
                        ci = newCi;
                    }
                }
                
                //We don't show the voice mail when the voice mail is set by cell network
                if (isSipConn && !ci.contactExists && !ci.isEmergencyNumber() && ci.isVoiceMailNumber()) {
                    ci.phoneNumber = conn.getAddress();
                }
                conn.setUserData(ci);
            }
        };


    /**
     * Returns a single "name" for the specified given a CallerInfo object.
     * If the name is null, return defaultString as the default value, usually
     * context.getString(R.string.unknown).
     */
    static String getCompactNameFromCallerInfo(CallerInfo ci, Context context) {
        if (DBG) log("getCompactNameFromCallerInfo: info = " + ci);

        String compactName = null;
        if (ci != null) {
            if (TextUtils.isEmpty(ci.name)) {
                // Perform any modifications for special CNAP cases to
                // the phone number being displayed, if applicable.
                compactName = modifyForSpecialCnapCases(context, ci, ci.phoneNumber,
                                                        ci.numberPresentation);
            } else {
                // Don't call modifyForSpecialCnapCases on regular name. See b/2160795.
                compactName = ci.name;
            }
        }

        if ((compactName == null) || (TextUtils.isEmpty(compactName))) {
            // If we're still null/empty here, then check if we have a presentation
            // string that takes precedence that we could return, otherwise display
            // "unknown" string.
            if (ci != null && ci.numberPresentation == Connection.PRESENTATION_RESTRICTED) {
                compactName = context.getString(R.string.private_num);
            } else if (ci != null && ci.numberPresentation == Connection.PRESENTATION_PAYPHONE) {
                compactName = context.getString(R.string.payphone);
            } else {
                compactName = context.getString(R.string.unknown);
            }
        }
        if (DBG) log("getCompactNameFromCallerInfo: compactName=" + compactName);
        return compactName;
    }

    /**
     * Returns true if the specified Call is a "conference call", meaning
     * that it owns more than one Connection object.  This information is
     * used to trigger certain UI changes that appear when a conference
     * call is active (like displaying the label "Conference call", and
     * enabling the "Manage conference" UI.)
     *
     * Watch out: This method simply checks the number of Connections,
     * *not* their states.  So if a Call has (for example) one ACTIVE
     * connection and one DISCONNECTED connection, this method will return
     * true (which is unintuitive, since the Call isn't *really* a
     * conference call any more.)
     *
     * @return true if the specified call has more than one connection (in any state.)
     */
    static boolean isConferenceCall(Call call) {
        // CDMA phones don't have the same concept of "conference call" as
        // GSM phones do; there's no special "conference call" state of
        // the UI or a "manage conference" function.  (Instead, when
        // you're in a 3-way call, all we can do is display the "generic"
        // state of the UI.)  So as far as the in-call UI is concerned,
        // Conference corresponds to generic display.
        PhoneApp app = PhoneApp.getInstance();
        int phoneType = call.getPhone().getPhoneType();
        if (phoneType == Phone.PHONE_TYPE_CDMA) {
            CdmaPhoneCallState.PhoneCallState state = app.cdmaPhoneCallState.getCurrentCallState();
            if ((state == CdmaPhoneCallState.PhoneCallState.CONF_CALL)
                    || ((state == CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE)
                    && !app.cdmaPhoneCallState.IsThreeWayCallOrigStateDialing())) {
                return true;
            }
/*MTK add but remove when merge*/
        } else if (phoneType == Phone.PHONE_TYPE_GSM) {
            return call.isMptyCall;
        }else if (phoneType == Phone.PHONE_TYPE_SIP) {
        	List<Connection> connections = call.getConnections();
          if (connections != null && connections.size() > 1) {
              return true;
          }
        }
//        } else {
//            List<Connection> connections = call.getConnections();
//            if (connections != null && connections.size() > 1) {
//                return true;
//            }
//        }
        return false;

        // TODO: We may still want to change the semantics of this method
        // to say that a given call is only really a conference call if
        // the number of ACTIVE connections, not the total number of
        // connections, is greater than one.  (See warning comment in the
        // javadoc above.)
        // Here's an implementation of that:
        //        if (connections == null) {
        //            return false;
        //        }
        //        int numActiveConnections = 0;
        //        for (Connection conn : connections) {
        //            if (DBG) log("  - CONN: " + conn + ", state = " + conn.getState());
        //            if (conn.getState() == Call.State.ACTIVE) numActiveConnections++;
        //            if (numActiveConnections > 1) {
        //                return true;
        //            }
        //        }
        //        return false;
    }

    /**
     * Launch the Dialer to start a new call.
     * This is just a wrapper around the ACTION_DIAL intent.
     */
    static void startNewCall(final CallManager cm) {
        // Sanity-check that this is OK given the current state of the phone.
        if (!okToAddCall(cm)) {
            Log.w(LOG_TAG, "startNewCall: can't add a new call in the current state");
            dumpCallManager();
            return;
        }

        // if applicable, mute the call while we're showing the add call UI.
        if (cm.hasActiveFgCall()) {
            setMuteInternal(cm.getActiveFgCall().getPhone(), true);
            // Inform the phone app that this mute state was NOT done
            // voluntarily by the User.
            PhoneApp.getInstance().setRestoreMuteOnInCallResume(true);
        }

        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // when we request the dialer come up, we also want to inform
        // it that we're going through the "add call" option from the
        // InCallScreen / PhoneUtils.
        intent.putExtra(ADD_CALL_MODE_KEY, true);

        PhoneApp.getInstance().startActivity(intent);
    }
//MTK add begin:
    /**
     * Brings up the UI used to handle an incoming call.
     *
     * Originally, this brought up an IncomingCallPanel instance
     * (which was a subclass of Dialog) on top of whatever app
     * was currently running.  Now, we take you directly to the
     * in-call screen, whose CallCard automatically does the right
     * thing if there's a Call that's currently ringing.
     */
    static void showIncomingCallUi() {
        if (DBG) log("showIncomingCallUi()...");
        PhoneApp app = PhoneApp.getInstance();

        // Before bringing up the "incoming call" UI, force any system
        // dialogs (like "recent tasks" or the power dialog) to close first.
        try {
            ActivityManagerNative.getDefault().closeSystemDialogs("call");
        } catch (RemoteException e) {
        }

        // Go directly to the in-call screen.
        // (No need to do anything special if we're already on the in-call
        // screen; it'll notice the phone state change and update itself.)

        // But first, grab a full wake lock.  We do this here, before we
        // even fire off the InCallScreen intent, to make sure the
        // ActivityManager doesn't try to pause the InCallScreen as soon
        // as it comes up.  (See bug 1648751.)
        //
        // And since the InCallScreen isn't visible yet (we haven't even
        // fired off the intent yet), we DON'T want the screen to actually
        // come on right now.  So *before* acquiring the wake lock we need
        // to call preventScreenOn(), which tells the PowerManager that
        // the screen should stay off even if someone's holding a full
        // wake lock.  (This prevents any flicker during the "incoming
        // call" sequence.  The corresponding preventScreenOn(false) call
        // will come from the InCallScreen when it's finally ready to be
        // displayed.)
        //
        // TODO: this is all a temporary workaround.  The real fix is to add
        // an Activity attribute saying "this Activity wants to wake up the
        // phone when it's displayed"; that way the ActivityManager could
        // manage the wake locks *and* arrange for the screen to come on at
        // the exact moment that the InCallScreen is ready to be displayed.
        // (See bug 1648751.)
        app.preventScreenOn(true);
        app.requestWakeState(PhoneApp.WakeState.FULL);

        // Fire off the InCallScreen intent.
        app.displayCallScreen();
    }
//MTK add end
    static void turnOnSpeaker(Context context, boolean flag, boolean store) {
        if (DBG) log("turnOnSpeaker(flag=" + flag + ", store=" + store + ")...");
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        audioManager.setSpeakerphoneOn(flag);
        // record the speaker-enable value
        if (store) {
            sIsSpeakerEnabled = flag;
        }
        if (flag) {
            NotificationMgr.getDefault().notifySpeakerphone();
        } else {
            NotificationMgr.getDefault().cancelSpeakerphone();
        }

        // We also need to make a fresh call to PhoneApp.updateWakeState()
        // any time the speaker state changes, since the screen timeout is
        // sometimes different depending on whether or not the speaker is
        // in use.
        PhoneApp app = PhoneApp.getInstance();
        app.updateWakeState();

        // Update the Proximity sensor based on speaker state
        app.updateProximitySensorMode(app.mCM.getState());

        app.mCM.setEchoSuppressionEnabled(flag);
    }

    /**
     * Restore the speaker mode, called after a wired headset disconnect
     * event.
     */
    static void restoreSpeakerMode(Context context) {
        if (DBG) log("restoreSpeakerMode, restoring to: " + sIsSpeakerEnabled);

        // change the mode if needed.
        if (isSpeakerOn(context) != sIsSpeakerEnabled) {
            turnOnSpeaker(context, sIsSpeakerEnabled, false);
        }
    }

    static boolean isSpeakerOn(Context context) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        return audioManager.isSpeakerphoneOn();
    }


    static void turnOnNoiseSuppression(Context context, boolean flag, boolean store) {
        if (DBG) log("turnOnNoiseSuppression: " + flag);
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        if (!context.getResources().getBoolean(R.bool.has_in_call_noise_suppression)) {
            return;
        }

        if (flag) {
            audioManager.setParameters("noise_suppression=auto");
        } else {
            audioManager.setParameters("noise_suppression=off");
        }

        // record the speaker-enable value
        if (store) {
            sIsNoiseSuppressionEnabled = flag;
        }

        // TODO: implement and manage ICON

    }

    static void restoreNoiseSuppression(Context context) {
        if (DBG) log("restoreNoiseSuppression, restoring to: " + sIsNoiseSuppressionEnabled);

        if (!context.getResources().getBoolean(R.bool.has_in_call_noise_suppression)) {
            return;
        }

        // change the mode if needed.
        if (isNoiseSuppressionOn(context) != sIsNoiseSuppressionEnabled) {
            turnOnNoiseSuppression(context, sIsNoiseSuppressionEnabled, false);
        }
    }

    static boolean isNoiseSuppressionOn(Context context) {

        if (!context.getResources().getBoolean(R.bool.has_in_call_noise_suppression)) {
            return false;
        }

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        String noiseSuppression = audioManager.getParameters("noise_suppression");
        if (DBG) log("isNoiseSuppressionOn: " + noiseSuppression);
        if (noiseSuppression.contains("off")) {
            return false;
        } else {
            return true;
        }
    }

    /**
     *
     * Mute / umute the foreground phone, which has the current foreground call
     *
     * All muting / unmuting from the in-call UI should go through this
     * wrapper.
     *
     * Wrapper around Phone.setMute() and setMicrophoneMute().
     * It also updates the connectionMuteTable and mute icon in the status bar.
     *
     */
    static void setMute(boolean muted) {
        CallManager cm = PhoneApp.getInstance().mCM;

        // make the call to mute the audio
        setMuteInternal(cm.getFgPhone(), muted);


        // update the foreground connections to match.  This includes
        // all the connections on conference calls.
        for (Connection cn : cm.getActiveFgCall().getConnections()) {
            if (sConnectionMuteTable.get(cn) == null) {
                if (DBG) log("problem retrieving mute value for this connection.");
            }
            sConnectionMuteTable.put(cn, Boolean.valueOf(muted));
        }
    }

    /**
     * Internally used muting function.
     */
    private static void setMuteInternal(Phone phone, boolean muted) {
        if (DBG) log("setMuteInternal: " + muted);
        Context context = phone.getContext();
        boolean routeToAudioManager =
            context.getResources().getBoolean(R.bool.send_mic_mute_to_AudioManager);
        if (routeToAudioManager) {
            AudioManager audioManager =
                (AudioManager) phone.getContext().getSystemService(Context.AUDIO_SERVICE);
            if (DBG) log("setMicrophoneMute: " + muted);
            audioManager.setMicrophoneMute(muted);
        } else {
            phone.setMute(muted);
        }
        NotificationMgr.getDefault().updateMuteNotification();
    }

    /**
     * Get the mute state of foreground phone, which has the current
     * foreground call
     */
    static boolean getMute() {
        PhoneApp app = PhoneApp.getInstance();

        boolean routeToAudioManager =
            app.getResources().getBoolean(R.bool.send_mic_mute_to_AudioManager);
        if (routeToAudioManager) {
            AudioManager audioManager =
                (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
            return audioManager.isMicrophoneMute();
        } else {
            return app.mCM.getMute();
        }
    }

    /* package */ static void setAudioMode() {
        setAudioMode(PhoneApp.getInstance().mCM);
    }

    /**
     * Sets the audio mode per current phone state.
     */
    /* package */ static void setAudioMode(CallManager cm) {
        if (DBG) Log.d(LOG_TAG, "setAudioMode()..." + cm.getState());

        Context context = PhoneApp.getInstance();
        AudioManager audioManager = (AudioManager)
                context.getSystemService(Context.AUDIO_SERVICE);
        int modeBefore = audioManager.getMode();
        boolean isSipPhone = cm.getFgPhone().getPhoneType() == Phone.PHONE_TYPE_SIP;
        int mode = getExpectedAudioMode();
        if (PhoneUtils.isSupportFeature("TTY") && !isSipPhone)
        {
            if ((mode == AudioManager.MODE_NORMAL) && (!ttyMode.equals("tty_off") && isOpen))
            {
                audioManager.setParameters("tty_mode="+ "tty_off");
                isOpen = false;
            }
        }
        cm.setAudioMode();
        if (PhoneUtils.isSupportFeature("TTY") == true && !isSipPhone)
        {
            if ((mode == AudioManager.MODE_IN_CALL) && (!ttyMode.equals("tty_off") && !isOpen))
            {
                audioManager.setParameters("tty_mode="+ ttyMode);
                isOpen = true;
            }
        }
        int modeAfter = audioManager.getMode();

        if (modeBefore != modeAfter) {
            // Enable stack dump only when actively debugging ("new Throwable()" is expensive!)
            if (DBG_SETAUDIOMODE_STACK) Log.d(LOG_TAG, "Stack:", new Throwable("stack dump"));
        } else {
            if (DBG) Log.d(LOG_TAG, "setAudioMode() no change: "
                    + audioModeToString(modeBefore));
        }
    }
    
    static int getExpectedAudioMode()
    {
    	int mode = AudioManager.MODE_NORMAL;
    	CallManager cm = PhoneApp.getInstance().mCM;
    	switch (cm.getState()) {
	        case RINGING:
	            mode = AudioManager.MODE_RINGTONE;
	            break;
	        case OFFHOOK:
	            Phone fgPhone = cm.getFgPhone();
	            // Enable IN_CALL mode while foreground call is in DIALING,
	            // ALERTING, ACTIVE and DISCONNECTING state and not from sipPhone
	            if (cm.getActiveFgCallState() != Call.State.IDLE
	                    && cm.getActiveFgCallState() != Call.State.DISCONNECTED
	                    && !(fgPhone instanceof SipPhone)) {
	                mode = AudioManager.MODE_IN_CALL;
	            }
	            break;
        }
    	
    	return mode;
    }
    
    private static String audioModeToString(int mode) {
        switch (mode) {
            case AudioManager.MODE_INVALID: return "MODE_INVALID";
            case AudioManager.MODE_CURRENT: return "MODE_CURRENT";
            case AudioManager.MODE_NORMAL: return "MODE_NORMAL";
            case AudioManager.MODE_RINGTONE: return "MODE_RINGTONE";
            case AudioManager.MODE_IN_CALL: return "MODE_IN_CALL";
            default: return String.valueOf(mode);
        }
    }

    /**
     * Handles the wired headset button while in-call.
     *
     * This is called from the PhoneApp, not from the InCallScreen,
     * since the HEADSETHOOK button means "mute or unmute the current
     * call" *any* time a call is active, even if the user isn't actually
     * on the in-call screen.
     *
     * @return true if we consumed the event.
     */
    /* package */ static boolean handleHeadsetHook(Phone phone, KeyEvent event) {
        if (DBG) log("handleHeadsetHook()..." + event.getAction() + " " + event.getRepeatCount());

        // If the phone is totally idle, we ignore HEADSETHOOK events
        // (and instead let them fall through to the media player.)
/*Google: if (phone.getState() == Phone.State.IDLE) {
            return false;
        } */
//MTK begin:
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            if (((GeminiPhone)phone).getState() == Phone.State.IDLE) {
                return false;
            }
        } else {
            if (phone.getState() == Phone.State.IDLE) {
                return false;
            }
        }
//MTK end

        // Ok, the phone is in use.
        // The headset button button means "Answer" if an incoming call is
        // ringing.  If not, it toggles the mute / unmute state.
        //
        // And in any case we *always* consume this event; this means
        // that the usual mediaplayer-related behavior of the headset
        // button will NEVER happen while the user is on a call.

/*Google: final boolean hasRingingCall = !phone.getRingingCall().isIdle();
        final boolean hasActiveCall = !phone.getForegroundCall().isIdle();
        final boolean hasHoldingCall = !phone.getBackgroundCall().isIdle(); */
//MTK begin:
        final boolean hasRingingCall , hasActiveCall , hasHoldingCall;

        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            hasRingingCall = !((GeminiPhone)phone).getRingingCall().isIdle();
            hasActiveCall = !((GeminiPhone)phone).getForegroundCall().isIdle();
            hasHoldingCall = !((GeminiPhone)phone).getBackgroundCall().isIdle();
        } else {
            hasRingingCall = !phone.getRingingCall().isIdle();
            hasActiveCall = !phone.getForegroundCall().isIdle();
            hasHoldingCall = !phone.getBackgroundCall().isIdle();
        }
//MTK end

        if (hasRingingCall &&
            event.getRepeatCount() == 0 &&
            event.getAction() == KeyEvent.ACTION_UP) {
            // If an incoming call is ringing, answer it (just like with the
            // CALL button):
            int phoneType = phone.getPhoneType();
            if (phoneType == Phone.PHONE_TYPE_CDMA) {
                answerCall(phone.getRingingCall());
            } else if ((phoneType == Phone.PHONE_TYPE_GSM)
                    || (phoneType == Phone.PHONE_TYPE_SIP)) {
                if (hasActiveCall && hasHoldingCall) {
                    if (DBG) log("handleHeadsetHook: ringing (both lines in use) ==> answer!");
                    answerAndEndActive(PhoneApp.getInstance().mCM, phone.getRingingCall());
                } else {
                    if (DBG) log("handleHeadsetHook: ringing ==> answer!");
                    answerCall(phone.getRingingCall());  // Automatically holds the current active call,
                                        // if there is one
                }
            } else {
                throw new IllegalStateException("Unexpected phone type: " + phoneType);
            }
        } else {
            // No incoming ringing call.
            if (event.isLongPress()) {
                if (DBG) log("handleHeadsetHook: longpress -> hangup");
                hangup(PhoneApp.getInstance().mCM);
            }
            else if (event.getAction() == KeyEvent.ACTION_UP &&
                     event.getRepeatCount() == 0) {
//Google:       Connection c = phone.getForegroundCall().getLatestConnection();
//MTK begin:
                Connection c;
                if (FeatureOption.MTK_GEMINI_SUPPORT) {
                    c = ((GeminiPhone)phone).getForegroundCall().getLatestConnection();
                } else {
                    c = phone.getForegroundCall().getLatestConnection();
                }
//MTK end
                // If it is NOT an emg #, toggle the mute state. Otherwise, ignore the hook.
                if (c != null && !PhoneNumberUtils.isEmergencyNumber(c.getAddress())) {
                    if (getMute()) {
                        if (DBG) log("handleHeadsetHook: UNmuting...");
                        setMute(false);
                    } else {
                        if (DBG) log("handleHeadsetHook: muting...");
                        setMute(true);
                    }
                }
            }
        }

        // Even if the InCallScreen is the current activity, there's no
        // need to force it to update, because (1) if we answered a
        // ringing call, the InCallScreen will imminently get a phone
        // state change event (causing an update), and (2) if we muted or
        // unmuted, the setMute() call automagically updates the status
        // bar, and there's no "mute" indication in the InCallScreen
        // itself (other than the menu item, which only ever stays
        // onscreen for a second anyway.)
        // TODO: (2) isn't entirely true anymore. Once we return our result
        // to the PhoneApp, we ask InCallScreen to update its control widgets
        // in case we changed mute or speaker state and phones with touch-
        // screen [toggle] buttons need to update themselves.

        return true;
    }

    /**
     * Look for ANY connections on the phone that qualify as being
     * disconnected.
     *
     * @return true if we find a connection that is disconnected over
     * all the phone's call objects.
     */
    /* package */ static boolean hasDisconnectedConnections(Phone phone) {
/*Google:  return hasDisconnectedConnections(phone.getForegroundCall()) ||
                hasDisconnectedConnections(phone.getBackgroundCall()) ||
                hasDisconnectedConnections(phone.getRingingCall()); */
//MTK begin:
           if (FeatureOption.MTK_GEMINI_SUPPORT) {
                return hasDisconnectedConnections(((GeminiPhone)phone).getForegroundCall()) ||
                        hasDisconnectedConnections(((GeminiPhone)phone).getBackgroundCall()) ||
                        hasDisconnectedConnections(((GeminiPhone)phone).getRingingCall());
           } else {
                return hasDisconnectedConnections(phone.getForegroundCall()) ||
                    hasDisconnectedConnections(phone.getBackgroundCall()) ||
                    hasDisconnectedConnections(phone.getRingingCall());
           }
//MTK end
    }

    /**
     * Iterate over all connections in a call to see if there are any
     * that are not alive (disconnected or idle).
     *
     * @return true if we find a connection that is disconnected, and
     * pending removal via
     * {@link com.android.internal.telephony.gsm.GsmCall#clearDisconnected()}.
     */
    private static final boolean hasDisconnectedConnections(Call call) {
        // look through all connections for non-active ones.
        for (Connection c : call.getConnections()) {
            if (!c.isAlive()) {
                return true;
            }
        }
        return false;
    }

    public static boolean holdAndActiveFromDifPhone(CallManager cm)
    {
        boolean isDiffrentPhone = false;
        List<Phone> array = cm.getAllPhones();
        boolean found = false;
        for (Phone p : array)
        {
            if (p.getState() == Phone.State.OFFHOOK)
            {
                if (!found)
                {
                    found = true;
                }else {
                    isDiffrentPhone = true;
                    break;
                }
            }
        }
        
        return isDiffrentPhone;
    }
    //
    // Misc UI policy helper functions
    //
    
    //For Google default, the swap button and hold button no dependency,
    //but about our solution, the swap and hold is exclusive:If the hold button display, the swap must hide
    //so we need this method to make sure the swap can be displayed
    static boolean okToShowSwapButton(CallManager cm) {
        Call fgCall = cm.getActiveFgCall();
        Call bgCall = cm.getFirstActiveBgCall();
        
        if ((fgCall.getState().isAlive() && bgCall.getState() == Call.State.HOLDING)
            || holdAndActiveFromDifPhone(cm)) {
            return true;
        }
        
        return false;
    }

    /**
     * @return true if we're allowed to swap calls, given the current
     * state of the Phone.
     */
    /* package */ static boolean okToSwapCalls(CallManager cm) {
        int phoneType = cm.getDefaultPhone().getPhoneType();
        if (phoneType == Phone.PHONE_TYPE_CDMA) {
            // CDMA: "Swap" is enabled only when the phone reaches a *generic*.
            // state by either accepting a Call Waiting or by merging two calls
            PhoneApp app = PhoneApp.getInstance();
            return (app.cdmaPhoneCallState.getCurrentCallState()
                    == CdmaPhoneCallState.PhoneCallState.CONF_CALL);
        } else if ((phoneType == Phone.PHONE_TYPE_GSM)
                || (phoneType == Phone.PHONE_TYPE_SIP)) {
            // GSM: "Swap" is available if both lines are in use and there's no
            // incoming call.  (Actually we need to verify that the active
            // call really is in the ACTIVE state and the holding call really
            // is in the HOLDING state, since you *can't* actually swap calls
            // when the foreground call is DIALING or ALERTING.)
            return !cm.hasActiveRingingCall()
                    && ((cm.getActiveFgCall().getState() == Call.State.ACTIVE || PhoneUtils.hasActivefgEccCall(cm))
                    && (cm.getFirstActiveBgCall().getState() == Call.State.HOLDING)
                    /*|| holdAndActiveFromDifPhone(cm)*/);
        } else {
            throw new IllegalStateException("Unexpected phone type: " + phoneType);
        }
    }
    
    /* added by xingping.zheng start */
    /* package */ static boolean hasActivefgEccCall(CallManager cm) {
        return PhoneUtils.hasActivefgEccCall(cm.getActiveFgCall());
    }

    static boolean isVoicemailNumber(Uri uri) {
        return uri != null && "voicemail".equals(uri.getScheme().toString());
    }

    static boolean hasActivefgEccCall(Call call) {
        if(call == null)
            return false;
        Connection connection = call.getEarliestConnection();
        return (call.getState() == Call.State.DIALING || call.getState() == Call.State.ALERTING) && 
                connection != null &&
                !TextUtils.isEmpty(connection.getAddress()) &&
                PhoneNumberUtils.isEmergencyNumber(connection.getAddress());
    }
    
    static boolean isEccCall(Call call) {
        Connection connection = call.getEarliestConnection();
        return (connection != null &&
                !TextUtils.isEmpty(connection.getAddress()) &&
                PhoneNumberUtils.isEmergencyNumber(connection.getAddress()));
    }
    /* added by xingping.zheng end   */

    /**
     * @return true if we're allowed to merge calls, given the current
     * state of the Phone.
     */
    /* package */ static boolean okToMergeCalls(CallManager cm) {
        int phoneType = cm.getFgPhone().getPhoneType();
        if (phoneType == Phone.PHONE_TYPE_CDMA) {
            // CDMA: "Merge" is enabled only when the user is in a 3Way call.
            PhoneApp app = PhoneApp.getInstance();
            return ((app.cdmaPhoneCallState.getCurrentCallState()
                    == CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE)
                    && !app.cdmaPhoneCallState.IsThreeWayCallOrigStateDialing());
        } else {
            // GSM: "Merge" is available if both lines are in use and there's no
            // incoming call, *and* the current conference isn't already
            // "full".
            // TODO: shall move all okToMerge logic to CallManager
            return !cm.hasActiveRingingCall() && cm.hasActiveFgCall()
                    && cm.getActiveFgCall().getState() != Call.State.DIALING 
                    && cm.getActiveFgCall().getState() != Call.State.ALERTING 
                    && cm.hasActiveBgCall() && cm.canConference(cm.getFirstActiveBgCall());
        }
    }

    /**
     * @return true if the UI should let you add a new call, given the current
     * state of the Phone.
     */
    /* package */ static boolean okToAddCall(CallManager cm) {
        Phone phone = cm.getActiveFgCall().getPhone();

        // "Add call" is never allowed in emergency callback mode (ECM).
        if (isPhoneInEcm(phone)) {
            return false;
        }

        int phoneType = phone.getPhoneType();
        final Call.State fgCallState = cm.getActiveFgCall().getState();
        if (phoneType == Phone.PHONE_TYPE_CDMA) {
           // CDMA: "Add call" menu item is only enabled when the call is in
           // - ForegroundCall is in ACTIVE state
           // - After 30 seconds of user Ignoring/Missing a Call Waiting call.
            PhoneApp app = PhoneApp.getInstance();
            return ((fgCallState == Call.State.ACTIVE)
                    && (app.cdmaPhoneCallState.getAddCallMenuStateAfterCallWaiting()));
        } else if ((phoneType == Phone.PHONE_TYPE_GSM)
                || (phoneType == Phone.PHONE_TYPE_SIP)) {
            // GSM: "Add call" is available only if ALL of the following are true:
            // - There's no incoming ringing call
            // - There's < 2 lines in use
            // - The foreground call is ACTIVE or IDLE or DISCONNECTED.
            //   (We mainly need to make sure it *isn't* DIALING or ALERTING.)
            final boolean hasRingingCall = cm.hasActiveRingingCall();
            final boolean hasActiveCall = cm.hasActiveFgCall();
            final boolean hasHoldingCall = cm.hasActiveBgCall();
            final boolean allLinesTaken = hasActiveCall && hasHoldingCall;

            return !hasRingingCall
                    && !allLinesTaken
                    && ((fgCallState == Call.State.ACTIVE)
                        || (fgCallState == Call.State.IDLE)
                        || (fgCallState == Call.State.DISCONNECTED));
        } else {
            throw new IllegalStateException("Unexpected phone type: " + phoneType);
        }
    }

    /**
     * Based on the input CNAP number string,
     * @return _RESTRICTED or _UNKNOWN for all the special CNAP strings.
     * Otherwise, return CNAP_SPECIAL_CASE_NO.
     */
    private static int checkCnapSpecialCases(String n) {
        if (n.equals("PRIVATE") ||
                n.equals("P") ||
                n.equals("RES")) {
            if (DBG) log("checkCnapSpecialCases, PRIVATE string: " + n);
            return Connection.PRESENTATION_RESTRICTED;
        } else if (n.equals("UNAVAILABLE") ||
                n.equals("UNKNOWN") ||
                n.equals("UNA") ||
                n.equals("U")) {
            if (DBG) log("checkCnapSpecialCases, UNKNOWN string: " + n);
            return Connection.PRESENTATION_UNKNOWN;
        } else {
            if (DBG) log("checkCnapSpecialCases, normal str. number: " + n);
            return CNAP_SPECIAL_CASE_NO;
        }
    }

    /**
     * Handles certain "corner cases" for CNAP. When we receive weird phone numbers
     * from the network to indicate different number presentations, convert them to
     * expected number and presentation values within the CallerInfo object.
     * @param number number we use to verify if we are in a corner case
     * @param presentation presentation value used to verify if we are in a corner case
     * @return the new String that should be used for the phone number
     */
    /* package */ static String modifyForSpecialCnapCases(Context context, CallerInfo ci,
            String number, int presentation) {
        // Obviously we return number if ci == null, but still return number if
        // number == null, because in these cases the correct string will still be
        // displayed/logged after this function returns based on the presentation value.
        if (ci == null || number == null) return number;

        if (DBG) log("modifyForSpecialCnapCases: initially, number=" + number
                + ", presentation=" + presentation + " ci " + ci);

        // "ABSENT NUMBER" is a possible value we could get from the network as the
        // phone number, so if this happens, change it to "Unknown" in the CallerInfo
        // and fix the presentation to be the same.
        if (number.equals(context.getString(R.string.absent_num))
                && presentation == Connection.PRESENTATION_ALLOWED) {
            number = context.getString(R.string.unknown);
            ci.numberPresentation = Connection.PRESENTATION_UNKNOWN;
        }

        // Check for other special "corner cases" for CNAP and fix them similarly. Corner
        // cases only apply if we received an allowed presentation from the network, so check
        // if we think we have an allowed presentation, or if the CallerInfo presentation doesn't
        // match the presentation passed in for verification (meaning we changed it previously
        // because it's a corner case and we're being called from a different entry point).
        if (ci.numberPresentation == Connection.PRESENTATION_ALLOWED
                || (ci.numberPresentation != presentation
                        && presentation == Connection.PRESENTATION_ALLOWED)) {
            int cnapSpecialCase = checkCnapSpecialCases(number);
            if (cnapSpecialCase != CNAP_SPECIAL_CASE_NO) {
                // For all special strings, change number & numberPresentation.
                if (cnapSpecialCase == Connection.PRESENTATION_RESTRICTED) {
                    number = context.getString(R.string.private_num);
                } else if (cnapSpecialCase == Connection.PRESENTATION_UNKNOWN) {
                    number = context.getString(R.string.unknown);
                }
                if (DBG) log("SpecialCnap: number=" + number
                        + "; presentation now=" + cnapSpecialCase);
                ci.numberPresentation = cnapSpecialCase;
            }
        }
        if (DBG) log("modifyForSpecialCnapCases: returning number string=" + number);
        return number;
    }

    //
    // Support for 3rd party phone service providers.
    //

    /**
     * Check if all the provider's info is present in the intent.
     * @param intent Expected to have the provider's extra.
     * @return true if the intent has all the extras to build the
     * in-call screen's provider info overlay.
     */
    /* package */ static boolean hasPhoneProviderExtras(Intent intent) {
        if (null == intent) {
            return false;
        }
        final String name = intent.getStringExtra(InCallScreen.EXTRA_GATEWAY_PROVIDER_PACKAGE);
        final String gatewayUri = intent.getStringExtra(InCallScreen.EXTRA_GATEWAY_URI);

        return !TextUtils.isEmpty(name) && !TextUtils.isEmpty(gatewayUri);
    }

    /**
     * Copy all the expected extras set when a 3rd party provider is
     * used from the source intent to the destination one.  Checks all
     * the required extras are present, if any is missing, none will
     * be copied.
     * @param src Intent which may contain the provider's extras.
     * @param dst Intent where a copy of the extras will be added if applicable.
     */
    /* package */ static void checkAndCopyPhoneProviderExtras(Intent src, Intent dst) {
        if (!hasPhoneProviderExtras(src)) {
            Log.d(LOG_TAG, "checkAndCopyPhoneProviderExtras: some or all extras are missing.");
            return;
        }

        dst.putExtra(InCallScreen.EXTRA_GATEWAY_PROVIDER_PACKAGE,
                     src.getStringExtra(InCallScreen.EXTRA_GATEWAY_PROVIDER_PACKAGE));
        dst.putExtra(InCallScreen.EXTRA_GATEWAY_URI,
                     src.getStringExtra(InCallScreen.EXTRA_GATEWAY_URI));
    }

    /**
     * Get the provider's label from the intent.
     * @param context to lookup the provider's package name.
     * @param intent with an extra set to the provider's package name.
     * @return The provider's application label. null if an error
     * occurred during the lookup of the package name or the label.
     */
    /* package */ static CharSequence getProviderLabel(Context context, Intent intent) {
        String packageName = intent.getStringExtra(InCallScreen.EXTRA_GATEWAY_PROVIDER_PACKAGE);
        PackageManager pm = context.getPackageManager();

        try {
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);

            return pm.getApplicationLabel(info);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /**
     * Get the provider's icon.
     * @param context to lookup the provider's icon.
     * @param intent with an extra set to the provider's package name.
     * @return The provider's application icon. null if an error occured during the icon lookup.
     */
    /* package */ static Drawable getProviderIcon(Context context, Intent intent) {
        String packageName = intent.getStringExtra(InCallScreen.EXTRA_GATEWAY_PROVIDER_PACKAGE);
        PackageManager pm = context.getPackageManager();

        try {
            return pm.getApplicationIcon(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /**
     * Return the gateway uri from the intent.
     * @param intent With the gateway uri extra.
     * @return The gateway URI or null if not found.
     */
    /* package */ static Uri getProviderGatewayUri(Intent intent) {
        String uri = intent.getStringExtra(InCallScreen.EXTRA_GATEWAY_URI);
        return TextUtils.isEmpty(uri) ? null : Uri.parse(uri);
    }

    /**
     * Return a formatted version of the uri's scheme specific
     * part. E.g for 'tel:12345678', return '1-234-5678'.
     * @param uri A 'tel:' URI with the gateway phone number.
     * @return the provider's address (from the gateway uri) formatted
     * for user display. null if uri was null or its scheme was not 'tel:'.
     */
    /* package */ static String formatProviderUri(Uri uri) {
        if (null != uri) {
            if ("tel".equals(uri.getScheme())) {
                return PhoneNumberUtils.formatNumber(uri.getSchemeSpecificPart());
            } else {
                return uri.toString();
            }
        }
        return null;
    }

    /**
     * Check if a phone number can be route through a 3rd party
     * gateway. The number must be a global phone number in numerical
     * form (1-800-666-SEXY won't work).
     *
     * MMI codes and the like cannot be used as a dial number for the
     * gateway either.
     *
     * @param number To be dialed via a 3rd party gateway.
     * @return true If the number can be routed through the 3rd party network.
     */
    /* package */ static boolean isRoutableViaGateway(String number) {
        if (TextUtils.isEmpty(number)) {
            return false;
        }
        number = PhoneNumberUtils.stripSeparators(number);
        if (!number.equals(PhoneNumberUtils.convertKeypadLettersToDigits(number))) {
            return false;
        }
        number = PhoneNumberUtils.extractNetworkPortion(number);
        return PhoneNumberUtils.isGlobalPhoneNumber(number);
    }

   /**
    * This function is called when phone answers or places a call.
    * Check if the phone is in a car dock or desk dock.
    * If yes, turn on the speaker, when no wired or BT headsets are connected.
    * Otherwise do nothing.
    */
    private static void activateSpeakerIfDocked(Phone phone) {
        if (DBG) log("activateSpeakerIfDocked()...");

        if (PhoneApp.mDockState == Intent.EXTRA_DOCK_STATE_DESK ||
                PhoneApp.mDockState == Intent.EXTRA_DOCK_STATE_CAR) {
            if (DBG) log("activateSpeakerIfDocked(): In a dock -> may need to turn on speaker.");
            PhoneApp app = PhoneApp.getInstance();
            BluetoothHandsfree bthf = app.getBluetoothHandsfree();

            if (!app.isHeadsetPlugged() && !(bthf != null && bthf.isAudioOn())) {
                turnOnSpeaker(phone.getContext(), true, true);
            }
        }
    }


    /**
     * Returns whether the phone is in ECM ("Emergency Callback Mode") or not.
     */
    /* package */ static boolean isPhoneInEcm(Phone phone) {
        if ((phone != null) && TelephonyCapabilities.supportsEcm(phone)) {
            // For phones that support ECM, return true iff PROPERTY_INECM_MODE == "true".
            // TODO: There ought to be a better API for this than just
            // exposing a system property all the way up to the app layer,
            // probably a method like "inEcm()" provided by the telephony
            // layer.
            String ecmMode =
                    SystemProperties.get(TelephonyProperties.PROPERTY_INECM_MODE);
            if (ecmMode != null) {
                return ecmMode.equals("true");
            }
        }
        return false;
    }

    /**
     * Returns the most appropriate Phone object to handle a call
     * to the specified number.
     *
     * @param cm the CallManager.
     * @param scheme the scheme from the data URI that the number originally came from.
     * @param number the phone number, or SIP address.
     */
    public static Phone pickPhoneBasedOnNumber(CallManager cm,
            String scheme, String number, String primarySipUri) {
        if (DBG) log("pickPhoneBasedOnNumber: scheme " + scheme
                + ", number " + number + ", sipUri " + primarySipUri);

        if (primarySipUri != null) {
            Phone phone = getSipPhoneFromUri(cm, primarySipUri);
            if (phone != null) return phone;
        }
        
        if(FeatureOption.MTK_GEMINI_SUPPORT){
            return PhoneApp.getInstance().mCMGemini.getDefaultPhoneGemini();
        } 
        else return cm.getDefaultPhone();
    }

    public static Phone getSipPhoneFromUri(CallManager cm, String target) {
        for (Phone phone : cm.getAllPhones()) {
            if (phone.getPhoneType() == Phone.PHONE_TYPE_SIP) {
                String sipUri = ((SipPhone) phone).getSipUri();
                if (target.equals(sipUri)) {
                    if (DBG) log("- pickPhoneBasedOnNumber:" +
                            "found SipPhone! obj = " + phone + ", "
                            + phone.getClass());
                    return phone;
                }
            }
        }
        return null;
    }

    public static boolean isRealIncomingCall(Call.State state) {
        return (state == Call.State.INCOMING && !PhoneApp.getInstance().mCM.hasActiveFgCall() && !PhoneApp.getInstance().mCM.hasActiveBgCall());
    }

    //
    // General phone and call state debugging/testing code
    //

    private static void dumpStateForCall(Call call, String prefix) {
        StringBuilder b = new StringBuilder(128);
        b.append(prefix).append(call.getState());
        b.append(" ").append(call.getPhone().getPhoneName());
        b.append(" isAlive ").append(call.getState().isAlive());
        b.append(" isRinging ").append(call.getState().isRinging());
        b.append(" isDialing ").append(call.getState().isDialing());
        b.append(" isIdle ").append(call.isIdle());
        b.append(" hasConnections ").append(call.hasConnections());
        Log.d(LOG_TAG, b.toString());
    }

    /* package */ static void dumpCallState() {
        PhoneApp app = PhoneApp.getInstance();
        CallManager cm = app.mCM;
        Log.d(LOG_TAG, "dumpCallState():");
        Log.d(LOG_TAG, "- Call state = " + cm.getState());

        dumpStateForCall(cm.getActiveFgCall(), "  - FG call: ");
        dumpStateForCall(cm.getFirstActiveBgCall(), "  - BG call: ");
        dumpStateForCall(cm.getFirstActiveRingingCall(), "  - RINGING call: ");

        final boolean hasRingingCall = cm.hasActiveRingingCall();
        final boolean hasActiveCall = cm.hasActiveFgCall();
        final boolean hasHoldingCall = cm.hasActiveBgCall();
        final boolean allLinesTaken = hasActiveCall && hasHoldingCall;
        StringBuilder b = new StringBuilder(128);
        b.append("  - hasRingingCall ").append(hasRingingCall);
        b.append(" hasActiveCall ").append(hasActiveCall);
        b.append(" hasHoldingCall ").append(hasHoldingCall);
        b.append(" allLinesTaken ").append(allLinesTaken);
        Log.d(LOG_TAG, b.toString());

        Phone phone = cm.getDefaultPhone();
        // On CDMA phones, dump out the CdmaPhoneCallState too:
        if (phone.getPhoneType() == Phone.PHONE_TYPE_CDMA) {
            if (app.cdmaPhoneCallState != null) {
                Log.d(LOG_TAG, "  - CDMA call state: "
                      + app.cdmaPhoneCallState.getCurrentCallState());
            } else {
                Log.d(LOG_TAG, "  - CDMA device, but null cdmaPhoneCallState!");
            }
        }

        // Watch out: the isRinging() call below does NOT tell us anything
        // about the state of the telephony layer; it merely tells us whether
        // the Ringer manager is currently playing the ringtone.
        boolean ringing = app.getRinger().isRinging();
        Log.d(LOG_TAG, "  - Ringer state: " + ringing);
    }

    /* package */ static void dumpCallState(Phone phone) {
        PhoneApp app = PhoneApp.getInstance();
        Log.d(LOG_TAG, "dumpCallState():");
//MTK begin:
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            Log.d(LOG_TAG, "- GeminiPhone: " + ((GeminiPhone)phone) + ", name = " + ((GeminiPhone)phone).getPhoneName()
                  + ", state = " + ((GeminiPhone)phone).getState());

            Log.d(LOG_TAG, "- GeminiPhone Sim1: " + ((GeminiPhone)phone) + ", name = " + ((GeminiPhone)phone).getPhoneName()
                  + ", state = " + ((GeminiPhone)phone).getStateGemini(Phone.GEMINI_SIM_1));			

            Log.d(LOG_TAG, "- GeminiPhone Sim2: " + ((GeminiPhone)phone) + ", name = " + ((GeminiPhone)phone).getPhoneName()
                  + ", state = " + ((GeminiPhone)phone).getStateGemini(Phone.GEMINI_SIM_2));						
        } else {
            Log.d(LOG_TAG, "- Phone: " + phone + ", name = " + phone.getPhoneName()
                  + ", state = " + phone.getState());
        }
//MTK end

        StringBuilder b = new StringBuilder(128);

//MTK begin:
        Call call;
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            call = ((GeminiPhone)phone).getForegroundCall();
        } else {
            call = phone.getForegroundCall();
	 }
//MTK end

        b.setLength(0);
        b.append("  - FG call: ").append(call.getState());
        b.append(" isAlive ").append(call.getState().isAlive());
        b.append(" isRinging ").append(call.getState().isRinging());
        b.append(" isDialing ").append(call.getState().isDialing());
        b.append(" isIdle ").append(call.isIdle());
        b.append(" hasConnections ").append(call.hasConnections());
        Log.d(LOG_TAG, b.toString());

//MTK begin:
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            call = ((GeminiPhone)phone).getBackgroundCall();
        } else {
            call = phone.getBackgroundCall();
	    }
//MTK end
        b.setLength(0);
        b.append("  - BG call: ").append(call.getState());
        b.append(" isAlive ").append(call.getState().isAlive());
        b.append(" isRinging ").append(call.getState().isRinging());
        b.append(" isDialing ").append(call.getState().isDialing());
        b.append(" isIdle ").append(call.isIdle());
        b.append(" hasConnections ").append(call.hasConnections());
        Log.d(LOG_TAG, b.toString());

//MTK begin:
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            call = ((GeminiPhone)phone).getRingingCall();
        } else {
            call = phone.getRingingCall();
	    }
//MTK end
        b.setLength(0);
        b.append("  - RINGING call: ").append(call.getState());
        b.append(" isAlive ").append(call.getState().isAlive());
        b.append(" isRinging ").append(call.getState().isRinging());
        b.append(" isDialing ").append(call.getState().isDialing());
        b.append(" isIdle ").append(call.isIdle());
        b.append(" hasConnections ").append(call.hasConnections());
        Log.d(LOG_TAG, b.toString());


        final boolean hasRingingCall = !phone.getRingingCall().isIdle();
        final boolean hasActiveCall = !phone.getForegroundCall().isIdle();
        final boolean hasHoldingCall = !phone.getBackgroundCall().isIdle();
        final boolean allLinesTaken = hasActiveCall && hasHoldingCall;
        b.setLength(0);
        b.append("  - hasRingingCall ").append(hasRingingCall);
        b.append(" hasActiveCall ").append(hasActiveCall);
        b.append(" hasHoldingCall ").append(hasHoldingCall);
        b.append(" allLinesTaken ").append(allLinesTaken);
        Log.d(LOG_TAG, b.toString());

        // On CDMA phones, dump out the CdmaPhoneCallState too:
        if (phone.getPhoneType() == Phone.PHONE_TYPE_CDMA) {
            if (app.cdmaPhoneCallState != null) {
                Log.d(LOG_TAG, "  - CDMA call state: "
                      + app.cdmaPhoneCallState.getCurrentCallState());
            } else {
                Log.d(LOG_TAG, "  - CDMA device, but null cdmaPhoneCallState!");
            }
        }

        // Watch out: the isRinging() call below does NOT tell us anything
        // about the state of the telephony layer; it merely tells us whether
        // the Ringer manager is currently playing the ringtone.
        boolean ringing = app.getRinger().isRinging();
        Log.d(LOG_TAG, "  - Ringer state: " + ringing);
    }

    private static void log(String msg) {
        Log.d(LOG_TAG, msg);
    }

    static void dumpCallManager() {
        Call call;
        CallManager cm = PhoneApp.getInstance().mCM;
        StringBuilder b = new StringBuilder(128);

        Log.d(LOG_TAG, "############### dumpCallManager() ##############");
        // TODO: Don't log "cm" itself, since CallManager.toString()
        // already spews out almost all this same information.
        // We should fix CallManager.toString() to be more minimal, and
        // use an explicit dumpState() method for the verbose dump.
        // Log.d(LOG_TAG, "CallManager: " + cm
        //         + ", state = " + cm.getState());
        Log.d(LOG_TAG, "CallManager: state = " + cm.getState());
        b.setLength(0);
        call = cm.getActiveFgCall();
        b.append(" - FG call: ").append(cm.hasActiveFgCall()? "YES ": "NO ");
        b.append(call);
        b.append( "  State: ").append(cm.getActiveFgCallState());
        b.append( "  Conn: ").append(cm.getFgCallConnections());
        Log.d(LOG_TAG, b.toString());
        b.setLength(0);
        call = cm.getFirstActiveBgCall();
        b.append(" - BG call: ").append(cm.hasActiveBgCall()? "YES ": "NO ");
        b.append(call);
        b.append( "  State: ").append(cm.getFirstActiveBgCall().getState());
        b.append( "  Conn: ").append(cm.getBgCallConnections());
        Log.d(LOG_TAG, b.toString());
        b.setLength(0);
        call = cm.getFirstActiveRingingCall();
        b.append(" - RINGING call: ").append(cm.hasActiveRingingCall()? "YES ": "NO ");
        b.append(call);
        b.append( "  State: ").append(cm.getFirstActiveRingingCall().getState());
        Log.d(LOG_TAG, b.toString());



        for (Phone phone : CallManager.getInstance().getAllPhones()) {
            if (phone != null) {
                Log.d(LOG_TAG, "Phone: " + phone + ", name = " + phone.getPhoneName()
                        + ", state = " + phone.getState());
                b.setLength(0);
                call = phone.getForegroundCall();
                b.append(" - FG call: ").append(call);
                b.append( "  State: ").append(call.getState());
                b.append( "  Conn: ").append(call.hasConnections());
                Log.d(LOG_TAG, b.toString());
                b.setLength(0);
                call = phone.getBackgroundCall();
                b.append(" - BG call: ").append(call);
                b.append( "  State: ").append(call.getState());
                b.append( "  Conn: ").append(call.hasConnections());
                Log.d(LOG_TAG, b.toString());b.setLength(0);
                call = phone.getRingingCall();
                b.append(" - RINGING call: ").append(call);
                b.append( "  State: ").append(call.getState());
                b.append( "  Conn: ").append(call.hasConnections());
                Log.d(LOG_TAG, b.toString());
            }
        }

        Log.d(LOG_TAG, "############## END dumpCallManager() ###############");
    }

    //Used for MMI
    
    private static void autoUpdateUssdReponseUi(View dialogView){
        TextView justForUpdate = (TextView) dialogView.findViewById(R.id.ussd_update);
        justForUpdate.setWidth(1);
        justForUpdate.setText(R.string.fdn_contact_name_number_invalid);
        justForUpdate.setFocusableInTouchMode(true);
    }
    
    //Add for recording the USSD dialog, and use to cancel the dialog when enter airplane mode
    public static void DismissMMIDialog()
    {
        if (null != mDialog)
        {
            mDialog.cancel();
            mDialog = null;
        }
    }
    
    public static boolean isShowUssdDialog()
    {
        return mUssdActivity != null;
    }

    static public boolean getMmiFinished()
    {
    	return mbMmiFinished;
    }
    
    static public void setMmiFinished(boolean state)
    {
    	mbMmiFinished = state;
    }
    //End Used for MMI
    
    static int placeVTCall(Phone phone, String number, Uri contactRef, int simId) {
        int status = CALL_STATUS_DIALED;
        try {
            if (DBG) log("placeVTCall: '" + number + "'..." + "simId : "+simId);

			placeCallRegister(phone);
            Connection cn;
			if (FeatureOption.MTK_GEMINI_SUPPORT) {
				if (FeatureOption.MTK_GEMINI_3G_SWITCH)
					cn = PhoneApp.getInstance().mCMGemini.vtDialGemini(phone,
							number, simId);
				else
					cn = PhoneApp.getInstance().mCMGemini.vtDialGemini(phone,
							number, Phone.GEMINI_SIM_1);
			} else {
				cn = PhoneApp.getInstance().mCM.vtDial(phone, number);
			}

            if (DBG) log("===> vtDial() returned: " + cn);

            int phoneType = phone.getPhoneType();

            // On GSM phones, null is returned for MMI codes
            if (cn == null) {
                if (phoneType == Phone.PHONE_TYPE_GSM) {
                    if (DBG) log("dialed MMI code: " + number);
                    status = CALL_STATUS_DIALED_MMI;
                    // Set dialed MMI command to service
                    if (mNwService != null) {
                        try {
                            mNwService.setMmiString(number);
                            if (DBG) log("Extended NW bindService setUssdString (" + number + ")");
                        } catch (RemoteException e) {
                            mNwService = null;
                        }
                    }
                } else {
                    status = PhoneUtils.CALL_STATUS_FAILED;
                }
            } else {
                PhoneApp app = PhoneApp.getInstance();

                if (phoneType == Phone.PHONE_TYPE_CDMA) {
                    updateCdmaCallStateOnNewOutgoingCall(app);
                }

                PhoneUtils.setAudioControlState(PhoneUtils.AUDIO_OFFHOOK);

                // phone.dial() succeeded: we're now in a normal phone call.
                // attach the URI to the CallerInfo Object if it is there,
                // otherwise just attach the Uri Reference.
                // if the uri does not have a "content" scheme, then we treat
                // it as if it does NOT have a unique reference.
                String content = phone.getContext().getContentResolver().SCHEME_CONTENT;
                if ((contactRef != null) && (contactRef.getScheme().equals(content))) {
                    Object userDataObject = cn.getUserData();
                    if (userDataObject == null) {
                        cn.setUserData(contactRef);
                    } else {
                        // TODO: This branch is dead code, we have
                        // just created the connection 'cn' which has
                        // no user data (null) by default.
                        if (userDataObject instanceof CallerInfo) {
                            ((CallerInfo) userDataObject).contactRefUri = contactRef;
                        } else {
                            ((CallerInfoToken) userDataObject).currentInfo.contactRefUri =
                                contactRef;
                        }
                    }
                }

                // Check is phone in any dock, and turn on speaker accordingly
                activateSpeakerIfDocked(phone);
            }
        } catch (CallStateException ex) {
            Log.w(LOG_TAG, "Exception from phone.dial()", ex);
            status = CALL_STATUS_FAILED;
        }

        return status;
    }

    // MTK_OP01_PROTECT_START
    // MTK_OP02_PROTECT_START
    public static String getOptrProperties() {
        if (null == mOptr) {
            mOptr = SystemProperties.get("ro.operator.optr");
            if (DBG) log("getOptrProperties, the mOptr is " + mOptr);
            if (null == mOptr) {
                mOptr = "";
            }
        }
        return mOptr;   
    } 

    public static String getSpecProperties() {
        if (null == mSpec) {
            mSpec = SystemProperties.get("ro.operator.spec");			 
            if (DBG) log("getSpecProperties, the mSpec is " + mSpec);
            if (null == mSpec) {
                mSpec = "";
            }
        }
        return mSpec;			
    }

    public static String getSegProperties() {
        if (null == mSeg) {
            mSeg = SystemProperties.get("ro.operator.seg");			
            if (DBG) log("getSegProperties, the mSeg is " + mSeg);
            if (null == mSeg) {
                mSeg = "";
            }
        }
        return mSeg;
    }
    // MTK_OP02_PROTECT_END
    // MTK_OP01_PROTECT_END

    public static boolean isDMLocked(){
        boolean locked = false;
        try{
            IBinder binder=ServiceManager.getService("DMAgent");
            DMAgent agent = null;
            if(binder != null)agent=DMAgent.Stub.asInterface (binder); 
            if(agent != null)locked=agent.isLockFlagSet ();
        }catch (Exception e) {
            e.printStackTrace ();
        } 
        if (DBG) log("isDMLocked(): locked = " + locked);
        return locked;
    }

    public static void setDualMicMode(String dualMic)
    {
        Context context = PhoneApp.getInstance().getApplicationContext();
        if (context == null) return;
        AudioManager audioManager = (AudioManager)
                context.getSystemService(Context.AUDIO_SERVICE);
        audioManager.setParameters(PhoneUtils.DUALMIC_MODE + "=" + dualMic);
    }
    
    public static boolean isSupportFeature(String feature) {
        if (feature == null) {
            return false;
        }
        
        if (feature.equals("TTY")) {
            return FeatureOption.MTK_TTY_SUPPORT;
        } else if (feature.equals("DUAL_MIC")) {
            return FeatureOption.MTK_DUAL_MIC_SUPPORT;
        } else if (feature.equals("IP_DIAL")) {
            return true;
        } else if (feature.equals("3G_SWITCH")) {
            return FeatureOption.MTK_GEMINI_3G_SWITCH;
        }
        
        return false;
    }
    
    public static boolean isVideoCall(Call call) {
        if (call == null /*|| !call.getState().isAlive()*/) {
            return false;
        }
        
        Connection c = call.getLatestConnection();
        if (c == null) {
            return false;
        } else {
            return c.isVideo();
        }
    }

     static void cancelUssdDialog() {
        if (curCode != null && curCode.isCancelable()) {
            curCode.cancel();
        }
    }

    static void displayMmiDialog(Context context, CharSequence text, int type, int slot) {
        Intent intent = new Intent();
        intent.setClass(context, com.android.phone.UssdAlertActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(UssdAlertActivity.USSD_MESSAGE_EXTRA, text);
        intent.putExtra(UssdAlertActivity.USSD_TYPE_EXTRA, type);
        intent.putExtra(UssdAlertActivity.USSD_SLOT_ID, slot);
        context.startActivity(intent);
    }
    
    static class WorkerThread extends HandlerThread {
        public final static String TAG = "WorkerThread";
        public final static int CLOSE_CURSOR = 1;
        private static final WorkerThread INSTANCE = new WorkerThread();
        
        Looper mLooper = null;
        Handler mHandler = null;
        
        private WorkerThread() {
            super("PhoneUtilsWorker");
            if (DBG) {
                Log.d(TAG, "construct the worker thread with looper = " + mLooper);
            }
        }
        
        public void startWorkerThread() {
            if (mLooper != null) {
                return ;
            }
            
            INSTANCE.start();
            mLooper = getLooper();
            mHandler = new MyHandler(mLooper);
        }
        
        public static  WorkerThread getWorkerThread() {
            return INSTANCE;
        }
        
        public Handler getHandler() {
            return mHandler;
        }
        
        class MyHandler extends Handler {
            
            public MyHandler(Looper loop) {
                super(loop);
            }
            
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case CLOSE_CURSOR:
                        if (DBG) Log.d(TAG, "handle message try to CLOSE_CURSOR");
                        if (msg.obj != null) {
                            if (msg.obj instanceof Cursor) {
                                try {
                                    Cursor c = (Cursor)msg.obj;
                                    c.close();
                                } catch (Exception e) {
                                    Log.d(TAG, "Close cursor exception : " + e.toString());
                                }
                            }
                        } else {
                            if (DBG) Log.d(TAG, "handle message CLOSE_CURSOR with null object!");
                        }
                        if (DBG) Log.d(TAG, "handle message finished CLOSE_CURSOR");
                        break;
                }
            }
        }
    }
}

/*
 * Copyright (C) 2012 The CyanogenMod Project
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

package com.android.settings.cyanogenmod;

import android.app.INotificationManager;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;

    public class HaloSettings extends SettingsPreferenceFragment implements OnPreferenceChangeListener {
    private static final String KEY_HALO_ENABLED = "halo_enabled";
    private static final String KEY_HALO_STATE = "halo_state";
    private static final String KEY_HALO_HIDE = "halo_hide";
    private static final String KEY_HALO_REVERSED = "halo_reversed"; 
    private static final String KEY_WE_WANT_POPUPS = "show_popup";
	
    private CheckBoxPreference mHaloEnabled;
    private ListPreference mHaloState;
    private CheckBoxPreference mHaloHide;
    private CheckBoxPreference mHaloReversed; 
    private CheckBoxPreference mWeWantPopups;

    private INotificationManager mNotificationManager; 
    private ContentResolver mContentResolver;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.halo);

        PreferenceScreen prefSet = getPreferenceScreen();
        mContentResolver = getActivity().getApplicationContext().getContentResolver();

        mNotificationManager = INotificationManager.Stub.asInterface(
                ServiceManager.getService(Context.NOTIFICATION_SERVICE));

        mHaloEnabled = (CheckBoxPreference) findPreference(KEY_HALO_ENABLED);
        mHaloEnabled.setChecked(Settings.System.getInt(getActivity().getContentResolver(),
                Settings.System.HALO_ENABLED, 0) == 1);

        mHaloState = (ListPreference) findPreference(KEY_HALO_STATE);
        mHaloState.setValue(String.valueOf((isHaloPolicyBlack() ? "1" : "0")));
        mHaloState.setOnPreferenceChangeListener(this);

        mHaloHide = (CheckBoxPreference) findPreference(KEY_HALO_HIDE);
        mHaloHide.setChecked(Settings.System.getInt(getActivity().getContentResolver(),
                Settings.System.HALO_HIDE, 0) == 1);

        mHaloReversed = (CheckBoxPreference) findPreference(KEY_HALO_REVERSED);
        mHaloReversed.setChecked(Settings.System.getInt(getActivity().getContentResolver(),
                Settings.System.HALO_REVERSED, 1) == 1); 

        int showPopups = Settings.System.getInt(getContentResolver(), Settings.System.WE_WANT_POPUPS, 1);

        mWeWantPopups = (CheckBoxPreference) findPreference(KEY_WE_WANT_POPUPS);
        mWeWantPopups.setOnPreferenceChangeListener(this);
        mWeWantPopups.setChecked(showPopups > 0);

    }

    private boolean isHaloPolicyBlack() {
        try {
            return mNotificationManager.isHaloPolicyBlack();
        } catch (android.os.RemoteException ex) {
                // System dead
        }
        return true;
    }

    public boolean onPreferenceChange(Preference preference, Object objValue) {
        if (preference == mHaloState) {
            boolean state = Integer.valueOf((String) objValue) == 1;
            try {
                mNotificationManager.setHaloPolicyBlack(state);
            } catch (android.os.RemoteException ex) {
                // System dead
            }          
            return true; 
        } else if (preference == mWeWantPopups) {
            boolean checked = (Boolean) objValue;
                        Settings.System.putInt(getActivity().getContentResolver(),
                                Settings.System.WE_WANT_POPUPS, checked ? 1 : 0);
            return true;
        }

        return false;
    }


     @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if  (preference == mHaloEnabled) {  
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.HALO_ENABLED, mHaloEnabled.isChecked()
                    ? 1 : 0);  
        } else if  (preference == mHaloHide) {  
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.HALO_HIDE, mHaloHide.isChecked()
                    ? 1 : 0);  

        } else if (preference == mHaloReversed) {  
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.HALO_REVERSED, mHaloReversed.isChecked()
                    ? 1 : 0);  
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }
}

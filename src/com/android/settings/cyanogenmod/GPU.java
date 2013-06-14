/*
 * Copyright (C) 2013 The CyanogenMod Project
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

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;

//
// KGSL Governor Related Settings
//
public class GPU extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    public static final String GPUGOV_PREF = "pref_gpu_governor";
    public static final String GPU_CUR_FREQ_PREF = "pref_gpu_freq_cur";
    public static final String GPUGOV_LIST_FILE = "/sys/devices/platform/kgsl-3d0.0/kgsl/kgsl-3d0/pwrscale/avail_policies";
    public static final String GPUGOV_CUR_FILE = "/sys/devices/platform/kgsl-3d0.0/kgsl/kgsl-3d0/pwrscale/policy";
    public static final String GPU_CUR_FREQ_FILE = "/sys/devices/platform/kgsl-3d0.0/kgsl/kgsl-3d0/gpuclk/";

    public static final String SOB_PREF = "pref_gpu_governor_set_on_boot";

    private static final String TAG = "GPUGovernor";

    private String mGPUGovernorFormat;

    private ListPreference mGPUGovernorPref;
    private Preference mCurFrequencyPref;

    private class CurGPUThread extends Thread {
        private boolean mInterrupt = false;

        public void interrupt() {
            mInterrupt = true;
        }

        @Override
        public void run() {
            try {
                while (!mInterrupt) {
                    sleep(500);
                    final String curFreq = Utils.fileReadOneLine(GPU_CUR_FREQ_FILE);
                    if (curFreq != null)
                        mCurGPUHandler.sendMessage(mCurGPUHandler.obtainMessage(0, curFreq));
                }
            } catch (InterruptedException e) {
            }
        }
    };

    private CurGPUThread mCurGPUThread = new CurGPUThread();

    private Handler mCurGPUHandler = new Handler() {
        public void handleMessage(Message msg) {
            mCurFrequencyPref.setSummary(toMHz((String) msg.obj));
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mGPUGovernorFormat = getString(R.string.gpu_governor_summary);

        String[] availableGPUGovernors = new String[0];
        String availableGPUGovernorsLine;
        String currentGPUGovernor = null;

        addPreferencesFromResource(R.xml.gpu_settings);

        PreferenceScreen prefScreen = getPreferenceScreen();

        mGPUGovernorPref = (ListPreference) prefScreen.findPreference(GPUGOV_PREF);
        mCurFrequencyPref = (Preference) prefScreen.findPreference(GPU_CUR_FREQ_PREF);

        /* GPU Governor
           Some systems might not use it */
        if (Utils.fileExists(GPUGOV_LIST_FILE) &&
            (availableGPUGovernorsLine = Utils.fileReadOneLine(GPUGOV_LIST_FILE)) != null) {
            availableGPUGovernors = Utils.fileReadOneLine(GPUGOV_LIST_FILE).split(" ");
            currentGPUGovernor = Utils.fileReadOneLine(GPUGOV_CUR_FILE);
            mGPUGovernorPref.setEntryValues(availableGPUGovernors);
            mGPUGovernorPref.setEntries(availableGPUGovernors);
            if (currentGPUGovernor != null)
                mGPUGovernorPref.setValue(currentGPUGovernor);
            mGPUGovernorPref.setSummary(String.format(mGPUGovernorFormat, currentGPUGovernor));
            mGPUGovernorPref.setOnPreferenceChangeListener(this);
        } else {
            prefScreen.removePreference(mGPUGovernorPref);
        }

        // Cur frequency
        if (!Utils.fileExists(GPU_CUR_FREQ_FILE) || Utils.fileReadOneLine(GPU_CUR_FREQ_FILE) == null) {
            mCurFrequencyPref.setEnabled(false);

        } else {
            mCurFrequencyPref.setSummary(toMHz(Utils.fileReadOneLine(GPU_CUR_FREQ_FILE)));

            mCurGPUThread.start();
        }
    }

    @Override
    public void onResume() {
        String availableGPUGovernorsLine;
        String currentGPUGovernor;

        super.onResume();

        if (Utils.fileExists(GPUGOV_LIST_FILE) &&
           (availableGPUGovernorsLine = Utils.fileReadOneLine(GPUGOV_LIST_FILE)) != null) {
            currentGPUGovernor = Utils.fileReadOneLine(GPUGOV_CUR_FILE);
            mGPUGovernorPref.setSummary(String.format(mGPUGovernorFormat, currentGPUGovernor));
        }
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String fname = "";

        if (newValue != null) {
            if (preference == mGPUGovernorPref) {
                fname = GPUGOV_CUR_FILE;
            }

            if (Utils.fileWriteOneLine(fname, (String) newValue)) {
                if (preference == mGPUGovernorPref) {
                    mGPUGovernorPref.setSummary(String.format(mGPUGovernorFormat, (String) newValue));
                }
                return true;
            } else {
                return false;
            }
        }
        return false;
    }

    private String toMHz(String mhzString) {
        return new StringBuilder().append(Integer.valueOf(mhzString) / 1000000).append(" MHz")
                .toString();
    }
}

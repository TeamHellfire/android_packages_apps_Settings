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

import android.app.Activity;
import android.app.ActivityManager;
import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.provider.MediaStore;
import android.view.Display;
import android.view.Window;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;

import com.android.internal.widget.LockPatternUtils;
import com.android.settings.colorpicker.ColorPickerPreference;
import com.android.settings.cyanogenmod.SeekBarPreference;
import com.android.settings.ChooseLockSettingsHelper;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;

import java.io.File;
import java.io.IOException;

public class LockscreenInterface extends SettingsPreferenceFragment implements
            Preference.OnPreferenceChangeListener {

    private static final String TAG = "LockscreenInterface";

    private static final String KEY_ENABLE_WIDGETS = "keyguard_enable_widgets";
    private static final String KEY_LOCK_CLOCK = "lock_clock";
    private static final String LOCKSCREEN_WIDGETS_CATEGORY = "lockscreen_widgets_category";
    private static final String LOCKSCREEN_BACKGROUND = "lockscreen_background";
    private static final String LOCKSCREEN_BACKGROUND_STYLE = "lockscreen_background_style";
    private static final String LOCKSCREEN_BACKGROUND_COLOR_FILL = "lockscreen_background_color_fill";
    private static final String LOCKSCREEN_WALLPAPER_ALPHA = "lockscreen_wallpaper_alpha";

    private static final int REQUEST_PICK_WALLPAPER = 201;
    private static final int COLOR_FILL = 0;
    private static final int CUSTOM_IMAGE = 1;
    private static final int DEFAULT = 2;

    private CheckBoxPreference mEnableKeyguardWidgets;
    private ColorPickerPreference mLockColorFill;
    private ListPreference mLockBackground;
    private SeekBarPreference mWallpaperAlpha;

    private ChooseLockSettingsHelper mChooseLockSettingsHelper;
    private DevicePolicyManager mDPM;
    private PreferenceCategory mLockscreenBackground;
    private boolean mIsPrimary;
    private File wallpaperImage;
    private File wallpaperTemporary;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mChooseLockSettingsHelper = new ChooseLockSettingsHelper(getActivity());
        mDPM = (DevicePolicyManager)getSystemService(Context.DEVICE_POLICY_SERVICE);

        addPreferencesFromResource(R.xml.lockscreen_interface_settings);
        PreferenceCategory widgetsCategory = (PreferenceCategory) findPreference(LOCKSCREEN_WIDGETS_CATEGORY);
        mLockscreenBackground = (PreferenceCategory) findPreference(LOCKSCREEN_BACKGROUND);

        mLockBackground = (ListPreference) findPreference(LOCKSCREEN_BACKGROUND_STYLE);
        mLockBackground.setOnPreferenceChangeListener(this);
        mLockBackground.setValue(Integer.toString(Settings.System.getInt(getContentResolver(),
                Settings.System.LOCKSCREEN_BACKGROUND_STYLE, 2)));
        mLockBackground.setSummary(mLockBackground.getEntry());

        mWallpaperAlpha = (SeekBarPreference) findPreference(LOCKSCREEN_WALLPAPER_ALPHA);
        mWallpaperAlpha.setOnPreferenceChangeListener(this);
        float mWallpaperAlphaTransparency = 1.0f;
        try {
            mWallpaperAlphaTransparency = Settings.System.getFloat(getContentResolver(),
                    Settings.System.LOCKSCREEN_WALLPAPER_ALPHA);
        } catch (Exception e) {
            mWallpaperAlphaTransparency = 1.0f;
            Settings.System.putFloat(getContentResolver(),
                    Settings.System.LOCKSCREEN_WALLPAPER_ALPHA, 1.0f);
        }
        mWallpaperAlpha.setInitValue((int) (mWallpaperAlphaTransparency * 100));

        mLockColorFill = (ColorPickerPreference) findPreference(LOCKSCREEN_BACKGROUND_COLOR_FILL);
        mLockColorFill.setOnPreferenceChangeListener(this);
        mLockColorFill.setSummary(ColorPickerPreference.convertToARGB(
                Settings.System.getInt(getContentResolver(),
                Settings.System.LOCKSCREEN_BACKGROUND_COLOR, 0x00000000)));

        // Only add if device has LockClock installed
        if (!isPackageInstalled("com.cyanogenmod.lockclock")) {
            widgetsCategory.removePreference(findPreference(KEY_LOCK_CLOCK));
        }

        // Determine which user is logged in
        mIsPrimary = UserHandle.myUserId() == UserHandle.USER_OWNER;
        if (mIsPrimary) {
            // Its the primary user, show all the settings
            if (!Utils.isPhone(getActivity())) {
                if (widgetsCategory != null) {
                    widgetsCategory.removePreference(
                            findPreference(Settings.System.LOCKSCREEN_MAXIMIZE_WIDGETS));
                }
            }

        } else {
            // Secondary user is logged in, remove all primary user specific preferences
        }

        // This applies to all users
        // Enable or disable keyguard widget checkbox based on DPM state
        mEnableKeyguardWidgets = (CheckBoxPreference) findPreference(KEY_ENABLE_WIDGETS);
        if (mEnableKeyguardWidgets != null) {
            if (ActivityManager.isLowRamDeviceStatic()) {
                    /*|| mLockPatternUtils.isLockScreenDisabled()) {*/
                // Widgets take a lot of RAM, so disable them on low-memory devices
                if (widgetsCategory != null) {
                    widgetsCategory.removePreference(findPreference(KEY_ENABLE_WIDGETS));
                    mEnableKeyguardWidgets = null;
                }
            } else {
                final boolean disabled = (0 != (mDPM.getKeyguardDisabledFeatures(null)
                        & DevicePolicyManager.KEYGUARD_DISABLE_WIDGETS_ALL));
                if (disabled) {
                    mEnableKeyguardWidgets.setSummary(
                            R.string.security_enable_widgets_disabled_summary);
                }
                mEnableKeyguardWidgets.setEnabled(!disabled);
            }
        }

        wallpaperImage = new File(getActivity().getFilesDir()+"/lockwallpaper");
        wallpaperTemporary = new File(getActivity().getCacheDir()+"/lockwallpaper.tmp");
        updateVisiblePreferences();
    }

    @Override
    public void onResume() {
        super.onResume();
        final LockPatternUtils lockPatternUtils = mChooseLockSettingsHelper.utils();
        if (mEnableKeyguardWidgets != null) {
            mEnableKeyguardWidgets.setChecked(lockPatternUtils.getWidgetsEnabled());
        }
        updateVisiblePreferences();
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        final String key = preference.getKey();

        final LockPatternUtils lockPatternUtils = mChooseLockSettingsHelper.utils();
        if (KEY_ENABLE_WIDGETS.equals(key)) {
            lockPatternUtils.setWidgetsEnabled(mEnableKeyguardWidgets.isChecked());
            return true;
        }

        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    public boolean onPreferenceChange(Preference preference, Object objValue) {
        if (preference == mLockBackground) {
            int index = mLockBackground.findIndexOfValue(objValue.toString());
            preference.setSummary(mLockBackground.getEntries()[index]);
            return handleBackgroundSelection(index);
        } else if (preference == mWallpaperAlpha) {
            float value = Float.parseFloat((String) objValue);
            Settings.System.putFloat(getContentResolver(),
                    Settings.System.LOCKSCREEN_WALLPAPER_ALPHA, value / 100);
            return true;
        } else if (preference == mLockColorFill) {
            String hex = ColorPickerPreference.convertToARGB(
                    Integer.valueOf(String.valueOf(objValue)));
            preference.setSummary(hex);
            int value = ColorPickerPreference.convertToColorInt(hex);
            Settings.System.putInt(getContentResolver(),
                    Settings.System.LOCKSCREEN_BACKGROUND_COLOR, value);
            return true;
        }
        return false;
    }

    private void updateVisiblePreferences() {
        int visible = Settings.System.getInt(getContentResolver(),
                Settings.System.LOCKSCREEN_BACKGROUND_STYLE, 2);
        if (visible == 1) {
            mLockscreenBackground.addPreference(mWallpaperAlpha);
        } else {
            mLockscreenBackground.removePreference(mWallpaperAlpha);
        }
        if (visible == 0) {
            mLockscreenBackground.addPreference(mLockColorFill);
        } else {
            mLockscreenBackground.removePreference(mLockColorFill);
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PICK_WALLPAPER) {
            if (resultCode == Activity.RESULT_OK) {
                if (wallpaperTemporary.exists()) {
                    wallpaperTemporary.renameTo(wallpaperImage);
                }
                wallpaperImage.setReadable(true, false);
                Toast.makeText(getActivity(), getResources().getString(R.string.
                        background_result_successful), Toast.LENGTH_LONG).show();
                Settings.System.putInt(getContentResolver(),
                        Settings.System.LOCKSCREEN_BACKGROUND_STYLE, 1);
                updateVisiblePreferences();
            } else {
                if (wallpaperTemporary.exists()) {
                    wallpaperTemporary.delete();
                }
                Toast.makeText(getActivity(), getResources().getString(R.string.
                        background_result_not_successful), Toast.LENGTH_LONG).show();
            }
        }
    }

    private boolean handleBackgroundSelection(int index) {
        if (index == COLOR_FILL) {
            Settings.System.putInt(getContentResolver(),
                    Settings.System.LOCKSCREEN_BACKGROUND_STYLE, 0);
            updateVisiblePreferences();
            return true;
        } else if (index == CUSTOM_IMAGE) {
            // Used to reset the image when already set
            Settings.System.putInt(getContentResolver(),
                    Settings.System.LOCKSCREEN_BACKGROUND_STYLE, 2);
            // Launches intent for user to select an image/crop it to set as background
            final Intent intent = new Intent(Intent.ACTION_GET_CONTENT, null);
            intent.setType("image/*");
            intent.putExtra("crop", "true");
            intent.putExtra("scale", true);
            intent.putExtra("scaleUpIfNeeded", false);
            intent.putExtra("scaleType", 6);
            intent.putExtra("layout_width", -1);
            intent.putExtra("layout_height", -2);
            intent.putExtra("outputFormat", Bitmap.CompressFormat.PNG.toString());

            final Display display = getActivity().getWindowManager().getDefaultDisplay();
            final Rect rect = new Rect();
            final Window window = getActivity().getWindow();

            window.getDecorView().getWindowVisibleDisplayFrame(rect);

            int statusBarHeight = rect.top;
            int contentViewTop = window.findViewById(Window.ID_ANDROID_CONTENT).getTop();
            int titleBarHeight = contentViewTop - statusBarHeight;
            boolean isPortrait = getResources().getConfiguration().orientation ==
                    Configuration.ORIENTATION_PORTRAIT;

            int width = display.getWidth();
            int height = display.getHeight() - titleBarHeight;

            intent.putExtra("aspectX", isPortrait ? width : height);
            intent.putExtra("aspectY", isPortrait ? height : width);

            try {
                wallpaperTemporary.createNewFile();
                wallpaperTemporary.setWritable(true, false);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(wallpaperTemporary));
                intent.putExtra("return-data", false);
                getActivity().startActivityFromFragment(this, intent, REQUEST_PICK_WALLPAPER);
            } catch (IOException e) {
            } catch (ActivityNotFoundException e) {
            }
        } else if (index == DEFAULT) {
            // Sets background to default
            Settings.System.putInt(getContentResolver(),
                            Settings.System.LOCKSCREEN_BACKGROUND_STYLE, 2);
            updateVisiblePreferences();
            return true;
        }
        return false;
    }

    public static class DeviceAdminLockscreenReceiver extends DeviceAdminReceiver {}

}

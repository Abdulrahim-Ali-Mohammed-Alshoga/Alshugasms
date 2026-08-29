/*
 * Copyright (C) 2026 yztz
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package top.yztz.msggo.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;

import java.util.HashMap;

public class SettingManager {
    private static final String TAG = "SettingManager";
    private static final String PREF_NAME = "setting_prefs";
    private static SharedPreferences mSp;
    private static SharedPreferences.Editor mEditor;
    private static final HashMap<String, Object> DefaultPropMap = new HashMap<>();

    private static final String SEND_DELAY_KEY = "send_delay_v1";
    private static final String SEND_DELAY_RANDOMIZATION_KEY = "send_delay_randomization_v1";
    private static final String EDIT_AFTER_IMPORT_KEY = "edit_after_import_v1";
    private static final String SMS_RATE_KEY = "sms_rate_v1";
    private static final String LANGUAGE_KEY = "language_v1";
    private static final String PRIVACY_ACCEPTED_KEY = "privacy_accepted";
    private static final String DISCLAIMER_ACCEPTED_KEY = "disclaimer_accepted";
    private static final String DARK_MODE_KEY = "dark_mode_v1";
    private static final String SENSITIVE_WORD_FILTER_KEY = "sensitive_word_filter_v1";
    private static final String COMPLETION_ALERT_KEY = "completion_alert_v1";

    /** 深色模式值常量：跟随系统 */
    public static final int DARK_MODE_SYSTEM = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    /** 深色模式值常量：始终浅色 */
    public static final int DARK_MODE_LIGHT = AppCompatDelegate.MODE_NIGHT_NO;
    /** 深色模式值常量：始终深色 */
    public static final int DARK_MODE_DARK = AppCompatDelegate.MODE_NIGHT_YES;


    // Default values
    static {
        DefaultPropMap.put(SEND_DELAY_KEY, Settings.SEND_DELAY_DEFAULT);
        DefaultPropMap.put(SEND_DELAY_RANDOMIZATION_KEY, Settings.SEND_DELAY_RANDOMIZATION_DEFAULT);
        DefaultPropMap.put(EDIT_AFTER_IMPORT_KEY, Settings.EDIT_AFTER_IMPORT_DEFAULT);
        DefaultPropMap.put(SMS_RATE_KEY, Settings.SMS_RATE_DEFAULT);
        DefaultPropMap.put(LANGUAGE_KEY, Settings.LANGUAGE_DEFAULT);
        DefaultPropMap.put(PRIVACY_ACCEPTED_KEY, Settings.PRIVACY_ACCEPTED_DEFAULT);
        DefaultPropMap.put(DISCLAIMER_ACCEPTED_KEY, Settings.DISCLAIMER_ACCEPTED_DEFAULT);
        DefaultPropMap.put(DARK_MODE_KEY, DARK_MODE_SYSTEM);
        DefaultPropMap.put(SENSITIVE_WORD_FILTER_KEY, true);
        DefaultPropMap.put(COMPLETION_ALERT_KEY, Settings.COMPLETION_ALERT_DEFAULT);
    }

    public static void init(Context context) {
        mSp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        mEditor = mSp.edit();
        Log.i(TAG, "Initializing SettingManager");
        for (String key : DefaultPropMap.keySet()) {
            //不包含key时使用默认值初始化
            if (!mSp.contains(key)) {
                Object obj = DefaultPropMap.get(key);
                Log.d(TAG, "Setting default value for: " + key + " -> " + obj);
                if (obj instanceof Integer) mEditor.putInt(key, (Integer) obj);
                else if (obj instanceof String) mEditor.putString(key, (String) obj);
                else if (obj instanceof Boolean) mEditor.putBoolean(key, (Boolean) obj);
            }
        }
        mEditor.apply();
    }


    public static boolean autoEnterEditor() {
        return mSp.getBoolean(EDIT_AFTER_IMPORT_KEY, Settings.EDIT_AFTER_IMPORT_DEFAULT);
    }

    public static void setAutoEnterEditor(boolean flag) {
        mEditor.putBoolean(EDIT_AFTER_IMPORT_KEY, flag);
        mEditor.apply();
    }

    public static int getDelay() {
        int saved = mSp.getInt(SEND_DELAY_KEY, Settings.SEND_DELAY_DEFAULT);
        // Clamp to valid range to prevent crash if old value is outside new range
        if (saved < Settings.SEND_DELAY_MIN) {
            saved = Settings.SEND_DELAY_MIN;
            setDelay(saved);
        } else if (saved > Settings.SEND_DELAY_MAX) {
            saved = Settings.SEND_DELAY_MAX;
            setDelay(saved);
        }
        return saved;
    }

    public static void setDelay(int num) {
        mEditor.putInt(SEND_DELAY_KEY, num);
        mEditor.apply();
    }

    public static float getSmsRate() {
        return mSp.getFloat(SMS_RATE_KEY, Settings.SMS_RATE_DEFAULT);
    }

    public static void setSmsRate(float rate) {
        mEditor.putFloat(SMS_RATE_KEY, rate).apply();
    }

    public static boolean isPrivacyAccepted() {
        return mSp.getBoolean(PRIVACY_ACCEPTED_KEY, Settings.PRIVACY_ACCEPTED_DEFAULT);
    }

    public static void setPrivacyAccepted(boolean flag) {
        mEditor.putBoolean(PRIVACY_ACCEPTED_KEY, flag).apply();
    }

    public static boolean isDisclaimerAccepted() {
        return mSp.getBoolean(DISCLAIMER_ACCEPTED_KEY, Settings.DISCLAIMER_ACCEPTED_DEFAULT);
    }

    public static void setDisclaimerAccepted(boolean flag) {
        mEditor.putBoolean(DISCLAIMER_ACCEPTED_KEY, flag).apply();
    }

    public static String getLanguage() {
        return mSp.getString(LANGUAGE_KEY, Settings.LANGUAGE_DEFAULT);
    }

    public static void setLanguage(String lang) {
        mEditor.putString(LANGUAGE_KEY, lang).apply();
    }

    public static boolean isRandomizeDelay() {
        return mSp.getBoolean(SEND_DELAY_RANDOMIZATION_KEY, Settings.SEND_DELAY_RANDOMIZATION_DEFAULT);
    }

    public static void setRandomizeDelay(boolean flag) {
        mEditor.putBoolean(SEND_DELAY_RANDOMIZATION_KEY, flag).apply();
    }

    public static int getDarkMode() {
        return mSp.getInt(DARK_MODE_KEY, DARK_MODE_SYSTEM);
    }

    public static void setDarkMode(int mode) {
        mEditor.putInt(DARK_MODE_KEY, mode).apply();
    }

    public static boolean isSensitiveWordFilterEnabled() {
        return mSp.getBoolean(SENSITIVE_WORD_FILTER_KEY, true);
    }

    public static void setSensitiveWordFilterEnabled(boolean enabled) {
        mEditor.putBoolean(SENSITIVE_WORD_FILTER_KEY, enabled).apply();
    }

    public static boolean isCompletionAlertEnabled() {
        return mSp.getBoolean(COMPLETION_ALERT_KEY, Settings.COMPLETION_ALERT_DEFAULT);
    }

    public static void setCompletionAlertEnabled(boolean enabled) {
        mEditor.putBoolean(COMPLETION_ALERT_KEY, enabled).apply();
    }

}

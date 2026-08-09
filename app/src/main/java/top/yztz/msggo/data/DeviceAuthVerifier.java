package top.yztz.msggo.data;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Verifies the current device ID against a whitelist stored in assets.
 * The whitelist file is app/src/main/assets/whitelist_android_ids.txt.
 */
public class DeviceAuthVerifier {
    private static final String TAG = "DeviceAuthVerifier";
    private static final String ASSET_FILE = "whitelist_android_ids.txt";

    private final Context context;
    private final Set<String> whitelist = new HashSet<>();

    public DeviceAuthVerifier(Context context) {
        this.context = context.getApplicationContext();
        loadWhitelist();
    }

    private void loadWhitelist() {
        whitelist.clear();
        try (InputStream is = context.getAssets().open(ASSET_FILE);
             BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                whitelist.add(normalizeId(line));
            }
        } catch (IOException e) {
            Log.w(TAG, "Could not load whitelist asset: " + ASSET_FILE + " — " + e.getMessage());
        }
    }

    private String normalizeId(String id) {
        if (id == null) return "";
        return id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public String getDeviceId() {
        try {
            String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            return normalizeId(androidId);
        } catch (Exception e) {
            Log.w(TAG, "Failed to read ANDROID_ID", e);
            return "";
        }
    }

    public boolean isAuthorized() {
        String deviceId = getDeviceId();
        return !deviceId.isEmpty() && whitelist.contains(deviceId);
    }

    public String getBlockReason() {
        String deviceId = getDeviceId();
        if (deviceId.isEmpty()) {
            return "ID_NOT_FOUND";
        }
        if (!whitelist.contains(deviceId)) {
            return "ID_NOT_WHITELISTED:" + deviceId;
        }
        return "OK";
    }
}
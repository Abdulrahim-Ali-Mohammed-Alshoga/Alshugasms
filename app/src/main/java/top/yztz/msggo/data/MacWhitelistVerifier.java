package top.yztz.msggo.data;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.NetworkInterface;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Utility to verify a MAC against a whitelist and check message date cutoff.
 * - Whitelist file: app/src/main/assets/whitelist_macs.txt (one MAC per line)
 * - Allowed dates: before 1 June 2027 (exclusive)
 */
public class MacWhitelistVerifier {
    private static final String TAG = "MacWhitelistVerifier";
    private static final String ASSET_FILE = "whitelist_macs.txt";

    private final Context context;
    private final Set<String> whitelist = new HashSet<>();
    private final Date cutoffDate;

    public MacWhitelistVerifier(Context context) {
        this.context = context.getApplicationContext();
        // cutoff = 2027-06-01 00:00:00
        Calendar cal = Calendar.getInstance();
        cal.set(2027, Calendar.JUNE, 1, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cutoffDate = cal.getTime();
        loadWhitelist();
    }

    private void loadWhitelist() {
        whitelist.clear();
        try (InputStream is = context.getAssets().open(ASSET_FILE);
             BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("#")) continue; // allow comments
                whitelist.add(normalizeMac(line));
            }
        } catch (IOException e) {
            Log.w(TAG, "Could not load whitelist asset: " + ASSET_FILE + " — " + e.getMessage());
        }
    }

    private String normalizeMac(String mac) {
        if (mac == null) return "";
        return mac.toUpperCase(Locale.ROOT).replaceAll("[^A-F0-9]", "");
    }

    public boolean isMacAllowed(String mac) {
        if (mac == null) return false;
        return whitelist.contains(normalizeMac(mac));
    }

    public boolean isDateAllowed(Date date) {
        if (date == null) return false;
        return date.before(cutoffDate);
    }

    private boolean isInvalidMac(String mac) {
        return mac == null || mac.isEmpty() || mac.equals("000000000000") || mac.equals("020000000000");
    }

    private String formatMac(byte[] address) {
        if (address == null || address.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : address) {
            sb.append(String.format(Locale.US, "%02X", b));
        }
        return sb.toString();
    }

    private String getMacFromInterface(NetworkInterface networkInterface) {
        if (networkInterface == null) return "";
        try {
            if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                return "";
            }
            String mac = formatMac(networkInterface.getHardwareAddress());
            return isInvalidMac(mac) ? "" : mac;
        } catch (Exception e) {
            Log.w(TAG, "Failed to read MAC from interface " + networkInterface.getName(), e);
            return "";
        }
    }

    public String getDeviceMac() {
        try {
            String[] preferredNames = {"wlan0", "eth0", "p2p0", "wlan1"};
            for (String name : preferredNames) {
                NetworkInterface ni = NetworkInterface.getByName(name);
                String mac = getMacFromInterface(ni);
                if (!mac.isEmpty()) {
                    return mac;
                }
            }

            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                String mac = getMacFromInterface(networkInterface);
                if (!mac.isEmpty()) {
                    return mac;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get device MAC", e);
        }
        return "";
    }

    public boolean isCurrentDeviceAllowed() {
        return isMacAllowed(getDeviceMac());
    }

    public boolean isCurrentDateAllowed() {
        return isDateAllowed(new Date());
    }

    public String getBlockReason() {
        String mac = getDeviceMac();
        if (mac.isEmpty()) {
            return "MAC_NOT_FOUND";
        }
        if (!isMacAllowed(mac)) {
            return "MAC_NOT_WHITELISTED:" + mac;
        }
        if (!isCurrentDateAllowed()) {
            return "DATE_NOT_ALLOWED";
        }
        return "OK";
    }

    /**
     * Try to "send" a message. This method performs the checks and returns a status string.
     * Replace the body where indicated with real sending logic.
     * @return "تم الإرسال" on success, or "خطأ في الإرسال" on failure
     */
    public String sendMessage(String mac, Date date, String message) {
        if (!isMacAllowed(mac)) return "خطأ في الإرسال";
        if (!isDateAllowed(date)) return "خطأ في الإرسال";

        // TODO: integrate real send logic here (SMS, network call, etc.)
        // For now we only simulate success.
        Log.i(TAG, "Sending message to MAC=" + mac + " date=" + date + " msg=" + message);
        return "تم الإرسال";
    }

    /**
     * Force reload the whitelist from assets (useful after editing the asset during development).
     */
    public void reload() {
        loadWhitelist();
    }
}

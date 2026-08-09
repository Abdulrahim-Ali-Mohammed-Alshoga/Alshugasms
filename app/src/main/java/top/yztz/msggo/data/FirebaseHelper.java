package top.yztz.msggo.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FirebaseHelper {
    private static final String TAG = "FirebaseHelper";
    private static final String USERS_NODE = "users";
    public static final int MAX_LIMIT_DEFAULT = 10000;

    // رابط الـ GitHub Gist لقراءة مفتاح التجاوز عند الحظر
    private static final String GITHUB_CONFIG_URL = "https://gist.githubusercontent.com/Abdulrahim-Ali-Mohammed-Alshoga/dba93f33ad3f681ec4b52742fa979766/raw/licensing.json";

    private final DatabaseReference database;
    private final String deviceId;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Context context;

    public interface AuthCallback {
        void onResult(boolean isAuthorized, boolean isPending);
        void onError(String message);
    }

    public interface QuotaCallback {
        void onQuotaChecked(boolean canSend, int remaining);
        void onError(String message);
    }

    public FirebaseHelper(Context context) {
        this.context = context.getApplicationContext();
        database = FirebaseDatabase.getInstance().getReference();
        deviceId = getDeviceId(context);
    }

    public String getCurrentDeviceId() {
        return deviceId;
    }

    // --- SharedPreferences Helpers ---

    private int getPendingSyncCount() {
        return context.getSharedPreferences("FirebaseHelperPrefs", Context.MODE_PRIVATE)
                .getInt("pending_sync_count", 0);
    }

    private void savePendingSyncCount(int count) {
        context.getSharedPreferences("FirebaseHelperPrefs", Context.MODE_PRIVATE)
                .edit()
                .putInt("pending_sync_count", count)
                .apply();
    }

    private void clearPendingSyncCount() {
        savePendingSyncCount(0);
    }

    private void saveAuthStatus(boolean authorized) {
        context.getSharedPreferences("FirebaseHelperPrefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("is_authorized", authorized)
                .apply();
    }

    private boolean getCachedAuthStatus() {
        return context.getSharedPreferences("FirebaseHelperPrefs", Context.MODE_PRIVATE)
                .getBoolean("is_authorized", false);
    }

    // --- Fetch Gist File ---

    private String fetchGitHubConfig() throws Exception {
        // إضافة طابع زمني فريد (Timestamp) لمنع الكاش الخاص بالشبكة أو خوادم GitHub من إرجاع نسخة قديمة
        java.net.HttpURLConnection conn = null;
        try {
            java.net.URL url = new java.net.URL(GITHUB_CONFIG_URL + "?t=" + System.currentTimeMillis());
            conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setUseCaches(false); // إلغاء استخدام الكاش المحلي
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream()))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    return sb.toString();
                }
            } else {
                throw new Exception("HTTP_ERR_" + responseCode);
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // --- Check Authentication (Firebase First -> Fallback to GitHub on Block) ---

    public void checkAuth(AuthCallback callback) {
        if (deviceId.isEmpty()) {
            callback.onError("ID_NOT_FOUND");
            return;
        }

        final boolean[] hasResponded = {false};

        Runnable fallbackRunnable = new Runnable() {
            @Override
            public void run() {
                synchronized (hasResponded) {
                    if (!hasResponded[0]) {
                        hasResponded[0] = true;
                        Log.i(TAG, "Firebase checkAuth timed out (5s). Checking GitHub bypass switch...");
                        checkAuthFallback(callback);
                    }
                }
            }
        };
        mainHandler.postDelayed(fallbackRunnable, 5000);

        DatabaseReference userRef = database.child(USERS_NODE).child(deviceId);
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                synchronized (hasResponded) {
                    if (hasResponded[0]) return;
                    hasResponded[0] = true;
                    mainHandler.removeCallbacks(fallbackRunnable);
                }
                
                if (snapshot.exists()) {
                    String status = snapshot.child("status").getValue(String.class);
                    if ("active".equals(status)) {
                        saveAuthStatus(true);
                        syncPendingCount(); // Sync any offline counts
                        callback.onResult(true, false);
                    } else {
                        saveAuthStatus(false);
                        callback.onResult(false, true);
                    }
                } else {
                    // Silent registration in Firebase
                    String currentMonth = new SimpleDateFormat("yyyy-MM", Locale.US).format(new Date());
                    java.util.HashMap<String, Object> userData = new java.util.HashMap<>();
                    userData.put("status", "pending");
                    userData.put("sent_count", 0);
                    userData.put("max_limit", MAX_LIMIT_DEFAULT);
                    userData.put("last_reset_month", currentMonth);
                    
                    userRef.setValue(userData);
                    saveAuthStatus(false);
                    callback.onResult(false, true);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                synchronized (hasResponded) {
                    if (hasResponded[0]) return;
                    hasResponded[0] = true;
                    mainHandler.removeCallbacks(fallbackRunnable);
                }
                Log.w(TAG, "Firebase checkAuth failed/cancelled: " + error.getMessage() + ". Checking GitHub...");
                checkAuthFallback(callback);
            }
        });
    }

    private void checkAuthFallback(AuthCallback callback) {
        new Thread(() -> {
            try {
                String json = fetchGitHubConfig();
                boolean licensingEnabled = !json.contains("\"licensing_enabled\": false") && !json.contains("\"licensing_enabled\":false");
                
                if (licensingEnabled) {
                    // الحالة الآخر (true): عند حظر الفايربيس، يتم الدخول الفوري للجميع (مفتوح طوالي)
                    saveAuthStatus(true);
                    mainHandler.post(() -> callback.onResult(true, false));
                } else {
                    // إذا كانت القيمة false وقاعدة الفايربيس محظورة: لا يفتح لأحد (يظل مغلقاً)
                    mainHandler.post(() -> callback.onError("CONNECTION_FAILED"));
                }
            } catch (Exception e) {
                // فشل الاتصال بالملفين، يظل التطبيق مغلقاً كحماية
                mainHandler.post(() -> callback.onError("CONNECTION_FAILED"));
            }
        }).start();
    }

    private void handleOfflineAuth(AuthCallback callback) {
        boolean cachedAuth = getCachedAuthStatus();
        if (cachedAuth) {
            mainHandler.post(() -> callback.onResult(true, false));
        } else {
            mainHandler.post(() -> callback.onError("CONNECTION_FAILED"));
        }
    }

    // --- Check Quota ---

    public void checkQuota(int requestedCount, QuotaCallback callback) {
        if (deviceId.isEmpty()) {
            callback.onError("ID_NOT_FOUND");
            return;
        }

        final boolean[] hasResponded = {false};

        Runnable fallbackRunnable = new Runnable() {
            @Override
            public void run() {
                synchronized (hasResponded) {
                    if (!hasResponded[0]) {
                        hasResponded[0] = true;
                        Log.i(TAG, "Firebase checkQuota timed out (5s). Checking GitHub...");
                        checkQuotaFallback(requestedCount, callback);
                    }
                }
            }
        };
        mainHandler.postDelayed(fallbackRunnable, 5000);

        DatabaseReference userRef = database.child(USERS_NODE).child(deviceId);
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                synchronized (hasResponded) {
                    if (hasResponded[0]) return;
                    hasResponded[0] = true;
                    mainHandler.removeCallbacks(fallbackRunnable);
                }

                if (snapshot.exists()) {
                    String currentMonth = new SimpleDateFormat("yyyy-MM", Locale.US).format(new Date());
                    String lastResetMonth = snapshot.child("last_reset_month").getValue(String.class);
                    
                    Long sentCountLong = snapshot.child("sent_count").getValue(Long.class);
                    Long maxLimitLong = snapshot.child("max_limit").getValue(Long.class);
                    
                    int sentCount = sentCountLong != null ? sentCountLong.intValue() : 0;
                    int maxLimit = maxLimitLong != null ? maxLimitLong.intValue() : MAX_LIMIT_DEFAULT;
                    
                    if (!currentMonth.equals(lastResetMonth)) {
                        sentCount = 0;
                        java.util.HashMap<String, Object> resetData = new java.util.HashMap<>();
                        resetData.put("sent_count", 0);
                        resetData.put("last_reset_month", currentMonth);
                        userRef.updateChildren(resetData);
                        clearPendingSyncCount();
                    }

                    int pending = getPendingSyncCount();
                    if (pending > 0) {
                        sentCount += pending;
                        userRef.child("sent_count").setValue(sentCount);
                        clearPendingSyncCount();
                    }

                    int remaining = maxLimit - sentCount;
                    if (requestedCount <= remaining) {
                        callback.onQuotaChecked(true, remaining);
                    } else {
                        callback.onQuotaChecked(false, remaining);
                    }
                } else {
                    callback.onError("USER_NOT_FOUND");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                synchronized (hasResponded) {
                    if (hasResponded[0]) return;
                    hasResponded[0] = true;
                    mainHandler.removeCallbacks(fallbackRunnable);
                }
                Log.w(TAG, "Firebase checkQuota cancelled/failed: " + error.getMessage() + ". Checking GitHub...");
                checkQuotaFallback(requestedCount, callback);
            }
        });
    }

    private void checkQuotaFallback(int requestedCount, QuotaCallback callback) {
        new Thread(() -> {
            try {
                String json = fetchGitHubConfig();
                boolean licensingEnabled = !json.contains("\"licensing_enabled\": false") && !json.contains("\"licensing_enabled\":false");
                
                if (licensingEnabled) {
                    // إذا كانت القيمة true وقاعدة البيانات محظورة، نسمح بالإرسال المفتوح (دخول طبيعي)
                    mainHandler.post(() -> callback.onQuotaChecked(true, 999999));
                } else {
                    // إذا كانت false، يتم حظر العملية
                    mainHandler.post(() -> callback.onError("CONNECTION_FAILED"));
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("CONNECTION_FAILED"));
            }
        }).start();
    }

    // --- Increment Count ---

    public void incrementSentCount(int count) {
        if (deviceId.isEmpty() || count <= 0) return;
        
        int currentPending = getPendingSyncCount();
        int newPending = currentPending + count;
        savePendingSyncCount(newPending);
        
        DatabaseReference sentCountRef = database.child(USERS_NODE).child(deviceId).child("sent_count");
        sentCountRef.setValue(com.google.firebase.database.ServerValue.increment(count))
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Log.i(TAG, "Successfully synced " + count + " sent messages to Firebase.");
                    int updatedPending = getPendingSyncCount() - count;
                    if (updatedPending < 0) updatedPending = 0;
                    savePendingSyncCount(updatedPending);
                } else {
                    Log.w(TAG, "Firebase increment failed (offline). Saved locally.");
                }
            });
    }

    private void syncPendingCount() {
        int pending = getPendingSyncCount();
        if (pending <= 0) return;
        
        DatabaseReference sentCountRef = database.child(USERS_NODE).child(deviceId).child("sent_count");
        sentCountRef.setValue(com.google.firebase.database.ServerValue.increment(pending))
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Log.i(TAG, "Successfully synced pending " + pending + " messages to Firebase.");
                    int updatedPending = getPendingSyncCount() - pending;
                    if (updatedPending < 0) updatedPending = 0;
                    savePendingSyncCount(updatedPending);
                }
            });
    }

    private String getDeviceId(Context context) {
        try {
            String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            return normalizeId(androidId);
        } catch (Exception e) {
            Log.w(TAG, "Failed to read ANDROID_ID", e);
            return "";
        }
    }

    private String normalizeId(String id) {
        if (id == null) return "";
        return id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}

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
package top.yztz.msggo.activities;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import top.yztz.msggo.R;
import top.yztz.msggo.data.DataModel;
import top.yztz.msggo.data.Message;
import top.yztz.msggo.data.SettingManager;
import top.yztz.msggo.services.MessageService;
import top.yztz.msggo.util.FileUtil;
import top.yztz.msggo.util.ToastUtil;
import top.yztz.msggo.data.FirebaseHelper;

public class SendingActivity extends AppCompatActivity implements MessageService.Callback {
    private static final String TAG = "SendingActivity";
    private static final int MAX_RETRIES = 3;
    private static final long CONFIRMATION_TIMEOUT_MS = 30000; // 30 seconds

    // UI
    private RecyclerView rvList;
    private SendingListAdapter adapter;
    private MaterialToolbar topAppBar;
    private TextView tvSubmittedCount, tvConfirmedCount;
    private LinearProgressIndicator progressSubmitted, progressConfirmed;

    // Data
    private List<Message> messages;
    private int subId;
    private int delay;
    private boolean randomize;

    // Service
    private MessageService service = null;
    private boolean isBound = false;

    // Sending state
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int currentIndex = 0;
    private int confirmedCount = 0;
    private boolean isPaused = false;
    private boolean isStopped = false;

    // Retry tracking: maps message index -> retry count
    private final Map<Integer, Integer> retryCountMap = new HashMap<>();
    // Timeout tracking: maps message index -> timeout runnable
    private final Map<Integer, Runnable> timeoutRunnableMap = new HashMap<>();
    // قائمة الرسائل الفاشلة المنتظرة لإعادة الإرسال في نهاية القائمة
    private final java.util.LinkedList<Integer> retryQueue = new java.util.LinkedList<>();
    private boolean isProcessingRetryQueue = false;

    public enum SendingState {
        IDLE, SENDING, PAUSED, COMPLETED, CANCELLED
    }

    public enum MessageState {
        PENDING, WAITING, SUBMITTED, SENT, FAILED, PAUSED
    }

    private SendingState currentState = SendingState.IDLE;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            MessageService.LocalBinder binder = (MessageService.LocalBinder) iBinder;
            service = binder.getService();
            isBound = true;
            service.setCallback(SendingActivity.this);

            Log.d(TAG, "Service connected. Starting sending session.");
            service.initSession(messages.size());
            
            FirebaseHelper firebaseHelper = new FirebaseHelper(SendingActivity.this);
            firebaseHelper.checkQuota(messages.size(), new FirebaseHelper.QuotaCallback() {
                @Override
                public void onQuotaChecked(boolean canSend, int remaining) {
                    if (isFinishing() || isDestroyed()) return;
                    if (canSend) {
                        startSending();
                    } else {
                        ToastUtil.show(SendingActivity.this, "\u0639\u0641\u0648\u0627\u064b\u060c \u0644\u0642\u062f \u062a\u062c\u0627\u0648\u0632\u062a \u0627\u0644\u062d\u062f \u0627\u0644\u0645\u0633\u0645\u0648\u062d \u0644\u0644\u0631\u0633\u0627\u0626\u0644. ");
                        cleanupAndFinish();
                    }
                }

                @Override
                public void onError(String message) {
                    if (isFinishing() || isDestroyed()) return;
                    ToastUtil.show(SendingActivity.this, "\u062e\u0637\u0623 \u0641\u064a \u0627\u0644\u0627\u062a\u0635\u0627\u0644 \u0628\u0627\u0644\u062e\u0627\u062f\u0645: " + message);
                    cleanupAndFinish();
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            if (service != null) {
                service.removeCallback();
                service = null;
            }
            isBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sending);

        // Load messages from serialized file
        Intent intent = getIntent();
        String serPath = intent.getStringExtra("to_send");
        if (TextUtils.isEmpty(serPath)) {
            Log.e(TAG, "No ser path found");
            finish();
            return;
        }

        Message[] msgArray = FileUtil.readMessageArrayFromFile(this, serPath);
        if (msgArray == null || msgArray.length == 0) {
            Log.e(TAG, "No messages to send");
            finish();
            return;
        }
        messages = new ArrayList<>(Arrays.asList(msgArray));

        // Load settings
        subId = DataModel.getSubId();
        delay = SettingManager.getDelay();
        randomize = SettingManager.isRandomizeDelay();

        initViews();
        setupList();

        // Handle Back Press
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentState == SendingState.SENDING || currentState == SendingState.PAUSED) {
                    showStopConfirmationDialog();
                } else {
                    navigateToHome();
                }
            }
        });

        // Bind to service
        intent = new Intent(this, MessageService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    private void initViews() {
        topAppBar = findViewById(R.id.topAppBar);
        rvList = findViewById(R.id.rv_sending_list);
        tvSubmittedCount = findViewById(R.id.tv_sent_count);
        tvConfirmedCount = findViewById(R.id.tv_confirmed_count);
        progressSubmitted = findViewById(R.id.progress_sent);
        progressConfirmed = findViewById(R.id.progress_confirmed);

        topAppBar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        topAppBar.inflateMenu(R.menu.menu_sending);

        topAppBar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_pause_resume) {
                togglePauseResume();
                return true;
            }
            return false;
        });
    }

    private void setupList() {
        rvList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SendingListAdapter(this);
        rvList.setAdapter(adapter);
        adapter.setMessages(messages);
    }

    // --- Sending Logic ---

    private void startSending() {
        currentState = SendingState.SENDING;
        updateUI();
        sendNextMessage();
    }

    private void sendNextMessage() {
        if (isStopped) return;
        if (isPaused) return;
        if (currentIndex >= messages.size()) {
            // كل الرسائل الأصلية اكتملت، تحقق من قائمة إعادة المحاولة
            processRetryQueue();
            return;
        }

        updateMessageState(currentIndex, MessageState.WAITING);
        scheduleWithDelay(this::executeCurrentSend);
    }

    private void scheduleWithDelay(Runnable action) {
        int targetDelay = delay;
        if (randomize) {
            // عشوائي ±2 ثوانٍ حول القيمة المختارة
            int variation = 2000;
            int minDelay = Math.max(6000, delay - variation);
            int maxDelay = delay + variation;
            targetDelay = (int) (minDelay + Math.random() * (maxDelay - minDelay));
        }

        Log.d(TAG, "Scheduling with delay " + targetDelay + "ms");
        handler.postDelayed(action, targetDelay);
    }

    /**
     * معالجة قائمة الرسائل الفاشلة — ترسلها واحدة واحدة مع نفس التأخير
     */
    private void processRetryQueue() {
        if (isStopped || isPaused) return;
        if (retryQueue.isEmpty()) {
            // لا توجد رسائل فاشلة، تحقق من الاكتمال
            checkCompletion();
            return;
        }

        isProcessingRetryQueue = true;
        int index = retryQueue.poll();
        int retries = retryCountMap.getOrDefault(index, 0);

        if (retries >= MAX_RETRIES) {
            // استنفذ كل المحاولات — فشل نهائي
            Log.w(TAG, "Message " + index + " failed after " + MAX_RETRIES + " retries. Marking as failed.");
            updateMessageState(index, MessageState.FAILED);
            confirmedCount++;
            updateProgress(confirmedCount, messages.size(), tvConfirmedCount, progressConfirmed);
            // تابع لبقية قائمة الإعادة
            processRetryQueue();
            return;
        }

        retryCountMap.put(index, retries + 1);
        Log.i(TAG, "Retry queue: sending message " + index + " (attempt " + (retries + 1) + "/" + MAX_RETRIES + ")");

        updateMessageState(index, MessageState.WAITING);
        scheduleWithDelay(() -> executeRetrySend(index));
    }

    private void executeCurrentSend() {
        if (isStopped || isPaused) return;
        if (currentIndex >= messages.size()) return;

        Message msg = messages.get(currentIndex);
        service.sendOne(msg, currentIndex, subId);
    }

    private void togglePauseResume() {
        if (currentState == SendingState.SENDING) {
            pauseSending();
        } else if (currentState == SendingState.PAUSED) {
            resumeSending();
        }
    }

    private void pauseSending() {
        isPaused = true;
        handler.removeCallbacksAndMessages(null);
        currentState = SendingState.PAUSED;
        if (currentIndex < messages.size()) {
            updateMessageState(currentIndex, MessageState.PAUSED);
        }
        if (isBound) service.notifyPaused();
        updateUI();
        Log.d(TAG, "Paused at index " + currentIndex);
    }

    private void resumeSending() {
        isPaused = false;
        currentState = SendingState.SENDING;
        if (isBound) service.notifyResumed();
        updateUI();
        sendNextMessage();
        Log.d(TAG, "Resumed at index " + currentIndex);
    }

    private void stopSending() {
        isStopped = true;
        handler.removeCallbacksAndMessages(null);
        // Cancel all pending timeout runnables
        for (Runnable r : timeoutRunnableMap.values()) {
            handler.removeCallbacks(r);
        }
        timeoutRunnableMap.clear();
        currentState = SendingState.CANCELLED;
        if (isBound) {
            if (service != null) {
                service.removeCallback();
                service.finishSession(false);
            }
            unbindService(connection);
            isBound = false;
        }
        updateUI();
    }

    /**
     * \u062a\u0646\u0638\u064a\u0641 \u0627\u0644\u062e\u062f\u0645\u0629 \u0648\u0625\u063a\u0644\u0627\u0642 \u0627\u0644\u0634\u0627\u0634\u0629 \u0628\u0634\u0643\u0644 \u0622\u0645\u0646
     */
    private void cleanupAndFinish() {
        if (isBound) {
            if (service != null) {
                service.removeCallback();
                service.finishSession(false);
            }
            unbindService(connection);
            isBound = false;
        }
        finish();
    }

    private void checkCompletion() {
        if (currentIndex >= messages.size() && retryQueue.isEmpty() && confirmedCount >= messages.size()) {
            currentState = SendingState.COMPLETED;
            isProcessingRetryQueue = false;
            // Cancel all pending timeout runnables
            for (Runnable r : timeoutRunnableMap.values()) {
                handler.removeCallbacks(r);
            }
            timeoutRunnableMap.clear();
            if (isBound) {
                service.finishSession(true);
            }
            updateUI();
            Log.i(TAG, "All messages sent and confirmed!");
        }
    }

    // --- Retry Logic ---

    /**
     * إضافة الرسالة الفاشلة لنهاية قائمة الإعادة بدل إعادة المحاولة فوراً
     */
    private void enqueueForRetry(int index, String reason) {
        if (isStopped) return;
        if (index < 0 || index >= messages.size()) return;

        Message msg = messages.get(index);
        msg.setFailReason(reason);

        int retries = retryCountMap.getOrDefault(index, 0);
        if (retries >= MAX_RETRIES) {
            // استنفذ كل المحاولات — فشل نهائي
            Log.w(TAG, "Message " + index + " failed after " + MAX_RETRIES + " retries. Marking as failed.");
            updateMessageState(index, MessageState.FAILED);
            confirmedCount++;
            updateProgress(confirmedCount, messages.size(), tvConfirmedCount, progressConfirmed);
            checkCompletion();
            return;
        }

        Log.i(TAG, "Message " + index + " queued for retry (attempt " + (retries + 1) + "/" + MAX_RETRIES + ") reason: " + reason);
        retryQueue.add(index);

        // إذا انتهت الرسائل الأصلية، ابدأ معالجة قائمة الإعادة
        if (currentIndex >= messages.size() && !isProcessingRetryQueue) {
            processRetryQueue();
        }
    }

    private void executeRetrySend(int index) {
        if (isStopped || isPaused) return;
        if (!isBound || service == null) return;
        if (index < 0 || index >= messages.size()) return;

        Message msg = messages.get(index);
        updateMessageState(index, MessageState.SUBMITTED);
        service.retrySend(msg, index, subId);
        startConfirmationTimeout(index);
    }

    private void startConfirmationTimeout(int index) {
        // Cancel any existing timeout for this index
        Runnable existingTimeout = timeoutRunnableMap.remove(index);
        if (existingTimeout != null) {
            handler.removeCallbacks(existingTimeout);
        }

        Runnable timeoutRunnable = () -> {
            timeoutRunnableMap.remove(index);
            Log.w(TAG, "Confirmation timeout for message " + index + ". Adding to retry queue...");
            enqueueForRetry(index, "انتهى وقت الانتظار (بدون رد)");
        };

        timeoutRunnableMap.put(index, timeoutRunnable);
        handler.postDelayed(timeoutRunnable, CONFIRMATION_TIMEOUT_MS);
    }

    // --- Callbacks from MessageService ---

    @Override
    public void onMessageSubmitted(int index) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            updateMessageState(index, MessageState.SUBMITTED);
            updateProgress(index + 1, messages.size(), tvSubmittedCount, progressSubmitted);
            
            // Start confirmation timeout for this message
            startConfirmationTimeout(index);
            
            currentIndex++;
            sendNextMessage();
        });
    }

    @Override
    public void onMessageConfirmed(int index, boolean success) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            // Cancel the timeout for this message since we got a confirmation
            Runnable timeout = timeoutRunnableMap.remove(index);
            if (timeout != null) {
                handler.removeCallbacks(timeout);
            }

            if (success) {
                updateMessageState(index, MessageState.SENT);
                confirmedCount++;
                updateProgress(confirmedCount, messages.size(), tvConfirmedCount, progressConfirmed);
                // Increment Firebase sent count only for successfully delivered messages
                new FirebaseHelper(SendingActivity.this).incrementSentCount(1);
                // إذا كنا نعالج قائمة الإعادة، تابع للرسالة التالية
                if (isProcessingRetryQueue) {
                    processRetryQueue();
                } else {
                    checkCompletion();
                }
            } else {
                // الرسالة فشلت — أضفها لنهاية قائمة الإعادة
                Log.w(TAG, "Message " + index + " send failed. Adding to retry queue.");
                enqueueForRetry(index, "تم رفض الرسالة من الشبكة");
            }
        });
    }

    @Override
    public void onMessageFailed(int index, String reason) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            // Cancel the timeout for this message
            Runnable timeout = timeoutRunnableMap.remove(index);
            if (timeout != null) {
                handler.removeCallbacks(timeout);
            }

            // الرسالة فشلت — أضفها لنهاية قائمة الإعادة
            Log.w(TAG, "Message " + index + " failed with reason: " + reason + ". Adding to retry queue.");
            enqueueForRetry(index, reason != null ? reason : "فشل غير معروف");
        });
    }

    // --- UI Updates ---

    private void updateMessageState(int index, MessageState state) {
        messages.get(index).setState(state);
        adapter.notifyItemChanged(index);
    }

    private void updateProgress(int current, int total, TextView tvText, LinearProgressIndicator progress) {
        progress.setMax(total);
        progress.setIndeterminate(false);
        progress.setProgress(current);
        tvText.setText(String.format(Locale.getDefault(), "%d/%d", current, total));
    }

    private void updateUI() {
        switch (currentState) {
            case SENDING:
                topAppBar.setTitle(R.string.sending);
                updateMenuIcon(true);
                break;
            case PAUSED:
                topAppBar.setTitle(R.string.paused);
                updateMenuIcon(false);
                break;
            case COMPLETED:
                topAppBar.setTitle(R.string.done);
                topAppBar.getMenu().clear();
                break;
            case CANCELLED:
                topAppBar.setTitle(R.string.cancelled);
                topAppBar.getMenu().clear();
                break;
        }
    }

    private void updateMenuIcon(boolean isSending) {
        MenuItem item = topAppBar.getMenu().findItem(R.id.action_pause_resume);
        if (item != null) {
            item.setIcon(isSending ? R.drawable.ic_pause : R.drawable.ic_play);
        }
    }

    private void showStopConfirmationDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.cancel_send))
                .setPositiveButton(getString(R.string.ok), (dialog, which) -> {
                    stopSending();
                    navigateToHome();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void navigateToHome() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "Paused!");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "Stopped!");
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, "Restart!");
        adapter.setMessages(messages);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        // Cancel all pending timeout runnables
        for (Runnable r : timeoutRunnableMap.values()) {
            handler.removeCallbacks(r);
        }
        timeoutRunnableMap.clear();
        if (isBound) {
            stopSending();
        }
        Log.d(TAG, "Activity destroyed");
    }
}

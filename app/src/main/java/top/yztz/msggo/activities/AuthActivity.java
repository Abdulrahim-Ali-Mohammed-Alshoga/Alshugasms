package top.yztz.msggo.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import top.yztz.msggo.R;
import top.yztz.msggo.data.FirebaseHelper;
import top.yztz.msggo.util.ToastUtil;

public class AuthActivity extends AppCompatActivity {
    private FirebaseHelper firebaseHelper;
    private TextView tvMessage;
    private TextView tvTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        tvTitle = findViewById(R.id.tv_auth_title);
        tvMessage = findViewById(R.id.tv_auth_message);

        firebaseHelper = new FirebaseHelper(this);
        String deviceCode = firebaseHelper.getCurrentDeviceId();

        if (deviceCode.isEmpty()) {
            tvTitle.setText("خطأ");
            tvMessage.setText(R.string.error_mac_not_found);
        } else {
            tvTitle.setText("جاري التحقق...");
            tvMessage.setText("التطبيق قيد التشغيل Loading");
            
            checkAuth();
        }
    }

    private void checkAuth() {
        firebaseHelper.checkAuth(new FirebaseHelper.AuthCallback() {
            @Override
            public void onResult(boolean isAuthorized, boolean isPending) {
                if (isFinishing() || isDestroyed()) return;
                if (isAuthorized) {
                    // Start MainActivity
                    startActivity(new Intent(AuthActivity.this, MainActivity.class));
                    finish();
                } else if (isPending) {
                    tvTitle.setText("التطبيق قيد التشغيل");
                    tvMessage.setText("الرجاء الانتظار ...");
                    tvMessage.setText("Loading");
                }
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                tvTitle.setText("خطأ في الاتصال");
                tvMessage.setText("تعذر الاتصال بالخادم: " + message);
            }
        });
    }
}
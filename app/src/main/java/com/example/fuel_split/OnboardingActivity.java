package com.example.fuel_split;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

import org.web3j.crypto.Credentials;

public class OnboardingActivity extends AppCompatActivity {

    private EditText etUsername, etReferral;
    private MaterialButton btnGetStarted;
    private ProgressBar progressBar;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        // Edge-to-edge: keep content clear of the status bar and gesture nav bar.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        View root = findViewById(R.id.rootOnboarding);
        final int basePad = root.getPaddingLeft();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(basePad + bars.left, basePad + bars.top,
                         basePad + bars.right, basePad + bars.bottom);
            return insets;
        });

        etUsername    = findViewById(R.id.etUsername);
        etReferral    = findViewById(R.id.etReferral);
        btnGetStarted = findViewById(R.id.btnGetStarted);
        progressBar   = findViewById(R.id.progressBar);
        tvStatus      = findViewById(R.id.tvStatus);

        btnGetStarted.setOnClickListener(v -> attemptRegister());
    }

    private void attemptRegister() {
        String username = etUsername.getText().toString().trim();
        String referral = etReferral.getText().toString().trim();

        if (username.isEmpty()) {
            etUsername.setError("Enter your name");
            return;
        }

        setLoading(true, "Creating your wallet...");

        new Thread(() -> {
            try {
                WalletManager wm       = new WalletManager(this);
                Credentials creds      = wm.getOrCreateWallet();
                android.util.Log.d("FUELSPLIT", "App wallet: " + creds.getAddress());
                BlockchainManager bm   = new BlockchainManager();
                ContractManager cm     = new ContractManager(bm.getWeb3(), creds);

                runOnUiThread(() -> setLoading(true, "Funding your wallet..."));
                FaucetClient.fundWallet(creds.getAddress());

                runOnUiThread(() -> setLoading(true, "Creating your profile..."));
                String code = ProfileClient.createProfile(creds.getAddress(), username);

                runOnUiThread(() -> setLoading(true, "Registering on blockchain..."));

                boolean already = cm.isRegistered(creds.getAddress());
                if (!already) {
                    String hash = cm.register(code, "");
                    cm.waitForReceipt(hash);
                }

                // Keep gate key so MainActivity doesn't redirect to onboarding
                getSharedPreferences("fuelsplit", MODE_PRIVATE)
                        .edit().putString("username", username).apply();
                // Save friend-code identity
                getSharedPreferences("FuelSplitProfile", MODE_PRIVATE)
                        .edit()
                        .putString("displayName", username)
                        .putString("code", code)
                        .apply();

                runOnUiThread(() -> {
                    setLoading(false, "");
                    goToMain();
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    setLoading(false, "Error: " + e.getMessage());
                });
            }
        }).start();
    }

    private void setLoading(boolean loading, String status) {
        btnGetStarted.setEnabled(!loading);
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        tvStatus.setText(status);
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
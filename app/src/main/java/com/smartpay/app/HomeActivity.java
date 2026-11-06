package com.smartpay.app;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.smartpay.app.databinding.ActivityHomeBinding;

import java.util.HashMap;
import java.util.Map;

/**
 * HomeActivity - Main dashboard of SmartPay
 *
 * Implements:
 * - Title "HOME" with top-right Notification Bell and unread badge dot (from notifications/{uid} where read == false)
 * - "WALLET BALANCE" Card with privacy protection (hidden dashes "- - - - -" by default)
 * - 4-digit PIN setup dialog if user has no PIN yet (salted SHA-256 via PinHelper)
 * - 4-digit PIN verification dialog to reveal live wallet balance
 * - 30-second auto-hide timer for wallet balance and immediate hide on pause/stop
 * - 3-attempt lockout (30-second block) on wrong PIN attempts
 * - ADD MONEY button (opens AddMoneyActivity)
 * - Menu rows: SEND MONEY, RECEIVE MONEY, SCAN QR, TRANSACTIONS, PROFILE, LOGOUT
 * - Logout confirmation BottomSheet (Slide 7) with red LOGOUT and outlined CANCEL
 * - Full Firebase listener lifecycle cleanup in onStop/onDestroy
 */
public class HomeActivity extends BaseActivity {

    private static final long BALANCE_AUTO_HIDE_MS = 30000; // 30 seconds
    private static final long PIN_LOCKOUT_MS = 30000;       // 30 seconds lockout
    private static final int MAX_PIN_ATTEMPTS = 3;

    private ActivityHomeBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;

    // Realtime Database references & listeners
    private DatabaseReference userRef;
    private ValueEventListener userListener;
    private DatabaseReference pinRef;
    private ValueEventListener pinListener;
    private Query notificationsQuery;
    private ValueEventListener notificationsListener;

    // Cached user state
    private double currentBalance = 0.0;
    private String currentPinHash = "";
    private String currentPinSalt = "";

    // PIN security tracking
    private int wrongPinAttempts = 0;
    private long lockoutUntilTimestamp = 0;

    // Auto-hide balance handler
    private final Handler balanceHideHandler = new Handler(Looper.getMainLooper());
    private final Runnable balanceHideRunnable = this::hideBalance;

    // Runtime notification permission launcher (Android 13+)
    private final ActivityResultLauncher<String> notifPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                // No hard requirement — in-app bell still works without it
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        // Ensure user is logged in
        if (currentUser == null) {
            redirectToLogin();
            return;
        }

        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupListeners();
        setupFirebaseListeners(currentUser.getUid());
        requestNotificationPermissionIfNeeded();
    }

    /**
     * On Android 13+ (API 33) POST_NOTIFICATIONS is a runtime permission.
     * Ask once; the user can always change it in system settings later.
     */
    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    private void setupListeners() {
        // Notification bell button
        binding.btnNotifications.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, NotificationsActivity.class);
            startActivity(intent);
        });

        // Wallet balance card and show button clicks
        View.OnClickListener balanceClickListener = v -> handleBalanceCardClick();
        binding.cardWalletBalance.setOnClickListener(balanceClickListener);

        // ADD MONEY button
        binding.btnAddMoney.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, AddMoneyActivity.class);
            startActivity(intent);
        });

        // Menu Rows navigation
        binding.rowSendMoney.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, SendMoneyActivity.class);
            startActivity(intent);
        });

        binding.rowReceiveMoney.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, ReceiveMoneyActivity.class);
            startActivity(intent);
        });

        binding.rowScanQr.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, QrScannerActivity.class);
            startActivity(intent);
        });

        binding.rowTransactions.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, TransactionsActivity.class);
            startActivity(intent);
        });

        binding.rowProfile.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, ProfileActivity.class);
            startActivity(intent);
        });

        // LOGOUT opens Bottom Sheet
        binding.rowLogout.setOnClickListener(v -> showLogoutBottomSheet());
    }

    /**
     * Listens to live user balance/PIN and unread notifications from Firebase.
     */
    private void setupFirebaseListeners(String uid) {
        userRef = FirebaseRefs.getUserRef(uid);
        userListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    return;
                }
                UserModel user = snapshot.getValue(UserModel.class);
                if (user != null) {
                    currentBalance = user.getBalance();
                    // Update formatted balance string
                    binding.tvBalance.setText(CurrencyHelper.formatRupees(currentBalance));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Silently handle cancelled event
            }
        };
        userRef.addValueEventListener(userListener);

        // Listen for user's private PIN in userPins/{uid}
        pinRef = FirebaseRefs.getUserPinRef(uid);
        pinListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String hash = snapshot.child("pinHash").getValue(String.class);
                    String salt = snapshot.child("pinSalt").getValue(String.class);
                    currentPinHash = hash != null ? hash : "";
                    currentPinSalt = salt != null ? salt : "";
                } else {
                    currentPinHash = "";
                    currentPinSalt = "";
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Silently handle cancelled event
            }
        };
        pinRef.addValueEventListener(pinListener);

        // Listen for unread notifications (read == false)
        notificationsQuery = FirebaseRefs.getNotificationsRef(uid)
                .orderByChild("read")
                .equalTo(false);

        notificationsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && snapshot.getChildrenCount() > 0) {
                    binding.viewUnreadDot.setVisibility(View.VISIBLE);
                } else {
                    binding.viewUnreadDot.setVisibility(View.GONE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Ignore cancel
            }
        };
        notificationsQuery.addValueEventListener(notificationsListener);
    }

    /**
     * Handles tapping on the balance card or check button.
     */
    private void handleBalanceCardClick() {
        // If balance is already visible, tap to hide it immediately
        if (binding.layoutBalanceVisible.getVisibility() == View.VISIBLE) {
            hideBalance();
            return;
        }

        // Check if user has a PIN set up
        if (TextUtils.isEmpty(currentPinHash) || TextUtils.isEmpty(currentPinSalt)) {
            showCreatePinDialog();
            return;
        }

        // Check lockout status
        long now = System.currentTimeMillis();
        if (now < lockoutUntilTimestamp) {
            long secondsLeft = (lockoutUntilTimestamp - now) / 1000 + 1;
            Toast.makeText(this,
                    "PIN entry is locked due to multiple incorrect attempts. Please wait " + secondsLeft + "s.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        showVerifyPinDialog();
    }

    /**
     * Dialog to create a 4-digit PIN for new users.
     */
    private void showCreatePinDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_pin, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        EditText etNewPin = dialogView.findViewById(R.id.etNewPin);
        EditText etConfirmNewPin = dialogView.findViewById(R.id.etConfirmNewPin);
        TextView tvError = dialogView.findViewById(R.id.tvCreatePinError);
        Button btnSavePin = dialogView.findViewById(R.id.btnSavePin);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelCreatePin);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSavePin.setOnClickListener(v -> {
            String newPin = etNewPin.getText().toString().trim();
            String confirmPin = etConfirmNewPin.getText().toString().trim();

            if (TextUtils.isEmpty(newPin) || newPin.length() != 4 || !newPin.matches("\\d{4}")) {
                tvError.setText("PIN must be exactly 4 digits.");
                tvError.setVisibility(View.VISIBLE);
                return;
            }

            if (!newPin.equals(confirmPin)) {
                tvError.setText("PINs do not match. Please re-enter.");
                tvError.setVisibility(View.VISIBLE);
                return;
            }

            // Save PIN to userPins/{uid} via PinHelper and clean up legacy fields
            PinHelper.savePin(currentUser.getUid(), newPin, new PinHelper.PinSaveCallback() {
                @Override
                public void onSuccess(String pinHash, String pinSalt) {
                    currentPinHash = pinHash;
                    currentPinSalt = pinSalt;
                    dialog.dismiss();
                    Toast.makeText(HomeActivity.this, "PIN created successfully!", Toast.LENGTH_SHORT).show();
                    showBalance();
                }

                @Override
                public void onFailure(String errorMessage) {
                    tvError.setText("Failed to save PIN: " + errorMessage);
                    tvError.setVisibility(View.VISIBLE);
                }
            });
        });

        dialog.show();
    }

    /**
     * Dialog to enter the 4-digit PIN to display wallet balance.
     */
    private void showVerifyPinDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_verify_pin, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        EditText etVerifyPin = dialogView.findViewById(R.id.etVerifyPin);
        TextView tvError = dialogView.findViewById(R.id.tvVerifyPinError);
        Button btnSubmit = dialogView.findViewById(R.id.btnSubmitPin);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelVerifyPin);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSubmit.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            if (now < lockoutUntilTimestamp) {
                long secondsLeft = (lockoutUntilTimestamp - now) / 1000 + 1;
                tvError.setText("PIN entry locked. Please wait " + secondsLeft + "s.");
                tvError.setVisibility(View.VISIBLE);
                return;
            }

            String enteredPin = etVerifyPin.getText().toString().trim();
            if (TextUtils.isEmpty(enteredPin) || enteredPin.length() != 4 || !enteredPin.matches("\\d{4}")) {
                tvError.setText("Please enter a 4-digit PIN.");
                tvError.setVisibility(View.VISIBLE);
                return;
            }

            // Verify with salted SHA-256
            boolean isValid = PinHelper.verifyPin(enteredPin, currentPinSalt, currentPinHash);
            if (isValid) {
                wrongPinAttempts = 0;
                dialog.dismiss();
                showBalance();
            } else {
                wrongPinAttempts++;
                if (wrongPinAttempts >= MAX_PIN_ATTEMPTS) {
                    lockoutUntilTimestamp = System.currentTimeMillis() + PIN_LOCKOUT_MS;
                    dialog.dismiss();
                    Toast.makeText(HomeActivity.this,
                            "Too many incorrect attempts! PIN entry is locked for 30 seconds.",
                            Toast.LENGTH_LONG).show();
                } else {
                    int attemptsRemaining = MAX_PIN_ATTEMPTS - wrongPinAttempts;
                    etVerifyPin.setText("");
                    tvError.setText("Incorrect PIN. " + attemptsRemaining + " attempt(s) remaining.");
                    tvError.setVisibility(View.VISIBLE);
                }
            }
        });

        dialog.show();
    }

    /**
     * Shows the live wallet balance and starts the 30-second auto-hide timer.
     */
    private void showBalance() {
        binding.layoutBalanceHidden.setVisibility(View.GONE);
        binding.layoutBalanceVisible.setVisibility(View.VISIBLE);

        // Schedule auto-hide after 30 seconds
        balanceHideHandler.removeCallbacks(balanceHideRunnable);
        balanceHideHandler.postDelayed(balanceHideRunnable, BALANCE_AUTO_HIDE_MS);
    }

    /**
     * Hides the wallet balance and shows the privacy dashes.
     */
    private void hideBalance() {
        if (binding != null) {
            binding.layoutBalanceVisible.setVisibility(View.GONE);
            binding.layoutBalanceHidden.setVisibility(View.VISIBLE);
        }
        balanceHideHandler.removeCallbacks(balanceHideRunnable);
    }

    /**
     * Shows the Logout confirmation bottom sheet.
     */
    private void showLogoutBottomSheet() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_logout, null);
        bottomSheetDialog.setContentView(sheetView);

        Button btnConfirmLogout = sheetView.findViewById(R.id.btnConfirmLogout);
        Button btnCancelLogout = sheetView.findViewById(R.id.btnCancelLogout);

        btnCancelLogout.setOnClickListener(v -> bottomSheetDialog.dismiss());

        btnConfirmLogout.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            mAuth.signOut();
            Toast.makeText(HomeActivity.this, "Logged out successfully", Toast.LENGTH_SHORT).show();
            redirectToLogin();
        });

        bottomSheetDialog.show();
    }

    private void redirectToLogin() {
        Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Immediately hide balance when screen is paused
        hideBalance();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Immediately hide balance when stopped
        hideBalance();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        hideBalance();

        // Clean up all Firebase Realtime Database listeners
        if (userRef != null && userListener != null) {
            userRef.removeEventListener(userListener);
            userListener = null;
        }

        if (pinRef != null && pinListener != null) {
            pinRef.removeEventListener(pinListener);
            pinListener = null;
        }

        if (notificationsQuery != null && notificationsListener != null) {
            notificationsQuery.removeEventListener(notificationsListener);
            notificationsListener = null;
        }
    }
}

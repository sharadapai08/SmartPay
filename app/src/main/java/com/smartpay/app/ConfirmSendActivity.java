package com.smartpay.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.smartpay.app.databinding.ActivityConfirmSendBinding;

import java.util.HashMap;
import java.util.Map;

/**
 * ConfirmSendActivity - Confirmation and PIN verification screen.
 *
 * Implements:
 * - Recipient details, formatted amount, note, and yellow confirmation box.
 * - PIN verification with PinHelper (create PIN prompt if missing).
 * - 3-attempt limit with 30s lockout.
 * - Button disabling during transfer execution.
 * - Call to TransferRepository for atomic balance deduction and dual transactions.
 * - Progress indicator dialog during execution.
 * - Failure dialog with retry option on failure.
 * - Navigation to SendSuccessActivity / MoneysentActivity on success.
 */
public class ConfirmSendActivity extends BaseActivity {

    private static final long PIN_LOCKOUT_MS = 30000;
    private static final int MAX_PIN_ATTEMPTS = 3;

    private ActivityConfirmSendBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;

    private DatabaseReference userRef;
    private ValueEventListener userListener;
    private DatabaseReference pinRef;
    private ValueEventListener pinListener;

    // Sender state
    private String senderName = "User";
    private String senderUpi = "";
    private String currentPinHash = "";
    private String currentPinSalt = "";

    // Transfer details
    private String receiverUid;
    private String receiverName;
    private String receiverUpi;
    private double amount = 0.0;
    private String note = "";

    // PIN attempt security
    private int wrongPinAttempts = 0;
    private long lockoutUntilTimestamp = 0;

    // Progress Dialog
    private AlertDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityConfirmSendBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, "Session expired. Please log in.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        readIntentExtras();
        setupViews();
        setupSenderListener();
        AvatarHelper.bindCurrentUser(binding.ivToolbarAvatar);
        AvatarHelper.bindUser(binding.ivRecipientAvatar, receiverUid);
    }

    private void readIntentExtras() {
        Intent intent = getIntent();
        receiverUid = intent.getStringExtra(SendMoneyActivity.EXTRA_RECEIVER_UID);
        receiverName = intent.getStringExtra(SendMoneyActivity.EXTRA_RECEIVER_NAME);
        receiverUpi = intent.getStringExtra(SendMoneyActivity.EXTRA_RECEIVER_UPI);
        amount = intent.getDoubleExtra(SendMoneyActivity.EXTRA_AMOUNT, 0.0);
        note = intent.getStringExtra(SendMoneyActivity.EXTRA_NOTE);

        if (TextUtils.isEmpty(receiverUid) || amount <= 0) {
            Toast.makeText(this, "Invalid transfer details.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void setupViews() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnCancel.setOnClickListener(v -> finish());

        binding.tvRecipientName.setText(receiverName != null ? receiverName : "SmartPay User");
        binding.tvRecipientUpiId.setText(receiverUpi != null ? receiverUpi : "");
        binding.tvAmount.setText(CurrencyHelper.formatRupees(amount));
        binding.tvNote.setText(TextUtils.isEmpty(note) ? "None" : note);

        String noteDisplay = "You are sending " + CurrencyHelper.formatRupees(amount) +
                " to " + (receiverName != null ? receiverName : "recipient") +
                " (" + (receiverUpi != null ? receiverUpi : "") + "). Please verify details before confirming.";
        binding.tvConfirmationNote.setText(noteDisplay);

        binding.btnConfirm.setOnClickListener(v -> handleConfirmClick());
    }

    private void setupSenderListener() {
        userRef = FirebaseRefs.getUserRef(currentUser.getUid());
        userListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                UserModel user = snapshot.getValue(UserModel.class);
                if (user != null) {
                    senderName = user.getName() != null ? user.getName() : "User";
                    senderUpi = user.getUpiId() != null ? user.getUpiId() : FirebaseConstants.generateUpiId(user.getPhone());
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Ignore cancel
            }
        };
        userRef.addValueEventListener(userListener);

        pinRef = FirebaseRefs.getUserPinRef(currentUser.getUid());
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
                // Ignore cancel
            }
        };
        pinRef.addValueEventListener(pinListener);
    }

    private void handleConfirmClick() {
        // 1. Check if sender has a PIN set up
        if (TextUtils.isEmpty(currentPinHash) || TextUtils.isEmpty(currentPinSalt)) {
            showCreatePinDialog();
            return;
        }

        // 2. Check 30s lockout
        long now = System.currentTimeMillis();
        if (now < lockoutUntilTimestamp) {
            long secondsLeft = (lockoutUntilTimestamp - now) / 1000 + 1;
            Toast.makeText(this,
                    "PIN entry locked due to multiple incorrect attempts. Please wait " + secondsLeft + "s.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        // 3. Prompt for PIN
        showVerifyPinDialog();
    }

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

            PinHelper.savePin(currentUser.getUid(), newPin, new PinHelper.PinSaveCallback() {
                @Override
                public void onSuccess(String pinHash, String pinSalt) {
                    currentPinHash = pinHash;
                    currentPinSalt = pinSalt;
                    dialog.dismiss();
                    Toast.makeText(ConfirmSendActivity.this, "PIN created! Please enter it to authorize transfer.", Toast.LENGTH_SHORT).show();
                    showVerifyPinDialog();
                }

                @Override
                public void onFailure(String errorMessage) {
                    tvError.setText("Failed to save PIN: " + (errorMessage != null ? errorMessage : "Unknown error"));
                    tvError.setVisibility(View.VISIBLE);
                }
            });
        });

        dialog.show();
    }

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
                tvError.setText("Please enter your 4-digit PIN.");
                tvError.setVisibility(View.VISIBLE);
                return;
            }

            boolean isValid = PinHelper.verifyPin(enteredPin, currentPinSalt, currentPinHash);
            if (isValid) {
                wrongPinAttempts = 0;
                dialog.dismiss();
                executePayment();
            } else {
                wrongPinAttempts++;
                if (wrongPinAttempts >= MAX_PIN_ATTEMPTS) {
                    lockoutUntilTimestamp = System.currentTimeMillis() + PIN_LOCKOUT_MS;
                    dialog.dismiss();
                    Toast.makeText(ConfirmSendActivity.this,
                            "Too many incorrect attempts! PIN entry is locked for 30 seconds.",
                            Toast.LENGTH_LONG).show();
                } else {
                    int remaining = MAX_PIN_ATTEMPTS - wrongPinAttempts;
                    etVerifyPin.setText("");
                    tvError.setText("Incorrect PIN. " + remaining + " attempt(s) remaining.");
                    tvError.setVisibility(View.VISIBLE);
                }
            }
        });

        dialog.show();
    }

    private void executePayment() {
        setButtonsEnabled(false);
        showProcessingDialog();

        TransferRepository.executeTransfer(
                currentUser.getUid(),
                senderName,
                senderUpi,
                receiverUid,
                receiverName,
                receiverUpi,
                amount,
                note,
                FirebaseConstants.TYPE_SENT,
                new TransferRepository.TransferCallback() {
                    @Override
                    public void onSuccess(String transactionId, double transferAmount) {
                        if (isFinishing() || isDestroyed()) return;
                        dismissProcessingDialog();

                        Intent intent = new Intent(ConfirmSendActivity.this, MoneysentActivity.class);
                        intent.putExtra(MoneysentActivity.EXTRA_TX_ID, transactionId);
                        intent.putExtra(MoneysentActivity.EXTRA_RECEIVER_NAME, receiverName);
                        intent.putExtra(MoneysentActivity.EXTRA_RECEIVER_UPI, receiverUpi);
                        intent.putExtra(MoneysentActivity.EXTRA_AMOUNT, transferAmount);
                        startActivity(intent);
                        finish();
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (isFinishing() || isDestroyed()) return;
                        dismissProcessingDialog();
                        setButtonsEnabled(true);
                        showFailureDialog(errorMessage);
                    }
                }
        );
    }

    private void setButtonsEnabled(boolean enabled) {
        binding.btnConfirm.setEnabled(enabled);
        binding.btnCancel.setEnabled(enabled);
        binding.btnBack.setEnabled(enabled);
    }

    private void showProcessingDialog() {
        if (progressDialog == null) {
            View view = LayoutInflater.from(this).inflate(R.layout.dialog_processing, null);
            TextView tvTitle = view.findViewById(R.id.tvProcessingTitle);
            TextView tvSub = view.findViewById(R.id.tvProcessingSubtitle);
            if (tvTitle != null) tvTitle.setText("Sending Money…");
            if (tvSub != null) tvSub.setText("Securing transaction and transferring funds.");

            progressDialog = new AlertDialog.Builder(this)
                    .setView(view)
                    .setCancelable(false)
                    .create();

            if (progressDialog.getWindow() != null) {
                progressDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }
        }
        progressDialog.show();
    }

    private void dismissProcessingDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    private void showFailureDialog(String errorMessage) {
        new AlertDialog.Builder(this)
                .setTitle("Transfer Failed")
                .setMessage(errorMessage != null ? errorMessage : "Payment could not be completed. Please try again.")
                .setIcon(R.drawable.ic_close_red)
                .setPositiveButton("TRY AGAIN", (dialog, which) -> handleConfirmClick())
                .setNegativeButton("CANCEL", null)
                .setCancelable(false)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-bind toolbar avatar in case the user updated their photo
        AvatarHelper.bindCurrentUser(binding.ivToolbarAvatar);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dismissProcessingDialog();
        if (userRef != null && userListener != null) {
            userRef.removeEventListener(userListener);
            userListener = null;
        }
        if (pinRef != null && pinListener != null) {
            pinRef.removeEventListener(pinListener);
            pinListener = null;
        }
    }
}

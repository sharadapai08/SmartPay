package com.smartpay.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.PopupMenu;
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
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.smartpay.app.databinding.ActivityQrPayBinding;

import java.util.HashMap;
import java.util.Map;

/**
 * QrPayActivity - QR Payment screen.
 *
 * Implements:
 * - Fetches receiver's real profile directly from users/{uid} (never trusts QR text).
 * - Blocks self-payment ("You cannot send money to yourself").
 * - Validates amount (> 0, <= available balance, <= ₹50,000 demo cap).
 * - PIN verification using PinHelper with 3-attempt 30s lockout and create-PIN prompt.
 * - Executes transfer via TransferRepository with transaction type QR_PAYMENT.
 * - Opens SendSuccessActivity on success and failure retry dialog on failure.
 * - Prevents double-submission and cleans up listeners in onDestroy.
 */
public class QrPayActivity extends BaseActivity {

    public static final String EXTRA_RECEIVER_UID = "extra_receiver_uid";
    private static final long PIN_LOCKOUT_MS = 30000;
    private static final int MAX_PIN_ATTEMPTS = 3;

    private ActivityQrPayBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;

    // Sender details & listeners
    private DatabaseReference senderRef;
    private ValueEventListener senderListener;
    private DatabaseReference pinRef;
    private ValueEventListener pinListener;
    private String senderName = "User";
    private String senderUpi = "";
    private double senderBalance = 0.0;
    private String senderPinHash = "";
    private String senderPinSalt = "";

    // Receiver details
    private String receiverUid;
    private String receiverName = "";
    private String receiverUpi = "";
    private DatabaseReference receiverRef;
    private ValueEventListener receiverListener;

    // Security tracking
    private int wrongPinAttempts = 0;
    private long lockoutUntilTimestamp = 0;

    private AlertDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityQrPayBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, "Session expired. Please log in.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        receiverUid = getIntent().getStringExtra(EXTRA_RECEIVER_UID);
        if (TextUtils.isEmpty(receiverUid)) {
            Toast.makeText(this, "Invalid QR code payload.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Self-payment prevention
        if (receiverUid.equals(currentUser.getUid())) {
            Toast.makeText(this, "You cannot send money to yourself.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setupUI();
        loadSenderData();
        loadReceiverData();
        AvatarHelper.bindCurrentUser(binding.ivToolbarAvatar);
    }

    private void setupUI() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnCancel.setOnClickListener(v -> finish());
        binding.btnMore.setOnClickListener(this::showPopupMenu);

        binding.btnPay.setOnClickListener(v -> handlePayClick());
    }

    private void showPopupMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.menu_qr_options, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_add_money) {
                startActivity(new Intent(QrPayActivity.this, AddMoneyActivity.class));
                return true;
            } else if (id == R.id.menu_send_money) {
                startActivity(new Intent(QrPayActivity.this, SendMoneyActivity.class));
                return true;
            } else if (id == R.id.menu_receive_money) {
                startActivity(new Intent(QrPayActivity.this, ReceiveMoneyActivity.class));
                return true;
            } else if (id == R.id.menu_transactions) {
                startActivity(new Intent(QrPayActivity.this, TransactionsActivity.class));
                return true;
            } else if (id == R.id.menu_profile) {
                startActivity(new Intent(QrPayActivity.this, ProfileActivity.class));
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void loadSenderData() {
        senderRef = FirebaseRefs.getUserRef(currentUser.getUid());
        senderListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                UserModel user = snapshot.getValue(UserModel.class);
                if (user != null) {
                    senderName = user.getName() != null ? user.getName() : "User";
                    senderUpi = user.getUpiId() != null ? user.getUpiId() : FirebaseConstants.generateUpiId(user.getPhone());
                    senderBalance = user.getBalance();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Ignore cancel
            }
        };
        senderRef.addValueEventListener(senderListener);

        pinRef = FirebaseRefs.getUserPinRef(currentUser.getUid());
        pinListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String hash = snapshot.child("pinHash").getValue(String.class);
                    String salt = snapshot.child("pinSalt").getValue(String.class);
                    senderPinHash = hash != null ? hash : "";
                    senderPinSalt = salt != null ? salt : "";
                } else {
                    senderPinHash = "";
                    senderPinSalt = "";
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Ignore cancel
            }
        };
        pinRef.addValueEventListener(pinListener);
    }

    /**
     * Fetches genuine receiver details from users/{uid}.
     * NEVER trusts the display name contained in the QR string.
     */
    private void loadReceiverData() {
        receiverRef = FirebaseRefs.getUserRef(receiverUid);
        receiverListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Toast.makeText(QrPayActivity.this, "User not registered on SmartPay.", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }

                UserModel receiver = snapshot.getValue(UserModel.class);
                if (receiver != null) {
                    receiverName = receiver.getName() != null ? receiver.getName() : "SmartPay User";
                    receiverUpi = receiver.getUpiId() != null ? receiver.getUpiId() : FirebaseConstants.generateUpiId(receiver.getPhone());

                    binding.tvRecipientName.setText(receiverName);
                    binding.tvRecipientUpiId.setText(receiverUpi);
                    // Load receiver's profile photo
                    AvatarHelper.bindPhoto(binding.ivRecipientAvatar, receiver.getPhotoBase64());

                    // Signal to the receiver's ReceiveMoneyActivity that their QR was scanned
                    FirebaseRefs.getQrPaymentsRef()
                            .child(receiverUid)
                            .child("scannedAt")
                            .setValue(ServerValue.TIMESTAMP);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(QrPayActivity.this, "Failed to load recipient details: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                finish();
            }
        };
        receiverRef.addListenerForSingleValueEvent(receiverListener);
    }

    private void handlePayClick() {
        if (TextUtils.isEmpty(receiverName)) {
            Toast.makeText(this, "Loading recipient details… please wait.", Toast.LENGTH_SHORT).show();
            return;
        }

        String amountStr = binding.etAmount.getText().toString().trim().replace("₹", "").trim();
        if (TextUtils.isEmpty(amountStr)) {
            binding.etAmount.setError("Enter amount to pay");
            binding.etAmount.requestFocus();
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            binding.etAmount.setError("Invalid amount format");
            binding.etAmount.requestFocus();
            return;
        }

        if (amount <= 0) {
            binding.etAmount.setError("Amount must be greater than zero");
            binding.etAmount.requestFocus();
            return;
        }

        if (amount > FirebaseConstants.MAX_TRANSFER_AMOUNT) {
            binding.etAmount.setError("Maximum transfer limit is ₹50,000");
            binding.etAmount.requestFocus();
            return;
        }

        if (amount > senderBalance) {
            binding.etAmount.setError("Insufficient balance (" + CurrencyHelper.formatRupees(senderBalance) + ")");
            Toast.makeText(this, "Insufficient balance! Please add money to your wallet.", Toast.LENGTH_SHORT).show();
            binding.etAmount.requestFocus();
            return;
        }

        // Verify PIN flow
        initiatePinAuthorization();
    }

    private void initiatePinAuthorization() {
        if (TextUtils.isEmpty(senderPinHash) || TextUtils.isEmpty(senderPinSalt)) {
            showCreatePinDialog();
            return;
        }

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
                    senderPinHash = pinHash;
                    senderPinSalt = pinSalt;
                    dialog.dismiss();
                    Toast.makeText(QrPayActivity.this, "PIN created! Enter PIN to authorize QR payment.", Toast.LENGTH_SHORT).show();
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

            boolean isValid = PinHelper.verifyPin(enteredPin, senderPinSalt, senderPinHash);
            if (isValid) {
                wrongPinAttempts = 0;
                dialog.dismiss();
                executeQrPayment();
            } else {
                wrongPinAttempts++;
                if (wrongPinAttempts >= MAX_PIN_ATTEMPTS) {
                    lockoutUntilTimestamp = System.currentTimeMillis() + PIN_LOCKOUT_MS;
                    dialog.dismiss();
                    Toast.makeText(QrPayActivity.this,
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

    private void executeQrPayment() {
        String amountStr = binding.etAmount.getText().toString().trim().replace("₹", "").trim();
        double amount = Double.parseDouble(amountStr);
        String note = binding.etNote.getText().toString().trim();
        if (TextUtils.isEmpty(note)) {
            note = "QR payment to " + receiverName;
        }

        setButtonsEnabled(false);
        showProcessingDialog();

        // Reuse TransferRepository with TYPE_QR_PAYMENT
        TransferRepository.executeTransfer(
                currentUser.getUid(),
                senderName,
                senderUpi,
                receiverUid,
                receiverName,
                receiverUpi,
                amount,
                note,
                FirebaseConstants.TYPE_QR_PAYMENT,
                new TransferRepository.TransferCallback() {
                    @Override
                    public void onSuccess(String transactionId, double transferAmount) {
                        if (isFinishing() || isDestroyed()) return;
                        dismissProcessingDialog();

                        Intent intent = new Intent(QrPayActivity.this, SendSuccessActivity.class);
                        intent.putExtra(SendSuccessActivity.EXTRA_TX_ID, transactionId);
                        intent.putExtra(SendSuccessActivity.EXTRA_RECEIVER_NAME, receiverName);
                        intent.putExtra(SendSuccessActivity.EXTRA_RECEIVER_UPI, receiverUpi);
                        intent.putExtra(SendSuccessActivity.EXTRA_AMOUNT, transferAmount);
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
        binding.btnPay.setEnabled(enabled);
        binding.btnCancel.setEnabled(enabled);
        binding.btnBack.setEnabled(enabled);
    }

    private void showProcessingDialog() {
        if (progressDialog == null) {
            View view = LayoutInflater.from(this).inflate(R.layout.dialog_processing, null);
            TextView tvTitle = view.findViewById(R.id.tvProcessingTitle);
            TextView tvSub = view.findViewById(R.id.tvProcessingSubtitle);
            if (tvTitle != null) tvTitle.setText("Authorizing QR Payment…");
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
                .setTitle("QR Payment Failed")
                .setMessage(errorMessage != null ? errorMessage : "Payment could not be completed. Please try again.")
                .setIcon(R.drawable.ic_close_red)
                .setPositiveButton("TRY AGAIN", (dialog, which) -> handlePayClick())
                .setNegativeButton("CANCEL", null)
                .setCancelable(false)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dismissProcessingDialog();

        if (senderRef != null && senderListener != null) {
            senderRef.removeEventListener(senderListener);
            senderListener = null;
        }

        if (pinRef != null && pinListener != null) {
            pinRef.removeEventListener(pinListener);
            pinListener = null;
        }

        if (receiverRef != null && receiverListener != null) {
            receiverRef.removeEventListener(receiverListener);
            receiverListener = null;
        }
    }
}

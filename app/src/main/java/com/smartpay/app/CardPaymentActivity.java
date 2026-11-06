package com.smartpay.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.smartpay.app.databinding.ActivityCardPaymentBinding;

/**
 * CardPaymentActivity - Handles simulated Debit/Credit Card top-up.
 *
 * Implements:
 * - Card number 16-digit auto-formatting (groups of 4)
 * - Strict Luhn algorithm verification (mod 10)
 * - Expiry auto-formatting (MM/YY) and non-expiry date verification
 * - Masked CVV verification (3 or 4 digits)
 * - Cardholder name verification
 * - Strict Security: NEVER stores or transmits card numbers, CVVs, or expiries anywhere.
 * - 1.5-second simulated payment processing dialog
 * - Atomic wallet balance top-up via PaymentHelper
 * - Payment success dialog and return to HomeActivity
 */
public class CardPaymentActivity extends BaseActivity {

    private ActivityCardPaymentBinding binding;
    private double amount = 0.0;
    private String userName = "User";
    private String uid = "";

    private AlertDialog processingDialog;
    private AlertDialog successDialog;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCardPaymentBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        amount = getIntent().getDoubleExtra(AddMoneyActivity.EXTRA_AMOUNT, 1000.0);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            uid = user.getUid();
            if (user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
                userName = user.getDisplayName();
            }
        }

        binding.tvCardAmountBanner.setText(CurrencyHelper.formatRupees(amount));

        setupFormatters();
        setupListeners();
    }

    private void setupFormatters() {
        // Auto-format card number in groups of 4: "1234 5678 9012 3456"
        binding.etCardNumber.addTextChangedListener(new TextWatcher() {
            private boolean isFormatting = false;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (isFormatting) return;
                isFormatting = true;

                String digits = s.toString().replaceAll("\\s+", "");
                if (digits.length() > 16) {
                    digits = digits.substring(0, 16);
                }

                StringBuilder formatted = new StringBuilder();
                for (int i = 0; i < digits.length(); i++) {
                    if (i > 0 && i % 4 == 0) {
                        formatted.append(" ");
                    }
                    formatted.append(digits.charAt(i));
                }

                binding.etCardNumber.setText(formatted.toString());
                binding.etCardNumber.setSelection(formatted.length());

                isFormatting = false;
            }
        });

        // Auto-format expiry: "MM/YY"
        binding.etExpiry.addTextChangedListener(new TextWatcher() {
            private boolean isFormatting = false;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (isFormatting) return;
                isFormatting = true;

                String text = s.toString().replace("/", "").replaceAll("\\s+", "");
                if (text.length() > 4) {
                    text = text.substring(0, 4);
                }

                StringBuilder formatted = new StringBuilder();
                for (int i = 0; i < text.length(); i++) {
                    if (i == 2) {
                        formatted.append("/");
                    }
                    formatted.append(text.charAt(i));
                }

                binding.etExpiry.setText(formatted.toString());
                binding.etExpiry.setSelection(formatted.length());

                isFormatting = false;
            }
        });
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());

        binding.btnProceed.setOnClickListener(v -> handleCardPayment());
    }

    private void handleCardPayment() {
        String rawCardNumber = binding.etCardNumber.getText().toString().replaceAll("\\s+", "");
        String expiry = binding.etExpiry.getText().toString().trim();
        String cvv = binding.etCvv.getText().toString().trim();
        String cardholder = binding.etCardHolderName.getText().toString().trim();

        // 1. Validate Card Number (16 digits + Luhn check)
        if (TextUtils.isEmpty(rawCardNumber)) {
            binding.etCardNumber.setError("Card number is required");
            binding.etCardNumber.requestFocus();
            return;
        }

        if (rawCardNumber.length() != 16) {
            binding.etCardNumber.setError("Card number must be exactly 16 digits");
            binding.etCardNumber.requestFocus();
            return;
        }

        if (!PaymentHelper.isValidLuhn(rawCardNumber)) {
            binding.etCardNumber.setError("Invalid card number (Luhn check failed)");
            binding.etCardNumber.requestFocus();
            return;
        }

        // 2. Validate Expiry (MM/YY format & non-expired)
        if (TextUtils.isEmpty(expiry)) {
            binding.etExpiry.setError("Expiry required");
            binding.etExpiry.requestFocus();
            return;
        }

        if (!PaymentHelper.isValidExpiry(expiry)) {
            binding.etExpiry.setError("Card is expired or invalid date");
            binding.etExpiry.requestFocus();
            return;
        }

        // 3. Validate CVV (3 or 4 digits)
        if (TextUtils.isEmpty(cvv) || cvv.length() < 3 || cvv.length() > 4) {
            binding.etCvv.setError("3-4 digit CVV required");
            binding.etCvv.requestFocus();
            return;
        }

        // 4. Validate Cardholder Name
        if (TextUtils.isEmpty(cardholder) || cardholder.length() < 2) {
            binding.etCardHolderName.setError("Cardholder name is required");
            binding.etCardHolderName.requestFocus();
            return;
        }

        // Extract masked last 4 digits for receipt display only
        String last4 = rawCardNumber.substring(rawCardNumber.length() - 4);

        // Security rule: Wipe sensitive data from memory and input fields
        binding.etCardNumber.setText("");
        binding.etExpiry.setText("");
        binding.etCvv.setText("");

        // Disable button to prevent double-submit
        binding.btnProceed.setEnabled(false);

        // Show 1.5s simulated processing dialog
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_processing, null);
        processingDialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        if (processingDialog.getWindow() != null) {
            processingDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        processingDialog.show();

        mHandler.postDelayed(() -> {
            String source = "Card (ending in " + last4 + ")";
            PaymentHelper.executeAddMoney(uid, userName, amount, source, new PaymentHelper.PaymentCallback() {
                @Override
                public void onSuccess(String transactionId, double amountAdded) {
                    if (processingDialog != null && processingDialog.isShowing()) {
                        processingDialog.dismiss();
                    }
                    showSuccessDialog(transactionId, amountAdded);
                }

                @Override
                public void onFailure(String errorMessage) {
                    if (processingDialog != null && processingDialog.isShowing()) {
                        processingDialog.dismiss();
                    }
                    binding.btnProceed.setEnabled(true);
                    Toast.makeText(CardPaymentActivity.this, "Transaction failed: " + errorMessage, Toast.LENGTH_LONG).show();
                }
            });
        }, 1500); // 1.5s simulated delay
    }

    private void showSuccessDialog(String transactionId, double amountAdded) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_payment_success, null);
        successDialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        if (successDialog.getWindow() != null) {
            successDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvAmount = dialogView.findViewById(R.id.tvSuccessAmount);
        TextView tvTxId = dialogView.findViewById(R.id.tvSuccessTxId);
        Button btnDone = dialogView.findViewById(R.id.btnSuccessDone);

        tvAmount.setText(CurrencyHelper.formatRupees(amountAdded));
        tvTxId.setText("Tx ID: " + transactionId);

        btnDone.setOnClickListener(v -> {
            successDialog.dismiss();
            // Return to HomeActivity
            Intent intent = new Intent(CardPaymentActivity.this, HomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        successDialog.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacksAndMessages(null);
        if (processingDialog != null && processingDialog.isShowing()) {
            processingDialog.dismiss();
        }
        if (successDialog != null && successDialog.isShowing()) {
            successDialog.dismiss();
        }
    }
}

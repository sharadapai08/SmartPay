package com.smartpay.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.smartpay.app.databinding.ActivityAddMoneyBinding;

/**
 * AddMoneyActivity - First step in wallet top-up flow
 *
 * Implements:
 * - Enter Amount field (₹) with Quick Amount Chips (₹500, ₹1,000, ₹2,000, ₹5,000)
 * - Payment Method selection: "UPI ID / BANK" and "DEBIT / CREDIT CARD"
 * - Validation: Amount must be > 0 and <= ₹50,000 (demo maximum limit)
 * - Navigation to UpiBankActivity or CardPaymentActivity with validated amount
 * - Displays mandatory demo disclaimer note
 */
public class AddMoneyActivity extends BaseActivity {

    public static final String EXTRA_AMOUNT = "EXTRA_AMOUNT";

    private ActivityAddMoneyBinding binding;
    private int selectedPaymentMethod = 1; // 1 = UPI/Bank, 2 = Debit/Credit Card

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddMoneyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupListeners();
    }

    private void setupListeners() {
        // Back button
        binding.btnBack.setOnClickListener(v -> finish());

        // More options
        binding.btnMore.setOnClickListener(this::showPopupMenu);

        // Quick amount chips
        binding.chip500.setOnClickListener(v -> selectChip("500"));
        binding.chip1000.setOnClickListener(v -> selectChip("1000"));
        binding.chip2000.setOnClickListener(v -> selectChip("2000"));
        binding.chip5000.setOnClickListener(v -> selectChip("5000"));

        // Clear amount field on tap so the user doesn't have to manually delete the default value
        binding.etAmount.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                binding.etAmount.setText("");
            }
        });
        binding.etAmount.setOnClickListener(v -> binding.etAmount.setText(""));

        // Payment method selection rows
        binding.rowUpiBank.setOnClickListener(v -> {
            selectPaymentMethod(1);
        });

        binding.rowCard.setOnClickListener(v -> {
            selectPaymentMethod(2);
        });

        // ADD MONEY Button
        binding.btnAddMoney.setOnClickListener(v -> proceedToPayment());
    }

    private void showPopupMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.menu_add_money_options, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_send_money) {
                startActivity(new Intent(AddMoneyActivity.this, SendMoneyActivity.class));
                return true;
            } else if (id == R.id.menu_receive_money) {
                startActivity(new Intent(AddMoneyActivity.this, ReceiveMoneyActivity.class));
                return true;
            } else if (id == R.id.menu_scan_qr) {
                startActivity(new Intent(AddMoneyActivity.this, QrScannerActivity.class));
                return true;
            } else if (id == R.id.menu_transactions) {
                startActivity(new Intent(AddMoneyActivity.this, TransactionsActivity.class));
                return true;
            } else if (id == R.id.menu_profile) {
                startActivity(new Intent(AddMoneyActivity.this, ProfileActivity.class));
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void selectPaymentMethod(int method) {
        selectedPaymentMethod = method;
        if (method == 1) {
            binding.rowUpiBank.setBackgroundResource(R.drawable.bg_option_selected);
            binding.rowCard.setBackgroundResource(R.drawable.bg_option_unselected);
        } else {
            binding.rowUpiBank.setBackgroundResource(R.drawable.bg_option_unselected);
            binding.rowCard.setBackgroundResource(R.drawable.bg_option_selected);
        }
    }

    private void selectChip(String amount) {
        binding.etAmount.setText(amount);
        binding.etAmount.setSelection(amount.length());
        resetChipStyles();
        if ("500".equals(amount)) {
            binding.chip500.setBackgroundResource(R.drawable.bg_chip_selected);
            binding.chip500.setTextColor(getColor(R.color.white));
        } else if ("1000".equals(amount)) {
            binding.chip1000.setBackgroundResource(R.drawable.bg_chip_selected);
            binding.chip1000.setTextColor(getColor(R.color.white));
        } else if ("2000".equals(amount)) {
            binding.chip2000.setBackgroundResource(R.drawable.bg_chip_selected);
            binding.chip2000.setTextColor(getColor(R.color.white));
        } else if ("5000".equals(amount)) {
            binding.chip5000.setBackgroundResource(R.drawable.bg_chip_selected);
            binding.chip5000.setTextColor(getColor(R.color.white));
        }
    }

    private void resetChipStyles() {
        int unselectedBg = R.drawable.bg_chip_unselected;
        int unselectedText = getColor(R.color.text_primary);

        binding.chip500.setBackgroundResource(unselectedBg);
        binding.chip500.setTextColor(unselectedText);
        binding.chip1000.setBackgroundResource(unselectedBg);
        binding.chip1000.setTextColor(unselectedText);
        binding.chip2000.setBackgroundResource(unselectedBg);
        binding.chip2000.setTextColor(unselectedText);
        binding.chip5000.setBackgroundResource(unselectedBg);
        binding.chip5000.setTextColor(unselectedText);
    }

    private void proceedToPayment() {
        String amountStr = binding.etAmount.getText().toString().trim();
        if (TextUtils.isEmpty(amountStr)) {
            binding.etAmount.setError("Please enter an amount");
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
            binding.etAmount.setError("Amount must be greater than ₹0");
            binding.etAmount.requestFocus();
            return;
        }

        if (amount > FirebaseConstants.MAX_ADD_MONEY_AMOUNT) {
            binding.etAmount.setError("Maximum top-up is " + CurrencyHelper.formatRupees(FirebaseConstants.MAX_ADD_MONEY_AMOUNT));
            binding.etAmount.requestFocus();
            return;
        }

        if (selectedPaymentMethod == 1) {
            Intent intent = new Intent(this, UpiBankActivity.class);
            intent.putExtra(EXTRA_AMOUNT, amount);
            startActivity(intent);
        } else {
            Intent intent = new Intent(this, CardPaymentActivity.class);
            intent.putExtra(EXTRA_AMOUNT, amount);
            startActivity(intent);
        }
    }
}

package com.smartpay.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.smartpay.app.databinding.ActivityMoneysentBinding;

/**
 * SendSuccessActivity - Payment success screen.
 *
 * Implements:
 * - Green checkmark circle with sparkles
 * - "MONEY SENT SUCCESSFULLY!" header
 * - Recipient card with name, UPI ID, and sent amount
 * - VIEW TRANSACTION button (opens TransactionsActivity)
 * - MAKE ANOTHER PAYMENT button (opens SendMoneyActivity)
 * - DONE button (returns to HomeActivity clearing the back stack)
 */
public class SendSuccessActivity extends BaseActivity {

    public static final String EXTRA_TX_ID = "extra_tx_id";
    public static final String EXTRA_RECEIVER_NAME = "extra_receiver_name";
    public static final String EXTRA_RECEIVER_UPI = "extra_receiver_upi";
    public static final String EXTRA_AMOUNT = "extra_amount";

    protected ActivityMoneysentBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMoneysentBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Intent intent = getIntent();
        String txId = intent.getStringExtra(EXTRA_TX_ID);
        String receiverName = intent.getStringExtra(EXTRA_RECEIVER_NAME);
        String receiverUpi = intent.getStringExtra(EXTRA_RECEIVER_UPI);
        double amount = intent.getDoubleExtra(EXTRA_AMOUNT, 0.0);

        binding.tvRecipientName.setText(receiverName != null ? receiverName : "SmartPay User");
        binding.tvRecipientUpi.setText(receiverUpi != null ? receiverUpi : "");
        binding.tvAmount.setText(CurrencyHelper.formatRupees(amount));

        // VIEW TRANSACTION
        binding.btnViewTransaction.setOnClickListener(v -> {
            Intent txIntent = new Intent(SendSuccessActivity.this, TransactionsActivity.class);
            if (txId != null) {
                txIntent.putExtra(EXTRA_TX_ID, txId);
            }
            startActivity(txIntent);
        });

        // MAKE ANOTHER PAYMENT
        binding.btnMakeAnotherPayment.setOnClickListener(v -> {
            Intent sendIntent = new Intent(SendSuccessActivity.this, SendMoneyActivity.class);
            startActivity(sendIntent);
            finish();
        });

        // DONE - Return to Home, clearing back stack
        binding.btnDone.setOnClickListener(v -> {
            Intent homeIntent = new Intent(SendSuccessActivity.this, HomeActivity.class);
            homeIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(homeIntent);
            finish();
        });
        // Modern back pressed handler - Return to Home clearing back stack
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Intent homeIntent = new Intent(SendSuccessActivity.this, HomeActivity.class);
                homeIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(homeIntent);
                finish();
            }
        });
    }
}

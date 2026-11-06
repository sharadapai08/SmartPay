package com.smartpay.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.smartpay.app.databinding.ActivityAddBankAccountBinding;

/**
 * AddBankAccountActivity — lets the user save a bank account to Firebase
 * at bankAccounts/{uid}/{accountId}.
 *
 * Can be launched from UpiBankActivity (bank selected but not yet linked) or
 * directly from ProfileActivity. When launched from UpiBankActivity the bank
 * name is pre-filled via EXTRA_BANK_NAME and the amount via EXTRA_AMOUNT so
 * that after saving the account we can immediately process the top-up.
 */
public class AddBankAccountActivity extends BaseActivity {

    public static final String EXTRA_BANK_NAME = "extra_bank_name";
    /** Amount to top up after saving (0 = no auto top-up, just save). */
    public static final String EXTRA_AMOUNT    = "extra_amount";

    private ActivityAddBankAccountBinding binding;
    private boolean isOperationInProgress = false;
    private double pendingAmount = 0.0;
    private String selectedBankName = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddBankAccountBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String prefilledBank = getIntent().getStringExtra(EXTRA_BANK_NAME);
        pendingAmount = getIntent().getDoubleExtra(EXTRA_AMOUNT, 0.0);

        setupBankSpinner(prefilledBank);

        if (!TextUtils.isEmpty(prefilledBank)) {
            binding.tvPrefilledBankName.setText(prefilledBank);
            binding.layoutBankBanner.setVisibility(View.VISIBLE);
        }

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnCancel.setOnClickListener(v -> finish());
        binding.btnSaveAccount.setOnClickListener(v -> handleSave());
    }

    /**
     * Sets up the bank name spinner with all available banks.
     * If prefilledBank is provided, selects it in the spinner.
     */
    private void setupBankSpinner(String prefilledBank) {
        java.util.List<String> bankList = FirebaseConstants.getBankList();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, bankList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerBankName.setAdapter(adapter);

        binding.spinnerBankName.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedBankName = bankList.get(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedBankName = null;
            }
        });

        // Pre-select if prefilled
        if (!TextUtils.isEmpty(prefilledBank)) {
            int index = bankList.indexOf(prefilledBank);
            if (index >= 0) {
                binding.spinnerBankName.setSelection(index);
                selectedBankName = prefilledBank;
            }
        }
    }

    private void handleSave() {
        if (isOperationInProgress) return;

        if (TextUtils.isEmpty(selectedBankName)) {
            Toast.makeText(this, "Please select a bank", Toast.LENGTH_SHORT).show();
            return;
        }

        String holder       = binding.etAccountHolder.getText().toString().trim();
        String acNumber     = binding.etAccountNumber.getText().toString().trim();
        String confirmAc    = binding.etConfirmAccountNumber.getText().toString().trim();
        String ifsc         = binding.etIfsc.getText().toString().trim().toUpperCase();

        // Validate
        if (TextUtils.isEmpty(holder)) {
            binding.etAccountHolder.setError("Account holder name is required");
            binding.etAccountHolder.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(acNumber) || acNumber.length() < 9) {
            binding.etAccountNumber.setError("Enter a valid account number (min 9 digits)");
            binding.etAccountNumber.requestFocus();
            return;
        }
        if (!acNumber.equals(confirmAc)) {
            binding.etConfirmAccountNumber.setError("Account numbers do not match");
            binding.etConfirmAccountNumber.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(ifsc) || !ifsc.matches("^[A-Z]{4}0[A-Z0-9]{6}$")) {
            binding.etIfsc.setError("Enter a valid IFSC code (e.g. HDFC0001234)");
            binding.etIfsc.requestFocus();
            return;
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Session expired. Please log in.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        startOperation();

        String uid = currentUser.getUid();
        String accountId = FirebaseRefs.getBankAccountsRef(uid).push().getKey();
        if (accountId == null) accountId = "acct_" + System.currentTimeMillis();

        BankAccountModel account = new BankAccountModel(
                accountId, selectedBankName, acNumber, ifsc, holder, System.currentTimeMillis());

        final String finalAccountId = accountId;
        FirebaseRefs.getBankAccountsRef(uid).child(accountId).setValue(account)
                .addOnSuccessListener(unused -> {
                    stopOperation();
                    Toast.makeText(this, "Bank account saved!", Toast.LENGTH_SHORT).show();

                    if (pendingAmount > 0) {
                        // Proceed to top-up using this bank
                        Intent result = new Intent();
                        result.putExtra(EXTRA_BANK_NAME, selectedBankName);
                        result.putExtra(EXTRA_AMOUNT, pendingAmount);
                        setResult(RESULT_OK, result);
                    }
                    finish();
                })
                .addOnFailureListener(e -> {
                    stopOperation();
                    String msg = e.getMessage() != null ? e.getMessage() : "Save failed";
                    Toast.makeText(this, "Error: " + msg, Toast.LENGTH_LONG).show();
                });
    }

    private void startOperation() {
        isOperationInProgress = true;
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnSaveAccount.setEnabled(false);
        binding.btnCancel.setEnabled(false);
    }

    private void stopOperation() {
        isOperationInProgress = false;
        binding.progressBar.setVisibility(View.GONE);
        binding.btnSaveAccount.setEnabled(true);
        binding.btnCancel.setEnabled(true);
    }
}

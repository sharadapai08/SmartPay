package com.smartpay.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.smartpay.app.databinding.ActivityUpiBankBinding;
import com.smartpay.app.databinding.ItemBankBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * UpiBankActivity — UPI / NetBanking top-up.
 *
 * Bank selection flow:
 * 1. User taps a bank.
 * 2. If the user has a saved account for that bank → process payment immediately.
 * 3. If not → open AddBankAccountActivity pre-filled with the bank name.
 *    On return (RESULT_OK) → process the payment using that bank.
 */
public class UpiBankActivity extends BaseActivity {

    public static class BankItem {
        final String name;
        final String badge;
        final int color;

        public BankItem(String name, String badge, int color) {
            this.name = name;
            this.badge = badge;
            this.color = color;
        }
    }

    private ActivityUpiBankBinding binding;
    private double amount = 0.0;
    private String userName = "User";
    private String uid = "";

    // Saved bank accounts loaded from Firebase
    private final List<BankAccountModel> savedAccounts = new ArrayList<>();
    private DatabaseReference bankAccountsRef;
    private ValueEventListener bankAccountsListener;

    private BankAdapter bankAdapter;
    private final List<BankItem> allBanks = new ArrayList<>();
    private final List<BankItem> topBanks = new ArrayList<>();
    private boolean isAllBanksExpanded = false;

    private AlertDialog processingDialog;
    private AlertDialog successDialog;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    // Launcher for AddBankAccountActivity — returns bank name to use for payment
    private final ActivityResultLauncher<Intent> addBankLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String bankName = result.getData().getStringExtra(AddBankAccountActivity.EXTRA_BANK_NAME);
                    double amt = result.getData().getDoubleExtra(AddBankAccountActivity.EXTRA_AMOUNT, 0.0);
                    if (!TextUtils.isEmpty(bankName) && amt > 0) {
                        processPayment("NetBanking (" + bankName + ")");
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityUpiBankBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        amount = getIntent().getDoubleExtra(AddMoneyActivity.EXTRA_AMOUNT, 1000.0);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            uid = user.getUid();
            if (user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
                userName = user.getDisplayName();
            }
        }

        binding.tvAmountBanner.setText(CurrencyHelper.formatRupees(amount));

        setupBankData();
        setupRecyclerView();
        setupListeners();
        loadSavedAccounts();
    }

    /** Loads the user's saved bank accounts so we can check on bank tap. */
    private void loadSavedAccounts() {
        if (TextUtils.isEmpty(uid)) return;
        bankAccountsRef = FirebaseRefs.getBankAccountsRef(uid);
        bankAccountsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                savedAccounts.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    BankAccountModel acc = child.getValue(BankAccountModel.class);
                    if (acc != null) {
                        if (acc.getAccountId() == null) acc.setAccountId(child.getKey());
                        savedAccounts.add(acc);
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        bankAccountsRef.addValueEventListener(bankAccountsListener);
    }

    /**
     * Returns the first saved account whose bank name contains (case-insensitive)
     * the given bank name, or null if none found.
     */
    private BankAccountModel findSavedAccount(String bankName) {
        String lower = bankName.toLowerCase().trim();
        for (BankAccountModel acc : savedAccounts) {
            if (acc.getBankName() != null && acc.getBankName().toLowerCase().contains(lower)) {
                return acc;
            }
        }
        return null;
    }

    private void setupBankData() {
        java.util.List<String> bankNames = FirebaseConstants.getBankList();
        
        // Top 4 banks with colors
        topBanks.add(new BankItem("State Bank of India", "SBI", android.graphics.Color.parseColor("#1E88E5")));
        topBanks.add(new BankItem("HDFC Bank", "HDFC", android.graphics.Color.parseColor("#004C8F")));
        topBanks.add(new BankItem("ICICI Bank", "ICICI", android.graphics.Color.parseColor("#F58220")));
        topBanks.add(new BankItem("Axis Bank", "AXIS", android.graphics.Color.parseColor("#97144D")));

        allBanks.addAll(topBanks);
        
        // Remaining banks
        int[] colors = {
            android.graphics.Color.parseColor("#ED1C24"),  // Kotak
            android.graphics.Color.parseColor("#D97706"),  // PNB
            android.graphics.Color.parseColor("#EA580C"),  // BOB
            android.graphics.Color.parseColor("#0284C7"),  // Canara
            android.graphics.Color.parseColor("#7C3AED"),  // IndusInd
            android.graphics.Color.parseColor("#2563EB"),  // Yes Bank
            android.graphics.Color.parseColor("#DC2626")   // Union Bank
        };
        
        String[] badges = {"KMBL", "PNB", "BOB", "CNB", "IIB", "YES", "UBI"};
        
        for (int i = 4; i < bankNames.size(); i++) {
            allBanks.add(new BankItem(bankNames.get(i), badges[i - 4], colors[i - 4]));
        }
    }

    private void setupRecyclerView() {
        bankAdapter = new BankAdapter(topBanks, this::handleBankTap);
        binding.rvBanks.setLayoutManager(new LinearLayoutManager(this));
        binding.rvBanks.setAdapter(bankAdapter);
    }

    /**
     * Called when the user taps a bank row.
     * Checks for a saved account matching that bank; otherwise opens AddBankAccountActivity.
     */
    private void handleBankTap(BankItem bank) {
        BankAccountModel saved = findSavedAccount(bank.name);
        if (saved != null) {
            // Saved account found — confirm and pay
            new AlertDialog.Builder(this)
                    .setTitle("Pay via " + bank.name)
                    .setMessage("Use account " + saved.getMaskedAccountNumber()
                            + " (" + saved.getAccountHolder() + ") to add "
                            + CurrencyHelper.formatRupees(amount) + " to your wallet?")
                    .setPositiveButton("CONFIRM", (d, w) ->
                            processPayment("NetBanking (" + bank.name + ")"))
                    .setNegativeButton("CANCEL", null)
                    .show();
        } else {
            // No saved account — prompt to add one first
            new AlertDialog.Builder(this)
                    .setTitle("No account linked")
                    .setMessage("You haven't linked a " + bank.name
                            + " account yet. Add your bank account details to proceed.")
                    .setPositiveButton("ADD ACCOUNT", (d, w) -> {
                        Intent intent = new Intent(UpiBankActivity.this, AddBankAccountActivity.class);
                        intent.putExtra(AddBankAccountActivity.EXTRA_BANK_NAME, bank.name);
                        intent.putExtra(AddBankAccountActivity.EXTRA_AMOUNT, amount);
                        addBankLauncher.launch(intent);
                    })
                    .setNegativeButton("CANCEL", null)
                    .show();
        }
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());

        binding.btnPayUpi.setOnClickListener(v -> {
            String upiId = binding.etUpiId.getText().toString().trim();
            if (TextUtils.isEmpty(upiId)) {
                binding.etUpiId.setError("Please enter a UPI ID");
                binding.etUpiId.requestFocus();
                return;
            }
            if (!PaymentHelper.isValidUpiId(upiId)) {
                binding.etUpiId.setError("Invalid UPI ID format (e.g. name@upi)");
                binding.etUpiId.requestFocus();
                return;
            }
            processPayment("UPI (" + upiId + ")");
        });

        binding.etSearchBank.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterBanks(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        binding.tvViewAllBanks.setOnClickListener(v -> {
            isAllBanksExpanded = !isAllBanksExpanded;
            if (isAllBanksExpanded) {
                bankAdapter.updateList(allBanks);
                binding.tvViewAllBanks.setText("SHOW LESS");
            } else {
                bankAdapter.updateList(topBanks);
                binding.tvViewAllBanks.setText("VIEW ALL BANKS");
            }
        });
    }

    private void filterBanks(String query) {
        if (TextUtils.isEmpty(query)) {
            bankAdapter.updateList(isAllBanksExpanded ? allBanks : topBanks);
            return;
        }
        List<BankItem> filtered = new ArrayList<>();
        String lowerQuery = query.toLowerCase().trim();
        for (BankItem bank : allBanks) {
            if (bank.name.toLowerCase().contains(lowerQuery)
                    || bank.badge.toLowerCase().contains(lowerQuery)) {
                filtered.add(bank);
            }
        }
        bankAdapter.updateList(filtered);
    }

    private void processPayment(String paymentSource) {
        binding.btnPayUpi.setEnabled(false);

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
            PaymentHelper.executeAddMoney(uid, userName, amount, paymentSource,
                    new PaymentHelper.PaymentCallback() {
                        @Override
                        public void onSuccess(String transactionId, double amountAdded) {
                            if (processingDialog != null && processingDialog.isShowing())
                                processingDialog.dismiss();
                            showSuccessDialog(transactionId, amountAdded);
                        }

                        @Override
                        public void onFailure(String errorMessage) {
                            if (processingDialog != null && processingDialog.isShowing())
                                processingDialog.dismiss();
                            binding.btnPayUpi.setEnabled(true);
                            Toast.makeText(UpiBankActivity.this,
                                    "Transaction failed: " + errorMessage, Toast.LENGTH_LONG).show();
                        }
                    });
        }, 1500);
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
        TextView tvTxId   = dialogView.findViewById(R.id.tvSuccessTxId);
        Button btnDone    = dialogView.findViewById(R.id.btnSuccessDone);

        tvAmount.setText(CurrencyHelper.formatRupees(amountAdded));
        tvTxId.setText("Tx ID: " + transactionId);

        btnDone.setOnClickListener(v -> {
            successDialog.dismiss();
            Intent intent = new Intent(UpiBankActivity.this, HomeActivity.class);
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
        if (processingDialog != null && processingDialog.isShowing()) processingDialog.dismiss();
        if (successDialog    != null && successDialog.isShowing())    successDialog.dismiss();
        if (bankAccountsRef  != null && bankAccountsListener != null) {
            bankAccountsRef.removeEventListener(bankAccountsListener);
        }
    }

    // ── Inner Bank RecyclerView Adapter ────────────────────────────────────

    private static class BankAdapter extends RecyclerView.Adapter<BankAdapter.BankViewHolder> {

        interface OnBankClickListener { void onBankClick(BankItem bank); }

        private final List<BankItem> banks = new ArrayList<>();
        private final OnBankClickListener listener;

        BankAdapter(List<BankItem> initial, OnBankClickListener listener) {
            this.banks.addAll(initial);
            this.listener = listener;
        }

        void updateList(List<BankItem> newBanks) {
            banks.clear();
            banks.addAll(newBanks);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public BankViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemBankBinding b = ItemBankBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new BankViewHolder(b);
        }

        @Override
        public void onBindViewHolder(@NonNull BankViewHolder holder, int position) {
            BankItem bank = banks.get(position);
            holder.binding.tvBankName.setText(bank.name);
            holder.binding.tvBankBadge.setText(bank.badge);

            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setShape(GradientDrawable.OVAL);
            badgeBg.setColor(bank.color);
            holder.binding.tvBankBadge.setBackground(badgeBg);

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onBankClick(bank);
            });
        }

        @Override public int getItemCount() { return banks.size(); }

        static class BankViewHolder extends RecyclerView.ViewHolder {
            final ItemBankBinding binding;
            BankViewHolder(ItemBankBinding b) { super(b.getRoot()); this.binding = b; }
        }
    }
}

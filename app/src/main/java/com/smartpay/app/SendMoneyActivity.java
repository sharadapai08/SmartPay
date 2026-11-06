package com.smartpay.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.smartpay.app.databinding.ActivitySendMoneyBinding;

/**
 * SendMoneyActivity - Enter recipient and amount.
 *
 * Implements:
 * - Recipient lookup by mobile number or UPI ID via TransferRepository.
 * - Auto-fill and lock recipient name.
 * - Validation: self-transfer check, unregistered check, amount > 0, amount <= balance,
 *   amount <= 50,000 demo cap.
 * - Real-time sender balance display.
 * - Contact selector shortcut for demo testing.
 * - Navigation to ConfirmSendActivity.
 */
public class SendMoneyActivity extends BaseActivity {

    public static final String EXTRA_RECEIVER_UID = "extra_receiver_uid";
    public static final String EXTRA_RECEIVER_NAME = "extra_receiver_name";
    public static final String EXTRA_RECEIVER_UPI = "extra_receiver_upi";
    public static final String EXTRA_AMOUNT = "extra_amount";
    public static final String EXTRA_NOTE = "extra_note";

    private ActivitySendMoneyBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;

    private DatabaseReference userRef;
    private ValueEventListener userBalanceListener;

    private double availableBalance = 0.0;
    private String currentUserPhone = "";   // cached own phone for instant self-check
    private String currentUserUpi = "";     // cached own UPI for instant self-check
    private String verifiedReceiverUid = null;
    private String verifiedReceiverName = null;
    private String verifiedReceiverUpi = null;
    private String lastLookedUpQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySendMoneyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, "Session expired. Please log in.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupUI();
        setupBalanceListener();
        AvatarHelper.bindCurrentUser(binding.ivToolbarAvatar);
    }

    private void setupUI() {
        // Back and Cancel buttons
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnCancel.setOnClickListener(v -> finish());

        // More options button
        binding.btnMore.setOnClickListener(this::showPopupMenu);

        // Quick contacts shortcut
        binding.btnContacts.setOnClickListener(v -> showContactsDialog());

        // Recipient query text watcher to reset verification if query changes
        binding.etUpiId.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String current = s != null ? s.toString().trim() : "";
                if (!current.equals(lastLookedUpQuery)) {
                    verifiedReceiverUid = null;
                    verifiedReceiverName = null;
                    verifiedReceiverUpi = null;
                    binding.etRecipientName.setText("");
                    binding.tvLookupStatus.setVisibility(View.GONE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Auto trigger lookup when user types 10-digit mobile or valid UPI
                String input = s != null ? s.toString().trim() : "";
                if (isSelf(input)) {
                    showSelfTransferError();
                    return;
                }
                if (input.matches("\\d{10}") || (input.contains("@") && input.length() > 5)) {
                    performLookup(input, false);
                }
            }
        });

        // Trigger lookup when focus is lost
        binding.etUpiId.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String input = binding.etUpiId.getText().toString().trim();
                if (!TextUtils.isEmpty(input)) {
                    performLookup(input, false);
                }
            }
        });

        // SEND MONEY button click
        binding.btnSendMoney.setOnClickListener(v -> handleSendMoneyClick());
    }

    private void showPopupMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.menu_send_options, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_add_money) {
                startActivity(new Intent(SendMoneyActivity.this, AddMoneyActivity.class));
                return true;
            } else if (id == R.id.menu_receive_money) {
                startActivity(new Intent(SendMoneyActivity.this, ReceiveMoneyActivity.class));
                return true;
            } else if (id == R.id.menu_scan_qr) {
                startActivity(new Intent(SendMoneyActivity.this, QrScannerActivity.class));
                return true;
            } else if (id == R.id.menu_transactions) {
                startActivity(new Intent(SendMoneyActivity.this, TransactionsActivity.class));
                return true;
            } else if (id == R.id.menu_profile) {
                startActivity(new Intent(SendMoneyActivity.this, ProfileActivity.class));
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void setupBalanceListener() {
        userRef = FirebaseRefs.getUserRef(currentUser.getUid());
        userBalanceListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                UserModel user = snapshot.getValue(UserModel.class);
                if (user != null) {
                    availableBalance = user.getBalance();
                    // Cache own identifiers for immediate self-transfer detection
                    if (user.getPhone() != null) currentUserPhone = user.getPhone().trim();
                    if (user.getUpiId() != null) currentUserUpi = user.getUpiId().trim();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Ignore cancel
            }
        };
        userRef.addValueEventListener(userBalanceListener);
    }

    /**
     * Looks up recipient in Firebase Realtime Database.
     */
    private void performLookup(String query, boolean proceedOnSuccess) {
        if (TextUtils.isEmpty(query)) {
            return;
        }

        if (query.equals(lastLookedUpQuery) && verifiedReceiverUid != null) {
            if (proceedOnSuccess) {
                proceedToConfirmation();
            }
            return;
        }

        binding.pbLookup.setVisibility(View.VISIBLE);
        binding.tvLookupStatus.setVisibility(View.VISIBLE);
        binding.tvLookupStatus.setText("Verifying recipient…");
        binding.tvLookupStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));

        TransferRepository.lookupReceiver(query, currentUser.getUid(), new TransferRepository.LookupCallback() {
            @Override
            public void onSuccess(String receiverUid, UserModel receiver) {
                if (isFinishing() || isDestroyed()) return;

                binding.pbLookup.setVisibility(View.GONE);
                verifiedReceiverUid = receiverUid;
                verifiedReceiverName = receiver.getName();
                verifiedReceiverUpi = receiver.getUpiId() != null ? receiver.getUpiId() : receiver.getPhone();
                lastLookedUpQuery = query;

                binding.etRecipientName.setText(verifiedReceiverName);
                binding.tvLookupStatus.setText("✓ " + verifiedReceiverName + " (" + verifiedReceiverUpi + ")");
                binding.tvLookupStatus.setTextColor(ContextCompat.getColor(SendMoneyActivity.this, R.color.success_green));
                binding.tvLookupStatus.setVisibility(View.VISIBLE);

                if (proceedOnSuccess) {
                    proceedToConfirmation();
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                if (isFinishing() || isDestroyed()) return;

                binding.pbLookup.setVisibility(View.GONE);
                verifiedReceiverUid = null;
                verifiedReceiverName = null;
                verifiedReceiverUpi = null;
                lastLookedUpQuery = "";

                binding.etRecipientName.setText("");
                binding.tvLookupStatus.setText(errorMessage);
                binding.tvLookupStatus.setTextColor(ContextCompat.getColor(SendMoneyActivity.this, R.color.error));
                binding.tvLookupStatus.setVisibility(View.VISIBLE);

                if (errorMessage != null && errorMessage.contains("yourself")) {
                    Toast.makeText(SendMoneyActivity.this,
                            "You cannot send money to yourself.",
                            Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void handleSendMoneyClick() {
        String query = binding.etUpiId.getText().toString().trim();
        if (TextUtils.isEmpty(query)) {
            binding.etUpiId.setError("Enter recipient's mobile or UPI ID");
            binding.etUpiId.requestFocus();
            return;
        }

        // Immediate self-transfer block before any network lookup
        if (isSelf(query)) {
            showSelfTransferError();
            return;
        }

        // Amount validation
        String amountStr = binding.etAmount.getText().toString().trim().replace("₹", "").trim();
        if (TextUtils.isEmpty(amountStr)) {
            binding.etAmount.setError("Enter amount to send");
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

        if (amount > availableBalance) {
            binding.etAmount.setError("Insufficient balance (" + CurrencyHelper.formatRupees(availableBalance) + ")");
            Toast.makeText(this, "Insufficient balance! Please add money to your wallet.", Toast.LENGTH_SHORT).show();
            binding.etAmount.requestFocus();
            return;
        }

        // If recipient not yet verified, run lookup and proceed
        if (verifiedReceiverUid == null || !query.equals(lastLookedUpQuery)) {
            performLookup(query, true);
        } else {
            proceedToConfirmation();
        }
    }

    private void proceedToConfirmation() {
        String amountStr = binding.etAmount.getText().toString().trim().replace("₹", "").trim();
        double amount = Double.parseDouble(amountStr);
        String note = binding.etNote.getText().toString().trim();

        Intent intent = new Intent(SendMoneyActivity.this, ConfirmSendActivity.class);
        intent.putExtra(EXTRA_RECEIVER_UID, verifiedReceiverUid);
        intent.putExtra(EXTRA_RECEIVER_NAME, verifiedReceiverName);
        intent.putExtra(EXTRA_RECEIVER_UPI, verifiedReceiverUpi);
        intent.putExtra(EXTRA_AMOUNT, amount);
        intent.putExtra(EXTRA_NOTE, note);
        startActivity(intent);
    }

    /**
     * Returns true if the given query matches the current user's own phone number or UPI ID.
     * Used for an immediate, no-network self-transfer block.
     */
    private boolean isSelf(String query) {
        if (TextUtils.isEmpty(query)) return false;
        String q = query.trim();
        if (!TextUtils.isEmpty(currentUserPhone) && currentUserPhone.equals(q)) return true;
        if (!TextUtils.isEmpty(currentUserUpi) && currentUserUpi.equalsIgnoreCase(q)) return true;
        // Also catch the generated UPI form of own phone (e.g. "9876543210@smartpay")
        if (!TextUtils.isEmpty(currentUserPhone)) {
            String ownGeneratedUpi = FirebaseConstants.generateUpiId(currentUserPhone);
            if (ownGeneratedUpi.equalsIgnoreCase(q)) return true;
        }
        return false;
    }

    /** Shows error UI and toast for a self-transfer attempt. */
    private void showSelfTransferError() {
        binding.pbLookup.setVisibility(View.GONE);
        verifiedReceiverUid = null;
        verifiedReceiverName = null;
        verifiedReceiverUpi = null;
        lastLookedUpQuery = "";
        binding.etRecipientName.setText("");
        binding.tvLookupStatus.setText("You cannot send money to yourself.");
        binding.tvLookupStatus.setTextColor(ContextCompat.getColor(this, R.color.error));
        binding.tvLookupStatus.setVisibility(View.VISIBLE);
        Toast.makeText(this, "You cannot send money to yourself.", Toast.LENGTH_LONG).show();
    }

    /**
     * Dialog providing quick demo contacts for seamless testing on emulator.
     */
    private void showContactsDialog() {
        String[] contacts = {
                "Priya Sharma (9876543210)",
                "Rahul Kumar (9123456780)",
                "Anjali Verma (9988776655)",
                "Vikram Singh (9012345678)"
        };
        String[] upiIds = {
                "9876543210@smartpay",
                "9123456780@smartpay",
                "9988776655@smartpay",
                "9012345678@smartpay"
        };

        new AlertDialog.Builder(this)
                .setTitle("Select Contact")
                .setItems(contacts, (dialog, which) -> {
                    binding.etUpiId.setText(upiIds[which]);
                    performLookup(upiIds[which], false);
                })
                .setNegativeButton("Cancel", null)
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
        if (userRef != null && userBalanceListener != null) {
            userRef.removeEventListener(userBalanceListener);
            userBalanceListener = null;
        }
    }
}

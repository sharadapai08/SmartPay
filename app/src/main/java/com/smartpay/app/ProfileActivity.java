package com.smartpay.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.smartpay.app.databinding.ActivityProfileBinding;
import com.smartpay.app.databinding.ItemSavedBankAccountBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * ProfileActivity — shows live profile data and a scrollable list of linked bank accounts.
 */
public class ProfileActivity extends BaseActivity {

    private ActivityProfileBinding binding;
    private DatabaseReference userRef;
    private ValueEventListener userListener;
    private DatabaseReference bankAccountsRef;
    private ValueEventListener bankAccountsListener;

    private BankAccountAdapter bankAdapter;
    private String currentUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) { finish(); return; }
        currentUid = currentUser.getUid();

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnMore.setOnClickListener(this::showPopupMenu);
        binding.btnEditProfile.setOnClickListener(v ->
                startActivity(new Intent(ProfileActivity.this, EditProfileActivity.class)));

        binding.btnAddBankAccount.setOnClickListener(v ->
                startActivity(new Intent(ProfileActivity.this, AddBankAccountActivity.class)));

        setupBankAccountsRecycler();
        loadUserProfile();
        loadBankAccounts();
    }

    private void setupBankAccountsRecycler() {
        bankAdapter = new BankAccountAdapter(account -> {
            // Confirm deletion
            new AlertDialog.Builder(this)
                    .setTitle("Remove Account")
                    .setMessage("Remove " + account.getBankName() + " ("
                            + account.getMaskedAccountNumber() + ") from your linked accounts?")
                    .setPositiveButton("REMOVE", (d, w) -> deleteAccount(account))
                    .setNegativeButton("CANCEL", null)
                    .show();
        });
        binding.rvBankAccounts.setLayoutManager(new LinearLayoutManager(this));
        binding.rvBankAccounts.setAdapter(bankAdapter);
        binding.rvBankAccounts.setNestedScrollingEnabled(false);
    }

    private void deleteAccount(BankAccountModel account) {
        if (account.getAccountId() == null) return;
        FirebaseRefs.getBankAccountsRef(currentUid).child(account.getAccountId()).removeValue()
                .addOnSuccessListener(u ->
                        Toast.makeText(this, "Account removed.", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to remove account.", Toast.LENGTH_SHORT).show());
    }

    private void showPopupMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.menu_profile_options, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_add_money) {
                startActivity(new Intent(ProfileActivity.this, AddMoneyActivity.class));
                return true;
            } else if (id == R.id.menu_send_money) {
                startActivity(new Intent(ProfileActivity.this, SendMoneyActivity.class));
                return true;
            } else if (id == R.id.menu_receive_money) {
                startActivity(new Intent(ProfileActivity.this, ReceiveMoneyActivity.class));
                return true;
            } else if (id == R.id.menu_scan_qr) {
                startActivity(new Intent(ProfileActivity.this, QrScannerActivity.class));
                return true;
            } else if (id == R.id.menu_transactions) {
                startActivity(new Intent(ProfileActivity.this, TransactionsActivity.class));
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void loadUserProfile() {
        userRef = FirebaseRefs.getUserRef(currentUid);
        userListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                UserModel user = snapshot.getValue(UserModel.class);
                if (user == null) return;

                binding.tvName.setText(user.getName() != null ? user.getName() : "User");
                binding.tvEmail.setText(user.getEmail() != null ? user.getEmail() : "N/A");
                binding.tvPhone.setText(user.getPhone() != null ? user.getPhone() : "N/A");
                binding.tvUpiId.setText(user.getUpiId() != null ? user.getUpiId() : "N/A");

                String photo = user.getPhotoBase64();
                if (photo != null && !photo.isEmpty()) {
                    byte[] bytes = Base64.decode(photo, Base64.NO_WRAP);
                    binding.ivAvatar.setPadding(0, 0, 0, 0);
                    Glide.with(ProfileActivity.this)
                            .load(bytes)
                            .skipMemoryCache(true)
                            .circleCrop()
                            .placeholder(R.drawable.ic_person)
                            .error(R.drawable.ic_person)
                            .into(binding.ivAvatar);
                } else {
                    binding.ivAvatar.setPadding(22, 22, 22, 22);
                    binding.ivAvatar.setImageResource(R.drawable.ic_person);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        userRef.addValueEventListener(userListener);
    }

    private void loadBankAccounts() {
        bankAccountsRef = FirebaseRefs.getBankAccountsRef(currentUid);
        bankAccountsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<BankAccountModel> accounts = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    BankAccountModel acc = child.getValue(BankAccountModel.class);
                    if (acc != null) {
                        if (acc.getAccountId() == null) acc.setAccountId(child.getKey());
                        accounts.add(acc);
                    }
                }
                bankAdapter.setAccounts(accounts);
                binding.tvNoBankAccounts.setVisibility(accounts.isEmpty() ? View.VISIBLE : View.GONE);
                binding.rvBankAccounts.setVisibility(accounts.isEmpty() ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        bankAccountsRef.addValueEventListener(bankAccountsListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (userRef != null && userListener != null) {
            userRef.removeEventListener(userListener);
        }
        if (bankAccountsRef != null && bankAccountsListener != null) {
            bankAccountsRef.removeEventListener(bankAccountsListener);
        }
    }

    // ── Inner RecyclerView Adapter ──────────────────────────────────────────

    private static class BankAccountAdapter
            extends RecyclerView.Adapter<BankAccountAdapter.VH> {

        interface OnDeleteListener { void onDelete(BankAccountModel account); }

        private final List<BankAccountModel> items = new ArrayList<>();
        private final OnDeleteListener deleteListener;

        BankAccountAdapter(OnDeleteListener l) { this.deleteListener = l; }

        void setAccounts(List<BankAccountModel> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemSavedBankAccountBinding b = ItemSavedBankAccountBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new VH(b);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            BankAccountModel acc = items.get(position);
            holder.binding.tvSavedBankName.setText(acc.getBankName());
            holder.binding.tvSavedAccountHolder.setText(acc.getAccountHolder());
            holder.binding.tvSavedAccountNumber.setText(acc.getMaskedAccountNumber());
            holder.binding.tvSavedIfsc.setText(acc.getIfsc());
            holder.binding.btnDeleteAccount.setOnClickListener(v -> {
                if (deleteListener != null) deleteListener.onDelete(acc);
            });
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final ItemSavedBankAccountBinding binding;
            VH(ItemSavedBankAccountBinding b) { super(b.getRoot()); this.binding = b; }
        }
    }
}

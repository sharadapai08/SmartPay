package com.smartpay.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.smartpay.app.databinding.ActivityTransactionsBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * TransactionsActivity — Transaction history list.
 *
 * Loads via two simultaneous orderByChild queries on the transactions node:
 *   • orderByChild("senderId").equalTo(currentUid)   → senderTxMap
 *   • orderByChild("receiverId").equalTo(currentUid) → receiverTxMap
 *
 * Both maps are merged once both queries have fired (senderQueryLoaded &&
 * receiverQueryLoaded). TYPE_RECEIVED records come from the receiver query
 * and show the blue down arrow in the adapter.
 */
public class TransactionsActivity extends BaseActivity {

    private ActivityTransactionsBinding binding;
    private TransactionAdapter adapter;

    private Query senderQuery;
    private ValueEventListener senderListener;
    private Query receiverQuery;
    private ValueEventListener receiverListener;

    private final Map<String, TransactionModel> senderTxMap   = new HashMap<>();
    private final Map<String, TransactionModel> receiverTxMap = new HashMap<>();
    private boolean senderQueryLoaded   = false;
    private boolean receiverQueryLoaded = false;

    private String currentUid;
    private final List<TransactionModel> masterTransactions    = new ArrayList<>();
    private final List<TransactionModel> displayedTransactions = new ArrayList<>();

    private enum FilterTab { ALL, SENT, RECEIVED, FAILED }
    private FilterTab currentTab       = FilterTab.ALL;
    private String currentSearchQuery  = "";
    private int    currentSortOption   = 0; // 0=Newest, 1=Oldest, 2=High→Low, 3=Low→High

    // ──────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTransactionsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            currentUid = user.getUid();
        }

        setupViews();
        setupTabs();
        setupSearch();
        loadTransactions();
    }

    // ── UI setup ──────────────────────────────────────────────────────────────

    private void setupViews() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnMore.setOnClickListener(this::showPopupMenu);

        binding.rvTransactions.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TransactionAdapter(this, currentUid);
        binding.rvTransactions.setAdapter(adapter);

        binding.btnRetry.setOnClickListener(v -> loadTransactions());
        binding.btnFilter.setOnClickListener(v -> showSortDialog());
    }

    private void showPopupMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.menu_transactions_options, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_add_money) {
                startActivity(new Intent(TransactionsActivity.this, AddMoneyActivity.class));
            } else if (id == R.id.menu_send_money) {
                startActivity(new Intent(TransactionsActivity.this, SendMoneyActivity.class));
            } else if (id == R.id.menu_receive_money) {
                startActivity(new Intent(TransactionsActivity.this, ReceiveMoneyActivity.class));
            } else if (id == R.id.menu_scan_qr) {
                startActivity(new Intent(TransactionsActivity.this, QrScannerActivity.class));
            } else if (id == R.id.menu_profile) {
                startActivity(new Intent(TransactionsActivity.this, ProfileActivity.class));
            } else {
                return false;
            }
            return true;
        });
        popup.show();
    }

    private void setupTabs() {
        binding.tabAll.setOnClickListener(v      -> selectTab(FilterTab.ALL));
        binding.tabSent.setOnClickListener(v     -> selectTab(FilterTab.SENT));
        binding.tabReceived.setOnClickListener(v -> selectTab(FilterTab.RECEIVED));
        binding.tabFailed.setOnClickListener(v   -> selectTab(FilterTab.FAILED));
        updateTabStyles();
    }

    private void selectTab(FilterTab tab) {
        if (currentTab == tab) return;
        currentTab = tab;
        updateTabStyles();
        applyFiltersAndSearch();
    }

    private void updateTabStyles() {
        setTabStyle(binding.tabAll,      currentTab == FilterTab.ALL);
        setTabStyle(binding.tabSent,     currentTab == FilterTab.SENT);
        setTabStyle(binding.tabReceived, currentTab == FilterTab.RECEIVED);
        setTabStyle(binding.tabFailed,   currentTab == FilterTab.FAILED);
    }

    private void setTabStyle(TextView tab, boolean selected) {
        if (selected) {
            tab.setBackgroundResource(R.drawable.bg_filter_selected);
            tab.setTextColor(ContextCompat.getColor(this, android.R.color.white));
        } else {
            tab.setBackgroundResource(R.drawable.bg_filter_unselected);
            tab.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        }
    }

    private void setupSearch() {
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                currentSearchQuery = s != null ? s.toString().trim().toLowerCase(Locale.getDefault()) : "";
                applyFiltersAndSearch();
            }
        });
    }

    // ── Firebase loading — dual-query approach ────────────────────────────────

    private void loadTransactions() {
        if (currentUid == null) {
            binding.progressBar.setVisibility(View.GONE);
            binding.layoutEmptyState.setVisibility(View.VISIBLE);
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.layoutEmptyState.setVisibility(View.GONE);
        binding.layoutErrorState.setVisibility(View.GONE);
        binding.rvTransactions.setVisibility(View.GONE);

        removeListeners();
        senderQueryLoaded   = false;
        receiverQueryLoaded = false;
        senderTxMap.clear();
        receiverTxMap.clear();

        // Query 1 — transactions where this user is the sender
        senderQuery = FirebaseRefs.getTransactionsRef()
                .orderByChild("senderId").equalTo(currentUid);

        senderListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                senderTxMap.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    TransactionModel tx = child.getValue(TransactionModel.class);
                    if (tx != null) {
                        if (tx.getTransactionId() == null || tx.getTransactionId().isEmpty()) {
                            tx.setTransactionId(child.getKey());
                        }
                        senderTxMap.put(child.getKey(), tx);
                    }
                }
                senderQueryLoaded = true;
                onQueriesUpdated();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Mark loaded so receiverQuery result can still render
                senderQueryLoaded = true;
                onQueriesUpdated();
            }
        };

        // Query 2 — transactions where this user is the receiver (TYPE_RECEIVED lives here)
        receiverQuery = FirebaseRefs.getTransactionsRef()
                .orderByChild("receiverId").equalTo(currentUid);

        receiverListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                receiverTxMap.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    TransactionModel tx = child.getValue(TransactionModel.class);
                    if (tx != null) {
                        if (tx.getTransactionId() == null || tx.getTransactionId().isEmpty()) {
                            tx.setTransactionId(child.getKey());
                        }
                        receiverTxMap.put(child.getKey(), tx);
                    }
                }
                receiverQueryLoaded = true;
                onQueriesUpdated();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Mark loaded so senderQuery result can still render
                receiverQueryLoaded = true;
                onQueriesUpdated();
            }
        };

        senderQuery.addValueEventListener(senderListener);
        receiverQuery.addValueEventListener(receiverListener);
    }

    /**
     * Called by each query listener. Only proceeds once both have fired,
     * ensuring the merged list is complete before rendering.
     */
    private void onQueriesUpdated() {
        if (!senderQueryLoaded || !receiverQueryLoaded) {
            return; // wait for the other query
        }

        masterTransactions.clear();

        // Merge both maps; receiverTxMap wins on key collision (different keys in practice)
        Map<String, TransactionModel> allMap = new HashMap<>(senderTxMap);
        allMap.putAll(receiverTxMap);

        for (TransactionModel tx : allMap.values()) {
            String type   = tx.getType();
            String status = tx.getStatus();
            boolean isSender   = currentUid.equals(tx.getSenderId());
            boolean isReceiver = currentUid.equals(tx.getReceiverId());

            if (FirebaseConstants.TYPE_ADD_MONEY.equalsIgnoreCase(type)) {
                // Sender == receiver for top-ups
                if (isSender || isReceiver) masterTransactions.add(tx);

            } else if (FirebaseConstants.STATUS_FAILED.equalsIgnoreCase(status)) {
                // Only the initiator sees failed records
                if (isSender) masterTransactions.add(tx);

            } else if (FirebaseConstants.TYPE_SENT.equalsIgnoreCase(type)
                    || FirebaseConstants.TYPE_QR_PAYMENT.equalsIgnoreCase(type)) {
                if (isSender) masterTransactions.add(tx);

            } else if (FirebaseConstants.TYPE_RECEIVED.equalsIgnoreCase(type)) {
                // TYPE_RECEIVED record — only the receiver sees it
                if (isReceiver) masterTransactions.add(tx);

            } else {
                // Fallback
                if (isSender || isReceiver) masterTransactions.add(tx);
            }
        }

        binding.progressBar.setVisibility(View.GONE);
        sortMasterTransactions();
        applyFiltersAndSearch();
    }

    private void removeListeners() {
        if (senderQuery != null && senderListener != null) {
            senderQuery.removeEventListener(senderListener);
            senderListener = null;
        }
        if (receiverQuery != null && receiverListener != null) {
            receiverQuery.removeEventListener(receiverListener);
            receiverListener = null;
        }
    }

    // ── Sorting & filtering ───────────────────────────────────────────────────

    private void sortMasterTransactions() {
        switch (currentSortOption) {
            case 1:
                Collections.sort(masterTransactions, (a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));
                break;
            case 2:
                Collections.sort(masterTransactions, (a, b) -> Double.compare(b.getAmount(), a.getAmount()));
                break;
            case 3:
                Collections.sort(masterTransactions, (a, b) -> Double.compare(a.getAmount(), b.getAmount()));
                break;
            default: // 0 — newest first
                Collections.sort(masterTransactions, (a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
                break;
        }
    }

    private void applyFiltersAndSearch() {
        displayedTransactions.clear();
        for (TransactionModel tx : masterTransactions) {
            if (matchesTab(tx) && matchesSearch(tx)) {
                displayedTransactions.add(tx);
            }
        }

        adapter.setTransactions(displayedTransactions);

        if (displayedTransactions.isEmpty()) {
            binding.rvTransactions.setVisibility(View.GONE);
            binding.layoutEmptyState.setVisibility(View.VISIBLE);
            binding.layoutErrorState.setVisibility(View.GONE);

            if (!currentSearchQuery.isEmpty()) {
                binding.tvEmptyTitle.setText("No matching transactions");
                binding.tvEmptySubtitle.setText("No results found for \"" + currentSearchQuery + "\"");
            } else if (currentTab != FilterTab.ALL) {
                binding.tvEmptyTitle.setText("No " + currentTab.name().toLowerCase(Locale.getDefault()) + " transactions");
                binding.tvEmptySubtitle.setText("You have no transactions in this category.");
            } else {
                binding.tvEmptyTitle.setText("No transactions yet");
                binding.tvEmptySubtitle.setText("Your transaction history will appear here.");
            }
        } else {
            binding.layoutEmptyState.setVisibility(View.GONE);
            binding.layoutErrorState.setVisibility(View.GONE);
            binding.rvTransactions.setVisibility(View.VISIBLE);
        }
    }

    private boolean matchesTab(TransactionModel tx) {
        String type   = tx.getType()   != null ? tx.getType()   : "";
        String status = tx.getStatus() != null ? tx.getStatus() : "";
        switch (currentTab) {
            case SENT:
                return (FirebaseConstants.TYPE_SENT.equalsIgnoreCase(type)
                        || FirebaseConstants.TYPE_QR_PAYMENT.equalsIgnoreCase(type))
                        && !FirebaseConstants.STATUS_FAILED.equalsIgnoreCase(status);
            case RECEIVED:
                return (FirebaseConstants.TYPE_RECEIVED.equalsIgnoreCase(type)
                        || FirebaseConstants.TYPE_ADD_MONEY.equalsIgnoreCase(type))
                        && !FirebaseConstants.STATUS_FAILED.equalsIgnoreCase(status);
            case FAILED:
                return FirebaseConstants.STATUS_FAILED.equalsIgnoreCase(status);
            default:
                return true;
        }
    }

    private boolean matchesSearch(TransactionModel tx) {
        if (currentSearchQuery.isEmpty()) return true;
        String q = currentSearchQuery;
        if (contains(tx.getSenderName(),   q)) return true;
        if (contains(tx.getReceiverName(), q)) return true;
        if (contains(tx.getNote(),         q)) return true;
        if (contains(tx.getType(),         q)) return true;
        if (contains(tx.getTransactionId(),q)) return true;
        if (contains(tx.getSenderUpi(),    q)) return true;
        if (contains(tx.getReceiverUpi(),  q)) return true;
        return String.valueOf(tx.getAmount()).contains(q);
    }

    private boolean contains(String field, String query) {
        return field != null && field.toLowerCase(Locale.getDefault()).contains(query);
    }

    private void showSortDialog() {
        String[] options = {"Newest First", "Oldest First", "Amount: High to Low", "Amount: Low to High"};
        new AlertDialog.Builder(this)
                .setTitle("Sort Transactions")
                .setSingleChoiceItems(options, currentSortOption, (dialog, which) -> {
                    currentSortOption = which;
                    sortMasterTransactions();
                    applyFiltersAndSearch();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onDestroy() {
        super.onDestroy();
        removeListeners();
    }
}

package com.smartpay.app;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Query;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * TransferRepository - Centralized business and data layer for Send Money & QR transfers.
 *
 * Responsibilities:
 * 1. Recipient lookup by phone number or UPI ID via Firebase queries.
 * 2. Self-transfer prevention.
 * 3. Server-side balance verification and atomic deduction via runTransaction.
 * 4. Multi-path atomic commit for receiver credit, dual transactions (SENT/QR_PAYMENT + RECEIVED),
 *    with complete sender & receiver UPI identifiers, and receiver notification.
 * 5. Rollback and failed transaction logging on any step failure.
 */
public final class TransferRepository {

    private TransferRepository() {
        // Prevent direct instantiation
    }

    public interface LookupCallback {
        void onSuccess(String receiverUid, UserModel receiver);
        void onFailure(String errorMessage);
    }

    public interface TransferCallback {
        void onSuccess(String transactionId, double amount);
        void onFailure(String errorMessage);
    }

    /**
     * Looks up a recipient in the 'users' node by mobile number or UPI ID.
     */
    public static void lookupReceiver(String query, String currentUserId, LookupCallback callback) {
        if (callback == null) return;

        if (query == null || query.trim().isEmpty()) {
            callback.onFailure("Please enter a mobile number or UPI ID.");
            return;
        }

        final String cleanQuery = query.trim();

        // 1. Try querying by 'phone'
        Query phoneQuery = FirebaseRefs.getUsersRef().orderByChild("phone").equalTo(cleanQuery);
        phoneQuery.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && snapshot.getChildrenCount() > 0) {
                    for (DataSnapshot child : snapshot.getChildren()) {
                        String receiverUid = child.getKey();
                        if (receiverUid != null && receiverUid.equals(currentUserId)) {
                            callback.onFailure("You cannot send money to yourself.");
                            return;
                        }
                        UserModel receiver = child.getValue(UserModel.class);
                        if (receiver != null) {
                            callback.onSuccess(receiverUid, receiver);
                            return;
                        }
                    }
                }

                // 2. If not found by phone, try querying by 'upiId'
                lookupByUpiId(cleanQuery, currentUserId, callback);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onFailure("Lookup cancelled: " + error.getMessage());
            }
        });
    }

    private static void lookupByUpiId(String upiQuery, String currentUserId, LookupCallback callback) {
        Query upiQueryRef = FirebaseRefs.getUsersRef().orderByChild("upiId").equalTo(upiQuery);
        upiQueryRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && snapshot.getChildrenCount() > 0) {
                    for (DataSnapshot child : snapshot.getChildren()) {
                        String receiverUid = child.getKey();
                        if (receiverUid != null && receiverUid.equals(currentUserId)) {
                            callback.onFailure("You cannot send money to yourself.");
                            return;
                        }
                        UserModel receiver = child.getValue(UserModel.class);
                        if (receiver != null) {
                            callback.onSuccess(receiverUid, receiver);
                            return;
                        }
                    }
                }

                // 3. If query doesn't contain '@', try checking with @smartpay appended
                if (!upiQuery.contains("@")) {
                    String candidateUpi = FirebaseConstants.generateUpiId(upiQuery);
                    Query smartpayQuery = FirebaseRefs.getUsersRef().orderByChild("upiId").equalTo(candidateUpi);
                    smartpayQuery.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (snapshot.exists() && snapshot.getChildrenCount() > 0) {
                                for (DataSnapshot child : snapshot.getChildren()) {
                                    String receiverUid = child.getKey();
                                    if (receiverUid != null && receiverUid.equals(currentUserId)) {
                                        callback.onFailure("You cannot send money to yourself.");
                                        return;
                                    }
                                    UserModel receiver = child.getValue(UserModel.class);
                                    if (receiver != null) {
                                        callback.onSuccess(receiverUid, receiver);
                                        return;
                                    }
                                }
                            }
                            callback.onFailure("User not registered on SmartPay.");
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            callback.onFailure("Lookup cancelled: " + error.getMessage());
                        }
                    });
                } else {
                    callback.onFailure("User not registered on SmartPay.");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onFailure("Lookup cancelled: " + error.getMessage());
            }
        });
    }

    /**
     * Overload defaulting transaction type to TYPE_SENT and auto sender UPI.
     */
    public static void executeTransfer(
            @NonNull String senderUid,
            String senderName,
            @NonNull String receiverUid,
            String receiverName,
            String receiverUpi,
            double amount,
            String note,
            @NonNull TransferCallback callback) {
        executeTransfer(senderUid, senderName, "", receiverUid, receiverName, receiverUpi, amount, note, FirebaseConstants.TYPE_SENT, callback);
    }

    /**
     * Overload defaulting sender UPI.
     */
    public static void executeTransfer(
            @NonNull String senderUid,
            String senderName,
            @NonNull String receiverUid,
            String receiverName,
            String receiverUpi,
            double amount,
            String note,
            String transactionType,
            @NonNull TransferCallback callback) {
        executeTransfer(senderUid, senderName, "", receiverUid, receiverName, receiverUpi, amount, note, transactionType, callback);
    }

    /**
     * Complete transfer execution with explicit sender & receiver UPI identifiers.
     */
    public static void executeTransfer(
            @NonNull String senderUid,
            String senderName,
            String senderUpi,
            @NonNull String receiverUid,
            String receiverName,
            String receiverUpi,
            double amount,
            String note,
            String transactionType,
            @NonNull TransferCallback callback) {

        if (senderUid.equals(receiverUid)) {
            callback.onFailure("You cannot send money to yourself.");
            return;
        }

        if (amount <= 0) {
            callback.onFailure("Transfer amount must be greater than zero.");
            return;
        }

        if (amount > FirebaseConstants.MAX_TRANSFER_AMOUNT) {
            callback.onFailure("Maximum transfer limit is ₹50,000.");
            return;
        }

        final String finalSenderName = (senderName != null && !senderName.trim().isEmpty()) ? senderName.trim() : "User";
        final String finalSenderUpi = (senderUpi != null && !senderUpi.trim().isEmpty()) ? senderUpi.trim() : "";
        final String finalReceiverName = (receiverName != null && !receiverName.trim().isEmpty()) ? receiverName.trim() : "SmartPay User";
        final String finalReceiverUpi = (receiverUpi != null && !receiverUpi.trim().isEmpty()) ? receiverUpi.trim() : "";
        final String finalNote = (note != null && !note.trim().isEmpty()) ? note.trim() : "Money transfer";
        final String senderTxType = (transactionType != null && !transactionType.trim().isEmpty()) ? transactionType.trim() : FirebaseConstants.TYPE_SENT;

        // Step 1: Atomic balance deduction on sender via transaction
        DatabaseReference senderBalanceRef = FirebaseRefs.getUserRef(senderUid).child("balance");
        senderBalanceRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                Double balance = currentData.getValue(Double.class);
                if (balance == null) {
                    balance = 0.0;
                }

                if (balance < amount) {
                    // Insufficient server-side balance: abort transaction
                    return Transaction.abort();
                }

                currentData.setValue(balance - amount);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(DatabaseError databaseError, boolean committed, DataSnapshot dataSnapshot) {
                if (databaseError != null || !committed) {
                    // Deduct failed or aborted due to insufficient balance
                    recordFailedTransaction(senderUid, finalSenderName, finalSenderUpi, receiverUid, finalReceiverName, finalReceiverUpi, amount, finalNote, senderTxType, "Insufficient balance or transaction aborted.");
                    callback.onFailure("Transfer failed: Insufficient balance on server.");
                    return;
                }

                // Step 2: Sender deducted successfully. Now credit receiver and write records
                completeTransferStep(senderUid, finalSenderName, finalSenderUpi, receiverUid, finalReceiverName, finalReceiverUpi, amount, finalNote, senderTxType, callback);
            }
        });
    }

    private static void completeTransferStep(
            String senderUid,
            String senderName,
            String senderUpi,
            String receiverUid,
            String receiverName,
            String receiverUpi,
            double amount,
            String note,
            String senderTxType,
            TransferCallback callback) {

        long timestamp = System.currentTimeMillis();

        // Unique IDs for transactions and notification
        String senderTxId = FirebaseRefs.getTransactionsRef().push().getKey();
        if (senderTxId == null) senderTxId = "tx_s_" + timestamp;

        String receiverTxId = FirebaseRefs.getTransactionsRef().push().getKey();
        if (receiverTxId == null) receiverTxId = "tx_r_" + (timestamp + 1);

        String notifId = FirebaseRefs.getNotificationsRef(receiverUid).push().getKey();
        if (notifId == null) notifId = "notif_" + timestamp;

        // Transaction record for Sender: type SENT or QR_PAYMENT
        TransactionModel senderTx = new TransactionModel(
                senderTxId,
                senderUid,
                receiverUid,
                senderName,
                receiverName,
                senderUpi,
                receiverUpi,
                amount,
                senderTxType,
                FirebaseConstants.STATUS_SUCCESS,
                note,
                timestamp
        );

        // Transaction record for Receiver: type RECEIVED
        TransactionModel receiverTx = new TransactionModel(
                receiverTxId,
                senderUid,
                receiverUid,
                senderName,
                receiverName,
                senderUpi,
                receiverUpi,
                amount,
                FirebaseConstants.TYPE_RECEIVED,
                FirebaseConstants.STATUS_SUCCESS,
                note,
                timestamp
        );

        // Receiver notification
        String notifMessage = senderTxType.equals(FirebaseConstants.TYPE_QR_PAYMENT)
                ? "Received " + CurrencyHelper.formatRupees(amount) + " via QR payment from " + senderName
                : "Received " + CurrencyHelper.formatRupees(amount) + " from " + senderName;

        NotificationModel notification = new NotificationModel(
                notifId,
                "Money received",
                notifMessage,
                amount,
                timestamp,
                false
        );

        // Multi-path atomic database update
        Map<String, Object> updates = new HashMap<>();
        // Credit receiver balance
        updates.put(FirebaseConstants.NODE_USERS + "/" + receiverUid + "/balance", ServerValue.increment(amount));
        // Add both transaction records
        updates.put(FirebaseConstants.NODE_TRANSACTIONS + "/" + senderTxId, senderTx);
        updates.put(FirebaseConstants.NODE_TRANSACTIONS + "/" + receiverTxId, receiverTx);
        // User transactions indexes
        updates.put(FirebaseConstants.NODE_USER_TRANSACTIONS + "/" + senderUid + "/" + senderTxId, true);
        updates.put(FirebaseConstants.NODE_USER_TRANSACTIONS + "/" + receiverUid + "/" + receiverTxId, true);
        // Notification for receiver
        updates.put(FirebaseConstants.NODE_NOTIFICATIONS + "/" + receiverUid + "/" + notifId, notification);

        final String finalSenderTxId = senderTxId;
        final String finalNotifMessage = notifMessage;

        FirebaseRefs.getRootRef().updateChildren(updates)
                .addOnSuccessListener(unused -> {
                    // Only post an OS notification if the currently logged-in user on this
                    // device is the receiver. This prevents the sender's device from showing
                    // a "money received" alert for a different account.
                    // (In a production app this would be a Cloud Function + FCM push.)
                    try {
                        android.content.Context appContext =
                                FirebaseRefs.getDatabase().getApp().getApplicationContext();
                        com.google.firebase.auth.FirebaseUser loggedIn =
                                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
                        if (loggedIn != null && loggedIn.getUid().equals(receiverUid)) {
                            NotificationHelper.postPaymentReceived(
                                    appContext,
                                    "Money Received",
                                    finalNotifMessage);
                        }
                    } catch (Exception ignored) {
                        // Never let a notification failure break the transfer callback
                    }
                    callback.onSuccess(finalSenderTxId, amount);
                })
                .addOnFailureListener(e -> {
                    // Credit or write failed! Rollback sender's balance deduction
                    rollbackSenderBalance(senderUid, amount);
                    // Record failed transaction
                    recordFailedTransaction(senderUid, senderName, senderUpi, receiverUid, receiverName, receiverUpi, amount, note, senderTxType, e.getMessage());
                    callback.onFailure("Payment error: " + (e.getMessage() != null ? e.getMessage() : "Unknown error") + ". Your balance was refunded.");
                });
    }

    private static void rollbackSenderBalance(String senderUid, double amount) {
        FirebaseRefs.getUserRef(senderUid).child("balance").setValue(ServerValue.increment(amount));
    }

    private static void recordFailedTransaction(
            String senderUid,
            String senderName,
            String senderUpi,
            String receiverUid,
            String receiverName,
            String receiverUpi,
            double amount,
            String note,
            String senderTxType,
            String reason) {

        String failTxId = FirebaseRefs.getTransactionsRef().push().getKey();
        if (failTxId == null) failTxId = "tx_fail_" + System.currentTimeMillis();

        TransactionModel failedTx = new TransactionModel(
                failTxId,
                senderUid,
                receiverUid,
                senderName,
                receiverName,
                senderUpi,
                receiverUpi,
                amount,
                senderTxType,
                FirebaseConstants.STATUS_FAILED,
                (note != null ? note : "Transfer") + " (Failed: " + reason + ")",
                System.currentTimeMillis()
        );

        Map<String, Object> failUpdates = new HashMap<>();
        failUpdates.put(FirebaseConstants.NODE_TRANSACTIONS + "/" + failTxId, failedTx);
        failUpdates.put(FirebaseConstants.NODE_USER_TRANSACTIONS + "/" + senderUid + "/" + failTxId, true);

        FirebaseRefs.getRootRef().updateChildren(failUpdates);
    }
}

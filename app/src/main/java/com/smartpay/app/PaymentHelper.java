package com.smartpay.app;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ServerValue;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Helper class for payment validation and Firebase database execution.
 * Encapsulates:
 * - Card Luhn algorithm validation
 * - Card expiry date validation
 * - UPI ID format validation
 * - Atomic wallet balance top-up via ServerValue.increment and transaction record creation
 *
 * As mandated by the Project Brief:
 * NEVER stores card number, CVV or expiry in any persistent storage or logs.
 */
public final class PaymentHelper {

    private static final Pattern UPI_PATTERN = Pattern.compile("^[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z]{2,64}$");

    private PaymentHelper() {
    }

    public interface PaymentCallback {
        void onSuccess(String transactionId, double amount);
        void onFailure(String errorMessage);
    }

    /**
     * Validates UPI ID format (e.g. name@upi, 9876543210@paytm).
     */
    public static boolean isValidUpiId(String upiId) {
        if (upiId == null) return false;
        return UPI_PATTERN.matcher(upiId.trim()).matches();
    }

    /**
     * Validates card number using the standard Luhn algorithm (mod 10).
     */
    public static boolean isValidLuhn(String cardNumber) {
        if (cardNumber == null) return false;
        String digitsOnly = cardNumber.replaceAll("\\s+", "");
        if (digitsOnly.length() != 16 || !digitsOnly.matches("\\d{16}")) {
            return false;
        }

        int sum = 0;
        boolean alternate = false;
        for (int i = digitsOnly.length() - 1; i >= 0; i--) {
            int n = Integer.parseInt(digitsOnly.substring(i, i + 1));
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n = (n % 10) + 1;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        return (sum % 10 == 0);
    }

    /**
     * Validates card expiry (MM/YY format, valid month, not in the past).
     */
    public static boolean isValidExpiry(String expiry) {
        if (expiry == null) return false;
        String clean = expiry.trim().replace(" ", "");
        if (!clean.matches("^(0[1-9]|1[0-2])/\\d{2}$")) {
            return false;
        }

        try {
            String[] parts = clean.split("/");
            int expMonth = Integer.parseInt(parts[0]);
            int expYear = 2000 + Integer.parseInt(parts[1]);

            Calendar now = Calendar.getInstance();
            int currentYear = now.get(Calendar.YEAR);
            int currentMonth = now.get(Calendar.MONTH) + 1; // 1-indexed

            if (expYear < currentYear) {
                return false;
            }
            if (expYear == currentYear && expMonth < currentMonth) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Executes atomic wallet top-up in Firebase Realtime Database:
     * 1. Atomically increments users/{uid}/balance by amount using ServerValue.increment.
     * 2. Saves an ADD_MONEY record under transactions/{transactionId}.
     */
    public static void executeAddMoney(String uid, String userName, double amount,
                                       String paymentSource, PaymentCallback callback) {
        if (uid == null || uid.isEmpty()) {
            if (callback != null) callback.onFailure("User authentication required");
            return;
        }

        DatabaseReference txRef = FirebaseRefs.getTransactionsRef();
        String transactionId = txRef.push().getKey();
        if (transactionId == null) {
            transactionId = "tx_" + System.currentTimeMillis();
        }

        long now = System.currentTimeMillis();
        String note = "Wallet top-up via " + paymentSource;

        TransactionModel tx = new TransactionModel(
                transactionId,
                uid, // senderId = uid for self top-up
                uid, // receiverId = uid
                userName != null ? userName : "User",
                userName != null ? userName : "User",
                amount,
                FirebaseConstants.TYPE_ADD_MONEY,
                FirebaseConstants.STATUS_SUCCESS,
                note,
                now
        );

        Map<String, Object> updates = new HashMap<>();
        updates.put(FirebaseConstants.NODE_USERS + "/" + uid + "/balance", ServerValue.increment(amount));
        updates.put(FirebaseConstants.NODE_TRANSACTIONS + "/" + transactionId, tx);
        updates.put(FirebaseConstants.NODE_USER_TRANSACTIONS + "/" + uid + "/" + transactionId, true);

        final String finalTxId = transactionId;
        FirebaseRefs.getRootRef().updateChildren(updates)
                .addOnSuccessListener(unused -> {
                    if (callback != null) {
                        callback.onSuccess(finalTxId, amount);
                    }
                })
                .addOnFailureListener(e -> {
                    if (callback != null) {
                        String msg = e.getMessage() != null ? e.getMessage() : "Payment processing failed";
                        callback.onFailure(msg);
                    }
                });
    }
}

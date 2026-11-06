package com.smartpay.app;

import java.util.ArrayList;
import java.util.List;

/**
 * Constants for Firebase Realtime Database nodes, transaction types,
 * statuses, and application settings according to the official Project Brief.
 */
public final class FirebaseConstants {

    private FirebaseConstants() {
        // Prevent instantiation
    }

    /**
     * List of all supported bank names for dropdown selection.
     * Used in AddBankAccountActivity and UpiBankActivity.
     */
    public static List<String> getBankList() {
        List<String> banks = new ArrayList<>();
        banks.add("State Bank of India");
        banks.add("HDFC Bank");
        banks.add("ICICI Bank");
        banks.add("Axis Bank");
        banks.add("Kotak Mahindra Bank");
        banks.add("Punjab National Bank");
        banks.add("Bank of Baroda");
        banks.add("Canara Bank");
        banks.add("IndusInd Bank");
        banks.add("Yes Bank");
        banks.add("Union Bank of India");
        return banks;
    }

    // Database root nodes
    public static final String NODE_USERS = "users";
    public static final String NODE_USER_PINS = "userPins";
    public static final String NODE_TRANSACTIONS = "transactions";
    public static final String NODE_QR_PAYMENTS = "QRPayments";
    public static final String NODE_NOTIFICATIONS = "notifications";
    public static final String NODE_USER_TRANSACTIONS = "userTransactions";
    public static final String NODE_BANK_ACCOUNTS = "bankAccounts"; // stored under users/{uid}/bankAccounts/

    // Transaction Types
    public static final String TYPE_SENT = "SENT";
    public static final String TYPE_RECEIVED = "RECEIVED";
    public static final String TYPE_ADD_MONEY = "ADD_MONEY";
    public static final String TYPE_QR_PAYMENT = "QR_PAYMENT";

    // Transaction Statuses
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    // Demo Initial Balance (as specified in brief)
    public static final double WELCOME_BALANCE = 0.0;

    // Maximum top-up / transfer amount for demo
    public static final double MAX_ADD_MONEY_AMOUNT = 50000.0;
    public static final double MAX_TRANSFER_AMOUNT = 50000.0;

    // UPI ID suffix
    public static final String UPI_SUFFIX = "@smartpay";

    /**
     * Auto-generate demo UPI ID from phone number: <phone>@smartpay
     */
    public static String generateUpiId(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return "user" + UPI_SUFFIX;
        }
        return phone.trim() + UPI_SUFFIX;
    }
}

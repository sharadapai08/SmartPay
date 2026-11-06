package com.smartpay.app;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

/**
 * Shared helper for Firebase Realtime Database.
 * Centralizes the regional database URL (asia-southeast1) so every class uses
 * the exact same configured database instance.
 */
public final class FirebaseRefs {

    // Regional Realtime Database URL
    public static final String DATABASE_URL = "https://smartpay-3338a-default-rtdb.asia-southeast1.firebasedatabase.app";

    private static FirebaseDatabase sDatabase;

    private FirebaseRefs() {
    }

    /**
     * Returns the singleton FirebaseDatabase instance pointing to the correct regional URL.
     */
    public static synchronized FirebaseDatabase getDatabase() {
        if (sDatabase == null) {
            sDatabase = FirebaseDatabase.getInstance(DATABASE_URL);
        }
        return sDatabase;
    }

    /**
     * Root reference of the Realtime Database.
     */
    public static DatabaseReference getRootRef() {
        return getDatabase().getReference();
    }

    /**
     * Reference to 'users' node.
     */
    public static DatabaseReference getUsersRef() {
        return getRootRef().child(FirebaseConstants.NODE_USERS);
    }

    /**
     * Reference to a specific user's node: users/{uid}
     */
    public static DatabaseReference getUserRef(String uid) {
        return getUsersRef().child(uid);
    }

    /**
     * Reference to 'userPins' root node.
     */
    public static DatabaseReference getUserPinsRef() {
        return getRootRef().child(FirebaseConstants.NODE_USER_PINS);
    }

    /**
     * Reference to a specific user's private PIN node: userPins/{uid}
     */
    public static DatabaseReference getUserPinRef(String uid) {
        return getUserPinsRef().child(uid);
    }

    /**
     * Reference to 'transactions' node.
     */
    public static DatabaseReference getTransactionsRef() {
        return getRootRef().child(FirebaseConstants.NODE_TRANSACTIONS);
    }

    /**
     * Reference to 'QRPayments' node.
     */
    public static DatabaseReference getQrPaymentsRef() {
        return getRootRef().child(FirebaseConstants.NODE_QR_PAYMENTS);
    }

    /**
     * Reference to 'notifications/{uid}' node.
     */
    public static DatabaseReference getNotificationsRef(String uid) {
        return getRootRef().child(FirebaseConstants.NODE_NOTIFICATIONS).child(uid);
    }

    /**
     * Reference to 'userTransactions/{uid}' node.
     */
    public static DatabaseReference getUserTransactionsRef(String uid) {
        return getRootRef().child(FirebaseConstants.NODE_USER_TRANSACTIONS).child(uid);
    }

    /**
     * Reference to 'users/{uid}/bankAccounts' node.
     * Stored under the user's own node so existing security rules allow owner access.
     */
    public static DatabaseReference getBankAccountsRef(String uid) {
        return getUserRef(uid).child(FirebaseConstants.NODE_BANK_ACCOUNTS);
    }
}

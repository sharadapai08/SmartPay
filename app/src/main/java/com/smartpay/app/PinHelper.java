package com.smartpay.app;

import android.util.Base64;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

/**
 * Security helper to securely store and verify the user's 4-digit PIN.
 * Uses salted SHA-256 hashing.
 *
 * Privacy Architecture:
 * - PIN hashes and salts are strictly stored under private userPins/{uid} (owner read/write only).
 * - PIN data is never exposed in users/{uid} or transmitted during public user lookups.
 * - Plaintext PIN is never logged, stored, or sent over network.
 */
public final class PinHelper {

    private PinHelper() {
    }

    public interface PinLoadCallback {
        void onPinLoaded(boolean hasPin, String pinHash, String pinSalt);
        void onError(String errorMessage);
    }

    public interface PinSaveCallback {
        void onSuccess(String pinHash, String pinSalt);
        void onFailure(String errorMessage);
    }

    /**
     * Generates a cryptographically secure random salt.
     */
    public static String generateSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return Base64.encodeToString(salt, Base64.NO_WRAP);
    }

    /**
     * Computes the SHA-256 hash of the PIN combined with the salt.
     */
    public static String hashPin(String pin, String salt) {
        if (pin == null || salt == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = salt + pin;
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(hashBytes, Base64.NO_WRAP);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Verifies if the provided PIN matches the stored salt and hash.
     */
    public static boolean verifyPin(String inputPin, String salt, String expectedHash) {
        if (inputPin == null || salt == null || expectedHash == null) {
            return false;
        }
        String computedHash = hashPin(inputPin, salt);
        return expectedHash.equals(computedHash);
    }

    /**
     * Loads the user's PIN data from private userPins/{uid} node.
     * If user has no record in userPins/{uid}, treats them as having no PIN so a new PIN is prompted.
     */
    public static void loadPin(String uid, PinLoadCallback callback) {
        if (callback == null) return;
        if (uid == null || uid.isEmpty()) {
            callback.onError("Invalid user ID");
            return;
        }

        FirebaseRefs.getUserPinRef(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String hash = snapshot.child("pinHash").getValue(String.class);
                    String salt = snapshot.child("pinSalt").getValue(String.class);
                    if (hash != null && !hash.isEmpty() && salt != null && !salt.isEmpty()) {
                        callback.onPinLoaded(true, hash, salt);
                        return;
                    }
                }
                // No PIN found in userPins/{uid}
                callback.onPinLoaded(false, "", "");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    /**
     * Saves a new PIN: computes salted SHA-256 hash and writes to userPins/{uid}.
     * Also removes any legacy pinHash/pinSalt from users/{uid}.
     */
    public static void savePin(String uid, String newPin, PinSaveCallback callback) {
        if (uid == null || uid.isEmpty()) {
            if (callback != null) callback.onFailure("Invalid user ID");
            return;
        }
        if (newPin == null || newPin.length() != 4 || !newPin.matches("\\d{4}")) {
            if (callback != null) callback.onFailure("PIN must be exactly 4 digits");
            return;
        }

        String salt = generateSalt();
        String hash = hashPin(newPin, salt);

        Map<String, Object> updates = new HashMap<>();
        updates.put(FirebaseConstants.NODE_USER_PINS + "/" + uid + "/pinHash", hash);
        updates.put(FirebaseConstants.NODE_USER_PINS + "/" + uid + "/pinSalt", salt);
        updates.put(FirebaseConstants.NODE_USER_PINS + "/" + uid + "/updatedAt", ServerValue.TIMESTAMP);

        // Remove old pinHash and pinSalt from users/{uid} for privacy and cleanup
        updates.put(FirebaseConstants.NODE_USERS + "/" + uid + "/pinHash", null);
        updates.put(FirebaseConstants.NODE_USERS + "/" + uid + "/pinSalt", null);

        FirebaseRefs.getRootRef().updateChildren(updates)
                .addOnSuccessListener(unused -> {
                    if (callback != null) {
                        callback.onSuccess(hash, salt);
                    }
                })
                .addOnFailureListener(e -> {
                    if (callback != null) {
                        callback.onFailure(e.getMessage() != null ? e.getMessage() : "Failed to save PIN");
                    }
                });
    }
}

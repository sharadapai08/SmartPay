package com.smartpay.app;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;

/**
 * Model class representing a user in Firebase Realtime Database.
 * Node: users/{uid}
 *
 * Contains only public profile fields:
 * - name, email, phone, upiId, balance, createdAt
 *
 * Note on PIN privacy:
 * PIN hash and salt are strictly stored under the separate private node userPins/{uid}.
 * They are excluded from users/{uid} serialization.
 */
@IgnoreExtraProperties
public class UserModel {

    private String name;
    private String email;
    private String phone;
    private String upiId;
    private double balance;
    private String pinHash;
    private String pinSalt;
    private long createdAt;
    private String photoBase64; // Base64-encoded profile photo (nullable)

    // Required empty constructor for Firebase deserialization
    public UserModel() {
    }

    public UserModel(String name, String email, String phone, String upiId,
                     double balance, long createdAt) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.upiId = upiId;
        this.balance = balance;
        this.createdAt = createdAt;
    }

    // Deprecated compatibility constructor
    public UserModel(String name, String email, String phone, String upiId,
                     double balance, String pinHash, String pinSalt, long createdAt) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.upiId = upiId;
        this.balance = balance;
        this.pinHash = pinHash;
        this.pinSalt = pinSalt;
        this.createdAt = createdAt;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getUpiId() { return upiId; }
    public void setUpiId(String upiId) { this.upiId = upiId; }

    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }

    @Exclude
    public String getPinHash() { return pinHash; }
    public void setPinHash(String pinHash) { this.pinHash = pinHash; }

    @Exclude
    public String getPinSalt() { return pinSalt; }
    public void setPinSalt(String pinSalt) { this.pinSalt = pinSalt; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public String getPhotoBase64() { return photoBase64; }
    public void setPhotoBase64(String photoBase64) { this.photoBase64 = photoBase64; }
}

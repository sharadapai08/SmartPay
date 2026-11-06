package com.smartpay.app;

/**
 * Model class for QR payment mapping in Firebase Realtime Database.
 * Node: QRPayments/{uid}
 *
 * Fields match the official project specification:
 * - userId, userName
 */
public class QRPaymentModel {

    private String userId;
    private String userName;

    public QRPaymentModel() {
    }

    public QRPaymentModel(String userId, String userName) {
        this.userId = userId;
        this.userName = userName;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
}

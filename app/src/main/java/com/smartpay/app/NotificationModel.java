package com.smartpay.app;

/**
 * Model class for notifications in Firebase Realtime Database.
 * Node: notifications/{uid}/{notificationId}
 *
 * Fields match the official project specification:
 * - title, message, amount, timestamp, read (boolean)
 */
public class NotificationModel {

    private String notificationId;
    private String title;
    private String message;
    private double amount;
    private long timestamp;
    private boolean read;

    public NotificationModel() {
    }

    public NotificationModel(String notificationId, String title, String message,
                             double amount, long timestamp, boolean read) {
        this.notificationId = notificationId;
        this.title = title;
        this.message = message;
        this.amount = amount;
        this.timestamp = timestamp;
        this.read = read;
    }

    public String getNotificationId() { return notificationId; }
    public void setNotificationId(String notificationId) { this.notificationId = notificationId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
}

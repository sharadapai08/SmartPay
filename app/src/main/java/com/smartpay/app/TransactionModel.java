package com.smartpay.app;

/**
 * Model class representing a payment transaction in Firebase Realtime Database.
 * Node: transactions/{transactionId}
 *
 * Fields match the official project specification:
 * - senderId, receiverId, senderName, receiverName, senderUpi, receiverUpi
 * - amount (double / paise)
 * - type: "SENT" | "RECEIVED" | "ADD_MONEY" | "QR_PAYMENT"
 * - status: "SUCCESS" | "FAILED"
 * - note
 * - timestamp (long)
 */
public class TransactionModel {

    private String transactionId;
    private String senderId;
    private String receiverId;
    private String senderName;
    private String receiverName;
    private String senderUpi;
    private String receiverUpi;
    private double amount;
    private String type;       // "SENT" | "RECEIVED" | "ADD_MONEY" | "QR_PAYMENT"
    private String status;     // "SUCCESS" | "FAILED"
    private String note;
    private long timestamp;

    // Required empty constructor for Firebase deserialization
    public TransactionModel() {
    }

    public TransactionModel(String transactionId, String senderId, String receiverId,
                            String senderName, String receiverName, double amount,
                            String type, String status, String note, long timestamp) {
        this(transactionId, senderId, receiverId, senderName, receiverName, "", "", amount, type, status, note, timestamp);
    }

    public TransactionModel(String transactionId, String senderId, String receiverId,
                            String senderName, String receiverName, String senderUpi, String receiverUpi,
                            double amount, String type, String status, String note, long timestamp) {
        this.transactionId = transactionId;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.senderName = senderName;
        this.receiverName = receiverName;
        this.senderUpi = senderUpi;
        this.receiverUpi = receiverUpi;
        this.amount = amount;
        this.type = type;
        this.status = status;
        this.note = note;
        this.timestamp = timestamp;
    }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getReceiverId() { return receiverId; }
    public void setReceiverId(String receiverId) { this.receiverId = receiverId; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getReceiverName() { return receiverName; }
    public void setReceiverName(String receiverName) { this.receiverName = receiverName; }

    public String getSenderUpi() { return senderUpi; }
    public void setSenderUpi(String senderUpi) { this.senderUpi = senderUpi; }

    public String getReceiverUpi() { return receiverUpi; }
    public void setReceiverUpi(String receiverUpi) { this.receiverUpi = receiverUpi; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}

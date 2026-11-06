package com.smartpay.app;

/**
 * Model for a saved bank account stored at bankAccounts/{uid}/{accountId}.
 * Fields: bankName, accountNumber, ifsc, accountHolder, addedAt.
 */
public class BankAccountModel {

    private String accountId;
    private String bankName;
    private String accountNumber; // stored as-is; shown masked in UI
    private String ifsc;
    private String accountHolder;
    private long addedAt;

    public BankAccountModel() {}

    public BankAccountModel(String accountId, String bankName, String accountNumber,
                             String ifsc, String accountHolder, long addedAt) {
        this.accountId = accountId;
        this.bankName = bankName;
        this.accountNumber = accountNumber;
        this.ifsc = ifsc;
        this.accountHolder = accountHolder;
        this.addedAt = addedAt;
    }

    public String getAccountId()        { return accountId; }
    public void setAccountId(String v)  { this.accountId = v; }

    public String getBankName()         { return bankName; }
    public void setBankName(String v)   { this.bankName = v; }

    public String getAccountNumber()    { return accountNumber; }
    public void setAccountNumber(String v) { this.accountNumber = v; }

    public String getIfsc()             { return ifsc; }
    public void setIfsc(String v)       { this.ifsc = v; }

    public String getAccountHolder()    { return accountHolder; }
    public void setAccountHolder(String v) { this.accountHolder = v; }

    public long getAddedAt()            { return addedAt; }
    public void setAddedAt(long v)      { this.addedAt = v; }

    /** Returns the last 4 digits of the account number for masked display. */
    public String getMaskedAccountNumber() {
        if (accountNumber == null || accountNumber.length() < 4) return "XXXX";
        return "XXXX " + accountNumber.substring(accountNumber.length() - 4);
    }
}

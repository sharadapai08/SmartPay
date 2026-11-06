package com.smartpay.app;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.smartpay.app.databinding.ActivityTransactionItemBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for displaying transaction history.
 * 5-column layout: Type | Date & Time | Description | Amount | Status
 */
public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder> {

    private final Context context;
    private final String currentUid;
    private List<TransactionModel> transactions = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy\nhh:mm a", Locale.US);
    private final SimpleDateFormat detailDateFormat = new SimpleDateFormat("dd MMMM yyyy, hh:mm:ss a", Locale.US);

    public TransactionAdapter(Context context, String currentUid) {
        this.context = context;
        this.currentUid = currentUid;
    }

    public void setTransactions(List<TransactionModel> transactions) {
        this.transactions = transactions != null ? transactions : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TransactionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ActivityTransactionItemBinding binding = ActivityTransactionItemBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new TransactionViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull TransactionViewHolder holder, int position) {
        TransactionModel tx = transactions.get(position);
        holder.bind(tx);
    }

    @Override
    public int getItemCount() {
        return transactions.size();
    }

    class TransactionViewHolder extends RecyclerView.ViewHolder {
        private final ActivityTransactionItemBinding binding;

        TransactionViewHolder(ActivityTransactionItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(TransactionModel tx) {
            // Column 2: Date & Time
            String formattedDate = dateFormat.format(new Date(tx.getTimestamp()));
            binding.tvDate.setText(formattedDate);

            // Column 3: Description
            String description = getTransactionDescription(tx);
            binding.tvDescription.setText(description);

            // Check if status is failed
            boolean isFailed = FirebaseConstants.STATUS_FAILED.equalsIgnoreCase(tx.getStatus());
            boolean isAddMoney = FirebaseConstants.TYPE_ADD_MONEY.equalsIgnoreCase(tx.getType());
            boolean isReceived = FirebaseConstants.TYPE_RECEIVED.equalsIgnoreCase(tx.getType());
            boolean isSender = currentUid != null && currentUid.equals(tx.getSenderId());

            // Column 1: Type Icon
            if (isFailed) {
                binding.ivType.setImageResource(R.drawable.ic_circle_arrow_up_red);
            } else if (isAddMoney) {
                binding.ivType.setImageResource(R.drawable.ic_circle_arrow_down_green);
            } else if (isReceived || (!isSender && !isAddMoney)) {
                binding.ivType.setImageResource(R.drawable.ic_circle_arrow_down);
            } else {
                binding.ivType.setImageResource(R.drawable.ic_circle_arrow_up);
            }

            // Column 4: Amount
            String formattedRupees = CurrencyHelper.formatRupees(tx.getAmount());
            if (isAddMoney || isReceived) {
                binding.tvAmount.setText("+ " + formattedRupees);
                binding.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.success_green));
            } else if (isFailed) {
                binding.tvAmount.setText("- " + formattedRupees);
                binding.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.fail_red));
            } else {
                binding.tvAmount.setText("- " + formattedRupees);
                binding.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
            }

            // Column 5: Status Icon
            if (isFailed) {
                binding.ivStatus.setImageResource(R.drawable.ic_circle_close);
            } else {
                binding.ivStatus.setImageResource(R.drawable.ic_circle_check);
            }

            // Tap item to show transaction details
            binding.getRoot().setOnClickListener(v -> showDetailDialog(tx, description));
        }

        private String getTransactionDescription(TransactionModel tx) {
            if (FirebaseConstants.TYPE_ADD_MONEY.equalsIgnoreCase(tx.getType())) {
                if (tx.getNote() != null && !tx.getNote().trim().isEmpty()) {
                    return tx.getNote().trim();
                }
                return "Added to Wallet";
            }

            boolean isSender = currentUid != null && currentUid.equals(tx.getSenderId());
            if (isSender) {
                String receiver = tx.getReceiverName();
                if (receiver == null || receiver.trim().isEmpty()) receiver = "SmartPay User";
                if (FirebaseConstants.TYPE_QR_PAYMENT.equalsIgnoreCase(tx.getType())) {
                    return "Paid to " + receiver;
                }
                return "Sent to " + receiver;
            } else {
                String sender = tx.getSenderName();
                if (sender == null || sender.trim().isEmpty()) sender = "SmartPay User";
                return "Received from " + sender;
            }
        }

        private void showDetailDialog(TransactionModel tx, String description) {
            String status = tx.getStatus() != null ? tx.getStatus().toUpperCase() : "SUCCESS";
            String date = detailDateFormat.format(new Date(tx.getTimestamp()));
            String amount = CurrencyHelper.formatRupees(tx.getAmount());
            String txId = tx.getTransactionId() != null ? tx.getTransactionId() : "N/A";
            String note = tx.getNote() != null && !tx.getNote().isEmpty() ? tx.getNote() : "None";

            StringBuilder details = new StringBuilder();
            details.append("Transaction ID:\n").append(txId).append("\n\n");
            details.append("Status: ").append(status).append("\n");
            details.append("Date: ").append(date).append("\n");
            details.append("Amount: ").append(amount).append("\n");
            details.append("Type: ").append(tx.getType() != null ? tx.getType() : "N/A").append("\n");

            if (tx.getSenderName() != null && !tx.getSenderName().isEmpty()) {
                details.append("From: ").append(tx.getSenderName());
                if (tx.getSenderUpi() != null && !tx.getSenderUpi().isEmpty()) {
                    details.append(" (").append(tx.getSenderUpi()).append(")");
                }
                details.append("\n");
            }

            if (tx.getReceiverName() != null && !tx.getReceiverName().isEmpty()) {
                details.append("To: ").append(tx.getReceiverName());
                if (tx.getReceiverUpi() != null && !tx.getReceiverUpi().isEmpty()) {
                    details.append(" (").append(tx.getReceiverUpi()).append(")");
                }
                details.append("\n");
            }

            details.append("Note: ").append(note);

            new AlertDialog.Builder(context)
                    .setTitle(description)
                    .setMessage(details.toString())
                    .setPositiveButton("CLOSE", null)
                    .show();
        }
    }
}

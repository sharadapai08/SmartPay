package com.smartpay.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.smartpay.app.databinding.ActivityNotificationItemBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * NotificationAdapter - Binds user notifications to activity_notification_item layout.
 */
public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder> {

    private final Context context;
    private List<NotificationModel> notifications = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.US);
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.US);

    public NotificationAdapter(Context context) {
        this.context = context;
    }

    public void setNotifications(List<NotificationModel> notifications) {
        this.notifications = notifications != null ? notifications : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ActivityNotificationItemBinding binding = ActivityNotificationItemBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new NotificationViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        NotificationModel notification = notifications.get(position);
        holder.bind(notification);
    }

    @Override
    public int getItemCount() {
        return notifications.size();
    }

    class NotificationViewHolder extends RecyclerView.ViewHolder {

        private final ActivityNotificationItemBinding binding;

        public NotificationViewHolder(@NonNull ActivityNotificationItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(NotificationModel item) {
            binding.tvNotifTitle.setText(item.getTitle() != null ? item.getTitle() : "Notification");
            binding.tvNotifDescription.setText(item.getMessage() != null ? item.getMessage() : "");

            long ts = item.getTimestamp() > 0 ? item.getTimestamp() : System.currentTimeMillis();
            Date date = new Date(ts);
            binding.tvNotifTime.setText(timeFormat.format(date));
            binding.tvNotifDate.setText(dateFormat.format(date));

            // Green dot for unread notification
            binding.viewUnreadDot.setVisibility(item.isRead() ? View.GONE : View.VISIBLE);
        }
    }
}

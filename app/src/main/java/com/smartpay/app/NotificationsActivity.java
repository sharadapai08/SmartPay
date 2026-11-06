package com.smartpay.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.smartpay.app.databinding.ActivityNotificationsBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NotificationsActivity - Displays user notifications.
 * Implements real-time synchronization, auto-marks unread notifications as read,
 * and handles empty states.
 */
public class NotificationsActivity extends BaseActivity {

    private ActivityNotificationsBinding binding;
    private NotificationAdapter adapter;
    private Query notificationsQuery;
    private ValueEventListener notificationsListener;
    private String currentUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNotificationsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            currentUid = user.getUid();
        }

        setupViews();
        loadNotifications();
    }

    private void setupViews() {
        binding.btnBack.setOnClickListener(v -> finish());

        // Hamburger menu
        binding.btnMore.setOnClickListener(this::showPopupMenu);

        binding.rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotificationAdapter(this);
        binding.rvNotifications.setAdapter(adapter);
    }

    private void showPopupMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.menu_notifications_options, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_add_money) {
                startActivity(new Intent(NotificationsActivity.this, AddMoneyActivity.class));
                return true;
            } else if (id == R.id.menu_send_money) {
                startActivity(new Intent(NotificationsActivity.this, SendMoneyActivity.class));
                return true;
            } else if (id == R.id.menu_receive_money) {
                startActivity(new Intent(NotificationsActivity.this, ReceiveMoneyActivity.class));
                return true;
            } else if (id == R.id.menu_scan_qr) {
                startActivity(new Intent(NotificationsActivity.this, QrScannerActivity.class));
                return true;
            } else if (id == R.id.menu_transactions) {
                startActivity(new Intent(NotificationsActivity.this, TransactionsActivity.class));
                return true;
            } else if (id == R.id.menu_profile) {
                startActivity(new Intent(NotificationsActivity.this, ProfileActivity.class));
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void loadNotifications() {
        if (currentUid == null) {
            binding.tvEmptyNotifications.setVisibility(View.VISIBLE);
            binding.rvNotifications.setVisibility(View.GONE);
            return;
        }

        notificationsQuery = FirebaseRefs.getNotificationsRef(currentUid).orderByChild("timestamp");
        notificationsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<NotificationModel> list = new ArrayList<>();
                Map<String, Object> readUpdates = new HashMap<>();

                if (snapshot.exists()) {
                    for (DataSnapshot child : snapshot.getChildren()) {
                        NotificationModel item = child.getValue(NotificationModel.class);
                        if (item != null) {
                            if (item.getNotificationId() == null || item.getNotificationId().isEmpty()) {
                                item.setNotificationId(child.getKey());
                            }
                            list.add(item);

                            // Mark as read if not already read
                            if (!item.isRead()) {
                                readUpdates.put(child.getKey() + "/read", true);
                            }
                        }
                    }
                }

                // Newest first
                Collections.reverse(list);
                adapter.setNotifications(list);

                if (list.isEmpty()) {
                    binding.tvEmptyNotifications.setVisibility(View.VISIBLE);
                    binding.rvNotifications.setVisibility(View.GONE);
                } else {
                    binding.tvEmptyNotifications.setVisibility(View.GONE);
                    binding.rvNotifications.setVisibility(View.VISIBLE);
                }

                // Auto-mark notifications as read in Firebase
                if (!readUpdates.isEmpty()) {
                    FirebaseRefs.getNotificationsRef(currentUid).updateChildren(readUpdates);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Silently handle cancelled event
            }
        };

        notificationsQuery.addValueEventListener(notificationsListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (notificationsQuery != null && notificationsListener != null) {
            notificationsQuery.removeEventListener(notificationsListener);
            notificationsListener = null;
        }
    }
}

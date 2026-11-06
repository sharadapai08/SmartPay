package com.smartpay.app;

import android.app.Activity;
import android.content.Context;
import android.util.Base64;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

/**
 * Loads a user's stored profile photo (users/{uid}/photoBase64) into an ImageView.
 * Falls back to the default person icon when no photo is saved.
 */
public final class AvatarHelper {

    private static final int PLACEHOLDER_PADDING_PX = 10;

    private AvatarHelper() {
    }

    /**
     * Loads the currently signed-in user's profile photo.
     */
    public static void bindCurrentUser(@Nullable ImageView view) {
        if (view == null) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            bindPlaceholder(view);
            return;
        }
        bindUser(view, user.getUid());
    }

    /**
     * Loads the profile photo for the given Firebase uid.
     */
    public static void bindUser(@Nullable ImageView view, @Nullable String uid) {
        if (view == null) return;
        if (uid == null || uid.isEmpty()) {
            bindPlaceholder(view);
            return;
        }

        FirebaseRefs.getUserRef(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isViewReady(view)) return;
                UserModel user = snapshot.getValue(UserModel.class);
                bindPhoto(view, user != null ? user.getPhotoBase64() : null);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (!isViewReady(view)) return;
                bindPlaceholder(view);
            }
        });
    }

    /**
     * Binds an already-loaded Base64 photo string, or the placeholder if empty.
     */
    public static void bindPhoto(@Nullable ImageView view, @Nullable String photoBase64) {
        if (view == null || !isViewReady(view)) return;
        if (photoBase64 == null || photoBase64.isEmpty()) {
            bindPlaceholder(view);
            return;
        }

        try {
            byte[] bytes = Base64.decode(photoBase64, Base64.NO_WRAP);
            view.setPadding(0, 0, 0, 0);
            view.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Glide.with(view.getContext())
                    .load(bytes)
                    .skipMemoryCache(true)
                    .circleCrop()
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .into(view);
        } catch (IllegalArgumentException e) {
            bindPlaceholder(view);
        }
    }

    private static boolean isViewReady(@Nullable ImageView view) {
        if (view == null) return false;
        Context ctx = view.getContext();
        if (ctx instanceof Activity) {
            Activity activity = (Activity) ctx;
            return !activity.isFinishing() && !activity.isDestroyed();
        }
        return true;
    }

    public static void bindPlaceholder(@NonNull ImageView view) {
        int pad = (int) (PLACEHOLDER_PADDING_PX * view.getResources().getDisplayMetrics().density);
        view.setPadding(pad, pad, pad, pad);
        view.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        view.setImageResource(R.drawable.ic_person);
    }
}

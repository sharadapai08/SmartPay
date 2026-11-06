package com.smartpay.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.smartpay.app.databinding.ActivityEditProfileBinding;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * EditProfileActivity - Allows the user to update their name, phone number,
 * and profile photo.
 *
 * Photo flow:
 * - Tapping the avatar opens the device image picker.
 * - The selected image is scaled down to 256×256 and compressed to JPEG at 70% quality.
 * - The compressed bytes are Base64-encoded and stored in users/{uid}/photoBase64.
 * - No Firebase Storage is required; everything lives in Realtime Database.
 *
 * Implements:
 * - Pre-fills name, email (read-only), phone, and current photo from Firebase.
 * - Validates: name must not be blank; phone must be exactly 10 digits.
 * - Atomic multi-path update: name, phone, upiId, photoBase64.
 * - Disables Save and shows progress bar during the write.
 * - Returns to ProfileActivity with a success Toast on completion.
 * - Full Firebase listener cleanup in onDestroy.
 */
public class EditProfileActivity extends BaseActivity {

    /** Maximum dimension (px) to scale the photo to before encoding. */
    private static final int MAX_PHOTO_PX = 256;
    /** JPEG quality (0–100) used when compressing the resized photo. */
    private static final int JPEG_QUALITY = 70;

    private ActivityEditProfileBinding binding;
    private DatabaseReference userRef;
    private ValueEventListener userListener;
    private boolean isOperationInProgress = false;

    /** Holds the Base64 string of the newly picked photo, or null if unchanged. */
    private String pendingPhotoBase64 = null;
    /** Holds the currently saved Base64 from Firebase (used to restore on cancel). */
    private String currentPhotoBase64 = null;

    // Image picker launcher
    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    handlePickedImage(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEditProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnCancelEdit.setOnClickListener(v -> finish());
        binding.btnSaveProfile.setOnClickListener(v -> handleSave());

        // Tap avatar or camera badge to pick a new photo
        binding.layoutAvatarPicker.setOnClickListener(v ->
                imagePickerLauncher.launch("image/*"));

        loadCurrentProfile();
    }

    /**
     * Loads the user's current profile from Firebase and pre-fills all form fields.
     */
    private void loadCurrentProfile() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Session expired. Please log in.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        userRef = FirebaseRefs.getUserRef(currentUser.getUid());
        userListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                UserModel user = snapshot.getValue(UserModel.class);
                if (user != null) {
                    binding.etEditName.setText(user.getName() != null ? user.getName() : "");
                    binding.etEditPhone.setText(user.getPhone() != null ? user.getPhone() : "");
                    binding.etEditEmail.setText(user.getEmail() != null ? user.getEmail() : "");

                    currentPhotoBase64 = user.getPhotoBase64();
                    loadAvatarIntoView(currentPhotoBase64);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(EditProfileActivity.this,
                        "Failed to load profile.", Toast.LENGTH_SHORT).show();
            }
        };
        userRef.addListenerForSingleValueEvent(userListener);
    }

    /**
     * Decodes the picked image URI, scales it down, compresses it, and previews it.
     */
    private void handlePickedImage(Uri uri) {
        try {
            Bitmap original;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                original = ImageDecoder.decodeBitmap(
                        ImageDecoder.createSource(getContentResolver(), uri));
            } else {
                original = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            }

            // Ensure software bitmap for manipulation
            if (original.getConfig() == Bitmap.Config.HARDWARE) {
                original = original.copy(Bitmap.Config.ARGB_8888, false);
            }

            // Scale down keeping aspect ratio, capping longest side at MAX_PHOTO_PX
            Bitmap scaled = scaleBitmap(original, MAX_PHOTO_PX);
            original.recycle();

            // Compress to JPEG bytes
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos);
            scaled.recycle();

            pendingPhotoBase64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

            // Preview immediately without waiting for Firebase
            Glide.with(this)
                    .load(baos.toByteArray())
                    .circleCrop()
                    .placeholder(R.drawable.ic_person)
                    .into(binding.ivAvatarEdit);

        } catch (IOException e) {
            Toast.makeText(this, "Could not load image: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Scales a bitmap so its longest side is at most maxPx, preserving aspect ratio.
     */
    private static Bitmap scaleBitmap(Bitmap src, int maxPx) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= maxPx && h <= maxPx) return src;
        float scale = maxPx / (float) Math.max(w, h);
        return Bitmap.createScaledBitmap(src, Math.round(w * scale), Math.round(h * scale), true);
    }

    /**
     * Loads a Base64 photo string into the avatar ImageView via Glide, or shows
     * the default person icon if no photo is available.
     */
    private void loadAvatarIntoView(String base64) {
        if (base64 != null && !base64.isEmpty()) {
            byte[] bytes = Base64.decode(base64, Base64.NO_WRAP);
            Glide.with(this)
                    .load(bytes)
                    .circleCrop()
                    .placeholder(R.drawable.ic_person)
                    .into(binding.ivAvatarEdit);
        } else {
            binding.ivAvatarEdit.setPadding(0, 0, 0, 0);
            binding.ivAvatarEdit.setImageResource(R.drawable.ic_person);
        }
    }

    /**
     * Validates inputs and writes the updated profile to Firebase.
     */
    private void handleSave() {
        if (isOperationInProgress) return;

        String newName = binding.etEditName.getText().toString().trim();
        String newPhone = binding.etEditPhone.getText().toString().trim();

        String nameError = Validators.validateName(newName);
        if (nameError != null) {
            binding.etEditName.setError(nameError);
            binding.etEditName.requestFocus();
            return;
        }

        String phoneError = Validators.validatePhone(newPhone);
        if (phoneError != null) {
            binding.etEditPhone.setError(phoneError);
            binding.etEditPhone.requestFocus();
            return;
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Session expired. Please log in.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        startOperation();

        String newUpiId = FirebaseConstants.generateUpiId(newPhone);

        Map<String, Object> updates = new HashMap<>();
        updates.put("name", newName);
        updates.put("phone", newPhone);
        updates.put("upiId", newUpiId);

        // Only update photo if the user actually picked a new one
        if (pendingPhotoBase64 != null) {
            updates.put("photoBase64", pendingPhotoBase64);
        }

        FirebaseRefs.getUserRef(currentUser.getUid()).updateChildren(updates)
                .addOnSuccessListener(unused -> {
                    stopOperation();
                    Toast.makeText(EditProfileActivity.this,
                            "Profile updated successfully!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    stopOperation();
                    String msg = e.getMessage() != null ? e.getMessage() : "Update failed";
                    Toast.makeText(EditProfileActivity.this,
                            "Error: " + msg, Toast.LENGTH_LONG).show();
                });
    }

    private void startOperation() {
        isOperationInProgress = true;
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnSaveProfile.setEnabled(false);
        binding.btnCancelEdit.setEnabled(false);
    }

    private void stopOperation() {
        isOperationInProgress = false;
        binding.progressBar.setVisibility(View.GONE);
        binding.btnSaveProfile.setEnabled(true);
        binding.btnCancelEdit.setEnabled(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // userListener was registered with addListenerForSingleValueEvent — no removal needed
    }
}

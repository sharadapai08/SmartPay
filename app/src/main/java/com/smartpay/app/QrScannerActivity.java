package com.smartpay.app;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.smartpay.app.databinding.ActivityQrScannerBinding;

import java.io.IOException;

/**
 * QrScannerActivity - Camera QR scanner.
 *
 * Implements:
 * - Full-screen camera preview using zxing-android-embedded library.
 * - Square scan frame with animated blue scan line and instruction label.
 * - Single-result scan protection (pauses scanner to prevent duplicate launches).
 * - Toast "Invalid QR code" for non-SmartPay QR codes with auto-resume.
 * - Torch toggle with hardware check (disables/hides if device lacks flashlight).
 * - Gallery QR code image picker decoded via ZXing core decoder.
 * - Camera permission flow with rationale, clear denied state, and settings shortcut.
 */
public class QrScannerActivity extends BaseActivity {

    private ActivityQrScannerBinding binding;
    private DecoratedBarcodeView barcodeScanner;
    private ObjectAnimator scanLineAnimator;

    private boolean isTorchOn = false;
    private boolean isScanned = false;
    private boolean hasFlashlight = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Camera permission launcher
    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    showScannerUI();
                    startCameraScanning();
                } else {
                    showPermissionDeniedUI();
                }
            });

    // Gallery image picker launcher
    private final ActivityResultLauncher<String> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    processGalleryImage(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityQrScannerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        barcodeScanner = binding.barcodeScanner;
        barcodeScanner.setStatusText(""); // suppress the default "Place a barcode..." label

        setupUI();
        checkFlashlightAvailability();
        checkAndRequestCameraPermission();
    }

    @Override
    protected void setupEdgeToEdgeAndInsets() {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        androidx.core.view.WindowInsetsControllerCompat controller =
                new androidx.core.view.WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(false); // White icons on camera preview
        controller.setAppearanceLightNavigationBars(false);
    }

    @Override
    protected void applySystemBarInsets(View targetView) {
        if (binding == null) return;
        // Apply status-bar inset as top padding on topBar so title is never clipped
        // Apply nav-bar inset as bottom padding on bottomBar
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, windowInsets) -> {
            androidx.core.graphics.Insets insets = windowInsets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars()
                            | androidx.core.view.WindowInsetsCompat.Type.displayCutout()
            );
            // Top bar: push content below status bar
            binding.topBar.setPadding(
                    binding.topBar.getPaddingLeft(),
                    insets.top,
                    binding.topBar.getPaddingRight(),
                    binding.topBar.getPaddingBottom()
            );
            // Bottom bar: push content above nav bar
            binding.bottomBar.setPadding(
                    binding.bottomBar.getPaddingLeft(),
                    binding.bottomBar.getPaddingTop(),
                    binding.bottomBar.getPaddingRight(),
                    insets.bottom
            );
            return windowInsets;
        });
        androidx.core.view.ViewCompat.requestApplyInsets(binding.getRoot());
    }

    private void setupUI() {
        // Back and more options
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnMore.setOnClickListener(this::showPopupMenu);

        // Gallery buttons
        binding.btnGallery.setOnClickListener(v -> openGalleryPicker());
        binding.btnChooseGalleryDenied.setOnClickListener(v -> openGalleryPicker());

        // Torch button
        binding.btnTorch.setOnClickListener(v -> toggleTorch());

        // Settings button on permission denied state
        binding.btnOpenSettings.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.fromParts("package", getPackageName(), null));
            startActivity(intent);
        });

        // Initialize animated blue scan line
        setupScanLineAnimation();
    }

    private void showPopupMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.menu_qr_options, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_add_money) {
                startActivity(new Intent(QrScannerActivity.this, AddMoneyActivity.class));
                return true;
            } else if (id == R.id.menu_send_money) {
                startActivity(new Intent(QrScannerActivity.this, SendMoneyActivity.class));
                return true;
            } else if (id == R.id.menu_receive_money) {
                startActivity(new Intent(QrScannerActivity.this, ReceiveMoneyActivity.class));
                return true;
            } else if (id == R.id.menu_transactions) {
                startActivity(new Intent(QrScannerActivity.this, TransactionsActivity.class));
                return true;
            } else if (id == R.id.menu_profile) {
                startActivity(new Intent(QrScannerActivity.this, ProfileActivity.class));
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void setupScanLineAnimation() {
        binding.viewScanLine.post(() -> {
            float containerHeight = binding.layoutScanFrameContainer.getHeight();
            if (containerHeight <= 0) {
                containerHeight = 260 * getResources().getDisplayMetrics().density;
            }
            float distance = containerHeight - (16 * getResources().getDisplayMetrics().density);

            scanLineAnimator = ObjectAnimator.ofFloat(binding.viewScanLine, "translationY", 0f, distance);
            scanLineAnimator.setDuration(2200);
            scanLineAnimator.setRepeatMode(ValueAnimator.REVERSE);
            scanLineAnimator.setRepeatCount(ValueAnimator.INFINITE);
            scanLineAnimator.start();
        });
    }

    private void checkFlashlightAvailability() {
        hasFlashlight = getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
        if (!hasFlashlight) {
            binding.btnTorch.setEnabled(false);
            binding.btnTorch.setAlpha(0.4f);
        }
    }

    private void toggleTorch() {
        if (!hasFlashlight) {
            Toast.makeText(this, "Device has no flashlight", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isTorchOn) {
            barcodeScanner.setTorchOff();
            isTorchOn = false;
            binding.tvTorchLabel.setText("TORCH");
            binding.ivTorchIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.white));
        } else {
            barcodeScanner.setTorchOn();
            isTorchOn = true;
            binding.tvTorchLabel.setText("TORCH ON");
            binding.ivTorchIcon.setColorFilter(ContextCompat.getColor(this, R.color.warning_note_bg));
        }
    }

    private void openGalleryPicker() {
        galleryLauncher.launch("image/*");
    }

    private void checkAndRequestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            showScannerUI();
            startCameraScanning();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void showScannerUI() {
        binding.layoutPermissionDenied.setVisibility(View.GONE);
        binding.layoutScanFrameContainer.setVisibility(View.VISIBLE);
        binding.tvInstruction.setVisibility(View.VISIBLE);
        binding.bottomBar.setVisibility(View.VISIBLE);
    }

    private void showPermissionDeniedUI() {
        binding.layoutPermissionDenied.setVisibility(View.VISIBLE);
        binding.layoutScanFrameContainer.setVisibility(View.GONE);
        binding.tvInstruction.setVisibility(View.GONE);
    }

    private void startCameraScanning() {
        isScanned = false;
        barcodeScanner.resume();
        barcodeScanner.decodeContinuous(new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                if (result == null || result.getText() == null || isScanned) {
                    return;
                }
                handleScannedContent(result.getText());
            }
        });
    }

    /**
     * Validates scanned text against the SmartPay payload scheme.
     * Prevents duplicate scans and triggers QrPayActivity for valid QR codes.
     */
    private void handleScannedContent(String rawText) {
        if (isScanned) return;

        String uid = QrHelper.parsePayload(rawText);
        if (uid != null) {
            // Valid SmartPay QR: Pause scanning immediately to prevent duplicate launches
            isScanned = true;
            barcodeScanner.pause();

            Intent intent = new Intent(QrScannerActivity.this, QrPayActivity.class);
            intent.putExtra(QrPayActivity.EXTRA_RECEIVER_UID, uid);
            startActivity(intent);
            finish();
        } else {
            // Invalid QR code: Show toast and continue scanning
            isScanned = true;
            Toast.makeText(this, "Invalid QR code", Toast.LENGTH_SHORT).show();
            mainHandler.postDelayed(() -> isScanned = false, 1800);
        }
    }

    /**
     * Decodes QR code from a gallery image URI via ZXing core decoder.
     */
    private void processGalleryImage(Uri uri) {
        try {
            Bitmap bitmap;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(), uri));
            } else {
                bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            }

            // Convert to ARGB_8888 if hardware bitmap
            if (bitmap.getConfig() == Bitmap.Config.HARDWARE) {
                bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
            }

            String decodedContent = QrHelper.decodeFromBitmap(bitmap);
            if (decodedContent == null) {
                Toast.makeText(this, "No QR code found in selected image.", Toast.LENGTH_SHORT).show();
                return;
            }

            String uid = QrHelper.parsePayload(decodedContent);
            if (uid != null) {
                Intent intent = new Intent(QrScannerActivity.this, QrPayActivity.class);
                intent.putExtra(QrPayActivity.EXTRA_RECEIVER_UID, uid);
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(this, "Invalid QR code. Not a SmartPay QR.", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Toast.makeText(this, "Failed to load selected image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            isScanned = false;
            barcodeScanner.resume();
            if (scanLineAnimator != null && !scanLineAnimator.isRunning()) {
                scanLineAnimator.start();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        barcodeScanner.pause();
        if (scanLineAnimator != null && scanLineAnimator.isRunning()) {
            scanLineAnimator.cancel();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
        if (scanLineAnimator != null) {
            scanLineAnimator.cancel();
            scanLineAnimator = null;
        }
    }
}

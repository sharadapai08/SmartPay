package com.smartpay.app;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.smartpay.app.databinding.ActivitySplashBinding;

/**
 * SplashActivity - Dedicated launcher splash screen.
 *
 * Implements:
 * - Full-screen navy theme (#0B1F4D) matching Android 12+ system splash window.
 * - Fade-in and gentle scale-in animation on the logo and typography group (~600ms).
 * - Light system bar icons (white) on the dark navy canvas.
 * - 2-second display delay on Looper.getMainLooper().
 * - Checks in-memory FirebaseAuth.getInstance().getCurrentUser() without any network/database calls.
 * - Navigates to HomeActivity if logged in, or LoginActivity if not.
 * - Clears back stack so pressing Back never returns to SplashActivity.
 * - Removes Handler callbacks in onDestroy to prevent leaks/crashes on rotation or quick exit.
 */
@SuppressLint("CustomSplashScreen")
public class SplashActivity extends BaseActivity {

    private static final long SPLASH_DURATION_MS = 2000;
    private static final long ANIMATION_DURATION_MS = 600;

    private ActivitySplashBinding binding;
    private Handler handler;
    private Runnable navigationRunnable;

    @Override
    protected void setupEdgeToEdgeAndInsets() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        // For navy background, ensure status bar & navigation bar icons are white (not dark)
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(false);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        startEntranceAnimations();
        scheduleNextScreenNavigation();
    }

    /**
     * Runs fade-in and subtle scale-in animation on the brand logo and text.
     */
    private void startEntranceAnimations() {
        binding.layoutSplashContent.setAlpha(0f);
        binding.layoutSplashContent.setScaleX(0.85f);
        binding.layoutSplashContent.setScaleY(0.85f);

        binding.layoutSplashContent.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(ANIMATION_DURATION_MS)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    /**
     * Schedules transition to HomeActivity or LoginActivity after 2 seconds.
     */
    private void scheduleNextScreenNavigation() {
        handler = new Handler(Looper.getMainLooper());
        navigationRunnable = this::proceedToNextScreen;
        handler.postDelayed(navigationRunnable, SPLASH_DURATION_MS);
    }

    private void proceedToNextScreen() {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        // Purely local check; no network or Firebase database calls
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        Intent intent;
        if (currentUser != null) {
            intent = new Intent(SplashActivity.this, HomeActivity.class);
        } else {
            intent = new Intent(SplashActivity.this, LoginActivity.class);
        }

        // Clear back stack completely so Back button exits rather than returning to Splash
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && navigationRunnable != null) {
            handler.removeCallbacks(navigationRunnable);
            navigationRunnable = null;
            handler = null;
        }
    }
}

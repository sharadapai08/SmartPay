package com.smartpay.app;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * BaseActivity - Shared base activity for all SmartPay activities.
 *
 * Provides a unified, robust solution for edge-to-edge system window insets:
 * - Content starts cleanly below the system status bar and above the system navigation bar.
 * - Prevents title bar and back button from overlapping the clock, battery, and signal icons.
 * - Prevents bottom buttons from overlapping the system navigation bar / gesture pill.
 * - Configures light status bar and navigation bar icons (dark text/icons) on light backgrounds.
 * - Enforces light mode (MODE_NIGHT_NO) globally across the entire app.
 */
public abstract class BaseActivity extends AppCompatActivity {

    static {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        setupEdgeToEdgeAndInsets();
    }

    protected void setupEdgeToEdgeAndInsets() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);
        controller.setAppearanceLightNavigationBars(true);
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        applySystemBarInsets(view);
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        applySystemBarInsets(findViewById(android.R.id.content));
    }

    protected void applySystemBarInsets(View targetView) {
        if (targetView == null) return;
        final int initialPaddingLeft = targetView.getPaddingLeft();
        final int initialPaddingTop = targetView.getPaddingTop();
        final int initialPaddingRight = targetView.getPaddingRight();
        final int initialPaddingBottom = targetView.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(targetView, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
            );
            v.setPadding(
                    initialPaddingLeft + insets.left,
                    initialPaddingTop + insets.top,
                    initialPaddingRight + insets.right,
                    initialPaddingBottom + insets.bottom
            );
            return windowInsets;
        });

        if (targetView.isAttachedToWindow()) {
            ViewCompat.requestApplyInsets(targetView);
        } else {
            targetView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    v.removeOnAttachStateChangeListener(this);
                    ViewCompat.requestApplyInsets(v);
                }

                @Override
                public void onViewDetachedFromWindow(View v) {}
            });
        }
    }
}

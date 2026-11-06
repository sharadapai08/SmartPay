package com.smartpay.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;
import com.smartpay.app.databinding.ActivityLoginBinding;

/**
 * LoginActivity - Handles User Login & Forgot Password
 *
 * Implements:
 * - Auto-login check: redirects already-authenticated users directly to HomeActivity
 * - Email and Password authentication via FirebaseAuth with timeout and failure handling
 * - Always stops progress bar and re-enables LOGIN button on both success and failure
 * - Shows real error message in Toast
 * - Password visibility toggle (eye / eye-off)
 * - Forgot Password reset email with failure handling
 * - Clean navigation to RegisterActivity for new users
 */
public class LoginActivity extends BaseActivity {

    private static final long TIMEOUT_MS = 15000; // 15-second timeout

    private ActivityLoginBinding binding;
    private FirebaseAuth mAuth;
    private Handler timeoutHandler;
    private Runnable timeoutRunnable;

    private boolean isPasswordVisible = false;
    private boolean isOperationInProgress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        timeoutHandler = new Handler(Looper.getMainLooper());

        // Auto-login check: If already logged in, navigate directly to HomeActivity
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            navigateToHome();
            return;
        }

        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupListeners();
    }

    private void setupListeners() {
        // Back arrow button
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnBack.setOnLongClickListener(v -> {
            binding.etLoginEmail.setText("alice.sharma@test.com");
            binding.etLoginPassword.setText("Password@123");
            return true;
        });

        // Password visibility toggle
        binding.btnToggleLoginPassword.setOnClickListener(v -> togglePasswordVisibility());

        // LOGIN button
        binding.btnLogin.setOnClickListener(v -> handleLogin());

        // Forgot Password link
        binding.tvForgotPassword.setOnClickListener(v -> handleForgotPassword());

        // Link to RegisterActivity
        binding.layoutRegisterLink.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });
    }

    /**
     * Toggles visibility of the login password field and updates the eye icon.
     */
    private void togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible;
        if (isPasswordVisible) {
            binding.etLoginPassword.setInputType(
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            binding.btnToggleLoginPassword.setImageResource(R.drawable.ic_eye_off);
        } else {
            binding.etLoginPassword.setInputType(
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            binding.btnToggleLoginPassword.setImageResource(R.drawable.ic_eye);
        }
        binding.etLoginPassword.setSelection(binding.etLoginPassword.getText().length());
    }

    /**
     * Validates input fields and authenticates with Firebase.
     */
    private void handleLogin() {
        if (isOperationInProgress) {
            return;
        }

        String email = binding.etLoginEmail.getText().toString().trim();
        String password = binding.etLoginPassword.getText().toString().trim();

        String emailError = Validators.validateEmail(email);
        if (emailError != null) {
            binding.etLoginEmail.setError(emailError);
            binding.etLoginEmail.requestFocus();
            return;
        }

        // Only check password is not empty (no complexity rules for login)
        if (TextUtils.isEmpty(password)) {
            binding.etLoginPassword.setError("Password is required");
            binding.etLoginPassword.requestFocus();
            return;
        }

        startOperation();

        // Setup timeout
        timeoutRunnable = () -> {
            if (isOperationInProgress) {
                stopOperation();
                Toast.makeText(LoginActivity.this,
                        "Login timed out. Please check your network connection and try again.",
                        Toast.LENGTH_LONG).show();
            }
        };
        timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_MS);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    cancelTimeout();
                    stopOperation();
                    Toast.makeText(LoginActivity.this, "Welcome to SmartPay!", Toast.LENGTH_SHORT).show();
                    navigateToHome();
                })
                .addOnFailureListener(e -> {
                    cancelTimeout();
                    stopOperation();
                    handleAuthError(e);
                });
    }

    /**
     * Prompts for or uses the login email to send a password reset link.
     */
    private void handleForgotPassword() {
        if (isOperationInProgress) {
            return;
        }

        String existingEmail = binding.etLoginEmail.getText().toString().trim();

        if (Validators.validateEmail(existingEmail) == null) {
            sendResetEmail(existingEmail);
        } else {
            final EditText input = new EditText(this);
            input.setHint("name@example.com");
            input.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            input.setPadding(40, 30, 40, 30);

            new AlertDialog.Builder(this)
                    .setTitle("Forgot Password")
                    .setMessage("Enter your registered email address to receive a password reset link:")
                    .setView(input)
                    .setPositiveButton("Send Link", (dialog, which) -> {
                        String email = input.getText().toString().trim();
                        if (Validators.validateEmail(email) != null) {
                            Toast.makeText(LoginActivity.this, "Please enter a valid email address.", Toast.LENGTH_SHORT).show();
                        } else {
                            sendResetEmail(email);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    private void sendResetEmail(String email) {
        startOperation();

        timeoutRunnable = () -> {
            if (isOperationInProgress) {
                stopOperation();
                Toast.makeText(LoginActivity.this,
                        "Password reset request timed out. Please try again.",
                        Toast.LENGTH_LONG).show();
            }
        };
        timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_MS);

        mAuth.sendPasswordResetEmail(email)
                .addOnSuccessListener(unused -> {
                    cancelTimeout();
                    stopOperation();
                    new AlertDialog.Builder(LoginActivity.this)
                            .setTitle("Email Sent")
                            .setMessage("A password reset link has been sent to " + email + ". Please check your inbox.")
                            .setPositiveButton("OK", null)
                            .show();
                })
                .addOnFailureListener(e -> {
                    cancelTimeout();
                    stopOperation();
                    handleAuthError(e);
                });
    }

    private void startOperation() {
        isOperationInProgress = true;
        if (binding.progressBar != null) {
            binding.progressBar.setVisibility(View.VISIBLE);
        }
        binding.btnLogin.setEnabled(false);
    }

    private void stopOperation() {
        isOperationInProgress = false;
        if (binding.progressBar != null) {
            binding.progressBar.setVisibility(View.GONE);
        }
        binding.btnLogin.setEnabled(true);
    }

    private void cancelTimeout() {
        if (timeoutHandler != null && timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
        }
    }

    /**
     * Translates Firebase auth errors into two clear, distinct toasts:
     * 1. Invalid email format or wrong password → credential error toast
     * 2. Email not registered in Firebase → account-not-found toast
     */
    private void handleAuthError(Exception e) {
        if (e instanceof FirebaseAuthInvalidUserException) {
            // Email does not exist in Firebase Authentication at all
            Toast.makeText(this,
                    "No SmartPay account found for this email. Please register first.",
                    Toast.LENGTH_LONG).show();

        } else if (e instanceof FirebaseAuthInvalidCredentialsException) {
            // Firebase gives us a machine-readable error code we can use to split the two sub-cases
            String errorCode = ((FirebaseAuthInvalidCredentialsException) e).getErrorCode();
            if ("ERROR_INVALID_EMAIL".equals(errorCode)) {
                Toast.makeText(this,
                        "Invalid email address format. Please check and try again.",
                        Toast.LENGTH_LONG).show();
            } else {
                // ERROR_WRONG_PASSWORD or any other credential mismatch
                Toast.makeText(this,
                        "Incorrect password. Please try again or use Forgot Password.",
                        Toast.LENGTH_LONG).show();
            }

        } else if (e instanceof FirebaseNetworkException) {
            Toast.makeText(this,
                    "No internet connection. Please verify your network and retry.",
                    Toast.LENGTH_LONG).show();

        } else {
            String msg = e.getLocalizedMessage() != null ? e.getLocalizedMessage() : "Operation failed";
            Toast.makeText(this, "Error: " + msg, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Clears backstack and launches HomeActivity.
     */
    private void navigateToHome() {
        Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelTimeout();
    }
}

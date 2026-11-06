package com.smartpay.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.smartpay.app.databinding.ActivityRegisterBinding;
import com.smartpay.app.R;

import java.util.HashMap;
import java.util.Map;

/**
 * RegisterActivity - Handles New User Registration (Slide 1)
 *
 * Implements:
 * - Validation: Full Name, Email, 10-digit Phone, Password (min 6 chars), Confirm Password
 * - Eye toggles for both Password and Confirm Password fields
 * - Uses shared FirebaseRefs for Realtime Database (asia-southeast1 regional instance)
 * - Firebase Authentication user creation with timeout and comprehensive failure handling
 * - Atomic write to users/{uid} and QRPayments/{uid} via multi-path update
 * - If database write fails, automatically deletes the just-created Auth user so the user can retry
 * - Re-enables SIGN UP button and hides progress bar on both success and failure
 * - Displays clear, real error messages in Toasts
 * - "Already have an account? Login" link returning to LoginActivity
 */
public class RegisterActivity extends BaseActivity {

    private static final long TIMEOUT_MS = 15000; // 15-second timeout for operations

    private ActivityRegisterBinding binding;
    private FirebaseAuth mAuth;
    private Handler timeoutHandler;
    private Runnable timeoutRunnable;

    private boolean isPasswordVisible = false;
    private boolean isConfirmPasswordVisible = false;
    private boolean isOperationInProgress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        timeoutHandler = new Handler(Looper.getMainLooper());

        setupListeners();
    }

    private void setupListeners() {
        // Back arrow button
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnBack.setOnLongClickListener(v -> {
            binding.etRegName.setText("Alice Sharma");
            binding.etRegEmail.setText("alice.sharma@test.com");
            binding.etRegPhone.setText("9876500001");
            binding.etRegPassword.setText("Password@123");
            binding.etRegConfirmPassword.setText("Password@123");
            return true;
        });

        // Live validation for password field
        binding.etRegPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                updatePasswordLiveValidation(s != null ? s.toString() : "");
            }
        });

        // Live validation for confirm password field
        binding.etRegConfirmPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                updateConfirmPasswordLiveValidation(s != null ? s.toString() : "");
            }
        });

        // Initialize checklist state to default
        updatePasswordLiveValidation(binding.etRegPassword.getText() != null
                ? binding.etRegPassword.getText().toString() : "");

        // Password visibility toggle
        binding.btnToggleRegPassword.setOnClickListener(v -> togglePasswordVisibility());

        // Confirm Password visibility toggle
        binding.btnToggleRegConfirmPassword.setOnClickListener(v -> toggleConfirmPasswordVisibility());

        // SIGN UP button
        binding.btnSignUp.setOnClickListener(v -> handleRegistration());

        // "Already have an account? Login" link
        binding.layoutLoginLink.setOnClickListener(v -> finish());
    }

    /**
     * Updates the password checklist, strength label, and error message live while typing.
     */
    private void updatePasswordLiveValidation(String password) {
        // 1. Checklist rules (evaluated via Validators)
        boolean minLen = Validators.hasMinLength(password);
        boolean upper = Validators.hasUppercase(password);
        boolean lower = Validators.hasLowercase(password);
        boolean digit = Validators.hasDigit(password);
        boolean special = Validators.hasSpecialChar(password);

        updateChecklistItem(binding.ivRuleMinLength, binding.tvRuleMinLength, minLen);
        updateChecklistItem(binding.ivRuleUppercase, binding.tvRuleUppercase, upper);
        updateChecklistItem(binding.ivRuleLowercase, binding.tvRuleLowercase, lower);
        updateChecklistItem(binding.ivRuleNumber, binding.tvRuleNumber, digit);
        updateChecklistItem(binding.ivRuleSpecial, binding.tvRuleSpecial, special);

        // 2. Strength label
        Validators.PasswordStrength strength = Validators.evaluatePasswordStrength(password);
        switch (strength) {
            case STRONG:
                binding.tvStrengthLabel.setText("Strong");
                binding.tvStrengthLabel.setTextColor(ContextCompat.getColor(this, R.color.strength_strong));
                break;
            case MEDIUM:
                binding.tvStrengthLabel.setText("Medium");
                binding.tvStrengthLabel.setTextColor(ContextCompat.getColor(this, R.color.strength_medium));
                break;
            case WEAK:
            case EMPTY:
            default:
                binding.tvStrengthLabel.setText("Weak");
                binding.tvStrengthLabel.setTextColor(ContextCompat.getColor(this, R.color.strength_weak));
                break;
        }

        // 3. Error on field
        if (password.isEmpty()) {
            binding.etRegPassword.setError(null);
        } else {
            String error = Validators.validatePassword(password);
            binding.etRegPassword.setError(error);
        }

        // 4. Live update confirm password matching if confirm password field has text
        String confirmPass = binding.etRegConfirmPassword.getText() != null
                ? binding.etRegConfirmPassword.getText().toString() : "";
        if (!confirmPass.isEmpty()) {
            updateConfirmPasswordLiveValidation(confirmPass);
        }
    }

    /**
     * Updates an individual checklist item's icon and text color.
     */
    private void updateChecklistItem(ImageView iv, TextView tv, boolean isSatisfied) {
        int colorMet = ContextCompat.getColor(this, R.color.success_green);
        int colorUnmet = ContextCompat.getColor(this, R.color.text_secondary);

        if (isSatisfied) {
            iv.setImageResource(R.drawable.ic_rule_met);
            tv.setTextColor(colorMet);
        } else {
            iv.setImageResource(R.drawable.ic_rule_unmet);
            tv.setTextColor(colorUnmet);
        }
    }

    /**
     * Validates confirm password live against the password field.
     */
    private void updateConfirmPasswordLiveValidation(String confirmPassword) {
        if (confirmPassword.isEmpty()) {
            binding.etRegConfirmPassword.setError(null);
            return;
        }

        String password = binding.etRegPassword.getText() != null
                ? binding.etRegPassword.getText().toString() : "";
        String error = Validators.validateConfirmPassword(password, confirmPassword);
        binding.etRegConfirmPassword.setError(error);
    }

    /**
     * Toggles visibility for the password field.
     */
    private void togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible;
        if (isPasswordVisible) {
            binding.etRegPassword.setInputType(
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            binding.btnToggleRegPassword.setImageResource(R.drawable.ic_eye_off);
        } else {
            binding.etRegPassword.setInputType(
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            binding.btnToggleRegPassword.setImageResource(R.drawable.ic_eye);
        }
        binding.etRegPassword.setSelection(binding.etRegPassword.getText().length());
    }

    /**
     * Toggles visibility for the confirm password field.
     */
    private void toggleConfirmPasswordVisibility() {
        isConfirmPasswordVisible = !isConfirmPasswordVisible;
        if (isConfirmPasswordVisible) {
            binding.etRegConfirmPassword.setInputType(
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            binding.btnToggleRegConfirmPassword.setImageResource(R.drawable.ic_eye_off);
        } else {
            binding.etRegConfirmPassword.setInputType(
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            binding.btnToggleRegConfirmPassword.setImageResource(R.drawable.ic_eye);
        }
        binding.etRegConfirmPassword.setSelection(binding.etRegConfirmPassword.getText().length());
    }

    /**
     * Validates input fields using centralized Validators and executes registration with Firebase Auth and Realtime Database.
     */
    private void handleRegistration() {
        if (isOperationInProgress) {
            return;
        }

        String name = binding.etRegName.getText().toString().trim();
        String email = binding.etRegEmail.getText().toString().trim();
        String phone = binding.etRegPhone.getText().toString().trim();
        String password = binding.etRegPassword.getText().toString();
        String confirmPassword = binding.etRegConfirmPassword.getText().toString();

        // 1. Validation: Full Name
        String nameError = Validators.validateName(name);
        if (nameError != null) {
            binding.etRegName.setError(nameError);
            binding.etRegName.requestFocus();
            return;
        }

        // 2. Validation: Email
        String emailError = Validators.validateEmail(email);
        if (emailError != null) {
            binding.etRegEmail.setError(emailError);
            binding.etRegEmail.requestFocus();
            return;
        }

        // 3. Validation: Phone (exactly 10 digits)
        String phoneError = Validators.validatePhone(phone);
        if (phoneError != null) {
            binding.etRegPhone.setError(phoneError);
            binding.etRegPhone.requestFocus();
            return;
        }

        // 4. Validation: Password (strong password rules)
        String passwordError = Validators.validatePassword(password);
        if (passwordError != null) {
            binding.etRegPassword.setError(passwordError);
            binding.etRegPassword.requestFocus();
            return;
        }

        // 5. Validation: Confirm Password (must match exactly)
        String confirmError = Validators.validateConfirmPassword(password, confirmPassword);
        if (confirmError != null) {
            binding.etRegConfirmPassword.setError(confirmError);
            binding.etRegConfirmPassword.requestFocus();
            return;
        }

        startOperation();

        // Schedule timeout
        timeoutRunnable = () -> {
            if (isOperationInProgress) {
                stopOperation();
                FirebaseUser currentUser = mAuth.getCurrentUser();
                if (currentUser != null) {
                    // Clean up created auth user if database timed out
                    currentUser.delete();
                }
                Toast.makeText(RegisterActivity.this,
                        "Registration timed out. Please check your internet connection and try again.",
                        Toast.LENGTH_LONG).show();
            }
        };
        timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_MS);

        // Step 1: Create Firebase Auth User
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    if (!isOperationInProgress) {
                        // Already timed out
                        if (authResult.getUser() != null) {
                            authResult.getUser().delete();
                        }
                        return;
                    }

                    FirebaseUser user = authResult.getUser();
                    if (user == null) {
                        cancelTimeout();
                        stopOperation();
                        Toast.makeText(RegisterActivity.this, "Authentication failed: No user created.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    String uid = user.getUid();
                    String upiId = FirebaseConstants.generateUpiId(phone);

                    // Step 2: Build Database Objects
                    UserModel newUser = new UserModel(
                            name,
                            email,
                            phone,
                            upiId,
                            FirebaseConstants.WELCOME_BALANCE,
                            System.currentTimeMillis()
                    );

                    QRPaymentModel qrModel = new QRPaymentModel(uid, name);

                    // Atomic multi-path update using shared FirebaseRefs
                    Map<String, Object> updates = new HashMap<>();
                    updates.put(FirebaseConstants.NODE_USERS + "/" + uid, newUser);
                    updates.put(FirebaseConstants.NODE_QR_PAYMENTS + "/" + uid, qrModel);

                    FirebaseRefs.getRootRef().updateChildren(updates)
                            .addOnSuccessListener(unused -> {
                                cancelTimeout();
                                stopOperation();
                                Toast.makeText(RegisterActivity.this, "Registration successful!", Toast.LENGTH_SHORT).show();
                                navigateToHome();
                            })
                            .addOnFailureListener(dbError -> {
                                cancelTimeout();
                                stopOperation();
                                String errorMsg = dbError.getMessage() != null ? dbError.getMessage() : "Database write failed";
                                Toast.makeText(RegisterActivity.this,
                                        "Database error: " + errorMsg + ". Cleaning up...",
                                        Toast.LENGTH_LONG).show();

                                // Rollback: Delete the created Auth user so the user can retry registration with same credentials
                                user.delete().addOnCompleteListener(deleteTask -> {
                                    Toast.makeText(RegisterActivity.this,
                                            "Account creation failed. Please retry.",
                                            Toast.LENGTH_SHORT).show();
                                });
                            });
                })
                .addOnFailureListener(authError -> {
                    cancelTimeout();
                    stopOperation();
                    handleAuthError(authError);
                });
    }

    private void startOperation() {
        isOperationInProgress = true;
        if (binding.progressBar != null) {
            binding.progressBar.setVisibility(View.VISIBLE);
        }
        binding.btnSignUp.setEnabled(false);
    }

    private void stopOperation() {
        isOperationInProgress = false;
        if (binding.progressBar != null) {
            binding.progressBar.setVisibility(View.GONE);
        }
        binding.btnSignUp.setEnabled(true);
    }

    private void cancelTimeout() {
        if (timeoutHandler != null && timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
        }
    }

    /**
     * Translates Firebase auth errors into clear, user-friendly messages.
     */
    private void handleAuthError(Exception e) {
        String msg = e.getLocalizedMessage() != null ? e.getLocalizedMessage() : "Registration failed";
        if (e instanceof FirebaseAuthUserCollisionException) {
            Toast.makeText(this, "An account with this email already exists. Please log in.", Toast.LENGTH_LONG).show();
        } else if (e instanceof FirebaseAuthWeakPasswordException) {
            Toast.makeText(this, "The password is too weak. Please choose a stronger password.", Toast.LENGTH_LONG).show();
        } else if (e instanceof FirebaseAuthInvalidCredentialsException) {
            Toast.makeText(this, "Invalid email address format.", Toast.LENGTH_LONG).show();
        } else if (e instanceof FirebaseNetworkException) {
            Toast.makeText(this, "No internet connection. Please verify your network and retry.", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Error: " + msg, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Signs out the current user and navigates to LoginActivity.
     */
    private void navigateToHome() {
        mAuth.signOut(); // Sign out so user returns to login screen, not home
        Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
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

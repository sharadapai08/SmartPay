package com.smartpay.app;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Centralized validator for user inputs and password security in SmartPay.
 * 
 * Rules for a new password:
 * - At least 6 characters
 * - At least one uppercase letter (A-Z)
 * - At least one lowercase letter (a-z)
 * - At least one digit (0-9)
 * - At least one special character (e.g. @ # $ % &amp; * ! ? _ - . ,)
 * - No spaces
 * - Confirm password must match exactly
 */
public final class Validators {

    private Validators() {
        // Prevent instantiation
    }

    private static final Pattern PATTERN_UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern PATTERN_LOWERCASE = Pattern.compile("[a-z]");
    private static final Pattern PATTERN_DIGIT = Pattern.compile("[0-9]");
    // Matches any special symbol that is not alphanumeric and not whitespace
    private static final Pattern PATTERN_SPECIAL = Pattern.compile("[^a-zA-Z0-9\\s]");
    private static final Pattern PATTERN_PHONE_10 = Pattern.compile("^[0-9]{10}$");
    private static final Pattern PATTERN_EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    /**
     * Checks if a string is null or empty.
     */
    private static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * Checks if password has at least 6 characters.
     */
    public static boolean hasMinLength(String password) {
        return password != null && password.length() >= 6;
    }

    /**
     * Checks if password has at least one uppercase letter (A-Z).
     */
    public static boolean hasUppercase(String password) {
        return password != null && PATTERN_UPPERCASE.matcher(password).find();
    }

    /**
     * Checks if password has at least one lowercase letter (a-z).
     */
    public static boolean hasLowercase(String password) {
        return password != null && PATTERN_LOWERCASE.matcher(password).find();
    }

    /**
     * Checks if password has at least one digit (0-9).
     */
    public static boolean hasDigit(String password) {
        return password != null && PATTERN_DIGIT.matcher(password).find();
    }

    /**
     * Checks if password has at least one special character.
     */
    public static boolean hasSpecialChar(String password) {
        return password != null && PATTERN_SPECIAL.matcher(password).find();
    }

    /**
     * Checks if password contains no spaces.
     */
    public static boolean hasNoSpaces(String password) {
        return password != null && !password.isEmpty() && !password.contains(" ");
    }

    /**
     * Validates a new password against all required security rules.
     * Returns a clear error message for the first rule that fails, or null if valid.
     *
     * @param password Password string to check
     * @return Error message for first failing rule, or null if all rules pass.
     */
    public static String validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            return "Password is required";
        }
        if (password.contains(" ")) {
            return "Password cannot contain spaces";
        }
        if (password.length() < 6) {
            return "Password must be at least 6 characters";
        }
        if (!hasUppercase(password)) {
            return "Add at least one uppercase letter";
        }
        if (!hasLowercase(password)) {
            return "Add at least one lowercase letter";
        }
        if (!hasDigit(password)) {
            return "Add at least one number";
        }
        if (!hasSpecialChar(password)) {
            return "Add at least one special character (@ # $ % & * !)";
        }
        return null;
    }

    /**
     * Alias for validatePassword for backwards and alternate naming compatibility.
     */
    public static String getPasswordValidationError(String password) {
        return validatePassword(password);
    }

    /**
     * Returns true if the password satisfies all strong password rules.
     */
    public static boolean isPasswordStrong(String password) {
        return validatePassword(password) == null;
    }

    /**
     * Password strength categorization.
     */
    public enum PasswordStrength {
        EMPTY("Weak", 0),
        WEAK("Weak", 1),
        MEDIUM("Medium", 2),
        STRONG("Strong", 3);

        private final String label;
        private final int level;

        PasswordStrength(String label, int level) {
            this.label = label;
            this.level = level;
        }

        public String getLabel() {
            return label;
        }

        public int getLevel() {
            return level;
        }
    }

    /**
     * Evaluates password strength based on the satisfied rules and length.
     */
    public static PasswordStrength evaluatePasswordStrength(String password) {
        if (password == null || password.isEmpty()) {
            return PasswordStrength.EMPTY;
        }

        int score = 0;
        if (hasMinLength(password)) score++;
        if (hasUppercase(password)) score++;
        if (hasLowercase(password)) score++;
        if (hasDigit(password)) score++;
        if (hasSpecialChar(password)) score++;

        if (password.contains(" ") || score <= 2 || password.length() < 6) {
            return PasswordStrength.WEAK;
        } else if (score == 5) {
            return PasswordStrength.STRONG;
        } else {
            return PasswordStrength.MEDIUM;
        }
    }

    /**
     * Validates confirm password against original password.
     *
     * @param password Original password
     * @param confirmPassword Confirmation password
     * @return Error message, or null if matching.
     */
    public static String validateConfirmPassword(String password, String confirmPassword) {
        if (confirmPassword == null || confirmPassword.isEmpty()) {
            return "Please confirm your password";
        }
        if (!Objects.equals(password, confirmPassword)) {
            return "Passwords do not match";
        }
        return null;
    }

    /**
     * Alias for validateConfirmPassword.
     */
    public static String getConfirmPasswordError(String password, String confirmPassword) {
        return validateConfirmPassword(password, confirmPassword);
    }

    /**
     * Returns true if passwords match exactly and are non-empty.
     */
    public static boolean doPasswordsMatch(String password, String confirmPassword) {
        return validateConfirmPassword(password, confirmPassword) == null;
    }

    /**
     * Validates full name.
     */
    public static String validateName(String name) {
        if (isBlank(name)) {
            return "Full name is required";
        }
        return null;
    }

    /**
     * Validates email address format.
     */
    public static String validateEmail(String email) {
        if (isBlank(email)) {
            return "Email address is required";
        }
        if (!PATTERN_EMAIL.matcher(email.trim()).matches()) {
            return "Please enter a valid email address";
        }
        return null;
    }

    /**
     * Validates 10-digit mobile phone number.
     */
    public static String validatePhone(String phone) {
        if (isBlank(phone)) {
            return "Phone number is required";
        }
        String trimmed = phone.trim();
        if (trimmed.length() != 10 || !PATTERN_PHONE_10.matcher(trimmed).matches()) {
            return "Phone number must be exactly 10 digits";
        }
        return null;
    }
}

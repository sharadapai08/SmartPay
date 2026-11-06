package com.smartpay.app;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests verifying strong password validation rules in Validators.
 */
public class ValidatorsTest {

    @Test
    public void testEmptyPassword() {
        assertEquals("Password is required", Validators.validatePassword(""));
        assertEquals("Password is required", Validators.validatePassword(null));
    }

    @Test
    public void testPasswordWithSpaces() {
        assertEquals("Password cannot contain spaces", Validators.validatePassword("Pass word@123"));
        assertEquals("Password cannot contain spaces", Validators.validatePassword(" Password@123"));
        assertEquals("Password cannot contain spaces", Validators.validatePassword("Password@123 "));
    }

    @Test
    public void testPasswordLessThan6Characters() {
        assertEquals("Password must be at least 6 characters", Validators.validatePassword("P@s1"));
        assertEquals("Password must be at least 6 characters", Validators.validatePassword("Ab1!"));
    }

    @Test
    public void testPasswordMissingUppercase() {
        assertEquals("Add at least one uppercase letter", Validators.validatePassword("password@123"));
    }

    @Test
    public void testPasswordMissingLowercase() {
        assertEquals("Add at least one lowercase letter", Validators.validatePassword("PASSWORD@123"));
    }

    @Test
    public void testPasswordMissingNumber() {
        assertEquals("Add at least one number", Validators.validatePassword("Password@!"));
    }

    @Test
    public void testPasswordMissingSpecialChar() {
        assertEquals("Add at least one special character (@ # $ % & * !)", Validators.validatePassword("Password123"));
    }

    @Test
    public void testValidStrongPasswords() {
        assertNull(Validators.validatePassword("Password@123"));
        assertNull(Validators.validatePassword("SmartPay#2026"));
        assertNull(Validators.validatePassword("Secure$99x"));
        assertNull(Validators.validatePassword("P.a-s_s*1"));
        assertTrue(Validators.isPasswordStrong("Password@123"));
    }

    @Test
    public void testPasswordStrengthEvaluation() {
        assertEquals(Validators.PasswordStrength.EMPTY, Validators.evaluatePasswordStrength(""));
        assertEquals(Validators.PasswordStrength.WEAK, Validators.evaluatePasswordStrength("pass"));
        assertEquals(Validators.PasswordStrength.WEAK, Validators.evaluatePasswordStrength("password"));
        assertEquals(Validators.PasswordStrength.MEDIUM, Validators.evaluatePasswordStrength("Password123")); // 4 rules: len, upper, lower, digit
        assertEquals(Validators.PasswordStrength.STRONG, Validators.evaluatePasswordStrength("Password@123")); // all 5 rules
    }

    @Test
    public void testConfirmPassword() {
        assertEquals("Please confirm your password", Validators.validateConfirmPassword("Password@123", ""));
        assertEquals("Passwords do not match", Validators.validateConfirmPassword("Password@123", "Password@124"));
        assertNull(Validators.validateConfirmPassword("Password@123", "Password@123"));
        assertTrue(Validators.doPasswordsMatch("Password@123", "Password@123"));
    }

    @Test
    public void testPhoneValidation() {
        assertEquals("Phone number is required", Validators.validatePhone(""));
        assertEquals("Phone number must be exactly 10 digits", Validators.validatePhone("12345"));
        assertEquals("Phone number must be exactly 10 digits", Validators.validatePhone("98765432101"));
        assertEquals("Phone number must be exactly 10 digits", Validators.validatePhone("987654321a"));
        assertNull(Validators.validatePhone("9876500001"));
    }

    @Test
    public void testNameValidation() {
        assertEquals("Full name is required", Validators.validateName(""));
        assertEquals("Full name is required", Validators.validateName("   "));
        assertNull(Validators.validateName("John Doe"));
    }
}

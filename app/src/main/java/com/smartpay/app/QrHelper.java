package com.smartpay.app;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.util.Base64;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * QrHelper - Utility class for SmartPay QR code generation, payload parsing, and image decoding.
 *
 * Implements:
 * - QR generation using com.google.zxing:core.
 * - Standard SmartPay URI payload builder: smartpay://pay?uid=<uid>&name=<name>
 * - Strict SmartPay URI payload validation and extraction.
 * - Bitmap decoding for gallery image picker via ZXing core.
 */
public final class QrHelper {

    private static final String SMARTPAY_SCHEME = "smartpay";
    private static final String SMARTPAY_HOST = "pay";
    private static final String PARAM_UID = "uid";
    private static final String PARAM_NAME = "name";
    private static final String PARAM_UPI = "upi"; // Base64-encoded UPI ID

    private QrHelper() {
        // Prevent instantiation
    }

    /**
     * Generates a square QR code Bitmap from the given content string using ZXing core.
     *
     * @param content The text/payload to encode.
     * @param size    Width and height in pixels.
     * @return A square Bitmap containing the QR code, or null on failure.
     */
    public static Bitmap generate(String content, int size) {
        if (content == null || content.trim().isEmpty() || size <= 0) {
            return null;
        }

        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1); // small padding

            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints);

            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Builds the standard SmartPay QR payload with the UPI ID Base64-encoded:
     * smartpay://pay?uid=<uid>&name=<URL-encoded name>&upi=<base64 upi>
     *
     * @param uid    Firebase UID of the recipient.
     * @param name   Recipient's display name.
     * @param upiId  Recipient's UPI ID (Base64-encoded for obfuscation).
     * @return URI formatted payload string.
     */
    public static String buildPayload(String uid, String name, String upiId) {
        if (uid == null) {
            return "";
        }
        String encodedName = "";
        String encodedUpi = "";
        try {
            encodedName = URLEncoder.encode(name != null ? name : "", "UTF-8");
            // Base64-encode the UPI ID so it is not stored as plain text in the QR
            String upi = upiId != null ? upiId : "";
            encodedUpi = URLEncoder.encode(
                    Base64.encodeToString(upi.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP),
                    "UTF-8");
        } catch (UnsupportedEncodingException ignored) {
        }
        return SMARTPAY_SCHEME + "://" + SMARTPAY_HOST
                + "?" + PARAM_UID + "=" + uid.trim()
                + "&" + PARAM_NAME + "=" + encodedName
                + "&" + PARAM_UPI + "=" + encodedUpi;
    }

    /**
     * Backward-compatible overload: builds payload without UPI encoding.
     */
    public static String buildPayload(String uid, String name) {
        return buildPayload(uid, name, "");
    }

    /**
     * Parses the QR code text and extracts the receiver's UID.
     * Returns null if the text is not a valid SmartPay payload (wrong scheme, wrong host, or missing uid).
     *
     * @param text Raw scanned QR text.
     * @return Extracted UID, or null if invalid.
     */
    public static String parsePayload(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        try {
            Uri uri = Uri.parse(text.trim());
            if (!SMARTPAY_SCHEME.equalsIgnoreCase(uri.getScheme())) {
                return null;
            }
            if (!SMARTPAY_HOST.equalsIgnoreCase(uri.getHost())) {
                return null;
            }

            String uid = uri.getQueryParameter(PARAM_UID);
            if (uid == null || uid.trim().isEmpty()) {
                return null;
            }
            return uid.trim();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Decodes a QR code from a Bitmap image (e.g. selected from device Gallery).
     *
     * @param bitmap The image Bitmap to decode.
     * @return The decoded string content, or null if no QR code was recognized.
     */
    public static String decodeFromBitmap(Bitmap bitmap) {
        if (bitmap == null) return null;

        try {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

            RGBLuminanceSource source = new RGBLuminanceSource(width, height, pixels);
            BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));

            MultiFormatReader reader = new MultiFormatReader();
            Result result = reader.decode(binaryBitmap);
            return result != null ? result.getText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}

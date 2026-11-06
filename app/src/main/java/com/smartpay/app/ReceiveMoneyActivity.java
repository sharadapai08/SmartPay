package com.smartpay.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.smartpay.app.databinding.ActivityReceiveMoneyBinding;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * ReceiveMoneyActivity - Full Receive Money flow.
 *
 * QR payload embeds the UPI ID Base64-encoded for obfuscation.
 * Waiting overlay is triggered only when another account actually scans and
 * opens this QR (QrPayActivity writes scannedAt to QRPayments/{uid}), not on
 * an arbitrary timer.
 */
public class ReceiveMoneyActivity extends BaseActivity {

    private static final String KEY_HAS_RECEIVED = "key_has_received";
    private static final String KEY_TX_ID = "key_tx_id";
    private static final String KEY_SENDER_NAME = "key_sender_name";
    private static final String KEY_SENDER_UPI = "key_sender_upi";
    private static final String KEY_AMOUNT = "key_amount";
    private static final String KEY_TIMESTAMP = "key_timestamp";

    private ActivityReceiveMoneyBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;

    // Database references & listeners
    private DatabaseReference userRef;
    private ValueEventListener userListener;
    private Query incomingTxQuery;
    private ChildEventListener incomingTxListener;

    // Listener that fires when another account scans and opens this QR
    private DatabaseReference scannedAtRef;
    private ValueEventListener scannedAtListener;

    // Current user state
    private String userName = "SmartPay User";
    private String userUpiId = "";
    private Bitmap currentQrBitmap = null;

    // Flow control & timestamp tracking
    private long screenOpenTime = 0;
    private boolean hasReceivedPayment = false;
    private final Handler waitingHandler = new Handler(Looper.getMainLooper());

    // Cached received transaction details for rotation handling
    private String receivedTxId = "";
    private String receivedSenderName = "";
    private String receivedSenderUpi = "";
    private double receivedAmount = 0.0;
    private long receivedTimestamp = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityReceiveMoneyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, "Session expired. Please log in.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        screenOpenTime = System.currentTimeMillis();

        if (savedInstanceState != null) {
            hasReceivedPayment = savedInstanceState.getBoolean(KEY_HAS_RECEIVED, false);
            if (hasReceivedPayment) {
                receivedTxId = savedInstanceState.getString(KEY_TX_ID, "");
                receivedSenderName = savedInstanceState.getString(KEY_SENDER_NAME, "");
                receivedSenderUpi = savedInstanceState.getString(KEY_SENDER_UPI, "");
                receivedAmount = savedInstanceState.getDouble(KEY_AMOUNT, 0.0);
                receivedTimestamp = savedInstanceState.getLong(KEY_TIMESTAMP, 0);
            }
        }

        setupUI();
        loadUserAndGenerateQr();

        if (hasReceivedPayment) {
            displayPaymentReceivedScreen();
        } else {
            // Show waiting overlay only when another account scans this QR (no timer)
            setupScannedAtListener();
            setupIncomingPaymentListener();
        }
    }

    private void setupUI() {
        // Back button
        binding.btnBack.setOnClickListener(v -> finish());

        // Step 4: Top-right menu popup (ADD MONEY, SEND MONEY, PROFILE)
        binding.btnMore.setOnClickListener(this::showPopupMenu);

        // Share QR Button
        binding.btnShareQr.setOnClickListener(v -> shareQrCode());

        // Step 2: Waiting dialog CLOSE button
        binding.btnCloseWaiting.setOnClickListener(v -> binding.layoutWaiting.setVisibility(View.GONE));

        // Step 3: Action Buttons
        binding.btnDone.setOnClickListener(v -> {
            Intent homeIntent = new Intent(ReceiveMoneyActivity.this, HomeActivity.class);
            homeIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(homeIntent);
            finish();
        });

        binding.btnViewTransactions.setOnClickListener(v -> {
            Intent txIntent = new Intent(ReceiveMoneyActivity.this, TransactionsActivity.class);
            startActivity(txIntent);
        });
    }

    /**
     * Step 4: Popup menu on top-right icon.
     */
    private void showPopupMenu(View anchorView) {
        PopupMenu popup = new PopupMenu(this, anchorView);
        popup.getMenuInflater().inflate(R.menu.menu_receive_options, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_add_money) {
                startActivity(new Intent(ReceiveMoneyActivity.this, AddMoneyActivity.class));
                return true;
            } else if (id == R.id.menu_send_money) {
                startActivity(new Intent(ReceiveMoneyActivity.this, SendMoneyActivity.class));
                return true;
            } else if (id == R.id.menu_scan_qr) {
                startActivity(new Intent(ReceiveMoneyActivity.this, QrScannerActivity.class));
                return true;
            } else if (id == R.id.menu_transactions) {
                startActivity(new Intent(ReceiveMoneyActivity.this, TransactionsActivity.class));
                return true;
            } else if (id == R.id.menu_profile) {
                startActivity(new Intent(ReceiveMoneyActivity.this, ProfileActivity.class));
                return true;
            }
            return false;
        });
        popup.show();
    }

    /**
     * Step 1: Loads user profile from Firebase and renders live QR code.
     */
    private void loadUserAndGenerateQr() {
        String uid = currentUser.getUid();
        userRef = FirebaseRefs.getUserRef(uid);
        userListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                UserModel user = snapshot.getValue(UserModel.class);
                if (user != null) {
                    userName = user.getName() != null ? user.getName() : "SmartPay User";
                    userUpiId = user.getUpiId() != null ? user.getUpiId() : FirebaseConstants.generateUpiId(user.getPhone());

                    // Uppercase name as specified in Step 1
                    binding.tvUserName.setText(userName.toUpperCase(Locale.getDefault()));
                    binding.tvUpiId.setText(userUpiId);

                    // Generate live QR bitmap — UPI ID is Base64-encoded inside the payload
                    String payload = QrHelper.buildPayload(uid, userName, userUpiId);
                    currentQrBitmap = QrHelper.generate(payload, 600);
                    if (currentQrBitmap != null) {
                        binding.ivQrCode.setImageBitmap(currentQrBitmap);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Ignore cancel
            }
        };
        userRef.addValueEventListener(userListener);
    }

    /**
     * Step 1: Saves QR bitmap to app cache and launches system share sheet via FileProvider.
     */
    private void shareQrCode() {
        if (currentQrBitmap == null) {
            Toast.makeText(this, "Generating QR code… please wait", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            File imagesFolder = new File(getCacheDir(), "images");
            if (!imagesFolder.exists()) {
                imagesFolder.mkdirs();
            }

            File qrFile = new File(imagesFolder, "smartpay_qr.png");
            FileOutputStream fos = new FileOutputStream(qrFile);
            currentQrBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();

            Uri contentUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    qrFile
            );

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/png");
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Pay me on SmartPay: " + userUpiId);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Share QR Code"));
        } catch (IOException e) {
            Toast.makeText(this, "Failed to share QR: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Listens to QRPayments/{uid}/scannedAt written by QrPayActivity when the QR is
     * opened in another account. Shows the waiting overlay only at that moment.
     */
    private void setupScannedAtListener() {
        scannedAtRef = FirebaseRefs.getQrPaymentsRef().child(currentUser.getUid()).child("scannedAt");
        scannedAtListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (hasReceivedPayment || !snapshot.exists()) return;
                Long scannedAt = snapshot.getValue(Long.class);
                if (scannedAt != null && scannedAt >= (screenOpenTime - 2000)) {
                    // QR was just scanned by another account — show the waiting overlay
                    if (!isFinishing() && !isDestroyed()) {
                        binding.layoutWaiting.setVisibility(View.VISIBLE);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        };
        scannedAtRef.addValueEventListener(scannedAtListener);
    }

    /**
     * Step 2: Listens for incoming transactions created after screenOpenTime.
     */
    private void setupIncomingPaymentListener() {
        incomingTxQuery = FirebaseRefs.getTransactionsRef()
                .orderByChild("receiverId")
                .equalTo(currentUser.getUid());

        incomingTxListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, String previousChildName) {
                if (hasReceivedPayment || !snapshot.exists()) return;

                TransactionModel tx = snapshot.getValue(TransactionModel.class);
                if (tx != null) {
                    // Check that the transaction was created after this screen opened (allow 2s clock skew)
                    if (tx.getTimestamp() >= (screenOpenTime - 2000)) {
                        String type = tx.getType();
                        String status = tx.getStatus();

                        if (FirebaseConstants.STATUS_SUCCESS.equals(status) &&
                                (FirebaseConstants.TYPE_RECEIVED.equals(type) || FirebaseConstants.TYPE_QR_PAYMENT.equals(type))) {
                            handlePaymentReceived(tx);
                        }
                    }
                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, String previousChildName) { }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) { }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, String previousChildName) { }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        };
        incomingTxQuery.addChildEventListener(incomingTxListener);
    }

    /**
     * Step 3: Transition to Payment Received Screen.
     */
    private void handlePaymentReceived(@NonNull TransactionModel tx) {
        if (hasReceivedPayment) return;
        hasReceivedPayment = true;

        // Dismiss waiting overlay
        waitingHandler.removeCallbacksAndMessages(null);
        binding.layoutWaiting.setVisibility(View.GONE);

        // Cache details
        receivedTxId = tx.getTransactionId();
        receivedSenderName = tx.getSenderName() != null ? tx.getSenderName() : "Sender";
        receivedSenderUpi = tx.getSenderUpi() != null ? tx.getSenderUpi() : "";
        receivedAmount = tx.getAmount();
        receivedTimestamp = tx.getTimestamp() > 0 ? tx.getTimestamp() : System.currentTimeMillis();

        displayPaymentReceivedScreen();
    }

    private void displayPaymentReceivedScreen() {
        binding.layoutWaiting.setVisibility(View.GONE);
        binding.layoutReceived.setVisibility(View.VISIBLE);

        // Format Date & Time
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
        binding.tvReceivedDate.setText(sdf.format(new Date(receivedTimestamp)));

        // From (sender name & UPI ID)
        String fromText = receivedSenderName;
        if (!TextUtils.isEmpty(receivedSenderUpi)) {
            fromText += " (" + receivedSenderUpi + ")";
        }
        binding.tvReceivedFrom.setText(fromText);

        // UPI Transaction ID
        binding.tvUpiTxId.setText(!TextUtils.isEmpty(receivedTxId) ? receivedTxId : "TX" + receivedTimestamp);

        // Paid to
        binding.tvPaidTo.setText(!TextUtils.isEmpty(userUpiId) ? userUpiId : (userName + FirebaseConstants.UPI_SUFFIX));

        // Amount Received in Indian Rupee format
        binding.tvReceivedAmount.setText(CurrencyHelper.formatRupees(receivedAmount));

        // Animate Green checkmark
        binding.ivCheckReceived.setScaleX(0f);
        binding.ivCheckReceived.setScaleY(0f);
        binding.ivCheckReceived.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(450)
                .setInterpolator(new OvershootInterpolator())
                .start();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_HAS_RECEIVED, hasReceivedPayment);
        if (hasReceivedPayment) {
            outState.putString(KEY_TX_ID, receivedTxId);
            outState.putString(KEY_SENDER_NAME, receivedSenderName);
            outState.putString(KEY_SENDER_UPI, receivedSenderUpi);
            outState.putDouble(KEY_AMOUNT, receivedAmount);
            outState.putLong(KEY_TIMESTAMP, receivedTimestamp);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        cleanupListeners();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanupListeners();
    }

    private void cleanupListeners() {
        waitingHandler.removeCallbacksAndMessages(null);

        if (userRef != null && userListener != null) {
            userRef.removeEventListener(userListener);
            userListener = null;
        }

        if (scannedAtRef != null && scannedAtListener != null) {
            scannedAtRef.removeEventListener(scannedAtListener);
            scannedAtListener = null;
        }

        if (incomingTxQuery != null && incomingTxListener != null) {
            incomingTxQuery.removeEventListener(incomingTxListener);
            incomingTxListener = null;
        }
    }
}

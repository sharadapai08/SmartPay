# SmartPay – Digital Payment Application

**Course Project Submission**  
**Academic Batch:** Batch 2, Team 4  

### Project Team Members
| Sl. No. | Student Name | University Roll Number |
|:---:|:---|:---:|
| 1 | **Ananya Shetty** | 251090700167 |
| 2 | **Aruna Patil** | 251090700144 |
| 3 | **Ashwin Maharaja** | 251090700145 |
| 4 | **Sharada** | 251090700120 |
| 5 | **Sannidhi** | 251090700199 |

---

> [!IMPORTANT]
> **DEMO PAYMENT NOTICE:**  
> SmartPay is an educational Android application designed solely for academic evaluation and demonstration. All monetary figures, wallet balances, UPI transfers, and payment processing are **simulated demo transactions**. No real financial accounts, actual bank integrations, or legal tenders are accessed, processed, or exchanged.

---

## 1. Project Overview & Objectives
Digital payment applications provide users with a secure and convenient interface to manage funds and perform cashless financial activities. **SmartPay** is an Android-based digital wallet and peer-to-peer payment application built with **Java**, **XML**, and **Google Firebase (Authentication & Realtime Database)**.

The project demonstrates a production-grade architecture that adheres strictly to the official design specifications, featuring a tailored **Navy & Blue** visual palette (`#0B1F4D` Primary, `#1E5EFF` Accent, `#F5F7FB` Background), robust client-side input validations, salted SHA-256 PIN security, live real-time synchronization, and strict database access rules.

---

## 2. Features Mapped to System Modules

### Module 1 – User Authentication Module
- **User Registration:** Simple, secure registration capturing Full Name, Email, 10-digit Phone Number, and Password. Live validation verifies email format, Indian 10-digit mobile numbers, and enforces strong password criteria (uppercase, lowercase, number, special character, no whitespace, minimum 6 characters).
- **Auto-generated UPI ID:** Automatically creates a unique UPI handle in the format `<phone>@smartpay` upon successful registration.
- **Secure Login:** Email/Password authentication using Firebase Authentication with automated session restoration.
- **Password Reset:** Integrated "Forgot Password" self-service sending verification links via Firebase.

### Module 2 – Digital Wallet Module
- **Home Dashboard:** High-contrast wallet balance card featuring a **Tap to View Balance** privacy mask requiring a 4-digit Wallet PIN.
- **Salted SHA-256 PIN Security:** PINs are never stored in plaintext. They are hashed using SHA-256 with an individual cryptographic salt and saved exclusively in the private `userPins/{uid}` database node.
- **PIN Lockout Mechanism:** Blocks authorization for 30 seconds after 3 consecutive incorrect PIN entries to prevent brute-force attacks.
- **Add Money Flow:** Allows wallet top-up (up to ₹50,000 per transaction) via:
  - **UPI ID / Bank:** Supports manual UPI ID entry with instant format validation and selectable top banks (SBI, HDFC, ICICI, Axis).
  - **Debit / Credit Card:** Features live card formatting (`#### #### #### ####`), Luhn algorithm (Mod 10) validation, future expiry checking (`MM/YY`), and 3-digit CVV checks. Card digits and CVVs are strictly processed in memory and never logged or persisted.

### Module 3 – Send Money Module
- **Recipient Directory Lookup:** Live single-event lookup by registered phone number or UPI handle against `users` using indexed child queries.
- **Self-Transfer Prevention:** Prohibits transferring funds to oneself.
- **3-Step Transfer Execution:**
  1. *Details Entry:* Recipient lookup, amount (₹1 to ₹50,000 limit, balance sufficiency check), and optional note.
  2. *Confirmation Screen:* Displays recipient name, UPI ID, formatted transfer amount, and authorization prompt.
  3. *Atomic Commit:* Verifies wallet PIN, checks server-side balance sufficiency via atomic Firebase transaction, deducts sender, credits recipient, writes dual immutable records, and dispatches an instant notification.
- **Digital Receipt:** Detailed success screen with generated Transaction ID, receiver name, amount, timestamp, and navigation to Home or Transaction History.

### Module 4 – QR Payment Module
- **Integrated QR Scanner:** Fast, full-screen camera scanning powered by `zxing-android-embedded` with customized scanning reticle, flash toggle, and orientation handling.
- **Payload Parsing & Verification:** Parses SmartPay URI schemes (`smartpay://pay?uid=<uid>&name=<name>`) and fetches the recipient's verified database record directly from `users/{uid}`, avoiding reliance on unverified QR labels.
- **Unified Transfer Pipeline:** Reuses the verified transfer engine with transaction type `QR_PAYMENT`.

### Module 5 – Transaction History Module
- **5-Column History Table:** Matches the project specification (`Type | Date & Time | Description | Amount | Status`).
- **Live Real-time Sync:** Subscribes to database queries filtered by `senderId` and `receiverId`.
- **Filtering & Search:** Real-time filter tabs (`ALL`, `SENT`, `RECEIVED`, `FAILED`), live keyword search across recipient names, notes, and Transaction IDs, and multiple sort modes (Newest, Oldest, Amount High-to-Low, Amount Low-to-High).
- **Transaction Details Dialog:** Modal dialog displaying full audit metadata including Transaction ID, Sender/Receiver UPI IDs, date/time, amount, and status.

### Module 6 – Profile Module
- **User Information:** Displays user avatar, registered full name, email, phone number, default UPI ID, and linked bank account information.
- **Logout Flow:** Interactive bottom-sheet confirmation with red logout action clearing Firebase sessions and safely resetting back stacks.

### Additional Modules:
- **Receive Money Screen:** Generates high-definition QR codes dynamically via ZXing, displays user UPI ID, provides a native **Share QR** sheet using Android `FileProvider`, and listens for incoming real-time transactions to trigger an instant received alert.
- **In-App Notifications:** Real-time notification feed tracking received funds, featuring unread badge indicators on the home screen and automatic mark-as-read capability.

---

## 3. Technology Stack

| Layer | Component | Details |
|:---|:---|:---|
| **Platform** | Android | Minimum SDK: 24 (Android 7.0) / Target & Compile SDK: 37 |
| **Language** | Java (JDK 17) | Object-oriented clean architecture |
| **UI Framework** | Native Android XML | Material Design Components, ConstraintLayout, ViewBinding |
| **Authentication** | Firebase Auth | Email & Password identity management |
| **Cloud Database** | Firebase Realtime Database | Real-time NoSQL cloud database with fine-grained security rules |
| **QR Code Scanner** | ZXing Core & Embedded | `com.google.zxing:core:3.5.3` and `com.journeyapps:zxing-android-embedded:4.3.0` |
| **Build System** | Gradle (Kotlin DSL `.kts`) | Android Gradle Plugin 8.9.0 |

---

## 4. Firebase Database Schema

The database structure is designed according to least-privilege principles and separation of concerns:

```text
smartpay-database-root/
│
├── users/
│   └── {uid}/
│       ├── name: "Alice Sharma"          // string, 1-50 chars
│       ├── email: "alice@test.com"       // string
│       ├── phone: "9876543210"           // string, exactly 10 digits
│       ├── upiId: "9876543210@smartpay"  // string
│       ├── balance: 5000.00              // number >= 0
│       └── createdAt: 1712000000000      // number
│
├── userPins/
│   └── {uid}/                            // Owner read/write only
│       ├── pinHash: "<64-char hex>"      // salted SHA-256 hash
│       └── pinSalt: "<32-char hex>"      // 16-byte random salt
│
├── transactions/
│   └── {transactionId}/                  // Create-only, immutable
│       ├── senderId: "{uid}"             // string
│       ├── receiverId: "{uid}"           // string
│       ├── senderName: "Alice Sharma"    // string
│       ├── receiverName: "Bob Patel"     // string
│       ├── senderUpi: "9876543210@smartpay"
│       ├── receiverUpi: "9812345678@smartpay"
│       ├── amount: 500.00                // number > 0 and <= 50,000
│       ├── type: "SENT"                  // "SENT" | "RECEIVED" | "ADD_MONEY" | "QR_PAYMENT"
│       ├── status: "SUCCESS"             // "SUCCESS" | "FAILED"
│       ├── note: "Dinner bill"           // string
│       └── timestamp: 1712000000000      // number
│
├── userTransactions/
│   └── {uid}/
│       └── {transactionId}: true         // per-user transaction index
│
├── notifications/
│   └── {uid}/
│       └── {notificationId}/
│           ├── title: "Money received"
│           ├── message: "Received ₹500 from Alice Sharma"
│           ├── amount: 500.00
│           ├── timestamp: 1712000000000
│           └── read: false
│
└── QRPayments/
    └── {uid}/
        ├── userId: "{uid}"
        └── userName: "Alice Sharma"
```

---

## 5. Setup & Installation Instructions

### Prerequisites
- **Android Studio** (Koala, Ladybug, or later recommended).
- **JDK 17** configured as the Gradle JDK.
- An Android Device or Emulator running **Android API 24 or higher**.

### Step 1: Clone the Repository
```bash
git clone https://github.com/<your-repo>/SmartPay.git
cd SmartPay
```

### Step 2: Configure Firebase Project
1. Open the [Firebase Console](https://console.firebase.google.com/) and create a new project named `SmartPay`.
2. Add an Android app with Package Name: **`com.smartpay.app`**.
3. Download the configuration file `google-services.json`.
4. Place the downloaded file into the `app/` folder (`e:\SmartPay\app\google-services.json`).
5. In Firebase Console:
   - Go to **Build > Authentication > Sign-in method** and enable **Email/Password**.
   - Go to **Build > Realtime Database** and create a database (select Singapore `asia-southeast1` or your preferred region).
   - Go to the **Rules** tab, paste the complete rules from `database.rules.json`, and click **Publish**.

### Step 3: Build & Install the App
From Android Studio:
- Click **Sync Project with Gradle Files**.
- Select target device (emulator or physical phone via USB debugging).
- Click **Run 'app'** (`Shift + F10`).

From Command Line:
```powershell
./gradlew clean assembleDebug
./gradlew installDebug
```

---

## 6. Two-Account Testing Guide

To test the complete end-to-end payment workflow, create two separate accounts using either two devices/emulators or by logging out and switching accounts:

### Test Data Reference Table
| Field | Account 1 (Sender / Alice) | Account 2 (Receiver / Bob) |
|:---|:---|:---|
| **Full Name** | Alice Sharma | Bob Patel |
| **Email** | `alice@test.com` | `bob@test.com` |
| **Phone Number** | `9876543210` | `9812345678` |
| **UPI ID** | `9876543210@smartpay` | `9812345678@smartpay` |
| **Password** | `SmartPay@123` | `SmartPay@123` |
| **Wallet PIN** | `1234` | `4321` |

### Step-by-Step Test Procedure:
1. **Register Both Users:** Register Alice with phone `9876543210` and Bob with phone `9812345678`.
2. **Top Up Wallet (Alice):**
   - Log in as Alice.
   - Tap **ADD MONEY** on the home dashboard.
   - Select quick chip **₹2,000** or type `2000`.
   - Choose **DEBIT / CREDIT CARD** > Enter test card `4242 4242 4242 4242`, Expiry `12/28`, CVV `123`.
   - Tap **PAY ₹2,000**. Observe the processing dialog and success receipt.
   - On the Home screen, tap the balance card, enter PIN `1234` (prompted to create on first use), and confirm balance reflects `₹2,000.00`.
3. **Send Money via UPI / Phone (Alice to Bob):**
   - Tap **SEND MONEY**.
   - Enter Bob's phone `9812345678` or UPI `9812345678@smartpay`.
   - Bob's name ("Bob Patel") will resolve automatically.
   - Enter amount `₹500` and note `"Project share"`.
   - Tap **SEND MONEY** > verify details on the yellow confirmation screen > tap **CONFIRM & SEND**.
   - Enter PIN `1234`. The transfer will complete, displaying the transaction receipt.
4. **Verify Live Notification & Balance (Bob):**
   - Log in as Bob.
   - Observe the green unread indicator dot on the bell icon at the top right of Home.
   - Tap the balance card and enter Bob's PIN `4321` to verify wallet balance increased by `₹500.00`.
   - Tap the bell icon to open **NOTIFICATIONS**; observe the `"Money received"` card for `₹500`. Returning to Home clears the unread dot.
5. **QR Code Payment:**
   - On Bob's phone, tap **RECEIVE MONEY** to display Bob's personalized QR Code.
   - On Alice's phone, tap **SCAN QR**, scan Bob's screen, enter `₹250`, enter PIN `1234`, and complete the transfer.
6. **Check History:**
   - Tap **TRANSACTIONS** on either account.
   - Use the filter tabs (`ALL`, `SENT`, `RECEIVED`) and search bar to view and verify transaction details.

---

## 7. Known Limitations & Architecture Notes

1. **Client-Side Transfer Execution in Demo Mode:**
   - In this academic demonstration, the balance deduction on the sender and crediting on the receiver are executed client-side via Firebase Realtime Database multi-path atomic updates.
   - *Production Architecture Note:* In a commercial enterprise fintech app, clients never possess direct write access to user balance fields. Balance mutations and transaction settlement would be securely handled on a private backend (e.g., Node.js / Go backend or Firebase Cloud Functions) verifying cryptographic JWTs and server-side digital signatures.
2. **Simulated Payment Gateways:**
   - Card validation implements the real-world **Luhn Algorithm (Mod 10)**, but does not connect to Visa/Mastercard payment gateways. No actual money is debited.
   - UPI bank transfers use simulated bank delays and demo transactions.
3. **Emulator Camera Feed:**
   - Scanning QR codes requires camera hardware or a virtual camera scene configured in the Android Emulator settings.

---

## 8. Potential Future Work
- **Biometric Authentication:** Integration of Android `BiometricPrompt` API for fingerprint and face unlock authorization alongside the 4-digit PIN.
- **NPCI / UPI 2.0 Integration:** Connecting to sandbox PSP gateways (Razorpay, Cashfree, or Setu) for live UPI deep-linking (`upi://pay`).
- **Export Statements:** Generating and exporting digitally signed PDF or CSV transaction account statements.
- **Split Bills & Payment Requests:** Peer-to-peer split expense requests with push notifications.

---

**SmartPay – Secure, Simple & Modern Digital Wallet**  
*Submitted in partial fulfillment of the academic curriculum requirements.*

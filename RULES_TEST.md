# Firebase Realtime Database Rules Test Plan

This document provides step-by-step test cases to verify `database.rules.json` using the **Rules Playground** in the [Firebase Console](https://console.firebase.google.com/).

---

## How to Access the Rules Playground
1. Open the [Firebase Console](https://console.firebase.google.com/) and select your project.
2. In the left navigation, go to **Build** > **Realtime Database**.
3. Select the **Rules** tab at the top.
4. Paste the contents of `database.rules.json` and click **Publish**.
5. Click the **Rules Playground** button (top right).

---

## Test Identities
For all test cases below, use:
- **User A (Sender / Owner)**: `UID: "user_a_uid_123"`
- **User B (Receiver / Target)**: `UID: "user_b_uid_456"`

---

## 1. Root & Unauthenticated Access
| # | Test Name | Operation | Location | Auth State | Expected Result | Why |
|---|---|---|---|---|---|---|
| 1.1 | Unauthenticated Root Read | `read` | `/` | Unauthenticated | **Denied** | Root has `".read": false` |
| 1.2 | Unauthenticated Root Write | `write` | `/` | Unauthenticated | **Denied** | Root has `".write": false` |
| 1.3 | Unauthenticated User Profile Read | `read` | `/users/user_a_uid_123` | Unauthenticated | **Denied** | Requires `auth != null` |

---

## 2. User Profiles & Directory Lookup (`users`)
| # | Test Name | Operation | Location | Auth State | Payload | Expected Result | Why |
|---|---|---|---|---|---|---|---|
| 2.1 | Authenticated Lookup Read | `read` | `/users/user_b_uid_456` | Authenticated as `user_a_uid_123` | N/A | **Allowed** | Authenticated users can read profiles for Send Money phone/UPI lookups |
| 2.2 | Edit Another User Profile | `write` | `/users/user_b_uid_456` | Authenticated as `user_a_uid_123` | `{"name": "Hacked"}` | **Denied** | Only account owner can edit profile (`auth.uid === $uid`) |
| 2.3 | Create Own Profile (Valid) | `write` | `/users/user_a_uid_123` | Authenticated as `user_a_uid_123` | `{"name": "Alice", "email": "alice@test.com", "phone": "9876543210", "upiId": "alice@smartpay", "balance": 0, "createdAt": 1712000000000}` | **Allowed** | Valid fields, valid 10-digit phone, owner write |
| 2.4 | Create Profile (Invalid Phone) | `write` | `/users/user_a_uid_123` | Authenticated as `user_a_uid_123` | `{"name": "Alice", "email": "alice@test.com", "phone": "123", "upiId": "alice@smartpay", "balance": 0, "createdAt": 1712000000000}` | **Denied** | Phone regex validation requires exactly 10 digits |
| 2.5 | Create Profile (Empty Name) | `write` | `/users/user_a_uid_123` | Authenticated as `user_a_uid_123` | `{"name": "", "email": "alice@test.com", "phone": "9876543210", "upiId": "alice@smartpay", "balance": 0, "createdAt": 1712000000000}` | **Denied** | Name length must be between 1 and 50 characters |

---

## 3. Balance Transfers (`users/{uid}/balance`)
| # | Test Name | Operation | Location | Auth State | Payload | Expected Result | Why |
|---|---|---|---|---|---|---|---|
| 3.1 | Credit Receiver Balance | `write` | `/users/user_b_uid_456/balance` | Authenticated as `user_a_uid_123` | `1500` | **Allowed** | Any authenticated user can write to balance (simulated demo transfer architecture) |
| 3.2 | Negative Balance Write | `write` | `/users/user_a_uid_123/balance` | Authenticated as `user_a_uid_123` | `-50` | **Denied** | Balance must satisfy `newData.val() >= 0` |
| 3.3 | Non-numeric Balance | `write` | `/users/user_a_uid_123/balance` | Authenticated as `user_a_uid_123` | `"1000"` | **Denied** | Balance must satisfy `newData.isNumber()` |

---

## 4. Wallet PIN Privacy (`userPins`)
| # | Test Name | Operation | Location | Auth State | Payload | Expected Result | Why |
|---|---|---|---|---|---|---|---|
| 4.1 | Read Another User's PIN | `read` | `/userPins/user_b_uid_456` | Authenticated as `user_a_uid_123` | N/A | **Denied** | `userPins` has owner-only read (`auth.uid === $uid`) |
| 4.2 | Read Own PIN | `read` | `/userPins/user_a_uid_123` | Authenticated as `user_a_uid_123` | N/A | **Allowed** | User is accessing their own PIN record |
| 4.3 | Write Another User's PIN | `write` | `/userPins/user_b_uid_456` | Authenticated as `user_a_uid_123` | `{"pinHash": "abc", "pinSalt": "xyz"}` | **Denied** | Owner-only write (`auth.uid === $uid`) |
| 4.4 | Save Own PIN | `write` | `/userPins/user_a_uid_123` | Authenticated as `user_a_uid_123` | `{"pinHash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "pinSalt": "d41d8cd98f00b204e9800998ecf8427e"}` | **Allowed** | Owner write with required hash and salt fields |

---

## 5. Transactions (`transactions`)
| # | Test Name | Operation | Location | Auth State | Payload | Expected Result | Why |
|---|---|---|---|---|---|---|---|
| 5.1 | Impersonate Sender | `write` | `/transactions/tx_test_01` | Authenticated as `user_a_uid_123` | `{"senderId": "user_b_uid_456", "receiverId": "user_a_uid_123", "amount": 100, "type": "SENT", "status": "SUCCESS", "timestamp": 1712000000000}` | **Denied** | `senderId` must equal `auth.uid` |
| 5.2 | Zero Amount Transfer | `write` | `/transactions/tx_test_02` | Authenticated as `user_a_uid_123` | `{"senderId": "user_a_uid_123", "receiverId": "user_b_uid_456", "amount": 0, "type": "SENT", "status": "SUCCESS", "timestamp": 1712000000000}` | **Denied** | `amount` must be greater than zero |
| 5.3 | Negative Amount Transfer | `write` | `/transactions/tx_test_03` | Authenticated as `user_a_uid_123` | `{"senderId": "user_a_uid_123", "receiverId": "user_b_uid_456", "amount": -100, "type": "SENT", "status": "SUCCESS", "timestamp": 1712000000000}` | **Denied** | `amount` must be > 0 |
| 5.4 | Exceed ₹50,000 Limit | `write` | `/transactions/tx_test_04` | Authenticated as `user_a_uid_123` | `{"senderId": "user_a_uid_123", "receiverId": "user_b_uid_456", "amount": 60000, "type": "SENT", "status": "SUCCESS", "timestamp": 1712000000000}` | **Denied** | Demo transfer cap is ₹50,000 (`amount <= 50000`) |
| 5.5 | Invalid Transaction Type | `write` | `/transactions/tx_test_05` | Authenticated as `user_a_uid_123` | `{"senderId": "user_a_uid_123", "receiverId": "user_b_uid_456", "amount": 500, "type": "CRYPTO", "status": "SUCCESS", "timestamp": 1712000000000}` | **Denied** | Type must be one of `SENT`, `RECEIVED`, `ADD_MONEY`, `QR_PAYMENT` |
| 5.6 | Valid Transaction Creation | `write` | `/transactions/tx_test_06` | Authenticated as `user_a_uid_123` | `{"senderId": "user_a_uid_123", "receiverId": "user_b_uid_456", "amount": 500, "type": "SENT", "status": "SUCCESS", "timestamp": 1712000000000}` | **Allowed** | Valid transaction data and matching `senderId` |
| 5.7 | Edit Existing Transaction | `write` (with existing data set) | `/transactions/tx_test_06` | Authenticated as `user_a_uid_123` | `{"senderId": "user_a_uid_123", "receiverId": "user_b_uid_456", "amount": 1000, "type": "SENT", "status": "SUCCESS", "timestamp": 1712000000000}` | **Denied** | Transactions are create-only (`!data.exists()`) |

---

## 6. Notifications (`notifications`)
| # | Test Name | Operation | Location | Auth State | Payload | Expected Result | Why |
|---|---|---|---|---|---|---|---|
| 6.1 | Read Another User's Notifications | `read` | `/notifications/user_b_uid_456` | Authenticated as `user_a_uid_123` | N/A | **Denied** | Notifications are private to the recipient (`auth.uid === $uid`) |
| 6.2 | Read Own Notifications | `read` | `/notifications/user_a_uid_123` | Authenticated as `user_a_uid_123` | N/A | **Allowed** | Recipient reading own notifications |
| 6.3 | Sender Creates Notification | `write` | `/notifications/user_b_uid_456/notif_01` | Authenticated as `user_a_uid_123` | `{"title": "Money received", "message": "Received ₹500 from Alice", "amount": 500, "timestamp": 1712000000000, "read": false}` | **Allowed** | Any authenticated user can create a notification for the recipient |
| 6.4 | Owner Marks Notification Read | `write` | `/notifications/user_a_uid_123/notif_02` | Authenticated as `user_a_uid_123` | `{"title": "Money received", "message": "Received ₹500", "amount": 500, "timestamp": 1712000000000, "read": true}` | **Allowed** | Notification owner can update read status or delete |

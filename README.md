# SmartPay – Digital Payment Application

SmartPay is an Android-based digital payment application developed as a team project. It demonstrates basic digital wallet functionality, allowing users to manage a wallet, send money, make QR-based payments, and view transaction history.

The application uses Firebase for authentication and data storage, with a simple and user-friendly interface.

> **Note:** SmartPay is an academic demonstration project. All payments and wallet transactions are simulated. It does not process real money, bank transfers, or UPI payments.

## Features

- User registration and login
- Password reset functionality
- Digital wallet balance management
- Add money to wallet
- Send money between registered users
- QR code generation and scanning
- QR-based simulated payments
- Transaction history
- User profile management
- Wallet PIN verification
- Payment confirmation and notifications

## Technology Stack

| Technology | Purpose |
|---|---|
| Java | Application development |
| XML | User interface design |
| Android Studio | Development environment |
| Firebase Authentication | User registration and login |
| Firebase Realtime Database | User and transaction data |
| ZXing | QR code generation and scanning |
| Glide | Image loading |



## Project Structure

The application includes the following main modules:

- Login and Registration
- Home and Wallet
- Add Money
- Send Money
- QR Payment
- Transaction History
- User Profile
- Notifications

## Installation and Setup

1. Clone the repository:

   ```bash
   git clone https://github.com/sharadapai08/SmartPay.git
   ```

2. Open the project in Android Studio.
3. Allow Gradle to sync.
4. Configure your own Firebase project.
5. Add your Firebase `google-services.json` file inside the `app` folder.
6. Build and run the application on an Android device or emulator.

## Requirements

- Android Studio
- Java Development Kit (JDK)
- Android SDK
- Firebase project
- Internet connection
- Android device or emulator

## Future Enhancements

- Integration with a secure real payment gateway
- Improved transaction security
- Additional payment options
- Enhanced user interface
- Advanced transaction management

## Disclaimer

This project was developed for academic and learning purposes. It is not a production-ready payment application and must not be used for actual financial transactions.

## Contributors

Developed as a team project by MCA students of Manipal Institute of Technology.

## License

This project is intended for educational and demonstration purposes.

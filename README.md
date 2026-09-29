# 🛒 Kirana POS & Inventory Management System

[![Build & Release Production APK](https://github.com/sancharinimondal-afk/kirana-pos-inventory-app/actions/workflows/release.yml/badge.svg)](https://github.com/sancharinimondal-afk/kirana-pos-inventory-app/actions/workflows/release.yml)
[![Latest Release](https://img.shields.io/github/v/release/sancharinimondal-afk/kirana-pos-inventory-app?color=00875A&label=Download%20APK)](https://github.com/sancharinimondal-afk/kirana-pos-inventory-app/releases)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-blue)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-4285F4)](https://developer.android.com/jetpack/compose)
[![Database](https://img.shields.io/badge/Offline%20DB-Room%202.6-FF6F00)](https://developer.android.com/training/data-storage/room)

A modern, fast, and 100% offline-first Android Point of Sale (POS) and inventory management application designed specifically for Indian Kirana stores, grocery marts, and retail shops.

---

## 📥 Direct APK Download

Download the ready-to-install Android APK on any Android phone, tablet, or handheld POS terminal:

👉 **[Download Latest APK from GitHub Releases](https://github.com/sancharinimondal-afk/kirana-pos-inventory-app/releases)**

1. On your device, tap the latest release link above.
2. Under **Assets**, tap `app-release.apk` (or `app-debug.apk`) to download.
3. Open the downloaded file and tap **Install** (allow "Install from this source" if prompted).

---

## 🌟 Key Features

### 🎙️ Voice Search in Inventory
- Search items instantly by speaking product names (e.g., *"Basmati Rice"*, *"Tata Tea"*, *"Sugar"*, *"Fortune Oil"*).
- Integrated with Android speech recognition and interactive pulse feedback with popular grocery suggestion chips.

### 🧾 High-Speed POS Billing
- Barcode scanning with device camera, physical USB barcode guns, and Bluetooth scanners.
- Rapid product search by Name, SKU, or Barcode with instant auto-add.
- Granular quantity adjustment with decimal support for loose groceries (e.g., `1.5 kg`, `0.25 kg`).
- Automatic GST tax computation with inclusive CGST & SGST breakdowns.
- Instant cash tender calculator with exact change return suggestions.
- Multi-mode tender support: **Cash**, **UPI**, **Card**, and **Khata (Credit)**.

### 📦 Smart Inventory & Stock Alerts
- Real-time stock tracking with color-coded alerts:
  - 🟢 **In Stock**
  - 🟡 **Low Stock Alert** (configurable thresholds)
  - 🔴 **Out of Stock**
- Dedicated stock adjustment engine (Add stock, Remove damaged/expired goods, or Set exact count).
- Full stock audit movement ledger with timestamps and reasons.
- Export inventory catalog to **Excel CSV** anytime.

### 👥 Customer Khata (Udhaar) Ledger
- Track credit balances, dues, and payment collections per customer.
- One-click **WhatsApp Payment Reminders** with pre-formatted invoice summary.
- Dynamic **UPI QR Code** generation for direct customer payment via PhonePe, Google Pay, or Paytm.

### 🖨️ Thermal Receipt Printing
- ESC/POS thermal printing over Bluetooth for **58mm (2-inch)** and **80mm (3-inch)** printers.
- Formatted bill with Shop Name, GSTIN, itemized rates, taxes, and footer greetings.
- Digital receipt sharing via WhatsApp and PDF export.

### 🔒 100% Offline-First & Data Safety
- Entire store database runs locally on your device via **Room SQLite**.
- Zero cloud dependency for daily operations—functions completely without internet.
- One-click encrypted JSON database backup and atomic, fault-tolerant restore.
- Automated daily background backup via Android WorkManager.

---

## 🛠️ Architecture & Tech Stack

- **Language:** Kotlin (100%)
- **UI Toolkit:** Jetpack Compose with Material Design 3 (M3)
- **Local Persistence:** Room Database (KSP Symbol Processing)
- **Concurrency & Streams:** Kotlin Coroutines & StateFlow
- **Architecture:** Clean MVVM (Model-View-ViewModel) + Repository Pattern
- **Barcode Engine:** ZXing Embedded + Android CameraX
- **Background Tasks:** Android Jetpack WorkManager
- **CI/CD:** GitHub Actions Automated APK Builder & Release Publisher

---

## 💻 Local Development & Building

To build the APK locally on your computer:

```bash
# Clone the repository
git clone https://github.com/sancharinimondal-afk/kirana-pos-inventory-app.git
cd kirana-pos-inventory-app

# Build debug APK
./gradlew assembleDebug

# Build production release APK
./gradlew assembleRelease

# Run unit and Robolectric tests
./gradlew testDebugUnitTest
```

The compiled APKs will be located at:
- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk`

---

## 📄 License

This project is licensed under the Apache 2.0 License.

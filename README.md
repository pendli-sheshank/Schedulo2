<div align="center">

# Schedulo

**Track shifts. Manage earnings. Stay organized.**

A modern Android app for shift workers to manage multiple jobs, track hours, monitor earnings, and stay on top of their schedules.

[![Build & Deploy](https://github.com/pendli-sheshank/Schedulo2/actions/workflows/deploy-playstore.yml/badge.svg)](https://github.com/pendli-sheshank/Schedulo2/actions/workflows/deploy-playstore.yml)

</div>

---

## Features

### Shift Management
- Add, edit, and delete shifts with date/time pickers
- Set shift reminders (15m, 30m, 1hr before)
- View upcoming and previous shifts in the Plan tab
- Support for both hourly and gig work shifts

### Multi-Job Support
- Configure multiple employers/jobs
- Set hourly rates per job
- Define weekly goals (hours or earnings targets)
- Customizable weekly cycle start day per employer

### Earnings & Hours Tracking
- Real-time dashboard with total earnings and hours
- Per-employer goal progress bars
- Weekly cycle filtering (current week, last week, etc.)
- Pay tracking with paid/unpaid status per shift

### Dashboard
- Earnings summary card with total pay and hours
- Employer goal tracker cards with progress indicators
- Upcoming shifts preview (next 5 shifts)
- Quick access to profile and shift management

### Profile
- User avatar with initials derived from account name
- Activity stats (total shifts, hours, earnings)
- Employer overview card
- Default shift settings (company and hourly rate)
- Member since date from account creation

### Authentication
- Firebase Authentication (email/password)
- Modern login and signup screens with gradient design
- Form validation and inline error banners
- Secure session management with proper logout/data clearing

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Kotlin |
| UI Framework | Jetpack Compose + Material 3 |
| Architecture | MVVM (ViewModel + StateFlow) |
| Backend | Firebase Auth + Cloud Firestore |
| Navigation | Jetpack Navigation Compose |
| Build | Gradle (Kotlin DSL) + AGP 9.1 |
| CI/CD | GitHub Actions → Google Play Store |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 36 |

---

## Project Structure

```
app/src/main/java/com/example/
├── MainActivity.kt          # Main activity, dashboard, navigation
├── AuthViewModel.kt         # Authentication state management
├── AuthScreens.kt           # Login and signup screens
├── DashboardSupport.kt      # DashboardViewModel, Shift/Job models, AddShiftScreen
├── TabsSupport.kt           # MainLayout, PlanScreen, JobsScreen, PayScreen, ProfileScreen
└── ui/theme/
    ├── Color.kt             # App color palette
    └── Theme.kt             # Material 3 theme configuration
```

---

## Getting Started

### Prerequisites
- Android Studio or the Android SDK command-line tools
- JDK 21
- A Firebase project with Authentication and Firestore enabled

### Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/pendli-sheshank/Schedulo2.git
   cd Schedulo2
   ```

2. **Add Firebase configuration**
   - Go to Firebase Console → Project Settings → Add Android app
   - Register with package name `com.schedulo2.app`
   - Download `google-services.json` and place it in the `app/` directory

3. **Build and run**
   ```bash
   ./gradlew assembleDebug
   ```
   Or open in Android Studio and run on an emulator/device.

---

## CI/CD

The project uses GitHub Actions for automated builds and deployment to Google Play Store.

- **Trigger**: Every push to `main` or manual dispatch via the Actions tab
- **Pipeline**: Build signed AAB → Upload to Play Store internal testing track
- **Artifacts**: The signed AAB is also saved as a downloadable artifact

### Required GitHub Secrets

| Secret | Description |
|--------|-------------|
| `KEYSTORE_BASE64` | Base64-encoded upload keystore (.jks) |
| `STORE_PASSWORD` | Keystore store password |
| `KEY_PASSWORD` | Keystore key password |
| `PLAY_SERVICE_ACCOUNT_JSON` | Google Play service account JSON key |

---

## Upcoming Features

- [ ] **Push Notifications** — Shift reminders via Firebase Cloud Messaging
- [ ] **Dark Mode** — Full dark theme support
- [ ] **Export Reports** — Export shift history and earnings as PDF/CSV
- [ ] **Recurring Shifts** — Set up repeating shift schedules
- [ ] **Overtime Tracking** — Auto-detect overtime hours with configurable thresholds
- [ ] **Multi-Currency Support** — Track earnings in different currencies
- [ ] **Shift Swap / Trade** — Request and manage shift swaps between coworkers
- [ ] **Calendar Integration** — Sync shifts with Google Calendar
- [ ] **Widgets** — Home screen widget showing next shift and weekly earnings
- [ ] **Biometric Login** — Fingerprint/face unlock support
- [ ] **Analytics Dashboard** — Charts and trends for earnings over time
- [ ] **Team Features** — Shared schedules for managers and team members

---

## License

This project is proprietary. All rights reserved.

---

<div align="center">

**Built with Kotlin + Jetpack Compose**

</div>

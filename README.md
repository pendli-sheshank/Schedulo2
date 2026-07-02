<div align="center">

# Schedulo

**Track shifts. Manage earnings. Stay organized.**

A cross-platform app for shift workers to manage multiple jobs, track hours, monitor earnings, and coordinate with teams — available on both iOS and Android.

[![Build & Deploy Android](https://github.com/pendli-sheshank/Schedulo2/actions/workflows/deploy-playstore.yml/badge.svg)](https://github.com/pendli-sheshank/Schedulo2/actions/workflows/deploy-playstore.yml)
[![Build & Deploy iOS](https://github.com/pendli-sheshank/Schedulo2/actions/workflows/deploy-appstore.yml/badge.svg)](https://github.com/pendli-sheshank/Schedulo2/actions/workflows/deploy-appstore.yml)

</div>

---

## Features

### Shift Management
- Add, edit, and delete shifts with date/time pickers
- Set shift reminders (15m, 30m, 1hr before)
- View upcoming and previous shifts in the Plan tab with calendar views (month, week, day)
- Support for both hourly and gig work shifts

### Multi-Job Support
- Configure multiple employers/jobs
- Set hourly rates per job
- Define weekly goals (hours or earnings targets)
- Customizable weekly cycle start day per employer
- Overtime tracking with configurable thresholds and multipliers

### Pay & Earnings
- Real-time dashboard with total earnings and hours
- Per-employer goal progress bars
- Pay cycle management with paid/unpaid status tracking
- Payment window period display with countdown timers
- Pay adjustments (Bonus, Overpaid, Underpaid, Deduction, Correction)
- Export reports in Text or CSV format with employer/week/cycle filtering

### Biometric Login
- Face ID / Touch ID on iOS
- Fingerprint / face unlock on Android
- Auto-triggers on app launch when enabled
- Convenience unlock with Firebase session persistence

### Calendar Integration
- Sync shifts to native device calendar (Apple Calendar / Android Calendar)
- Auto-sync on shift create, update, and delete
- Choose which calendar to sync to
- Toggle on/off from Profile settings

### Home Screen Widgets
- **iOS**: WidgetKit extension with small and medium sizes
- **Android**: Glance widget
- Shows next upcoming shift with company, role, and countdown
- Displays weekly earnings and hours summary
- Auto-updates when shift data changes

### Team Management
- **Create / Edit Teams**: Managers create a team (name, company, working hours, address) and receive a unique 6-character invite code
- **Join Teams**: Members enter the invite code to join from any device
- **Assign Shifts**: Managers assign shifts to specific team members with company, role, time, and pay details
- **Accept / Decline**: Members see assigned shifts and can accept or decline them
- **Shift Swaps**: Members request to swap or trade shifts with teammates; managers approve or decline
- **Per-Shift Tasks**: Attach a task checklist to a shift and track completion per member
- **Weekly Roster Grid**: View the whole team's week at a glance to spot coverage gaps
- **Team Chat & Announcements**: Send messages and pinned announcements to the team in real time
- **Photo Sharing**: Share a photo in team chat — images are stored securely and **auto-deleted once every member has seen them**
- **Real-time Updates**: All team data syncs in real-time across devices via Firestore
- **Roles**: Owner / Manager (full control) and Member (view + respond to assignments)

### Earnings Insights
- Charts and analytics for earnings trends over time
- Weekly/monthly breakdowns
- Per-employer analytics

### Profile & Settings
- User avatar with initials
- Activity stats (total shifts, hours, earnings)
- Theme selection (Light / Dark / System)
- Notification preferences
- Default shift settings

### Authentication
- Firebase Authentication (email/password)
- Biometric login option (Face ID, Touch ID, Fingerprint)
- Form validation and inline error banners
- Secure session management with proper logout/data clearing

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| Shared Logic | Kotlin Multiplatform (KMP) |
| iOS UI | SwiftUI |
| Android UI | Jetpack Compose + Material 3 |
| Architecture | MVVM |
| Backend | Firebase Auth + Cloud Firestore |
| Media Storage | Firebase Cloud Storage (team chat photos) |
| iOS Widgets | WidgetKit |
| Android Widgets | Glance |
| iOS Calendar | EventKit |
| Android Calendar | CalendarContract |
| iOS Biometric | LocalAuthentication (Face ID / Touch ID) |
| Android Biometric | AndroidX Biometric |
| iOS CI/CD | GitHub Actions → TestFlight |
| Android CI/CD | GitHub Actions → Google Play Store |

---

## Project Structure

```
Schedulo2/
├── shared/                          # Kotlin Multiplatform shared module
│   └── src/commonMain/kotlin/
│       └── com/schedulo/shared/
│           ├── model/               # Shift, Job, Team data models
│           ├── logic/               # ShiftCalculator, InsightsCalculator
│           └── utils/               # Validation, DesignTokens
├── androidApp/                      # Android app (Jetpack Compose)
│   └── src/main/java/com/example/
│       ├── MainActivity.kt          # Navigation and app entry
│       ├── AuthViewModel.kt         # Auth + biometric state
│       ├── AuthScreens.kt           # Login and signup UI
│       ├── DashboardSupport.kt      # Dashboard ViewModel, Shift/Job CRUD
│       ├── TabsSupport.kt           # Plan, Jobs, Pay, Profile screens
│       ├── TeamViewModel.kt         # Team management state, chat, photo upload
│       ├── TeamSupport.kt           # Team UI screens
│       ├── TeamDetailSupport.kt     # Team detail, chat, and photo sharing UI
│       ├── CalendarService.kt       # Native calendar sync
│       └── widget/                  # Home screen widget (Glance)
├── iosApp/                          # iOS app (SwiftUI)
│   └── Schedulo2/
│       ├── Services/                # FirebaseService, CalendarService
│       ├── ViewModels/              # Auth, Dashboard, Team ViewModels
│       └── Views/                   # SwiftUI views organized by feature
│           ├── Auth/                # Login, Signup
│           ├── Dashboard/           # Main tab view, Dashboard
│           ├── Pay/                 # Pay view, Export reports
│           ├── Plan/                # Calendar views
│           ├── Team/                # Team management views
│           └── Profile/             # Profile, Settings
├── iosApp/ScheduloWidget/           # iOS widget extension (WidgetKit)
└── firestore.rules                  # Firestore security rules
```

---

## Getting Started

### Prerequisites
- **Android**: Android Studio, JDK 21
- **iOS**: Xcode 15+, macOS
- A Firebase project with Authentication and Firestore enabled

### Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/pendli-sheshank/Schedulo2.git
   cd Schedulo2
   ```

2. **Add Firebase configuration**
   - **Android**: Download `google-services.json` → place in `androidApp/`
   - **iOS**: Download `GoogleService-Info.plist` → place in `iosApp/Schedulo2/`

3. **Build and run**
   ```bash
   # Android
   ./gradlew :androidApp:assembleDebug

   # iOS
   cd iosApp && xcodegen generate && open Schedulo2.xcodeproj
   ```

---

## CI/CD

### Android → Google Play Store
- **Trigger**: Push to `main` or manual dispatch
- **Pipeline**: Build signed AAB → Upload to Play Store internal testing track

### iOS → TestFlight
- **Trigger**: Push to `main` or manual dispatch
- **Pipeline**: Build KMP framework → Generate Xcode project → Archive → Upload to TestFlight
- **Build number**: Auto-incremented from `github.run_number + 100`

### Required GitHub Secrets

| Secret | Platform | Description |
|--------|----------|-------------|
| `KEYSTORE_BASE64` | Android | Base64-encoded upload keystore (.jks) |
| `STORE_PASSWORD` | Android | Keystore store password |
| `KEY_PASSWORD` | Android | Keystore key password |
| `PLAY_SERVICE_ACCOUNT_JSON` | Android | Google Play service account JSON key |
| `IOS_P12_CERTIFICATE_BASE64` | iOS | Base64-encoded distribution certificate |
| `IOS_P12_PASSWORD` | iOS | Certificate password |
| `IOS_PROVISIONING_PROFILE_BASE64` | iOS | Base64-encoded provisioning profile |
| `IOS_PROVISIONING_PROFILE_NAME` | iOS | Profile specifier name |
| `IOS_TEAM_ID` | iOS | Apple Developer Team ID |
| `IOS_APPSTORE_API_KEY_ID` | iOS | App Store Connect API key ID |
| `IOS_APPSTORE_API_KEY_P8_BASE64` | iOS | Base64-encoded .p8 API key |
| `IOS_APPSTORE_ISSUER_ID` | iOS | App Store Connect issuer ID |
| `IOS_GOOGLE_SERVICE_PLIST_BASE64` | iOS | Base64-encoded GoogleService-Info.plist |
| `WIDGET_PROVISIONING_PROFILE` | iOS | Base64-encoded widget provisioning profile |
| `IOS_WIDGET_PROVISIONING_PROFILE_NAME` | iOS | Widget profile specifier name |

---

## Team Management — How It Works

1. **Manager creates a team** in the Profile → Team Management screen, giving it a name
2. A **6-character invite code** is generated automatically (e.g., `X4K9PM`)
3. **Manager shares the code** with team members (via text, chat, etc.)
4. **Members join** by entering the invite code on their device
5. **Manager assigns shifts** to specific members — setting company, role, date/time, and hourly rate
6. **Members receive assignments** and can **Accept** or **Decline** each shift
7. All data syncs in **real-time** across all team members' devices

### Firestore Collections

| Collection | Purpose |
|------------|---------|
| `teams` | Team metadata (name, owner, invite code, member count) |
| `team_members` | Membership records (userId, teamId, role) |
| `team_shifts` | Assigned shifts (assignedTo, assignedBy, status) |
| `team_messages` | Chat messages and announcements (text, `imageUrl`, `seenBy`) |

> Chat photos are uploaded to **Firebase Cloud Storage** under `chat_images/{teamId}/{messageId}.jpg` and are deleted automatically once every team member has seen the message.

---

## Security

Team data is isolated per team. Both `firestore.rules` and `storage.rules` scope
every team collection (and each user's `jobs`/`shifts`) to the requesting user's
membership — a signed-in user can only read teams they belong to, and joining a
team requires the correct invite code (verified server-side, not just in the app).
Membership documents use the deterministic id `{teamId}_{userId}` so the rules can
prove membership with a single `exists()` check, and an `invite_codes/{CODE}`
lookup lets joiners resolve a code to a team without the `teams` collection being
world-readable.

Security-rules changes are validated by an emulator test suite in `firebase-tests/`
(run `cd firebase-tests && npm install && npm test`) and in CI on every pull
request. **Rules are not deployed by CI** — after merging, deploy them manually:

```bash
firebase deploy --only firestore:rules,storage
```

Team features (creating/joining a team) require a **verified email address**; a
verification link is sent at signup.

### Operational hardening (console actions)

- **Restrict the Android API key** in the Google Cloud console to the app's
  package name + release/debug SHA-1 fingerprints. The key in
  `google-services.json` is not a secret, but restricting it limits abuse.
- **Enable Firebase App Check** (Play Integrity on Android, App Attest on iOS) so
  only genuine app builds can reach Firestore/Storage with a user token.
- The committed `debug.keystore.base64` is a **public, non-secret** debug keystore
  (password `android`), shared so every clone signs debug builds with the same
  Firebase debug SHA-1. The Gradle build decodes it to a gitignored
  `debug.keystore` automatically.

---

## Upcoming Features

- [ ] **Push Notifications** — Shift reminders via Firebase Cloud Messaging
- [ ] **Recurring Shifts** — Set up repeating shift schedules
- [ ] **Multi-Currency Support** — Track earnings in different currencies
- [ ] **Google Calendar Sync** — Two-way sync with Google Calendar API

---

## License

This project is proprietary. All rights reserved.

---

<div align="center">

**Built with Kotlin Multiplatform + SwiftUI + Jetpack Compose**

</div>

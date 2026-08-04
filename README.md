<div align="center">

# Shifnex

**Track shifts. Manage earnings. Stay organized.**

A cross-platform app for shift workers to manage multiple jobs, track hours, monitor earnings, and coordinate with teams — available on both iOS and Android.

*Formerly known as Schedulo — rebranded to Shifnex in July 2026.*

[![Build & Deploy Android](https://github.com/pendli-sheshank/Shifnex/actions/workflows/deploy-playstore.yml/badge.svg)](https://github.com/pendli-sheshank/Shifnex/actions/workflows/deploy-playstore.yml)
[![Build & Deploy iOS](https://github.com/pendli-sheshank/Shifnex/actions/workflows/deploy-appstore.yml/badge.svg)](https://github.com/pendli-sheshank/Shifnex/actions/workflows/deploy-appstore.yml)

</div>

---

## Features

### Shift Management
- Add, edit, and delete shifts with date/time pickers
- Set shift reminders (15m, 30m, 1hr before) with full-screen alarm-style alerts
- View upcoming and previous shifts in the Plan tab with calendar views (month, week, day)
- Plan an entire week at once, aligned to each employer's pay-cycle start day
- Support for both hourly and gig work shifts (gig shifts auto-mark as paid)
- Each shift stores its hourly rate at creation, so later job-rate changes are never retroactive

### Multi-Job Support
- Configure multiple employers/jobs
- Set hourly rates per job, plus bonus amounts
- Define weekly goals (hours or earnings targets)
- Customizable weekly cycle start day per employer (e.g. Friday–Thursday pay weeks)
- Overtime tracking with configurable thresholds and multipliers

### Pay & Earnings
- Real-time dashboard with earnings and hours, split between personal and team shifts
- Per-employer goal progress bars
- Pay cycle management with paid/unpaid status tracking
- **Fiscal-week payroll grouping** — weekly totals anchor to each job's cycle start day, never a hardcoded Monday
- Payment window period display with countdown timers
- Pay adjustments (Bonus, Overpaid, Underpaid, Deduction, Correction)
- Export reports in Text or CSV format with employer/week/cycle filtering and overtime breakdown

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
- **Accept / Decline**: Members see assigned shifts and can accept or decline them; accepted shifts sync into their personal schedule
- **Shift Swaps**: Members request to swap or trade shifts with teammates; managers approve or decline; each member keeps their own pay rate
- **Per-Shift Tasks**: Attach a task checklist to a shift and track completion per member
- **Weekly Roster Grid**: View the whole team's week at a glance to spot coverage gaps
- **Weekly Team Plan**: Managers plan a full week of coverage with per-member pay rates
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
- Grouped settings lists with auto-save

### Authentication
- Firebase Authentication (email/password) with email verification
- Biometric login option (Face ID, Touch ID, Fingerprint)
- Privacy policy consent checkbox at signup
- Form validation and inline error banners
- Secure session management with proper logout/data clearing (no data leaks across accounts)

### Polish & Resilience
- Skeleton loaders while data streams in
- Optimistic local updates with rollback on Firestore failure
- Connectivity monitoring with offline indicators
- Micro-animations and a notched bottom nav bar with centered FAB menu
- Audited against 2026 mobile design guidelines (see `AUDIT_REPORT_2026_DESIGN_GUIDELINES.md`)

---

## Development History — Updates & Improvements

Shifnex has shipped continuously since day one: **100+ pull requests** merged between May 31 and July 31, 2026, taking the project from a single-platform prototype to a live, dual-store release (tagged `Live_Release`). The story so far:

### Phase 1 — Android Foundation (late May – early June 2026)
The project began as an Android-only timesheet app named **Schedulo**.
- Email/password authentication with Firebase, redesigned login/signup screens, and an enhanced profile screen
- Core shift CRUD with date/time pickers and a real-time dashboard
- **Full Firestore real-time sync** for cross-device data access
- Full dark mode with system theme auto-detection
- Overtime tracking with configurable threshold and multiplier
- CSV export reports; Plan screen redesign with weekly grouping and timesheet details
- Full-screen alarm-style shift reminders with sound and vibration
- First Play Store CI/CD pipeline (GitHub Actions → internal testing track)

### Phase 2 — Reliability & Pay Cycles (early June 2026)
- Fixed mark-as-paid persistence (optimistic local updates + Firestore field-name mismatch)
- Gig shifts auto-marked as paid and separated from weekly pay cycles
- Fixed memory leaks, missing optimistic updates, and data-loss edge cases; Firestore became the single source of truth
- Dashboard redesigned with modern design principles
- Security hardening, account deletion, and analytics groundwork
- Reliable reminders, profile settings, shift notes, and notification preferences
- Export redesigned around employer pay cycles with overtime breakdown
- Plan tab rebuilt with calendar **Month, Week, and Day views**, future-week planning, and calendar add-shift buttons
- Pay adjustments per weekly cycle (bonus, underpaid, overpaid, deduction)

### Phase 3 — Going Cross-Platform: iOS + KMP (mid June 2026)
- **iOS app added with a Kotlin Multiplatform shared module** — shared models (`Shift`, `Job`, `Team`) and business logic (`ShiftCalculator`, `InsightsCalculator`) now compile for both platforms
- SwiftUI UI with MVVM mirroring the Android architecture
- Connected both apps to the **named Firestore database `"schedulo2"`** (fixing sync against the default DB)
- Week planner aligned to each job's cycle start day, with access to the past 3 weeks
- Fixed 14 issues from a full app audit
- iOS CI/CD to **TestFlight**: an extended hardening campaign (PRs #21–#44) covering code signing for SPM packages, keychain setup, App Store validation (icons, orientations, encryption declaration), auto-incrementing build numbers from the CI run number, and reliable IPA export/upload

### Phase 4 — Platform Features: Biometrics, Calendar, Widgets, Teams v1 (mid-late June 2026)
- Biometric login on both platforms (Face ID / Touch ID / Android Biometric), including fixes for the auto-trigger, bypass-on-reopen, and logout-loop bugs
- Native calendar sync on both platforms (EventKit / CalendarContract)
- **Home screen widgets** on both platforms (WidgetKit / Glance) showing the next shift and weekly summary
- First team management release: create/join teams with invite codes, assign shifts, Firestore security rules
- iOS Pay tab reached parity: payment window, pay adjustments, mark as paid
- Tab restructure with a dedicated Team tab, employer dropdowns, accept-to-sync shifts
- Auto-approve options, hide-pay setting, weekly team plan, and a manager dashboard

### Phase 5 — Teams Deep Build (late June 2026)
A five-phase epic turned teams into a full workforce tool:
- **Phase 0**: notification and widget data reliability fixes
- **Phase 1**: team permissions + shift accept/decline
- **Phase 2**: weekly roster grid view
- **Phase 3**: team chat and announcements
- **Phase 4**: shift swap requests with manager approval workflow
- Then on top: per-shift task checklists, swap initiation from the schedule, team weekly cycle start day, photo sharing in chat with **auto-delete after all members have seen the image**, WhatsApp-style chat UI, and chat notifications
- Team management redesigned into a responsive **bento-style dashboard** (dashboard / schedule / tasks tiles)
- Create/Edit Team expanded (company, hours, address) and teams decoupled from personal Jobs
- Firestore rules hardened against team privilege-escalation and shift-forgery; error feedback surfaced in the UI
- Fixed orphaned personal shifts when team shifts are deleted; members keep their own pay rate through swaps
- New app icon and UI animations (splash screens later removed after causing an Android force-close)

### Phase 6 — Polish, Compliance & Hardening (early July 2026)
- Widget reliability on both platforms: fixed the Android "couldn't add widget" error and hardcoded preview data, and the iOS widget not receiving app data
- Photo picker fixes across Android versions (Android 11+ visibility, requestCode overflow, `ACTION_GET_CONTENT` fallback)
- Privacy policy checkbox at login; Terms & Conditions added to store listings
- Full **2026 design guidelines audit** with compliance fixes, followed by skeleton loaders, optimistic updates, connectivity monitoring, and micro-animations
- Dashboard polish: notched nav bar, centered FAB menu; Profile & Settings polish: grouped lists, auto-save, modal and jobs UX
- Patched the Glance protobuf **CVE-2024-7254** flagged by Play Console
- **Security & UI audit** (`AUDIT_REPORT_SECURITY_AND_UI.md`) with fixes for team data isolation and design tokens; security-rules **emulator test suite** (`firebase-tests/`) wired into CI
- GitHub Actions workflow to deploy Firestore/Storage rules directly

### Phase 7 — Rebrand & Live Release (mid-late July 2026)
- **Renamed the app to Shifnex** on both platforms and rebranded all in-app text
- iOS marketing version automation in CI (auto-injected `MARKETING_VERSION`); version 1.0.1 shipped to TestFlight
- **Payroll fiscal weeks**: weekly earnings grouping anchored to each job's `weeklyCycleStartDay` instead of calendar weeks — codified as a repo skill so future changes follow the same rules
- Shifts now priced at their **stored hourly rate**, so employer rate changes aren't retroactively applied to past shifts
- Fixed the team schedule flow, Plan-tab duplicates, and split dashboard earnings into personal vs. team
- Fixed team creation being blocked by stale email verification, and **team data leaking across sign-outs**
- CI storage-quota management: deploy artifacts now expire after 7 days; store deploys unblocked with non-blocking archival
- Tagged **`Live_Release`** — Shifnex is live on both store pipelines

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
Shifnex/
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
├── firebase-tests/                  # Firestore/Storage rules emulator tests
└── firestore.rules                  # Firestore security rules
```

> Internal module and bundle identifiers retain the original `schedulo`/`Schedulo2` naming for store and Firebase continuity; the user-facing brand is **Shifnex**.

---

## Getting Started

### Prerequisites
- **Android**: Android Studio, JDK 21
- **iOS**: Xcode 15+, macOS
- A Firebase project with Authentication and Firestore enabled

### Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/pendli-sheshank/Shifnex.git
   cd Shifnex
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

### Tests

```bash
# Android unit tests
./gradlew :androidApp:testDebugUnitTest

# Shared module tests (commonTest on the JVM)
./gradlew :shared:testAndroidHostTest

# Firestore/Storage security-rules tests (needs Firebase emulator)
cd firebase-tests && npm install && npm test
```

---

## CI/CD

### Android → Google Play Store
- **Trigger**: Push to `main` or manual dispatch
- **Pipeline**: Build signed AAB → Upload to Play Store internal testing track
- **Version code**: Set from `github.run_number + 100`
- **Artifacts**: Build artifacts expire after 7 days to protect storage quota

### iOS → TestFlight
- **Trigger**: Push to `main` or manual dispatch
- **Pipeline**: Build KMP framework → Generate Xcode project (XcodeGen) → Archive → Upload to TestFlight
- **Build number**: Auto-incremented from `github.run_number + 100`; `MARKETING_VERSION` injected automatically

### Firebase Rules
- **`deploy-firebase-rules.yml`** deploys `firestore.rules` and `storage.rules` — runs automatically when a rules file changes on `main`, or manually from the Actions tab

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
| `FIREBASE_SERVICE_ACCOUNT` | Firebase | Service account JSON for rules deployment |

---

## Team Management — How It Works

1. **Manager creates a team** in the Profile → Team Management screen, giving it a name, company, working hours, and address
2. A **6-character invite code** is generated automatically (e.g., `X4K9PM`)
3. **Manager shares the code** with team members (via text, chat, etc.)
4. **Members join** by entering the invite code on their device
5. **Manager assigns shifts** to specific members — setting company, role, date/time, and hourly rate
6. **Members receive assignments** and can **Accept** or **Decline** each shift; accepted shifts sync into their personal schedule
7. All data syncs in **real-time** across all team members' devices

### Firestore Collections

| Collection | Purpose |
|------------|---------|
| `teams` | Team metadata (name, owner, invite code, member count) |
| `team_members` | Membership records (userId, teamId, role) |
| `team_shifts` | Assigned shifts (assignedTo, assignedBy, status, tasks) |
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
request.

Team features (creating/joining a team) require a **verified email address**; a
verification link is sent at signup.

### Deploying the rules

Rules target the **named `schedulo2` Firestore database** (see `firebase.json`) —
not `(default)`. From a machine with the Firebase CLI:

```bash
firebase deploy --only firestore:rules,storage
```

No machine handy? The **Deploy Firebase rules** GitHub Actions workflow
(`.github/workflows/deploy-firebase-rules.yml`) does it for you — trigger it from
the **Actions** tab (the **Run workflow** button works from the GitHub mobile
site), or it runs automatically when a rules file changes on `main`. It needs one
repository secret:

1. In the [Google Cloud console](https://console.cloud.google.com/iam-admin/serviceaccounts)
   for project `gen-lang-client-0769292643`, create a **service account** and
   grant it the **Firebase Rules Admin** role (`roles/firebaserules.admin`).
2. Create a **JSON key** for that account and download it.
3. In GitHub → **Settings → Secrets and variables → Actions → New repository
   secret**, name it `FIREBASE_SERVICE_ACCOUNT` and paste the entire JSON.

Then run the workflow. All three steps work from a phone browser.

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

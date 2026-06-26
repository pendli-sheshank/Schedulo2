# Apple App Store — Listing, Privacy Policy & App Privacy Labels

## 1. App Store Connect Listing Details

| Field | Value |
|---|---|
| **App name** | Schedulo — Shift & Pay Tracker |
| **Subtitle** (30 chars) | Track Shifts & Team Schedules |
| **Bundle ID** | `com.schedulo2.ios` |
| **Primary category** | Business |
| **Secondary category** | Productivity |
| **Age rating** | 4+ (no objectionable content; user-generated chat is moderated by team owner) |
| **Price** | Free |
| **Contact email** | sheshank3336@gmail.com |
| **Support URL** | *(enter your support page or email link)* |
| **Marketing URL** | *(optional)* |

### Description

Schedulo is the all-in-one shift tracking and team management app built for hourly workers, gig workers, and store managers.

**For Workers:**
- Log shifts with start/end times, breaks, and hourly rates
- Track earnings across multiple jobs with overtime calculations
- Get shift reminders so you never miss a clock-in
- Sync shifts to your iPhone's calendar
- Accept or decline assigned shifts from your manager
- Request shift swaps with teammates

**For Managers:**
- Create and manage store teams with invite codes
- Assign shifts individually or plan an entire week at a glance
- View a team roster grid to spot coverage gaps
- Approve or decline shift swap requests
- Send team announcements, chat messages, and photos
- Track task completion for each shift
- Promote or demote team members

**Key Features:**
- Real-time sync — changes appear instantly for all team members
- Face ID / Touch ID lock for privacy
- Home screen widget showing your next shift and weekly earnings
- Dark mode support
- Account deletion available in-app

### Keywords (100 chars max)

```
shift,tracker,timesheet,schedule,team,roster,hourly,pay,earnings,overtime,gig,work,manager,employee
```

### Screenshots Required

**iPhone 6.7" (required):** iPhone 15 Pro Max / iPhone 16 Pro Max
**iPhone 6.5":** iPhone 11 Pro Max / iPhone 13 Pro Max
**iPad 12.9" (if universal):** iPad Pro

Suggested screenshots (minimum 3, maximum 10):
1. Dashboard with upcoming shifts and weekly earnings
2. Shift creation/editing screen
3. Team dashboard with bento tile grid
4. Team schedule with assigned shifts and accept/decline
5. Team roster weekly grid view
6. Team chat with messages and announcements
7. Shift swap request flow
8. Home screen widget
9. Biometric lock screen
10. Dark mode view

### What's New (for updates)

```
- Team shift swap requests with multi-step approval
- Team chat, announcements, and photo sharing
- Weekly roster grid view
- Shift accept/decline for team members
- Member role management (promote/demote)
- Bug fixes and performance improvements
```

---

## 2. Privacy Policy

> **Host this text at a public URL and enter that URL in App Store Connect → App Information → Privacy Policy URL. Apple requires this before submission.**

---

### Privacy Policy for Schedulo

**Effective Date:** June 23, 2026
**Last Updated:** June 26, 2026

Schedulo ("we," "our," or "the app") is developed and operated by Sheshank Pendli. This Privacy Policy describes how we collect, use, store, and protect your personal information when you use the Schedulo iOS application.

By downloading or using Schedulo, you agree to the practices described in this policy.

#### 1. Information We Collect

**a) Account Information**
- Email address (used for sign-in)
- Password (stored as a cryptographic hash by Firebase Authentication; we never access your plaintext password)
- Display name (full name you provide)

**b) Work & Financial Data**
- Shift details: employer/company name, job role, start and end times, break duration
- Pay information: hourly rates, custom earnings, overtime thresholds and multipliers, bonus tracking
- Pay adjustments: type (bonus, deduction, overpaid, underpaid, correction), amount, and notes
- Job profiles: titles, default rates, weekly hour goals, gig work designations

**c) Team Collaboration Data**
If you create or join a team:
- Team membership (your user ID, display name, email, role)
- Shift assignments and their status (assigned, accepted, declined)
- Team chat messages (text content you compose)
- **Photos** you choose to share in team chat. Shared photos are uploaded to Firebase Cloud Storage, visible to members of that team, and **automatically deleted from our servers once every team member has seen the message**. The app uses Apple's system photo picker and does **not** request access to your full photo library.
- Shift swap requests (identifiers for requester, target member, and associated shifts)

**d) User Preferences**
- Theme preference (light, dark, system)
- Notification and reminder settings
- Default company and hourly rate
- Calendar sync preferences

**e) Diagnostics**
We use Firebase Crashlytics to automatically collect crash reports including device model, OS version, app version, and error stack traces. These reports do **not** contain your personal data, shift data, or financial information.

**f) Calendar Data**
If you enable calendar sync, the app creates shift events in your device calendar via EventKit. The app requests full calendar access but only **writes** shift events — it does not read or collect your existing calendar data.

**g) Biometric Data**
If you enable Face ID or Touch ID, authentication is handled entirely by Apple's LocalAuthentication framework on your device. No biometric data is ever transmitted to our servers or any third party.

#### 2. How We Use Your Information

We use your information exclusively to:
- Provide the app's core functionality (shift tracking, pay calculation, team management)
- Authenticate your identity
- Send local shift reminder notifications
- Sync shifts to your device calendar when you opt in
- Diagnose and fix crashes
- Enable team features (shift assignments, chat, swap requests)

**We do not:**
- Display advertisements
- Sell or rent your data to anyone
- Track your behavior across apps or websites
- Send marketing emails
- Use your data for profiling or automated decision-making

#### 3. Data Storage & Third-Party Services

Your data is stored in **Google Cloud Firestore** (Firebase), and chat photos in **Firebase Cloud Storage**, encrypted in transit (TLS) and at rest (Google Cloud default encryption).

| Service | Provider | Data Received | Purpose |
|---|---|---|---|
| Firebase Authentication | Google LLC | Email, password hash | Account authentication |
| Cloud Firestore | Google LLC | All app data | Cloud storage & real-time sync |
| Cloud Storage for Firebase | Google LLC | Team chat photos | Image storage & delivery (auto-deleted after all members view) |
| Firebase Crashlytics | Google LLC | Crash logs, device info | Crash diagnostics |
| EventKit (Calendar) | Apple Inc. | Shift event details (write-only) | Calendar sync |

We do not use any advertising frameworks, analytics SDKs, social media integrations, or payment processing services.

#### 4. Data Sharing

We do **not** sell, trade, or transfer your personal data to third parties for their independent use.

Your data is accessible to:
- **Google/Firebase** — as our data processor, to provide cloud infrastructure
- **Your team members** — within a team, members can see your name, email, assigned shifts, messages, and swap requests
- **Law enforcement** — only when required by applicable law

#### 5. Data Retention & Deletion

Your data is retained for as long as your account is active. You may delete your account at any time from within the app (Settings → Delete Account). Upon deletion, we permanently remove:
- Your profile and app settings
- All personal shift records
- All job profiles
- All pay adjustment records
- Your team memberships

Photos shared in team chat are deleted automatically once all team members have viewed the message.

To request data deletion by email, contact sheshank3336@gmail.com. We will process requests within 30 days.

#### 6. Children's Privacy

Schedulo is not directed at children under 13 (or the minimum age of digital consent in your country). We do not knowingly collect information from children. If you believe a child has created an account, please contact us and we will delete the account.

#### 7. Security

We use industry-standard measures to protect your data:
- TLS/SSL encryption for all network communication
- Firebase Security Rules restricting database access to authorized users
- Cryptographic password hashing via Firebase Authentication
- Optional biometric device lock

No system is perfectly secure. We cannot guarantee absolute protection but will notify affected users of any data breach as required by law.

#### 8. Your Privacy Rights

**All users** may:
- Access their data (viewable in-app)
- Correct their data (editable in-app)
- Delete their account and all associated data (in-app or via email)

**California residents (CCPA/CPRA):** We do not sell or share personal information for cross-context behavioral advertising. You have the right to know, delete, and opt out, and we will not discriminate against you for exercising these rights.

**EU/EEA residents (GDPR):** Our legal basis for processing is performance of a contract (providing the service) and legitimate interest (crash reporting). You have rights to access, rectification, erasure, restriction, portability, and objection. Contact us to exercise these rights.

#### 9. International Data Transfers

Your data is stored on Google Cloud servers which may be located outside your country of residence. By using the app, you consent to this transfer. Google complies with applicable data transfer frameworks.

#### 10. Changes to This Policy

We may update this policy periodically. We will notify you of material changes through the app or at the privacy policy URL. Your continued use after changes constitutes acceptance.

#### 11. Contact

For questions, concerns, or data requests:

**Email:** sheshank3336@gmail.com
**Developer:** Sheshank Pendli

---

## 3. App Privacy Nutrition Labels (App Store Connect)

> **Navigate to:** App Store Connect → Your App → App Privacy → Get Started

### Do you or your third-party partners collect data from this app?

**Yes**

### Data Used to Track You

**None.** We do not track users across apps or websites owned by other companies.

### Data Linked to You

The following data types are collected and linked to your identity:

#### Contact Info

| Data Type | Collected | Purpose | Linked to Identity |
|---|---|---|---|
| Name | **Yes** | App Functionality | Yes |
| Email Address | **Yes** | App Functionality | Yes |

#### Financial Info

| Data Type | Collected | Purpose | Linked to Identity |
|---|---|---|---|
| Other Financial Info (hourly rates, earnings, pay adjustments) | **Yes** | App Functionality | Yes |

#### Identifiers

| Data Type | Collected | Purpose | Linked to Identity |
|---|---|---|---|
| User ID | **Yes** | App Functionality | Yes |

#### Usage Data

| Data Type | Collected | Purpose | Linked to Identity |
|---|---|---|---|
| Other Usage Data (shift creation, task completion actions) | **Yes** | App Functionality | Yes |

#### User Content

| Data Type | Collected | Purpose | Linked to Identity |
|---|---|---|---|
| Photos or Videos (photos shared in team chat) | **Yes** | App Functionality | Yes |
| Other User Content (team chat messages) | **Yes** | App Functionality | Yes |

#### Other Data

| Data Type | Collected | Purpose | Linked to Identity |
|---|---|---|---|
| Other Data Types (shift details, job profiles) | **Yes** | App Functionality | Yes |

### Data Not Linked to You

#### Diagnostics

| Data Type | Collected | Purpose | Linked to Identity |
|---|---|---|---|
| Crash Data | **Yes** | App Functionality (stability) | No |

### Data NOT Collected

Confirm "No" for all of the following in App Store Connect:

- Health & Fitness (Health, Fitness)
- Location (Precise Location, Coarse Location)
- Sensitive Info
- Contacts (Contacts, Phone Number, Physical Address)
- Browsing History
- Search History
- Purchases
- Audio Data
- Gameplay Content
- Customer Support
- Advertising Data
- Product Interaction (only collect functional interactions, not for analytics)
- Credit Info
- Device ID (Crashlytics ID is classified under Diagnostics, not Device ID per Apple guidelines)
- Performance Data (covered under Crash Data)

---

## 4. App Store Connect — Privacy Questions Walkthrough

When you tap "Get Started" under App Privacy, Apple asks a series of questions. Here are the exact answers:

### Question 1: Do you or your third-party partners collect data from this app?
**Yes**

### Question 2: For each data type, select the applicable purposes:

**Contact Info — Name:**
- App Functionality: **Yes**
- Analytics: No
- Developer Advertising: No
- Third-Party Advertising: No
- Product Personalization: No

**Contact Info — Email Address:**
- App Functionality: **Yes**
- Analytics: No

**Financial Info — Other Financial Info:**
- App Functionality: **Yes**
- Analytics: No

**Identifiers — User ID:**
- App Functionality: **Yes**
- Analytics: No

**User Content — Photos or Videos:**
- App Functionality: **Yes**
- Analytics: No

**User Content — Other User Content (team chat messages):**
- App Functionality: **Yes**
- Analytics: No

**Usage Data — Other Usage Data:**
- App Functionality: **Yes**
- Analytics: No

**Diagnostics — Crash Data:**
- App Functionality: **Yes** (improving stability)
- Analytics: No

**Other Data — Other Data Types:**
- App Functionality: **Yes**
- Analytics: No

### Question 3: Is the data linked to the user's identity?

| Data Type | Linked? |
|---|---|
| Name | **Yes** |
| Email | **Yes** |
| Financial Info | **Yes** |
| User ID | **Yes** |
| Photos or Videos | **Yes** |
| Other User Content | **Yes** |
| Usage Data | **Yes** |
| Crash Data | **No** |
| Other Data | **Yes** |

### Question 4: Is the data used for tracking?

**No** — for all data types. We do not track users across apps or websites.

---

## 5. Export Compliance (Encryption)

> **App Store Connect → Your App → General → App Information → Export Compliance**

| Question | Answer |
|---|---|
| Does your app use encryption? | **Yes** (HTTPS/TLS for Firebase communication) |
| Does your app qualify for any encryption exemptions? | **Yes** |
| Which exemption? | The app uses standard HTTPS/TLS encryption only, which qualifies under exemption category **(b)(2)** — authentication, access control, digital signature, or encryption for data in transit |
| Do you need to submit encryption documentation? | **No** (TLS-only apps are exempt) |

In `Info.plist`, this is already declared:
```xml
<key>ITSAppUsesNonExemptEncryption</key>
<false/>
```

---

## 6. Age Rating Questionnaire

| Category | Selection |
|---|---|
| Cartoon or Fantasy Violence | None |
| Realistic Violence | None |
| Prolonged Graphic or Sadistic Violence | None |
| Profanity or Crude Humor | None |
| Mature/Suggestive Themes | None |
| Horror/Fear Themes | None |
| Medical/Treatment Information | None |
| Alcohol, Tobacco, or Drug Use | None |
| Simulated Gambling | None |
| Sexual Content or Nudity | None |
| Unrestricted Web Access | No |
| Gambling and Contests | No |

**Expected age rating: 4+**

Note: Team chat allows user-generated content (text and photos), but since it's within private teams (invite-code gated, not publicly discoverable), Apple does not typically require a higher age rating. If Apple requests it during review, bump to 12+ and add "Infrequent/Mild Mature/Suggestive Themes."

---

## 7. App Review Notes

> **Provide in App Store Connect → Your App → Version → App Review Information**

### Demo Account

```
Email: [provide a test account email]
Password: [provide a test account password]
```

### Notes for Reviewer

```
Schedulo is a shift tracking and team management app for hourly workers and managers.

To test team features:
1. Sign in with the demo account above
2. Navigate to the "Team" tab
3. A demo team is pre-configured with sample shifts and members

To test core features:
1. Add a shift from the Dashboard (tap +)
2. View earnings in the Pay tab
3. Enable calendar sync from shift creation screen
4. Enable Face ID from Settings

The app requires an internet connection for all features (Firebase backend).
No in-app purchases. No ads.
```

---

## 8. Pre-Submission Checklist

- [ ] Privacy policy hosted at a public URL
- [ ] Privacy policy URL entered in App Store Connect → App Information
- [ ] App Privacy nutrition labels completed (all data types declared)
- [ ] Export compliance answered (ITSAppUsesNonExemptEncryption = false)
- [ ] Age rating questionnaire completed
- [ ] App category set (Business, Productivity)
- [ ] App name, subtitle, description, keywords filled in
- [ ] Screenshots uploaded for required device sizes (6.7" iPhone mandatory)
- [ ] App icon provided (1024x1024 px, no alpha channel, no rounded corners)
- [ ] Demo account credentials provided in App Review Information
- [ ] Build uploaded via Xcode or Transporter
- [ ] Version number and build number set correctly
- [ ] Support URL provided
- [ ] Contact information filled in
- [ ] "Sign in with Apple" — **not required** (app uses email/password only, no third-party social login)
- [ ] Account deletion available in-app (Apple requirement since June 2022)
- [ ] Test on physical device before submission

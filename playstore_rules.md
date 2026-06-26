# Google Play Store — Listing, Privacy Policy & Data Safety

## 1. Store Listing Details

| Field | Value |
|---|---|
| **App name** | Schedulo — Shift & Pay Tracker |
| **Short description** (80 chars) | Track shifts, manage team schedules, and calculate earnings in one app. |
| **Category** | Business |
| **Content rating** | Everyone |
| **Target audience** | 18+ (employment/financial content) |
| **Contact email** | sheshank3336@gmail.com |
| **App ID** | `com.schedulo2.app` |

### Long Description

Schedulo is the all-in-one shift tracking and team management app built for hourly workers, gig workers, and store managers.

**For Workers:**
- Log shifts with start/end times, breaks, and hourly rates
- Track earnings across multiple jobs with overtime calculations
- Get shift reminders so you never miss a clock-in
- Sync shifts to your phone's calendar
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
- Works across iOS and Android
- Real-time sync — changes appear instantly for all team members
- Biometric lock (fingerprint/Face ID) for privacy
- Home screen widget showing your next shift and weekly earnings
- Dark mode support
- Account deletion available in-app

### Screenshots Required

1. Dashboard with upcoming shifts and weekly earnings
2. Shift creation/editing screen
3. Team dashboard with bento tile grid
4. Team schedule with assigned shifts
5. Team roster weekly grid view
6. Team chat messages
7. Shift swap request flow
8. Home screen widget

---

## 2. Privacy Policy

> **Host this text at a public URL and enter that URL in Play Console → Store Presence → Store Listing → Privacy Policy.**

---

### Privacy Policy for Schedulo

**Effective Date:** June 23, 2026
**Last Updated:** June 26, 2026

Schedulo ("we," "our," or "the app") is operated by Sheshank Pendli. This Privacy Policy explains how we collect, use, store, and protect your information when you use the Schedulo mobile application.

By using Schedulo, you agree to the collection and use of information as described in this policy.

#### 1. Information We Collect

**a) Account Information**
When you create an account, we collect:
- Email address
- Password (stored as a hash by Firebase Authentication; we never see or store your plaintext password)
- Full name (display name you provide)

**b) Work & Financial Data**
When you use the app, you may provide:
- Shift details: employer/company name, job role, start and end times, break duration
- Pay information: hourly rates, custom earnings amounts, overtime thresholds and multipliers, bonus tracking
- Pay adjustments: bonus, deduction, overpaid, underpaid, or correction entries with amounts and notes
- Job profiles: job titles, default rates, weekly hour goals, gig work designations

**c) Team Data**
If you create or join a team:
- Team membership information (your user ID, display name, email, and role within the team)
- Team shift assignments (who is assigned which shifts, shift status)
- Team messages (text content you send in team chat, announcements)
- **Photos** you choose to share in team chat. Shared photos are uploaded to Firebase Cloud Storage, visible to members of that team, and **automatically deleted from our servers once every team member has seen the message**. The app uses the privacy-friendly system photo picker and does **not** request access to your full photo library.
- Shift swap requests (requester and target member identifiers, request status)

**d) User Preferences**
- Theme preference (light, dark, or system)
- Notification and reminder settings
- Default company and hourly rate
- Calendar sync preferences

**e) Crash Reports**
We use Firebase Crashlytics to collect anonymous crash and error reports. These include:
- Device model and operating system version
- App version
- Stack traces of errors
- Crashlytics installation ID

Crash reports do **not** include your personal data, shift data, or financial information.

**f) Calendar Data**
If you enable calendar sync, the app writes shift events (title, start/end time, notes) to your device's calendar. We do **not** read or collect your existing calendar events.

**g) Biometric Data**
If you enable biometric lock, Face ID or fingerprint authentication is processed entirely on your device. We never receive, transmit, or store your biometric data.

#### 2. How We Use Your Information

We use your information solely to:
- Provide and operate the app's core features (shift tracking, earnings calculation, team management)
- Authenticate your identity and secure your account
- Deliver shift reminder notifications
- Sync shift events to your device calendar (when enabled)
- Improve app stability through crash reports
- Facilitate team collaboration (shift assignments, chat, swap requests)

We do **not** use your data for:
- Advertising or ad targeting
- Selling to third parties
- User behavior analytics or profiling
- Marketing communications

#### 3. Data Storage & Third-Party Services

All personal and work data is stored in **Google Cloud Firestore** (Firebase), a cloud database operated by Google LLC, and chat photos are stored in **Firebase Cloud Storage**. Data is:
- Encrypted in transit using TLS/SSL
- Encrypted at rest by Google Cloud
- Stored in Google's data centers subject to [Google's Privacy Policy](https://policies.google.com/privacy) and [Firebase Terms of Service](https://firebase.google.com/terms)

**Third-party services used:**

| Service | Provider | Data Received | Purpose |
|---|---|---|---|
| Firebase Authentication | Google | Email, password hash | Account sign-in |
| Cloud Firestore | Google | All app data (profile, shifts, teams, messages) | Data storage & real-time sync |
| Cloud Storage for Firebase | Google | Team chat photos | Image storage & delivery (auto-deleted after all members view) |
| Firebase Crashlytics | Google | Crash logs, device info | Error monitoring |
| Device Calendar | OS Provider | Shift event details (write-only) | Calendar sync |

We do **not** integrate any advertising SDKs, analytics platforms (e.g., Google Analytics, Mixpanel), social media SDKs, or payment processors.

#### 4. Data Sharing

We do **not** sell, rent, or share your personal data with third parties for their own purposes.

Data is shared only with:
- **Google/Firebase** — as our infrastructure provider, to operate the app
- **Your team members** — if you join a team, other team members can see your display name, email, assigned shifts, chat messages, and swap requests within that team
- **Law enforcement** — if required by law, subpoena, or court order

#### 5. Data Retention

- Your data is retained in Firestore for as long as your account exists
- There is no automatic data expiration or deletion schedule
- You can delete your account at any time (see Section 6)

#### 6. Data Deletion

You can delete your account from within the app at any time. When you delete your account, the following data is **permanently removed** from our servers:
- Your profile and settings
- All personal shifts
- All job profiles
- All pay adjustments
- Your team memberships

Team messages you sent and team shifts you were assigned may retain your user ID reference but will no longer be linked to an active account. Photos shared in team chat are deleted automatically once all team members have viewed the message.

To request manual data deletion, contact us at sheshank3336@gmail.com.

#### 7. Children's Privacy

Schedulo is not intended for children under the age of 13 (or the applicable age of digital consent in your jurisdiction). We do not knowingly collect personal information from children. If we learn that we have collected data from a child under 13, we will delete it promptly. If you believe a child has provided us with personal data, please contact us.

#### 8. Security

We implement reasonable security measures including:
- TLS/SSL encryption for all data in transit
- Firebase Authentication for secure sign-in
- Firestore Security Rules that restrict data access to authorized users only
- Optional biometric authentication for app access

No method of transmission or storage is 100% secure. While we strive to protect your data, we cannot guarantee absolute security.

#### 9. Your Rights

Depending on your jurisdiction, you may have the right to:
- **Access** your personal data (available in-app)
- **Correct** inaccurate data (editable in-app)
- **Delete** your account and data (available in-app)
- **Export** your data (contact us)
- **Object** to data processing (contact us)

**For California residents (CCPA):** We do not sell personal information. You have the right to know what data we collect, request deletion, and not be discriminated against for exercising your rights.

**For EU/EEA residents (GDPR):** Our legal basis for processing is contract performance (providing the app service you signed up for) and legitimate interest (crash reporting for app stability). You may contact us to exercise your rights under GDPR.

#### 10. Changes to This Policy

We may update this Privacy Policy from time to time. Changes will be posted within the app or at the privacy policy URL. Continued use after changes constitutes acceptance.

#### 11. Contact Us

If you have questions about this Privacy Policy or your data:

**Email:** sheshank3336@gmail.com
**Developer:** Sheshank Pendli

---

## 3. Data Safety Form (Play Console)

> **Navigate to:** Play Console → App content → Data safety → Manage

### Overview Questions

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes** |
| Do you provide a way for users to request that their data is deleted? | **Yes** (in-app account deletion + email request) |

### Data Types — Collected

| Category | Data Type | Collected? | Shared? | Purpose | Optional? |
|---|---|---|---|---|---|
| **Personal info** | Name | Yes | No | App functionality | No |
| **Personal info** | Email address | Yes | No | App functionality, Account management | No |
| **Personal info** | User IDs | Yes | No | App functionality | No |
| **Financial info** | Salary or wage info | Yes | No | App functionality | No |
| **Financial info** | Other financial info (pay adjustments, bonuses) | Yes | No | App functionality | Yes |
| **Messages** | Other in-app messages (team chat) | Yes | No | App functionality | Yes |
| **Photos and videos** | Photos (shared in team chat) | Yes | No | App functionality | Yes |
| **App activity** | App interactions (shift creation, task completion) | Yes | No | App functionality | No |
| **App info and performance** | Crash logs | Yes | No | Analytics (crash reporting) | No |
| **Device or other IDs** | Device or other IDs (Crashlytics installation ID) | Yes | No | Analytics (crash reporting) | No |

### Data Types — NOT Collected

Check "No" for all of the following:
- Location (approximate or precise)
- Phone number
- Address
- Race and ethnicity
- Political or religious beliefs
- Sexual orientation
- Health info
- Fitness info
- Videos
- Voice or sound recordings
- Music files
- Files and docs
- Calendar events (we write-only to calendar, we don't read/collect)
- Contacts
- SMS or MMS
- Web browsing history
- Installed apps
- Other user-generated content (beyond team chat above)
- Other actions
- Search history
- Advertising ID

### Data Handling Practices

| Question | Answer |
|---|---|
| Is data encrypted in transit? | **Yes** (TLS/SSL) |
| Is data encrypted at rest? | **Yes** (Google Cloud default encryption) |
| Can users request data deletion? | **Yes** |
| Committed to Play Families Policy? | **No** (not a children's app) |
| Independent security review? | **No** |

### Shared Data

**None.** No data types are shared with third parties for their own purposes. Firebase/Google acts as a service provider (data processor), not a data recipient.

> **Note:** In Play Console, "shared" means transferred to a third party for their own use. Firebase hosting does not count as "sharing" per Google's definition since Google processes data on your behalf.

---

## 4. Content Rating Questionnaire

| Question | Answer |
|---|---|
| Violence | No |
| Sexual content | No |
| Language | No (user-generated team chat may contain language, but app doesn't provide it) |
| Controlled substances | No |
| User interaction | Yes (team chat text and photos, shift swaps) |
| Users can share location | No |
| Users can purchase digital goods | No |
| Unrestricted internet access | No |
| Personal info shared with third parties | No |

Expected rating: **Everyone** or **Everyone 10+** (due to user interaction in team chat)

---

## 5. Pre-Launch Checklist

- [ ] Privacy policy hosted at a public URL
- [ ] Privacy policy URL entered in Play Console
- [ ] Data Safety form completed
- [ ] Content rating questionnaire completed
- [ ] Target audience and content set (18+ / not designed for children)
- [ ] App access instructions provided if app requires login (provide test account credentials)
- [ ] Store listing: title, short description, full description, screenshots (phone + 7" tablet + 10" tablet)
- [ ] Feature graphic (1024x500 px)
- [ ] App icon (512x512 px)
- [ ] App category set to "Business"
- [ ] Contact email set
- [ ] Signed release AAB uploaded
- [ ] Ads declaration: "No, my app does not contain ads"
- [ ] Government apps declaration: "No"
- [ ] Financial features declaration: "No" (app tracks pay but does not process payments)

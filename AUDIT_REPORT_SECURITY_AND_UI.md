# Schedulo2 — Security & UI Consistency Audit

**Date:** 2026-07-02
**Scope:** Cybersecurity / data-security issues and UI consistency (design tokens & component reuse). Code organization and visual redesign are explicitly out of scope for this audit.
**Method:** Static review of `firestore.rules`, `storage.rules`, Android sources (`androidApp/`), iOS sources (`iosApp/`), the KMP shared module, Android manifest/config XML, and CI workflows. No code was executed against production data.

---

## 1. Executive Summary

The most serious problems are server-side: **Firestore security rules do not implement team-level data isolation.** Every team-related collection (and the `jobs` collection) is readable by *any* signed-in user, which exposes team rosters, shift schedules, chat messages, and every user's job titles and hourly pay rates to strangers. Compounding this, team membership can be created for any team **without knowing the invite code**, and invite codes themselves are readable — so a malicious user can enumerate teams, join any of them, and read or post in their chat. Storage rules allow any authenticated user to read **and delete** any team's chat images. On the client side, the iOS app never detaches team listeners on sign-out, so a previous account's team data keeps streaming after an account switch.

The UI problem is not a missing design system — both platforms have a real, well-used color/theme layer — it is a **half-finished** one: Android has no spacing/dimension tokens (1,144 hard-coded `.dp` literals) and iOS has no typography scale (462 ad-hoc font calls), and small components are re-implemented per screen instead of shared. This is why the UI drifts and feels inconsistent.

### Findings index

| ID | Severity | Area | Finding |
|---|---|---|---|
| SEC-01 | **Critical** | Firestore rules | All team collections + `jobs` readable by any authenticated user (no membership scoping) |
| SEC-02 | **Critical** | Firestore rules | Anyone can join any team: membership create skips invite-code verification; invite codes are readable |
| SEC-03 | **High** | Storage rules | Any authenticated user can read and delete any team's chat images |
| SEC-04 | **High** | iOS client | Team Firestore listeners never detached on sign-out — cross-account data exposure |
| SEC-05 | Medium | Firestore rules | No `hasOnly()` field lockdown — arbitrary extra fields writable on almost all collections |
| SEC-06 | Medium | Firestore rules | `swap_requests` create does not verify team membership or shift ownership |
| SEC-07 | Low | Firestore rules | `shifts` create cross-write path depends on unscoped `team_shifts` reads |
| SEC-08 | Low | Android config | `allowBackup="true"` with sample-only (empty) backup/extraction rules |
| SEC-09 | Info | Auth | No email verification anywhere in either app |
| SEC-10 | Info | Repo/config hygiene | `debug.keystore.base64` in repo root; no Firebase App Check; Crashlytics Android-only |
| UI-01 | High (maintainability) | Android | No spacing/dimension tokens — 1,144 hard-coded `.dp` literals |
| UI-02 | Medium | Android | Small components duplicated per file instead of shared |
| UI-03 | High (maintainability) | iOS | No typography scale — 462 ad-hoc `.font(.system(...))` calls |
| UI-04 | Medium | Cross-platform | Two parallel design-token sources; `DesignTokens.kt` is dead code duplicating `Color.kt` |

**What is already good:** per-user collections (`profiles`, `settings`, `shifts`, `pay_adjustments`) are correctly scoped with type/length validation; cleartext traffic is disabled on Android (`network_security_config.xml`) and iOS has no ATS exceptions; exported components in the Android manifest are limited to system-broadcast receivers; no hard-coded secrets were found in source; Android color theming is disciplined (~419 `MaterialTheme.colorScheme` usages vs. only 22 hard-coded colors); iOS colors are centralized in `AppTheme.swift`.

---

## 2. Security Findings

### SEC-01 — Critical: No team-level data isolation in Firestore rules

**Where:** `firestore.rules` — `jobs` (line 74), `teams` (116), `team_members` (135), `team_shifts` (160), `team_messages` (200), `team_tasks` (229), `swap_requests` (255). Each is:

```
allow read: if request.auth != null;
```

**Attack scenario:** Any person who creates a free account can run collection queries against the named database and read: every team's name/owner/invite code, every team's member roster and per-member `defaultHourlyRate`, every assigned shift with pay rate, all team chat messages (including image URLs), all tasks, and all swap requests. `jobs` additionally exposes every individual user's job titles, pay rates, and bonus amounts — including solo users who never joined a team. This is a full cross-tenant confidentiality breach of pay and schedule data.

**Remediation:** Introduce a membership helper and scope every team collection's read to it. Membership can be proven with an `exists()` on a deterministic `team_members` document ID (`{teamId}_{userId}`), which requires migrating membership documents to deterministic IDs (or duplicating a `memberIds` array on the team document):

```
function isTeamMember(teamId) {
  return exists(/databases/$(database)/documents/team_members/$(teamId + '_' + request.auth.uid));
}

match /team_messages/{messageId} {
  allow read: if request.auth != null && isTeamMember(resource.data.teamId);
  ...
}
```

`jobs` reads should be `resource.data.userId == request.auth.uid`, with the manager-visibility use case served through `team_members.defaultHourlyRate` (already exists) rather than open reads.

> **Coordination warning:** Firestore rules are *not* filters — a query must be provably within the rule or it fails outright. Every client listener must be checked when rules tighten: Android `TeamViewModel.kt` (15 `addSnapshotListener` call sites) and iOS `TeamViewModel.swift` (7 listeners). Queries filtered by `whereEqualTo("teamId", …)` remain provable under an `isTeamMember(resource.data.teamId)` rule; anything broader breaks. There is also **no rules deployment in CI** (no `firebase.json` in the repo) — updated rules must be deployed via the Firebase console or `firebase deploy --only firestore:rules`, and tested with the Rules emulator first.

### SEC-02 — Critical: Anyone can join any team; invite codes are readable

**Where:** `firestore.rules` lines 136–145 (`team_members` create) and 116 (`teams` read). Client join flow: `androidApp/.../TeamViewModel.kt:524-535`.

**Attack scenario (two independent paths):**
1. `team_members` create only requires `userId == request.auth.uid`, a string `teamId`, and role `"member"`. The invite code is checked **client-side only** (the app queries `teams` by `inviteCode`), so a direct Firestore write joins any team with no code at all.
2. Even if path 1 were closed, `teams` reads are open (SEC-01), so invite codes can simply be read and used "legitimately."

Once joined, the attacker is a member: they can read chat, post messages, and appear on the roster.

**Remediation:** Verify the invite code inside the create rule by requiring the client to include it in the membership document:

```
allow create: if request.auth != null
  && request.resource.data.userId == request.auth.uid
  && request.resource.data.role == "member"
  && get(/databases/$(database)/documents/teams/$(request.resource.data.teamId))
       .data.inviteCode == request.resource.data.inviteCode;
```

Because scoping `teams` reads (SEC-01) breaks the current "query teams by inviteCode" join flow, add a small lookup collection `invite_codes/{code} → { teamId }` maintained alongside the team, with `allow get` (never `list`) for authenticated users — the code itself is the secret. Longer-term, consider rotating codes and expiring invites; today codes are permanent 6-character values.

### SEC-03 — High: Storage — any user can read and delete any team's chat images

**Where:** `storage.rules` lines 5–11: `chat_images/{teamId}/{imageId}` allows read and **delete** for any `request.auth != null`; write checks only size (<2 MB) and `image/*` content type.

**Attack scenario:** Any account holder can fetch any team's chat images (privacy breach) or delete them (data destruction), and can upload images into another team's folder.

**Remediation:** Storage rules can call Firestore since rules v2 — mirror the membership check:

```
match /chat_images/{teamId}/{imageId} {
  allow read, write: if request.auth != null
    && firestore.exists(/databases/schedulo2/documents/team_members/$(teamId + '_' + request.auth.uid))
    && (request.resource == null || (request.resource.size < 2 * 1024 * 1024
        && request.resource.contentType.matches('image/.*')));
  allow delete: if request.auth != null /* sender or team owner via metadata/Firestore check */;
}
```

Note the cross-service call must reference the **named database** `schedulo2`, not `(default)`.

### SEC-04 — High: iOS team listeners survive sign-out (cross-account data exposure)

**Where:** `iosApp/Schedulo2/ViewModels/TeamViewModel.swift` (1,268 lines). It registers 7 `addSnapshotListener`s, defines `removeAllListeners()` (~line 1254) — but **nothing ever calls it**, and there is no `deinit`. The VM is an app-lifetime `@StateObject` singleton (`ScheduloApp.swift:10-12`), so its lifecycle never ends.

**Attack/failure scenario:** User A signs out on a shared device; User B signs in. User A's team listeners keep streaming (team chat, shifts, roster) into the still-alive view model until each listener happens to be re-attached, and any UI bound to that state can briefly show User A's data to User B. By contrast, `FirebaseService.swift` correctly tears its 5 listeners down in `signOut`/`deleteAccount`/`deinit`, and Android removes registrations in `refreshData()`.

**Remediation:** Call `TeamViewModel.removeAllListeners()` from the sign-out and delete-account paths (mirroring `FirebaseService`), add a `deinit` as a safety net, and clear all `@Published` team state on sign-out.

### SEC-05 — Medium: No field-name lockdown on writes

**Where:** `firestore.rules` — only the `team_messages` update path uses `hasOnly()` (line 220). Every other create/update validates *known* fields but accepts unlimited **extra** fields.

**Risk:** Clients (or attackers) can attach arbitrary payloads to documents — junk data growth, smuggled fields that a future feature or a buggy client might trust (e.g., writing `role: "manager"` style fields onto documents where rules don't check them today but code reads them tomorrow).

**Remediation:** Add to each create/update: `request.resource.data.keys().hasOnly([...allowed field names...])`. Do this in the same pass as SEC-01 since both touch every match block.

### SEC-06 — Medium: `swap_requests` creation is unanchored

**Where:** `firestore.rules` lines 256–263. Create only checks `requesterId == auth.uid` and that `teamId`/shift IDs are strings — not that the requester belongs to the team, nor that `requesterShiftId` is actually the requester's shift, nor that `targetShiftId` belongs to `targetMemberId`.

**Risk:** Spoofed or spam swap requests into arbitrary teams; a manager approving a plausible-looking but fabricated request could corrupt schedules.

**Remediation:** Require `isTeamMember(teamId)` (from SEC-01) plus `get()` checks that both shifts exist in `team_shifts`, belong to the stated members, and share the same `teamId`.

### SEC-07 — Low: `shifts` cross-write path relies on unscoped reads

**Where:** `firestore.rules` lines 54–57: a manager may create a personal `shifts` copy for an assignee if a matching `team_shifts` doc names them as `assignedBy`/`assignedTo`. The logic is sound, but its integrity depends on `team_shifts` writes being trustworthy — which SEC-01/SEC-02 currently undermine. Re-verify this path after the team rules are fixed; no standalone change needed.

### SEC-08 — Low: Auto Backup enabled with empty rules

**Where:** `androidApp/src/main/AndroidManifest.xml:32` (`android:allowBackup="true"`) with `backup_rules.xml` and `data_extraction_rules.xml` still containing only commented-out sample content.

**Risk:** App data — shared prefs (`schedulo_prefs`, widget earnings cache) and Firestore's on-device cache — is included in Google cloud backups and device-to-device transfer by default. Low sensitivity today (no tokens are manually stored; Firebase Auth state is managed by the SDK), but earnings/schedule data is personal.

**Remediation:** Either set `allowBackup="false"` or write real `<exclude>` rules for the Firestore cache directory and preference files.

### SEC-09 — Info: No email verification

No `sendEmailVerification` / `isEmailVerified` usage exists on either platform. Accounts are fully functional with unverified emails — spam accounts are cheap (relevant to SEC-02), and email-based recovery trust is weaker. Consider requiring verification before team features unlock.

### SEC-10 — Info: Hygiene items

- `debug.keystore.base64` is committed at the repo root. It's a *debug* keystore (low direct risk) but doesn't belong in version control root; `.gitignore` already excludes `debug.keystore`/`*.jks`, so the base64 copy sidesteps that intent.
- `google-services.json` committed — normal for Firebase, but ensure the Android API key is restricted (package name + SHA-1) in Google Cloud console since rules are the real security boundary.
- **No Firebase App Check** on either platform — direct REST access to Firestore with a stolen/scripted auth token is unthrottled by client attestation. Worth enabling once rules are fixed.
- **Crashlytics is Android-only**; iOS links the SDK (`project.yml` includes FirebaseCrashlytics) but parity should be verified when fixing SEC-04.
- CI deploys straight to Play internal / TestFlight on push to `main` with **no test or lint gates** — a security regression in rules or auth code ships unreviewed by tooling.

---

## 3. UI Consistency Findings

The perceived "mess" is measurable: both platforms enforce **color** through a theme, but neither enforces **spacing** (Android) or **typography** (iOS), and small UI patterns are copy-pasted per screen. Fixing the token layer is the cheapest path to a consistent-feeling UI without a redesign.

### UI-01 — Android: no spacing/dimension tokens (1,144 hard-coded `.dp`)

Padding, corner radii, icon sizes, and component heights are magic numbers at every call site across `TabsSupport.kt`, `TeamSupport.kt`, `TeamDetailSupport.kt`, `DashboardSupport.kt`, `MainActivity.kt`, etc. Identical concepts (card padding, section gaps) drift between screens because there is nothing to reference.

**Remediation:** Add a `Dimens` object next to `ui/theme/Theme.kt` (e.g., `spacingXs/S/M/L/Xl`, `radiusCard`, `iconSize`, `minTouchTarget = 48.dp`) and adopt it opportunistically — new code uses tokens immediately; existing screens get migrated screen-by-screen. Contrast with color, which is already disciplined (~419 `colorScheme` uses, 22 stray `Color(0x…)` literals mostly in `AlarmActivity.kt` — sweep those into `Color.kt` in the same pass).

### UI-02 — Android: duplicated micro-components

Near-identical private composables are re-declared per file instead of living in `Components.kt` (242 lines, underused): `StatCard`, `StatPill`, `JobMetric`, `SectionLabel`, `SettingsGroupLabel`, hand-rolled dialog scaffolds, and three structurally similar calendar renderers in `TabsSupport.kt`. Each copy is a place for spacing/typography to diverge.

**Remediation:** Promote the repeated stat-card/pill/section-label patterns into `Components.kt` (parameterized), and give dialogs one shared scaffold. This is mechanical and low-risk; it also shrinks the monolith files as a side effect without being a reorganization project.

### UI-03 — iOS: no typography scale (462 ad-hoc font calls)

`Theme/AppTheme.swift` (225 lines) centralizes colors, gradients, and card styling well — but there is no type scale, so every label hand-picks `.font(.system(size: …, weight: …))` (462 call sites across `Views/`). Headings and body text differ subtly between screens.

**Remediation:** Add an `AppFont` (or `Font` extension) scale to `AppTheme.swift` — e.g., `largeTitle/title/headline/body/caption` mapped to fixed sizes or, better, Dynamic Type text styles (also an accessibility win, since hard-coded sizes ignore the user's text-size setting). Migrate views screen-by-screen, same policy as UI-01.

### UI-04 — Cross-platform: two token sources, one dead

`shared/src/commonMain/.../util/DesignTokens.kt` duplicates the hex palette in `androidApp/.../ui/theme/Color.kt`, and `AppTheme.swift` states it manually "matches Android" — yet **no platform references `DesignTokens.kt`**. Three hand-synced palettes is how platforms drift apart.

**Remediation (decide one):** either make `DesignTokens.kt` the single source (Android theme reads it directly; iOS reads it via the already-linked shared framework — note the framework is currently built on every iOS compile but imported by zero Swift files), **or** delete it and accept two platform-native palettes. Recommendation: adopt it, since the KMP framework cost is already being paid.

---

## 4. Prioritized Remediation Order

| # | Work item | Findings | Effort | Notes |
|---|---|---|---|---|
| 1 | Rewrite `firestore.rules` with membership scoping, invite-code verification, `hasOnly()` lockdown; migrate `team_members` to deterministic doc IDs; update every client query to stay provable | SEC-01, SEC-02, SEC-05, SEC-06, SEC-07 | **Large** (rules + both clients + data migration) | Highest urgency. Test with the Rules emulator; deploy manually (no CI path exists). Breaks old app versions that query broadly — coordinate a forced-update or staged rollout. |
| 2 | Rewrite `storage.rules` with membership checks against the named `schedulo2` database | SEC-03 | Small | Can ship with #1. |
| 3 | iOS: tear down `TeamViewModel` listeners + clear state on sign-out/delete-account; add `deinit` | SEC-04 | Small | Independent of rules work; ship immediately. |
| 4 | Android backup rules or `allowBackup="false"`; move/remove `debug.keystore.base64`; restrict API keys; plan App Check | SEC-08, SEC-10 | Small | Hygiene batch. |
| 5 | Email verification gate for team features | SEC-09 | Small–Medium | Both platforms. |
| 6 | Android `Dimens` tokens + color-literal sweep; iOS typography scale; promote shared components into `Components.kt` | UI-01, UI-02, UI-03 | Medium (incremental) | Adopt-in-new-code immediately; migrate screens opportunistically. |
| 7 | Single design-token source (adopt `DesignTokens.kt` cross-platform or delete it) | UI-04 | Small–Medium | Decision first; mechanical after. |
| 8 | CI gates: run unit tests + a rules-emulator test suite + linters (detekt/ktlint/swiftlint) before deploy jobs | SEC-10 | Medium | Prevents regressions of all of the above. |

Items 1–3 close the actual data-exposure holes and should land before any feature work. Per `CLAUDE.md`, each code-change PR must also bump the fallback version code in `androidApp/build.gradle.kts` and apply changes to **both** platforms.

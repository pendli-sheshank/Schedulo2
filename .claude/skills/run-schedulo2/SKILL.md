---
name: run-schedulo2
description: Build, deploy, test, and troubleshoot Schedulo2 iOS (TestFlight) and Android (Play Store) CI/CD pipelines. Use when asked to run, deploy, fix CI, or debug build errors.
---

# Schedulo2 CI/CD Pipeline

Schedulo2 is a KMP (Kotlin Multiplatform) mobile app with iOS and Android targets.
It cannot be run locally in a headless container — it deploys via GitHub Actions to
TestFlight (iOS) and Play Store (Android). The "driver" is the CI/CD pipeline itself.

All paths below are relative to the repo root.

## Prerequisites

- GitHub Secrets must be configured (see Secrets section below)
- Xcode 26+ on macOS runner (iOS)
- JDK 21 + Android SDK on Ubuntu runner (Android)

## Build & Deploy

### iOS (TestFlight)
Workflow: `.github/workflows/deploy-appstore.yml`
Triggers on push to `main` or manual dispatch.

### Android (Play Store)
Workflow: `.github/workflows/deploy-playstore.yml`
Triggers on push to `main` or manual dispatch.

## Agent Path: Checking CI Status

```bash
# Check latest workflow runs (use MCP GitHub tools)
# mcp__github__actions_list for repo pendli-sheshank/schedulo2
# mcp__github__get_job_logs for specific run failures
```

## Version Bumping (CRITICAL)

**Every code change MUST bump the version number** to avoid rejection:

- **iOS**: Increment `CURRENT_PROJECT_VERSION` in `iosApp/project.yml` under `settings.base`
- **Android**: Uses `github.run_number + 100` automatically — no manual bump needed

```yaml
# iosApp/project.yml — bump this number every time
settings:
  base:
    CURRENT_PROJECT_VERSION: "6"  # was 5, increment each deploy
```

## Required GitHub Secrets

### iOS
| Secret | Description |
|--------|-------------|
| `IOS_TEAM_ID` | Apple Developer Team ID |
| `IOS_P12_CERTIFICATE_BASE64` | Distribution certificate (.p12) base64-encoded |
| `IOS_P12_PASSWORD` | Password for the .p12 certificate |
| `IOS_PROVISIONING_PROFILE_BASE64` | App Store provisioning profile, base64-encoded |
| `IOS_PROVISIONING_PROFILE_NAME` | Provisioning profile name (exact match) |
| `IOS_GOOGLE_SERVICE_PLIST_BASE64` | Firebase GoogleService-Info.plist, base64-encoded |
| `IOS_APPSTORE_API_KEY_ID` | App Store Connect API Key ID |
| `IOS_APPSTORE_API_KEY_P8_BASE64` | API Key .p8 file, base64-encoded |
| `IOS_APPSTORE_ISSUER_ID` | App Store Connect Issuer ID |

### Android
| Secret | Description |
|--------|-------------|
| `KEYSTORE_BASE64` | Upload keystore (.jks), base64-encoded |
| `STORE_PASSWORD` | Keystore password |
| `KEY_PASSWORD` | Key password |
| `PLAY_SERVICE_ACCOUNT_JSON` | Google Play service account JSON |

## Gotchas — Battle-Tested Lessons from PRs #31-40

### 1. NEVER use `echo` for base64 decoding secrets
`echo "$VAR" | base64 -d` adds a trailing newline that corrupts binary files (.p8 keys, certificates).
**Always use:** `printf '%s' "$VAR" | base64 -d`

### 2. NEVER skip iOS platform download based on SDK listing
`xcodebuild -showsdks` listing `iphoneos` does NOT mean the platform is fully installed.
`grep -q iphoneos` also matches `iphonesimulator18.2` — false positive.
**Always download:** `xcodebuild -downloadPlatform iOS` with retries. Never skip it.

### 3. ALWAYS specify `-destination generic/platform=iOS` in archive
Without it, `xcodebuild archive` fails with "Found no destinations for the scheme."
```yaml
xcodebuild archive \
  -destination generic/platform=iOS \
  ...
```

### 4. Use `app-store` not `app-store-connect` in ExportOptions.plist
`app-store-connect` tries to upload during export, which requires an authenticated
Apple ID account — impossible on CI. Use `app-store` (just creates the IPA) and
upload separately with `xcrun altool`.

### 5. Upload to TestFlight with `xcrun altool`, not `xcodebuild -exportArchive`
```yaml
xcrun altool --upload-app \
  --type ios \
  --file "$RUNNER_TEMP/export/Schedulo2.ipa" \
  --apiKey "$API_KEY_ID" \
  --apiIssuer "$ISSUER_ID" \
  --apiKeyPath ~/private_keys
```
The `--apiKeyPath` flag is required — altool won't find the key automatically.

### 6. App Store Connect requires ALL icon sizes
Missing even one icon size causes validation failure. Required sizes:
1024, 180, 167, 152, 120, 87, 80, 76, 60, 58, 40, 29, 20 pixels.
These live in `iosApp/Schedulo2/Assets.xcassets/AppIcon.appiconset/`.

### 7. Info.plist must have `CFBundleIconName` and iPad orientations
```xml
<key>CFBundleIconName</key>
<string>AppIcon</string>
<key>UISupportedInterfaceOrientations~ipad</key>
<array>
  <string>UIInterfaceOrientationPortrait</string>
  <string>UIInterfaceOrientationPortraitUpsideDown</string>
  <string>UIInterfaceOrientationLandscapeLeft</string>
  <string>UIInterfaceOrientationLandscapeRight</string>
</array>
```

### 8. Use latest Xcode (26+) — Apple rejects old SDK builds
As of 2026, Apple requires iOS 26 SDK. Set `XCODE_VERSION: "26.0"` and
`runs-on: macos-latest`. Include fallback logic for version selection.

### 9. iOS platform download needs retries with backoff
Network flakiness on GitHub runners is common. Use 3 retries with 10s/20s/30s delays:
```bash
for i in 1 2 3; do
  xcodebuild -downloadPlatform iOS && break
  echo "Download attempt $i failed, retrying in $((i * 10))s..."
  sleep $((i * 10))
done
```

### 10. Android version codes must auto-increment
Use `github.run_number + offset` to guarantee uniqueness:
```yaml
echo "VERSION_CODE=$(( ${{ github.run_number }} + 100 ))" >> $GITHUB_ENV
```

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| `Failed to generate JWT token: file isn't in correct format` | .p8 key corrupted by `echo` newline | Use `printf '%s'` for base64 decode |
| `exportArchive Failed to Use Accounts` (exit 70) | ExportOptions uses `app-store-connect` method | Change to `app-store`, upload separately with altool |
| `Found no destinations for the scheme` | Missing `-destination` flag | Add `-destination generic/platform=iOS` |
| `iOS 18.2 is not installed` after showsdks lists it | Platform not fully installed; skip logic matched simulator | Always run `xcodebuild -downloadPlatform iOS` |
| `ITMS-90022: Missing required icon` | Asset catalog missing icon sizes | Generate all 13 required icon sizes |
| `ITMS-90474: Invalid Bundle` (no CFBundleIconName) | Info.plist missing icon reference | Add `CFBundleIconName` = `AppIcon` |
| `ITMS-90102: Missing orientation` | iPad missing UpsideDown orientation | Add all 4 iPad orientations to Info.plist |
| `Version code X has already been used` | Static version code | Auto-increment with `run_number` |
| `Unable to connect to simulator` during platform download | Transient runner issue | Retry with backoff, never skip |

## Key Files

| File | Purpose |
|------|---------|
| `.github/workflows/deploy-appstore.yml` | iOS TestFlight CI/CD |
| `.github/workflows/deploy-playstore.yml` | Android Play Store CI/CD |
| `iosApp/project.yml` | XcodeGen project config (version numbers here) |
| `iosApp/ExportOptions.plist` | IPA export settings |
| `iosApp/Schedulo2/Info.plist` | iOS app metadata |
| `iosApp/Schedulo2/Assets.xcassets/` | App icons and colors |
| `shared/` | KMP shared module |

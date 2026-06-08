# Schedulo iOS App

## Prerequisites

- macOS with Xcode 15.0+
- CocoaPods or Swift Package Manager
- JDK 21 (for building the shared KMP framework)

## Setup

### 1. Firebase Configuration

1. Go to [Firebase Console](https://console.firebase.google.com)
2. Open project `gen-lang-client-0769292643`
3. Add an iOS app with bundle ID `com.schedulo2.ios`
4. Download `GoogleService-Info.plist`
5. Place it in `iosApp/Schedulo2/GoogleService-Info.plist`

### 2. Build Shared KMP Framework

From the project root:
```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

### 3. Xcode Project Setup

1. Open Xcode and create a new iOS App project
2. Set product name to "Schedulo2"
3. Set bundle identifier to "com.schedulo2.ios"
4. Set deployment target to iOS 16.0
5. Add all Swift files from `iosApp/Schedulo2/`
6. Add Firebase iOS SDK via Swift Package Manager:
   - URL: `https://github.com/firebase/firebase-ios-sdk`
   - Products: FirebaseAuth, FirebaseFirestore, FirebaseCrashlytics
7. Link the Shared.framework from `shared/build/bin/`
8. Add a Run Script build phase to build the shared framework:
   ```bash
   cd "$SRCROOT/.."
   ./gradlew :shared:embedAndSignAppleFrameworkForXcode
   ```

### 4. Run

Build and run on iOS Simulator or device from Xcode.

## Architecture

- **Shared Module** (`shared/`): KMP module with data models and business logic
- **iOS App** (`iosApp/`): SwiftUI views, ViewModels, Firebase services
- **Android App** (`androidApp/`): Jetpack Compose UI, Android-specific code

Both apps share the same Firebase backend (Firestore) and user accounts.

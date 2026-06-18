#!/usr/bin/env bash
# Validates Schedulo2 CI/CD configuration files before pushing.
# Run from repo root: bash .claude/skills/run-schedulo2/smoke.sh
set -euo pipefail

PASS=0
FAIL=0
WARN=0

check() {
  if eval "$2" >/dev/null 2>&1; then
    echo "  ✓ $1"
    PASS=$((PASS + 1))
  else
    echo "  ✗ $1"
    FAIL=$((FAIL + 1))
  fi
}

warn() {
  if eval "$2" >/dev/null 2>&1; then
    echo "  ✓ $1"
    PASS=$((PASS + 1))
  else
    echo "  ⚠ $1"
    WARN=$((WARN + 1))
  fi
}

echo "=== iOS CI/CD Configuration ==="

check "deploy-appstore.yml exists" "[ -f .github/workflows/deploy-appstore.yml ]"
check "ExportOptions.plist exists" "[ -f iosApp/ExportOptions.plist ]"
check "project.yml exists" "[ -f iosApp/project.yml ]"
check "Info.plist exists" "[ -f iosApp/Schedulo2/Info.plist ]"

check "ExportOptions uses app-store (not app-store-connect)" \
  "grep -q '<string>app-store</string>' iosApp/ExportOptions.plist && ! grep -q 'app-store-connect' iosApp/ExportOptions.plist"

check "Workflow uses printf for base64 decode (not echo)" \
  "grep -q 'printf' .github/workflows/deploy-appstore.yml"

check "Workflow uses --apiKeyPath" \
  "grep -q 'apiKeyPath' .github/workflows/deploy-appstore.yml"

check "Workflow uses xcrun altool for upload" \
  "grep -q 'xcrun altool --upload-app' .github/workflows/deploy-appstore.yml"

check "Workflow has -destination generic/platform=iOS" \
  "grep -q 'destination generic/platform=iOS' .github/workflows/deploy-appstore.yml"

check "Workflow always downloads iOS platform (no skip logic)" \
  "grep -q 'downloadPlatform iOS' .github/workflows/deploy-appstore.yml"

check "Info.plist has CFBundleIconName" \
  "grep -q 'CFBundleIconName' iosApp/Schedulo2/Info.plist"

check "Info.plist has iPad orientations (including UpsideDown)" \
  "grep -q 'UIInterfaceOrientationPortraitUpsideDown' iosApp/Schedulo2/Info.plist"

check "AppIcon asset catalog exists" \
  "[ -f iosApp/Schedulo2/Assets.xcassets/AppIcon.appiconset/Contents.json ]"

check "1024x1024 app icon exists" \
  "[ -f iosApp/Schedulo2/Assets.xcassets/AppIcon.appiconset/icon_1024x1024.png ]"

VERSION=$(grep 'CURRENT_PROJECT_VERSION' iosApp/project.yml | head -1 | sed 's/.*"\(.*\)".*/\1/')
echo ""
echo "  Current iOS version: $VERSION"
warn "Version > 1 (has been bumped)" "[ \"$VERSION\" -gt 1 ] 2>/dev/null"

echo ""
echo "=== Android CI/CD Configuration ==="

check "deploy-playstore.yml exists" "[ -f .github/workflows/deploy-playstore.yml ]"
check "Android uses run_number for version code" \
  "grep -q 'run_number' .github/workflows/deploy-playstore.yml"

echo ""
echo "=== Results ==="
echo "  Passed: $PASS  |  Failed: $FAIL  |  Warnings: $WARN"

if [ "$FAIL" -gt 0 ]; then
  echo ""
  echo "  ✗ CONFIGURATION HAS ISSUES — fix before pushing"
  exit 1
else
  echo ""
  echo "  ✓ Configuration looks good"
  exit 0
fi

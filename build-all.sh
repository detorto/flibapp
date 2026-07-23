#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
CLIENT="$ROOT/client"
DIST="$ROOT/dist"
VERSION="1.0.0"
OS="$(uname -s)"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log()  { echo -e "${GREEN}[BUILD]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
fail() { echo -e "${RED}[FAIL]${NC} $*"; }

rm -rf "$DIST"
mkdir -p "$DIST/android" "$DIST/desktop" "$DIST/ios"

# ─── Android (signed release + debug) ────────────────────────────
build_android() {
    log "Building Android..."
    cd "$CLIENT"

    log "  → debug APK"
    ./gradlew :androidApp:assembleDebug --no-daemon -q
    cp androidApp/build/outputs/apk/debug/androidApp-debug.apk \
       "$DIST/android/FlibApp-${VERSION}-debug.apk"
    log "  ✓ debug APK"

    log "  → release APK (signed)"
    if ./gradlew :androidApp:assembleRelease --no-daemon -q 2>/dev/null; then
        cp androidApp/build/outputs/apk/release/androidApp-release.apk \
           "$DIST/android/FlibApp-${VERSION}-release.apk"
        log "  ✓ release APK (signed)"
    else
        warn "  release APK failed (check keystore.properties)"
    fi
}

# ─── macOS (native .dmg + .app) ─────────────────────────────────
build_macos() {
    if [[ "$OS" != "Darwin" ]]; then
        warn "macOS builds require macOS — skipping."
        return
    fi

    cd "$CLIENT"

    log "Building macOS .dmg..."
    ./gradlew :desktopApp:packageDmg --no-daemon -q
    find desktopApp/build/compose/binaries/main/dmg -name "*.dmg" -exec cp {} "$DIST/desktop/" \;
    log "  ✓ macOS .dmg"

    if [ -d "desktopApp/build/compose/binaries/main/app" ]; then
        cd desktopApp/build/compose/binaries/main/app
        zip -r -q "$DIST/desktop/FlibApp-${VERSION}-macOS.app.zip" *.app
        cd "$CLIENT"
        log "  ✓ macOS .app (zipped)"
    fi
}

# ─── Linux (native via Docker, x86_64) ──────────────────────────
build_linux() {
    if ! command -v docker &>/dev/null; then
        fail "Docker required for Linux build — skipping."
        return
    fi

    if ! docker info &>/dev/null 2>&1; then
        fail "Docker daemon not running — skipping Linux build."
        return
    fi

    log "Building Linux x86_64 via Docker (this takes a while)..."
    cd "$CLIENT"

    docker build --platform linux/amd64 -f Dockerfile.linux -t flibapp-linux . 2>&1 | \
        while IFS= read -r line; do
            case "$line" in
                *"BUILD SUCCESSFUL"*) log "  ✓ Gradle build succeeded" ;;
                *"BUILD FAILED"*)     fail "  ✗ Gradle build failed" ;;
                *"Step "*) echo "  $line" ;;
            esac
        done

    local container_id
    container_id=$(docker create --platform linux/amd64 flibapp-linux)

    docker cp "$container_id:/build/desktopApp/build/compose/binaries/main/app/" "/tmp/flibapp-linux-app" 2>/dev/null && {
        cd /tmp/flibapp-linux-app
        tar czf "$DIST/desktop/FlibApp-${VERSION}-linux-x86_64.tar.gz" .
        cd "$CLIENT"
        log "  ✓ Linux x86_64 distributable (tar.gz)"
    } || warn "  Linux distributable not found"

    docker cp "$container_id:/build/desktopApp/build/compose/binaries/main/deb/" "/tmp/flibapp-linux-deb" 2>/dev/null && {
        find /tmp/flibapp-linux-deb -name "*.deb" -exec cp {} "$DIST/desktop/" \;
        log "  ✓ Linux .deb"
    } || warn "  Linux .deb not found"

    docker rm "$container_id" >/dev/null
    rm -rf /tmp/flibapp-linux-app /tmp/flibapp-linux-deb
}

# ─── Windows (native .exe — requires Windows or GH Actions) ─────
build_windows() {
    case "$OS" in
        MINGW*|MSYS*|CYGWIN*)
            cd "$CLIENT"
            log "Building Windows .exe..."
            ./gradlew :desktopApp:packageExe --no-daemon -q
            find desktopApp/build/compose/binaries/main/exe -name "*.exe" -exec cp {} "$DIST/desktop/" \;
            log "  ✓ Windows .exe"
            ;;
        *)
            warn "Windows .exe can only be built on Windows."
            warn "  Use GitHub Actions with windows-latest runner, or build on a Windows machine."
            warn "  The build command is: ./gradlew :desktopApp:packageExe"
            ;;
    esac
}

# ─── iOS (framework — needs Xcode project for .ipa) ─────────────
build_ios() {
    if [[ "$OS" != "Darwin" ]]; then
        warn "iOS builds require macOS — skipping."
        return
    fi

    log "Building iOS framework (arm64)..."
    cd "$CLIENT"

    if ./gradlew :composeApp:linkReleaseFrameworkIosArm64 --no-daemon -q 2>/dev/null; then
        local fw="composeApp/build/bin/iosArm64/releaseFramework"
        if [ -d "$fw/ComposeApp.framework" ]; then
            cd "$fw"
            zip -r -q "$DIST/ios/ComposeApp-iosArm64.framework.zip" ComposeApp.framework
            cd "$CLIENT"
            log "  ✓ iOS arm64 framework"
        fi
    else
        warn "  iOS arm64 framework build failed"
    fi

    if ./gradlew :composeApp:linkReleaseFrameworkIosSimulatorArm64 --no-daemon -q 2>/dev/null; then
        local fw="composeApp/build/bin/iosSimulatorArm64/releaseFramework"
        if [ -d "$fw/ComposeApp.framework" ]; then
            cd "$fw"
            zip -r -q "$DIST/ios/ComposeApp-iosSimulatorArm64.framework.zip" ComposeApp.framework
            cd "$CLIENT"
            log "  ✓ iOS simulator arm64 framework"
        fi
    else
        warn "  iOS simulator framework build failed"
    fi

    echo ""
    warn "iOS NOTE: .ipa requires an Xcode project wrapping ComposeApp.framework."
    warn "  Create iosApp/, embed the framework, then: xcodebuild archive + exportArchive"
}

# ─── Main ────────────────────────────────────────────────────────
echo ""
echo "╔══════════════════════════════════════╗"
echo "║     FlibApp Client Build v${VERSION}      ║"
echo "╚══════════════════════════════════════╝"
echo ""

TARGETS="${1:-all}"

case "$TARGETS" in
    android) build_android ;;
    macos)   build_macos ;;
    linux)   build_linux ;;
    windows) build_windows ;;
    ios)     build_ios ;;
    all)
        build_android
        build_macos
        build_linux
        build_windows
        build_ios
        ;;
    *)
        echo "Usage: $0 [all|android|macos|linux|windows|ios]"
        exit 1
        ;;
esac

echo ""
log "═══════════════════════════════════════"
log "Artifacts:"
echo ""
find "$DIST" -type f | sort | while read -r f; do
    size=$(du -h "$f" | cut -f1 | xargs)
    echo "  $size  ${f#$ROOT/}"
done
echo ""
log "All in: $DIST"

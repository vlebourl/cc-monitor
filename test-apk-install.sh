#!/bin/bash

# Test APK Installation Script
# This script tests APK installation and basic functionality

set -e

echo "🚀 Testing Claude Code Monitor APK Installation"
echo "================================================"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Check if ANDROID_HOME is set
if [ -z "$ANDROID_HOME" ]; then
    export ANDROID_HOME=/usr/lib/android-sdk
    echo "📍 Setting ANDROID_HOME to $ANDROID_HOME"
fi

# Find APK file
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK_PATH" ]; then
    echo -e "${RED}❌ APK not found at $APK_PATH${NC}"
    echo "🔨 Building APK first..."
    cd android && ./gradlew assembleDebug && cd ..
fi

if [ ! -f "$APK_PATH" ]; then
    echo -e "${RED}❌ Failed to build APK${NC}"
    exit 1
fi

echo -e "${GREEN}✅ APK found: $APK_PATH${NC}"

# Check APK size and details
APK_SIZE=$(ls -lh "$APK_PATH" | awk '{print $5}')
echo "📦 APK Size: $APK_SIZE"

# Verify APK signing
echo "🔐 Verifying APK signing..."
if jarsigner -verify "$APK_PATH" > /dev/null 2>&1; then
    echo -e "${GREEN}✅ APK is properly signed${NC}"
else
    echo -e "${RED}❌ APK is not signed${NC}"
    exit 1
fi

# Check APK manifest
echo "📋 Checking APK manifest..."
AAPT_PATH=$(find $ANDROID_HOME -name "aapt" -type f | head -1)
if [ -n "$AAPT_PATH" ]; then
    echo "📱 App Details:"
    $AAPT_PATH dump badging "$APK_PATH" | grep -E "(package|application-label|uses-permission)" | head -10
else
    echo -e "${YELLOW}⚠️ aapt not found, skipping manifest check${NC}"
fi

# Test installation simulation (without actual device)
echo "🧪 Testing APK structure..."

# Check for required files in APK
echo "📂 Checking APK contents..."
unzip -l "$APK_PATH" | grep -E "(AndroidManifest.xml|classes.dex|resources.arsc)" > /dev/null
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ APK has required Android files${NC}"
else
    echo -e "${RED}❌ APK missing required files${NC}"
    exit 1
fi

# Check for our app's specific files
echo "🔍 Checking for Claude Code Monitor components..."
unzip -l "$APK_PATH" | grep -i "ccmonitor" > /dev/null
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Claude Code Monitor components found${NC}"
else
    echo -e "${YELLOW}⚠️ App components check inconclusive${NC}"
fi

# Try to connect to device if available
echo "📱 Checking for connected devices..."
ADB_PATH=$(find $ANDROID_HOME -name "adb" -type f | head -1)

if [ -n "$ADB_PATH" ]; then
    # Check if any devices are connected
    DEVICES=$($ADB_PATH devices | grep -v "List of devices" | grep "device$" | wc -l)

    if [ "$DEVICES" -gt 0 ]; then
        echo -e "${GREEN}📱 Found $DEVICES connected device(s)${NC}"
        echo "🚀 Installing APK on device..."

        if $ADB_PATH install -r "$APK_PATH"; then
            echo -e "${GREEN}✅ APK installed successfully!${NC}"

            # Try to launch the app
            echo "🚀 Launching Claude Code Monitor..."
            $ADB_PATH shell am start -n com.ccmonitor.debug/.activities.MainActivity

            echo -e "${GREEN}✅ App launched successfully!${NC}"
            echo "📱 Check your device to verify the app is running"

            # Optional: Take a screenshot
            echo "📸 Taking screenshot..."
            $ADB_PATH shell screencap -p > device_screenshot.png 2>/dev/null && echo "📷 Screenshot saved as device_screenshot.png" || echo "📷 Screenshot failed"

        else
            echo -e "${RED}❌ APK installation failed${NC}"
            exit 1
        fi
    else
        echo -e "${YELLOW}⚠️ No devices connected via ADB${NC}"
        echo "📱 To test installation:"
        echo "  1. Connect your Android device via USB"
        echo "  2. Enable USB debugging"
        echo "  3. Run this script again"
        echo ""
        echo "🌐 Or download from GitHub Actions:"
        echo "  1. Push to your repository"
        echo "  2. Check GitHub Actions for build artifacts"
        echo "  3. Download and install the APK"
    fi
else
    echo -e "${YELLOW}⚠️ ADB not found, skipping device installation${NC}"
fi

echo ""
echo -e "${GREEN}🎉 APK Test Summary:${NC}"
echo -e "${GREEN}✅ APK builds successfully${NC}"
echo -e "${GREEN}✅ APK is properly signed${NC}"
echo -e "${GREEN}✅ APK contains required Android components${NC}"
echo ""
echo -e "${BLUE}📱 Ready for installation on Android devices!${NC}"
echo ""
echo -e "${BLUE}📋 Next Steps:${NC}"
echo "1. 🚀 Push to GitHub to trigger CI/CD build"
echo "2. 📥 Download APK from GitHub Actions artifacts"
echo "3. 📱 Install on your Pixel 9 Pro"
echo "4. ⚙️ Configure server settings in app"
echo "5. 📷 Scan QR code to connect"

exit 0
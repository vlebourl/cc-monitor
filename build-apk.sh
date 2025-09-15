#!/bin/bash

# Claude Code Monitor - Android APK Build Script
# This script builds the Android APK for installation on phones

set -e  # Exit on any error

echo "🚀 Building Claude Code Monitor Android APK..."
echo ""

# Check if we're in the right directory
if [ ! -d "android" ]; then
    echo "❌ Error: Please run this script from the cc-monitor root directory"
    echo "   Current directory: $(pwd)"
    echo "   Expected: cc-monitor/"
    exit 1
fi

# Change to android directory
cd android

echo "📱 Building Android APK..."
echo ""

# Clean previous builds
echo "🧹 Cleaning previous builds..."
./gradlew clean

echo ""
echo "🔨 Building debug APK (for testing)..."
./gradlew assembleDebug

echo ""
echo "🔨 Building release APK (optimized)..."
./gradlew assembleRelease

echo ""
echo "✅ Build completed successfully!"
echo ""
echo "📦 APK Files Generated:"
echo ""

# Check if debug APK exists
if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
    DEBUG_SIZE=$(du -h app/build/outputs/apk/debug/app-debug.apk | cut -f1)
    echo "  🐛 Debug APK:   app/build/outputs/apk/debug/app-debug.apk ($DEBUG_SIZE)"
else
    echo "  ❌ Debug APK:   Build failed"
fi

# Check if release APK exists
if [ -f "app/build/outputs/apk/release/app-release-unsigned.apk" ]; then
    RELEASE_SIZE=$(du -h app/build/outputs/apk/release/app-release-unsigned.apk | cut -f1)
    echo "  🚀 Release APK: app/build/outputs/apk/release/app-release-unsigned.apk ($RELEASE_SIZE)"
else
    echo "  ❌ Release APK: Build failed"
fi

echo ""
echo "📋 Installation Instructions:"
echo ""
echo "1. Copy the APK file to your Android phone"
echo "2. Enable 'Install from Unknown Sources' in Android settings"
echo "3. Tap the APK file to install"
echo "4. Open 'Claude Code Monitor' app"
echo "5. Scan QR code from your Claude Code server"
echo ""
echo "💡 Tip: Use the Release APK for better performance and smaller size"
echo ""
echo "🎉 Ready to monitor Claude Code sessions on your phone!"
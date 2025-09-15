#!/bin/bash

# Android SDK Setup Script for Ubuntu
# Run with: sudo bash setup-android-sdk.sh

set -e

echo "🚀 Setting up Android SDK for Claude Code Monitor..."
echo ""

# Check if running as root
if [ "$EUID" -ne 0 ]; then
    echo "❌ Please run as root (use sudo)"
    exit 1
fi

# Variables
ANDROID_SDK_ROOT="/usr/lib/android-sdk"
CMDTOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip"
TEMP_DIR="/tmp/android-sdk-setup"

echo "📁 Creating Android SDK directory..."
mkdir -p "$ANDROID_SDK_ROOT"
mkdir -p "$TEMP_DIR"
cd "$TEMP_DIR"

echo "📥 Downloading Android command line tools..."
curl -L "$CMDTOOLS_URL" -o cmdtools.zip

echo "📦 Extracting command line tools..."
unzip -q cmdtools.zip
rm cmdtools.zip

echo "📋 Installing command line tools..."
mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"
mv cmdline-tools "$ANDROID_SDK_ROOT/cmdline-tools/latest"

echo "🔑 Setting permissions..."
chmod +x "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
chmod +x "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/avdmanager"

# Set environment for this session
export ANDROID_SDK_ROOT="$ANDROID_SDK_ROOT"
export ANDROID_HOME="$ANDROID_SDK_ROOT"

echo "📄 Accepting Android SDK licenses..."
yes | "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_SDK_ROOT" --licenses

echo "📦 Installing required SDK components..."
"$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_SDK_ROOT" \
    "platform-tools" \
    "platforms;android-34" \
    "build-tools;34.0.0" \
    "build-tools;33.0.1"

echo "🔧 Setting up global environment..."
# Add to system environment
echo "# Android SDK Environment" >> /etc/environment
echo "ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT" >> /etc/environment
echo "ANDROID_HOME=$ANDROID_SDK_ROOT" >> /etc/environment
echo "PATH=\$PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools" >> /etc/environment

# Create profile script for all users
cat > /etc/profile.d/android-sdk.sh << EOF
export ANDROID_SDK_ROOT="$ANDROID_SDK_ROOT"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export PATH="\$PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools"
EOF

chmod +x /etc/profile.d/android-sdk.sh

echo "🔒 Setting permissions for all users..."
chmod -R 755 "$ANDROID_SDK_ROOT"

echo "🧹 Cleaning up temporary files..."
rm -rf "$TEMP_DIR"

echo ""
echo "✅ Android SDK setup complete!"
echo ""
echo "📋 Summary:"
echo "   SDK Location: $ANDROID_SDK_ROOT"
echo "   Components installed:"
echo "     - Command line tools"
echo "     - Platform tools (adb, etc.)"
echo "     - Android 34 Platform"
echo "     - Build tools 34.0.0 & 33.0.1"
echo "     - All licenses accepted"
echo ""
echo "🔄 To use immediately, run:"
echo "   source /etc/profile.d/android-sdk.sh"
echo ""
echo "🚀 Now you can build the Android APK:"
echo "   cd ~/Downloads/cc-monitor"
echo "   ./build-apk.sh"
echo ""
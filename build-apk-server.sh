#!/bin/bash

# Claude Code Monitor - Server APK Build Script
# For building on Ubuntu servers without Android Studio

set -e

echo "🚀 Building Claude Code Monitor Android APK on Ubuntu Server..."
echo ""

# Check if we're in the right directory
if [ ! -d "android" ]; then
    echo "❌ Error: Please run this script from the cc-monitor root directory"
    exit 1
fi

# Set JAVA_HOME if not set
if [ -z "$JAVA_HOME" ]; then
    if [ -d "/usr/lib/jvm/java-17-openjdk-amd64" ]; then
        export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
        echo "✅ JAVA_HOME set to: $JAVA_HOME"
    elif [ -d "/usr/lib/jvm/java-11-openjdk-amd64" ]; then
        export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
        echo "✅ JAVA_HOME set to: $JAVA_HOME"
    else
        echo "❌ Error: Java not found. Please install OpenJDK:"
        echo "   sudo apt update"
        echo "   sudo apt install -y openjdk-17-jdk"
        exit 1
    fi
fi

# Check for Android SDK
ANDROID_HOME_CANDIDATES=(
    "$HOME/Android/Sdk"
    "$HOME/android-sdk"
    "/opt/android-sdk"
    "/usr/lib/android-sdk"
)

for candidate in "${ANDROID_HOME_CANDIDATES[@]}"; do
    if [ -d "$candidate" ]; then
        export ANDROID_HOME="$candidate"
        export ANDROID_SDK_ROOT="$candidate"
        echo "✅ ANDROID_HOME set to: $ANDROID_HOME"
        break
    fi
done

if [ -z "$ANDROID_HOME" ]; then
    echo "⚠️  Android SDK not found. Installing Android SDK..."

    # Create SDK directory
    mkdir -p "$HOME/android-sdk"
    cd "$HOME/android-sdk"

    # Download command line tools
    echo "📥 Downloading Android SDK command line tools..."
    curl -L https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip -o cmdtools.zip
    unzip -q cmdtools.zip
    rm cmdtools.zip

    # Set up environment
    export ANDROID_HOME="$HOME/android-sdk"
    export ANDROID_SDK_ROOT="$HOME/android-sdk"
    export PATH="$PATH:$ANDROID_HOME/cmdline-tools/bin:$ANDROID_HOME/platform-tools"

    # Accept licenses and install required packages
    echo "📦 Installing Android SDK packages..."
    yes | ./cmdline-tools/bin/sdkmanager --sdk_root="$ANDROID_HOME" --licenses
    ./cmdline-tools/bin/sdkmanager --sdk_root="$ANDROID_HOME" "platform-tools" "platforms;android-34" "build-tools;34.0.0"

    cd - > /dev/null
    echo "✅ Android SDK installed successfully"
fi

# Add Android tools to PATH
export PATH="$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/bin"

echo ""
echo "🔧 Environment Setup Complete:"
echo "   JAVA_HOME: $JAVA_HOME"
echo "   ANDROID_HOME: $ANDROID_HOME"
echo ""

# Change to android directory
cd android

echo "📱 Building Android APK..."

# Clean and build
echo "🧹 Cleaning previous builds..."
./gradlew clean

echo ""
echo "🔨 Building APK..."
./gradlew assembleDebug

echo ""
echo "✅ Build completed successfully!"
echo ""

# Show results
if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
    APK_SIZE=$(du -h app/build/outputs/apk/debug/app-debug.apk | cut -f1)
    APK_PATH=$(realpath app/build/outputs/apk/debug/app-debug.apk)
    echo "📦 APK Generated:"
    echo "   File: $APK_PATH"
    echo "   Size: $APK_SIZE"
    echo ""
    echo "📋 To install on your phone:"
    echo "1. Copy the APK to your phone"
    echo "2. Enable 'Install from Unknown Sources'"
    echo "3. Tap the APK to install"
    echo ""
    echo "💡 You can copy the APK with:"
    echo "   scp '$APK_PATH' your-phone:/sdcard/Download/"
else
    echo "❌ Build failed - APK not generated"
    exit 1
fi

echo ""
echo "🎉 Android APK ready for installation!"
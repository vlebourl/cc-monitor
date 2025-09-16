#!/bin/bash

# Android Emulator Setup Script for Pixel 9 Pro Testing
# Run this with sudo to fix permissions and install emulator components

set -e

echo "🚀 Setting up Android Emulator for Pixel 9 Pro APK Testing"
echo "========================================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Set Android SDK paths
export ANDROID_HOME=/usr/lib/android-sdk
export ANDROID_SDK_ROOT=/usr/lib/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator

echo "📍 Android SDK Location: $ANDROID_HOME"

# Fix SDK directory permissions
echo "🔧 Fixing Android SDK permissions..."
sudo chown -R $USER:$USER $ANDROID_HOME 2>/dev/null || true
sudo chmod -R 755 $ANDROID_HOME 2>/dev/null || true

# Create and fix .android directory permissions
echo "🔧 Setting up .android directory..."
mkdir -p ~/.android
sudo chown -R $USER:$USER ~/.android 2>/dev/null || true

# Accept all SDK licenses
echo "📜 Accepting Android SDK licenses..."
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses 2>/dev/null || true

# Install emulator if not present
echo "📦 Installing Android Emulator..."
echo 'y' | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "emulator"

# Install Android 14 system image for Pixel 9 Pro compatibility
echo "📱 Installing Android 14 (API 34) system image with Google Play..."
echo 'y' | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "system-images;android-34-ext12;google_apis_playstore;x86_64"

# Verify installation
echo "✅ Verifying installation..."
echo "Installed packages:"
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --list_installed

# Find emulator executable
EMULATOR_PATH=$(find $ANDROID_HOME -name "emulator" -type f | head -1)
if [ -n "$EMULATOR_PATH" ]; then
    echo -e "${GREEN}✅ Emulator found at: $EMULATOR_PATH${NC}"

    # Create AVD
    echo "📱 Creating Pixel 9 Pro AVD..."
    echo 'no' | $ANDROID_HOME/cmdline-tools/latest/bin/avdmanager create avd \
        -n "Pixel_9_Pro_API_34" \
        -k "system-images;android-34-ext12;google_apis_playstore;x86_64" \
        -d "pixel_9_pro" \
        --force 2>/dev/null || {

        # If pixel_9_pro device profile doesn't exist, use a similar one
        echo "📱 Creating AVD with default device profile..."
        echo 'no' | $ANDROID_HOME/cmdline-tools/latest/bin/avdmanager create avd \
            -n "Pixel_9_Pro_API_34" \
            -k "system-images;android-34-ext12;google_apis_playstore;x86_64" \
            --force
    }

    echo -e "${GREEN}✅ AVD 'Pixel_9_Pro_API_34' created successfully${NC}"

    # Set executable permissions on emulator
    sudo chmod +x "$EMULATOR_PATH" 2>/dev/null || true

    echo ""
    echo -e "${GREEN}🎉 Android Emulator Setup Complete!${NC}"
    echo ""
    echo -e "${BLUE}📋 Next Steps:${NC}"
    echo "1. Run: $EMULATOR_PATH -avd Pixel_9_Pro_API_34 -no-audio -no-window &"
    echo "2. Wait for emulator to boot (2-3 minutes)"
    echo "3. Test APK installation with adb install app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    echo -e "${BLUE}🔧 Available AVDs:${NC}"
    $ANDROID_HOME/cmdline-tools/latest/bin/avdmanager list avd

else
    echo -e "${RED}❌ Emulator installation failed${NC}"
    exit 1
fi

# Create environment setup for user
echo ""
echo "📝 Creating environment setup file..."
cat > ~/.android_emulator_env << 'EOF'
# Android Emulator Environment
export ANDROID_HOME=/usr/lib/android-sdk
export ANDROID_SDK_ROOT=/usr/lib/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator

# Aliases for convenience
alias start-emulator='$ANDROID_HOME/emulator/emulator -avd Pixel_9_Pro_API_34 -no-audio &'
alias list-devices='adb devices'
alias install-apk='adb install -r app/build/outputs/apk/debug/app-debug.apk'
EOF

echo -e "${GREEN}✅ Environment file created at ~/.android_emulator_env${NC}"
echo "Source it with: source ~/.android_emulator_env"

echo ""
echo -e "${GREEN}🚀 Ready to test Claude Code Monitor APK on Android 14!${NC}"
# Building Claude Code Monitor Android APK

This guide explains how to build the Android APK for the Claude Code Monitor app that you can install directly on your phone.

## Prerequisites

### Required Software
1. **Android Studio** (Latest version - Arctic Fox or later)
   - Download from: https://developer.android.com/studio
   - Includes Android SDK and build tools

2. **Java Development Kit (JDK) 17 or 11**
   - Android Studio usually includes this
   - Or download from: https://adoptium.net/

3. **Git** (to clone the repository)

### Hardware Requirements
- **RAM**: 8GB minimum, 16GB recommended
- **Storage**: 8GB free space for Android Studio + SDK
- **OS**: Windows 10+, macOS 10.14+, or Ubuntu 18.04+

## Step-by-Step Build Instructions

### 1. Clone the Repository
```bash
git clone git@github.com:vlebourl/cc-monitor.git
cd cc-monitor
```

### 2. Open in Android Studio
1. Launch Android Studio
2. Click "Open an Existing Project"
3. Navigate to `cc-monitor/android/` folder
4. Click "OK" to open the project

### 3. Configure Project
When Android Studio opens the project:

1. **Accept SDK licenses** if prompted
2. **Sync Gradle** (should happen automatically)
   - Look for "Sync Now" banner at the top
   - Wait for sync to complete (may take 5-10 minutes first time)

3. **Install missing SDKs** if prompted:
   - Click "Install missing platform(s)"
   - Accept licenses and download

### 4. Build the APK

#### Option A: Using Android Studio (Recommended)
1. In Android Studio menu: **Build → Build Bundle(s) / APK(s) → Build APK(s)**
2. Wait for build to complete (2-10 minutes depending on your machine)
3. When complete, click "locate" in the notification popup
4. APK will be in: `android/app/build/outputs/apk/debug/app-debug.apk`

#### Option B: Using Command Line
```bash
cd android
./gradlew assembleDebug
```
APK location: `android/app/build/outputs/apk/debug/app-debug.apk`

### 5. Build Release APK (Optimized)
For a smaller, optimized APK:

#### Option A: Android Studio
1. **Build → Build Bundle(s) / APK(s) → Build APK(s)**
2. **Build → Select Build Variant**
3. Change "debug" to "release"
4. **Build → Build Bundle(s) / APK(s) → Build APK(s)**

#### Option B: Command Line
```bash
cd android
./gradlew assembleRelease
```
APK location: `android/app/build/outputs/apk/release/app-release-unsigned.apk`

## Installing the APK on Your Phone

### Method 1: Direct Transfer (Recommended)
1. **Enable Developer Options** on your phone:
   - Go to Settings → About Phone
   - Tap "Build Number" 7 times
   - Go back to Settings → Developer Options
   - Enable "USB Debugging"

2. **Connect phone to computer** via USB
3. **Copy APK to phone**:
   - Copy `app-debug.apk` to Downloads folder on phone
   - Or use `adb install app-debug.apk`

4. **Install on phone**:
   - Open file manager on phone
   - Navigate to Downloads
   - Tap the APK file
   - Allow "Install from Unknown Sources" if prompted
   - Tap "Install"

### Method 2: Email/Cloud Transfer
1. **Email the APK** to yourself
2. **Open email on phone**
3. **Download APK** attachment
4. **Tap to install** (allow unknown sources if prompted)

### Method 3: ADB Install (Technical Users)
```bash
# Make sure phone is connected and USB debugging enabled
adb devices  # Should show your device
adb install android/app/build/outputs/apk/debug/app-debug.apk
```

## Troubleshooting

### Common Build Issues

#### Gradle Sync Failed
```bash
# Clean and rebuild
cd android
./gradlew clean
./gradlew assembleDebug
```

#### SDK Not Found
1. Open Android Studio
2. **Tools → SDK Manager**
3. Install Android SDK Platform 34 (or latest)
4. Install Android SDK Build-Tools 34.0.0

#### Memory Issues
Add to `android/gradle.properties`:
```
org.gradle.jvmargs=-Xmx4g -XX:MaxPermSize=512m
```

#### Kotlin Compilation Error
1. **File → Invalidate Caches and Restart**
2. **Build → Clean Project**
3. **Build → Rebuild Project**

### APK Installation Issues

#### "App not installed" Error
- **Enable "Install unknown apps"** for your file manager
- **Try different file manager** (like Files by Google)
- **Clear package installer cache**: Settings → Apps → Package Installer → Storage → Clear Cache

#### "Parse Error"
- **Check APK is not corrupted** (re-download/copy)
- **Ensure Android version compatibility** (requires Android 7.0+)

#### Permission Issues
- **Allow installation from unknown sources**
- **Disable Play Protect temporarily**: Play Store → Menu → Play Protect → Settings

## APK File Locations

After successful build, find your APK here:

### Debug APK (For Testing)
```
android/app/build/outputs/apk/debug/app-debug.apk
```
- **Size**: ~15-25 MB
- **Features**: Includes debugging symbols
- **Performance**: Slower, larger file

### Release APK (For Distribution)
```
android/app/build/outputs/apk/release/app-release-unsigned.apk
```
- **Size**: ~8-15 MB
- **Features**: Optimized, minified
- **Performance**: Faster, smaller file

## First Time Setup on Phone

After installing the APK:

1. **Open "Claude Code Monitor" app**
2. **Grant permissions** when prompted:
   - Camera (for QR scanning)
   - Storage (for message caching)
3. **Start your Claude Code server** on your computer
4. **Scan QR code** from server dashboard
5. **Begin monitoring** your Claude Code sessions!

## Automated Build Script

For convenience, here's a build script:

```bash
#!/bin/bash
# save as build-apk.sh
echo "Building Claude Code Monitor APK..."
cd android
./gradlew clean
./gradlew assembleRelease
echo "✅ APK built successfully!"
echo "📱 Location: android/app/build/outputs/apk/release/app-release-unsigned.apk"
```

Make executable and run:
```bash
chmod +x build-apk.sh
./build-apk.sh
```

## Development Notes

- **Debug APK**: Good for testing and development
- **Release APK**: Optimized for production use
- **Bundle (AAB)**: For Google Play Store (not needed for direct install)
- **Signed APK**: Required for Play Store (we're using unsigned for direct install)

## Security Note

The APK generated is unsigned and intended for development/personal use. For production distribution, you would need to sign the APK with a keystore.

---

**🎉 Once installed, your phone can now monitor Claude Code sessions in real-time!**
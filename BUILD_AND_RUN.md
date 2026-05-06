# How to Build and Run QoS Scheduler on Your Android Phone

**Complete step-by-step guide from source code to running app**

---

## 📋 Prerequisites

### Required Software

1. **Android Studio** (Latest version recommended)
   - Download: https://developer.android.com/studio
   - Version: Arctic Fox (2020.3.1) or newer
   - Includes: Android SDK, Gradle, Emulator

2. **Java Development Kit (JDK)**
   - JDK 11 or newer
   - Usually bundled with Android Studio
   - Verify: `java -version`

3. **Git** (if cloning from repository)
   - Download: https://git-scm.com/downloads
   - Verify: `git --version`

### Required Hardware

1. **Development Computer**
   - Windows 10/11, macOS 10.14+, or Linux
   - 8 GB RAM minimum (16 GB recommended)
   - 10 GB free disk space

2. **Android Phone**
   - Android 10 (API level 29) or newer
   - USB cable for connection
   - USB debugging enabled

---

## 🚀 Step 1: Set Up Android Studio

### 1.1 Install Android Studio

**Windows:**
```powershell
# Download installer from https://developer.android.com/studio
# Run the .exe installer
# Follow installation wizard
# Choose "Standard" installation type
```

**macOS:**
```bash
# Download .dmg from https://developer.android.com/studio
# Drag Android Studio to Applications folder
# Open Android Studio
# Follow setup wizard
```

**Linux:**
```bash
# Download .tar.gz from https://developer.android.com/studio
tar -xzf android-studio-*.tar.gz
cd android-studio/bin
./studio.sh
```

### 1.2 Install Android SDK Components

1. Open Android Studio
2. Go to **Tools → SDK Manager**
3. In **SDK Platforms** tab, install:
   - ✅ Android 13.0 (Tiramisu) - API Level 33
   - ✅ Android 10.0 (Q) - API Level 29
4. In **SDK Tools** tab, install:
   - ✅ Android SDK Build-Tools
   - ✅ Android SDK Platform-Tools
   - ✅ Android Emulator
   - ✅ Google Play services
5. Click **Apply** and wait for downloads

---

## 📂 Step 2: Open the Project

### 2.1 Open Project in Android Studio

1. Launch Android Studio
2. Click **Open** (or File → Open)
3. Navigate to your project folder
4. Select the root folder (containing `build.gradle.kts`)
5. Click **OK**

### 2.2 Wait for Gradle Sync

Android Studio will automatically:
- Download dependencies
- Configure build tools
- Index project files

**This takes 5-10 minutes on first open.**

Look for "Gradle sync finished" in the bottom status bar.

### 2.3 Resolve Any Errors

If you see errors:

**"SDK not found":**
```
File → Project Structure → SDK Location
Set Android SDK location (usually ~/Android/Sdk or C:\Users\YourName\AppData\Local\Android\Sdk)
```

**"Gradle version mismatch":**
```
File → Settings → Build, Execution, Deployment → Build Tools → Gradle
Select "Use Gradle from: 'gradle-wrapper.properties'"
```

**"Missing dependencies":**
```
Tools → SDK Manager → SDK Tools
Install missing components
Sync project again (File → Sync Project with Gradle Files)
```

---

## 📱 Step 3: Prepare Your Android Phone

### 3.1 Enable Developer Options

1. Go to **Settings → About Phone**
2. Find **Build Number**
3. Tap **Build Number** 7 times
4. You'll see "You are now a developer!"

### 3.2 Enable USB Debugging

1. Go to **Settings → System → Developer Options**
2. Enable **USB Debugging**
3. Enable **Install via USB** (if available)

### 3.3 Connect Phone to Computer

1. Connect phone via USB cable
2. On phone, you'll see "Allow USB debugging?" dialog
3. Check "Always allow from this computer"
4. Tap **OK**

### 3.4 Verify Connection

In Android Studio:
1. Look at the device dropdown (top toolbar)
2. You should see your phone model listed
3. If not, click the dropdown and select your device

**Troubleshooting:**

**Phone not detected:**
```bash
# Check ADB connection
cd ~/Android/Sdk/platform-tools  # macOS/Linux
cd C:\Users\YourName\AppData\Local\Android\Sdk\platform-tools  # Windows

./adb devices  # macOS/Linux
adb.exe devices  # Windows

# Should show:
# List of devices attached
# ABC123456789    device
```

**"Unauthorized" status:**
- Disconnect and reconnect USB cable
- Check phone for authorization dialog
- Tap "Always allow" and OK

**Still not working:**
- Try different USB cable
- Try different USB port
- Install phone manufacturer's USB drivers (Windows)
- Restart ADB: `adb kill-server` then `adb start-server`

---

## 🔨 Step 4: Build the Project

### 4.1 Clean Build

1. In Android Studio, go to **Build → Clean Project**
2. Wait for completion (1-2 minutes)

### 4.2 Build APK

**Option A: Build and Run (Recommended)**
1. Click the green **Run** button (▶️) in toolbar
2. Or press **Shift + F10** (Windows/Linux) or **Control + R** (macOS)
3. Select your phone from device list
4. Click **OK**

**Option B: Build APK Only**
1. Go to **Build → Build Bundle(s) / APK(s) → Build APK(s)**
2. Wait for build to complete
3. Click "locate" in the notification to find APK
4. APK location: `app/build/outputs/apk/debug/app-debug.apk`

### 4.3 Monitor Build Progress

- Check **Build** tab at bottom of Android Studio
- Build takes 2-5 minutes on first build
- Subsequent builds are faster (30 seconds - 1 minute)

### 4.4 Resolve Build Errors

**Common errors and fixes:**

**"Execution failed for task ':app:compileDebugKotlin'":**
```
File → Invalidate Caches → Invalidate and Restart
```

**"Could not resolve all dependencies":**
```
Check internet connection
File → Sync Project with Gradle Files
```

**"Manifest merger failed":**
```
Check app/src/main/AndroidManifest.xml for errors
Ensure all required permissions are declared
```

**"Duplicate class found":**
```
Build → Clean Project
Build → Rebuild Project
```

---

## 📲 Step 5: Install and Run

### 5.1 Automatic Installation (via Android Studio)

If you clicked **Run** in Step 4.2:
1. App automatically installs on your phone
2. App automatically launches
3. You'll see the main screen

### 5.2 Manual Installation (via APK)

If you built APK separately:

**Method 1: ADB Install**
```bash
cd app/build/outputs/apk/debug
adb install app-debug.apk

# If app already installed:
adb install -r app-debug.apk  # -r = replace existing
```

**Method 2: Transfer APK to Phone**
1. Copy `app-debug.apk` to phone (via USB, email, cloud)
2. On phone, open file manager
3. Tap the APK file
4. Tap **Install**
5. If blocked, enable "Install from unknown sources" in Settings

---

## ✅ Step 6: First Run Setup

### 6.1 Grant VPN Permission

1. Open **QoS Scheduler** app
2. You'll see the main dashboard
3. Tap the **toggle switch** to start QoS
4. Android shows "Connection request" dialog
5. Tap **OK** to grant VPN permission

**Important:** This is a one-time permission. The app creates a local VPN tunnel to intercept traffic.

### 6.2 Enable Mobile Hotspot

1. Go to **Settings → Network & Internet → Hotspot & Tethering**
2. Enable **Wi-Fi Hotspot**
3. Note the hotspot name and password
4. Return to QoS Scheduler app

### 6.3 Configure Uplink Bandwidth

1. In QoS Scheduler app, tap **Settings** (gear icon)
2. Set **Uplink Bandwidth** to your internet speed
   - Example: 10 Mbps for typical mobile data
   - Check with your carrier or run speed test
3. Tap **Save**

### 6.4 Connect Test Devices

1. On another device (laptop, tablet, phone), connect to your hotspot
2. Wait 5-10 seconds
3. Send some traffic (open a website, ping 8.8.8.8)
4. Device should appear in QoS Scheduler dashboard

---

## 🎮 Step 7: Test the App

### 7.1 Basic Functionality Test

**Test 1: Device Detection**
1. Connect a device to hotspot
2. On that device, open a website
3. Check QoS Scheduler dashboard
4. Device should appear with IP address

**Test 2: Priority Assignment**
1. Tap a device in the dashboard
2. Change priority to **HIGH**
3. Check that priority label updates
4. Return to dashboard

**Test 3: Traffic Monitoring**
1. On connected device, download a file or stream video
2. Watch QoS Scheduler dashboard
3. Throughput should update in real-time (every 1 second)

**Test 4: Stop/Start**
1. Toggle QoS switch OFF
2. VPN notification should disappear
3. Toggle QoS switch ON
4. VPN notification should reappear

### 7.2 Advanced Testing (Optional)

**Test with iPerf3:**
Follow `validation/QUICKSTART.md` for detailed testing instructions.

---

## 🐛 Troubleshooting

### App Crashes on Launch

**Check Logcat:**
1. In Android Studio, open **Logcat** tab (bottom)
2. Filter by package name: `com.qos.scheduler`
3. Look for red error messages
4. Common issues:
   - Missing permissions in AndroidManifest.xml
   - Null pointer exceptions
   - Resource not found errors

**Fix:**
```bash
# Uninstall and reinstall
adb uninstall com.qos.scheduler
adb install app-debug.apk
```

### VPN Permission Denied

**Symptoms:** Toggle switch turns off immediately after turning on

**Fix:**
1. Go to **Settings → Apps → QoS Scheduler**
2. Tap **Permissions**
3. Ensure all permissions are granted
4. Try again

### Devices Not Appearing

**Symptoms:** Connected devices don't show in dashboard

**Possible causes:**
1. **QoS not started:** Check toggle switch is ON
2. **No traffic sent:** Device must send packets to be detected
3. **VPN tunnel not established:** Check VPN notification is visible

**Fix:**
1. On connected device, ping 8.8.8.8 or open a website
2. Wait 10 seconds
3. Check dashboard again

### No Internet on Connected Devices

**Symptoms:** Devices connect to hotspot but can't access internet

**Possible causes:**
1. **VPN tunnel routing issue**
2. **Packet forwarding disabled**

**Fix:**
1. Stop QoS (toggle OFF)
2. Test internet works without QoS
3. If works, there's a bug in packet forwarding
4. Check Logcat for errors in `QosVpnService`

### High Battery Drain

**Expected behavior:** QoS service runs in foreground, uses 5-10% battery per hour

**If excessive (>20% per hour):**
1. Check for infinite loops in Logcat
2. Reduce packet logging (disable `PacketLogger`)
3. Increase rebalancing interval (from 1000 to 5000 packets)

---

## 📊 Performance Monitoring

### Check CPU Usage

**Via Android Studio:**
1. **View → Tool Windows → Profiler**
2. Select your device and app
3. Click **CPU** section
4. Should be <15% under normal load

### Check Memory Usage

**Via Android Studio:**
1. **View → Tool Windows → Profiler**
2. Select your device and app
3. Click **Memory** section
4. Should be <80 MB

### Check Network Stats

**Via App:**
1. Open QoS Scheduler
2. View dashboard
3. Check throughput values match expected speeds

---

## 🔄 Updating the App

### After Code Changes

1. Make your code changes in Android Studio
2. Click **Run** button (▶️)
3. Android Studio automatically:
   - Builds new APK
   - Uninstalls old version
   - Installs new version
   - Launches app

### Preserving Data

**Priority assignments are preserved** because they're stored in DataStore (persistent storage).

**To clear all data:**
```bash
adb shell pm clear com.qos.scheduler
```

---

## 📦 Building Release APK (For Distribution)

### Generate Signed APK

1. **Build → Generate Signed Bundle / APK**
2. Select **APK**
3. Click **Next**

**Create Keystore (first time only):**
1. Click **Create new...**
2. Fill in details:
   - Key store path: Choose location (e.g., `qos-keystore.jks`)
   - Password: Create strong password
   - Alias: `qos-key`
   - Validity: 25 years
   - Certificate info: Your name and organization
3. Click **OK**

**Sign APK:**
1. Select your keystore
2. Enter passwords
3. Click **Next**
4. Select **release** build variant
5. Check **V1** and **V2** signature versions
6. Click **Finish**

**Output:**
- Location: `app/release/app-release.apk`
- This APK can be distributed to others
- Can be uploaded to Google Play Store

---

## 🌐 Publishing to Google Play Store (Optional)

### Prerequisites

1. Google Play Developer account ($25 one-time fee)
2. Signed release APK
3. App icon (512×512 PNG)
4. Screenshots (at least 2)
5. Privacy policy URL

### Steps

1. Go to https://play.google.com/console
2. Create new app
3. Fill in app details
4. Upload APK
5. Set pricing (free or paid)
6. Submit for review

**Review time:** 1-7 days

---

## 📝 Summary Checklist

**Before building:**
- [ ] Android Studio installed
- [ ] Project opened and synced
- [ ] No Gradle errors

**Before running:**
- [ ] Phone connected via USB
- [ ] USB debugging enabled
- [ ] Device detected in Android Studio

**After installation:**
- [ ] VPN permission granted
- [ ] Mobile hotspot enabled
- [ ] Uplink bandwidth configured
- [ ] Test device connected and detected

**Validation:**
- [ ] Devices appear in dashboard
- [ ] Priorities can be changed
- [ ] Throughput updates in real-time
- [ ] QoS enforcement works (test with iPerf3)

---

## 🆘 Getting Help

**If you encounter issues:**

1. **Check Logcat** for error messages
2. **Read error messages carefully** - they usually indicate the problem
3. **Search Stack Overflow** for specific error messages
4. **Check Android Studio documentation**
5. **Verify all prerequisites** are met

**Common resources:**
- Android Developer Docs: https://developer.android.com/docs
- Stack Overflow: https://stackoverflow.com/questions/tagged/android
- Kotlin Docs: https://kotlinlang.org/docs/home.html

---

## 🎉 Success!

If you've followed all steps, you should now have:
- ✅ QoS Scheduler running on your phone
- ✅ Devices detected and displayed
- ✅ Priority-based bandwidth allocation working
- ✅ Real-time traffic monitoring

**Next steps:**
1. Run experimental validation (see `validation/QUICKSTART.md`)
2. Test with real applications (video calls, gaming, downloads)
3. Collect data for your thesis

**Congratulations!** 🚀

# Quick Start - 5 Minutes to Running App

**Fastest path from code to running app on your phone**

---

## ⚡ Super Quick Version (For Experienced Developers)

```bash
# 1. Open in Android Studio
File → Open → Select project folder

# 2. Connect phone via USB (USB debugging enabled)

# 3. Click Run button (▶️) or press Shift+F10

# 4. Grant VPN permission when prompted

# 5. Enable mobile hotspot on phone

# Done! App is running.
```

---

## 📱 Detailed Quick Start (For Everyone)

### Step 1: Install Android Studio (10 minutes)
- Download: https://developer.android.com/studio
- Install with default settings
- Open Android Studio

### Step 2: Open Project (2 minutes)
1. Click **Open** in Android Studio
2. Select your project folder
3. Wait for Gradle sync (5-10 minutes first time)

### Step 3: Connect Phone (2 minutes)
1. Enable **Developer Options** on phone:
   - Settings → About Phone → Tap "Build Number" 7 times
2. Enable **USB Debugging**:
   - Settings → Developer Options → USB Debugging ON
3. Connect phone via USB cable
4. Tap **OK** on "Allow USB debugging?" dialog

### Step 4: Run App (1 minute)
1. Click green **Run** button (▶️) in Android Studio toolbar
2. Select your phone from device list
3. Click **OK**
4. Wait for build and installation (2-5 minutes)

### Step 5: First Launch (1 minute)
1. App opens automatically on your phone
2. Tap the **toggle switch** to start QoS
3. Tap **OK** on VPN permission dialog
4. Enable **Mobile Hotspot** in phone settings

### Step 6: Test (1 minute)
1. Connect another device to your hotspot
2. On that device, open a website
3. Check QoS Scheduler app - device should appear!

---

## 🚨 Common Issues & Quick Fixes

### "Phone not detected"
```bash
# Check connection
adb devices

# If empty, try:
- Different USB cable
- Different USB port
- Restart phone
- Restart computer
```

### "Build failed"
```
File → Invalidate Caches → Invalidate and Restart
Build → Clean Project
Build → Rebuild Project
```

### "VPN permission denied"
- Make sure you tapped OK on the system dialog
- Try toggling switch again
- Restart app if needed

### "Devices not appearing"
- Make sure toggle switch is ON (green)
- Make sure hotspot is enabled
- On connected device, open a website or ping 8.8.8.8
- Wait 10 seconds

---

## 📖 Need More Help?

**Read the full guide:** `BUILD_AND_RUN.md`

**Key sections:**
- Detailed troubleshooting
- Performance monitoring
- Building release APK
- Publishing to Play Store

---

## ✅ Success Checklist

After following these steps, you should have:
- [x] App installed on phone
- [x] VPN permission granted
- [x] Mobile hotspot enabled
- [x] Test device connected and visible in app
- [x] Real-time throughput displayed

**Total time:** 15-20 minutes (including downloads)

---

## 🎯 What's Next?

1. **Test basic functionality:**
   - Change device priorities
   - Monitor throughput
   - Test with multiple devices

2. **Run validation experiments:**
   - See `validation/QUICKSTART.md`
   - Use iPerf3 for quantitative results
   - Use Wireshark for packet analysis

3. **Collect thesis data:**
   - Take screenshots
   - Record throughput measurements
   - Document results

**Good luck!** 🚀

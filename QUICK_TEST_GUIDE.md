# Quick Test Guide - After Critical Fixes

## 🎯 Mục Đích
Hướng dẫn test nhanh để verify các fixes đã hoạt động đúng

---

## ⚡ Quick Test (5 phút)

### Bước 1: Chuẩn Bị
```bash
# Build app mới
./gradlew assembleDebug

# Install lên điện thoại
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Bước 2: Enable Logcat
```bash
# Mở terminal và chạy logcat để xem logs
adb logcat -s QosVpnService:D DeviceRegistry:D HotspotManager:D
```

### Bước 3: Test Network Speed
1. **Turn OFF app**
2. Mở browser trên điện thoại → Visit fast.com
3. Note speed: _______ Mbps (baseline)
4. **Turn ON app** 
5. Refresh fast.com
6. Note speed: _______ Mbps (should be similar)

✅ **PASS**: Speed giảm < 10%  
❌ **FAIL**: Speed giảm > 30% → Vẫn còn routing issue

### Bước 4: Test Device Detection
1. **Turn ON hotspot** trên điện thoại
2. **Connect laptop** vào hotspot
3. **Turn ON app**
4. Trên laptop: Mở browser → Visit google.com
5. Đợi 5 giây
6. Check app dashboard

✅ **PASS**: Laptop xuất hiện trong device list  
❌ **FAIL**: Vẫn "Waiting for device to connect"

---

## 🔍 Detailed Test (15 phút)

### Test 1: Verify VPN Routing

**Check trong logcat**:
```
QosVpnService: Starting tunnel with hotspot subnet: 192.168.43
QosVpnService: Added route: 192.168.43.0/24
QosVpnService: VPN tunnel established successfully
```

✅ **PASS**: Thấy subnet được detect và route đúng  
❌ **FAIL**: Thấy "Could not detect hotspot subnet" hoặc không có logs

**Check routing table** (optional):
```bash
adb shell ip route | grep tun
```

Expected output:
```
192.168.43.0/24 dev tun0 scope link
```

NOT expected:
```
0.0.0.0/0 dev tun0  # ❌ This means routing ALL traffic
```

---

### Test 2: Verify Packet Flow

**Generate traffic từ laptop**:
1. Open browser
2. Visit multiple websites: google.com, youtube.com, facebook.com
3. Download a small file

**Check logcat**:
```
QosVpnService: Packet #50: 192.168.43.2:54321 -> 142.250.185.46:443 (TCP)
QosVpnService: Packet #100: 192.168.43.2:54322 -> 8.8.8.8:53 (UDP)
QosVpnService: New hotspot client detected: 192.168.43.2
```

✅ **PASS**: Thấy packets từ hotspot subnet (192.168.43.x)  
❌ **FAIL**: Không thấy packets hoặc thấy packets từ 10.0.0.x

---

### Test 3: Verify Device Information

**Check trong app dashboard**:
- IP address: 192.168.43.x (correct subnet)
- Hostname: "DESKTOP-XXX" hoặc "Device-XX" (not just IP)
- MAC address: Visible in device detail (if available)
- Throughput: Updates every second

✅ **PASS**: All info hiển thị đúng  
❌ **FAIL**: Chỉ thấy IP, không có hostname/MAC

---

### Test 4: Verify QoS Enforcement

**Setup**:
1. Connect 2 devices (Device A và Device B)
2. Set Device A → HIGH priority
3. Set Device B → LOW priority

**Test**:
1. Trên cả 2 devices: Mở fast.com hoặc speedtest.net
2. Run speed test **đồng thời** trên cả 2
3. So sánh kết quả

✅ **PASS**: Device A gets 3-4x bandwidth của Device B  
❌ **FAIL**: Cả 2 devices có speed tương đương

**Example results**:
```
Device A (HIGH):  8.5 Mbps
Device B (LOW):   2.1 Mbps
Ratio: 4.0x ✅
```

---

## 🐛 Troubleshooting

### Issue: "Waiting for device to connect" vẫn xuất hiện

**Debug steps**:

1. **Check hotspot subnet detection**:
```bash
adb logcat -s HotspotManager:D | grep subnet
```

Expected:
```
HotspotManager: getHotspotSubnet() -> 192.168.43
```

If null → Hotspot không được detect

**Fix**: 
- Verify hotspot is actually ON
- Try turning hotspot OFF then ON again
- Check Android version (must be 10+)

---

2. **Check VPN tunnel**:
```bash
adb logcat -s QosVpnService:D | grep tunnel
```

Expected:
```
QosVpnService: VPN tunnel established successfully
```

If "Failed to establish" → VPN permission issue

**Fix**:
- Uninstall app
- Reinstall
- Grant VPN permission when prompted

---

3. **Check packet flow**:
```bash
adb logcat -s QosVpnService:D | grep Packet
```

Expected: See packets every few seconds

If no packets → VPN not intercepting traffic

**Fix**:
- Check VPN is actually active (should see key icon in status bar)
- Try turning app OFF then ON
- Restart phone if needed

---

### Issue: Network vẫn chậm khi app ON

**Debug steps**:

1. **Check routing**:
```bash
adb shell ip route | grep tun
```

If you see `0.0.0.0/0 dev tun0` → WRONG! Still routing all traffic

**Fix**: Code changes didn't apply properly
- Clean build: `./gradlew clean`
- Rebuild: `./gradlew assembleDebug`
- Reinstall app

---

2. **Check CPU usage**:
```bash
adb shell top | grep qos.scheduler
```

Expected: < 15% CPU

If > 30% → Possible infinite loop

**Fix**: Check logcat for errors, may need to restart app

---

### Issue: Devices appear but no throughput shown

**Debug steps**:

1. **Check statistics update**:
```bash
adb logcat -s DeviceRegistry:D | grep throughput
```

Expected: See updates every second

**Fix**: 
- Verify traffic is actually flowing (browse websites on client)
- Check `startThroughputSampler()` is running

---

## 📊 Success Criteria

Tất cả tests phải PASS:

- [x] Network speed normal khi app ON (< 10% slower)
- [x] Devices detected within 5 seconds
- [x] Packets flowing through VPN tunnel
- [x] Device info displayed correctly
- [x] QoS enforcement working (HIGH > LOW by 3-4x)
- [x] No routing loops
- [x] CPU usage < 15%

---

## 🎉 If All Tests Pass

**Congratulations!** Critical fixes đã hoạt động đúng.

**Next steps**:
1. Run full validation tests (iPerf3, Wireshark)
2. Test với nhiều devices hơn (3-5 devices)
3. Test với different traffic types (video, gaming, downloads)
4. Update thesis documentation với fixes

---

## 📞 If Tests Fail

**Collect debug info**:
```bash
# Capture full logcat
adb logcat -d > logcat_full.txt

# Capture routing table
adb shell ip route > routes.txt

# Capture network interfaces
adb shell ip addr > interfaces.txt
```

**Share**:
- logcat_full.txt
- routes.txt
- interfaces.txt
- Screenshots of app dashboard
- Description of what's not working

---

## 🔄 Quick Reset (If Things Go Wrong)

```bash
# Stop app
adb shell am force-stop com.qos.scheduler

# Clear app data
adb shell pm clear com.qos.scheduler

# Restart app
adb shell am start -n com.qos.scheduler/.MainActivity

# Grant VPN permission again when prompted
```

---

**Good luck with testing! 🚀**

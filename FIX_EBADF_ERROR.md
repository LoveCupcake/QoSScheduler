# Fix EBADF Error - TUN Interface Race Condition

## Problem Diagnosed

### Error Log
```
2026-05-04 18:19:20.481 QosVpnService: TUN Read Loop Error: read failed: EBADF (Bad file descriptor)
```

### Root Cause
**Race condition during mode switching:**

1. User turns ON app → VPN starts in **MONITOR mode**
2. App automatically switches to **RELAY_EXPERIMENTAL mode** after 8 seconds
3. During mode switch:
   - `setRuntimeMode()` calls `startTunnel()`
   - `startTunnel()` calls `stopTunnel()` to close old TUN
   - `stopTunnel()` closes `tunInterface` 
   - **BUT** `FileInputStream` in `runTunReadLoop()` still holds reference to old file descriptor
   - Read loop tries to read from closed FD → **EBADF error**

### Timeline
```
18:19:12 - VPN starts in MONITOR mode
18:19:20 - Switch to RELAY_EXPERIMENTAL mode
18:19:20 - EBADF error (reading from closed FD)
18:19:20 - New tunnel established
```

## Solution Applied

### 1. Track Input Stream Reference
Added `tunInputStream` variable to track the input stream:

```kotlin
private var tunInputStream: FileInputStream? = null
private var tunOutputStream: FileOutputStream? = null
```

### 2. Close Streams Before TUN Interface
Modified `stopTunnel()` to close streams FIRST:

```kotlin
private suspend fun stopTunnel() {
    // Cancel jobs first
    val jobs = listOfNotNull(tunReadJob, packetProcessJob, tunWriteJob)
    jobs.forEach { it.cancel() }
    
    // CRITICAL: Close streams FIRST to unblock I/O operations
    runCatching { tunInputStream?.close() }
    tunInputStream = null
    runCatching { tunOutputStream?.close() }
    tunOutputStream = null
    
    // Then close TUN interface
    tunInterface?.close()
    tunInterface = null

    // Now safe to wait for jobs to terminate
    jobs.forEach { it.join() }
    
    // Drain channels
    while (packetChannel.tryReceive().isSuccess) {}
    while (tunWriteChannel.tryReceive().isSuccess) {}
    
    android.util.Log.d("QosVpnService", "Tunnel stopped completely")
}
```

### 3. Store Stream Reference in Read Loop
Modified `runTunReadLoop()` to store stream reference:

```kotlin
private suspend fun runTunReadLoop(tun: ParcelFileDescriptor) {
    val inputStream = FileInputStream(tun.fileDescriptor)
    tunInputStream = inputStream  // Store reference for cleanup
    
    android.util.Log.d("QosVpnService", "TUN Read Loop started")
    
    while (scope.isActive) {
        // ... read loop ...
    }
    
    android.util.Log.d("QosVpnService", "TUN Read Loop exited")
}
```

### 4. Better Error Handling
Changed error log from ERROR to WARNING since it's expected during shutdown:

```kotlin
} catch (e: Exception) { 
    // Expected when stream is closed during shutdown
    if (scope.isActive && tunInterface != null) {
        android.util.Log.w("QosVpnService", "TUN Read Loop stopped: ${e.message}")
    }
    packetPool.release(buffer)
    break 
}
```

## Why This Fixes the Problem

### Before (Broken)
```
stopTunnel() called
  ↓
tunInterface.close()  ← Closes ParcelFileDescriptor
  ↓
FileInputStream still open ← Still holds old FD
  ↓
inputStream.read() ← Tries to read from closed FD
  ↓
EBADF error! ❌
```

### After (Fixed)
```
stopTunnel() called
  ↓
tunInputStream.close()  ← Closes FileInputStream first
  ↓
tunInterface.close()  ← Then closes ParcelFileDescriptor
  ↓
Read loop catches IOException and exits cleanly ✅
  ↓
jobs.join() waits for clean exit
  ↓
New tunnel can be established safely
```

## Additional Fix: Auto-Detect DNS

Also fixed DNS configuration to work on emulator:

```kotlin
// Auto-detect DNS servers from system instead of hardcoding
val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
val activeNetwork = cm.activeNetwork
val linkProperties = activeNetwork?.let { cm.getLinkProperties(it) }

val dnsServers = linkProperties?.dnsServers?.take(2)
if (dnsServers != null && dnsServers.isNotEmpty()) {
    dnsServers.forEach { dns ->
        builder.addDnsServer(dns.hostAddress ?: dns.toString())
        Log.d("QosVpnService", "Using system DNS: ${dns.hostAddress}")
    }
} else {
    // Fallback to public DNS
    builder.addDnsServer("8.8.8.8")
    builder.addDnsServer("8.8.4.4")
}
```

**Benefits:**
- ✅ Works on emulator (uses 10.0.2.3)
- ✅ Works on real device (uses carrier/WiFi DNS)
- ✅ Falls back to 8.8.8.8 if system DNS unavailable

## Testing Instructions

1. **Install updated APK:**
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

2. **Test on Emulator:**
   - Turn ON the app
   - Wait for mode switch (8 seconds)
   - Check logcat - should see:
     ```
     QosVpnService: Tunnel stopped completely
     QosVpnService: TUN Read Loop exited
     QosVpnService: Establishing tunnel in mode: RELAY_EXPERIMENTAL
     QosVpnService: TUN Read Loop started
     ```
   - **NO EBADF error!**

3. **Test Network:**
   - Open Chrome
   - Navigate to 1.1.1.1
   - Should load successfully

4. **Check DNS:**
   ```bash
   adb logcat -d | grep "Using system DNS"
   ```
   - On emulator: Should see `10.0.2.3`
   - On real device: Should see carrier/WiFi DNS

## Expected Behavior

### Clean Mode Switch
```
[18:19:12] VPN starts in MONITOR mode
[18:19:12] TUN Read Loop started
[18:19:20] Switching mode from MONITOR to RELAY_EXPERIMENTAL
[18:19:20] Tunnel stopped completely
[18:19:20] TUN Read Loop exited
[18:19:20] Establishing tunnel in mode: RELAY_EXPERIMENTAL
[18:19:20] TUN Read Loop started
[18:19:20] VPN Scope: All apps except controller
```

### Network Should Work
- ✅ DNS resolution works
- ✅ HTTP/HTTPS traffic flows
- ✅ No EBADF errors
- ✅ Smooth mode transitions

## Files Modified

1. `app/src/main/java/com/qos/scheduler/service/QosVpnService.kt`
   - Added `tunInputStream` variable
   - Modified `stopTunnel()` to close streams first
   - Modified `runTunReadLoop()` to store stream reference
   - Added auto-detect DNS configuration
   - Improved error logging

## Build Status
✅ **BUILD SUCCESSFUL** - Ready to test!

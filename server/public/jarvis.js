// jarvis.js — Hand Gesture Control Engine v2 (Smooth Edition)
// Requires MediaPipe Hands loaded via CDN in index.html

(function () {
  'use strict';

  // ─── Config (tweak these to taste) ───────────────────────────────────────
  const LERP_FACTOR       = 0.14;   // cursor smoothing 0.0 (frozen) – 1.0 (instant)
  const GESTURE_BUFFER    = 8;      // frames to vote on gesture stability
  const PINCH_ENTER_DIST  = 0.055;  // distance to enter pinch state
  const PINCH_EXIT_DIST   = 0.085;  // distance to exit pinch (hysteresis gap)
  const SWIPE_VEL_THRESH  = 0.022;  // wrist velocity to trigger swipe
  const SWIPE_COOLDOWN_MS = 900;
  const CLICK_COOLDOWN_MS = 650;
  const ACTION_HOLD_FRAMES= 18;     // frames gesture must hold before bulk actions

  // ─── State ───────────────────────────────────────────────────────────────
  let jarvisActive   = false;
  let hands          = null;
  let camera         = null;

  // Cursor smoothing
  let cursorX = window.innerWidth  / 2;
  let cursorY = window.innerHeight / 2;
  let targetX = cursorX;
  let targetY = cursorY;
  let rafId   = null;

  // Gesture stability
  const gestureBuffer = [];
  let stableGesture   = '';
  let gestureHoldCount= 0;

  // Pinch hysteresis
  let isPinching = false;

  // Swipe velocity
  let prevWristX      = null;
  let prevWristTime   = null;
  let lastSwipeTime   = 0;

  // Cooldowns (timestamps)
  let lastClickTime   = 0;
  let lastActionTime  = 0;

  const PAGE_ORDER = ['dashboard','devices','policies','statistics','telemetry','connect'];

  // ─── DOM refs ─────────────────────────────────────────────────────────────
  const btnJarvis     = document.getElementById('btn-jarvis');
  const cursorEl      = document.getElementById('jarvis-cursor');
  const hud           = document.getElementById('jarvis-hud');
  const hudGesture    = document.getElementById('hud-gesture');
  const hudHint       = document.getElementById('hud-hint');
  const camBox        = document.getElementById('jarvis-cam');
  const video         = document.getElementById('jarvis-video');
  const overlayCanvas = document.getElementById('jarvis-canvas');
  const guide         = document.getElementById('jarvis-guide');
  const octx          = overlayCanvas?.getContext('2d') ?? null;

  // ─── Toggle ───────────────────────────────────────────────────────────────
  btnJarvis.addEventListener('click', () => jarvisActive ? deactivate() : activate());

  function activate() {
    jarvisActive = true;
    btnJarvis.classList.add('active');
    document.body.classList.add('jarvis-active');
    [cursorEl, hud, camBox, guide].forEach(el => el && (el.style.display = ''));
    startCursorLoop();
    if (!hands) initMediaPipe(); else startCamera();
  }

  function deactivate() {
    jarvisActive = false;
    btnJarvis.classList.remove('active');
    document.body.classList.remove('jarvis-active');
    [cursorEl, hud, camBox, guide].forEach(el => el && (el.style.display = 'none'));
    if (rafId) { cancelAnimationFrame(rafId); rafId = null; }
    if (camera) { try { camera.stop(); } catch(e) {} camera = null; }
    if (video?.srcObject) {
      video.srcObject.getTracks().forEach(t => t.stop());
      video.srcObject = null;
    }
    gestureBuffer.length = 0;
    stableGesture = '';
    isPinching = false;
  }

  // ─── Smooth cursor loop (runs on RAF, independent of MediaPipe framerate) ─
  function startCursorLoop() {
    function loop() {
      if (!jarvisActive) return;
      // Lerp current position toward target
      cursorX += (targetX - cursorX) * LERP_FACTOR;
      cursorY += (targetY - cursorY) * LERP_FACTOR;
      cursorEl.style.left = cursorX + 'px';
      cursorEl.style.top  = cursorY + 'px';
      rafId = requestAnimationFrame(loop);
    }
    rafId = requestAnimationFrame(loop);
  }

  // ─── MediaPipe Setup ─────────────────────────────────────────────────────
  function initMediaPipe() {
    if (typeof Hands === 'undefined') {
      showHUD('MEDIAPIPE NOT LOADED', 'Check internet connection', '');
      return;
    }
    hands = new Hands({
      locateFile: f => `https://cdn.jsdelivr.net/npm/@mediapipe/hands/${f}`
    });
    hands.setOptions({
      maxNumHands: 1,
      modelComplexity: 1,
      minDetectionConfidence: 0.72,
      minTrackingConfidence: 0.65,
    });
    hands.onResults(onResults);
    startCamera();
  }

  function startCamera() {
    if (typeof Camera === 'undefined') return;
    camera = new Camera(video, {
      onFrame: async () => { if (jarvisActive && hands) await hands.send({ image: video }); },
      width: 200, height: 150,
    });
    camera.start();
  }

  // ─── Frame processor ─────────────────────────────────────────────────────
  function onResults(results) {
    if (!jarvisActive) return;
    if (octx) octx.clearRect(0, 0, overlayCanvas.width, overlayCanvas.height);

    if (!results.multiHandLandmarks?.length) {
      pushGesture('none');
      showHUD('SCANNING...', 'Show your hand to the camera', '');
      return;
    }

    const lm = results.multiHandLandmarks[0];
    drawSkeleton(lm);

    // Raw gesture classification
    const raw = classifyGesture(lm);
    // Push into stability buffer
    const stable = pushGesture(raw);

    // Update cursor target (not cursor position — RAF handles that)
    const tip = lm[8]; // index fingertip
    targetX = (1 - tip.x) * window.innerWidth;
    targetY = tip.y * window.innerHeight;

    // Swipe via wrist velocity
    detectSwipe(lm[0]);

    // Execute action only for stable gesture
    executeGesture(stable, lm);
  }

  // ─── Gesture stability: majority vote over buffer ─────────────────────────
  function pushGesture(g) {
    gestureBuffer.push(g);
    if (gestureBuffer.length > GESTURE_BUFFER) gestureBuffer.shift();

    // Count occurrences
    const counts = {};
    gestureBuffer.forEach(x => counts[x] = (counts[x] || 0) + 1);
    let winner = 'none', maxCount = 0;
    for (const [k, v] of Object.entries(counts)) {
      if (v > maxCount) { winner = k; maxCount = v; }
    }

    // Must win majority (>50%)
    if (maxCount > GESTURE_BUFFER / 2) {
      if (winner !== stableGesture) {
        stableGesture = winner;
        gestureHoldCount = 0;
      } else {
        gestureHoldCount++;
      }
    }
    return stableGesture;
  }

  // ─── Gesture Classification ───────────────────────────────────────────────
  function classifyGesture(lm) {
    const thumbTip  = lm[4];
    const indexTip  = lm[8];
    const pinchDist = dist(thumbTip, indexTip);

    // Pinch with hysteresis
    if (!isPinching && pinchDist < PINCH_ENTER_DIST) isPinching = true;
    if (isPinching  && pinchDist > PINCH_EXIT_DIST)  isPinching = false;
    if (isPinching) return 'pinch';

    const fingers = getFingersUp(lm);
    const [thumb, index, middle, ring, pinky] = fingers;

    if (!thumb && !index && !middle && !ring && !pinky) return 'fist';
    if (thumb  &&  index &&  middle && ring  && pinky)  return 'open';
    if (!thumb &&  index && !middle && !ring && !pinky)  return 'point';
    if (!thumb &&  index &&  middle && !ring && !pinky)  return 'peace';
    return 'unknown';
  }

  function getFingersUp(lm) {
    const thumb  = lm[4].x  < lm[3].x;   // mirrored
    const index  = lm[8].y  < lm[6].y;
    const middle = lm[12].y < lm[10].y;
    const ring   = lm[16].y < lm[14].y;
    const pinky  = lm[20].y < lm[18].y;
    return [thumb, index, middle, ring, pinky];
  }

  function dist(a, b) {
    return Math.sqrt((a.x - b.x) ** 2 + (a.y - b.y) ** 2);
  }

  // ─── Swipe via wrist velocity ─────────────────────────────────────────────
  function detectSwipe(wrist) {
    const now = performance.now();
    if (prevWristX !== null && prevWristTime !== null) {
      const dt = now - prevWristTime;
      if (dt > 0) {
        const vel = (wrist.x - prevWristX) / dt * 1000; // units/s
        const cooldown = now - lastSwipeTime;
        if (Math.abs(vel) > SWIPE_VEL_THRESH * 1000 && cooldown > SWIPE_COOLDOWN_MS) {
          // vel positive → hand moved right in normalized coords → after mirror: swipe left
          navigatePage(vel > 0 ? 1 : -1);
          lastSwipeTime = now;
        }
      }
    }
    prevWristX    = wrist.x;
    prevWristTime = now;
  }

  // ─── Execute action from stable gesture ──────────────────────────────────
  function executeGesture(gesture, lm) {
    const now = Date.now();
    cursorEl.classList.remove('pinching', 'clicking');

    switch (gesture) {
      case 'point':
        setHUD('☝️  POINTING', 'Pinch to click', '');
        break;

      case 'pinch':
        cursorEl.classList.add('pinching');
        setHUD('🤏  PINCH', 'Clicking...', 'gesture-pinch');
        if (now - lastClickTime > CLICK_COOLDOWN_MS) {
          triggerClickAt(cursorX, cursorY);
          lastClickTime = now;
        }
        break;

      case 'peace':
        setHUD('✌️  PEACE', 'Scrolling...', 'gesture-peace');
        // Scroll speed proportional to how long held
        const scrollAmt = Math.min(gestureHoldCount * 3, 80);
        window.scrollBy({ top: scrollAmt, behavior: 'auto' });
        break;

      case 'open':
        setHUD('🖐️  OPEN HAND', 'Hold to refresh...', 'gesture-open');
        if (gestureHoldCount > ACTION_HOLD_FRAMES && now - lastActionTime > 2000) {
          document.getElementById('btn-refresh')?.click();
          lastActionTime = now;
        }
        break;

      case 'fist':
        setHUD('✊  FIST', 'Hold to deactivate...', 'gesture-fist');
        if (gestureHoldCount > ACTION_HOLD_FRAMES && now - lastActionTime > 2000) {
          deactivate();
          lastActionTime = now;
        }
        break;

      case 'none':
        setHUD('SCANNING...', 'Show your hand', '');
        break;

      default:
        setHUD('🤖 JARVIS ACTIVE', 'Waiting for gesture...', '');
    }
  }

  // ─── Click simulation ─────────────────────────────────────────────────────
  function triggerClickAt(x, y) {
    cursorEl.classList.add('clicking');
    setTimeout(() => cursorEl.classList.remove('clicking'), 200);
    const el = document.elementFromPoint(x, y);
    if (el) el.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, clientX: x, clientY: y }));
  }

  // ─── Page navigation ──────────────────────────────────────────────────────
  function navigatePage(direction) {
    const active = document.querySelector('.nav-item.active');
    if (!active) return;
    const idx    = PAGE_ORDER.indexOf(active.dataset.page);
    const next   = (idx + direction + PAGE_ORDER.length) % PAGE_ORDER.length;
    document.querySelector(`.nav-item[data-page="${PAGE_ORDER[next]}"]`)?.click();
  }

  // ─── HUD ─────────────────────────────────────────────────────────────────
  function setHUD(gestureText, hintText, cls) {
    hudGesture.textContent = gestureText;
    hudHint.textContent    = hintText;
    hudGesture.className   = 'hud-gesture' + (cls ? ' ' + cls : '');
  }

  // ─── Skeleton overlay ────────────────────────────────────────────────────
  function drawSkeleton(lm) {
    if (!octx || !overlayCanvas) return;
    const W = overlayCanvas.width  = overlayCanvas.offsetWidth;
    const H = overlayCanvas.height = overlayCanvas.offsetHeight;

    const CONNECTIONS = [
      [0,1],[1,2],[2,3],[3,4],
      [0,5],[5,6],[6,7],[7,8],
      [0,9],[9,10],[10,11],[11,12],
      [0,13],[13,14],[14,15],[15,16],
      [0,17],[17,18],[18,19],[19,20],
      [5,9],[9,13],[13,17]
    ];

    octx.clearRect(0, 0, W, H);
    octx.lineWidth   = 1.5;
    octx.strokeStyle = 'rgba(0,243,255,0.75)';
    octx.shadowBlur  = 6;
    octx.shadowColor = '#00f3ff';

    CONNECTIONS.forEach(([a, b]) => {
      octx.beginPath();
      octx.moveTo(lm[a].x * W, lm[a].y * H);
      octx.lineTo(lm[b].x * W, lm[b].y * H);
      octx.stroke();
    });

    lm.forEach((pt, i) => {
      const isIndexTip = i === 8;
      octx.beginPath();
      octx.arc(pt.x * W, pt.y * H, isIndexTip ? 4 : 2, 0, Math.PI * 2);
      octx.fillStyle   = isIndexTip ? '#ff2d9b' : 'rgba(0,243,255,0.9)';
      octx.shadowColor = isIndexTip ? '#ff2d9b' : '#00f3ff';
      octx.shadowBlur  = isIndexTip ? 10 : 4;
      octx.fill();
    });
  }

})();

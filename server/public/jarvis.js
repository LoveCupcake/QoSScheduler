// jarvis.js — Hand Gesture Control Engine
// Requires MediaPipe Hands loaded via CDN in index.html

(function () {
  'use strict';

  // ─── State ───────────────────────────────────────────────────────────────
  let jarvisActive    = false;
  let hands           = null;
  let camera          = null;
  let lastGesture     = '';
  let gestureHoldMs   = 0;
  let lastSwipeX      = null;
  let swipeCooldown   = 0;
  let pinchClickCooldown = 0;

  const PAGE_ORDER = ['dashboard', 'devices', 'policies', 'statistics', 'telemetry', 'connect'];

  // ─── DOM refs ─────────────────────────────────────────────────────────────
  const btnJarvis    = document.getElementById('btn-jarvis');
  const cursor       = document.getElementById('jarvis-cursor');
  const hud          = document.getElementById('jarvis-hud');
  const hudGesture   = document.getElementById('hud-gesture');
  const hudHint      = document.getElementById('hud-hint');
  const camBox       = document.getElementById('jarvis-cam');
  const video        = document.getElementById('jarvis-video');
  const overlayCanvas= document.getElementById('jarvis-canvas');
  const guide        = document.getElementById('jarvis-guide');
  const octx         = overlayCanvas ? overlayCanvas.getContext('2d') : null;

  // ─── Toggle ───────────────────────────────────────────────────────────────
  btnJarvis.addEventListener('click', () => {
    jarvisActive ? deactivate() : activate();
  });

  function activate() {
    jarvisActive = true;
    btnJarvis.classList.add('active');
    document.body.classList.add('jarvis-active');

    [cursor, hud, camBox, guide].forEach(el => { if (el) el.style.display = ''; });

    if (!hands) initMediaPipe();
    else startCamera();
  }

  function deactivate() {
    jarvisActive = false;
    btnJarvis.classList.remove('active');
    document.body.classList.remove('jarvis-active');

    [cursor, hud, camBox, guide].forEach(el => { if (el) el.style.display = 'none'; });

    if (camera) { try { camera.stop(); } catch(e) {} }
    // Release webcam
    if (video && video.srcObject) {
      video.srcObject.getTracks().forEach(t => t.stop());
      video.srcObject = null;
    }
  }

  // ─── MediaPipe Hands ──────────────────────────────────────────────────────
  function initMediaPipe() {
    if (typeof Hands === 'undefined') {
      showHUD('MEDIAPIPE NOT LOADED', 'Check internet connection', '');
      return;
    }

    hands = new Hands({
      locateFile: file =>
        `https://cdn.jsdelivr.net/npm/@mediapipe/hands/${file}`
    });

    hands.setOptions({
      maxNumHands: 1,
      modelComplexity: 1,
      minDetectionConfidence: 0.7,
      minTrackingConfidence: 0.6,
    });

    hands.onResults(onResults);
    startCamera();
  }

  function startCamera() {
    if (typeof Camera === 'undefined') return;
    camera = new Camera(video, {
      onFrame: async () => {
        if (jarvisActive && hands) await hands.send({ image: video });
      },
      width: 200, height: 150,
    });
    camera.start();
  }

  // ─── Landmark processing ──────────────────────────────────────────────────
  function onResults(results) {
    if (!jarvisActive) return;

    // Clear skeleton overlay
    if (octx) {
      octx.clearRect(0, 0, overlayCanvas.width, overlayCanvas.height);
    }

    if (!results.multiHandLandmarks || results.multiHandLandmarks.length === 0) {
      showHUD('SCANNING...', 'Show your hand to the camera', '');
      return;
    }

    const lm = results.multiHandLandmarks[0];
    drawSkeleton(lm);

    const gesture = classifyGesture(lm);
    handleGesture(gesture, lm);
  }

  // ─── Gesture Classification ───────────────────────────────────────────────
  function classifyGesture(lm) {
    const fingers = getFingersUp(lm);
    const [thumb, index, middle, ring, pinky] = fingers;

    // Pinch: thumb tip close to index tip
    const thumbTip = lm[4];
    const indexTip = lm[8];
    const pinchDist = dist(thumbTip, indexTip);
    if (pinchDist < 0.06) return 'pinch';

    const upCount = fingers.filter(Boolean).length;

    if (!thumb && !index && !middle && !ring && !pinky) return 'fist';
    if (thumb && index && middle && ring && pinky)      return 'open';
    if (!thumb && index && !middle && !ring && !pinky)  return 'point';
    if (!thumb && index && middle && !ring && !pinky)   return 'peace';
    if (!thumb && index && middle && ring && pinky)     return 'four';

    return 'unknown';
  }

  function getFingersUp(lm) {
    // Thumb: compare tip x vs IP x (mirrored)
    const thumb  = lm[4].x  < lm[3].x;
    // Other fingers: tip y < PIP y means up
    const index  = lm[8].y  < lm[6].y;
    const middle = lm[12].y < lm[10].y;
    const ring   = lm[16].y < lm[14].y;
    const pinky  = lm[20].y < lm[18].y;
    return [thumb, index, middle, ring, pinky];
  }

  function dist(a, b) {
    return Math.sqrt((a.x - b.x) ** 2 + (a.y - b.y) ** 2);
  }

  // ─── Gesture → Action ────────────────────────────────────────────────────
  function handleGesture(gesture, lm) {
    const now = Date.now();

    // Move virtual cursor based on index fingertip (landmark 8)
    const tip = lm[8];
    // MediaPipe coords are [0,1] normalized, mirror X because webcam is mirrored
    const screenX = (1 - tip.x) * window.innerWidth;
    const screenY = tip.y * window.innerHeight;
    moveCursor(screenX, screenY);

    // Swipe detection: track wrist (lm[0]) horizontal movement
    const wristX = lm[0].x;
    if (lastSwipeX !== null && swipeCooldown < now) {
      const dx = wristX - lastSwipeX;
      if (Math.abs(dx) > 0.18) {
        // dx positive = hand moved right on screen (mirrored = swipe left gesture)
        navigatePage(dx > 0 ? -1 : 1);
        swipeCooldown = now + 1000;
      }
    }
    lastSwipeX = wristX;

    // Per-gesture actions
    switch (gesture) {
      case 'point':
        showHUD('☝️  POINTING', 'Pinch to click', '');
        cursor.classList.remove('pinching', 'clicking');
        hudGesture.className = 'hud-gesture';
        break;

      case 'pinch':
        cursor.classList.add('pinching');
        hudGesture.className = 'hud-gesture gesture-pinch';
        showHUD('🤏  PINCH', 'Clicking...', 'gesture-pinch');
        if (pinchClickCooldown < now) {
          triggerClickAt(screenX, screenY);
          pinchClickCooldown = now + 700;
        }
        break;

      case 'peace':
        cursor.classList.remove('pinching');
        hudGesture.className = 'hud-gesture gesture-peace';
        showHUD('✌️  PEACE', 'Scrolling...', 'gesture-peace');
        window.scrollBy({ top: 60, behavior: 'smooth' });
        break;

      case 'open':
        cursor.classList.remove('pinching');
        hudGesture.className = 'hud-gesture gesture-open';
        showHUD('🖐️  OPEN HAND', 'Refreshing page...', 'gesture-open');
        if (gestureHoldMs < now) {
          document.getElementById('btn-refresh')?.click();
          gestureHoldMs = now + 2000;
        }
        break;

      case 'fist':
        cursor.classList.remove('pinching');
        hudGesture.className = 'hud-gesture gesture-fist';
        showHUD('✊  FIST', 'Deactivating Jarvis...', 'gesture-fist');
        if (gestureHoldMs < now) {
          deactivate();
          gestureHoldMs = now + 2000;
        }
        break;

      default:
        cursor.classList.remove('pinching', 'clicking');
        hudGesture.className = 'hud-gesture';
        showHUD('🤖 JARVIS ACTIVE', 'Show a gesture', '');
    }

    lastGesture = gesture;
  }

  // ─── Cursor ───────────────────────────────────────────────────────────────
  function moveCursor(x, y) {
    cursor.style.left = x + 'px';
    cursor.style.top  = y + 'px';
  }

  // ─── Fake click at coords ─────────────────────────────────────────────────
  function triggerClickAt(x, y) {
    cursor.classList.add('clicking');
    setTimeout(() => cursor.classList.remove('clicking'), 200);

    const el = document.elementFromPoint(x, y);
    if (el) {
      el.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, clientX: x, clientY: y }));
    }
  }

  // ─── Page navigation ──────────────────────────────────────────────────────
  function navigatePage(direction) {
    const activeNav = document.querySelector('.nav-item.active');
    if (!activeNav) return;
    const curPage = activeNav.dataset.page;
    const curIdx  = PAGE_ORDER.indexOf(curPage);
    const nextIdx = (curIdx + direction + PAGE_ORDER.length) % PAGE_ORDER.length;
    const nextNav = document.querySelector(`.nav-item[data-page="${PAGE_ORDER[nextIdx]}"]`);
    if (nextNav) nextNav.click();
  }

  // ─── HUD update ───────────────────────────────────────────────────────────
  function showHUD(gestureText, hintText, cls) {
    hudGesture.textContent = gestureText;
    hudHint.textContent    = hintText;
    if (cls) hudGesture.className = 'hud-gesture ' + cls;
  }

  // ─── Draw hand skeleton on overlay canvas ────────────────────────────────
  function drawSkeleton(lm) {
    if (!octx || !overlayCanvas) return;
    const W = overlayCanvas.width  = overlayCanvas.offsetWidth;
    const H = overlayCanvas.height = overlayCanvas.offsetHeight;

    const CONNECTIONS = [
      [0,1],[1,2],[2,3],[3,4],     // thumb
      [0,5],[5,6],[6,7],[7,8],     // index
      [0,9],[9,10],[10,11],[11,12],// middle
      [0,13],[13,14],[14,15],[15,16],// ring
      [0,17],[17,18],[18,19],[19,20],// pinky
      [5,9],[9,13],[13,17]         // palm
    ];

    octx.clearRect(0, 0, W, H);
    octx.strokeStyle = 'rgba(0, 243, 255, 0.7)';
    octx.lineWidth = 1.5;
    octx.shadowBlur = 6;
    octx.shadowColor = '#00f3ff';

    CONNECTIONS.forEach(([a, b]) => {
      octx.beginPath();
      // Mirror X (canvas is also CSS-mirrored, so coords are already mirrored back)
      octx.moveTo(lm[a].x * W, lm[a].y * H);
      octx.lineTo(lm[b].x * W, lm[b].y * H);
      octx.stroke();
    });

    // Draw landmark dots
    lm.forEach((pt, i) => {
      octx.beginPath();
      octx.arc(pt.x * W, pt.y * H, i === 8 ? 4 : 2, 0, Math.PI * 2);
      octx.fillStyle = i === 8 ? '#ff2d9b' : 'rgba(0, 243, 255, 0.9)';
      octx.shadowColor = i === 8 ? '#ff2d9b' : '#00f3ff';
      octx.shadowBlur  = i === 8 ? 10 : 4;
      octx.fill();
    });
  }

})();

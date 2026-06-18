// app.js — QoS Scheduler Web Admin Panel Frontend Logic

const API = '';  // same origin

// ─────────────────────────────────────────────
// Routing
// ─────────────────────────────────────────────
const pages = document.querySelectorAll('.page');
const navItems = document.querySelectorAll('.nav-item');

const pageMeta = {
  dashboard: { title: 'Dashboard',    sub: 'Real-time network overview' },
  devices:   { title: 'Devices',      sub: 'Manage connected Android devices' },
  policies:  { title: 'QoS Policies', sub: 'Configure per-app bandwidth rules' },
  telemetry:  { title: 'Telemetry',    sub: 'App traffic statistics' },
  statistics: { title: 'QoS Statistics', sub: 'Validation charts for QoS theories' },
  connect:    { title: 'Connect App',  sub: 'Pair Android device via QR code' },
};

function showPage(name) {
  pages.forEach(p => p.classList.toggle('active', p.id === `page-${name}`));
  navItems.forEach(n => n.classList.toggle('active', n.dataset.page === name));
  document.getElementById('page-title').textContent = pageMeta[name].title;
  document.getElementById('page-sub').textContent   = pageMeta[name].sub;

  if (name === 'dashboard')  loadDashboard();
  if (name === 'devices')    loadDevices();
  if (name === 'policies')   loadPolicies();
  if (name === 'telemetry')  loadTelemetry();
  if (name === 'statistics') loadStatistics();
  if (name === 'connect')    loadConnect();
}

navItems.forEach(n => n.addEventListener('click', e => {
  e.preventDefault();
  showPage(n.dataset.page);
}));

// ─────────────────────────────────────────────
// Clock
// ─────────────────────────────────────────────
function updateClock() {
  document.getElementById('topbar-time').textContent =
    new Date().toLocaleTimeString('en-GB');
}
setInterval(updateClock, 1000);
updateClock();

// ─────────────────────────────────────────────
// Server health check
// ─────────────────────────────────────────────
async function checkHealth() {
  const dot  = document.getElementById('status-dot');
  const text = document.getElementById('status-text');
  try {
    await fetch(`${API}/api/info`);
    dot.className  = 'status-dot online';
    text.textContent = 'Server Online';
  } catch {
    dot.className  = 'status-dot offline';
    text.textContent = 'Server Offline';
  }
}
checkHealth();
setInterval(checkHealth, 30_000);

// ─────────────────────────────────────────────
// Utils
// ─────────────────────────────────────────────
function fmtBytes(b) {
  if (b === 0) return '0 B';
  const k = 1024;
  const units = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(b) / Math.log(k));
  return `${(b / Math.pow(k, i)).toFixed(1)} ${units[i]}`;
}

function fmtTime(ts) {
  return new Date(ts * 1000).toLocaleString('en-GB');
}

function timeAgo(ts) {
  const diff = Math.floor(Date.now() / 1000) - ts;
  if (diff < 60)   return `${diff}s ago`;
  if (diff < 3600) return `${Math.floor(diff/60)}m ago`;
  return `${Math.floor(diff/3600)}h ago`;
}

// ─────────────────────────────────────────────
// Neon Glow Plugin for Chart.js
// ─────────────────────────────────────────────
const neonGlowPlugin = {
  id: 'neonGlow',
  beforeDatasetsDraw(chart) {
    const ctx = chart.ctx;
    ctx.save();
    // Pick glow color from first dataset border color, fallback cyan
    const ds = chart.data.datasets[0];
    const glowColor = (ds && ds.borderColor && typeof ds.borderColor === 'string')
      ? ds.borderColor : '#00f3ff';
    ctx.shadowBlur   = 18;
    ctx.shadowColor  = glowColor;
  },
  afterDatasetsDraw(chart) {
    chart.ctx.restore();
  }
};
Chart.register(neonGlowPlugin);

// ─────────────────────────────────────────────
// Charts
// ─────────────────────────────────────────────
let timeseriesChart = null;
let appsChart = null;

function initCharts() {
  // Shared dark theme defaults for Chart.js
  const darkDefaults = {
    color: '#7a8baa',
    borderColor: 'rgba(0,243,255,0.08)',
  };
  Chart.defaults.color = darkDefaults.color;
  Chart.defaults.borderColor = darkDefaults.borderColor;

  const gradCyan = (ctx) => {
    const g = ctx.createLinearGradient(0, 0, 0, 220);
    g.addColorStop(0, 'rgba(0,243,255,0.22)');
    g.addColorStop(1, 'rgba(0,243,255,0)');
    return g;
  };

  const tsCtx = document.getElementById('chart-timeseries').getContext('2d');
  timeseriesChart = new Chart(tsCtx, {
    type: 'line',
    data: {
      labels: [],
      datasets: [{
        label: 'Total Traffic',
        data: [],
        borderColor: '#00f3ff',
        backgroundColor: (ctx) => gradCyan(ctx.chart.ctx),
        fill: true,
        tension: 0.4,
        borderWidth: 2,
        pointRadius: 0,
        pointHoverRadius: 4,
        pointHoverBackgroundColor: '#00f3ff',
      }]
    },
    options: {
      responsive: true,
      plugins: { legend: { display: false } },
      scales: {
        x: {
          grid: { color: 'rgba(0,243,255,0.06)' },
          ticks: { maxTicksLimit: 8, font: { size: 11 }, color: '#4a5568' }
        },
        y: {
          grid: { color: 'rgba(0,243,255,0.06)' },
          ticks: { font: { size: 11 }, color: '#4a5568', callback: v => fmtBytes(v) }
        }
      },
      animation: { duration: 0 }
    }
  });

  const apCtx = document.getElementById('chart-apps').getContext('2d');
  appsChart = new Chart(apCtx, {
    type: 'doughnut',
    data: { labels: [], datasets: [{ data: [], backgroundColor: [], borderWidth: 1, borderColor: 'rgba(0,243,255,0.1)' }] },
    options: {
      responsive: true,
      plugins: {
        legend: { position: 'bottom', labels: { font: { size: 11 }, padding: 10, color: '#7a8baa' } },
        tooltip: { callbacks: { label: ctx => ` ${ctx.label}: ${fmtBytes(ctx.raw)}` } }
      },
      cutout: '62%',
      animation: { duration: 0 }
    }
  });

  // QoS Statistics Charts
  const gradPink = (ctx) => {
    const g = ctx.createLinearGradient(0, 0, 0, 200);
    g.addColorStop(0, 'rgba(255,45,155,0.28)');
    g.addColorStop(1, 'rgba(255,45,155,0)');
    return g;
  };

  const scalesDark = {
    x: { grid: { color: 'rgba(0,243,255,0.06)' }, ticks: { color: '#4a5568', font: { size: 11 } } },
    y: { grid: { color: 'rgba(0,243,255,0.06)' }, ticks: { color: '#4a5568', font: { size: 11 } } }
  };

  const createTbChart = (id) => {
    if (!document.getElementById(id)) return null;
    return new Chart(document.getElementById(id).getContext('2d'), {
      type: 'line',
      data: { labels: [], datasets: [
        { label: 'Requested BPS', data: [], borderColor: '#ff2d9b', backgroundColor: (ctx) => gradPink(ctx.chart.ctx), fill: true, tension: 0.3, borderWidth: 2, pointRadius: 0 },
        { label: 'Allowed BPS', data: [], borderColor: '#00f3ff', borderDash: [5, 4], fill: false, tension: 0.3, borderWidth: 2, pointRadius: 0 }
      ]},
      options: {
        responsive: true,
        plugins: { legend: { position: 'bottom', labels: { color: '#7a8baa', font: { size: 11 } } } },
        scales: { ...scalesDark, y: { ...scalesDark.y, beginAtZero: true, ticks: { ...scalesDark.y.ticks, callback: v => fmtBytes(v/8)+'/s' } } },
        animation: { duration: 0 }
      }
    });
  };
  window.tokenBucketTotalChart = createTbChart('chart-token-bucket-total');
  
  if (document.getElementById('chart-token-bucket-top')) {
    window.tokenBucketTopChart = new Chart(document.getElementById('chart-token-bucket-top').getContext('2d'), {
      type: 'bar',
      data: { labels: [], datasets: [
        { label: 'Requested BPS', data: [], backgroundColor: 'rgba(255,45,155,0.8)', borderWidth: 0 },
        { label: 'Allowed BPS', data: [], backgroundColor: 'rgba(0,243,255,0.8)', borderWidth: 0 }
      ]},
      options: {
        responsive: true,
        plugins: { legend: { position: 'bottom', labels: { color: '#7a8baa', font: { size: 11 } } } },
        scales: { ...scalesDark, y: { ...scalesDark.y, beginAtZero: true, ticks: { ...scalesDark.y.ticks, callback: v => fmtBytes(v/8)+'/s' } } },
        animation: { duration: 500 }
      }
    });
  }

  if (document.getElementById('chart-wfq-pie')) {
    window.wfqPieChart = new Chart(document.getElementById('chart-wfq-pie').getContext('2d'), {
      type: 'doughnut',
      data: {
        labels: ['HIGH (Weight 4)', 'MEDIUM (Weight 2)', 'LOW (Weight 1)'],
        datasets: [{ data: [0,0,0], backgroundColor: ['rgba(239,68,68,0.7)', 'rgba(245,158,11,0.7)', 'rgba(0,243,255,0.7)'], borderWidth: 1, borderColor: 'rgba(0,0,0,0.3)' }]
      },
      options: {
        responsive: true,
        plugins: { legend: { position: 'bottom', labels: { color: '#7a8baa', font: { size: 11 } } } },
        cutout: '52%',
        animation: { duration: 500 }
      }
    });
  }

  if (document.getElementById('chart-drop-rate')) {
    window.dropRateChart = new Chart(document.getElementById('chart-drop-rate').getContext('2d'), {
      type: 'line',
      data: { labels: [], datasets: [] },
      options: {
        responsive: true,
        plugins: { legend: { position: 'bottom', labels: { color: '#7a8baa', font: { size: 11 } } } },
        scales: { ...scalesDark, y: { ...scalesDark.y, min: 0, max: 100, ticks: { ...scalesDark.y.ticks, callback: v => v+'%' } } },
        animation: { duration: 0 }
      }
    });
  }
}

const PALETTE = [
  '#00f3ff','#ff2d9b','#8b5cf6','#10f59e',
  '#f59e0b','#ef4444','#06b6d4','#a78bfa'
];

// ─────────────────────────────────────────────
// Dashboard
// ─────────────────────────────────────────────
async function loadDashboard() {
  try {
    const [stats, timeseries, summary] = await Promise.all([
      fetch(`${API}/api/stats`).then(r => r.json()),
      fetch(`${API}/api/telemetry/timeseries?minutes=5`).then(r => r.json()),
      fetch(`${API}/api/telemetry/summary?minutes=2`).then(r => r.json()),
    ]);

    // Stat cards
    document.getElementById('stat-total-devices').textContent  = stats.total_devices;
    document.getElementById('stat-online-devices').textContent = stats.online_devices;
    document.getElementById('stat-total-policies').textContent = stats.total_policies;
    document.getElementById('stat-total-data').textContent     = stats.total_data_mb;

    // Timeseries chart
    if (timeseriesChart) {
      timeseriesChart.data.labels   = timeseries.map(r => {
        const d = new Date(r.bucket * 1000);
        return `${d.getHours()}:${String(d.getMinutes()).padStart(2,'0')}`;
      });
      timeseriesChart.data.datasets[0].data = timeseries.map(r => r.total_bytes);
      timeseriesChart.update();
    }

    // Apps doughnut
    if (appsChart && summary.length > 0) {
      appsChart.data.labels = summary.map(r => r.app_name || r.package_name);
      appsChart.data.datasets[0].data  = summary.map(r => r.total_bytes_in + r.total_bytes_out);
      appsChart.data.datasets[0].backgroundColor = summary.map((_, i) => PALETTE[i % PALETTE.length]);
      appsChart.update();
    }
  } catch (err) {
    console.warn('Dashboard load error:', err);
  }
}

// ─────────────────────────────────────────────
// Devices
// ─────────────────────────────────────────────
async function loadDevices() {
  const tbody = document.getElementById('devices-tbody');
  try {
    const devices = await fetch(`${API}/api/devices`).then(r => r.json());
    document.getElementById('devices-count').textContent = devices.length;

    if (devices.length === 0) {
      tbody.innerHTML = `<tr><td colspan="7" class="empty-row">No devices registered yet. Connect a device using the QR Code.</td></tr>`;
      return;
    }

    tbody.innerHTML = devices.map(d => `
      <tr>
        <td><code style="font-size:11px;color:#6b7280">${d.id.substring(0,12)}…</code></td>
        <td><strong>${d.name}</strong></td>
        <td>${d.model}</td>
        <td>${d.ip || '—'}</td>
        <td>${timeAgo(d.last_seen)}</td>
        <td><span class="priority-pill ${d.is_online ? 'online-pill' : 'offline-pill'}">${d.is_online ? '🟢 Online' : '⚫ Offline'}</span></td>
        <td>
          <button class="btn btn-danger btn-sm" onclick="deleteDevice('${d.id}')">🗑 Remove</button>
        </td>
      </tr>
    `).join('');
  } catch { tbody.innerHTML = `<tr><td colspan="7" class="empty-row">Failed to load devices.</td></tr>`; }
}

async function deleteDevice(id) {
  if (!confirm('Remove this device?')) return;
  await fetch(`${API}/api/devices/${id}`, { method: 'DELETE' });
  loadDevices();
}

// ─────────────────────────────────────────────
// Policies
// ─────────────────────────────────────────────
async function loadPolicies() {
  const tbody = document.getElementById('policies-tbody');
  const select = document.getElementById('pol-device');

  try {
    const [policies, devices] = await Promise.all([
      fetch(`${API}/api/policies`).then(r => r.json()),
      fetch(`${API}/api/devices`).then(r => r.json()),
    ]);

    document.getElementById('policies-count').textContent = policies.length;

    // Repopulate device selector
    select.innerHTML = `<option value="__all__">📡 All Devices</option>` +
      devices.map(d => `<option value="${d.id}">${d.name}</option>`).join('');

    if (policies.length === 0) {
      tbody.innerHTML = `<tr><td colspan="7" class="empty-row">No policies created yet.</td></tr>`;
      return;
    }

    tbody.innerHTML = policies.map(p => `
      <tr>
        <td><strong>${p.app_name}</strong></td>
        <td><code style="font-size:11px;color:#6b7280">${p.package_name}</code></td>
        <td><span class="priority-pill priority-${p.priority}">${p.priority}</span></td>
        <td>${p.max_bps > 0 ? `${(p.max_bps/1000).toFixed(0)} Kbps` : 'Auto (WFQ)'}</td>
        <td>${p.device_id === '__all__' ? '📡 All Devices' : p.device_id.substring(0,10)+'…'}</td>
        <td>${fmtTime(p.created_at)}</td>
        <td>
          <button class="btn btn-danger btn-sm" onclick="deletePolicy(${p.id})">🗑 Remove</button>
        </td>
      </tr>
    `).join('');
  } catch { tbody.innerHTML = `<tr><td colspan="7" class="empty-row">Failed to load policies.</td></tr>`; }
}

document.getElementById('btn-add-policy').addEventListener('click', async () => {
  const body = {
    app_name:     document.getElementById('pol-app-name').value.trim(),
    package_name: document.getElementById('pol-package').value.trim(),
    priority:     document.getElementById('pol-priority').value,
    max_bps:      parseInt(document.getElementById('pol-max-bps').value) * 1000,
    device_id:    document.getElementById('pol-device').value,
  };
  if (!body.app_name || !body.package_name) {
    alert('Please fill in App Name and Package Name.');
    return;
  }
  const btn = document.getElementById('btn-add-policy');
  btn.textContent = 'Applying…';
  await fetch(`${API}/api/policies`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  btn.textContent = '✓ Applied!';
  setTimeout(() => { btn.textContent = 'Apply Policy'; }, 2000);
  loadPolicies();
});

async function deletePolicy(id) {
  if (!confirm('Remove this policy?')) return;
  await fetch(`${API}/api/policies/${id}`, { method: 'DELETE' });
  loadPolicies();
}

// ─────────────────────────────────────────────
// Telemetry
// ─────────────────────────────────────────────
async function loadTelemetry() {
  const tbody = document.getElementById('telemetry-tbody');
  try {
    const rows = await fetch(`${API}/api/telemetry/summary?minutes=10`).then(r => r.json());
    if (rows.length === 0) {
      tbody.innerHTML = `<tr><td colspan="6" class="empty-row">No telemetry data yet. Make sure the Android app is connected and sending data.</td></tr>`;
      return;
    }
    tbody.innerHTML = rows.map(r => `
      <tr>
        <td><strong>${r.app_name}</strong></td>
        <td><code style="font-size:11px;color:#6b7280">${r.package_name}</code></td>
        <td>${fmtBytes(r.total_bytes_in)}</td>
        <td>${fmtBytes(r.total_bytes_out)}</td>
        <td><strong>${fmtBytes(r.total_bytes_in + r.total_bytes_out)}</strong></td>
        <td>${r.device_count} device(s)</td>
      </tr>
    `).join('');
  } catch { tbody.innerHTML = `<tr><td colspan="6" class="empty-row">Failed to load telemetry.</td></tr>`; }
}

document.getElementById('btn-refresh-telemetry').addEventListener('click', loadTelemetry);

// ─────────────────────────────────────────────
// Statistics (Live QoS)
// ─────────────────────────────────────────────
let liveStatsInterval = null;

async function loadStatistics() {
  if (liveStatsInterval) clearInterval(liveStatsInterval);
  
  const fetchAndRender = async () => {
    try {
      const data = await fetch(`${API}/api/statistics/live`).then(r => r.json());
      const history = data.history;
      
      // Update top stat cards
      document.getElementById('stat-tcp-flows').textContent = data.flows.tcp;
      document.getElementById('stat-udp-flows').textContent = data.flows.udp;

      // Group by timestamp for timeline charts
      const timeMap = new Map();
      let totalRequested = 0, totalAllowed = 0, totalDropped = 0;
      let wfqBps = { HIGH: 0, MEDIUM: 0, LOW: 0 };
      
      // We will identify the top app to plot on the Token Bucket chart
      const appTotals = new Map();
      const appSeries = new Map();

      history.forEach(row => {
        const t = row.timestamp;
        if (!timeMap.has(t)) timeMap.set(t, { requested: 0, allowed: 0, dropped: 0 });
        const tsData = timeMap.get(t);
        tsData.requested += row.requested_bps;
        tsData.allowed += row.allowed_bps;
        tsData.dropped += row.dropped_pkts;
        
        totalRequested += row.requested_bps;
        totalAllowed += row.allowed_bps;
        totalDropped += row.dropped_pkts;

        if (wfqBps[row.priority] !== undefined) wfqBps[row.priority] += row.allowed_bps;
        
        const appKey = row.package_name;
        if (!appTotals.has(appKey)) appTotals.set(appKey, { name: row.package_name, req: 0, allow: 0 });
        const at = appTotals.get(appKey);
        at.req += row.requested_bps;
        at.allow += row.allowed_bps;

        if (!appSeries.has(appKey)) appSeries.set(appKey, new Map());
        const series = appSeries.get(appKey);
        if (!series.has(t)) series.set(t, { requested: 0, allowed: 0 });
        series.get(t).requested += row.requested_bps;
        series.get(t).allowed += row.allowed_bps;
      });

      // Find top 5 apps by requested bytes
      const topApps = Array.from(appTotals.entries())
        .filter(([key]) => key !== '__host__')
        .sort((a, b) => b[1].req - a[1].req)
        .slice(0, 5)
        .map(x => x[0]);

      // Calculate Global Drop Rate
      // CORRECT FORMULA: Both requested_bps and allowed_bps are in the same unit (bits/sec)
      // Drop Rate = (requested - allowed) / requested * 100
      // This avoids the unit mismatch of comparing dropped_pkts (cumulative count) vs bps (rate)
      const globalDropRate = totalRequested > 0
        ? Math.min(Math.max((totalRequested - totalAllowed) / totalRequested * 100, 0), 100)
        : 0;

      const dropRateEl = document.getElementById('stat-drop-rate');
      dropRateEl.textContent = globalDropRate.toFixed(1) + '%';
      if (globalDropRate > 5) dropRateEl.parentElement.parentElement.className = 'stat-card card-red';
      else dropRateEl.parentElement.parentElement.className = 'stat-card card-pink';

      // Sort timeline
      const times = Array.from(timeMap.keys()).sort();

      // Format timestamp for X-axis: Unix seconds → HH:MM:SS
      const fmtTimestamp = (unixSec) => {
        const d = new Date(unixSec * 1000);
        return `${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}:${String(d.getSeconds()).padStart(2,'0')}`;
      };
      const timeLabels = times.map(fmtTimestamp);

      // 1. Token Bucket Chart (Total vs Top App)
      if (window.tokenBucketTotalChart) {
        window.tokenBucketTotalChart.data.labels = timeLabels;
        window.tokenBucketTotalChart.data.datasets[0].data = times.map(t => timeMap.get(t).requested);
        window.tokenBucketTotalChart.data.datasets[1].data = times.map(t => timeMap.get(t).allowed);
        window.tokenBucketTotalChart.update();
      }

      if (window.tokenBucketTopChart) {
        const latestTime = times.length > 0 ? times[times.length - 1] : 0;
        
        window.tokenBucketTopChart.data.labels = topApps.map(app => app.split('.').pop());
        
        const reqData = topApps.map(appKey => {
            const series = appSeries.get(appKey);
            return series.has(latestTime) ? series.get(latestTime).requested : 0;
        });
        
        const allowData = topApps.map(appKey => {
            const series = appSeries.get(appKey);
            return series.has(latestTime) ? series.get(latestTime).allowed : 0;
        });

        window.tokenBucketTopChart.data.datasets[0].data = reqData;
        window.tokenBucketTopChart.data.datasets[1].data = allowData;
        window.tokenBucketTopChart.update();
      }

      // 2. WFQ Pie Chart
      if (window.wfqPieChart) {
        window.wfqPieChart.data.datasets[0].data = [wfqBps.HIGH, wfqBps.MEDIUM, wfqBps.LOW];
        window.wfqPieChart.update();
      }

      // 3. Drop Rate Timeline
      if (window.dropRateChart) {
        window.dropRateChart.data.labels = timeLabels;
        window.dropRateChart.data.datasets = topApps.map((appKey, i) => {
           const topSeries = appSeries.get(appKey);
           return {
             label: appKey.split('.').pop(),
             data: times.map(t => {
               if (!topSeries.has(t)) return 0;
               const d = topSeries.get(t);
               if (d.requested <= 0) return 0;
               return Math.min(Math.max((d.requested - d.allowed) / d.requested * 100, 0), 100);
             }),
             borderColor: PALETTE[i % PALETTE.length],
             fill: false, tension: 0.3, borderWidth: 2, pointRadius: 0
           };
        });
        window.dropRateChart.update();
      }

      // 4. Flow Health Table (Grouped by latest apps)
      const healthTbody = document.getElementById('qos-health-tbody');
      const latestApps = new Map();
      history.forEach(r => {
        if (!latestApps.has(r.package_name)) latestApps.set(r.package_name, r);
      });
      
      if (latestApps.size === 0) {
         healthTbody.innerHTML = `<tr><td colspan="5" class="empty-row">No live flows</td></tr>`;
      } else {
         healthTbody.innerHTML = Array.from(latestApps.values()).map(app => {
           // Calculate drop rate for this app
           const pAllow = app.allowed_bps / (1000 * 8);
           const pTotal = pAllow + app.dropped_pkts;
           const dRate = pTotal > 0 ? (app.dropped_pkts / pTotal) * 100 : 0;
           
           return `
             <tr>
               <td><strong>${app.package_name.split('.').pop()}</strong></td>
               <td><span class="priority-pill priority-${app.priority}">${app.priority}</span></td>
               <td>${fmtBytes(app.requested_bps/8)}/s</td>
               <td>${fmtBytes(app.allowed_bps/8)}/s</td>
               <td><span class="badge ${dRate > 0 ? 'badge-red' : 'badge-green'}">${app.dropped_pkts} pkts (${dRate.toFixed(1)}%)</span></td>
             </tr>
           `;
         }).join('');
      }

    } catch (err) { console.warn('Statistics update error', err); }
  };

  await fetchAndRender();
  liveStatsInterval = setInterval(fetchAndRender, 1000); // 1s live update
}

// Ensure interval is cleared when leaving page
navItems.forEach(n => n.addEventListener('click', () => {
  if (n.dataset.page !== 'statistics' && liveStatsInterval) {
    clearInterval(liveStatsInterval);
    liveStatsInterval = null;
  }
}));

// ─────────────────────────────────────────────
// Connect / QR Code
// ─────────────────────────────────────────────
let qrGenerated = false;

async function loadConnect() {
  if (qrGenerated) return;
  try {
    const info = await fetch(`${API}/api/info`).then(r => r.json());
    const url  = `http://${info.ip}:${info.port}`;
    const payload = JSON.stringify({ server: url, version: info.version });

    document.getElementById('connect-ip').textContent   = info.ip;
    document.getElementById('connect-port').textContent = info.port;
    document.getElementById('connect-url').textContent  = `${url}/api`;

    document.getElementById('qr-shimmer').classList.add('hidden');
    document.getElementById('qr-code').innerHTML = '';

    new QRCode(document.getElementById('qr-code'), {
      text:   payload,
      width:  192,
      height: 192,
      colorDark:  '#1e3a8a',
      colorLight: '#ffffff',
      correctLevel: QRCode.CorrectLevel.M,
    });

    qrGenerated = true;
  } catch (err) {
    document.getElementById('qr-shimmer').textContent = 'Failed to load server info';
    console.error(err);
  }
}

document.getElementById('btn-regenerate-qr').addEventListener('click', () => {
  qrGenerated = false;
  document.getElementById('qr-shimmer').classList.remove('hidden');
  document.getElementById('qr-code').innerHTML = '';
  loadConnect();
});

// ─────────────────────────────────────────────
// Global refresh button
// ─────────────────────────────────────────────
document.getElementById('btn-refresh').addEventListener('click', () => {
  const active = document.querySelector('.nav-item.active');
  if (active) showPage(active.dataset.page);
});

// ─────────────────────────────────────────────
// Init
// ─────────────────────────────────────────────
initCharts();
showPage('dashboard');

// Auto-refresh dashboard every 1 second for real-time feel
setInterval(() => {
  const active = document.querySelector('.nav-item.active');
  if (active?.dataset.page === 'dashboard') loadDashboard();
}, 1000);

// Expose for inline onclick handlers
window.deleteDevice = deleteDevice;
window.deletePolicy = deletePolicy;

// server.js — QoS Scheduler Cloud Management Backend
const express = require('express');
const cors    = require('cors');
const path    = require('path');
const os      = require('os');

const app  = express();
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

// ─────────────────────────────────────────────
// Helper: LAN IP
// ─────────────────────────────────────────────
function getLanIp() {
  const ifaces = os.networkInterfaces();
  for (const name of Object.keys(ifaces)) {
    for (const iface of ifaces[name]) {
      if (iface.family === 'IPv4' && !iface.internal) return iface.address;
    }
  }
  return '127.0.0.1';
}

// ─────────────────────────────────────────────
// Bootstrap: init DB then start server
// ─────────────────────────────────────────────
const { getDb, run, all, get } = require('./database');

getDb().then(db => {
  console.log('✓ Database ready');

  // ── API: Server Info ──
  app.get('/api/info', (req, res) => {
    res.json({ ip: getLanIp(), port: PORT, version: '1.0.0', name: 'QoS Scheduler Server' });
  });

  // ── API: Devices ──
  app.post('/api/devices', (req, res) => {
    const { id, name, model, ip } = req.body;
    if (!id || !name) return res.status(400).json({ error: 'id and name required' });
    run(`INSERT INTO devices (id,name,model,ip,last_seen,is_online)
         VALUES (?,?,?,?,strftime('%s','now'),1)
         ON CONFLICT(id) DO UPDATE SET
           name=excluded.name, model=excluded.model, ip=excluded.ip,
           last_seen=strftime('%s','now'), is_online=1`,
      [id, name, model || 'Unknown', ip || req.ip]);
    res.json({ success: true });
  });

  app.get('/api/devices', (req, res) => {
    run(`UPDATE devices SET is_online=0 WHERE (strftime('%s','now')-last_seen)>90`);
    res.json(all('SELECT * FROM devices ORDER BY last_seen DESC'));
  });

  app.delete('/api/devices/:id', (req, res) => {
    run('DELETE FROM devices WHERE id=?', [req.params.id]);
    res.json({ success: true });
  });

  // ── API: Policies ──
  app.get('/api/policies', (req, res) => {
    const { device_id } = req.query;
    if (device_id) {
      res.json(all(
        `SELECT * FROM policies WHERE device_id='__all__' OR device_id=? ORDER BY created_at DESC`,
        [device_id]
      ));
    } else {
      res.json(all('SELECT * FROM policies ORDER BY created_at DESC'));
    }
  });

  app.post('/api/policies', (req, res) => {
    const { package_name, app_name, priority, max_bps, device_id } = req.body;
    if (!package_name || !app_name) return res.status(400).json({ error: 'package_name and app_name required' });
    const devId = device_id || '__all__';
    run(`INSERT INTO policies (package_name,app_name,priority,max_bps,device_id)
         VALUES (?,?,?,?,?)
         ON CONFLICT(package_name,device_id) DO UPDATE SET
           app_name=excluded.app_name, priority=excluded.priority,
           max_bps=excluded.max_bps, created_at=strftime('%s','now')`,
      [package_name, app_name, priority || 'MEDIUM', max_bps || 0, devId]);
    res.json({ success: true });
  });

  app.delete('/api/policies/:id', (req, res) => {
    run('DELETE FROM policies WHERE id=?', [req.params.id]);
    res.json({ success: true });
  });

  // ── API: Telemetry ──
  app.post('/api/telemetry', (req, res) => {
    const { device_id, entries } = req.body;
    if (!device_id || !Array.isArray(entries)) {
      return res.status(400).json({ error: 'device_id and entries[] required' });
    }
    for (const e of entries) {
      run(`INSERT INTO telemetry (device_id,package_name,app_name,bytes_in,bytes_out)
           VALUES (?,?,?,?,?)`,
        [device_id, e.package_name, e.app_name, e.bytes_in || 0, e.bytes_out || 0]);

      // Save to qos_validation table if it has extended telemetry
      if (e.requested_bps !== undefined) {
        run(`INSERT INTO qos_validation (device_id,package_name,priority,requested_bps,allowed_bps,dropped_pkts,tcp_flows,udp_flows)
             VALUES (?,?,?,?,?,?,?,?)`,
          [device_id, e.package_name, e.priority || 'MEDIUM', e.requested_bps || 0, e.allowed_bps || 0, e.dropped_pkts || 0, e.tcp_flows || 0, e.udp_flows || 0]);
      }
    }
    res.json({ success: true, count: entries.length });
  });

  app.get('/api/telemetry/summary', (req, res) => {
    const minutes = parseInt(req.query.minutes) || 10;
    const since   = Math.floor(Date.now() / 1000) - minutes * 60;
    res.json(all(`
      SELECT package_name, app_name,
        SUM(bytes_in)  AS total_bytes_in,
        SUM(bytes_out) AS total_bytes_out,
        COUNT(DISTINCT device_id) AS device_count,
        MAX(timestamp) AS last_seen
      FROM telemetry WHERE timestamp >= ?
      GROUP BY package_name ORDER BY (total_bytes_in+total_bytes_out) DESC LIMIT 20
    `, [since]));
  });

  app.get('/api/telemetry/timeseries', (req, res) => {
    const minutes = parseInt(req.query.minutes) || 30;
    const since   = Math.floor(Date.now() / 1000) - minutes * 60;
    res.json(all(`
      SELECT (timestamp/60)*60 AS bucket, SUM(bytes_in+bytes_out) AS total_bytes
      FROM telemetry WHERE timestamp >= ?
      GROUP BY bucket ORDER BY bucket ASC
    `, [since]));
  });

  // ── API: Statistics (QoS Validation Live Data) ──
  app.get('/api/statistics/live', (req, res) => {
    // Only fetch data from the last 5 minutes to keep it live
    const since = Math.floor(Date.now() / 1000) - 5 * 60;
    
    // 1. Get recent validation entries
    const entries = all(`
      SELECT package_name, priority, requested_bps, allowed_bps, dropped_pkts, timestamp
      FROM qos_validation
      WHERE timestamp >= ?
      ORDER BY timestamp DESC
      LIMIT 100
    `, [since]);

    // 2. Aggregate active flows (sum of latest flow counts per device)
    const flows = get(`
      SELECT SUM(tcp_flows) as tcp, SUM(udp_flows) as udp
      FROM (
        SELECT tcp_flows, udp_flows
        FROM qos_validation
        WHERE timestamp >= ?
        GROUP BY device_id
        HAVING MAX(timestamp)
      )
    `, [since]);

    res.json({
      history: entries.reverse(), // Send oldest first for charts
      flows: { tcp: flows?.tcp || 0, udp: flows?.udp || 0 }
    });
  });

  // ── API: Stats ──
  app.get('/api/stats', (req, res) => {
    run(`UPDATE devices SET is_online=0 WHERE (strftime('%s','now')-last_seen)>90`);
    res.json({
      total_devices:  get('SELECT COUNT(*) AS c FROM devices').c,
      online_devices: get('SELECT COUNT(*) AS c FROM devices WHERE is_online=1').c,
      total_policies: get('SELECT COUNT(*) AS c FROM policies').c,
      total_data_mb:  (get('SELECT COALESCE(SUM(bytes_in+bytes_out),0) AS s FROM telemetry').s / 1048576).toFixed(1),
    });
  });

  // ── Start ──
  app.listen(PORT, '0.0.0.0', () => {
    const ip = getLanIp();
    console.log('\n╔══════════════════════════════════════════╗');
    console.log('║     QoS Scheduler Management Server     ║');
    console.log('╠══════════════════════════════════════════╣');
    console.log(`║  Web Admin : http://${ip}:${PORT}           `);
    console.log(`║  API Base  : http://${ip}:${PORT}/api       `);
    console.log('╚══════════════════════════════════════════╝\n');
  });

  // Periodic pruning of old telemetry & QoS validation data (older than 1 hour)
  setInterval(() => {
    try {
      const cutoff = Math.floor(Date.now() / 1000) - 3600; // 1 hour ago in seconds
      run(`DELETE FROM telemetry WHERE timestamp < ?`, [cutoff]);
      run(`DELETE FROM qos_validation WHERE timestamp < ?`, [cutoff]);
      console.log('[DB] Pruned telemetry & QoS data older than 1 hour');
    } catch (err) {
      console.error('Pruning error:', err);
    }
  }, 5 * 60 * 1000); // every 5 minutes

}).catch(err => {
  console.error('Failed to init database:', err);
  process.exit(1);
});

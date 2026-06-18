// database.js — SQLite via sql.js (pure JS, no native build needed)
const initSqlJs = require('sql.js');
const fs        = require('fs');
const path      = require('path');

const DB_PATH = path.join(__dirname, 'qos_scheduler.db');

let db = null;
let dbDirty = false; // dirty flag for throttled background saving

async function getDb() {
  if (db) return db;

  const SQL = await initSqlJs();

  // Load existing DB file or create new one
  if (fs.existsSync(DB_PATH)) {
    const fileBuffer = fs.readFileSync(DB_PATH);
    db = new SQL.Database(fileBuffer);
  } else {
    db = new SQL.Database();
  }

  // Create schema
  db.run(`
    CREATE TABLE IF NOT EXISTS devices (
      id          TEXT PRIMARY KEY,
      name        TEXT NOT NULL,
      model       TEXT DEFAULT 'Unknown',
      ip          TEXT,
      last_seen   INTEGER DEFAULT (strftime('%s','now')),
      is_online   INTEGER DEFAULT 0
    );

    CREATE TABLE IF NOT EXISTS policies (
      id            INTEGER PRIMARY KEY AUTOINCREMENT,
      package_name  TEXT NOT NULL,
      app_name      TEXT NOT NULL,
      priority      TEXT NOT NULL DEFAULT 'MEDIUM',
      max_bps       INTEGER DEFAULT 0,
      device_id     TEXT DEFAULT '__all__',
      created_at    INTEGER DEFAULT (strftime('%s','now')),
      UNIQUE(package_name, device_id)
    );

    CREATE TABLE IF NOT EXISTS telemetry (
      id            INTEGER PRIMARY KEY AUTOINCREMENT,
      device_id     TEXT NOT NULL,
      package_name  TEXT NOT NULL,
      app_name      TEXT NOT NULL,
      bytes_in      INTEGER DEFAULT 0,
      bytes_out     INTEGER DEFAULT 0,
      timestamp     INTEGER DEFAULT (strftime('%s','now'))
    );

    CREATE INDEX IF NOT EXISTS idx_telemetry_time ON telemetry(timestamp);

    CREATE TABLE IF NOT EXISTS qos_validation (
      id            INTEGER PRIMARY KEY AUTOINCREMENT,
      device_id     TEXT NOT NULL,
      package_name  TEXT NOT NULL,
      priority      TEXT NOT NULL,
      requested_bps INTEGER DEFAULT 0,
      allowed_bps   INTEGER DEFAULT 0,
      dropped_pkts  INTEGER DEFAULT 0,
      tcp_flows     INTEGER DEFAULT 0,
      udp_flows     INTEGER DEFAULT 0,
      timestamp     INTEGER DEFAULT (strftime('%s','now'))
    );

    CREATE INDEX IF NOT EXISTS idx_qos_val_time ON qos_validation(timestamp);
  `);

  // Start background saver interval (flush every 5 seconds if dirty)
  setInterval(() => {
    if (dbDirty) {
      save();
      dbDirty = false;
      console.log('[DB] Periodic save executed');
    }
  }, 5000);
  return db;
}

// Persist in-memory DB to disk after every write
function save() {
  if (!db) return;
  try {
    const data = db.export();
    fs.writeFileSync(DB_PATH, Buffer.from(data));
  } catch (err) {
    console.error('[DB] Error saving database to disk:', err.message);
  }
}

// Helper: run a query that modifies data and auto-save
function run(sql, params = []) {
  db.run(sql, params);
  dbDirty = true; // mark DB as dirty; will be flushed by background interval
}

// Helper: return all rows as array of objects
function all(sql, params = []) {
  let stmt;
  try {
    stmt = db.prepare(sql);
    if (params.length) stmt.bind(params);
    const rows = [];
    while (stmt.step()) rows.push(stmt.getAsObject());
    return rows;
  } finally {
    if (stmt) stmt.free();
  }
}

// Helper: return first row as object or null
function get(sql, params = []) {
  const rows = all(sql, params);
  return rows[0] || null;
}

module.exports = { getDb, run, all, get, save };

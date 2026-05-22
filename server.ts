import express, { Request, Response } from 'express';
import http from 'node:http';
import dgram from 'node:dgram';
import os from 'node:os';

const app = express();
const port = Number(process.env.API_PORT || 4000);
const DISCOVERY_PORT = 4001;
const DISCOVERY_MAGIC = 'NADH_WALLET_SERVER';

// Target: website Next.js nadh-store
const WEBSITE_HOST = '127.0.0.1';
const WEBSITE_PORT = 3000;

// ==========================================
// AUTO-DISCOVERY: UDP Broadcast
// Server broadcasts its presence every 3 seconds
// so the APK can find it without knowing the IP
// ==========================================
function getLocalIPs(): string[] {
  const interfaces = os.networkInterfaces();
  const ips: string[] = [];
  for (const name of Object.keys(interfaces)) {
    for (const iface of interfaces[name] || []) {
      if (iface.family === 'IPv4' && !iface.internal) {
        ips.push(iface.address);
      }
    }
  }
  return ips;
}

function startDiscoveryBroadcast() {
  const socket = dgram.createSocket({ type: 'udp4', reuseAddr: true });

  socket.bind(() => {
    socket.setBroadcast(true);

    const broadcast = () => {
      const ips = getLocalIPs();
      const message = JSON.stringify({
        magic: DISCOVERY_MAGIC,
        port,
        ips,
        service: 'NADH Wallet API',
        timestamp: Date.now(),
      });
      const buf = Buffer.from(message);

      // Broadcast to all subnet broadcast addresses
      for (const ip of ips) {
        const parts = ip.split('.');
        const broadcastAddr = `${parts[0]}.${parts[1]}.${parts[2]}.255`;
        socket.send(buf, 0, buf.length, DISCOVERY_PORT, broadcastAddr, (err) => {
          if (err) console.error(`[DISCOVERY] Broadcast error to ${broadcastAddr}:`, err.message);
        });
      }
    };

    // Broadcast every 3 seconds
    broadcast();
    setInterval(broadcast, 3000);
    console.log(`[DISCOVERY] Broadcasting on UDP port ${DISCOVERY_PORT} every 3s`);
  });
}

// Also listen for discovery requests (APK sends a "ping", server replies directly)
function startDiscoveryListener() {
  const listener = dgram.createSocket({ type: 'udp4', reuseAddr: true });

  listener.on('message', (msg, rinfo) => {
    try {
      const data = JSON.parse(msg.toString());
      if (data.magic === 'NADH_WALLET_DISCOVER') {
        // APK is looking for us, reply directly
        const reply = JSON.stringify({
          magic: DISCOVERY_MAGIC,
          port,
          ips: getLocalIPs(),
          service: 'NADH Wallet API',
          timestamp: Date.now(),
        });
        const buf = Buffer.from(reply);
        listener.send(buf, 0, buf.length, rinfo.port, rinfo.address);
        console.log(`[DISCOVERY] Replied to ${rinfo.address}:${rinfo.port}`);
      }
    } catch { /* ignore non-JSON messages */ }
  });

  listener.bind(DISCOVERY_PORT, '0.0.0.0', () => {
    console.log(`[DISCOVERY] Listening for discovery requests on UDP port ${DISCOVERY_PORT}`);
  });
}

// ==========================================
// EXPRESS SERVER + PROXY
// ==========================================
app.use((req, _res, next) => {
  req.url = req.url.replace(/^\/{2,}/, '/');
  next();
});

app.use(express.json());
app.use((req, res, next) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
  if (req.method === 'OPTIONS') {
    res.sendStatus(204);
    return;
  }
  next();
});

// Discovery endpoint (HTTP fallback for APK to find server via subnet scan)
app.get('/api/discover', (_req, res) => {
  res.json({
    magic: DISCOVERY_MAGIC,
    port,
    ips: getLocalIPs(),
    service: 'NADH Wallet API',
    timestamp: Date.now(),
  });
});

app.get('/api/health', (_req, res) => {
  res.json({ success: true, service: 'NADH Wallet Proxy API', port, ips: getLocalIPs(), target: `http://${WEBSITE_HOST}:${WEBSITE_PORT}` });
});

// Proxy wallet, VA, and QRIS payment requests to the Next.js website
app.all(['/api/wallet/*', '/api/va/*', '/api/qris/*'], (req: Request, res: Response) => {
  const bodyStr = req.method !== 'GET' && req.body ? JSON.stringify(req.body) : '';

  const options: http.RequestOptions = {
    hostname: WEBSITE_HOST,
    port: WEBSITE_PORT,
    path: req.originalUrl,
    method: req.method,
    headers: {
      'Content-Type': 'application/json',
      ...(req.headers.authorization ? { authorization: req.headers.authorization } : {}),
      ...(bodyStr ? { 'Content-Length': Buffer.byteLength(bodyStr).toString() } : {}),
    },
  };

  const proxyReq = http.request(options, (proxyRes) => {
    res.status(proxyRes.statusCode || 500);
    if (proxyRes.headers['content-type']) {
      res.setHeader('Content-Type', proxyRes.headers['content-type']);
    }

    const chunks: Buffer[] = [];
    proxyRes.on('data', (chunk) => chunks.push(chunk));
    proxyRes.on('end', () => {
      const body = Buffer.concat(chunks).toString('utf-8');
      console.log(`[PROXY] ${req.method} ${req.originalUrl} -> ${proxyRes.statusCode}`);
      res.send(body);
    });
  });

  proxyReq.on('error', (err) => {
    console.error(`[PROXY ERROR] ${req.method} ${req.originalUrl}:`, err.message);
    res.status(502).json({
      success: false,
      error: `Tidak bisa terhubung ke website (${WEBSITE_HOST}:${WEBSITE_PORT}). Pastikan website nadh-store sudah running.`,
    });
  });

  if (bodyStr) {
    proxyReq.write(bodyStr);
  }
  proxyReq.end();
});

app.post('/api/payment-callback', (req, res) => {
  console.log('[CALLBACK] Website callback diterima:', req.body);
  res.json({ success: true });
});

app.listen(port, '0.0.0.0', () => {
  const ips = getLocalIPs();
  console.log(`NADH Wallet Proxy API running at http://0.0.0.0:${port}`);
  console.log(`Local IPs: ${ips.join(', ')}`);
  console.log(`Proxying /api/wallet/*, /api/va/*, and /api/qris/* -> http://${WEBSITE_HOST}:${WEBSITE_PORT}`);

  // Start auto-discovery
  startDiscoveryBroadcast();
  startDiscoveryListener();
});

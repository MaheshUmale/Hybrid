const WebSocket = require('ws');

const wss = new WebSocket.Server({ port: 7070 });

// Simulation Variables
let spot = 24650.00;
let timeRemaining = 1200; // 20 minutes in seconds for Theta-Guard
let mode = 'NORMAL'; // Switch to 'VOLATILE' to test RBI Event

console.log("JULES-HF-ATS Mock Server started on ws://localhost:7070");

wss.on('connection', (ws) => {
  const interval = setInterval(() => {
    // 1. Simulate Price Movement
    const volatility = mode === 'VOLATILE' ? 5.0 : 0.5;
    spot += (Math.random() - 0.48) * volatility; // Slight upward bias

    // 2. Simulate Weighted Delta (The Engine)
    const weightedDelta = (spot - 24650) * 1500 + (Math.random() * 500);

    // 3. Determine Auction State
    let state = "ROTATION";
    if (weightedDelta > 5000) state = "INITIATIVE_BUY";
    if (weightedDelta < -5000) state = "INITIATIVE_SELL";

    // 4. Update Theta-Guard
    timeRemaining = timeRemaining > 0 ? timeRemaining - 0.1 : 1200;

    // 5. Construct JSON Packet
    const packet = {
      timestamp: Date.now(),
      spot: parseFloat(spot.toFixed(2)),
      fut: parseFloat((spot + 15).toFixed(2)),
      weighted_delta: Math.floor(weightedDelta),
      auction_state: state,
      pcr: parseFloat((1.1 + (Math.random() * 0.2)).toFixed(2)),
      theta_gcr: parseFloat((1.1 + (Math.random() * 0.2)).toFixed(2)),
      wssLatency: Math.floor(Math.random() * 10),
      questDbLag: Math.floor(Math.random() * 15),
      disruptor: Math.floor(Math.random() * 100),
      username: "Beal",
      active_trades: [
        { symbol: 'NIFTY 24600 CE', entry: 100, ltp: 112.5, qty: 50, pnl: 6250, reason: ''},
        { symbol: 'NIFTY 24700 PE', entry: 80, ltp: 75.2, qty: 100, pnl: -480, reason: ''},
        { symbol: 'NIFTY 24500 CE', entry: 150, ltp: 180.1, qty: 50, pnl: 1505, reason: 'THETA (1min)'},
      ],
      alerts: [
        { type: 'success', message: 'Put OI @ 24600 Spiking' },
        { type: 'warning', message: 'PCR Divergence: None' },
        { type: 'error', message: 'Call Wall Def: Strong' },
      ],
      ohlc: {
        time: Date.now() / 1000,
        open: spot - (Math.random() * 2),
        high: spot + (Math.random() * 2),
        low: spot - (Math.random() * 2),
        close: spot
      },
      heavyweights: [
        { name: "RELIANCE", delta: Math.floor(Math.random() * 5000), change: 1.2, qtp: 10 },
        { name: "HDFCBANK", delta: Math.floor(Math.random() * 4500), change: 0.8, qtp: 8 },
        { name: "ICICIBANK", delta: Math.floor(Math.random() * 4000), change: -0.5, qtp: 5 },
        { name: "INFOSYS", delta: Math.floor(Math.random() * 3500), change: 2.1, qtp: 12 },
      ],
      option_window: [
        { strike: 24600, type: "CE", oi: 1000, oi_chg: 5.2 },
        { strike: 24650, type: "CE", oi: 1200, oi_chg: 12.5 },
        { strike: 24700, type: "CE", oi: 1500, oi_chg: 8.1 },
        { strike: 24600, type: "PE", oi: 900, oi_chg: -3.4 },
        { strike: 24650, type: "PE", oi: 800, oi_chg: -2.1 },
        { strike: 24700, type: "PE", oi: 1100, oi_chg: 4.5 },
      ]
    };

    ws.send(JSON.stringify(packet));
  }, 100); // 10Hz update rate

  ws.on('close', () => clearInterval(interval));
});

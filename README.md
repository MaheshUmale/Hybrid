# Hybrid
hybrid
# TradingView Data Bridge - Multi-Source Redundancy System

A robust Python data bridge that provides real-time market data with **three-tier redundancy** for candles, market breadth, and option chain feeds.

## 🎯 Features

### Multi-Tier Data Redundancy
- **Candles**: Upstox Intraday (Primary) → TradingView Premium → TradingView Public
- **Market Breadth**: NSE API → TradingView Screener (Nifty 50 constituents)
- **PCR/OI Data**: NSE v3 API → Trendlyne SQLite Database (with historical backfill)

### Core Components
1. **SymbolMaster** - Centralized instrument key resolver (8,792+ NSE instruments)
2. **TV Data Bridge** - WebSocket server broadcasting real-time feeds
3. **Trendlyne Integration** - SQLite database (`options_data.db`) for option chain snapshots and backfill
4. **NSE Client** - Live market breadth and option chain data
5. **Upstox SDK** - Primary source for intraday candles and historical data

## 📦 Installation

### Prerequisites
```bash
# Python 3.8+
pip install websockets pandas numpy requests upstox-client tradingview-screener rookiepy
```

### Configuration
Create `config.py`:
```python
ACCESS_TOKEN = 'your_upstox_access_token'
# Optional: MongoDB connection (not actively used)
# MONGO_URI = "mongodb://localhost:27017/"
# DB_NAME = "upstox_strategy_db"
```

### SQLite Database
The system uses SQLite (`options_data.db`) for:
- Option chain snapshots (1-minute intervals)
- PCR historical data
- Market breadth records
- **Backfill support** when system restarts or was offline

## 🚀 Quick Start

### 1. Start the Data Bridge
```bash
python tv_data_bridge.py
```

The bridge will:
- Initialize SymbolMaster (downloads Upstox instrument master)
- Start WebSocket server on `ws://localhost:8765`
- Begin broadcasting:
  - `candle_update` (every 60s)
  - `market_breadth` (every 60s)
  - `option_chain` (every 60s)
  - `pcr_update` (every 60s)

### 2. Connect from Java Client
```java
TVMarketDataStreamer streamer = new TVMarketDataStreamer("ws://localhost:8765");
streamer.connect();
```

## 📊 Data Formats

### Candle Update
```json
{
  "type": "candle_update",
  "data": [{
    "symbol": "RELIANCE",
    "timestamp": 1704441000000,
    "1m": {"open": 2500.0, "high": 2505.0, "low": 2498.0, "close": 2502.0, "volume": 15000},
    "5m": {"open": 2495.0, "high": 2510.0, "low": 2490.0, "close": 2502.0, "volume": 75000},
    "pcr": 1.05
  }]
}
```

### Market Breadth
```json
{
  "type": "market_breadth",
  "data": {
    "advance": 32,
    "decline": 15,
    "unchanged": 3,
    "timestamp": 1704441000000
  }
}
```

## 🔧 Architecture

### Failover Logic

#### Candles (tv_data_bridge.py)
```
┌─────────────────────────────────────────┐
│  1. Upstox Intraday API (PRIMARY)      │
│     - Fastest, most reliable (if token)│
└───────────────┬─────────────────────────┘
                │ FAIL or No Token
                ▼
┌─────────────────────────────────────────┐
│  2. TradingView Premium (with cookies) │
└───────────────┬─────────────────────────┘
                │ FAIL
                ▼
┌─────────────────────────────────────────┐
│  3. TradingView Public (screener scan)  │
└─────────────────────────────────────────┘
```

#### Trendlyne Backfill
The SQLite database automatically stores:
- All option chain snapshots (1-min resolution)
- PCR historical data (daily aggregates)
- Market breadth records

**Recovery Mode**: When the bridge restarts, it can retrieve the latest cached data from SQLite to seamlessly resume operations without data gaps.

### SymbolMaster - Central Governor
Resolves instrument identifiers across data providers:
```python
from SymbolMaster import MASTER

MASTER.initialize()
key = MASTER.get_upstox_key("RELIANCE")  # NSE_EQ|INE002A01018
symbol = MASTER.get_ticker_from_key(key)  # RELIANCE
```

## 📁 File Structure

```
Java_CompleteSystem/
├── tv_data_bridge.py          # Main WebSocket bridge
├── SymbolMaster.py             # Instrument key resolver
├── NSEAPICLient.py             # NSE API client
├── backfill_trendlyne.py       # SQLite option chain storage & backfill
├── config.py                   # Upstox API credentials
├── options_data.db             # SQLite database (auto-created)
└── test_symbol_master.py       # Verification script
```

## 🧪 Testing

### Verify SymbolMaster
```bash
python test_symbol_master.py
```
Expected output:
```
[OK] NIFTY -> NSE_INDEX|Nifty 50
[OK] RELIANCE -> NSE_EQ|INE002A01018
```

### Verify Upstox Fallback
```bash
python test_upstox_fallback_standalone.py
```

## ⏪ Backtesting System

The system includes a transparent backtesting engine that replays historical data via WebSocket.

### 1. Collect Backtest Data
Fetch historical 1-minute candles and option chain data:
```bash
python collect_backtest_data.py --date 2026-01-05
```

### 2. Run Replay Engine
Start the replay server (default: `ws://localhost:8765`):
```bash
python backtest_replay.py --date 2026-01-05 --speed 5
```
- `--speed`: 1 = realtime, 5 = 5x faster, 999 = instant.
- `--start`: Start time (HH:MM, default: 09:15).
- `--end`: End time (HH:MM, default: 15:30).

### 3. Connect Java Dashboard
Point your Java dashboard to `ws://localhost:8765`. It will receive `candle_update`, `option_chain`, and `pcr_update` messages exactly like the live system.

## 🛡️ Error Handling

All data feeds include graceful degradation:
- **Primary failure**: Automatic fallback to secondary source
- **All sources down**: Returns empty array, logs critical error
- **Partial data**: Continues with available symbols

## 📝 Logging

Monitor fallback events:
```
[CANDLE PRIMARY ERROR] ... Trying Lightweight Fallback (No Cookies)...
[CANDLE LVL1 ERROR] ... Trying Upstox SDK Fallback...
[UPSTOX FALLBACK] Recovered 47 symbols.
```

## 🔐 Security Notes

- Store `ACCESS_TOKEN` in `config.py` (add to .gitignore)
- Upstox tokens expire daily, refresh via OAuth2 flow
- NSE API uses session cookies (auto-managed)
- SQLite database (`options_data.db`) stores historical data locally

## 💾 Data Persistence

The system uses **SQLite** (`options_data.db`) for:
- Option chain historical snapshots
- PCR data (minute-level and daily aggregates)
- Market breadth records
- **Automatic backfill** when restarting after downtime

## 🚧 Future Enhancements

- [ ] FII/DII Net Flow integration
- [ ] Historical data backfill via Upstox

> **Note**: This system is optimized for 1-minute candle-based trading with ~5 second acceptable latency.  
> High-frequency tick ingestion (Redis/Prometheus) is intentionally not implemented to avoid system overhead.

## 📄 License

Proprietary - Internal Use Only

## 🤝 Contributors

Mahesh - Data Bridge Architecture & Multi-Source Redundancy

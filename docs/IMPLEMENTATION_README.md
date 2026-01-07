# Implementation Summary and Run Instructions

This document summarizes the recent code updates, how to run the system for integration/backtest, and the next steps (TODOs).

## Recent Changes (high level)
- Implemented risk-based sizing with lot rounding and max cap.
- Added `LotSizeProvider` and integrated lot-based rounding.
- Added EMA9 indicator to `TechnicalIndicators`.
- Added VWAP+9EMA gate using N-bar consolidation and measured-move TP/SL.
- Added partial take-profit support and `Position.partialClose` semantics.
- Added EMA/VWAP-based trailing SL rules.
- Added options heuristics: ATM mapping, OI wall detection, PCR-based scaling.
- Extensive logging added: `SIZING`, `POSITION_CREATED`, `PARTIAL_TP`, `TRAIL_SL_UPDATED`, `OI wall detected`.

## Files added/modified
- `ats-core/src/main/java/com/trading/hf/ScalpingSignalEngine.java` — VWAP+9EMA gate, N-bar consolidation, partial TP, trailing.
- `ats-core/src/main/java/com/trading/hf/TechnicalIndicators.java` — EMA9 added.
- `ats-core/src/main/java/com/trading/hf/Position.java` — partial-close support.
- `ats-core/src/main/java/com/trading/hf/PositionManager.java` — `partialClosePosition` API.
- `config.properties` — new config keys and defaults.
- `docs/` — `IMPLEMENTATION_README.md` (this file) and `rules/*.json` (from earlier PDF extraction).

## How to run locally (integration/backtest)

Prerequisites:
- Java 17+ (JDK 21 recommended)
- Maven
- Python 3.10+ with project venv (see existing `venv` usage in repo)

1) Build `ats-core` only (core trading logic):

```powershell
mvn -f ats-core/pom.xml -DskipTests package
```

2) Start the Python backtest bridge (in activated venv) which serves historical/replay data on `ws://localhost:8765`:

```powershell
# from repo root
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt  # if present, or install websockets
python backtest_replay.py
```

3) Start Java dashboard / engine (uses built jars). If you have assembled full app, start the jar from `ats-dashboard/target` or run the main module in your IDE; to run the core engine in isolation develop a small runner that wires `ScalpingSignalEngine` with `TVMarketDataStreamer`.

Notes:
- The Java engine expects `config.properties` in the working directory. Tune `partial.tp.percent`, `measured.move.window`, `measured.move.multiplier`, `trailing.trigger.atr.mult` as needed.
- Watch logs for these keywords: `SIZING:`, `POSITION_CREATED:`, `PARTIAL_TP:`, `TRAIL_SL_UPDATED:`, `AUTO-EXIT`.

## Backtest verification checklist (what to capture)
- For each trade: entry price, quantity, stop, take-profit, realized PnL, timestamps
- Aggregate metrics: total PnL, win-rate, average R:R, max drawdown, sharpe (optional)
- Inspect whether computed quantity aligns with `risk.per.trade` and round-to-lot behavior.

## Config keys and recommended defaults (see `config.properties`)
- `risk.per.trade` = 1000.0
- `max.position.qty` = 10000
- `measured.move.window` = 5
- `measured.move.multiplier` = 2.0
- `measured.move.stop.buffer.mult` = 0.02
- `partial.tp.percent` = 0.5
- `partial.remaining.tp.multiplier` = 3.0
- `trailing.ema.enabled` = true
- `trailing.vwap.enabled` = true
- `trailing.trigger.atr.mult` = 1.0

## Next steps (high-level)
- Run integrated backtests and collect trade logs; iterate tuning.
- Add unit tests and integration test harness.
- Implement remaining PlayBook gates (SNAP_B/SNAP_S wick logic, pattern detectors).
- Formalize options SL/TP to be premium-aware in sizing algorithm.
- Fix multi-module build's parent POM or publish parent to local repo to enable full `mvn package`.


## Where the artifacts are
- `ats-core/target/ats-core-1.0-SNAPSHOT.jar` — compiled core module
- `docs/extracted_text/` and `docs/pdf_images/` — PDF extraction outputs
- `docs/rules/*.json` — per-PDF structured rules


## Contact / Next actions
- I can run the integrated backtest and stream logs when you are ready. I will run and produce a CSV of trades and a summary PnL table.

*** End of document

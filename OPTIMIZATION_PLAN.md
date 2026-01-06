# Backtest Analysis & Optimization Plan

## Current Performance (2026-01-05 Replay)
- **Total PnL:** Negative
- **Win Rate:** ~30% (Target: > 55%)
- **Dominant Failure:** `TECH_SL_HIT` (Underlying price hitting Stop Loss)

## Weak Strategy Identification

### 1. Rejection Strategies (`STUFF_S`, `CRUSH_L`)
- **Performance:**
  - `STUFF_S`: 20% WR, -10k PnL
  - `CRUSH_L`: 12.5% WR, -6.5k PnL
- **Diagnosis:**
  - **SL too tight:** `0.1 * ATR` buffer is noise-prone in volatile markets.
  - **Weak Rejection Validation:** Fading a move just because of a wick/PCR is risky if the trend is strong.
  - **Volume:** No volume confirmation on the rejection candle.

### 2. Opening Range Breakout (`ORB_S`)
- **Performance:** 28% WR, -1.2k PnL
- **Diagnosis:**
  - **Stop Loss Logic:** Using `(ORB_High + ORB_Low)/2` as SL. If the ORB range is wide (volatile morning), the risk is huge, and the R:R skews against us.
  - **False Breakouts:** Frequent in index trading without volume confirmation.

### 3. Late Squeeze (`LATE_SQ`)
- **Performance:** 0% WR (3/3 Loss)
- **Diagnosis:**
  - 14:45 is widely used by institutions for positioning, often leading to chop before the 3 PM move. Simple EMA crossover is insufficient.

## Optimization Strategy

### Optimize `STUFF_S` (Short Rejection)
1.  **Widen SL:** Increase buffer to `0.25 * ATR`.
2.  **Volume Rejection:** Require `Volume Ratio > 1.5` (Heavy betting on the rejection).
3.  **VWAP Filter:** Ensure `Close < VWAP` (Price staying below equilibrium).

### Optimize `CRUSH_L` (Long Rejection)
1.  **Widen SL:** Increase buffer to `0.25 * ATR`.
2.  **Volume Rejection:** Require `Volume Ratio > 1.5`.
3.  **VWAP Filter:** Ensure `Close > VWAP` OR `Close > EMA20` (Regaining strength).

### Optimize `ORB_S` (Short Breakout)
1.  **Tighter SL:** Use `ORB_Low + 0.5 * ATR` or `Candle High` instead of generic Midpoint, to improve R:R.
2.  **Volume Confirmation:** Require `VolRatio > 1.2` on the breakout candle.

### Optimize `LATE_SQ`
1.  **Disable:** temporarily or restrict to `VolRatio > 5.0` (Extreme spikes only).

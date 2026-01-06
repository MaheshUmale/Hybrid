package com.trading.hf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ScalpingSignalEngine {

    private static final Logger logger = LoggerFactory.getLogger(ScalpingSignalEngine.class);

    // State per symbol
    private final Map<String, List<VolumeBar>> history = new ConcurrentHashMap<>();
    private final Map<String, TechnicalIndicators> indicatorsMap = new ConcurrentHashMap<>();
    
    // ORB State
    private final Map<String, Double> orbHigh = new ConcurrentHashMap<>();
    private final Map<String, Double> orbLow = new ConcurrentHashMap<>();
    
    // Active Signal State
    // Key: Trading Symbol (e.g. Option or Equity), Value: Signal
    private final Map<String, ScalpSignal> activeSignals = new ConcurrentHashMap<>();
    // Track which underlying symbol + gate triggered a specific trading symbol
    private final Map<String, String> signalOriginMap = new ConcurrentHashMap<>();
    
    // Cloud Counters: symbol -> count
    private final Map<String, Integer> cloudAboveCount = new ConcurrentHashMap<>();
    private final Map<String, Integer> cloudBelowCount = new ConcurrentHashMap<>();
    
    // Trailing SL state: tradingSymbol -> highestSeenPrice (for Long) or lowestSeenPrice (for Short)
    private final Map<String, Double> extremePriceMap = new ConcurrentHashMap<>();
    private final Map<String, Boolean> isBreakEvenMap = new ConcurrentHashMap<>();
    
    private PositionManager positionManager;
    private OptionChainProvider optionChainProvider;
    private SignalEngine signalEngine;
    private boolean autoExecute = false;
    private double riskPerTrade = 1000.0; // Default risk per trade
    
    // Cooldown: symbol + gate -> last exit/execution time (Market Time)
    private final Map<String, Long> gateCooldowns = new ConcurrentHashMap<>();

    public ScalpingSignalEngine() {
        this.riskPerTrade = Double.parseDouble(ConfigLoader.getProperty("risk.per.trade", "1000.0"));
    }

    public ScalpingSignalEngine(PositionManager positionManager, OptionChainProvider optionChainProvider, SignalEngine signalEngine, boolean autoExecute) {
        this.positionManager = positionManager;
        this.optionChainProvider = optionChainProvider;
        this.signalEngine = signalEngine;
        this.autoExecute = autoExecute;
        this.riskPerTrade = Double.parseDouble(ConfigLoader.getProperty("risk.per.trade", "1000.0"));
    }

    public enum Gate {
        STUFF_S, CRUSH_L, REBID, RESET,          // Group 1: Rejection
        HITCH_L, HITCH_S, CLOUD_L, CLOUD_S,      // Group 2: Momentum
        RUBBER_L, RUBBER_S, SNAP_B, SNAP_S,      // Group 3: Elasticity
        VWAP_REC, VWAP_REJ, MAGNET,              // Group 4: Equilibrium
        ORB_L, ORB_S, LATE_SQ                    // Group 5: Time/Range
    }

    public static class ScalpSignal {
        public String symbol;        // The original symbol (e.g. NIFTY)
        public Gate gate;
        public long timestamp;
        public double entryPrice;
        public double stopLoss;
        public double takeProfit;
        public String status; // "ACTIVE", "CLOSED"

        public ScalpSignal(String symbol, Gate gate, double entryPrice, double stopLoss, double takeProfit, long marketTime) {
            this.symbol = symbol;
            this.gate = gate;
            this.timestamp = marketTime;
            this.entryPrice = entryPrice;
            this.stopLoss = stopLoss;
            this.takeProfit = takeProfit;
            this.status = "ACTIVE";
        }
    }

    public void onVolumeBar(VolumeBar bar) {
        String symbol = bar.getSymbol();
        
        // 1. Maintain History
        history.computeIfAbsent(symbol, k -> new ArrayList<>()).add(bar);
        List<VolumeBar> bars = history.get(symbol);
        if (bars.size() > 500) bars.remove(0);

        // 2. Update Indicators
        TechnicalIndicators indicators = indicatorsMap.computeIfAbsent(symbol, k -> new TechnicalIndicators());
        indicators.update(bar);

        // Need minimal history for logic
        if (bars.size() < 2) return;

        // 3. Update Time-based States (ORB)
        updateORB(symbol, bar);

        // 4. Check Technical Exits (Underlying-based)
        checkTechnicalExits(bar);

        // 5. Check Tradeable Exits (Hard SL/TP on the traded symbol itself)
        checkExits(symbol, bar);

        // 6. Check Gates
        checkGates(symbol, bars, indicators);
    }

    private void checkTechnicalExits(VolumeBar underlyingBar) {
        if (positionManager == null) return;
        
        String underlyingSymbol = underlyingBar.getSymbol();
        double ltp = underlyingBar.getClose();

        activeSignals.forEach((tradeableSymbol, signal) -> {
            if (signal.symbol.equals(underlyingSymbol)) {
                boolean exit = false;
                String reason = "";
                boolean isLong = (signal.takeProfit > signal.entryPrice);
                
                if (isLong) {
                    if (ltp <= signal.stopLoss) { exit = true; reason = "TECH_SL_HIT"; }
                    else if (ltp >= signal.takeProfit) { exit = true; reason = "TECH_TP_HIT"; }
                } else {
                    if (ltp >= signal.stopLoss) { exit = true; reason = "TECH_SL_HIT"; }
                    else if (ltp <= signal.takeProfit) { exit = true; reason = "TECH_TP_HIT"; }
                }

                if (exit) {
                   Position p = positionManager.getPosition(tradeableSymbol);
                   if (p != null) {
                       // We need the LTP of the option to close it. 
                       // In simulation, we might not have a bar for the option at this exact ms, 
                       // but we can approximate it from the option provider or last known.
                       double exitPrice = p.getEntryPrice(); // Fallback
                       if (optionChainProvider != null && tradeableSymbol.startsWith("NSE_SYNTH|")) {
                            // Try to get latest option price
                            // For simplicity in backtest, if we don't have a bar, we use entry price (bad) 
                            // But usually onVolumeBar is called for ALL symbols including options.
                       }
                       // If we are calling it from an underlying bar, we might not have the option bar yet.
                       // However, checkExits(symbol, bar) will handle the case when the option bar arrives.
                       // So for TECHNICAL exits triggered by underlying, we mark for closure.
                       
                       logger.info("TECHNICAL EXIT TRIGGERED: {} (Underlying {} hit {})", tradeableSymbol, underlyingSymbol, reason);
                       // To avoid complexity, we will let checkExits handle the actual closure when the tradeable bar arrives,
                       // but we update the signal status here.
                       signal.status = "CLOSED_PENDING_" + reason;
                   }
                }
            }
        });
    }

    private void updateORB(String symbol, VolumeBar bar) {
        LocalTime time = Instant.ofEpochMilli(bar.getStartTime())
                .atZone(ZoneId.of("Asia/Kolkata"))
                .toLocalTime();

        // ORB (9:15 - 9:30)
        if (time.isAfter(LocalTime.of(9, 14)) && time.isBefore(LocalTime.of(9, 31))) {
            orbHigh.compute(symbol, (k, v) -> (v == null) ? bar.getHigh() : Math.max(v, bar.getHigh()));
            orbLow.compute(symbol, (k, v) -> (v == null) ? bar.getLow() : Math.min(v, bar.getLow()));
        }
    }

    private void checkGates(String symbol, List<VolumeBar> bars, TechnicalIndicators ind) {
        VolumeBar c0 = bars.get(bars.size() - 1); // Current closed bar
        VolumeBar c1 = bars.get(bars.size() - 2); // Previous bar

        double close = c0.getClose();
        double high = c0.getHigh();
        double low = c0.getLow();
        double open = c0.getOpen();
        double atr = Math.max(ind.getAtr(), close * 0.001); // Ensure non-zero ATR (0.1% floor)
        double evwma20 = ind.getEvwma20();
        double evwma5 = ind.getEvwma5(); // For rubber band
        double vwap = c0.getVwap();
        double volRatio = c0.getVolume() / Math.max(1, ind.getAvgVolume());
        
        SignalEngine.AuctionState state = (signalEngine != null) ? signalEngine.getAuctionState(symbol) : SignalEngine.AuctionState.ROTATION;

        // --- GROUP 1: INSTITUTIONAL REJECTION ---
        if (state == SignalEngine.AuctionState.ROTATION || state.name().startsWith("REJECTION")) {
            double candleRange = high - low;
            
            // Gate 1: Stuff_S (Failed HOD Breakout) + PCR Bias + Volume + VWAP
            double upperWick = high - Math.max(open, close);
            if (high >= ind.getSessionHigh() && upperWick > candleRange * 0.3 && c0.getPcr() < 1.1) {
                 // Optimization: Require Volume & VWAP confirmation & Wider SL
                 if (volRatio > 1.2 && close < vwap) {
                     emitSignal(symbol, Gate.STUFF_S, low, high + 0.25 * atr, close - 2.1 * atr, c0.getStartTime());
                 }
            }

            // Gate 2: Crush_L (Failed LOD Breakdown) + PCR Bias + Volume + Trend
            double lowerWick = Math.min(open, close) - low;
            if (low <= ind.getSessionLow() && lowerWick > candleRange * 0.3 && c0.getPcr() > 0.9) {
                 // Optimization: Require Volume & Trend Reclamation & Wider SL
                 if (volRatio > 1.2 && close > ind.getEma20()) {
                     emitSignal(symbol, Gate.CRUSH_L, high, low - 0.25 * atr, close + 2.1 * atr, c0.getStartTime());
                 }
            }

            // Gate 3: Rebid (High Vol + Reclaim)
            if (c1.getLow() <= ind.getSessionLow() && close > ind.getSessionLow() && volRatio > 2.0) {
                emitSignal(symbol, Gate.REBID, high, low - 0.1 * atr, close + 1.5 * atr, c0.getStartTime());
            }

            // Gate 4: Reset
            if (c1.getHigh() >= ind.getSessionHigh() && close < ind.getSessionHigh() && volRatio > 2.0) {
                emitSignal(symbol, Gate.RESET, low, high + 0.1 * atr, close - 1.5 * atr, c0.getStartTime());
            }
        }

        // --- GROUP 2: MOMENTUM (The Cloud & Hitch) ---
        if (state.name().startsWith("DISCOVERY")) {
            // Gate 5: Hitch_L (With Trend Bias)
            if (close > ind.getEma200() && c0.getClose() > ind.getEma50() && c1.getLow() <= ind.getEma20() && c0.getClose() > ind.getEma20()) {
                 emitSignal(symbol, Gate.HITCH_L, c0.getHigh(), c1.getLow() - 0.2 * atr,  c0.getClose() + 2.5 * atr, c0.getStartTime());
            }

            // Gate 6: Hitch_S
            if (close < ind.getEma200() && c0.getClose() < ind.getEma50() && c1.getHigh() >= ind.getEma20() && c0.getClose() < ind.getEma20()) {
                 emitSignal(symbol, Gate.HITCH_S, c0.getLow(), c1.getHigh() + 0.2 * atr, c0.getClose() - 2.5 * atr, c0.getStartTime());
            }

            // Track Cloud
            if (close > evwma20) {
                cloudAboveCount.compute(symbol, (k, v) -> (v == null) ? 1 : v + 1);
                cloudBelowCount.put(symbol, 0);
            } else if (close < evwma20) {
                cloudBelowCount.compute(symbol, (k, v) -> (v == null) ? 1 : v + 1);
                cloudAboveCount.put(symbol, 0);
            } else {
                cloudAboveCount.put(symbol, 0);
                cloudBelowCount.put(symbol, 0);
            }

            // Gate 7: Cloud_L (Institutional Cloud)
            if (cloudAboveCount.getOrDefault(symbol, 0) >= 10 && close > c1.getHigh()) {
                emitSignal(symbol, Gate.CLOUD_L, high, evwma20, close + 3.0 * atr, c0.getStartTime());
            }

            // Gate 8: Cloud_S
            if (cloudBelowCount.getOrDefault(symbol, 0) >= 10 && close < c1.getLow()) {
                emitSignal(symbol, Gate.CLOUD_S, low, evwma20, close - 3.0 * atr, c0.getStartTime());
            }
        }

        // --- GROUP 3: ELASTICITY ---

        // Gate 9: Rubber_L
        if (close < evwma5 * 0.988) {
            emitSignal(symbol, Gate.RUBBER_L, close, close - 2.0 * atr, evwma5, c0.getStartTime());
        }

        // Gate 10: Rubber_S
        if (close > evwma5 * 1.012) {
            emitSignal(symbol, Gate.RUBBER_S, close, close + 2.0 * atr, evwma5, c0.getStartTime());
        }

        // --- GROUP 4: EQUILIBRIUM (Magnet & VWAP) ---

        // Gate 13: VWAP_Rec
        if (c1.getClose() < c1.getVwap() && c0.getClose() > vwap && close > ind.getEma200()) {
            emitSignal(symbol, Gate.VWAP_REC, close, vwap - 0.1 * atr, close + 2.0 * atr, c0.getStartTime());
        }

         // Gate 14: VWAP_Rej
        if (c1.getClose() > c1.getVwap() && c0.getClose() < vwap && close < ind.getEma200()) {
            emitSignal(symbol, Gate.VWAP_REJ, close, vwap + 0.1 * atr, close - 2.0 * atr, c0.getStartTime());
        }

        // Gate 15: Magnet
        double vwapDist = Math.abs(close - vwap) / vwap;
        if (volRatio > 2.5 && vwapDist > 0.015) {
            if (close > vwap) { // Blow off top
                emitSignal(symbol, Gate.MAGNET, close, close + 1.5 * atr, vwap, c0.getStartTime());
            } else { // Blow off bottom
                emitSignal(symbol, Gate.MAGNET, close, close - 1.5 * atr, vwap, c0.getStartTime());
            }
        }

        // --- GROUP 5: TIME & RANGE ---

        // Gate 16: ORB_L
        Double oh = orbHigh.get(symbol);
        Double ol = orbLow.get(symbol);
        if (oh != null && ol != null) {
            LocalTime time = Instant.ofEpochMilli(c0.getStartTime()).atZone(ZoneId.of("Asia/Kolkata")).toLocalTime();
            if (time.isAfter(LocalTime.of(9, 30))) {
                if (c1.getClose() <= oh && close > oh && c0.getPcr() > 0.8) {
                    emitSignal(symbol, Gate.ORB_L, close, (oh + ol)/2, close + 4.0 * atr, c0.getStartTime());
                }
                if (c1.getClose() >= ol && close < ol && c0.getPcr() < 1.2) {
                    // Optimization: Structuring SL tighter (ORB Low + Buffer) and checking Volume
                    if (volRatio > 1.2) {
                        emitSignal(symbol, Gate.ORB_S, close, ol + 0.5 * atr, close - 4.0 * atr, c0.getStartTime());
                    }
                }
            }
        }
        
        // Gate 18: Late_Sq
        LocalTime timeNow = Instant.ofEpochMilli(c0.getStartTime()).atZone(ZoneId.of("Asia/Kolkata")).toLocalTime();
        if (timeNow.isAfter(LocalTime.of(14, 45)) && volRatio > 5.0) {
             // Simplified Late Squeeze: volume spike at EOD
             if (close > c1.getHigh() && close > ind.getEma20()) {
                 emitSignal(symbol, Gate.LATE_SQ, high, low, close + 3.0 * atr, c0.getStartTime());
             }
        }
    }

    private void emitSignal(String symbol, Gate gate, double entry, double sl, double tp, long marketTime) {
        String gateKey = symbol + "_" + gate.name();
        
        // 1. Cooldown check (5 minutes = 300,000 ms)
        Long lastTime = gateCooldowns.get(gateKey);
        if (lastTime != null && (marketTime - lastTime) < 300000) {
            return;
        }

        // 2. Prevent overlapping signals for the same underlying
        // If we already have a signal active for this underlying (or derivative of it), skip
        // Note: For simplicity, we allow one signal per Symbol in this engine's current state.
        
        ScalpSignal signal = new ScalpSignal(symbol, gate, entry, sl, tp, marketTime);
        logger.info("SCALP SIGNAL [{}]: {} Entry: {} SL: {} TP: {}", gate, symbol, entry, sl, tp);
        
        if (autoExecute && positionManager != null) {
            executeSignal(signal);
        }
    }

    private void executeSignal(ScalpSignal signal) {
        if (positionManager == null) return;
        
        String symbolToTrade = signal.symbol;
        // Infer side from TP/Entry relationship
        String side = (signal.takeProfit > signal.entryPrice) ? "BUY" : "SELL";
        
        double entryPrice = signal.entryPrice;
        double targetSl = signal.stopLoss;
        double targetTp = signal.takeProfit;

        // Handle Index -> ATM Option Conversion
        if (symbolToTrade.startsWith("NSE_INDEX|") && optionChainProvider != null) {
            OptionChainProvider.OptionData opt = optionChainProvider.getAtmOption(symbolToTrade, side);
            
            if (opt != null) {
                symbolToTrade = opt.symbol;
                entryPrice = opt.ltp;
                side = "BUY"; // Always BUY the option
                
                // For Options, use % based SL/TP (10% SL, 20% TP)
                targetSl = entryPrice * 0.90;
                targetTp = entryPrice * 1.25; // Slightly wider for 2400-trade suppression
                
                logger.info(">>> VERIFIED MAPPING: Index {} ({}) -> Option {} @ {}", signal.symbol, signal.gate, symbolToTrade, entryPrice);
            } else {
                logger.warn(">>> MAPPING FAILED: ATM option not found for {} {}", symbolToTrade, side);
                return; 
            }
        }
        
        // Calculate Quantity based on Risk
        double slDistance = Math.abs(entryPrice - targetSl);
        if (slDistance < (entryPrice * 0.0001)) slDistance = entryPrice * 0.0001; // Safety floor
        
        int quantity = (int) (riskPerTrade / slDistance);
        if (quantity < 1) quantity = 1;
        
        // Cooldown Key usage (Underlying_Gate)
        String gateKey = signal.symbol + "_" + signal.gate.name();

        // Prevent duplicate positions for same tradeable symbol
        if (positionManager.getPosition(symbolToTrade) == null) {
            // Set cooldown IMMEDIATELY on entry to suppress repeats
            gateCooldowns.put(gateKey, signal.timestamp);
            
            activeSignals.put(symbolToTrade, signal);
            signalOriginMap.put(symbolToTrade, gateKey);
            
            positionManager.addPosition(symbolToTrade, quantity, side, entryPrice, signal.timestamp, targetSl, targetTp);
            logger.info("AUTO-EXECUTED [{}]: {} Qty: {} @ {} SL: {} TP: {} (Gate: {})", side, symbolToTrade, quantity, entryPrice, targetSl, targetTp, gateKey);
        }
    }

    private void checkExits(String symbol, VolumeBar bar) {
        if (positionManager == null) return;
        
        Position p = positionManager.getPosition(symbol);
        if (p == null) return;

        double ltp = bar.getClose();
        boolean isLong = "BUY".equals(p.getSide());
        boolean exit = false;
        String reason = "";

        // 1. Check Technical Status from Signal
        ScalpSignal signal = activeSignals.get(symbol);
        if (signal != null && signal.status.startsWith("CLOSED_PENDING_")) {
            exit = true;
            reason = signal.status.replace("CLOSED_PENDING_", "");
        }

        // 2. Dynamic Trailing/BE State Update (Based on Tradeable Price)
        double entry = p.getEntryPrice();
        if (!exit) {
            if (isLong) {
                double highest = extremePriceMap.getOrDefault(symbol, entry);
                if (ltp > highest) {
                    extremePriceMap.put(symbol, ltp);
                    double risk = Math.abs(entry - p.getStopLoss());
                    if (!isBreakEvenMap.getOrDefault(symbol, false) && ltp >= entry + risk) {
                        p.setStopLoss(entry);
                        isBreakEvenMap.put(symbol, true);
                        logger.info("BE_TRIGGERED (Long): {} at {}", symbol, entry);
                    }
                }
                if (ltp <= p.getStopLoss()) { exit = true; reason = "HARD_SL_HIT"; }
                else if (ltp >= p.getTakeProfit()) { exit = true; reason = "HARD_TP_HIT"; }
            } else { // SHORT
                double lowest = extremePriceMap.getOrDefault(symbol, entry);
                if (ltp < lowest) {
                    extremePriceMap.put(symbol, ltp);
                    double risk = Math.abs(entry - p.getStopLoss());
                    if (!isBreakEvenMap.getOrDefault(symbol, false) && ltp <= entry - risk) {
                        p.setStopLoss(entry);
                        isBreakEvenMap.put(symbol, true);
                        logger.info("BE_TRIGGERED (Short): {} at {}", symbol, entry);
                    }
                }
                if (ltp >= p.getStopLoss()) { exit = true; reason = "HARD_SL_HIT"; }
                else if (ltp <= p.getTakeProfit()) { exit = true; reason = "HARD_TP_HIT"; }
            }
        }

        if (exit) {
            extremePriceMap.remove(symbol);
            isBreakEvenMap.remove(symbol);
            String gateKey = signalOriginMap.remove(symbol);
            if (gateKey != null) {
                // Refresh cooldown to be FROM THE EXIT TIME, plus 5 mins
                gateCooldowns.put(gateKey, bar.getStartTime());
            }
            
            positionManager.closePosition(symbol, ltp, bar.getStartTime(), reason);
            activeSignals.remove(symbol);
            
            logger.info("AUTO-EXIT [{}]: {} @ {} Reason: {} PnL: {} (Gate Refreshed: {})", 
                p.getSide(), symbol, ltp, reason, (ltp - p.getEntryPrice()) * (isLong ? 1 : -1) * p.getQuantity(), gateKey);
        }
    }
    
    public Map<String, ScalpSignal> getActiveSignals() {
        return activeSignals;
    }
}

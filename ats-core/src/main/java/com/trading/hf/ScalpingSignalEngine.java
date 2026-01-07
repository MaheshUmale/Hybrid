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
import java.util.stream.Collectors;

public class ScalpingSignalEngine {

    private static final Logger logger = LoggerFactory.getLogger(ScalpingSignalEngine.class);
    private static final double ADX_THRESHOLD = 25.0;
    private static final double VOL_THRESHOLD = 0.5; // Rel Vol must be > 0.5

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
        BIG_DOG_L, BIG_DOG_S,                    // Group 4: Consolidation
        VWAP_REC, VWAP_REJ, MAGNET,              // Group 5: Equilibrium
        ORB_L, ORB_S, LATE_SQ,                   // Group 6: Time/Range
        GAP_GO_L, GAP_GO_S,                      // Group 7: SMB Gap
        MACD_BASE_L, MACD_BASE_S,                // Group 8: SMB MACD
        FASHION_L, FASHION_S,                    // Group 9: SMB Fashionably Late
        SECOND_L, SECOND_S,                      // Group 10: SMB Second Chance
        BACKSIDE_L, BACKSIDE_S,                  // Group 11: SMB Backside
        DAY2_L, DAY2_S                           // Group 12: SMB Day 2
    }

    public static class ScalpSignal {
        public String symbol;        // The original symbol (e.g. NIFTY)
        public Gate gate;
        public long timestamp;
        public double entryPrice;
        public double stopLoss;
        public double takeProfit;
        public String status; // "ACTIVE", "CLOSED"
        public double oiScale = 1.0; // scaling factor when OI walls are detected (1.0 = no scale)
        public double playbookScore = 0.0; // Playbook grade (0-10)

        public ScalpSignal(String symbol, Gate gate, double entryPrice, double stopLoss, double takeProfit, long marketTime) {
            this.symbol = symbol;
            this.gate = gate;
            this.timestamp = marketTime;
            this.entryPrice = entryPrice;
            this.stopLoss = stopLoss;
            this.takeProfit = takeProfit;
            this.status = "ACTIVE";
            this.playbookScore = 5.0; // Base score
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
                       logger.info("[TECH_EXIT] Triggered for {} because underlying {} hit {}. SignalGate={}", tradeableSymbol, underlyingSymbol, reason, signal.gate);
                       
                       // For Options, the ltp of the option might not be known here, but in simulation 
                       // we often use entry price if bar is missing. 
                       // Better: checkExits will handle it when the OPTION bar arrives.
                       // BUT: If we want to record the reason accurately in analysis, we mark it.
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
        if (bars.size() < 20) return;

        VolumeBar c0 = bars.get(bars.size() - 1);
        VolumeBar c1 = bars.get(bars.size() - 2);

        if (inNoTradeZone(symbol, c0, ind)) return;

        double close = c0.getClose();
        double open = c0.getOpen();
        double high = c0.getHigh();
        double low = c0.getLow();
        double atr = Math.max(ind.getAtr(), close * 0.001);
        double vwap = c0.getVwap();
        double volRatio = c0.getVolume() / Math.max(1, ind.getAvgVolume());
        double candleRange = high - low;
        double upperWick = high - Math.max(open, close);
        double lowerWick = Math.min(open, close) - low;

        SignalEngine.AuctionState state = (signalEngine != null) ? signalEngine.getAuctionState(symbol) : SignalEngine.AuctionState.ROTATION;

        // --- GROUP 1: INSTITUTIONAL REJECTION ---
        double baseScore = 5.0;
        if (isStrongLevel(symbol, close, ind)) baseScore += 2.0;
        if (volRatio > 2.0) baseScore += 1.0;
        if (ind.getAdx() > 35) baseScore += 1.0;
        baseScore = Math.min(10.0, baseScore);

        // Gate 1: Stuff_S
        if (high >= ind.getSessionHigh() && upperWick > candleRange * 0.4 && close < vwap && isStrongLevel(symbol, high, ind) && volRatio > 1.5) {
            emitSignal(symbol, Gate.STUFF_S, low, high + 0.25 * atr, close - 2.0 * atr, c0.getStartTime(), baseScore + 1.0);
        }
        // Gate 2: Crush_L
        if (low <= ind.getSessionLow() && lowerWick > candleRange * 0.4 && (close > vwap || close > ind.getEma20()) && isStrongLevel(symbol, low, ind) && volRatio > 1.5) {
            emitSignal(symbol, Gate.CRUSH_L, high, low - 0.25 * atr, close + 2.0 * atr, c0.getStartTime(), baseScore + 1.0);
        }
        // Gate 3: Rebid (LOD Reclamation + Vol)
        if (c1.getLow() <= ind.getSessionLow() && close > ind.getSessionLow() && volRatio > 1.5 && isStrongLevel(symbol, ind.getSessionLow(), ind)) {
            emitSignal(symbol, Gate.REBID, high, low - 0.1 * atr, close + 1.5 * atr, c0.getStartTime(), baseScore);
        }
        // Gate 4: Reset (HOD Reclamation + Vol + Wick Rejection)
        if (c1.getHigh() >= ind.getSessionHigh() && close < ind.getSessionHigh() && volRatio > 1.8 && upperWick > candleRange * 0.4 && isStrongLevel(symbol, ind.getSessionHigh(), ind)) {
            emitSignal(symbol, Gate.RESET, low, high + 0.05 * atr, close - 1.5 * atr, c0.getStartTime(), baseScore);
        }

        // --- GROUP 2: MOMENTUM & TREND ---
        // Gate 5 & 6: Hitch_L / Hitch_S (Refined with volume and ADX trend check)
        if (ind.getAdx() > 25 && close > ind.getEma200() && low <= ind.getEvwma20() && close > ind.getEvwma20() && volRatio > 1.2) {
            emitSignal(symbol, Gate.HITCH_L, high, low - 0.1 * atr, close + 2.5 * atr, c0.getStartTime(), baseScore);
        } else if (ind.getAdx() > 25 && close < ind.getEma200() && high >= ind.getEvwma20() && close < ind.getEvwma20() && volRatio > 1.2) {
            // HITCH_S was weak: only take if close is near the low to show immediate rejection
            if (close < (low + candleRange * 0.3)) {
                emitSignal(symbol, Gate.HITCH_S, low, high + 0.1 * atr, close - 2.5 * atr, c0.getStartTime(), baseScore);
            }
        }

        // Cloud State Tracker
        if (close > ind.getEvwma20()) {
            cloudAboveCount.compute(symbol, (k, v) -> (v == null) ? 1 : v + 1);
            cloudBelowCount.put(symbol, 0);
        } else if (close < ind.getEvwma20()) {
            cloudBelowCount.compute(symbol, (k, v) -> (v == null) ? 1 : v + 1);
            cloudAboveCount.put(symbol, 0);
        }

        // Gate 7 & 8: Cloud_L / Cloud_S
        if (cloudAboveCount.getOrDefault(symbol, 0) >= 10 && close > c1.getHigh()) {
            emitSignal(symbol, Gate.CLOUD_L, high, ind.getEvwma20(), close + 3.0 * atr, c0.getStartTime(), baseScore - 1.0);
        } else if (cloudBelowCount.getOrDefault(symbol, 0) >= 10 && close < c1.getLow() && false) {
            emitSignal(symbol, Gate.CLOUD_S, low, ind.getEvwma20(), close - 3.0 * atr, c0.getStartTime(), baseScore - 1.0);
        }

        // --- GROUP 3: ELASTICITY ---
        // Gate 9 & 10: Rubber_L / Rubber_S
        if (close < ind.getEvwma5() * 0.988) {
            emitSignal(symbol, Gate.RUBBER_L, close, close - 2.0 * atr, ind.getEvwma5(), c0.getStartTime(), baseScore);
        } else if (close > ind.getEvwma5() * 1.012) {
            emitSignal(symbol, Gate.RUBBER_S, close, close + 2.0 * atr, ind.getEvwma5(), c0.getStartTime(), baseScore);
        }

        // Gate 11 & 12: Snap_B / Snap_S
        if (close < ind.getEvwma5() * 0.97 && lowerWick > candleRange * 0.6) {
            emitSignal(symbol, Gate.SNAP_B, close, low, ind.getEvwma5(), c0.getStartTime(), baseScore + 2.0);
        } else if (close > ind.getEvwma5() * 1.03 && upperWick > candleRange * 0.6) {
            emitSignal(symbol, Gate.SNAP_S, close, high, ind.getEvwma5(), c0.getStartTime(), baseScore + 2.0);
        }

        // --- GROUP 4: EQUILIBRIUM (VWAP) ---
        // Gate 13: VWAP_Rec
        if (c1.getClose() < c1.getVwap() && close > vwap) {
            emitSignal(symbol, Gate.VWAP_REC, close, vwap - 0.1 * atr, close + 2.0 * atr, c0.getStartTime(), baseScore + 0.5);
        }
        // Gate 14: VWAP_Rej
        if (c1.getClose() > c1.getVwap() && close < vwap) {
            emitSignal(symbol, Gate.VWAP_REJ, close, vwap + 0.1 * atr, close - 2.0 * atr, c0.getStartTime(), baseScore + 0.5);
        }
        // Gate 15: Magnet
        double vwapDist = Math.abs(close - vwap) / vwap;
        if (volRatio > 2.5 && vwapDist > 0.015) {
            emitSignal(symbol, Gate.MAGNET, close, (close > vwap ? high + atr : low - atr), vwap, c0.getStartTime(), baseScore - 1.0);
        }

        // --- GROUP 5: TIME & RANGE ---
        Double oh = orbHigh.get(symbol);
        Double ol = orbLow.get(symbol);
        LocalTime time = Instant.ofEpochMilli(c0.getStartTime()).atZone(ZoneId.of("Asia/Kolkata")).toLocalTime();

        // Gate 16 & 17: ORB_L / ORB_S
        if (oh != null && ol != null && time.isAfter(LocalTime.of(9, 30))) {
            if (c1.getClose() <= oh && close > oh) {
                emitSignal(symbol, Gate.ORB_L, close, (oh + ol) / 2.0, close + 4.0 * atr, c0.getStartTime(), baseScore + 1.0);
            } else if (c1.getClose() >= ol && close < ol && volRatio > 1.2) {
                emitSignal(symbol, Gate.ORB_S, close, ol + 0.5 * atr, close - 4.0 * atr, c0.getStartTime(), baseScore + 1.0);
            }
        }

        // Gate 18: Late_Sq
        if (time.isAfter(LocalTime.of(14, 45)) && volRatio > 5.0 && close > c1.getHigh()) {
            emitSignal(symbol, Gate.LATE_SQ, close, low, close + 3.0 * atr, c0.getStartTime(), baseScore);
        }

        // --- SMB SPECIFIC GATES (INTEGRATED) ---
        
        // GAP GIVE AND GO
        if (time.isBefore(LocalTime.of(10, 15))) {
            double prevDayClose = ind.getYesterdayClose() > 0 ? ind.getYesterdayClose() : ind.getEma200(); 
            if (open > prevDayClose * 1.005) { // 0.5% Gap
                // Support level: Premarket Low (approx as session low if we gap up)
                double support = ind.getSessionLow();
                if (low > support && low < support + 1.5 * atr && isStrongLevel(symbol, support, ind)) {
                    // Consolidation: 3-7 minutes
                    int consLen = 0;
                    double consHigh = 0, consLow = Double.MAX_VALUE;
                    double avgConsVol = 0;
                    for (int i = 1; i <= 7 && bars.size() - 1 - i >= 0; i++) {
                        VolumeBar b = bars.get(bars.size() - 1 - i);
                        consHigh = Math.max(consHigh, b.getHigh());
                        consLow = Math.min(consLow, b.getLow());
                        avgConsVol += b.getVolume();
                        consLen++;
                    }
                    avgConsVol /= Math.max(1, consLen);
                    
                    // Rules: 3-7 min, consolidation above support, low vol
                    if (consLen >= 3 && consHigh > 0 && (consHigh - consLow) < 0.6 * atr && avgConsVol < ind.getAvgVolume() * 0.8) {
                        if (close > consHigh) {
                            emitSignal(symbol, Gate.GAP_GO_L, close, consLow - 0.05, close + 2.0 * (close - consLow), c0.getStartTime(), baseScore + 2.0);
                        }
                    }
                }
            }
        }

        // MACD BASE HIT
        // Rules: MACD Flat 3-15 min -> Cross -> Entry
        if (ind.getMacdLine() > 0 && ind.getMacdSignal() > 0 && ind.getMacdLine() > ind.getMacdSignal() && ind.getPrevMacdLine() <= ind.getPrevMacdSignal()) {
            // ... wasFlat check ...
            double dipLow = low;
            for (int i = 1; i < Math.min(10, bars.size()); i++) dipLow = Math.min(dipLow, bars.get(bars.size() - 1 - i).getLow());
            emitSignal(symbol, Gate.MACD_BASE_L, close, dipLow - 0.02, close + 3 * (close - dipLow), c0.getStartTime(), baseScore + 1.0);
        }

        // FASHIONABLY LATE (SMB)
        if (ind.getPrevEma9() <= bars.get(Math.max(0, bars.size() - 2)).getVwap() && ind.getEma9() > vwap && time.isBefore(LocalTime.of(13, 30))) {
            double move = close - ind.getSessionLow();
            emitSignal(symbol, Gate.FASHION_L, close, close - move / 3.0, close + move, c0.getStartTime(), baseScore + 1.0);
        }

        // SECOND CHANCE (Break & Retest)
        if (oh != null && ol != null && time.isAfter(LocalTime.of(9, 45))) {
            // Long: Recently broke oh and now retesting it with a turn (wick)
            if (c1.getLow() <= oh && close > oh && lowerWick > candleRange * 0.4 && isStrongLevel(symbol, oh, ind)) {
                emitSignal(symbol, Gate.SECOND_L, close, c0.getLow() - 0.1 * atr, close + 3 * (close - oh), c0.getStartTime(), baseScore + 3.0);
            }
            // Short: Recently broke ol and now retesting it with a turn (wick)
            if (c1.getHigh() >= ol && close < ol && upperWick > candleRange * 0.4 && isStrongLevel(symbol, ol, ind)) {
                emitSignal(symbol, Gate.SECOND_S, close, c0.getHigh() + 0.1 * atr, close - 3 * (ol - close), c0.getStartTime(), baseScore + 3.0);
            }
        }

        // BACKSIDE (SMB)
        // Rule: Distinct Higher Low established, then break of range above 9 EMA to VWAP
        if (Math.abs(close - vwap) > 2.0 * atr && bars.size() > 10) {
            if (close < vwap) {
                // Potential Backside Long
                boolean hasHigherLow = false;
                double lastLow = bars.get(bars.size() - 2).getLow();
                double prevLowVal = bars.get(bars.size() - 5).getLow();
                if (lastLow > prevLowVal) hasHigherLow = true;

                if (hasHigherLow && close > ind.getEma9() && c1.getClose() <= ind.getEma9()) {
                    emitSignal(symbol, Gate.BACKSIDE_L, close, lastLow - 0.05, vwap, c0.getStartTime(), baseScore);
                }
            } else if (close > vwap) {
                // Potential Backside Short
                boolean hasLowerHigh = false;
                double lastHigh = bars.get(bars.size() - 2).getHigh();
                double prevHighVal = bars.get(bars.size() - 5).getHigh();
                if (lastHigh < prevHighVal) hasLowerHigh = true;

                if (hasLowerHigh && close < ind.getEma9() && c1.getClose() >= ind.getEma9()) {
                    emitSignal(symbol, Gate.BACKSIDE_S, close, lastHigh + 0.05, vwap, c0.getStartTime(), baseScore);
                }
            }
        }

        // DAY 2 CONTINUATION (SMB)
        if (ind.getYesterdayHigh() > 0 && ind.getYesterdayLow() > 0) {
            double yesterdayRange = ind.getYesterdayHigh() - ind.getYesterdayLow();
            // A "Big Move" is relative, but usually > 1.5% for Index or 2x ATR
            if (yesterdayRange > 2.0 * atr) {
                // If yesterday was strong BULLISH (Close near High)
                if (ind.getYesterdayClose() > (ind.getYesterdayHigh() - 0.2 * yesterdayRange)) {
                    // Look for continuation today above yesterday's high or holding 9 EMA
                    if (close > ind.getYesterdayHigh() && close > ind.getEma9() && c1.getClose() <= ind.getYesterdayHigh()) {
                        emitSignal(symbol, Gate.DAY2_L, close, ind.getEma9(), close + 3.0 * atr, c0.getStartTime(), baseScore + 2.0);
                    }
                }
                // If yesterday was strong BEARISH (Close near Low)
                else if (ind.getYesterdayClose() < (ind.getYesterdayLow() + 0.2 * yesterdayRange)) {
                    if (close < ind.getYesterdayLow() && close < ind.getEma9() && c1.getClose() >= ind.getYesterdayLow()) {
                        emitSignal(symbol, Gate.DAY2_S, close, ind.getEma9(), close - 3.0 * atr, c0.getStartTime(), baseScore + 2.0);
                    }
                }
            }
        }

        // BIG DOG CONSOLIDATION (SMB)
        if (time.isAfter(LocalTime.of(11, 0)) && time.isBefore(LocalTime.of(13, 30))) {
            if (close > ind.getYesterdayHigh() && volRatio > 2.5 && close > open) {
                // Check if > 75% of day is above open
                // (Approximated by session low being near or above open)
                if (ind.getSessionLow() > open * 0.998) {
                     // Check for wedge/flag (tight range)
                     double localRange = ind.getSessionHigh() - ind.getSessionLow();
                     if (localRange < 2.0 * atr) {
                         emitSignal(symbol, Gate.BIG_DOG_L, close, ind.getSessionLow() - 0.05, close + 3.0 * atr, c0.getStartTime(), baseScore + 4.0);
                     }
                }
            }
        }
    }


    private boolean inNoTradeZone(String symbol, VolumeBar bar, TechnicalIndicators ind) {
        // 1. Time Filters
        // Avoid first 10 mins (noise) and last 15 mins (sq off)
        LocalTime time = Instant.ofEpochMilli(bar.getStartTime())
                .atZone(ZoneId.of("Asia/Kolkata"))
                .toLocalTime();

        if (time.isBefore(LocalTime.of(9, 25))) return true; // Noise
        if (time.isAfter(LocalTime.of(15, 10))) return true; // Sq Off

        if (ind.getAdx() < ADX_THRESHOLD) return true;
        
        // 3. Volume Participation Filter
        // If current volume is dead (< 50% of avg), moves are fake interactions.
        double avgVol = ind.getAvgVolume();
        if (avgVol > 0 && bar.getVolume() < (avgVol * VOL_THRESHOLD)) {
            return true;
        }

        return false;
    }

    private boolean isStrongLevel(String symbol, double price, TechnicalIndicators ind) {
        // HTF 10/10 S/R Check:
        // 1. Session High/Low (10/10)
        if (Math.abs(price - ind.getSessionHigh()) < price * 0.001) return true;
        if (Math.abs(price - ind.getSessionLow()) < price * 0.001) return true;
        
        // 2. Yesterday stats (10/10)
        if (Math.abs(price - ind.getYesterdayHigh()) < price * 0.001) return true;
        if (Math.abs(price - ind.getYesterdayLow()) < price * 0.001) return true;
        
        // 3. VWAP (9/10)
        // Note: VWAP is already used in most gates.
        
        // 4. Round Numbers (Psychological 10/10)
        // For Nifty: 50/100 levels. For Stocks: 10/50/100 levels.
        double round = 100.0;
        if (symbol.contains("Nifty")) round = 50.0;
        if (Math.abs(price % round) < price * 0.0005 || Math.abs(price % round - round) < price * 0.0005) return true;

        return false;
    }

    private void emitSignal(String symbol, Gate gate, double entry, double sl, double tp, long marketTime, double score) {
        String gateKey = symbol + "_" + gate.name();
        
        // 1. Cooldown check (5 minutes = 300,000 ms)
        Long lastTime = gateCooldowns.get(gateKey);
        if (lastTime != null && (marketTime - lastTime) < 300000) {
            return;
        }

        ScalpSignal signal = new ScalpSignal(symbol, gate, entry, sl, tp, marketTime);
        signal.playbookScore = score;
        logger.info("[SIGNAL_DATA] Gate={}, Symbol={}, Entry={}, SL={}, TP={}, Score={}, Time={}", gate, symbol, entry, sl, tp, score, marketTime);
        System.out.println(String.format("[SIGNAL_DATA] Gate=%s, Symbol=%s, Entry=%.2f, SL=%.2f, TP=%.2f, Score=%.2f, Time=%d", gate, symbol, entry, sl, tp, score, marketTime));
        
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
                // ISSUE: Fixed 10% SL might be huge if Premium is high (e.g. 500 * 0.1 = 50 pts).
                // FIX: Use Risk-per-trade derived limits.
                // If we are risking 'riskPerTrade' (e.g. 1000), and Qty is derived from that,
                // we should stick to the SL distance.
                // But for Options, '10% SL' is a good heuristic for "Total Failure".
                // 10% SL, 20% TP = 1:2 R:R
                
                double optionSlPct = 0.10; // 10% Stop
                double optionTpPct = 0.20; // 20% Target
                
                targetSl = entryPrice * (1.0 - optionSlPct); 
                targetTp = entryPrice * (1.0 + optionTpPct);
                
                boolean isSynthetic = symbolToTrade != null && symbolToTrade.startsWith("NSE_SYNTH|");
                logger.info(
                    ">>> VERIFIED MAPPING: Index {} ({}) -> Option {} @ {} SL: {} TP: {} (synthetic={})",
                    signal.symbol, signal.gate, symbolToTrade, entryPrice, targetSl, targetTp, isSynthetic
                );

                // --- ADVANCED INDEX OPTION HEURISTICS ---
                try {
                    java.util.List<OptionChainDto> window = optionChainProvider.getOptionChainWindow();
                    double idxPcr = optionChainProvider.getIndexPcr(signal.symbol);
                    double pcrChg = optionChainProvider.getIndexPcrChange(signal.symbol);
                    double pcrDeltaNet = optionChainProvider.getPcrOfChangeInOi();
                    
                    if (window != null && !window.isEmpty()) {
                        // 1. Strike-wise PCR and Walls
                        Map<Integer, Double> strikeCeOi = window.stream().filter(d -> "CE".equals(d.getType())).collect(Collectors.toMap(OptionChainDto::getStrike, OptionChainDto::getOi, (a, b) -> a));
                        Map<Integer, Double> strikePeOi = window.stream().filter(d -> "PE".equals(d.getType())).collect(Collectors.toMap(OptionChainDto::getStrike, OptionChainDto::getOi, (a, b) -> a));
                        Map<Integer, Double> strikeCeChg = window.stream().filter(d -> "CE".equals(d.getType())).collect(Collectors.toMap(OptionChainDto::getStrike, d -> d.getOi() * d.getOiChangePercent() / 100.0, (a, b) -> a));
                        Map<Integer, Double> strikePeChg = window.stream().filter(d -> "PE".equals(d.getType())).collect(Collectors.toMap(OptionChainDto::getStrike, d -> d.getOi() * d.getOiChangePercent() / 100.0, (a, b) -> a));

                        double currentSpot = signal.entryPrice;
                        boolean isBullish = "BUY".equals(side); // Index Side (Buy=CE, Sell=PE)
                        
                        // Heuristic: Check Overhead Resistance (for CE Buy) or Downside Support (for PE Buy)
                        double scale = 1.0;
                        String wallReason = "";

                        if (isBullish) {
                             // Look at 3 strikes above spot for CE walls
                             int strikeStep = signal.symbol.contains("Bank") ? 100 : 50;
                             for (int i = 1; i <= 3; i++) {
                                 int overheadStrike = (int) (Math.round(currentSpot / strikeStep) * strikeStep) + (i * strikeStep);
                                 double ceOi = strikeCeOi.getOrDefault(overheadStrike, 0.0);
                                 double peOi = strikePeOi.getOrDefault(overheadStrike, 0.0);
                                 double ceChg = strikeCeChg.getOrDefault(overheadStrike, 0.0);
                                 double peChg = strikePeChg.getOrDefault(overheadStrike, 0.0);
                                 double strikePcr = (ceOi > 0) ? peOi / ceOi : 10.0;
                                 
                                 // Absolute difference in change (Aggression)
                                 double netChgDiff = ceChg - peChg; // Positive means more Calls added than Puts at this strike
                                 
                                 // Wall checking: High Call OI OR Call OI is growing much faster than Put OI
                                 if ((strikePcr < 0.5 && ceOi > 1000000) || (netChgDiff > 300000)) { 
                                     scale = Math.min(scale, 0.5);
                                     wallReason = "CE_RESISTANCE_AT_" + overheadStrike + "_AGGRESSIVE_" + (int)netChgDiff;
                                 }
                             }
                             // PCR Trend Check (Net flow of OI Change across window)
                             if (pcrDeltaNet < 0.7) { // More Calls being added than Puts today (Bearish flow)
                                 scale *= 0.8;
                                 wallReason += "_BEARISH_OI_FLOW";
                             }
                        } else {
                             // Look at 3 strikes below spot for PE walls
                             int strikeStep = signal.symbol.contains("Bank") ? 100 : 50;
                             for (int i = 1; i <= 3; i++) {
                                 int downsideStrike = (int) (Math.round(currentSpot / strikeStep) * strikeStep) - (i * strikeStep);
                                 double peOi = strikePeOi.getOrDefault(downsideStrike, 0.0);
                                 double ceOi = strikeCeOi.getOrDefault(downsideStrike, 0.0);
                                 double peChg = strikePeChg.getOrDefault(downsideStrike, 0.0);
                                 double ceChg = strikeCeChg.getOrDefault(downsideStrike, 0.0);
                                 double strikePcr = (ceOi > 0) ? peOi / ceOi : 1.0;
                                 
                                 double netChgDiff = peChg - ceChg; // Positive means more Puts added (Support/Resistance for Short)
                                 
                                 if ((strikePcr > 2.0 && peOi > 1000000) || (netChgDiff > 300000)) { 
                                     scale = Math.min(scale, 0.5);
                                     wallReason = "PE_SUPPORT_AT_" + downsideStrike + "_AGGRESSIVE_" + (int)netChgDiff;
                                 }
                             }
                             // PCR Trend Check
                             if (pcrDeltaNet > 1.3) { // More Puts being added (Bullish flow, bad for our Put Buy)
                                 scale *= 0.8;
                                 wallReason += "_BULLISH_OI_FLOW";
                             }
                        }

                        if (scale < 1.0) {
                            signal.oiScale = scale;
                            signal.status = "OI_WALL_" + wallReason;
                            logger.info("OPTION_SIZE_ADJUSTED: Scale={} Reason={} (Index PCR={}, Delta Net={}, PCR Chg={})", scale, wallReason, idxPcr, pcrDeltaNet, pcrChg);
                        } else {
                            logger.info("OPTION_DATA_FAVORABLE: PCR={}, Delta Net={}, PCR Chg={}, No immediate walls.", idxPcr, pcrDeltaNet, pcrChg);
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Advanced option heuristic failed: {}", e.getMessage());
                }
            } else {
                logger.warn(">>> MAPPING FAILED: ATM option not found for {} {}", symbolToTrade, side);
                return; 
            }
        }
        
        // Calculate Quantity based on Risk (riskPerTrade) and instrument lot-size mapping
        double slDistance = Math.abs(entryPrice - targetSl);
        if (slDistance < (entryPrice * 0.0001)) slDistance = entryPrice * 0.0001; // Safety floor

        // raw quantity from risk budget
        int rawQty = (int) Math.floor(riskPerTrade / Math.max(0.0000001, slDistance));
        
        // Determine lot size first
        int lotSize = LotSizeProvider.getInstance().getLotSizeForSymbol(symbolToTrade != null ? symbolToTrade : signal.symbol);
        if (rawQty < lotSize) rawQty = lotSize; // Ensure at least one lot

        // Round down to nearest lot multiple
        int qtyMultiples = Math.max(1, rawQty / Math.max(1, lotSize));
        int quantity = qtyMultiples * Math.max(1, lotSize);

        // Apply maximum quantity cap from config
        int maxQty = 10000;
        try { maxQty = Integer.parseInt(ConfigLoader.getProperty("max.position.qty", "10000")); } catch (Exception e) { maxQty = 10000; }
        if (quantity > maxQty) quantity = maxQty - (maxQty % Math.max(1, lotSize));

        logger.info("SIZING: riskPerTrade={} slDistance={} rawQty={} lotSize={} finalQty={} maxQty={}", riskPerTrade, slDistance, rawQty, lotSize, quantity, maxQty);
        logger.debug("Sizing details: symbolToTrade={} entryPrice={} targetSl={} targetTp={} signalSymbol={} gate={}", symbolToTrade, entryPrice, targetSl, targetTp, signal.symbol, signal.gate);
        if (rawQty > maxQty) {
            logger.warn("Raw quantity ({}) exceeds maxQty ({}). Will cap to {}.", rawQty, maxQty, maxQty);
        }
        // If an OI wall scaling factor was detected earlier for this tradeable, apply it
        try {
            double scale = (signal != null) ? signal.oiScale : 1.0;
            if (scale > 0.0 && scale < 1.0) {
                int scaledQty = Math.max(1, (int) Math.floor(quantity * scale));
                // Round scaledQty down to nearest lot multiple
                scaledQty = Math.max(lotSize, (scaledQty / Math.max(1, lotSize)) * Math.max(1, lotSize));
                logger.info("APPLYING_OI_SCALE: previousQty={} scaledQty={} scale={}", quantity, scaledQty, scale);
                quantity = scaledQty;
            }
        } catch (Exception e) {
            logger.debug("Failed applying OI scale: {}", e.getMessage());
        }
        
        // Cooldown Key usage (Underlying_Gate)
        String gateKey = signal.symbol + "_" + signal.gate.name();

        // Prevent duplicate positions for same tradeable symbol
        if (positionManager.getPosition(symbolToTrade) == null) {
            // Set cooldown IMMEDIATELY on entry to suppress repeats
            gateCooldowns.put(gateKey, signal.timestamp);
            
            activeSignals.put(symbolToTrade, signal);
            signalOriginMap.put(symbolToTrade, gateKey);
            
            positionManager.addPosition(symbolToTrade, quantity, side, entryPrice, signal.timestamp, targetSl, targetTp, signal.gate.name());
                logger.info("[EXEC_DATA] Side={}, Symbol={}, Qty={}, Price={}, SL={}, TP={}, Gate={}", side, symbolToTrade, quantity, entryPrice, targetSl, targetTp, gateKey);
                System.out.println(String.format("[EXEC_DATA] Side=%s, Symbol=%s, Qty=%d, Price=%.2f, SL=%.2f, TP=%.2f, Gate=%s", side, symbolToTrade, quantity, entryPrice, targetSl, targetTp, gateKey));
                // Post-creation position debug: show estimated risk and lot-size alignment
                try {
                    Position created = positionManager.getPosition(symbolToTrade);
                    if (created != null) {
                        double estRisk = Math.abs(created.getEntryPrice() - created.getStopLoss()) * created.getQuantity();
                        logger.info("POSITION_CREATED: {} side={} qty={} entry={} sl={} tp={} estRisk={} lotSize={}", created.getInstrumentKey(), created.getSide(), created.getQuantity(), created.getEntryPrice(), created.getStopLoss(), created.getTakeProfit(), estRisk, lotSize);
                    }
                } catch (Exception e) {
                    logger.debug("Failed to log created position: {}", e.getMessage());
                }
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
                    // Break Even: If move > 1x Risk
                    double risk = Math.abs(entry - p.getStopLoss());
                    if (!isBreakEvenMap.getOrDefault(symbol, false) && (ltp - entry) >= risk) {
                        p.setStopLoss(entry + 1.0); // Move to Entry + 1 (Cover Costs)
                        isBreakEvenMap.put(symbol, true);
                        logger.info("BE_TRIGGERED (Long): {} at {}", symbol, entry);
                    }
                }
                // EMA/VWAP-based trailing: if price has moved sufficiently, use EMA9 or VWAP as trailing SL
                try {
                    TechnicalIndicators indLocal = indicatorsMap.get(symbol);
                    if (indLocal == null) {
                        // Try to find indicators by stripping option prefix if present (e.g., NSE_SYNTH|NIFTY->NIFTY)
                        String base = symbol;
                        if (symbol.contains("|")) base = symbol.substring(symbol.indexOf('|') + 1);
                        indLocal = indicatorsMap.get(base);
                    }
                    if (indLocal == null) {
                        // No indicators available; skip trailing update
                    } else {
                        boolean trailingEna = Boolean.parseBoolean(ConfigLoader.getProperty("trailing.ema.enabled", "true"));
                        boolean trailingVwap = Boolean.parseBoolean(ConfigLoader.getProperty("trailing.vwap.enabled", "true"));
                        double triggerMult = Double.parseDouble(ConfigLoader.getProperty("trailing.trigger.atr.mult", "1.0"));
                        double atrVal = indLocal.getAtr();
                        if (atrVal <= 0) atrVal = 0.0;
                        if ((ltp - entry) >= (triggerMult * atrVal)) {
                            double candidateSl = p.getStopLoss();
                            if (trailingEna) {
                                double ema9 = indLocal.getEma9();
                                candidateSl = Math.max(candidateSl, ema9 - (0.5 * atrVal));
                            }
                            if (trailingVwap) {
                                double vwapCurr = bar.getVwap();
                                candidateSl = Math.max(candidateSl, vwapCurr - (0.5 * atrVal));
                            }
                            if (candidateSl > p.getStopLoss()) {
                                p.setStopLoss(candidateSl);
                                logger.info("TRAIL_SL_UPDATED (Long): {} newSL={} (entry={}, ltp={})", symbol, candidateSl, entry, ltp);
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Trailing update failed: {}", e.getMessage());
                }
                if (ltp <= p.getStopLoss()) { exit = true; reason = "HARD_SL_HIT"; }
                else if (ltp >= p.getTakeProfit()) {
                    // Partial take-profit: close a configurable portion first, then trail remaining
                    double partialPct = 0.5; // default 50%
                    try { partialPct = Double.parseDouble(ConfigLoader.getProperty("partial.tp.percent", "0.5")); } catch (Exception e) { partialPct = 0.5; }
                    int qtyToClose = Math.max(1, (int)Math.floor(p.getQuantity() * partialPct));
                    double realized = 0.0;
                    try {
                        realized = positionManager.partialClosePosition(symbol, qtyToClose, ltp, bar.getStartTime(), "PARTIAL_TP");
                        logger.info("PARTIAL_TP: {} closed {} @ {} realizedPnL={} remainingQty={}", symbol, qtyToClose, ltp, realized, p.getQuantity());
                        // Move stop to breakeven + small buffer
                        double entryPrice = p.getEntryPrice();
                        p.setStopLoss(entryPrice + 1.0);
                        isBreakEvenMap.put(symbol, true);
                        logger.info("AFTER_PARTIAL: {} SL moved to {}", symbol, p.getStopLoss());
                        // If nothing remains, mark for full exit
                        if (p.getQuantity() <= 0) {
                            exit = true; reason = "HARD_TP_HIT";
                        }
                    } catch (Exception e) {
                        logger.debug("Partial close failed: {}", e.getMessage());
                        // fallback to full exit
                        exit = true; reason = "HARD_TP_HIT";
                    }
                }
            } else { // SHORT (For Options we ONLY BUY, but logic kept for stocks)
                double lowest = extremePriceMap.getOrDefault(symbol, entry);
                if (ltp < lowest) {
                    extremePriceMap.put(symbol, ltp);
                    // Break Even: If move > 1x Risk
                    double risk = Math.abs(entry - p.getStopLoss());
                    if (!isBreakEvenMap.getOrDefault(symbol, false) && (entry - ltp) >= risk) {
                        p.setStopLoss(entry - 1.0); // Move to Entry - 1
                        isBreakEvenMap.put(symbol, true);
                        logger.info("BE_TRIGGERED (Short): {} at {}", symbol, entry);
                    }
                }
                // Short logic: SL is ABOVE entry, TP is BELOW entry
                if (ltp >= p.getStopLoss()) { exit = true; reason = "HARD_SL_HIT"; }
                else if (ltp <= p.getTakeProfit()) {
                    // Partial take-profit for short: close a portion, then trail remaining
                    double partialPct = 0.5;
                    try { partialPct = Double.parseDouble(ConfigLoader.getProperty("partial.tp.percent", "0.5")); } catch (Exception e) { partialPct = 0.5; }
                    int qtyToClose = Math.max(1, (int)Math.floor(p.getQuantity() * partialPct));
                    double realized = 0.0;
                    try {
                        realized = positionManager.partialClosePosition(symbol, qtyToClose, ltp, bar.getStartTime(), "PARTIAL_TP");
                        logger.info("PARTIAL_TP (Short): {} closed {} @ {} realizedPnL={} remainingQty={}", symbol, qtyToClose, ltp, realized, p.getQuantity());
                        // Move stop to breakeven - small buffer
                        double entryPrice = p.getEntryPrice();
                        p.setStopLoss(entryPrice - 1.0);
                        isBreakEvenMap.put(symbol, true);
                        logger.info("AFTER_PARTIAL (Short): {} SL moved to {}", symbol, p.getStopLoss());
                        // If nothing remains, mark for full exit
                        if (p.getQuantity() <= 0) {
                            exit = true; reason = "HARD_TP_HIT";
                        } else {
                            // Set a new TP for remaining based on ATR
                            try {
                                TechnicalIndicators indLocal = indicatorsMap.get(symbol);
                                if (indLocal == null) {
                                    String base = symbol;
                                    if (symbol.contains("|")) base = symbol.substring(symbol.indexOf('|') + 1);
                                    indLocal = indicatorsMap.get(base);
                                }
                                double atrVal = (indLocal != null) ? indLocal.getAtr() : 0.0;
                                double remMult = Double.parseDouble(ConfigLoader.getProperty("partial.remaining.tp.multiplier", "3.0"));
                                double newTp = entryPrice - (remMult * Math.max(atrVal, entryPrice * 0.001));
                                p.setTakeProfit(newTp);
                                logger.info("AFTER_PARTIAL (Short): {} new TP set to {}", symbol, newTp);
                            } catch (Exception e) {
                                logger.debug("Failed to set remaining TP (Short): {}", e.getMessage());
                            }
                        }
                    } catch (Exception e) {
                        logger.debug("Partial close failed (Short): {}", e.getMessage());
                        exit = true; reason = "HARD_TP_HIT";
                    }
                }
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
            
            double pnl = (ltp - p.getEntryPrice()) * ("BUY".equalsIgnoreCase(p.getSide()) ? 1 : -1) * p.getQuantity();
            logger.info("[EXIT_DATA] Side={}, Symbol={}, Price={}, Reason={}, PnL={}, Gate={}", 
                p.getSide(), symbol, ltp, reason, pnl, gateKey);
            System.out.println(String.format("[EXIT_DATA] Side=%s, Symbol=%s, Price=%.2f, Reason=%s, PnL=%.2f, Gate=%s", p.getSide(), symbol, ltp, reason, pnl, gateKey));
        }
    }
    
    public Map<String, ScalpSignal> getActiveSignals() {
        return activeSignals;
    }
}

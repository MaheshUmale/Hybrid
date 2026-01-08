package com.trading.hf;

import java.util.ArrayList;
import java.util.List;

public class TechnicalIndicators {

    // --- Stateful Indicators for a single symbol ---
    private double ema20 = 0;
    private double ema9 = 0;
    private double prevEma9 = 0;
    private double ema50 = 0;
    private double ema200 = 0;
    private double atr14 = 0;
    private double evwma20 = 0; // Using 20 period for EVWMA equivalent
    private double ema12 = 0;
    private double ema26 = 0;
    private double macdLine = 0;
    private double prevMacdLine = 0;
    private double macdSignal = 0;
    private double prevMacdSignal = 0;
    private double macdHist = 0;
    private double sessionHigh = 0;
    private double sessionLow = Double.MAX_VALUE;
    private List<Long> recentVolumes = new ArrayList<>();
    private double avgVolume50 = 0;
    private double evwma5 = 0;
    private double prevClose = 0;
    private double prevHigh = 0;
    private double prevLow = 0;
    private double smoothedTR = 0;
    private double smoothedPlusDM = 0;
    private double smoothedMinusDM = 0;
    private double adx = 0;
    private double yesterdayHigh = 0;
    private double yesterdayLow = 0;
    private double yesterdayClose = 0;
    private boolean isYesterdayReversal = false;
    private boolean isInitialized = false;

    public void update(VolumeBar bar) {
        double close = bar.getClose();
        double high = bar.getHigh();
        double low = bar.getLow();
        long volume = bar.getVolume();

        // 1. Session Stats
        sessionHigh = Math.max(sessionHigh, high);
        sessionLow = Math.min(sessionLow, low);

        // 2. Volume MA (Keeping last 50)
        recentVolumes.add(volume);
        if (recentVolumes.size() > 50) recentVolumes.remove(0);
        avgVolume50 = recentVolumes.stream().mapToLong(v -> v).average().orElse(0);

        if (!isInitialized) {
            // First bar initialization
            prevEma9 = close;
            ema9 = close;
            ema20 = close;
            ema50 = close;
            ema200 = close;
            ema12 = close;
            ema26 = close;
            prevMacdLine = 0;
            macdLine = 0;
            prevMacdSignal = 0;
            macdSignal = 0;
            evwma20 = close;
            evwma5 = close;
            atr14 = high - low; // Approximate first TR
            prevClose = close;
            prevHigh = high;
            prevLow = low;
            isInitialized = true;
            return;
        }

        // 3. EMAs
        prevEma9 = ema9;
        ema9 = calculateEMA(close, ema9, 9);
        ema20 = calculateEMA(close, ema20, 20);
        ema50 = calculateEMA(close, ema50, 50);
        ema200 = calculateEMA(close, ema200, 200);
        ema12 = calculateEMA(close, ema12, 12);
        ema26 = calculateEMA(close, ema26, 26);

        // 4. MACD
        prevMacdLine = macdLine;
        macdLine = ema12 - ema26;
        prevMacdSignal = macdSignal;
        macdSignal = calculateEMA(macdLine, macdSignal, 9);
        macdHist = macdLine - macdSignal;

        // 5. EVWMA (Elastic Volume Weighted Moving Average)
        // Standard EVWMA formula: ((prevEVWMA * (period - volume)) + (price * volume)) / period ??
        // Actually simplified version: EMA but time is replaced by volume.
        // Let's use standard VWAP-like smoothing or the provided definition of "Elastic".
        // A common approximation for "Elastic" moving average involves volume weighting the span.
        // For now, implementing standard Volume Weighted EMA logic:
        // alpha = volume / sum_volume_period. keeping it simple with volume pressure approximation.
        // Let's stick to a standard definition:
        // EVWMA = ( (Volume * Price) + ( (Period_Volume - Volume) * Prev_EVWMA ) ) / Period_Volume
        // We will approximate Period_Volume as AvgVolume * Period
        double volPeriod20 = Math.max(avgVolume50 * 20, volume * 20); // Safety floor
        evwma20 = ((volume * close) + ((volPeriod20 - volume) * evwma20)) / volPeriod20;
        
        double volPeriod5 = Math.max(avgVolume50 * 5, volume * 5);
        evwma5 = ((volume * close) + ((volPeriod5 - volume) * evwma5)) / volPeriod5;

        // 5. ATR (14)
        double tr = Math.max(high - low, Math.max(Math.abs(high - prevClose), Math.abs(low - prevClose)));
        atr14 = calculateEMA(tr, atr14, 14); // ATR is essentially an RMA/EMA of TR

        // 6. ADX (14)
        double upMove = high - prevHigh;
        double downMove = prevLow - low;
        double plusDM = (upMove > downMove && upMove > 0) ? upMove : 0;
        double minusDM = (downMove > upMove && downMove > 0) ? downMove : 0;

        // Use RMA (Wilder's Moving Average) for ADX components
        smoothedTR = calculateRMA(tr, smoothedTR, 14);
        smoothedPlusDM = calculateRMA(plusDM, smoothedPlusDM, 14);
        smoothedMinusDM = calculateRMA(minusDM, smoothedMinusDM, 14);

        double plusDI = (smoothedTR == 0) ? 0 : 100 * (smoothedPlusDM / smoothedTR);
        double minusDI = (smoothedTR == 0) ? 0 : 100 * (smoothedMinusDM / smoothedTR);
        double dx = (plusDI + minusDI == 0) ? 0 : 100 * Math.abs(plusDI - minusDI) / (plusDI + minusDI);
        
        adx = calculateRMA(dx, adx, 14);

        prevClose = close;
        prevHigh = high;
        prevLow = low;
    }

    private double calculateEMA(double current, double previous, int period) {
        double multiplier = 2.0 / (period + 1);
        return (current - previous) * multiplier + previous;
    }

    private double calculateRMA(double current, double previous, int period) {
        // RMA (Wilder's) is equivalent to EMA with period = 2*N - 1
        // Or simply: (Prev * (N-1) + Curr) / N
        return ((previous * (period - 1)) + current) / period;
    }

    // Getters
    public double getEma20() { return ema20; }
    public double getEma9() { return ema9; }
    public double getPrevEma9() { return prevEma9; }
    public double getEma50() { return ema50; }
    public double getEma200() { return ema200; }
    public double getAtr() { return atr14; }
    public double getEvwma20() { return evwma20; }
    public double getEvwma5() { return evwma5; }
    public double getYesterdayHigh() { return yesterdayHigh; }
    public double getYesterdayLow() { return yesterdayLow; }
    public double getYesterdayClose() { return yesterdayClose; }
    public boolean isYesterdayReversal() { return isYesterdayReversal; }

    // public void setYesterdayStats(double high, double low, double close, boolean reversal) {
    //     this.yesterdayHigh = high;
    //     this.yesterdayLow = low;
    //     this.yesterdayClose = close;
    //     this.isYesterdayReversal = reversal;
    // }

    public double getSessionHigh() { return sessionHigh; }
    public double getSessionLow() { return sessionLow; }
    public double getAvgVolume() { return avgVolume50; }
    public double getAdx() { return adx; }
    public double getMacdLine() { return macdLine; }
    public double getPrevMacdLine() { return prevMacdLine; }
    public double getMacdSignal() { return macdSignal; }
    public double getPrevMacdSignal() { return prevMacdSignal; }
    public double getMacdHist() { return macdHist; }

    private boolean isDayTwoCandidate = false;

    public boolean isDayTwoCandidate() { return isDayTwoCandidate; }

    public void setYesterdayStats(double high, double low, double close, boolean reversal) {
        this.yesterdayHigh = high;
        this.yesterdayLow = low;
        this.yesterdayClose = close;
        this.isYesterdayReversal = reversal;

        // MMM Step 1: Logic to identify "Big Candle" Day Two Names
        // Criteria: Yesterday's range > 1.5% of price OR > 2.0x ATR
        double bodySize = Math.abs(high - low);
        if (close > 0) {
            double pctMove = (bodySize / close) * 100;
            // Flag as Day Two if move was > 1.5% (suggests forced participation)
            this.isDayTwoCandidate = pctMove > 1.5; 
        }
    }
}


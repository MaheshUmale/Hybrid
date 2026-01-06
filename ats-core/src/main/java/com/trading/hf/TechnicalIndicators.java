package com.trading.hf;

import java.util.ArrayList;
import java.util.List;

public class TechnicalIndicators {

    // --- Stateful Indicators for a single symbol ---
    private double ema20 = 0;
    private double ema50 = 0;
    private double ema200 = 0;
    private double atr14 = 0;
    private double evwma20 = 0; // Using 20 period for EVWMA equivalent
    private double evwma5 = 0;  // For "Rubber Band" logic
    
    // Session High/Low
    private double sessionHigh = Double.MIN_VALUE;
    private double sessionLow = Double.MAX_VALUE;

    // Volume MA
    private final List<Long> recentVolumes = new ArrayList<>();
    private double avgVolume50 = 0;

    // Helper history for ATR
    private double prevClose = 0;

    // Initialization flag
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
            ema20 = close;
            ema50 = close;
            ema200 = close;
            evwma20 = close;
            evwma5 = close;
            atr14 = high - low; // Approximate first TR
            prevClose = close;
            isInitialized = true;
            return;
        }

        // 3. EMAs
        ema20 = calculateEMA(close, ema20, 20);
        ema50 = calculateEMA(close, ema50, 50);
        ema200 = calculateEMA(close, ema200, 200);

        // 4. EVWMA (Elastic Volume Weighted Moving Average)
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

        prevClose = close;
    }

    private double calculateEMA(double current, double previous, int period) {
        double multiplier = 2.0 / (period + 1);
        return (current - previous) * multiplier + previous;
    }

    // Getters
    public double getEma20() { return ema20; }
    public double getEma50() { return ema50; }
    public double getEma200() { return ema200; }
    public double getAtr() { return atr14; }
    public double getEvwma20() { return evwma20; }
    public double getEvwma5() { return evwma5; }
    public double getSessionHigh() { return sessionHigh; }
    public double getSessionLow() { return sessionLow; }
    public double getAvgVolume() { return avgVolume50; }
}

package com.trading.hf;

public class SignalFactory {

    public static ScalpingSignalEngine.ScalpSignal createSignal(
            String symbol,
            ScalpingSignalEngine.Gate gate,
            double entry,
            double sl,
            double tp,
            long marketTime,
            double score) {
        ScalpingSignalEngine.ScalpSignal signal = new ScalpingSignalEngine.ScalpSignal(symbol, gate, entry, sl, tp, marketTime);
        signal.playbookScore = score;
        return signal;
    }
}

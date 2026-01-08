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
       
        if ( sl < tp ) {
            ScalpingSignalEngine.ScalpSignal signal = new ScalpingSignalEngine.ScalpSignal(symbol, "BUY", gate, entry, sl, tp, marketTime);
            signal.playbookScore = score;
            return signal;
        } else {
            ScalpingSignalEngine.ScalpSignal signal = new ScalpingSignalEngine.ScalpSignal(symbol, "SELL", gate, entry, sl, tp, marketTime);
            signal.playbookScore = score;
            return signal;
        }
       
    }
}

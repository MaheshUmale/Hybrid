package com.trading.hf.dashboard;

import java.util.List;

public class DashboardViewModel {
    // Header
    public long timestamp;
    public String symbol;
    public double spot;
    public double future;
    public double basis;
    public double pcr;
    public OhlcViewModel ohlc;

    // System Health
    public long wssLatency;
    public long questDbWriteLag;

    // Auction Profile
    public MarketProfileViewModel auctionProfile;

    // Heavyweights
    public List<HeavyweightViewModel> heavyweights;
    public double weighted_delta;

    // Option Chain
    public List<OptionViewModel> optionChain;

    // Sentiment & Alerts
    public String auctionState;
    public List<String> alerts;
    public List<ScalpSignalViewModel> scalpSignals;
    public List<ActiveTradeViewModel> active_trades;
    public List<ClosedTradeViewModel> closed_trades;

    // Trade Panel
    public double thetaGuard; // in seconds

    // Inner classes for nested structures
    public static class MarketProfileViewModel {
        public double vah;
        public double val;
        public double poc;
    }

    public static class HeavyweightViewModel {
        public String name;
        public double delta;
        public String weight;
        public double price;
        public double change;
        public long qtp;
    }

    public static class OptionViewModel {
        public int strike;
        public String type; // "CE" or "PE"
        public double ltp;
        public double oiChangePercent;
        public String sentiment;
    }

    public static class ScalpSignalViewModel {
        public String symbol;
        public String gate;
        public double entry;
        public double sl;
        public double tp;
        public String status;
    }

    public static class ActiveTradeViewModel {
        public String symbol;
        public String side;
        public double entry;
        public double ltp;
        public int qty;
        public double pnl;
        public long entryTime;
        public String strategy;
        public String reason;
    }

    public static class ClosedTradeViewModel {
        public String symbol;
        public String side;
        public double entry;
        public double exit;
        public double pnl;
        public long entryTime;
        public long exitTime;
        public String strategy;
        public String reason;
    }

    public static class OhlcViewModel {
        public double open;
        public double high;
        public double low;
        public double close;
    }
}

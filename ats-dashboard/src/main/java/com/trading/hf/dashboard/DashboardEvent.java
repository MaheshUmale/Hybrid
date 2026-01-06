package com.trading.hf.dashboard;

import com.google.gson.annotations.SerializedName;
import com.trading.hf.AuctionProfileCalculator;
import com.trading.hf.SignalEngine;
import com.trading.hf.VolumeBar;

import java.util.List;

public class DashboardEvent {
    @SerializedName("timestamp")
    public long timestamp;
    @SerializedName("spot")
    public double spot;
    @SerializedName("future")
    public double future;
    @SerializedName("weighted_delta")
    public double weighted_delta;
    @SerializedName("auction_state")
    public String auction_state;
    @SerializedName("pcr")
    public double pcr;
    @SerializedName("theta_guard_sec")
    public int theta_guard_sec;
    @SerializedName("heavyweights")
    public List<Heavyweight> heavyweights;
    @SerializedName("option_window")
    public List<Option> option_window;
    @SerializedName("market_profile")
    public MarketProfileData market_profile;

    public static class Heavyweight {
        @SerializedName("name")
        public String name;
        @SerializedName("delta")
        public double delta;
        @SerializedName("weight")
        public String weight;

        public Heavyweight(String name, double delta, String weight) {
            this.name = name;
            this.delta = delta;
            this.weight = weight;
        }
    }

    public static class Option {
        @SerializedName("strike")
        public int strike;
        @SerializedName("type")
        public String type;
        @SerializedName("ltp")
        public double ltp;
        @SerializedName("oi_chg")
        public double oi_chg;

        public Option(int strike, String type, double ltp, double oi_chg) {
            this.strike = strike;
            this.type = type;
            this.ltp = ltp;
            this.oi_chg = oi_chg;
        }
    }

    public static class MarketProfileData {
        @SerializedName("vah")
        public double vah;
        @SerializedName("val")
        public double val;
        @SerializedName("poc")
        public double poc;

        public MarketProfileData(AuctionProfileCalculator.MarketProfile profile) {
            this.vah = profile.getVah();
            this.val = profile.getVal();
            this.poc = profile.getPoc();
        }
    }

    public static DashboardEvent from(VolumeBar bar, SignalEngine.AuctionState state, AuctionProfileCalculator.MarketProfile profile) {
        DashboardEvent event = new DashboardEvent();
        event.timestamp = bar.getStartTime();
        event.spot = bar.getClose();
        event.future = bar.getClose() + 15.0; // Placeholder
        event.weighted_delta = bar.getCumulativeVolumeDelta(); // Placeholder using delta
        event.auction_state = state.toString();
        event.pcr = 1.1; // Placeholder
        event.theta_guard_sec = 1200; // Placeholder

        event.heavyweights = List.of(
            new Heavyweight("RELIANCE", 3353, "10.2%"),
            new Heavyweight("HDFC BANK", 1690, "9.1%")
        );
        event.option_window = List.of(
            new Option(24600, "CE", 114.78, 5.2),
            new Option(24650, "CE", 79.57, 12.5),
            new Option(24650, "PE", 81.56, -2.1)
        );

        if (profile != null) {
            event.market_profile = new MarketProfileData(profile);
        }

        return event;
    }
}

package com.trading.hf;

import com.trading.hf.VolumeBar;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public class AuctionProfileCalculator {

    private final Map<String, MarketProfile> profiles = new ConcurrentHashMap<>();

    public void onVolumeBar(VolumeBar volumeBar) {
        MarketProfile profile = profiles.computeIfAbsent(volumeBar.getSymbol(), k -> new MarketProfile());
        synchronized (profile) {
            profile.addVolume(volumeBar.getClose(), volumeBar.getVolume());
            profile.calculateValueArea();
        }
    }

    public MarketProfile getProfile(String symbol) {
        MarketProfile profile = profiles.get(symbol);
        if (profile != null) {
            synchronized (profile) {
                // Return a copy to ensure thread safety for the caller
                return new MarketProfile(profile);
            }
        }
        return null;
    }

    public static class MarketProfile {
        private final TreeMap<Double, Long> volumeAtPrice = new TreeMap<>();
        private double poc;
        private double vah;
        private double val;
        private long totalVolume;

        // Default constructor
        public MarketProfile() {}

        // Copy constructor for thread-safe snapshots
        public MarketProfile(MarketProfile other) {
            this.volumeAtPrice.putAll(other.volumeAtPrice);
            this.poc = other.poc;
            this.vah = other.vah;
            this.val = other.val;
            this.totalVolume = other.totalVolume;
        }

        public void addVolume(double price, long volume) {
            volumeAtPrice.put(price, volumeAtPrice.getOrDefault(price, 0L) + volume);
            totalVolume += volume;
        }

        public void calculateValueArea() {
            if (volumeAtPrice.isEmpty()) {
                return;
            }

            // Find POC
            poc = Collections.max(volumeAtPrice.entrySet(), Map.Entry.comparingByValue()).getKey();

            long vaVolume = (long) (totalVolume * 0.70);
            long currentVolume = volumeAtPrice.get(poc);

            // Expand around POC
            Map.Entry<Double, Long> lowerEntry = volumeAtPrice.lowerEntry(poc);
            Map.Entry<Double, Long> higherEntry = volumeAtPrice.higherEntry(poc);

            vah = poc;
            val = poc;

            while (currentVolume < vaVolume) {
                if (higherEntry == null && lowerEntry == null) {
                    break;
                }

                if (higherEntry != null && (lowerEntry == null || higherEntry.getValue() >= lowerEntry.getValue())) {
                    currentVolume += higherEntry.getValue();
                    vah = higherEntry.getKey();
                    higherEntry = volumeAtPrice.higherEntry(vah);
                } else if (lowerEntry != null) {
                    currentVolume += lowerEntry.getValue();
                    val = lowerEntry.getKey();
                    lowerEntry = volumeAtPrice.lowerEntry(val);
                }
            }
        }

        public double getPoc() {
            return poc;
        }

        public double getVah() {
            return vah;
        }

        public double getVal() {
            return val;
        }

        public TreeMap<Double, Long> getVolumeAtPrice() {
            return volumeAtPrice;
        }
    }
}

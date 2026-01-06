package com.trading.hf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MarketBreadthEngine stores the latest market-wide advance/decline data.
 */
public class MarketBreadthEngine {
    private static final Logger logger = LoggerFactory.getLogger(MarketBreadthEngine.class);
    private final AtomicReference<BreadthSnapshot> latestSnapshot = new AtomicReference<>(new BreadthSnapshot(0, 0, 0, 0));

    public void update(int advances, int declines, int unchanged, int total) {
        BreadthSnapshot next = new BreadthSnapshot(advances, declines, unchanged, total);
        latestSnapshot.set(next);
        logger.info("Market Breadth Updated: Adv: {}, Dec: {}, Total: {}", advances, declines, total);
    }

    public BreadthSnapshot getLatest() {
        return latestSnapshot.get();
    }

    public static class BreadthSnapshot {
        public final int advances;
        public final int declines;
        public final int unchanged;
        public final int total;

        public BreadthSnapshot(int advances, int declines, int unchanged, int total) {
            this.advances = advances;
            this.declines = declines;
            this.unchanged = unchanged;
            this.total = total;
        }

        public double getRatio() {
            return (declines == 0) ? advances : (double) advances / declines;
        }
    }
}

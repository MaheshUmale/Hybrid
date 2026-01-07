package com.trading.hf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShadowBacktestRunner {
    private static final Logger logger = LoggerFactory.getLogger(ShadowBacktestRunner.class);

    public static void main(String[] args) {
        System.setProperty("db.url", "jdbc:sqlite:shadow_backtest.db");
        System.setProperty("mock.todayStart", "1710000000000"); // 2024 or earlier
        
        logger.info("Starting ShadowBacktestRunner - connecting to ws://localhost:8770");
        logger.info("Using Database: shadow_backtest.db");

        // Initialize Listeners
        PositionManager positionManager = new PositionManager();
        AuctionProfileCalculator auctionProfileCalculator = new AuctionProfileCalculator();
        OptionChainProvider optionChainProvider = new OptionChainProvider(positionManager);
        SignalEngine signalEngine = new SignalEngine(auctionProfileCalculator);
        
        // Auto-Execute is TRUE for backtest simulation
        ScalpingSignalEngine scalpingSignalEngine = new ScalpingSignalEngine(positionManager, optionChainProvider, signalEngine, true);

        java.util.function.Consumer<VolumeBar> barHandler = bar -> {
            try {
                auctionProfileCalculator.onVolumeBar(bar);
                signalEngine.onVolumeBar(bar);
                scalpingSignalEngine.onVolumeBar(bar);
            } catch (Exception e) {
                logger.error("Handler error: {}", e.getMessage());
            }
        };

        // Connect to Parallel Bridge on Port 8770
        TVMarketDataStreamer tvStreamer = new TVMarketDataStreamer(barHandler, optionChainProvider, new MarketBreadthEngine(), "ws://localhost:8770");
        tvStreamer.connect();

        logger.info("Shadow Backtest Runner ACTIVE. Waiting for data...");

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            logger.info("Shadow Backtest Runner interrupted.");
        }
    }
}

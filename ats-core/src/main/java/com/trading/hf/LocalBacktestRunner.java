package com.trading.hf;

public class LocalBacktestRunner {
    public static void main(String[] args) {
        System.out.println("Starting LocalBacktestRunner - connecting to ws://localhost:8765");

        boolean dashboardEnabled = false;

        // Initialize Listeners
        PositionManager positionManager = new PositionManager();
        AuctionProfileCalculator auctionProfileCalculator = new AuctionProfileCalculator();
        OptionChainProvider optionChainProvider = new OptionChainProvider(positionManager);
        SignalEngine signalEngine = new SignalEngine(auctionProfileCalculator);
        ScalpingSignalEngine scalpingSignalEngine = new ScalpingSignalEngine(positionManager, optionChainProvider, signalEngine, true);

        InstrumentMaster instrumentMaster = new InstrumentMaster("instrument-master.json");
        IndexWeightCalculator indexWeightCalculator = new IndexWeightCalculator("IndexWeights.json", instrumentMaster);
        MarketBreadthEngine marketBreadthEngine = new MarketBreadthEngine();

        java.util.function.Consumer<VolumeBar> barHandler = bar -> {
            try {
                auctionProfileCalculator.onVolumeBar(bar);
                signalEngine.onVolumeBar(bar);
                scalpingSignalEngine.onVolumeBar(bar);
            } catch (Exception e) {
                System.err.println("Handler error: " + e.getMessage());
            }
        };

        VolumeBarGenerator volumeBarGenerator = new VolumeBarGenerator(1000L, barHandler);

        // Start the TV MarketData Streamer which will connect to the backtest_replay.py websocket
        TVMarketDataStreamer tvStreamer = new TVMarketDataStreamer(barHandler, optionChainProvider, marketBreadthEngine);
        tvStreamer.connect();

        // Keep the process alive
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            // exit
        }
    }
}

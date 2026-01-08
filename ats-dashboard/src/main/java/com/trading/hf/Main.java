package com.trading.hf;

public class Main {

    private static TVMarketDataStreamer tvStreamer = null;

    public static void main(String[] args) {
        // --- Configuration ---
        String runMode = ConfigLoader.getProperty("run.mode", "simulation");

        // --- Initialization ---
        boolean dashboardEnabled = ConfigLoader.getBooleanProperty("dashboard.enabled", true);
        
        // Initialize Listeners
        PositionManager positionManager = new PositionManager();
        
        OptionChainProvider optionChainProvider = new OptionChainProvider(positionManager);
        
        
        ScalpingSignalEngine scalpingSignalEngine = new ScalpingSignalEngine(positionManager, optionChainProvider,  true);
        

        //InstrumentMaster instrumentMaster = new InstrumentMaster("instrument-master.json");
        MarketBreadthEngine marketBreadthEngine = new MarketBreadthEngine();
        
        long volumeThreshold = Long.parseLong(ConfigLoader.getProperty("volume.threshold", "1000"));
        
        java.util.function.Consumer<VolumeBar> barHandler = bar -> {
            
            scalpingSignalEngine.onVolumeBar(bar);
            if (dashboardEnabled) DashboardBridge.onVolumeBar(bar);
        };

        VolumeBarGenerator volumeBarGenerator = new VolumeBarGenerator(volumeThreshold, barHandler);
 

        if (dashboardEnabled) {
            DashboardBridge.start(
                volumeBarGenerator,
                
                optionChainProvider,
                scalpingSignalEngine,
                positionManager
            );
        }

        if ("live".equalsIgnoreCase(runMode) || "simulation".equalsIgnoreCase(runMode)) {
            // Both live and simulation (via backtest_replay.py) use the TVMarketDataStreamer
            System.out.println("Starting application in " + runMode.toUpperCase() + " mode (Connecting to Bridge).");
            String dataSource = ConfigLoader.getProperty("live.data.source", "tradingview");
            
            if ("tradingview".equalsIgnoreCase(dataSource)) {
                String wsUrl = ConfigLoader.getProperty("ws.url", "ws://127.0.0.1:8765");
                System.out.println("Connecting to TV Bridge at: " + wsUrl);
                tvStreamer = new TVMarketDataStreamer(barHandler, optionChainProvider, marketBreadthEngine, wsUrl);
                tvStreamer.connect();

                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    if (tvStreamer != null) tvStreamer.disconnect();
                }));
            } else {
                System.err.println("FATAL: Unsupported data source: " + dataSource);
            }
        } else {
            System.err.println("FATAL: Unknown run.mode: " + runMode);
        }
    }
}

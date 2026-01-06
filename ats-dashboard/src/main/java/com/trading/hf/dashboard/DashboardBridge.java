package com.trading.hf.dashboard;

import com.google.gson.Gson;
import com.trading.hf.*;

import java.util.ArrayList;
import java.util.stream.Collectors;

public class DashboardBridge {
    private static final Gson gson = new Gson();
    private static DashboardService dashboardService = null;
    private static final Object lock = new Object();

    public static void start(
            VolumeBarGenerator volumeBarGenerator,
            SignalEngine signalEngine,
            AuctionProfileCalculator auctionProfileCalculator,
            IndexWeightCalculator indexWeightCalculator,
            OptionChainProvider optionChainProvider,
            ScalpingSignalEngine scalpingSignalEngine,
            PositionManager positionManager
    ) {
        synchronized (lock) {
            if (dashboardService == null) {
                dashboardService = new DashboardService();
                dashboardService.start();
                Runtime.getRuntime().addShutdownHook(new Thread(dashboardService::stop));
            }
        }

        // Configure the static provider references for the update method
        DashboardBridge.signalEngine = signalEngine;
        DashboardBridge.auctionProfileCalculator = auctionProfileCalculator;
        DashboardBridge.indexWeightCalculator = indexWeightCalculator;
        DashboardBridge.optionChainProvider = optionChainProvider;
        DashboardBridge.scalpingSignalEngine = scalpingSignalEngine;
        DashboardBridge.positionManager = positionManager;

        volumeBarGenerator.setDashboardConsumer(DashboardBridge::onVolumeBar);
    }

    private static SignalEngine signalEngine;
    private static AuctionProfileCalculator auctionProfileCalculator;
    private static IndexWeightCalculator indexWeightCalculator;
    private static OptionChainProvider optionChainProvider;
    private static ScalpingSignalEngine scalpingSignalEngine;
    private static PositionManager positionManager;

    public static void onVolumeBar(VolumeBar volumeBar) {
        if (dashboardService == null) return;
        
        // Always update the central price cache for PnL tracking
        if (positionManager != null) {
            positionManager.updateLtp(volumeBar.getSymbol(), volumeBar.getClose());
        }

        // User requested to "focus on Nifty". This filter prevents Adani/Heavyweights 
        // from clearing the dashboard state if the frontend isn't handling multi-symbol gracefully.
        if (!volumeBar.getSymbol().contains("Nifty 50")) return;

        DashboardViewModel viewModel = new DashboardViewModel();

        // 1. Populate Header Info
        viewModel.timestamp = System.currentTimeMillis();
        viewModel.symbol = volumeBar.getSymbol();
        viewModel.spot = volumeBar.getClose();
        
        viewModel.ohlc = new DashboardViewModel.OhlcViewModel();
        viewModel.ohlc.open = volumeBar.getOpen();
        viewModel.ohlc.high = volumeBar.getHigh();
        viewModel.ohlc.low = volumeBar.getLow();
        viewModel.ohlc.close = volumeBar.getClose();

        // 2. Populate Auction Profile
        if (auctionProfileCalculator != null) {
            AuctionProfileCalculator.MarketProfile profile = auctionProfileCalculator.getProfile(volumeBar.getSymbol());
            if (profile != null) {
                viewModel.auctionProfile = new DashboardViewModel.MarketProfileViewModel();
                viewModel.auctionProfile.vah = profile.getVah();
                viewModel.auctionProfile.val = profile.getVal();
                viewModel.auctionProfile.poc = profile.getPoc();
            }
        }

        // 3. Populate Heavyweights
        if (indexWeightCalculator != null) {
            viewModel.heavyweights = indexWeightCalculator.getHeavyweights().values().stream()
                    .map(hw -> {
                        DashboardViewModel.HeavyweightViewModel hwvm = new DashboardViewModel.HeavyweightViewModel();
                        hwvm.name = hw.getName();
                        hwvm.weight = String.format("%.2f%%", hw.getWeight() * 100);
                        hwvm.delta = hw.getDelta();
                        hwvm.price = hw.getPrice();
                        hwvm.change = (hw.getPrice() - hw.getPrevClose()) / hw.getPrevClose() * 100;
                        hwvm.qtp = (long)hw.getVolume(); // Mapping volume to QTP for UI
                        return hwvm;
                    })
                    .collect(Collectors.toList());
            viewModel.weighted_delta = indexWeightCalculator.getAggregateWeightedDelta();
        }

        // 4. Populate Option Chain
        if (optionChainProvider != null) {
            viewModel.optionChain = optionChainProvider.getOptionChainWindow().stream()
                    .map(dto -> {
                        DashboardViewModel.OptionViewModel ovm = new DashboardViewModel.OptionViewModel();
                        ovm.strike = dto.getStrike();
                        ovm.type = dto.getType();
                        ovm.ltp = dto.getLtp();
                        ovm.oiChangePercent = dto.getOiChangePercent();
                        ovm.sentiment = dto.getSentiment();
                        return ovm;
                    })
                    .collect(Collectors.toList());
        }

        // 5. Populate Sentiment & Alerts
        if (signalEngine != null) {
            SignalEngine.AuctionState auctionState = signalEngine.getAuctionState(volumeBar.getSymbol());
            viewModel.auctionState = (auctionState != null) ? auctionState.toString() : "ROTATION";
        }
        viewModel.alerts = new ArrayList<>();

        // 6. Populate Scalping Signals
        if (scalpingSignalEngine != null) {
            viewModel.scalpSignals = scalpingSignalEngine.getActiveSignals().values().stream()
                    .map(s -> {
                        DashboardViewModel.ScalpSignalViewModel svm = new DashboardViewModel.ScalpSignalViewModel();
                        svm.symbol = s.symbol;
                        svm.gate = s.gate.name();
                        svm.entry = s.entryPrice;
                        svm.sl = s.stopLoss;
                        svm.tp = s.takeProfit;
                        svm.status = s.status;
                        return svm;
                    })
                    .collect(Collectors.toList());
        }

        // 7. Populate Active Trades
        if (positionManager != null) {
            viewModel.active_trades = positionManager.getAllPositions().values().stream()
                    .map(p -> {
                        DashboardViewModel.ActiveTradeViewModel avm = new DashboardViewModel.ActiveTradeViewModel();
                        avm.symbol = p.getInstrumentKey();
                        avm.side = p.getSide();
                        avm.entry = p.getEntryPrice();
                        avm.qty = p.getQuantity();
                        // Use the centralized price cache to get the valid LTP for ANY symbol
                        avm.ltp = positionManager.getLtp(p.getInstrumentKey());
                        
                        boolean isLong = p.getSide().trim().equalsIgnoreCase("BUY");
                        double pnlPerUnit;
                        if (isLong) {
                            pnlPerUnit = avm.ltp - p.getEntryPrice();
                        } else {
                            // Assume SHORT for anything else (SELL)
                            pnlPerUnit = p.getEntryPrice() - avm.ltp;
                        }
                        
                        avm.pnl = pnlPerUnit * p.getQuantity();
                        avm.reason = "ALGO_" + p.getSide().toUpperCase();
                        return avm;
                    })
                    .collect(Collectors.toList());
        }

        // 8. Populate Closed Trades
        if (positionManager != null) {
            viewModel.closed_trades = positionManager.getClosedPositions().stream()
                    .map(p -> {
                        DashboardViewModel.ClosedTradeViewModel cvm = new DashboardViewModel.ClosedTradeViewModel();
                        cvm.symbol = p.getInstrumentKey();
                        cvm.side = p.getSide();
                        cvm.entry = p.getEntryPrice();
                        cvm.exit = p.getExitPrice();
                        cvm.pnl = p.getRealizedPnL();
                        cvm.exitTime = p.getExitTimestamp();
                        cvm.reason = p.getExitReason();
                        return cvm;
                    })
                    .collect(Collectors.toList());
        }

        // 9. Populate Trade Panel
        viewModel.thetaGuard = 1200;

        // Serialize and broadcast
        String json = gson.toJson(viewModel);
        dashboardService.broadcast(json);
    }
}

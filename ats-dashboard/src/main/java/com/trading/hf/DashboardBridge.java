 package com.trading.hf;
 
 import com.google.gson.Gson;
 import com.trading.hf.*;
 import com.trading.hf.dashboard.*;
 
 import java.util.ArrayList;
 import java.util.stream.Collectors;
 
 public class DashboardBridge {
     private static final Gson gson = new Gson();
     private static DashboardService dashboardService = null;
     private static final Object lock = new Object();
 
     public static void start(
             VolumeBarGenerator volumeBarGenerator,
             
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
         
      
         DashboardBridge.optionChainProvider = optionChainProvider;
         DashboardBridge.scalpingSignalEngine = scalpingSignalEngine;
         DashboardBridge.positionManager = positionManager;
 
         volumeBarGenerator.setDashboardConsumer(DashboardBridge::onVolumeBar);
     }
 
  
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
             long todayStart = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"))
                     .atStartOfDay(java.time.ZoneId.of("Asia/Kolkata"))
                     .toInstant()
                     .toEpochMilli();
 
             viewModel.active_trades = positionManager.getAllPositions().values().stream()
                     .filter(p -> p.getEntryTimestamp() >= todayStart)
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
                         avm.entryTime = p.getEntryTimestamp();
                         avm.strategy = p.getStrategy();
                         avm.reason = "ALGO_" + p.getSide().toUpperCase();
                         return avm;
                     })
                     .collect(Collectors.toList());
         }
 
         // 8. Populate Closed Trades
         if (positionManager != null) {
             long todayStart = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"))
                     .atStartOfDay(java.time.ZoneId.of("Asia/Kolkata"))
                     .toInstant()
                     .toEpochMilli();
 
             viewModel.closed_trades = positionManager.getClosedPositions().stream()
                     .filter(p -> p.getEntryTimestamp() >= todayStart || p.getExitTimestamp() >= todayStart)
                     .map(p -> {
                         DashboardViewModel.ClosedTradeViewModel cvm = new DashboardViewModel.ClosedTradeViewModel();
                         cvm.symbol = p.getInstrumentKey();
                         cvm.side = p.getSide();
                         cvm.entry = p.getEntryPrice();
                         cvm.exit = p.getExitPrice();
                         cvm.pnl = p.getRealizedPnL();
                         cvm.entryTime = p.getEntryTimestamp();
                         cvm.exitTime = p.getExitTimestamp();
                         cvm.strategy = p.getStrategy();
                         cvm.reason = p.getExitReason();
                         return cvm;
                     })
                     .collect(Collectors.toList());
         }
 
         // Serialize and broadcast
         String json = gson.toJson(viewModel);
         dashboardService.broadcast(json);
     }
 }

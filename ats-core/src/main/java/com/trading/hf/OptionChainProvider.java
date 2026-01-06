package com.trading.hf;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.Comparator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OptionChainProvider implements MarketEventListener {
    private static final Logger logger = LoggerFactory.getLogger(OptionChainProvider.class);
    private final Map<String, MarketEvent> optionState = new ConcurrentHashMap<>();
    private final Map<String, Double> previousOi = new ConcurrentHashMap<>();
    private final Map<String, Double> indexSpots = new ConcurrentHashMap<>();
    private final AtomicReference<Double> spotPrice = new AtomicReference<>(0.0);
    private static final int STRIKE_DIFFERENCE = 50;
    private static final int BANKNIFTY_STRIKE_DIFF = 100;
    private static final int WINDOW_SIZE = 2; // ATM +/- 2 strikes
    
    private final PositionManager positionManager;

    public OptionChainProvider(PositionManager positionManager) {
        this.positionManager = positionManager;
    }

    @Override
    public void onEvent(MarketEvent event) {
        String symbol = event.getSymbol();
        if (symbol == null) return;

        if (symbol.startsWith("NSE_INDEX|")) {
            indexSpots.put(symbol, event.getLtp());
            if ("NSE_INDEX|Nifty 50".equals(symbol)) {
                spotPrice.set(event.getLtp());
            }
        } else if (symbol.contains(" CE") || symbol.contains(" PE")) {
            optionState.put(symbol, event);
            if (positionManager != null) {
                positionManager.updateLtp(symbol, event.getLtp());
            }
        }
        
        // Generate Synthetic Option Prices from Spot for Backtesting if Data Missing
        if (symbol.startsWith("NSE_INDEX|") && indexSpots.containsKey(symbol)) {
            updateSyntheticOptions(symbol, event.getLtp());
        }
    }

    public void updateSpot(String symbol, double price) {
        if (symbol.startsWith("NSE_INDEX|")) {
             indexSpots.put(symbol, price);
             // Also update synthetic options immediately
             updateSyntheticOptions(symbol, price);
        }
    }

    public void updateFromBridge(List<OptionChainDto> chain) {
        if (chain == null || chain.isEmpty()) return;
        // Pre-sorted and pre-calculated in Python, just store or signal
        // For now, we can log or trigger events if needed
        logger.debug("Option Chain Window injected from bridge (size: {})", chain.size());
    }

    public static class OptionData {
        public final String symbol;
        public final double ltp;
        public OptionData(String symbol, double ltp) { this.symbol = symbol; this.ltp = ltp; }
    }

    private void updateSyntheticOptions(String indexSymbol, double spot) {
        if (positionManager == null || spot <= 0) return;
        
        // Calculate ATM Strike
        int strikeDiff = indexSymbol.contains("Bank") ? BANKNIFTY_STRIKE_DIFF : STRIKE_DIFFERENCE;
        int atmStrike = (int) (Math.round(spot / strikeDiff) * strikeDiff);
        
        // Synthetic Base Name Logic
        String baseName = indexSymbol.replace("NSE_INDEX|", "").replace(" ", "").toUpperCase();
        if (baseName.equals("NIFTY50")) baseName = "NIFTY";
        if (baseName.equals("BANKNIFTY")) baseName = "BANKNIFTY"; // Standardize
        
        String ceSymbol = "NSE_SYNTH|" + baseName + atmStrike + "CE";
        String peSymbol = "NSE_SYNTH|" + baseName + atmStrike + "PE";
        
        // Use a realistic premium (e.g., 0.5% of spot)
        double premium = spot * 0.005;
        
        // CE Price = Intrinsic + Premium
        double cePrice = Math.max(5.0, (spot - atmStrike) + premium);
        positionManager.updateLtp(ceSymbol, cePrice);
        
        // PE Price = Intrinsic + Premium
        double pePrice = Math.max(5.0, (atmStrike - spot) + premium);
        positionManager.updateLtp(peSymbol, pePrice);
    }

    public OptionData getAtmOption(String indexSymbol, String side) {
        Double spot = indexSpots.get(indexSymbol);
        if (spot == null || spot <= 0) return null;

        int strikeDiff = indexSymbol.contains("Bank") ? BANKNIFTY_STRIKE_DIFF : STRIKE_DIFFERENCE;
        int atmStrike = (int) (Math.round(spot / strikeDiff) * strikeDiff);
        
        String type = "BUY".equals(side) ? "CE" : "PE";
        
        // 1. Try Real Data first
        OptionData realOpt = optionState.values().stream()
            .filter(e -> {
                 SymbolUtil.OptionSymbol os = SymbolUtil.parseOptionSymbol(e.getSymbol());
                 return os != null && os.getStrike() == atmStrike && e.getSymbol().endsWith(type);
            })
            .findFirst()
            .map(e -> new OptionData(e.getSymbol(), e.getLtp()))
            .orElse(null);
            
        if (realOpt != null && realOpt.ltp > 0) return realOpt;
        
        // 2. Return Synthetic using the same realistic model
        String baseName = indexSymbol.replace("NSE_INDEX|", "").replace(" ", "").toUpperCase();
        if (baseName.equals("NIFTY50")) baseName = "NIFTY";
        if (baseName.equals("BANKNIFTY")) baseName = "BANKNIFTY";
        
        String synthSymbol = "NSE_SYNTH|" + baseName + atmStrike + type;
        double premium = spot * 0.005;
        double intrinsic = type.equals("CE") ? (spot - atmStrike) : (atmStrike - spot);
        double synthPrice = Math.max(5.0, intrinsic + premium);
        
        return new OptionData(synthSymbol, synthPrice);
    }

    public List<OptionChainDto> getOptionChainWindow() {
        // ...Existing logic remains for local fallback if needed...
        double currentSpot = spotPrice.get();
        if (currentSpot == 0.0) {
            return List.of();
        }

        int atmStrike = (int) (Math.round(currentSpot / STRIKE_DIFFERENCE) * STRIKE_DIFFERENCE);

        return optionState.values().stream()
                .map(event -> {
                    SymbolUtil.OptionSymbol optionSymbol = SymbolUtil.parseOptionSymbol(event.getSymbol());
                    if (optionSymbol == null) return null;

                    int strike = optionSymbol.getStrike();
                    int lowerBound = atmStrike - (WINDOW_SIZE * STRIKE_DIFFERENCE);
                    int upperBound = atmStrike + (WINDOW_SIZE * STRIKE_DIFFERENCE);

                    if (strike >= lowerBound && strike <= upperBound) {
                        double currentOi = event.getOi();
                        double prevOi = previousOi.getOrDefault(event.getSymbol(), currentOi);
                        double oiChangePercent = (prevOi == 0) ? 0 : ((currentOi - prevOi) / prevOi) * 100;
                        previousOi.put(event.getSymbol(), currentOi);

                        return new OptionChainDto(
                                strike,
                                optionSymbol.getType(),
                                event.getLtp(),
                                oiChangePercent,
                                "NEUTRAL"
                        );
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(OptionChainDto::getStrike))
                .collect(Collectors.toList());
    }
}

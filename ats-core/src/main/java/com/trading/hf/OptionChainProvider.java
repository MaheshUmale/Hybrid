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
    private final Map<String, Double> indexPcr = new ConcurrentHashMap<>();
    private final Map<String, Double> prevIndexPcr = new ConcurrentHashMap<>();
    private final AtomicReference<Double> spotPrice = new AtomicReference<>(0.0);
    private static final int STRIKE_DIFFERENCE = 50;
    private static final int BANKNIFTY_STRIKE_DIFF = 100;
    private static final int WINDOW_SIZE = 2; // ATM +/- 2 strikes
    
    private final PositionManager positionManager;
    private final AtomicReference<List<OptionChainDto>> injectedChain = new AtomicReference<>(List.of());

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

    public void updateIndexPcr(String symbol, double pcr) {
        if (symbol == null) return;
        if (symbol.startsWith("NSE_INDEX|")) {
            Double current = indexPcr.get(symbol);
            if (current != null) prevIndexPcr.put(symbol, current);
            indexPcr.put(symbol, pcr);
            logger.debug("Index PCR updated: {} -> {} (prev: {})", symbol, pcr, current);
        }
    }

    public double getIndexPcrChange(String symbol) {
        Double current = indexPcr.get(symbol);
        Double prev = prevIndexPcr.get(symbol);
        if (current == null || prev == null || prev == 0) return 0.0;
        return ((current - prev) / prev) * 100.0;
    }

    public double getIndexPcr(String symbol) {
        return indexPcr.getOrDefault(symbol, 1.0);
    }

    public void updateFromBridge(List<OptionChainDto> chain) {
        if (chain == null || chain.isEmpty()) return;
        injectedChain.set(chain);
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
        
        // 1. Try Injected Chain from Bridge (High Priority in Live)
        List<OptionChainDto> injected = injectedChain.get();
        if (injected != null && !injected.isEmpty()) {
            OptionChainDto match = injected.stream()
                .filter(d -> d.getStrike() == atmStrike && type.equals(d.getType()))
                .findFirst()
                .orElse(null);
            
            if (match != null && match.getLtp() > 0) {
                // Construct a symbolic name for the bridge-provided option
                String baseName = indexSymbol.replace("NSE_INDEX|", "").replace(" ", "").toUpperCase();
                if (baseName.equals("NIFTY50")) baseName = "NIFTY";
                String symbol = "NSE_OPT|" + baseName + atmStrike + type;
                logger.debug("Found ATM option in injected chain: {} @ {}", symbol, match.getLtp());
                return new OptionData(symbol, match.getLtp());
            }
        }

        // 2. Try Real Tick Data (fallback)
        OptionData realOpt = optionState.values().stream()
            .filter(e -> {
                 SymbolUtil.OptionSymbol os = SymbolUtil.parseOptionSymbol(e.getSymbol());
                 return os != null && os.getStrike() == atmStrike && e.getSymbol().endsWith(type);
            })
            .findFirst()
            .map(e -> new OptionData(e.getSymbol(), e.getLtp()))
            .orElse(null);
            
        if (realOpt != null && realOpt.ltp > 0) return realOpt;
        
        // 3. Return Synthetic using the same realistic model
        String baseName = indexSymbol.replace("NSE_INDEX|", "").replace(" ", "").toUpperCase();
        if (baseName.equals("NIFTY50")) baseName = "NIFTY";
        if (baseName.equals("BANKNIFTY")) baseName = "BANKNIFTY";
        
        String synthSymbol = "NSE_SYNTH|" + baseName + atmStrike + type;
        double premium = spot * 0.005;
        double intrinsic = type.equals("CE") ? (spot - atmStrike) : (atmStrike - spot);
        double synthPrice = Math.max(5.0, intrinsic + premium);
        
        logger.debug("Returning SYNTHETIC ATM option {} @ {} for index {} side={}", synthSymbol, synthPrice, indexSymbol, side);
        return new OptionData(synthSymbol, synthPrice);
    }

    public List<OptionChainDto> getOptionChainWindow() {
        // Priority 1: Use injected chain from bridge (Python processed)
        List<OptionChainDto> injected = injectedChain.get();
        if (injected != null && !injected.isEmpty()) {
            return injected;
        }

        // Priority 2: Local calculation fallback
        double currentSpot = spotPrice.get();
        if (currentSpot == 0.0) {
            return List.of();
        }

        int atmStrike = (int) (Math.round(currentSpot / STRIKE_DIFFERENCE) * STRIKE_DIFFERENCE);

        java.util.List<OptionChainDto> window = optionState.values().stream()
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
                            event.getOi(),
                            oiChangePercent,
                            "NEUTRAL"
                        );
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(OptionChainDto::getStrike))
                .collect(Collectors.toList());

        // Log window contents for debugging
        logger.debug("Computed option chain window around ATM (size={})", window.size());
        for (OptionChainDto dto : window) {
            logger.debug("WindowEntry strike={} type={} ltp={} oi={} oiChangePct={}", dto.getStrike(), dto.getType(), dto.getLtp(), dto.getOi(), dto.getOiChangePercent());
        }

        return window;
    }
    public double getPcrOfChangeInOi() {
        List<OptionChainDto> window = getOptionChainWindow();
        if (window == null || window.isEmpty()) return 1.0;

        double putChangeTotal = 0;
        double callChangeTotal = 0;

        for (OptionChainDto dto : window) {
            // OptionChainDto already has ltp, oi, and oiChangePercent. 
            // We need absolute change = (oi * (oiChangePercent/100)) / (1 + (oiChangePercent/100)) ??
            // Actually, we should store absolute change in the DTO or track it here.
            // Let's assume we want to know if Put OI is growing faster than Call OI today.
            
            double deltaOi = dto.getOi() * (dto.getOiChangePercent() / 100.0);
            if ("PE".equals(dto.getType())) {
                putChangeTotal += deltaOi;
            } else {
                callChangeTotal += deltaOi;
            }
        }

        if (callChangeTotal == 0) return putChangeTotal > 0 ? 10.0 : 1.0;
        return Math.max(0.1, putChangeTotal / callChangeTotal);
    }
}

package com.trading.hf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;

public class SignalEngine {

    private static final Logger logger = LoggerFactory.getLogger(SignalEngine.class);

    private final AuctionProfileCalculator auctionProfileCalculator;
    private final Map<String, AuctionState> auctionStates = new ConcurrentHashMap<String, AuctionState>();

    public enum AuctionState {
        ROTATION,
        DISCOVERY_UP,
        DISCOVERY_DOWN,
        REJECTION_UP,
        REJECTION_DOWN
    }

    public SignalEngine(AuctionProfileCalculator auctionProfileCalculator) {
        this.auctionProfileCalculator = auctionProfileCalculator;
    }

    public AuctionState getAuctionState(String symbol) {
        return auctionStates.getOrDefault(symbol, AuctionState.ROTATION);
    }

    public void onVolumeBar(VolumeBar bar) {
        String symbol = bar.getSymbol();
        AuctionProfileCalculator.MarketProfile profile = auctionProfileCalculator.getProfile(symbol);

        if (profile == null) {
            logger.warn("[{}] Market profile is null, skipping signal generation.", symbol);
            return;
        }

        double close = bar.getClose();
        double vah = profile.getVah();
        double val = profile.getVal();
        double poc = profile.getPoc();
        double delta = bar.getCumulativeVolumeDelta();
        AuctionState currentState = auctionStates.getOrDefault(symbol, AuctionState.ROTATION);

        logger.info("[{}] Processing VolumeBar: Close={}, VAH={}, VAL={}, POC={}, Delta={}, State={}",
                symbol, close, vah, val, poc, delta, currentState);

        AuctionState nextState = currentState;

        // State Transition Logic based on PLAN.md (Step 11)
        switch (currentState) {
            case ROTATION:
                if (close > vah && delta > 0) {
                    nextState = AuctionState.DISCOVERY_UP;
                    logger.info("SIGNAL: [{}] State change ROTATION -> DISCOVERY_UP. Price {} broke VAH {} with positive delta {}", symbol, close, vah, delta);
                } else if (close < val && delta < 0) {
                    nextState = AuctionState.DISCOVERY_DOWN;
                    logger.info("SIGNAL: [{}] State change ROTATION -> DISCOVERY_DOWN. Price {} broke VAL {} with negative delta {}", symbol, close, val, delta);
                }
                break;
            case DISCOVERY_UP:
                if (close < vah) {
                    nextState = AuctionState.REJECTION_UP;
                    logger.info("SIGNAL: [{}] State change DISCOVERY_UP -> REJECTION_UP. Price {} fell back below VAH {}", symbol, close, vah);
                } else if (close < poc) {
                    nextState = AuctionState.ROTATION;
                    logger.info("SIGNAL: [{}] State change DISCOVERY_UP -> ROTATION. Price {} fell back below POC {}", symbol, close, poc);
                }
                break;
            case DISCOVERY_DOWN:
                if (close > val) {
                    nextState = AuctionState.REJECTION_DOWN;
                    logger.info("SIGNAL: [{}] State change DISCOVERY_DOWN -> REJECTION_DOWN. Price {} moved back above VAL {}", symbol, close, val);
                } else if (close > poc) {
                    nextState = AuctionState.ROTATION;
                    logger.info("SIGNAL: [{}] State change DISCOVERY_DOWN -> ROTATION. Price {} moved back above POC {}", symbol, close, poc);
                }
                break;
            case REJECTION_UP:
            case REJECTION_DOWN:
                if (close < vah && close > val) {
                    nextState = AuctionState.ROTATION;
                    logger.info("SIGNAL: [{}] State change {} -> ROTATION. Price {} is back inside value area.", symbol, currentState, close);
                }
                break;
        }

        if (nextState != currentState) {
            auctionStates.put(symbol, nextState);
        }

        // Signal Logic based on PLAN.md (Step 8)
        detectInitiativeAndAbsorption(bar, profile, currentState);
    }

    private void detectInitiativeAndAbsorption(VolumeBar bar, AuctionProfileCalculator.MarketProfile profile, AuctionState state) {
        double close = bar.getClose();
        double delta = bar.getCumulativeVolumeDelta();
        double vah = profile.getVah();
        double val = profile.getVal();

        // Initiative Signal: Price breaks VA with confirming delta
        if (state == AuctionState.ROTATION && close > vah && delta > 0) {
            logger.info("SIGNAL: [{}] Initiative Buy detected. Price broke VAH with positive delta.", bar.getSymbol());
        } else if (state == AuctionState.ROTATION && close < val && delta < 0) {
            logger.info("SIGNAL: [{}] Initiative Sell detected. Price broke VAL with negative delta.", bar.getSymbol());
        }

        // Absorption Signal: Price at VA edge with diverging delta
        boolean atVah = Math.abs(close - vah) < (vah * 0.001); // within 0.1%
        boolean atVal = Math.abs(close - val) < (val * 0.001); // within 0.1%

        if (atVah && delta < 0) {
            logger.info("SIGNAL: [{}] Absorption detected at VAH. Price is holding at VAH despite negative delta.", bar.getSymbol());
        }
        if (atVal && delta > 0) {
            logger.info("SIGNAL: [{}] Absorption detected at VAL. Price is holding at VAL despite positive delta.", bar.getSymbol());
        }
    }
}

package com.trading.hf;

public class ThetaExitGuard implements MarketEventListener {

    private final PositionManager positionManager;
    private static final double THETA_DECAY_THRESHOLD = 0.5; // Example threshold

    public ThetaExitGuard(PositionManager positionManager) {
        this.positionManager = positionManager;
    }

    @Override
    public void onEvent(MarketEvent event) {
        positionManager.getAllPositions().forEach((instrumentKey, position) -> {
            if (instrumentKey.equals(event.getSymbol())) {
                double pnl = 0;
                if (position.getSide().equals("BUY")) {
                    pnl = (event.getLtp() - position.getEntryPrice()) * position.getQuantity();
                } else {
                    pnl = (position.getEntryPrice() - event.getLtp()) * position.getQuantity();
                }

                long timeInMarket = (event.getTs() - position.getEntryTimestamp()) / 1000;
                double thetaDecay = event.getTheta() * timeInMarket;

                if (pnl + thetaDecay < -THETA_DECAY_THRESHOLD) {
                    positionManager.closePosition(instrumentKey, event.getLtp(), event.getTs(), "THETA_DECAY");
                }
            }
        });
    }
}

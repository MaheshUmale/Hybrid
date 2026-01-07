package com.trading.hf;

public class Position {

    private final String instrumentKey;
    private int quantity;
    private final int initialQuantity;
    private final String side;
    private final double entryPrice;
    private final long entryTimestamp;
    private double stopLoss;
    private double takeProfit;

    // Exit fields
    private double exitPrice;
    private long exitTimestamp;
    private double realizedPnL;
    private String exitReason;
    private final String strategy;

    public Position(String instrumentKey, int quantity, String side, double entryPrice, long entryTimestamp, double stopLoss, double takeProfit, String strategy) {
        this.instrumentKey = instrumentKey;
        this.quantity = quantity;
        this.initialQuantity = quantity;
        this.side = side;
        this.entryPrice = entryPrice;
        this.entryTimestamp = entryTimestamp;
        this.stopLoss = stopLoss;
        this.takeProfit = takeProfit;
        this.strategy = strategy;
    }

    public void close(double exitPrice, long exitTimestamp, String exitReason) {
        this.exitPrice = exitPrice;
        this.exitTimestamp = exitTimestamp;
        this.exitReason = exitReason;
        
        // Calculate PnL
        boolean isLong = "BUY".equalsIgnoreCase(side);
        double diff = isLong ? (exitPrice - entryPrice) : (entryPrice - exitPrice);
        this.realizedPnL = diff * quantity;
    }

    public double reduceQuantity(int qtyToClose, double exitPrice, long exitTimestamp, String exitReason) {
        if (qtyToClose <= 0) return 0.0;
        int closing = Math.min(qtyToClose, this.quantity);
        boolean isLong = "BUY".equalsIgnoreCase(side);
        double diff = isLong ? (exitPrice - entryPrice) : (entryPrice - exitPrice);
        double realized = diff * closing;
        this.realizedPnL += realized;
        this.quantity -= closing;
        // If fully closed, set exit fields
        if (this.quantity <= 0) {
            this.exitPrice = exitPrice;
            this.exitTimestamp = exitTimestamp;
            this.exitReason = exitReason;
        }
        return realized;
    }

    public String getInstrumentKey() { return instrumentKey; }
    public int getQuantity() { return quantity; }
    public int getInitialQuantity() { return initialQuantity; }
    public String getSide() { return side; }
    public double getEntryPrice() { return entryPrice; }
    public long getEntryTimestamp() { return entryTimestamp; }
    public double getStopLoss() { return stopLoss; }
    public double getTakeProfit() { return takeProfit; }
    public void setStopLoss(double sl) { this.stopLoss = sl; }
    public void setTakeProfit(double tp) { this.takeProfit = tp; }

    public double getExitPrice() { return exitPrice; }
    public long getExitTimestamp() { return exitTimestamp; }
    public double getRealizedPnL() { return realizedPnL; }
    public String getExitReason() { return exitReason; }
    public String getStrategy() { return strategy; }

}

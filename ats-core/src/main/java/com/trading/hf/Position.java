package com.trading.hf;

public class Position {

    private final String instrumentKey;
    private final int quantity;
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

    public Position(String instrumentKey, int quantity, String side, double entryPrice, long entryTimestamp, double stopLoss, double takeProfit) {
        this.instrumentKey = instrumentKey;
        this.quantity = quantity;
        this.side = side;
        this.entryPrice = entryPrice;
        this.entryTimestamp = entryTimestamp;
        this.stopLoss = stopLoss;
        this.takeProfit = takeProfit;
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

    public String getInstrumentKey() { return instrumentKey; }
    public int getQuantity() { return quantity; }
    public String getSide() { return side; }
    public double getEntryPrice() { return entryPrice; }
    public long getEntryTimestamp() { return entryTimestamp; }
    public double getStopLoss() { return stopLoss; }
    public double getTakeProfit() { return takeProfit; }
    public void setStopLoss(double sl) { this.stopLoss = sl; }

    public double getExitPrice() { return exitPrice; }
    public long getExitTimestamp() { return exitTimestamp; }
    public double getRealizedPnL() { return realizedPnL; }
    public String getExitReason() { return exitReason; }

}

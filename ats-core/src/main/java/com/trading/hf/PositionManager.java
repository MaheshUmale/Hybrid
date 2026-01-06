package com.trading.hf;

import java.util.concurrent.ConcurrentHashMap;

public class PositionManager {

    private final ConcurrentHashMap<String, Position> positions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> latestPrices = new ConcurrentHashMap<>();
    private final java.util.List<Position> closedPositions = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    public void addPosition(String instrumentKey, int quantity, String side, double entryPrice, long entryTimestamp, double stopLoss, double takeProfit) {
        positions.put(instrumentKey, new Position(instrumentKey, quantity, side, entryPrice, entryTimestamp, stopLoss, takeProfit));
    }

    public void closePosition(String instrumentKey, double exitPrice, long exitTimestamp, String reason) {
        Position p = positions.remove(instrumentKey);
        if (p != null) {
            p.close(exitPrice, exitTimestamp, reason);
            closedPositions.add(p);
        }
    }
    
    public java.util.List<Position> getClosedPositions() {
        return closedPositions;
    }

    public Position getPosition(String instrumentKey) {
        return positions.get(instrumentKey);
    }

    public ConcurrentHashMap<String, Position> getAllPositions() {
        return positions;
    }

    public void updateLtp(String symbol, double price) {
        latestPrices.put(symbol, price);
    }

    public double getLtp(String symbol) {
        // Fallback to entry price if current LTP not found (avoids 0.0 PnL issues if data hasn't arrived)
        if (latestPrices.containsKey(symbol)) {
            return latestPrices.get(symbol);
        }
        Position p = positions.get(symbol);
        return (p != null) ? p.getEntryPrice() : 0.0;
    }
}

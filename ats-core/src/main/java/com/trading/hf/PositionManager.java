package com.trading.hf;

import java.sql.*;
import java.util.concurrent.ConcurrentHashMap;
import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PositionManager {

    private static final Logger logger = LoggerFactory.getLogger(PositionManager.class);
    private static final String STATE_FILE = "positions.json";
    private static final String DB_URL = System.getProperty("db.url", "jdbc:sqlite:trading_system.db");
    
    private final ConcurrentHashMap<String, Position> positions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> latestPrices = new ConcurrentHashMap<>();
    private final List<Position> closedPositions = Collections.synchronizedList(new ArrayList<>());
    private final Gson gson = new Gson();

    public PositionManager() {
        initializeDatabase();
        migrateFromJson();
        loadFromDatabase();
    }

    private void initializeDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            String sql = "CREATE TABLE IF NOT EXISTS positions (" +
                         "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                         "instrument_key TEXT," +
                         "quantity INTEGER," +
                         "initial_quantity INTEGER," +
                         "side TEXT," +
                         "entry_price REAL," +
                         "entry_timestamp INTEGER," +
                         "stop_loss REAL," +
                         "take_profit REAL," +
                         "exit_price REAL," +
                         "exit_timestamp INTEGER," +
                         "realized_pnl REAL," +
                         "exit_reason TEXT," +
                         "strategy TEXT," +
                         "status TEXT" + // ACTIVE or CLOSED
                         ")";
            stmt.execute(sql);
            logger.info("Database initialized successfully.");
        } catch (SQLException e) {
            logger.error("Failed to initialize database", e);
        }
    }

    private void migrateFromJson() {
        if (!Files.exists(Paths.get(STATE_FILE))) return;
        
        logger.info("Found legacy positions.json. Starting migration to SQLite...");
        try {
            String json = new String(Files.readAllBytes(Paths.get(STATE_FILE)));
            PositionState state = gson.fromJson(json, PositionState.class);
            
            if (state != null) {
                if (state.activePositions != null) {
                    for (Position p : state.activePositions) {
                        saveToDb(p, "ACTIVE");
                    }
                }
                if (state.closedPositions != null) {
                    for (Position p : state.closedPositions) {
                        saveToDb(p, "CLOSED");
                    }
                }
            }
            
            Files.move(Paths.get(STATE_FILE), Paths.get(STATE_FILE + ".deprecated"));
            logger.info("Migration completed. positions.json moved to positions.json.deprecated");
        } catch (Exception e) {
            logger.error("Failed to migrate from JSON", e);
        }
    }

    private void loadFromDatabase() {
        long todayStart = Long.parseLong(System.getProperty("mock.todayStart", String.valueOf(
                LocalDate.now(ZoneId.of("Asia/Kolkata"))
                .atStartOfDay(ZoneId.of("Asia/Kolkata"))
                .toInstant()
                .toEpochMilli())));

        String sql = "SELECT * FROM positions WHERE entry_timestamp >= ? OR exit_timestamp >= ? OR status = 'ACTIVE'";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, todayStart);
            pstmt.setLong(2, todayStart);
            
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Position p = new Position(
                    rs.getString("instrument_key"),
                    rs.getInt("quantity"),
                    rs.getString("side"),
                    rs.getDouble("entry_price"),
                    rs.getLong("entry_timestamp"),
                    rs.getDouble("stop_loss"),
                    rs.getDouble("take_profit"),
                    rs.getString("strategy")
                );
                
                // Set closed fields if necessary
                if ("CLOSED".equals(rs.getString("status"))) {
                    p.close(rs.getDouble("exit_price"), rs.getLong("exit_timestamp"), rs.getString("exit_reason"));
                    closedPositions.add(p);
                } else {
                    positions.put(p.getInstrumentKey(), p);
                }
            }
            logger.info("Loaded {} active and {} closed positions from database.", positions.size(), closedPositions.size());
        } catch (SQLException e) {
            logger.error("Failed to load positions from database", e);
        }
    }

    private void saveToDb(Position p, String status) {
        String sql = "INSERT INTO positions (instrument_key, quantity, initial_quantity, side, entry_price, " +
                     "entry_timestamp, stop_loss, take_profit, exit_price, exit_timestamp, realized_pnl, " +
                     "exit_reason, strategy, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, p.getInstrumentKey());
            pstmt.setInt(2, p.getQuantity());
            pstmt.setInt(3, p.getInitialQuantity());
            pstmt.setString(4, p.getSide());
            pstmt.setDouble(5, p.getEntryPrice());
            pstmt.setLong(6, p.getEntryTimestamp());
            pstmt.setDouble(7, p.getStopLoss());
            pstmt.setDouble(8, p.getTakeProfit());
            pstmt.setDouble(9, p.getExitPrice());
            pstmt.setLong(10, p.getExitTimestamp());
            pstmt.setDouble(11, p.getRealizedPnL());
            pstmt.setString(12, p.getExitReason());
            pstmt.setString(13, p.getStrategy());
            pstmt.setString(14, status);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to save position to DB", e);
        }
    }

    private void updateInDb(Position p, String status) {
        String sql = "UPDATE positions SET quantity = ?, stop_loss = ?, take_profit = ?, exit_price = ?, " +
                     "exit_timestamp = ?, realized_pnl = ?, exit_reason = ?, status = ? " +
                     "WHERE instrument_key = ? AND entry_timestamp = ?";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, p.getQuantity());
            pstmt.setDouble(2, p.getStopLoss());
            pstmt.setDouble(3, p.getTakeProfit());
            pstmt.setDouble(4, p.getExitPrice());
            pstmt.setLong(5, p.getExitTimestamp());
            pstmt.setDouble(6, p.getRealizedPnL());
            pstmt.setString(7, p.getExitReason());
            pstmt.setString(8, status);
            pstmt.setString(9, p.getInstrumentKey());
            pstmt.setLong(10, p.getEntryTimestamp());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to update position in DB", e);
        }
    }

    public void addPosition(String instrumentKey, int quantity, String side, double entryPrice, long entryTimestamp, double stopLoss, double takeProfit, String strategy) {
        Position p = new Position(instrumentKey, quantity, side, entryPrice, entryTimestamp, stopLoss, takeProfit, strategy);
        positions.put(instrumentKey, p);
        saveToDb(p, "ACTIVE");
    }

    public void closePosition(String instrumentKey, double exitPrice, long exitTimestamp, String reason) {
        Position p = positions.remove(instrumentKey);
        if (p != null) {
            p.close(exitPrice, exitTimestamp, reason);
            closedPositions.add(p);
            updateInDb(p, "CLOSED");
        }
    }

    public double partialClosePosition(String instrumentKey, int qtyToClose, double exitPrice, long exitTimestamp, String reason) {
        Position p = positions.get(instrumentKey);
        if (p == null) return 0.0;
        double realized = p.reduceQuantity(qtyToClose, exitPrice, exitTimestamp, reason);
        
        if (p.getQuantity() <= 0) {
            positions.remove(instrumentKey);
            closedPositions.add(p);
            updateInDb(p, "CLOSED");
        } else {
            updateInDb(p, "ACTIVE");
        }
        return realized;
    }
    
    public List<Position> getClosedPositions() {
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
    
    public void syncPositionUpdate(String instrumentKey) {
        Position p = positions.get(instrumentKey);
        if (p != null) {
            updateInDb(p, "ACTIVE");
        }
    }

    public double getLtp(String symbol) {
        if (latestPrices.containsKey(symbol)) {
            return latestPrices.get(symbol);
        }
        Position p = positions.get(symbol);
        return (p != null) ? p.getEntryPrice() : 0.0;
    }

    // Legacy helper class for migration
    private static class PositionState {
        List<Position> activePositions;
        List<Position> closedPositions;
    }
}

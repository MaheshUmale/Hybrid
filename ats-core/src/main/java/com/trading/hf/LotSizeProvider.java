package com.trading.hf;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.FileReader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads NSE.json (if available) to provide lot sizes / qty multipliers for instruments.
 * Falls back to a configured default lot size when data is missing.
 */
public class LotSizeProvider {

    private static LotSizeProvider instance;
    private final Map<String, Integer> lotSizeMap = new ConcurrentHashMap<>();
    private int defaultLotSize = 1;

    private LotSizeProvider() {
        String def = ConfigLoader.getProperty("default.lot.size", "1");
        try { defaultLotSize = Integer.parseInt(def); } catch (Exception e) { defaultLotSize = 1; }
        loadNseJson();
    }

    public static synchronized LotSizeProvider getInstance() {
        if (instance == null) instance = new LotSizeProvider();
        return instance;
    }

    private void loadNseJson() {
        try {
            String path = "NSE.json"; // try working directory
            if (!Files.exists(Paths.get(path))) return;

            Gson gson = new Gson();
            Type listType = new TypeToken<List<java.util.Map<String, Object>>>(){}.getType();
            List<Map<String, Object>> arr = gson.fromJson(new FileReader(path), listType);
            System.out.println("LotSizeProvider: Loaded NSE.json entries=" + (arr == null ? 0 : arr.size()));
            for (Map<String, Object> item : arr) {
                try {
                    // trading_symbol may contain full option description or underlying name
                    Object t = item.get("trading_symbol");
                    String trading = t == null ? null : t.toString().toUpperCase();
                    if (trading == null) continue;

                    // Prefer 'lot_size' or 'qty_multiplier'
                    int lot = 1;
                    if (item.containsKey("lot_size") && item.get("lot_size") != null) {
                        try { lot = ((Number)item.get("lot_size")).intValue(); } catch (Exception e) { lot = Integer.parseInt(item.get("lot_size").toString()); }
                    } else if (item.containsKey("qty_multiplier") && item.get("qty_multiplier") != null) {
                        try { lot = ((Number)item.get("qty_multiplier")).intValue(); } catch (Exception e) { lot = Integer.parseInt(item.get("qty_multiplier").toString()); }
                    }

                    // Map by trading symbol and also by asset_symbol if present
                    lotSizeMap.put(trading, Math.max(1, lot));
                    Object asset = item.get("asset_symbol");
                    if (asset != null) lotSizeMap.put(asset.toString().toUpperCase(), Math.max(1, lot));
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            // silent fallback
        }
    }

    public int getLotSizeForSymbol(String symbol) {
        if (symbol == null) return defaultLotSize;
        String s = symbol.toUpperCase();
        // Try direct
        if (lotSizeMap.containsKey(s)) return lotSizeMap.get(s);

        // Try tokens
        for (String key : lotSizeMap.keySet()) {
            if (s.contains(key)) return lotSizeMap.get(key);
        }
        // Fallback
        if (defaultLotSize != 1) {
            System.out.println("LotSizeProvider: Fallback default lot size used for symbol=" + symbol + " -> " + defaultLotSize);
        }
        return defaultLotSize;
    }
}

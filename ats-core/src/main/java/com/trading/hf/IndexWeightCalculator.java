package com.trading.hf;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class IndexWeightCalculator implements MarketEventListener {

    private static final Logger logger = LoggerFactory.getLogger(IndexWeightCalculator.class);
    private final Map<String, Heavyweight> heavyweights;
    private volatile double aggregateWeightedDelta = 0.0;

    public IndexWeightCalculator(String indexPath, InstrumentMaster instrumentMaster) {
        this.heavyweights = loadWeights(indexPath, instrumentMaster);
    }

    @Override
    public void onEvent(MarketEvent event) {
        Heavyweight hw = heavyweights.get(event.getSymbol());
        if (hw != null) {
            double delta = (event.getTbq() - event.getTsq());
            hw.setDelta(delta);
            hw.setPrice(event.getLtp());
            hw.setVolume(event.getVtt());
            if (hw.getPrevClose() == 0 && event.getCp() > 0) {
                hw.setPrevClose(event.getCp());
            }
            updateAggregateWeightedDelta();
        }
    }

    private void updateAggregateWeightedDelta() {
        aggregateWeightedDelta = heavyweights.values().stream()
                .mapToDouble(hw -> hw.getDelta() * hw.getWeight())
                .sum();
    }

    public double getAggregateWeightedDelta() {
        return aggregateWeightedDelta;
    }

    public Map<String, Heavyweight> getHeavyweights() {
        return heavyweights;
    }

    public java.util.Set<String> getInstrumentKeys() {
        return heavyweights.values().stream().map(Heavyweight::getInstrumentKey).collect(Collectors.toSet());
    }

    private Map<String, Heavyweight> loadWeights(String path, InstrumentMaster instrumentMaster) {
        Gson gson = new Gson();
        Type type = new TypeToken<Map<String, Map<String, Double>>>() {}.getType();

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new RuntimeException("Cannot find resource: " + path);
            }
            Map<String, Map<String, Double>> rawData = gson.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), type);
            Map<String, Double> nifty50Weights = rawData.get("NIFTY50");

            Map<String, Heavyweight> tempMap = new ConcurrentHashMap<>();
            for (Map.Entry<String, Double> entry : nifty50Weights.entrySet()) {
                String tradingSymbol = entry.getKey();
                double weight = entry.getValue();
                instrumentMaster.findInstrumentKeyForEquity(tradingSymbol).ifPresentOrElse(
                        instrumentKey -> tempMap.put(instrumentKey, new Heavyweight(tradingSymbol, weight, instrumentKey)),
                        () -> logger.warn("Could not find instrument key for equity: {}", tradingSymbol)
                );
            }
            return tempMap;

        } catch (Exception e) {
            throw new RuntimeException("Failed to load index weights", e);
        }
    }

    public static class Heavyweight {
        private final String name;
        private final double weight;
        private final String instrumentKey;
        private volatile double delta;
        private volatile double price;
        private volatile double prevClose;
        private volatile long volume;

        public Heavyweight(String name, double weight, String instrumentKey) {
            this.name = name;
            this.weight = weight;
            this.instrumentKey = instrumentKey;
            this.delta = 0.0;
            this.price = 0.0;
            this.prevClose = 0.0;
            this.volume = 0;
        }

        public String getName() { return name; }
        public double getWeight() { return weight; }
        public String getInstrumentKey() { return instrumentKey; }
        public double getDelta() { return delta; }
        public void setDelta(double delta) { this.delta = delta; }
        public double getPrice() { return price; }
        public void setPrice(double price) { this.price = price; }
        public double getPrevClose() { return prevClose; }
        public void setPrevClose(double prevClose) { this.prevClose = prevClose; }
        public long getVolume() { return volume; }
        public void setVolume(long volume) { this.volume = volume; }
    }
}

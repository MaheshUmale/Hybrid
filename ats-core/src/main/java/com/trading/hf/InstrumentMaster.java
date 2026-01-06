package com.trading.hf;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.annotations.SerializedName;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Collectors;

public class InstrumentMaster {

    private final Map<String, List<InstrumentDefinition>> underlyingMap = new ConcurrentHashMap<>();
    private final Map<String, String> tradingSymbolToInstrumentKeyMap = new ConcurrentHashMap<>();
    private final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public InstrumentMaster(String resourcePath) {
        loadInstruments(resourcePath);
    }

    private void loadInstruments(String resourcePath) {
        Gson gson = new Gson();
        Type listType = new TypeToken<List<InstrumentDefinition>>() {}.getType();

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new RuntimeException("Cannot find resource: " + resourcePath);
            }
            List<InstrumentDefinition> instruments = gson.fromJson(new InputStreamReader(is), listType);
            for (InstrumentDefinition instrument : instruments) {
                if (instrument.getUnderlyingKey() != null) {
                    underlyingMap
                            .computeIfAbsent(instrument.getUnderlyingKey(), k -> new java.util.ArrayList<>())
                            .add(instrument);
                }
                if (instrument.getTradingSymbol() != null && instrument.getOptionType() == null) {
                    tradingSymbolToInstrumentKeyMap.put(instrument.getTradingSymbol(), instrument.getInstrumentKey());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load instrument master", e);
        }
    }

    public Optional<String> findInstrumentKey(String underlying, int strike, String optionType, LocalDate expiry) {
        return underlyingMap.getOrDefault(underlying, List.of()).stream()
                .filter(inst -> inst.getStrikePrice() == strike)
                .filter(inst -> inst.getOptionType() != null && inst.getOptionType().equalsIgnoreCase(optionType))
                .filter(inst -> inst.getExpiry().isEqual(expiry))
                .map(InstrumentDefinition::getInstrumentKey)
                .findFirst();
    }

    public Optional<String> findInstrumentKeyForEquity(String tradingSymbol) {
        return Optional.ofNullable(tradingSymbolToInstrumentKeyMap.get(tradingSymbol));
    }

    public Optional<LocalDate> findNearestExpiry(String underlying, LocalDate date) {
        return underlyingMap.getOrDefault(underlying, List.of()).stream()
                .map(InstrumentDefinition::getExpiry)
                .filter(expiry -> expiry != null && !expiry.isBefore(date))
                .min(Comparator.naturalOrder());
    }


    public static class InstrumentDefinition {
        @SerializedName("instrument_key")
        private String instrumentKey;
        @SerializedName("underlying_key")
        private String underlyingKey;
        @SerializedName("tradingsymbol")
        private String tradingSymbol;
        @SerializedName("strike_price")
        private Integer strikePrice;
        @SerializedName("option_type")
        private String optionType;
        private String expiry;

        public String getInstrumentKey() {
            return instrumentKey;
        }

        public String getUnderlyingKey() {
            return underlyingKey;
        }

        public String getTradingSymbol() {
            return tradingSymbol;
        }

        public int getStrikePrice() {
            return strikePrice == null ? 0 : strikePrice;
        }

        public String getOptionType() {
            return optionType;
        }

        public LocalDate getExpiry() {
            return expiry == null ? null : LocalDate.parse(expiry, DateTimeFormatter.ISO_LOCAL_DATE);
        }
    }
}

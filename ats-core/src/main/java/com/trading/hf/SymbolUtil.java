package com.trading.hf;

public class SymbolUtil {

    public static OptionSymbol parseOptionSymbol(String symbol) {
        if (symbol == null || !symbol.startsWith("NSE|OPTION|")) {
            return null;
        }

        try {
            String[] parts = symbol.split("\\|");
            if (parts.length != 3) return null;

            String instrumentPart = parts[2];
            String[] instrumentDetails = instrumentPart.split("_");
            if (instrumentDetails.length != 3) return null;

            int strike = Integer.parseInt(instrumentDetails[1]);
            String type = instrumentDetails[2];

            return new OptionSymbol(strike, type);
        } catch (Exception e) {
            return null;
        }
    }

    public static class OptionSymbol {
        private final int strike;
        private final String type;

        public OptionSymbol(int strike, String type) {
            this.strike = strike;
            this.type = type;
        }

        public int getStrike() {
            return strike;
        }

        public String getType() {
            return type;
        }
    }
}

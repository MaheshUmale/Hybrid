package com.trading.hf;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SymbolUtil {

    private static final Pattern OPTION_SYMBOL_PATTERN = Pattern.compile("\\b(\\d{5})\\s(CE|PE)$");

    public static OptionSymbol parseOptionSymbol(String symbol) {
        if (symbol == null) {
            return null;
        }

        Matcher matcher = OPTION_SYMBOL_PATTERN.matcher(symbol);
        if (matcher.find()) {
            int strike = Integer.parseInt(matcher.group(1));
            String type = matcher.group(2);
            return new OptionSymbol(strike, type);
        }
        return null;
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

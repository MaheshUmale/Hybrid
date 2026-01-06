package com.trading.hf;

public class OptionChainDto {
    private final int strike;
    private final String type;
    private final double ltp;
    private final double oiChangePercent;
    private final String sentiment;

    public OptionChainDto(int strike, String type, double ltp, double oiChangePercent, String sentiment) {
        this.strike = strike;
        this.type = type;
        this.ltp = ltp;
        this.oiChangePercent = oiChangePercent;
        this.sentiment = sentiment;
    }

    public int getStrike() {
        return strike;
    }

    public String getType() {
        return type;
    }

    public double getLtp() {
        return ltp;
    }

    public double getOiChangePercent() {
        return oiChangePercent;
    }

    public String getSentiment() {
        return sentiment;
    }
}

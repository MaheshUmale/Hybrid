package com.trading.hf;

public class OptionChainDto {
    private final int strike;
    private final String type;
    private final double ltp;
    private final double oi; // absolute open interest
    private final double oiChangePercent; // percent change vs previous snapshot
    private final String sentiment;

    public OptionChainDto(int strike, String type, double ltp, double oi, double oiChangePercent, String sentiment) {
        this.strike = strike;
        this.type = type;
        this.ltp = ltp;
        this.oi = oi;
        this.oiChangePercent = oiChangePercent;
        this.sentiment = sentiment;
    }

    public int getStrike() { return strike; }
    public String getType() { return type; }
    public double getLtp() { return ltp; }
    public double getOi() { return oi; }
    public double getOiChangePercent() { return oiChangePercent; }
    public String getSentiment() { return sentiment; }
}

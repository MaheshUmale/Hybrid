package com.trading.hf;

public class MarketEvent {
    private String symbol;
    private double ltp;
    private long ltt;
    private long ltq;
    private double cp;
    private double tbq;
    private double tsq;
    private long vtt;
    private double oi;
    private double iv;
    private double atp;
    private long ts;
    private double theta;
    private double bestBidPrice;
    private double bestAskPrice;

    // Simplified MarketEvent without padding or Disruptor dependencies

    public double getBestBidPrice() {
        return bestBidPrice;
    }

    public void setBestBidPrice(double bestBidPrice) {
        this.bestBidPrice = bestBidPrice;
    }

    public double getBestAskPrice() {
        return bestAskPrice;
    }

    public void setBestAskPrice(double bestAskPrice) {
        this.bestAskPrice = bestAskPrice;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public double getLtp() {
        return ltp;
    }

    public void setLtp(double ltp) {
        this.ltp = ltp;
    }

    public long getLtt() {
        return ltt;
    }

    public void setLtt(long ltt) {
        this.ltt = ltt;
    }

    public long getLtq() {
        return ltq;
    }

    public void setLtq(long ltq) {
        this.ltq = ltq;
    }

    public double getCp() {
        return cp;
    }

    public void setCp(double cp) {
        this.cp = cp;
    }

    public double getTbq() {
        return tbq;
    }

    public void setTbq(double tbq) {
        this.tbq = tbq;
    }

    public double getTsq() {
        return tsq;
    }

    public void setTsq(double tsq) {
        this.tsq = tsq;
    }

    public long getVtt() {
        return vtt;
    }

    public void setVtt(long vtt) {
        this.vtt = vtt;
    }

    public double getOi() {
        return oi;
    }

    public void setOi(double oi) {
        this.oi = oi;
    }

    public double getIv() {
        return iv;
    }

    public void setIv(double iv) {
        this.iv = iv;
    }

    public double getAtp() {
        return atp;
    }

    public void setAtp(double atp) {
        this.atp = atp;
    }

    public long getTs() {
        return ts;
    }

    public void setTs(long ts) {
        this.ts = ts;
    }

    public double getTheta() {
        return theta;
    }

    public void setTheta(double theta) {
        this.theta = theta;
    }

}

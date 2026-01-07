package com.trading.hf;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.function.Consumer;

/**
 * TVMarketDataStreamer handles 1-minute candle snapshots from the Python Bridge.
 * This simplifies logic by moving away from raw streaming ticks.
 */
public class TVMarketDataStreamer {

    private static final Logger logger = LoggerFactory.getLogger(TVMarketDataStreamer.class);
    private final Consumer<VolumeBar> barConsumer;
    private final OptionChainProvider optionChainProvider;
    private final MarketBreadthEngine breadthEngine;
    private final String url;
    private WebSocketClient webSocketClient;
    private final Gson gson = new Gson();

    public TVMarketDataStreamer(Consumer<VolumeBar> barConsumer, OptionChainProvider ocp, MarketBreadthEngine mbe) {
        this(barConsumer, ocp, mbe, "ws://localhost:8765");
    }

    public TVMarketDataStreamer(Consumer<VolumeBar> barConsumer, OptionChainProvider ocp, MarketBreadthEngine mbe, String url) {
        this.barConsumer = barConsumer;
        this.optionChainProvider = ocp;
        this.breadthEngine = mbe;
        this.url = url;
    }

    public void connect() {
        try {
            URI serverUri = new URI(this.url);
            this.webSocketClient = new WebSocketClient(serverUri) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    logger.info("TV Candle Bridge connection opened");
                }

                @Override
                public void onMessage(String message) {
                    handleTextMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    logger.info("TV Candle Bridge connection closed. Reconnecting in 5s...");
                    new Thread(() -> {
                        try { Thread.sleep(5000); TVMarketDataStreamer.this.connect(); } catch (InterruptedException e) {}
                    }).start();
                }

                @Override
                public void onError(Exception ex) {
                    logger.error("TV Candle Bridge error", ex);
                }
            };
            this.webSocketClient.connect();
        } catch (Exception e) {
            logger.error("Failed to connect to TV Candle Bridge", e);
        }
    }

    private void handleTextMessage(String message) {
        try {
            JsonObject jsonObject = gson.fromJson(message, JsonObject.class);
            String type = jsonObject.get("type").getAsString();
            
            if ("candle_update".equals(type)) {
                JsonArray dataArray = jsonObject.getAsJsonArray("data");
                for (JsonElement element : dataArray) {
                    processCandle(element.getAsJsonObject());
                }
            } else if ("option_chain".equals(type)) {
                processOptionChain(jsonObject);
            } else if ("market_breadth".equals(type)) {
                processMarketBreadth(jsonObject);
            }
        } catch (Exception e) {
            logger.error("Error parsing message from TV Bridge: " + e.getMessage());
        }
    }

    private void processCandle(JsonObject data) {
        String symbol = data.get("symbol").getAsString();
        long ts = data.get("timestamp").getAsLong();
        
        // 1-minute candle data
        JsonObject m1 = data.getAsJsonObject("1m");
        double open = m1.get("open").getAsDouble();
        double high = m1.get("high").getAsDouble();
        double low = m1.get("low").getAsDouble();
        double close = m1.get("close").getAsDouble();
        long volume = m1.get("volume").getAsLong();
        double vwap = m1.has("vwap") ? m1.get("vwap").getAsDouble() : close;

        // Map to internal symbol format
        String prefix = (symbol.equals("NIFTY") || symbol.equals("BANKNIFTY")) ? "NSE_INDEX|" : "NSE_EQ|";
        String fullSymbol = prefix + symbol;
        if ("NIFTY".equals(symbol)) fullSymbol = "NSE_INDEX|Nifty 50";
        if ("BANKNIFTY".equals(symbol)) fullSymbol = "NSE_INDEX|Nifty Bank";
        
        if ("NIFTY".equals(symbol) && ts % 300000 == 0) {
            System.out.println("Processing Nifty candle at " + ts + " Close=" + close);
        }

        // Create a VolumeBar directly from the candle data
        VolumeBar bar = new VolumeBar(fullSymbol, ts, open, volume);
        bar.setHigh(high);
        bar.setLow(low);
        bar.setClose(close);
        bar.setVwap(vwap);
        
        if (data.has("pcr")) {
            bar.setPcr(data.get("pcr").getAsDouble());
        }
        
        // Pass to consumers
        if (optionChainProvider != null) {
            optionChainProvider.updateSpot(bar.getSymbol(), bar.getClose());
            if (bar.getPcr() != 0.0) {
                logger.debug("Received index candle with PCR={} for {}", bar.getPcr(), bar.getSymbol());
                optionChainProvider.updateIndexPcr(bar.getSymbol(), bar.getPcr());
            }
        }

        if (barConsumer != null) {
            barConsumer.accept(bar);
        }
    }

    private void processOptionChain(JsonObject jsonObject) {
        try {
            JsonArray chainData = jsonObject.getAsJsonArray("data");
            java.util.List<OptionChainDto> dtoList = new java.util.ArrayList<>();

                logger.info("Received option_chain from bridge (entries={})", chainData.size());

                int sample = 0;
                for (JsonElement el : chainData) {
                    JsonObject d = el.getAsJsonObject();
                    int strike = (int) d.get("strike").getAsDouble();
                    String type = d.has("type") ? d.get("type").getAsString() : (d.has("call_oi") ? "CE" : "PE");
                    double ltp = d.has("ltp") ? d.get("ltp").getAsDouble() : 0.0;
                    double oi = 0.0;
                    if (d.has("oi")) oi = d.get("oi").getAsDouble();
                    else if (d.has("call_oi") && "CE".equals(type)) oi = d.get("call_oi").getAsDouble();
                    else if (d.has("put_oi") && "PE".equals(type)) oi = d.get("put_oi").getAsDouble();
                    double oiChange = d.has("oi_change_pct") ? d.get("oi_change_pct").getAsDouble() : 0.0;

                    if (sample < 5) {
                        logger.debug("OptionChainEntry[{}] strike={} type={} ltp={} oi={} oiChange={}", sample, strike, type, ltp, oi, oiChange);
                        sample++;
                    }

                    dtoList.add(new OptionChainDto(
                        strike,
                        type,
                        ltp,
                        oi,
                        oiChange,
                        "NEUTRAL"
                    ));
                }
            
            if (optionChainProvider != null) {
                optionChainProvider.updateFromBridge(dtoList);
            }
        } catch (Exception e) {
            logger.error("Error processing option chain: {}", e.getMessage());
        }
    }

    private void processMarketBreadth(JsonObject jsonObject) {
        try {
            JsonObject data = jsonObject.getAsJsonObject("data");
            int adv = data.get("advances").getAsInt();
            int dec = data.get("declines").getAsInt();
            int unc = data.get("unchanged").getAsInt();
            int tot = data.get("total").getAsInt();
            
            if (breadthEngine != null) {
                breadthEngine.update(adv, dec, unc, tot);
            }
        } catch (Exception e) {
            logger.error("Error processing market breadth: {}", e.getMessage());
        }
    }

    public void disconnect() {
        if (webSocketClient != null) {
            webSocketClient.close();
        }
    }
}

package com.trading.hf.dashboard;

import io.javalin.Javalin;
import io.javalin.websocket.WsContext;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class DashboardService {
    private Javalin app;
    private final Set<WsContext> sessions = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final AtomicReference<String> lastMessage = new AtomicReference<>();

    public void start() {
        if (app != null) return;

        this.app = Javalin.create(config -> {
            // FIX 1: ONE block for CORS. Multiple blocks cause a "PluginAlreadyRegisteredException"
            config.bundledPlugins.enableCors(cors -> {
                cors.addRule(it -> {
                    it.anyHost(); // Allows React (localhost:5173) to connect to Java (7070)
                });
            });

            // FIX 2: Stable Javalin 6 Jetty config to allow larger market data
            config.jetty.modifyWebSocketServletFactory(factory -> {
                factory.setMaxTextMessageSize(1024 * 1024); // 1MB
            });

            config.staticFiles.add("/public");
        }).start(7070);

        app.ws("/data", ws -> {
            ws.onConnect(ctx -> {
                ctx.enableAutomaticPings();
                sessions.add(ctx);
                System.out.println("Browser Connected: " + ctx.sessionId());
                
                // Initial send with a safety delay
                CompletableFuture.delayedExecutor(300, TimeUnit.MILLISECONDS).execute(() -> {
                    String data = lastMessage.get();
                    if (data != null && ctx.session.isOpen()) {
                        try {
                            ctx.send(data);
                        } catch (Exception ignored) {}
                    }
                });
            });

            ws.onClose(ctx -> {
                sessions.remove(ctx);
                System.out.println("Browser Disconnected (Code: " + ctx.status() + ")");
            });

            ws.onError(ctx -> {
                sessions.remove(ctx);
                // Suppress ClosedChannelException to keep logs clean
                if (!(ctx.error() instanceof java.io.IOException)) {
                    System.err.println("WS Error: " + ctx.error().getMessage());
                }
            });
        });
        
        System.out.println("Dashboard WSS Bridge listening on ws://localhost:7070/data");
    }

    public void broadcast(String message) {
        lastMessage.set(message);
        sessions.removeIf(s -> !s.session.isOpen());
        for (WsContext session : sessions) {
            try {
                if (session.session.isOpen()) {
                    session.send(message);
                }
            } catch (Exception e) {
                // Channel already dead
            }
        }
    }

    public void stop() {
        if (app != null) {
            app.stop();
            app = null;
        }
    }
}
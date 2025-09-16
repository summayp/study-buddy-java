package com.summay.studybuddy;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

public class ApiServer {
    private final Storage storage;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    public ApiServer(Storage storage) { this.storage = storage; }

    public void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/stats", this::handleStats);
        server.createContext("/api/decks", this::handleDecks);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
    }

    private void handleStats(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { send(ex, 405, ""); return; }
        try {
            Decks decks = storage.load();
            LocalDate today = LocalDate.now();
            int deckCount = decks.decks.size();
            int cardCount = 0, dueToday = 0;
            List<Map<String,Object>> byDeck = new ArrayList<>();
            for (Deck d : decks.decks) {
                cardCount += d.cards.size();
                int dd = 0; LocalDate nextDue = null;
                for (Flashcard c : d.cards) {
                    if (!c.dueDate().isAfter(today)) dd++;
                    if (nextDue == null || c.dueDate().isBefore(nextDue)) nextDue = c.dueDate();
                }
                Map<String,Object> m = new LinkedHashMap<>();
                m.put("name", d.name);
                m.put("cards", d.cards.size());
                m.put("dueToday", dd);
                m.put("nextDue", String.valueOf(nextDue));
                byDeck.add(m);
                dueToday += dd;
            }
            Map<String,Object> out = new LinkedHashMap<>();
            out.put("decks", deckCount);
            out.put("cards", cardCount);
            out.put("dueToday", dueToday);
            out.put("byDeck", byDeck);
            sendJson(ex, 200, out);
        } catch (Exception e) { sendError(ex, e); }
    }

    private void handleDecks(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (!"GET".equals(ex.getRequestMethod())) { send(ex, 405, ""); return; }
        try {
            Decks decks = storage.load();
            if ("/api/decks".equals(path)) {
                List<String> names = new ArrayList<>();
                for (Deck d : decks.decks) names.add(d.name);
                sendJson(ex, 200, names);
                return;
            }
            String prefix = "/api/decks/";
            if (path.startsWith(prefix)) {
                String enc = path.substring(prefix.length());
                String name = URLDecoder.decode(enc, StandardCharsets.UTF_8);
                Deck d = decks.getDeck(name);
                if (d == null) { sendJson(ex, 404, Map.of("error","deck not found")); return; }
                LocalDate today = LocalDate.now();
                List<Map<String,Object>> cards = new ArrayList<>();
                int due = 0;
                for (Flashcard c : d.cards) {
                    boolean isDue = !c.dueDate().isAfter(today);
                    if (isDue) due++;
                    Map<String,Object> m = new LinkedHashMap<>();
                    m.put("front", c.front);
                    m.put("back", c.back);
                    m.put("due", c.due);
                    m.put("intervalDays", c.intervalDays);
                    m.put("ease", c.ease);
                    m.put("repetitions", c.repetitions);
                    m.put("dueNow", isDue);
                    cards.add(m);
                }
                Map<String,Object> out = new LinkedHashMap<>();
                out.put("name", d.name);
                out.put("cards", d.cards.size());
                out.put("dueToday", due);
                out.put("items", cards);
                sendJson(ex, 200, out);
                return;
            }
            send(ex, 404, "");
        } catch (Exception e) { sendError(ex, e); }
    }

    private void send(HttpExchange ex, int code, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }

    private void sendJson(HttpExchange ex, int code, Object obj) throws IOException {
        String json = gson.toJson(obj);
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }

    private void sendError(HttpExchange ex, Exception e) throws IOException {
        String json = gson.toJson(Map.of("error", e.getClass().getSimpleName(), "message", e.getMessage()));
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(500, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }
}

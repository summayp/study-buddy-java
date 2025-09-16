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
        server.createContext("/", this::handleUi);
        server.createContext("/ui", this::handleUi);
        server.createContext("/api/stats", this::handleStats);
        server.createContext("/api/decks", this::handleDecks);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
    }

    // ---------- UI (HTML) ----------
    private void handleUi(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { send(ex, 405, ""); return; }
        String html = """
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8"/>
  <meta name="viewport" content="width=device-width,initial-scale=1"/>
  <title>Study Buddy Dashboard</title>
  <style>
    :root { --bg:#fff; --fg:#111; --muted:#666; --card:#f7f7f7; --border:#e5e5e5; }
    *{box-sizing:border-box} body{font-family:system-ui,-apple-system,Segoe UI,Roboto,Ubuntu,Cantarell,Helvetica,Arial,sans-serif;background:var(--bg);color:var(--fg);max-width:900px;margin:32px auto;padding:0 16px}
    h1{margin:0 0 8px} .muted{color:var(--muted)}
    .grid{display:grid;grid-template-columns:1fr 2fr;gap:16px;margin-top:16px}
    .card{background:var(--card);border:1px solid var(--border);border-radius:12px;padding:14px}
    .deck{padding:10px;border-radius:10px;border:1px solid var(--border);background:#fff;margin-bottom:8px;display:flex;justify-content:space-between;gap:8px;align-items:center;cursor:pointer}
    .deck:hover{background:#fafafa}
    .badge{border-radius:999px;padding:2px 8px;border:1px solid var(--border);font-size:12px}
    table{width:100%;border-collapse:separate;border-spacing:0 8px}
    td,th{text-align:left;padding:8px}
    tr{background:#fff;border:1px solid var(--border);border-radius:8px}
    .controls{display:flex;gap:8px;margin:8px 0 0}
    input[type="text"]{flex:1;padding:8px;border:1px solid var(--border);border-radius:8px}
    button{padding:8px 12px;border:0;border-radius:8px;background:#111;color:#fff;cursor:pointer}
  </style>
</head>
<body>
  <h1>📚 Study Buddy</h1>
  <div class="muted">Overview and per-deck details. Open this while the app serves on port 8080.</div>

  <div class="grid">
    <div class="card">
      <h3>Overview</h3>
      <div id="overview" class="muted">Loading…</div>
      <h3 style="margin-top:14px;">Decks</h3>
      <div id="decks"></div>
      <div class="controls">
        <input id="newDeck" type="text" placeholder="New deck name"/>
        <button id="btnAddDeck">Add deck</button>
      </div>
    </div>

    <div class="card">
      <h3 id="deckTitle">Deck</h3>
      <div id="deckMeta" class="muted" style="margin-bottom:8px;"></div>
      <div style="overflow:auto;">
        <table id="deckTable">
          <thead><tr><th>#</th><th>Front</th><th>Back</th><th>Due</th><th>Ease</th><th>Interval</th><th>Reps</th></tr></thead>
          <tbody></tbody>
        </table>
      </div>
      <div class="controls">
        <input id="front" type="text" placeholder="Front"/>
        <input id="back" type="text" placeholder="Back"/>
        <button id="btnAddCard">Add card</button>
      </div>
    </div>
  </div>

  <script>
    async function j(u, opts){ const r=await fetch(u,opts); if(!r.ok) throw new Error(await r.text()); return r.json(); }
    async function load(){
      const s = await j('/api/stats');
      document.getElementById('overview').textContent =
        `Decks: ${s.decks} • Cards: ${s.cards} • Due today: ${s.dueToday}`;
      const decks = await j('/api/decks');
      const wrap = document.getElementById('decks'); wrap.innerHTML='';
      if(decks.length===0){ wrap.innerHTML = '<div class="muted">No decks yet.</div>'; }
      decks.forEach(name=>{
        const div = document.createElement('div'); div.className='deck';
        const left = document.createElement('div'); left.textContent = name;
        const badge = document.createElement('span'); badge.className='badge'; badge.textContent='open';
        div.append(left, badge);
        div.onclick=()=>openDeck(name);
        wrap.append(div);
      });
      if(decks[0]) openDeck(decks[0]);
    }
    async function openDeck(name){
      const d = await j('/api/decks/'+encodeURIComponent(name));
      document.getElementById('deckTitle').textContent = d.name;
      document.getElementById('deckMeta').textContent =
        `Cards: ${d.cards} • Due today: ${d.dueToday}`;
      const tb = document.querySelector('#deckTable tbody'); tb.innerHTML='';
      d.items.forEach((c,i)=>{
        const tr = document.createElement('tr');
        tr.innerHTML = '<td>'+(i+1)+'</td><td>'+esc(c.front)+'</td><td>'+esc(c.back)+'</td>'+
          '<td>'+(c.dueNow? 'DUE' : c.due)+'</td>'+
          '<td>'+c.ease.toFixed(2)+'</td><td>'+c.intervalDays+'</td><td>'+c.repetitions+'</td>';
        tb.append(tr);
      });
      window.currentDeck = d.name;
    }
    function esc(s){ return String(s).replace(/[&<>"']/g, m=>({ '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;' }[m])); }

    document.getElementById('btnAddDeck').onclick = async ()=>{
      const name = document.getElementById('newDeck').value.trim();
      if(!name) return;
      await runCli(['add-deck', name]);
      document.getElementById('newDeck').value='';
      await load();
    };
    document.getElementById('btnAddCard').onclick = async ()=>{
      const f = document.getElementById('front').value.trim();
      const b = document.getElementById('back').value.trim();
      if(!window.currentDeck || !f || !b) return;
      await runCli(['add-card', window.currentDeck, f, b]);
      document.getElementById('front').value=''; document.getElementById('back').value='';
      await openDeck(window.currentDeck);
    };

    async function runCli(args){
      // lightweight bridge via /api/run?args=...
      const r = await fetch('/api/run?'+new URLSearchParams({args: JSON.stringify(args)}));
      if(!r.ok){ throw new Error('command failed'); }
      return r.text();
    }
    load();
  </script>
</body>
</html>
""";
        sendHtml(ex, 200, html);
    }

    // ---------- API ----------
    private void handleStats(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { send(ex, 405, ""); return; }
        try {
            Decks decks = storage.load();
            LocalDate today = LocalDate.now();
            int deckCount = decks.decks.size(), cardCount = 0, dueToday = 0;
            List<Map<String,Object>> byDeck = new ArrayList<>();
            for (Deck d : decks.decks) {
                cardCount += d.cards.size();
                int dd = 0; LocalDate nextDue = null;
                for (Flashcard c : d.cards) {
                    if (!c.dueDate().isAfter(today)) dd++;
                    if (nextDue == null || c.dueDate().isBefore(nextDue)) nextDue = c.dueDate();
                }
                Map<String,Object> m = new LinkedHashMap<>();
                m.put("name", d.name); m.put("cards", d.cards.size()); m.put("dueToday", dd); m.put("nextDue", String.valueOf(nextDue));
                byDeck.add(m);
                dueToday += dd;
            }
            Map<String,Object> out = new LinkedHashMap<>();
            out.put("decks", deckCount); out.put("cards", cardCount); out.put("dueToday", dueToday); out.put("byDeck", byDeck);
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
                    m.put("front", c.front); m.put("back", c.back);
                    m.put("due", c.due); m.put("intervalDays", c.intervalDays);
                    m.put("ease", c.ease); m.put("repetitions", c.repetitions);
                    m.put("dueNow", isDue);
                    cards.add(m);
                }
                Map<String,Object> out = new LinkedHashMap<>();
                out.put("name", d.name); out.put("cards", d.cards.size()); out.put("dueToday", due); out.put("items", cards);
                sendJson(ex, 200, out);
                return;
            }
            send(ex, 404, "");
        } catch (Exception e) { sendError(ex, e); }
    }

    // ---------- helpers ----------
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
    private void sendHtml(HttpExchange ex, int code, String html) throws IOException {
        byte[] b = html.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
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

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
    private final Scheduler scheduler = new Scheduler();

    public ApiServer(Storage storage) { this.storage = storage; }

    public void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this::handleUi);
        server.createContext("/ui", this::handleUi);
        server.createContext("/api/stats", this::handleStats);
        server.createContext("/api/decks", this::handleDecks);
        server.createContext("/api/run", this::handleRun); // add-deck / add-card
        server.createContext("/api/review/start", this::handleReviewStart);
        server.createContext("/api/review/grade", this::handleReviewGrade);
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
    *{box-sizing:border-box} body{font-family:system-ui,-apple-system,Segoe UI,Roboto,Ubuntu,Cantarell,Helvetica,Arial,sans-serif;background:var(--bg);color:var(--fg);max-width:960px;margin:32px auto;padding:0 16px}
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
    .review{margin-top:16px;padding:12px;border:1px solid var(--border);border-radius:12px;background:#fff}
    .front{font-weight:600}
    .ans{margin-top:8px;padding:8px;border-radius:8px;background:#f0f0f0}
    .grade{display:flex;gap:6px;flex-wrap:wrap;margin-top:10px}
    .grade button{background:#222}
  </style>
</head>
<body>
  <h1>📚 Study Buddy</h1>
  <div class="muted">Overview, per-deck details, and an in-browser review flow.</div>

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

      <div class="review">
        <div style="display:flex;justify-content:space-between;align-items:center;">
          <h3 style="margin:0">Review</h3>
          <button id="btnStart">Start Review</button>
        </div>
        <div id="rvBox" style="display:none;">
          <div class="muted"><span id="rvProg"></span></div>
          <div class="front" id="rvFront" style="margin-top:8px;"></div>
          <div class="ans" id="rvBack" style="display:none;"></div>
          <div class="grade">
            <button data-g="0">0</button><button data-g="1">1</button><button data-g="2">2</button>
            <button data-g="3">3</button><button data-g="4">4</button><button data-g="5">5</button>
            <button id="btnShow">Show Answer</button>
          </div>
        </div>
        <div id="rvEmpty" class="muted">No cards due.</div>
      </div>
    </div>
  </div>

  <script>
    const S = { currentDeck:null, cur:null, total:0 };

    async function j(u,opts){ const r=await fetch(u,opts); if(!r.ok) throw new Error(await r.text()); return r.json(); }
    async function t(u,opts){ const r=await fetch(u,opts); if(!r.ok) throw new Error(await r.text()); return r.text(); }
    function esc(s){ return String(s).replace(/[&<>"']/g, m=>({ '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',\"'\":'&#39;' }[m])); }

    async function load(){
      const s = await j('/api/stats');
      document.getElementById('overview').textContent = `Decks: ${s.decks} • Cards: ${s.cards} • Due today: ${s.dueToday}`;
      const decks = await j('/api/decks');
      const wrap = document.getElementById('decks'); wrap.innerHTML='';
      if(decks.length===0){ wrap.innerHTML = '<div class="muted">No decks yet.</div>'; }
      decks.forEach(name=>{
        const div=document.createElement('div'); div.className='deck';
        const left=document.createElement('div'); left.textContent=name;
        const badge=document.createElement('span'); badge.className='badge'; badge.textContent='open';
        div.append(left,badge); div.onclick=()=>openDeck(name); wrap.append(div);
      });
      if(decks[0]) openDeck(decks[0]);
    }

    async function openDeck(name){
      const d = await j('/api/decks/'+encodeURIComponent(name));
      S.currentDeck = d.name;
      document.getElementById('deckTitle').textContent = d.name;
      document.getElementById('deckMeta').textContent = `Cards: ${d.cards} • Due today: ${d.dueToday}`;
      const tb=document.querySelector('#deckTable tbody'); tb.innerHTML='';
      d.items.forEach((c,i)=>{
        const tr=document.createElement('tr');
        tr.innerHTML = '<td>'+(i+1)+'</td><td>'+esc(c.front)+'</td><td>'+esc(c.back)+'</td>'+
          '<td>'+(c.dueNow? 'DUE' : c.due)+'</td>'+
          '<td>'+c.ease.toFixed(2)+'</td><td>'+c.intervalDays+'</td><td>'+c.repetitions+'</td>';
        tb.append(tr);
      });
      // reset review box state
      document.getElementById('rvBox').style.display='none';
      document.getElementById('rvEmpty').style.display='block';
    }

    // ---- create deck/card via /api/run
    document.getElementById('btnAddDeck').onclick = async ()=>{
      const name = document.getElementById('newDeck').value.trim(); if(!name) return;
      await t('/api/run?'+new URLSearchParams({args: JSON.stringify(['add-deck', name])}));
      document.getElementById('newDeck').value=''; await load();
    };
    document.getElementById('btnAddCard').onclick = async ()=>{
      const f=document.getElementById('front').value.trim(), b=document.getElementById('back').value.trim();
      if(!S.currentDeck||!f||!b) return;
      await t('/api/run?'+new URLSearchParams({args: JSON.stringify(['add-card', S.currentDeck, f, b])}));
      document.getElementById('front').value=''; document.getElementById('back').value='';
      await openDeck(S.currentDeck);
    };

    // ---- review flow
    document.getElementById('btnStart').onclick = async ()=>{
      const data = await j('/api/review/start?'+new URLSearchParams({deck:S.currentDeck||''}));
      if(!data.card){ document.getElementById('rvEmpty').style.display='block'; document.getElementById('rvBox').style.display='none'; return; }
      S.total = data.totalDue;
      S.cur = data.card;
      document.getElementById('rvProg').textContent = `Due: ${S.total}`;
      document.getElementById('rvFront').textContent = data.card.front;
      document.getElementById('rvBack').textContent = data.card.back;
      document.getElementById('rvBack').style.display='none';
      document.getElementById('rvEmpty').style.display='none';
      document.getElementById('rvBox').style.display='block';
    };
    document.getElementById('btnShow').onclick = ()=>{ document.getElementById('rvBack').style.display='block'; };

    document.querySelectorAll('.grade button[data-g]').forEach(btn=>{
      btn.onclick = async ()=>{
        if(!S.cur) return;
        const body = JSON.stringify({ deck: S.cur.deck, index: S.cur.index, grade: parseInt(btn.dataset.g,10) });
        const data = await j('/api/review/grade', { method:'POST', headers:{'Content-Type':'application/json'}, body });
        if(!data.card){
          document.getElementById('rvProg').textContent = 'No more due cards. 🎉';
          S.cur=null; return;
        }
        S.total = data.totalDue;
        S.cur = data.card;
        document.getElementById('rvProg').textContent = `Due: ${S.total}`;
        document.getElementById('rvFront').textContent = data.card.front;
        document.getElementById('rvBack').textContent = data.card.back;
        document.getElementById('rvBack').style.display='none';
      };
    });

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
            for (Deck d : decks.decks) {
                cardCount += d.cards.size();
                for (Flashcard c : d.cards) if (!c.dueDate().isAfter(today)) dueToday++;
            }
            Map<String,Object> out = new LinkedHashMap<>();
            out.put("decks", deckCount); out.put("cards", cardCount); out.put("dueToday", dueToday);
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
                sendJson(ex, 200, names); return;
            }
            String prefix = "/api/decks/";
            if (path.startsWith(prefix)) {
                String name = URLDecoder.decode(path.substring(prefix.length()), StandardCharsets.UTF_8);
                Deck d = decks.getDeck(name);
                if (d == null) { sendJson(ex, 404, Map.of("error","deck not found")); return; }
                LocalDate today = LocalDate.now();
                List<Map<String,Object>> items = new ArrayList<>();
                int due = 0;
                for (Flashcard c : d.cards) {
                    boolean isDue = !c.dueDate().isAfter(today);
                    if (isDue) due++;
                    Map<String,Object> m = new LinkedHashMap<>();
                    m.put("front", c.front); m.put("back", c.back);
                    m.put("due", c.due); m.put("intervalDays", c.intervalDays);
                    m.put("ease", c.ease); m.put("repetitions", c.repetitions);
                    m.put("dueNow", isDue);
                    items.add(m);
                }
                Map<String,Object> out = new LinkedHashMap<>();
                out.put("name", d.name); out.put("cards", d.cards.size()); out.put("dueToday", due); out.put("items", items);
                sendJson(ex, 200, out); return;
            }
            send(ex, 404, "");
        } catch (Exception e) { sendError(ex, e); }
    }

    // Minimal "run" bridge for add-deck / add-card
    private void handleRun(HttpExchange ex) throws IOException {
        try {
            Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());
            String argsJson = q.getOrDefault("args", "[]");
            String[] args = gson.fromJson(argsJson, String[].class);
            if (args == null || args.length == 0) { send(ex, 400, "no args"); return; }
            String cmd = args[0];
            Decks decks = storage.load();
            if ("add-deck".equals(cmd) && args.length >= 2) {
                String name = args[1];
                if (decks.getDeck(name) == null) decks.decks.add(new Deck(name));
                storage.save(decks);
                send(ex, 200, "ok"); return;
            } else if ("add-card".equals(cmd) && args.length >= 4) {
                String deck = args[1], front = args[2], back = args[3];
                Deck d = decks.getDeck(deck);
                if (d == null) { send(ex, 404, "deck not found"); return; }
                d.cards.add(Flashcard.newCard(front, back));
                storage.save(decks);
                send(ex, 200, "ok"); return;
            }
            send(ex, 400, "unsupported command");
        } catch (Exception e) { sendError(ex, e); }
    }

    // Review: start and grade
    private void handleReviewStart(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { send(ex, 405, ""); return; }
        try {
            Map<String,String> q = parseQuery(ex.getRequestURI().getRawQuery());
            String deckName = q.get("deck");
            Decks decks = storage.load();
            LocalDate today = LocalDate.now();

            List<Map<String,Object>> dueList = new ArrayList<>();
            if (deckName != null && !deckName.isBlank()) {
                Deck d = decks.getDeck(deckName);
                if (d != null) {
                    for (int i=0;i<d.cards.size();i++) {
                        Flashcard c = d.cards.get(i);
                        if (!c.dueDate().isAfter(today)) {
                            dueList.add(cardPayload(d.name, i, c));
                        }
                    }
                }
            } else {
                for (Deck d : decks.decks) {
                    for (int i=0;i<d.cards.size();i++) {
                        Flashcard c = d.cards.get(i);
                        if (!c.dueDate().isAfter(today)) {
                            dueList.add(cardPayload(d.name, i, c));
                        }
                    }
                }
            }

            Map<String,Object> out = new LinkedHashMap<>();
            out.put("totalDue", dueList.size());
            out.put("card", dueList.isEmpty()? null : dueList.get(0));
            sendJson(ex, 200, out);
        } catch (Exception e) { sendError(ex, e); }
    }

    private void handleReviewGrade(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { send(ex, 405, ""); return; }
        try (InputStream is = ex.getRequestBody()) {
            JsonObject body = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            String deckName = body.get("deck").getAsString();
            int index = body.get("index").getAsInt();
            int grade = body.get("grade").getAsInt();
            grade = Math.max(0, Math.min(5, grade));

            Decks decks = storage.load();
            Deck d = decks.getDeck(deckName);
            if (d == null || index < 0 || index >= d.cards.size()) { sendJson(ex, 404, Map.of("error","card not found")); return; }
            Flashcard c = d.cards.get(index);
            scheduler.grade(c, grade);
            storage.save(decks);

            // find next due in same deck
            LocalDate today = LocalDate.now();
            int totalDue = 0;
            Map<String,Object> next = null;
            for (int i=0;i<d.cards.size();i++) {
                Flashcard cc = d.cards.get(i);
                if (!cc.dueDate().isAfter(today)) {
                    totalDue++;
                    if (next == null) next = cardPayload(d.name, i, cc);
                }
            }
            Map<String,Object> out = new LinkedHashMap<>();
            out.put("totalDue", totalDue);
            out.put("card", next);
            sendJson(ex, 200, out);
        } catch (Exception e) { sendError(ex, e); }
    }

    private Map<String,Object> cardPayload(String deck, int index, Flashcard c) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("deck", deck); m.put("index", index);
        m.put("front", c.front); m.put("back", c.back);
        m.put("due", c.due); m.put("intervalDays", c.intervalDays);
        m.put("ease", c.ease); m.put("repetitions", c.repetitions);
        return m;
    }

    // ---------- helpers ----------
    private Map<String,String> parseQuery(String raw){
        Map<String,String> out = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return out;
        for (String part : raw.split("&")) {
            int i = part.indexOf('=');
            if (i < 0) out.put(decode(part), "");
            else out.put(decode(part.substring(0,i)), decode(part.substring(i+1)));
        }
        return out;
    }
    private String decode(String s){ return URLDecoder.decode(s, StandardCharsets.UTF_8); }

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

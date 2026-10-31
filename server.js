const express = require("express");
const path = require("path");
const fs = require("fs");

const app = express();
app.set("trust proxy", 1);
app.use(express.json({ limit: "100kb" }));

// Simple rate limit: max 40 requests per minute per IP
const hits = new Map();
app.use("/chat", (req, res, next) => {
  const now = Date.now();
  const arr = (hits.get(req.ip) || []).filter((t) => now - t < 60000);
  arr.push(now);
  hits.set(req.ip, arr);
  if (arr.length > 40) return res.status(429).json({ reply: "Thoda ruk kar dobara bolein.", intent: "none", slots: {} });
  next();
});

const { LLM_API_KEY, APP_TOKEN, TAVILY_API_KEY } = process.env;
const BASE = (process.env.LLM_BASE_URL || "https://api.deepseek.com").replace(/\/$/, "");
const MODEL = process.env.LLM_MODEL || "deepseek-chat";

const SYSTEM = `You are NOOR, a warm, smart, female personal voice assistant.
Reply in the SAME language and script the user used: Urdu script, Roman Urdu, or English.
Use feminine grammar when speaking about yourself (e.g. "kar rahi hoon").
Keep replies short (1-3 sentences) because they are spoken aloud. No markdown, no emojis.
Return ONLY a JSON object: {"reply": string, "intent": string, "slots": object}
Allowed intents and slots:
- "none": {}  (normal conversation; ALSO use it to answer time, date or battery questions from the device context)
- "open_app": {"name": "app name, e.g. WhatsApp"}
- "set_alarm": {"hour": 0-23, "minute": 0-59, "label": string}
- "set_timer": {"seconds": number}
- "call": {"name": string, "number": string (digits only if the user said a number, else "")}
- "remember": {"text": "short fact worth remembering about the user"}
- "search": {"query": string}  (current events, news, prices, facts you are unsure of)
- "weather": {"city": string}
- "torch": {"on": true or false}
- "volume": {"level": 0-100 or -1, "direction": "up" or "down" or ""}
- "play_youtube": {"query": string}  (play a video or song)
- "maps": {"place": string, "navigate": true or false}
- "open_settings": {"target": "wifi" or "bluetooth" or "sound" or "battery" or "location" or "display"}
- "add_event": {"title": string, "hour": 0-23, "minute": 0-59, "day_offset": 0 for today, 1 for tomorrow, etc}
- "send_message": {"app": "whatsapp" or "sms", "name": string, "number": string or "", "text": string}
Morning briefing: if the user says good morning or asks for a briefing, greet them, say the time and battery, mention relevant user memories, and if a home city is in the memories use intent "weather" with that city.
For actions, "reply" is what you say right now, e.g. "Theek hai, WhatsApp khol rahi hoon."
Never claim an action is finished; the phone app performs it.
Only use "remember" if the user clearly asks you to remember something.`;

async function llm(messages) {
  const r = await fetch(`${BASE}/chat/completions`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${LLM_API_KEY}` },
    body: JSON.stringify({
      model: MODEL,
      messages,
      temperature: 0.6,
      response_format: { type: "json_object" },
    }),
  });
  if (!r.ok) throw new Error(`LLM error ${r.status}: ${(await r.text()).slice(0, 300)}`);
  const j = await r.json();
  return JSON.parse(j.choices[0].message.content);
}

async function webSearch(query) {
  if (!TAVILY_API_KEY) return "No search provider is configured. Answer from your own knowledge and say you could not check the internet.";
  const r = await fetch("https://api.tavily.com/search", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ api_key: TAVILY_API_KEY, query, max_results: 3 }),
  });
  if (!r.ok) return "Search failed.";
  const j = await r.json();
  return (j.results || []).map((x) => `${x.title}: ${x.content}`).join("\n").slice(0, 3000);
}

// Free, no key needed (open-meteo.com)
async function weather(city) {
  try {
    const g = await (await fetch(`https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(city)}&count=1`)).json();
    const p = g.results && g.results[0];
    if (!p) return "City not found.";
    const w = await (await fetch(
      `https://api.open-meteo.com/v1/forecast?latitude=${p.latitude}&longitude=${p.longitude}` +
      `&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m` +
      `&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto&forecast_days=1`
    )).json();
    return `${p.name}: now ${w.current.temperature_2m}C (feels like ${w.current.apparent_temperature}C), wind ${w.current.wind_speed_10m} km/h, ` +
      `WMO weather code ${w.current.weather_code}, today max ${w.daily.temperature_2m_max[0]}C, min ${w.daily.temperature_2m_min[0]}C, ` +
      `rain chance ${w.daily.precipitation_probability_max[0]}%.`;
  } catch (e) {
    return "Weather lookup failed.";
  }
}

// ---- Website (index.html) + install support ----
const INDEX = path.join(__dirname, "index.html");
const AVATAR = path.join(__dirname, "avatar.png");
app.get("/", (_req, res) => (fs.existsSync(INDEX) ? res.sendFile(INDEX) : res.send("NOOR backend OK")));
app.get("/avatar.png", (_req, res) => (fs.existsSync(AVATAR) ? res.sendFile(AVATAR) : res.status(404).end()));
app.get("/icon.svg", (_req, res) =>
  res.type("image/svg+xml").send(
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512"><rect width="512" height="512" fill="#050008"/><circle cx="256" cy="256" r="150" fill="#7c4dff"/><circle cx="256" cy="256" r="100" fill="#b388ff"/></svg>`
  )
);
app.get("/manifest.json", (_req, res) =>
  res.json({
    name: "NOOR AI",
    short_name: "NOOR",
    start_url: "/",
    display: "standalone",
    background_color: "#000000",
    theme_color: "#000000",
    icons: [{ src: "/icon.svg", sizes: "any", type: "image/svg+xml", purpose: "any" }],
  })
);

app.post("/chat", async (req, res) => {
  if (APP_TOKEN && req.get("x-app-token") !== APP_TOKEN) {
    return res.status(401).json({ reply: "Unauthorized", intent: "none", slots: {} });
  }
  try {
    const { message = "", history = [], memories = [], context = "" } = req.body;
    const sys =
      SYSTEM +
      (context ? `\nDevice context: ${String(context).slice(0, 300)}` : "") +
      (memories.length ? `\nUser-approved memories:\n- ${memories.slice(-20).join("\n- ")}` : "");
    const msgs = [
      { role: "system", content: sys },
      ...history.slice(-12).map((h) => ({
        role: h.role === "assistant" ? "assistant" : "user",
        content: String(h.content).slice(0, 1000),
      })),
      { role: "user", content: String(message).slice(0, 1000) },
    ];
    let out = await llm(msgs);

    let extra = null;
    if (out.intent === "search" && out.slots && out.slots.query) {
      extra = `Search results:\n${await webSearch(out.slots.query)}`;
    } else if (out.intent === "weather" && out.slots && out.slots.city) {
      extra = `Weather data:\n${await weather(out.slots.city)}\nDescribe it simply in the user's language (convert the weather code to words).`;
    }
    if (extra) {
      out = await llm([
        ...msgs,
        { role: "assistant", content: JSON.stringify(out) },
        { role: "system", content: `${extra}\nNow answer the user briefly using this. Use intent "none". Return JSON.` },
      ]);
    }
    res.json({ reply: out.reply ?? "", intent: out.intent ?? "none", slots: out.slots ?? {} });
  } catch (e) {
    console.error(e);
    res.status(500).json({ reply: "Maaf kijiye, abhi mujhe jawab nahi mil raha.", intent: "none", slots: {} });
  }
});

app.listen(process.env.PORT || 3000, () => console.log("NOOR backend running"));
      

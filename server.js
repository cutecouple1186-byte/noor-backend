const express = require("express");
const path = require("path");
const fs = require("fs");

const app = express();
app.set("trust proxy", 1);
app.use(express.json({ limit: "3mb" })); // camera frame (chhoti JPEG) ke liye

// Simple rate limit: max 40 requests per minute per IP
const hits = new Map();
app.use(["/chat", "/tts", "/trending"], (req, res, next) => {
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
- "trending": {"topic": string}  (user asks what is trending, viral, new or popular today, on TikTok, YouTube, social media or in the news)
- "weather": {"city": string}
- "torch": {"on": true or false}
- "volume": {"level": 0-100 or -1, "direction": "up" or "down" or ""}
- "play_youtube": {"query": string}  (play a video or song)
- "maps": {"place": string, "navigate": true or false}
- "open_settings": {"target": "wifi" or "bluetooth" or "sound" or "battery" or "location" or "display"}
- "add_event": {"title": string, "hour": 0-23, "minute": 0-59, "day_offset": 0 for today, 1 for tomorrow, etc}
- "send_message": {"app": "whatsapp" or "sms", "name": string, "number": string or "", "text": string}
- "camera": {"on": true or false}  (user asks to turn the back camera on or off, e.g. "camera on karo", "mujhe dekho")
Personality: you are a warm, curious friend, not a robot. Share a small thought of your own now and then, and often (not every time) end with one short, natural question back to the user about their day, plans or interests. Never ask more than one question at a time.
Vision: if an image is attached, it is a live frame from the user's back camera. Answer about what you actually see, briefly and accurately. If it is blurry or unclear, say so and ask them to hold steady or come closer. Do not identify real people from their faces.
Morning briefing: if the user says good morning or asks for a briefing, greet them, say the time and battery, mention relevant user memories, and if a home city is in the memories use intent "weather" with that city.
For actions, "reply" is what you say right now, e.g. "Theek hai, WhatsApp khol rahi hoon."
App knowledge: you know popular apps well (TikTok, YouTube, WhatsApp, Instagram, Snapchat, Facebook, Pinterest, CapCut, PicsArt, WPS Office, ChatGPT, Gemini, Claude, Snaptube, Free Fire, Chrome, Gmail, Google Maps and so on). When asked how an app works or where a feature is, explain in 2-3 short simple steps. The device context may list the apps installed on the phone; use that list to know what the user has.
If you do not know something, or it is about recent events, prices, people, a new app or anything you are unsure of, use intent "search" instead of guessing.
Calls and messages: when the user asks you to call or message someone, use "call" or "send_message" right away; the phone app will do it, do not ask for confirmation unless the name or text is missing.
Never claim an action is finished; the phone app performs it.
Only use "remember" if the user clearly asks you to remember something.`;

// Agar pehla model masroof (503) ya band (404) ho to agla model try hota hai
const FALLBACKS = (process.env.LLM_FALLBACK_MODELS || "gemini-flash-latest,gemini-flash-lite-latest,gemini-2.5-flash")
  .split(",")
  .map((s) => s.trim())
  .filter(Boolean);

function parseJson(text) {
  const t = String(text || "").trim().replace(/^```(?:json)?\s*/i, "").replace(/\s*```$/, "");
  return JSON.parse(t);
}

async function llmOne(m, messages) {
  const r = await fetch(`${BASE}/chat/completions`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${LLM_API_KEY}` },
    body: JSON.stringify({
      model: m,
      messages,
      temperature: 0.6,
      response_format: { type: "json_object" },
    }),
    signal: AbortSignal.timeout(12000),
  });
  if (!r.ok) {
    const err = new Error(`LLM error ${r.status} (model ${m}): ${(await r.text()).slice(0, 300)}`);
    err.status = r.status;
    throw err;
  }
  const j = await r.json();
  return parseJson(j.choices[0].message.content);
}

// Tez jawab ke liye: pehla model chalta hai, 2.5 second mein na aaye to agla model bhi saath mein shuru,
// jo pehle sahi jawab de wahi le lo.
async function llm(messages) {
  const models = [MODEL, ...FALLBACKS.filter((m) => m !== MODEL)];
  return new Promise((resolve, reject) => {
    let started = 0;
    let failed = 0;
    let done = false;
    let lastErr;
    const timers = [];
    const startNext = () => {
      if (done || started >= models.length) return;
      const m = models[started++];
      llmOne(m, messages).then(
        (out) => {
          if (done) return;
          done = true;
          timers.forEach(clearTimeout);
          resolve(out);
        },
        (e) => {
          lastErr = e;
          console.error(`model ${m} failed:`, String(e.message || e).slice(0, 200));
          failed++;
          if (done) return;
          if (e.status === 401 || e.status === 403) {
            done = true;
            timers.forEach(clearTimeout);
            reject(e);
            return;
          }
          if (started < models.length) startNext();
          else if (failed >= models.length) {
            done = true;
            timers.forEach(clearTimeout);
            reject(lastErr);
          }
        }
      );
      if (started < models.length) timers.push(setTimeout(startNext, 2500));
    };
    startNext();
  });
}

function decodeXml(t) {
  return String(t || "")
    .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, "$1")
    .replace(/<[^>]+>/g, " ")
    .replace(/&amp;/g, "&").replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&quot;/g, '"').replace(/&#39;/g, "'")
    .replace(/\s+/g, " ").trim();
}
function xmlItems(xml, tag) {
  const out = [];
  const re = new RegExp(`<${tag}[^>]*>([\\s\\S]*?)</${tag}>`, "g");
  let m;
  while ((m = re.exec(xml))) out.push(m[1]);
  return out;
}
function firstTag(xml, tag) {
  const m = new RegExp(`<${tag}[^>]*>([\\s\\S]*?)</${tag}>`).exec(xml);
  return m ? decodeXml(m[1]) : "";
}

// Free, bina key: Google News RSS
async function googleNews(query, n = 6) {
  try {
    const r = await fetch(`https://news.google.com/rss/search?q=${encodeURIComponent(query)}&hl=en-PK&gl=PK&ceid=PK:en`, {
      signal: AbortSignal.timeout(6000),
      headers: { "User-Agent": "Mozilla/5.0" },
    });
    if (!r.ok) return "";
    const xml = await r.text();
    return xmlItems(xml, "item").slice(0, n).map((it) => `- ${firstTag(it, "title")}`).join("\n");
  } catch (e) {
    return "";
  }
}

// Free, bina key: Google Trends (aaj Pakistan mein kya trend kar raha hai)
async function googleTrends(geo = "PK", n = 8) {
  try {
    const r = await fetch(`https://trends.google.com/trending/rss?geo=${geo}`, {
      signal: AbortSignal.timeout(6000),
      headers: { "User-Agent": "Mozilla/5.0" },
    });
    if (!r.ok) return "";
    const xml = await r.text();
    return xmlItems(xml, "item")
      .slice(0, n)
      .map((it) => {
        const t = firstTag(it, "title");
        const traffic = firstTag(it, "ht:approx_traffic");
        const news = firstTag(it, "ht:news_item_title");
        return `- ${t}${traffic ? ` (${traffic} searches)` : ""}${news ? `: ${news}` : ""}`;
      })
      .join("\n");
  } catch (e) {
    return "";
  }
}

async function webSearch(query) {
  if (TAVILY_API_KEY) {
    try {
      const r = await fetch("https://api.tavily.com/search", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ api_key: TAVILY_API_KEY, query, max_results: 3 }),
        signal: AbortSignal.timeout(7000),
      });
      if (r.ok) {
        const j = await r.json();
        const t = (j.results || []).map((x) => `${x.title}: ${x.content}`).join("\n").slice(0, 3000);
        if (t) return t;
      }
    } catch (e) {}
  }
  const news = await googleNews(query, 8);
  if (news) return `Latest news headlines for "${query}":\n${news}`;
  return "Search failed. Answer from your own knowledge and say you could not check the internet.";
}

async function trendingInfo(topic = "") {
  const [trends, news] = await Promise.all([
    googleTrends("PK", 8),
    googleNews(`${topic || ""} viral trending TikTok YouTube Pakistan`.trim(), 6),
  ]);
  return `Google Trends Pakistan (what people are searching right now):\n${trends || "(unavailable)"}\n\nViral/trending headlines:\n${news || "(unavailable)"}`;
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
const AVATAR = path.join(__dirname, "avatar.png");
// Website: pehle public/index.html (Vercel), warna index.html (purana tareeqa)
app.get("/", (_req, res) => {
  let html = null;
  try {
    html = fs.readFileSync(path.join(__dirname, "public", "index.html"), "utf8");
  } catch (e) {
    try {
      html = fs.readFileSync(path.join(__dirname, "index.html"), "utf8");
    } catch (e2) {}
  }
  if (html) res.type("html").send(html);
  else res.send("NOOR backend OK");
});
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

app.post("/trending", async (req, res) => {
  if (APP_TOKEN && req.get("x-app-token") !== APP_TOKEN) return res.status(401).json({ reply: "" });
  try {
    const data = await trendingInfo("");
    const out = await llm([
      {
        role: "system",
        content:
          'You are NOOR, a warm female voice assistant talking to Rizwan in Roman Urdu. Using the data, tell him in at most 2 short sentences the most interesting thing that is trending or viral in Pakistan right now (start with "Rizwan,"). No markdown, no emojis. Return JSON {"reply": string}.',
      },
      { role: "user", content: data },
    ]);
    res.json({ reply: out.reply || "" });
  } catch (e) {
    res.json({ reply: "" });
  }
});

app.post("/chat", async (req, res) => {
  if (APP_TOKEN && req.get("x-app-token") !== APP_TOKEN) {
    return res.status(401).json({ reply: "Unauthorized", intent: "none", slots: {} });
  }
  try {
    const { message = "", history = [], memories = [], context = "", image = "" } = req.body;
    const img =
      typeof image === "string" && /^data:image\/(jpeg|png|webp);base64,/.test(image) && image.length < 2500000
        ? image
        : "";
    const sys =
      SYSTEM +
      (context ? `\nDevice context: ${String(context).slice(0, 2000)}` : "") +
      (memories.length ? `\nUser-approved memories:\n- ${memories.slice(-20).join("\n- ")}` : "");
    const msgs = [
      { role: "system", content: sys },
      ...history.slice(-12).map((h) => ({
        role: h.role === "assistant" ? "assistant" : "user",
        content: String(h.content).slice(0, 1000),
      })),
      {
        role: "user",
        content: img
          ? [
              { type: "text", text: String(message).slice(0, 1000) },
              { type: "image_url", image_url: { url: img } },
            ]
          : String(message).slice(0, 1000),
      },
    ];
    let out = await llm(msgs);

    let extra = null;
    if (out.intent === "search" && out.slots && out.slots.query) {
      extra = `Search results:\n${await webSearch(out.slots.query)}`;
    } else if (out.intent === "trending") {
      extra = `Trending data:\n${await trendingInfo((out.slots && out.slots.topic) || "")}\nTell the user the 2-3 most interesting things in a friendly way, in their language. Be honest that this comes from Google Trends and news, not directly from TikTok or YouTube.`;
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

// ---- Awaaz (Gemini TTS): kam robot jaisi. Na chale to website browser ki awaaz use karti hai ----
const TTS_MODELS = [process.env.TTS_MODEL || "gemini-3.8-flash-tts", "gemini-3.8-flash-lite-tts"].filter(
  (m, i, a) => a.indexOf(m) === i
);
const TTS_VOICE = process.env.TTS_VOICE || "Leda"; // Leda = Youthful
const TTS_STYLE = "warm, friendly young woman chatting with a close friend, relaxed pace, natural and expressive";

function pcmToWav(pcm, rate) {
  const h = Buffer.alloc(44);
  h.write("RIFF", 0);
  h.writeUInt32LE(36 + pcm.length, 4);
  h.write("WAVEfmt ", 8);
  h.writeUInt32LE(16, 16);
  h.writeUInt16LE(1, 20); // PCM
  h.writeUInt16LE(1, 22); // mono
  h.writeUInt32LE(rate, 24);
  h.writeUInt32LE(rate * 2, 28);
  h.writeUInt16LE(2, 32);
  h.writeUInt16LE(16, 34);
  h.write("data", 36);
  h.writeUInt32LE(pcm.length, 40);
  return Buffer.concat([h, pcm]);
}

app.post("/tts", async (req, res) => {
  if (APP_TOKEN && req.get("x-app-token") !== APP_TOKEN) return res.status(401).end();
  if (!BASE.includes("generativelanguage.googleapis.com")) return res.status(501).end();
  const text = String((req.body && req.body.text) || "").slice(0, 500).trim();
  if (!text) return res.status(400).end();
  for (const m of TTS_MODELS) {
    try {
      // Gemini "Interactions" API (ai.google.dev/gemini-api/docs/speech-generation)
      const r = await fetch("https://generativelanguage.googleapis.com/v1beta/interactions", {
        method: "POST",
        headers: { "Content-Type": "application/json", "x-goog-api-key": LLM_API_KEY },
        body: JSON.stringify({
          model: m,
          input: [
            {
              type: "user_input",
              content: [{ type: "text", text, annotations: [{ type: "speech_metadata", style: TTS_STYLE }] }],
            },
          ],
          response_format: { type: "audio" },
          generation_config: { speech_config: [{ voice: TTS_VOICE }] },
        }),
        signal: AbortSignal.timeout(9000),
      });
      if (!r.ok) {
        console.error(`tts ${m} failed ${r.status}:`, (await r.text()).slice(0, 300));
        continue;
      }
      const j = await r.json();
      const audio = (j.steps || [])
        .filter((st) => st.type === "model_output")
        .flatMap((st) => st.content || [])
        .filter((c) => c.type === "audio" && c.data)
        .pop();
      if (!audio) {
        console.error(`tts ${m}: no audio in response`);
        continue;
      }
      let buf = Buffer.from(audio.data, "base64");
      if (buf.slice(0, 4).toString() !== "RIFF") buf = pcmToWav(buf, 24000); // unary mein WAV aata hai; warna raw PCM
      res.set("Content-Type", "audio/wav").set("Cache-Control", "no-store").send(buf);
      return;
    } catch (e) {
      console.error(`tts ${m} error:`, String(e.message || e).slice(0, 200));
    }
  }
  res.status(502).end();
});

app.listen(process.env.PORT || 3000, () => console.log("NOOR backend running"));
      

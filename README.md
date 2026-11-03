# NOOR AI

Urdu / Hindi / English voice assistant. Isme ek Node.js server (`server.js`) aur ek website (`index.html`) hai.

## Chalane ka tareeqa

```bash
npm install
LLM_API_KEY=your_key APP_TOKEN=your_password npm start
```

Phir browser mein `http://localhost:3000` kholein.

## Environment variables

| Naam | Zaroori? | Kaam |
|---|---|---|
| `LLM_API_KEY` | Haan | LLM (DeepSeek ya OpenAI jaisi API) ki key |
| `APP_TOKEN` | Behtar hai | Password. Isay set na karein to koi bhi `/chat` use kar sakta hai |
| `TAVILY_API_KEY` | Nahi | Internet search ke liye. Na ho to Noor apne ilm se jawab degi |
| `LLM_BASE_URL` | Nahi | Default `https://api.deepseek.com` |
| `LLM_MODEL` | Nahi | Default `deepseek-chat` |
| `PORT` | Nahi | Default `3000` |

## Endpoints

- `GET /` website
- `POST /chat` Noor se baat. Header `x-app-token` chahiye agar `APP_TOKEN` set hai
- `GET /manifest.json`, `GET /icon.svg`, `GET /avatar.png` website install ke liye

## Hadood

Website mein torch, volume, settings aur background alarm nahi chal sakte. Inke liye asli Android app (APK) chahiye.

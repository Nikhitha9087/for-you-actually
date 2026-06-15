<!-- The YAML block below configures the Hugging Face Space deploy; it is ignored by GitHub's renderer aside from a small header. -->
---
title: For You, Actually
emoji: "\U0001F3AC"
colorFrom: indigo
colorTo: purple
sdk: docker
app_port: 8080
pinned: false
---

# For You, Actually

A movie recommender that learns the **feeling** you love and finds it for you **across languages** , so a Spanish thriller can lead you to the Korean one that hits the same nerve. It gets sharper every time you react to its picks.

> **One line:** It learns the feeling you love and finds it across everything you watch , getting smarter every time you react.

---

## Why it's different

Most recommenders match on genre tags or "people who watched X also watched Y." That collapses the thing that actually makes you love a film , its *feeling*. **For You, Actually** models taste as a point in a learned "map of feelings" and recommends by emotional proximity, not labels. Because feeling is language-agnostic, it surfaces films from anywhere , the dread of *Parasite* leading you to *The Wailing* or *The Platform*.

---

## What it does

1. **Onboarding** , for each of five universal moods, you name one film you love (with a live **TMDB search-as-you-type**, so you pick the real title) and say, in a line, *why*. Your words are the richest signal we have.
2. **Discover loop** , three picks at a time, each with a **grounded "why you'll like this"** blurb written by an LLM (never invented , see RAG below). React with buttons (*More like this / Not for me / Seen it*) or free text. Every reaction reshapes your taste.
3. **Your taste, mirrored** , a plain-English portrait ("You're drawn to: slow-building dread, unreliable narrators… You avoid: lighthearted comedies") plus a cross-language shelf per genre.

---

## How it works (the AI, in plain words)

- **Feeling fingerprint (embedding).** Each film's synopsis is turned into a list of numbers , coordinates on a shared "map of feelings." Films that *feel* alike land near each other, regardless of language.
- **You are five dots, not one.** Loving slow-burn thrillers *and* rom-coms shouldn't average into mush, so taste is modelled as one point per genre. Each is seeded from your favourite + your "why" + that film's real synopsis.
- **Recommend = nearest neighbours.** Within a genre, the picks are the films whose fingerprints sit closest to your taste dot (cosine similarity). "Surprise me" pools the best across all your dots.
- **Adaptive learning.** A reaction nudges the relevant dot toward (or away from) that film's fingerprint, so the next round leans into what you actually responded to.
- **RAG-grounded explanations.** The "why you'll like this" note is written by an LLM that is handed *only* the film's real facts (title, year, language, mood, synopsis) plus your own stated taste , so it explains honestly and never hallucinates a plot or spoils a twist.
- **The mirror.** One LLM call summarises the themes recurring across the films you gravitate toward (and push away) into short, human phrases.
- **Provider-agnostic generation.** Text generation sits behind a `TextGenerator` seam with an ordered fallback chain: **Groq (Llama-3.3-70b) → Gemini → a deterministic, no-LLM template**. No single provider's quota or outage can blank out a feature , if generation is unavailable, the blurb/mirror degrade to an honest sentence built from the film's own metadata rather than disappearing. Embeddings always use Gemini (Groq has no embeddings API).

---

## Architecture

```
  Angular (4300)                Spring Boot API (8080)                External
  ┌───────────────┐  REST/JSON  ┌──────────────────────────┐
  │ Onboarding    │ ─────────▶  │ SearchController          │ ─────▶  TMDB  /search
  │  (TMDB type-  │             │ RecommendationController  │ ─────▶  TMDB  /discover
  │   ahead)      │             │ ProfileController         │
  │ Discover loop │ ◀─────────  │   ├─ RecommendationService│
  │ Taste profile │             │   ├─ TasteProfileService  │ ─────▶  Gemini embeddings
  └───────────────┘             │   ├─ ExplanationService   │      generation chain:
                                │   ├─ ProfileService       │ ─────▶  Groq Llama-3.3-70b
                                │   ├─ TextGenerationService │ ──▶ Gemini (fallback)
                                │   │   (ordered fallback)   │ ──▶ template (no-LLM floor)
                                │   └─ MovieVectorIndex     │   (in-memory similarity)
                                │ H2 (embedded file DB)     │
                                └──────────────────────────┘
```

**Data flow:** TMDB → catalogue (H2) → Gemini embeddings (feeling fingerprints) → in-memory vector index. A user onboards → per-genre taste dots → nearest-neighbour search → generation chain writes the explanation → reactions nudge the dots.

### How it scales

The current catalogue is a **curated ~960-film slice** of TMDB's ~1.2M movies , we only recommend films we've fingerprinted, so the catalogue *is* the candidate pool (not a sample we extrapolate from). What scales is the **method**, not today's data:

- **Matching is a vector nearest-neighbour search**, the same family of technique used at hundreds-of-millions scale. Today `MovieVectorIndex` is a brute-force in-memory scan (O(N) per query) , instant at ~1k films. Growing to millions changes exactly **one component**: swap the in-memory map for an **ANN index** (FAISS / HNSW / `pgvector` / Qdrant) for sub-linear search. The user model, 5-dot taste logic, and recommendation flow are untouched.
- **The real bottleneck to a bigger catalogue is embedding cost**, not the algorithm: fingerprinting N films = N embedding calls (~1k/day on the free tier). That's why we curate rather than ingest everything.
- **Curation is a feature, not just a limit.** TMDB's long tail has thin/missing synopses that yield noisy embeddings; a well-described, multi-language set of ~1k films gives *cleaner* feel-matches than millions of sparse rows.

---

## Tech stack

| Layer | Choice |
| --- | --- |
| Frontend | Angular 19 (standalone components, RxJS) |
| Backend | Spring Boot 3 (Java 17, Maven) |
| Storage | H2 (embedded, file-based, zero install) |
| Matching | In-memory cosine similarity (instant at ~1k films; swap to an ANN index to scale) |
| Embeddings | Google Gemini `gemini-embedding-001` |
| Generation (blurbs / mirror) | Provider chain: **Groq `llama-3.3-70b` → Gemini `gemini-2.5-flash-lite` → deterministic template**, RAG-grounded |
| Film data + search | TMDB API |

Genres (universal, cross-language): **Thriller / Mystery · Horror · Romance / Comedy · Sci-fi / Fantasy · Drama / Slice-of-life**

---

## Project structure

```
for-you-actually/
├─ backend/                 Spring Boot API
│  ├─ src/main/java/com/foryouactually/backend/
│  │  ├─ client/            TMDB + Gemini API clients (+ DTOs)
│  │  ├─ ingest/            Catalogue ingestion, genre mapping
│  │  ├─ embed/             Embedding generation
│  │  ├─ match/             In-memory vector index + similarity
│  │  ├─ taste/             Onboarding + reaction → taste-dot updates
│  │  ├─ recommend/         Recommendations, explanations, profile mirror
│  │  ├─ model/ repository/ JPA entities + repos
│  │  └─ web/               REST controllers + DTOs
│  ├─ data/                 H2 database file (gitignored)
│  └─ .env                  API keys (gitignored)
├─ frontend/                Angular app (onboarding / discover / profile)
└─ run.ps1                  One-command launcher
```

---

## Getting started

### Prerequisites
- **Java 17**, **Maven**, **Node 22+**

> **The repo ships with a prebuilt catalogue** (960 films, all fingerprinted), so it runs **with no API keys at all**: local search, feel-matching, and template "why you'll like this" lines all work offline. Keys are optional and only unlock richer output.

### 1. (Optional) Add your keys
Copy `backend/.env.example` to `backend/.env` and fill in whichever you want (all gitignored):
```
TMDB_API_KEY=     # only to rebuild the catalogue from scratch
GROQ_API_KEY=     # real LLM blurbs + taste mirror (free, no card: console.groq.com/keys)
GEMINI_API_KEY=   # embeddings for novel onboarding picks + generation fallback
```
Leave them blank to run fully offline against the shipped catalogue.

### 2. Run everything (one command, Windows/PowerShell)
```powershell
./run.ps1
```
This launches the backend (:8080) and the Angular dev server (:4300) in separate windows. Open **http://localhost:4300**. On macOS/Linux, use the manual steps below.

### Or run manually
```powershell
# Terminal 1 - backend (keys read from backend/.env)
cd backend
mvn spring-boot:run

# Terminal 2 - frontend
cd frontend
npm install            # first time only
npm start -- --port 4300
```

> **Port note:** the dev server uses **4300**, not Angular's default 4200, because 4200 sits inside a Windows reserved port range on this machine.

### First-time catalogue setup (admin)
The repo runs against an already-ingested H2 catalogue. To build one from scratch:
```powershell
# Ingest a multi-language catalogue from TMDB
curl -X POST "http://localhost:8080/api/admin/ingest/global?pagesPerLanguage=5&minVotes=50"
# Generate feeling fingerprints (call repeatedly until remaining = 0)
curl -X POST "http://localhost:8080/api/admin/embed?max=150"
```

---

## API reference

| Method | Endpoint | Purpose |
| --- | --- | --- |
| GET  | `/api/health` | Liveness + key-config check |
| GET  | `/api/search?q=` | TMDB title autocomplete (onboarding) |
| POST | `/api/onboard` | Seed taste dots from favourites + reasons |
| GET  | `/api/recommend?userId=&genre=&count=` | Picks + grounded "why" blurbs |
| POST | `/api/react` | Apply a reaction; nudge the taste dot |
| GET  | `/api/profile?userId=` | Taste mirror + per-genre shelves |
| GET  | `/api/admin/similar/{id}` | "What feels like this film?" demo |

---

## Screenshots

_Add captures here for the writeup:_
- `docs/onboarding.png` , the five-mood onboarding with TMDB type-ahead.
- `docs/discover.png` , three picks with match % and grounded blurbs.
- `docs/profile.png` , the "drawn to / avoid" mirror and cross-language shelves.

---

## Known limits

- **Free-tier quotas, handled by design.** Generation runs through a fallback chain (Groq → Gemini → deterministic template), so blurbs and the mirror never go blank , at worst they degrade to an honest, metadata-built sentence. Embeddings use Gemini (~1000/day); onboarding is largely insulated because catalogue picks reuse their stored fingerprint (**zero** embedding calls). A live embedding is only needed for a picked film we've never seen; if that quota is spent, those specific picks pause until reset.
- **In-memory matching.** Great to ~1k films; swap `MovieVectorIndex` for an ANN/vector DB (pgvector / Qdrant / FAISS) to scale to millions , see *How it scales* above.

---

## Roadmap

- Other media (anime, books, manga) , the architecture is media-agnostic by design.
- "Did you watch it?" tracking + post-watch ratings.
- Account integrations (Letterboxd / Trakt) for real watch history.
- A proper vector database (pgvector / Qdrant) as the catalogue grows.
- An evaluation harness to measure recommendation quality.

---

_Built by the Digital COE Gen AI Team._

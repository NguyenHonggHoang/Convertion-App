# app.py (crawl-service) — batch NLP + batch Sentiment + predict v2 + 304 short-circuit
from flask import Flask, request, jsonify, make_response
from flask_cors import CORS
import logging, os, time, hashlib, json, random, re, html, unicodedata
from datetime import datetime, timezone, timedelta
from urllib.parse import urlparse
from typing import Optional, List, Dict
from concurrent.futures import ThreadPoolExecutor, as_completed
from email.utils import parsedate_to_datetime, format_datetime
from collections import defaultdict
from threading import Semaphore, Lock

import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry
import feedparser

# ---------------------------
# Setup
# ---------------------------
logging.basicConfig(level=logging.INFO)
log = logging.getLogger("crawl-service")

app = Flask(__name__)

CORS_ALLOWED = [o.strip() for o in os.getenv("CORS_ALLOWED_ORIGINS", "").split(",") if o.strip()]
CORS(app,
     resources={r"/*": {"origins": CORS_ALLOWED or []}},
     supports_credentials=False,
     methods=["GET", "POST", "OPTIONS"],
     allow_headers=["Content-Type", "If-None-Match", "If-Modified-Since"],
     expose_headers=["ETag", "Last-Modified"],
     max_age=300)

# For local testing
NLP_URL        = os.getenv("NLP_SERVICE_URL",        "http://127.0.0.1:5004")
SENTI_URL      = os.getenv("SENTIMENT_SERVICE_URL",  "http://127.0.0.1:5002")
PREDICT_URL    = os.getenv("PREDICT_SERVICE_URL",    "http://127.0.0.1:5001")

# FOR docker
#NLP_URL        = os.getenv("NLP_SERVICE_URL",        "http://nlp-service:5004")
#SENTI_URL      = os.getenv("SENTIMENT_SERVICE_URL",  "http://sentiment-service:5002")
#PREDICT_URL    = os.getenv("PREDICT_SERVICE_URL",    "http://predict-service:5001")

# ---------------------------
# Per-host concurrency & spacing
# ---------------------------
HOST_LIMITS = defaultdict(lambda: 2, {
    "news.google.com": 4,
    "feeds.reuters.com": 2,
    "feeds.bloomberg.com": 2,
    "marketwatch.com": 2,
    "www.ft.com": 2,
    "www.investing.com": 2,
    "www.fxstreet.com": 2,
    "www.forexlive.com": 2,
    "www.coindesk.com": 2,
    "cointelegraph.com": 2,
    "www.federalreserve.gov": 2,
    "www.ecb.europa.eu": 2,
    "www.bis.org": 2,
    "www.eia.gov": 2,
    "www.bankofengland.co.uk": 2,
})
_SEMAPHORES: Dict[str, Semaphore] = {}
_SEM_LOCK = Lock()

_MIN_SPACING = defaultdict(lambda: 1.0, {
    "news.google.com": 0.5,
})
_LAST_CALL = defaultdict(float)

# ---------------------------
# Feed catalog
# ---------------------------
SOURCE_GROUPS = {
    "google_news": [
        "https://news.google.com/rss/search?q=forex+OR+%22exchange+rate%22&hl=en-US&gl=US&ceid=US:en",
        "https://news.google.com/rss/search?q=USD+EUR+OR+currency&hl=en-US&gl=US&ceid=US:en",
        "https://news.google.com/rss/search?q=economy+inflation+interest+rates&hl=en-US&gl=US&ceid=US:en",
    ],

    "central_banks": [
        "https://www.federalreserve.gov/feeds/press_all.xml",
        "https://www.ecb.europa.eu/home/html/rss.en.html",
        "https://www.bis.org/rss/index.htm",
        "https://www.chicagofed.org/forms/rss/NewsReleases",
        "https://www.dnb.nl/en/rss/",
        "https://www.norges-bank.no/en/rss-feeds/",
        "https://www.bankofengland.co.uk/rss/news",
    ],

    "institutions": [
        "https://www.imf.org/en/rss-list",
        "https://www.oecd.org/en/about/newsroom.html",
        "https://www.worldbank.org/en/news",
    ],

    "energy": [
        "https://www.eia.gov/rss/todayinenergy.xml",
        "https://www.eia.gov/rss/press_rss.xml",
        "https://www.energyintel.com/rss-feed",
        "https://energy.einnews.com/all_rss",
    ],

    "markets": [
        "https://feeds.marketwatch.com/marketwatch/topstories/",
        "https://www.investing.com/rss/news_1.rss", 
        "https://rss.cnn.com/rss/money_latest.rss",
        "https://feeds.bbci.co.uk/news/business/rss.xml",
        "https://news.google.com/rss/search?q=site:reuters.com+markets&hl=en-US&gl=US&ceid=US:en",
        "https://news.google.com/rss/search?q=stock+market+OR+trading&hl=en-US&gl=US&ceid=US:en",
    ],

    "crypto": [
        "https://www.coindesk.com/arc/outboundfeeds/rss/?outputType=xml",
        "https://cointelegraph.com/feed",
    ],

    "financial_media": [
        "https://www.ft.com/news-feed?format=rss",
        "https://news.google.com/rss/search?q=finance&hl=en-US&gl=US&ceid=US:en",
        "https://www.cnbc.com/id/100003114/device/rss/rss.html",
        "https://www.cnbc.com/id/10001147/device/rss/rss.html",
        "https://rss.cnn.com/rss/money_latest.rss",
        "https://feeds.businessinsider.com/custom/all",
    ],

    "regional_vn": [
        "https://news.google.com/rss/search?q=t%E1%BB%B7+gi%C3%A1+h%E1%BB%91i+%C4%91o%E1%BA%A1i+OR+%22t%E1%BB%B7+gi%C3%A1%22&hl=vi&gl=VN&ceid=VN:vi",
        "https://news.google.com/rss/search?q=l%E1%BA%A1m+ph%C3%A1t+Vi%E1%BB%87t+Nam+OR+l%C3%A3i+su%E1%BA%A5t+Vi%E1%BB%87t+Nam&hl=vi&gl=VN&ceid=VN:vi",
    ],
}


DEFAULT_GROUPS = [g.strip() for g in os.getenv(
    "RSS_FEED_GROUPS",
    "google_news,central_banks,energy,markets,crypto,financial_media,regional_vn,institutions"
).split(",") if g.strip()]

RSS_FEEDS_ENV  = os.getenv("RSS_FEEDS", "").strip()

# ---------------------------
# Expanded local rules (fallback)
# ---------------------------
EXPANDED_RULES = {
    "forex": ["forex","exchange rate","fx","usd","eur","jpy","gbp","aud","cad","cny","dxy"],
    "economy": ["inflation","interest rate","gdp","recession","economic","cpi","ppi","pce","payrolls","jobs","unemployment","retail sales","pmi"],
    "crypto": ["bitcoin","ethereum","btc","eth","crypto","blockchain","stablecoin","defi","etf","halving"],
    "stocks": ["stock","equity","nasdaq","nasdaq-100","dow","djia","s&p","s&amp;p","s p 500","sp500","earnings","ipo","buyback","dividend"],
    "commodities": ["oil","brent","wti","gold","xau","silver","xag","copper","commodity","futures","opec","natural gas"],
    "central_banks": ["fed","ecb","boj","boe","rba","rbnz","boc","snb","norges bank","rate hike","rate cut","qe","qt"],
}

# ---------------------------
# Short-circuit 304 meta cache
# ---------------------------
_CACHE_META: Dict[str, dict] = {}
_CACHE_META_LOCK = Lock()
META_TTL_SEC = int(os.getenv("CRAWL_META_TTL", "180"))

def _meta_key(window_hours, limit, groups_param, base, quote):
    return (
        f"wh={window_hours}|lim={limit}"
        f"|grp={groups_param or ','.join(DEFAULT_GROUPS)}"
        f"|base={(base or '').upper()}|quote={(quote or '').upper()}"
    )

def try_short_circuit_304(req, key):
    with _CACHE_META_LOCK:
        meta = _CACHE_META.get(key)
    if not meta or meta["expires_at"] < time.time():
        return None
    return conditional_response(req, meta["etag"], meta["last_modified_iso"])

def update_meta_after_crawl(key, etag_value, last_modified_iso, ttl_sec=META_TTL_SEC):
    with _CACHE_META_LOCK:
        _CACHE_META[key] = {
            "etag": etag_value,
            "last_modified_iso": last_modified_iso,
            "expires_at": time.time() + ttl_sec,
        }

# ---------------------------
# HTTP session with retry/pool
# ---------------------------
def make_session():
    s = requests.Session()
    retry = Retry(
        total=3,
        backoff_factor=0.5,
        status_forcelist=[429, 500, 502, 503, 504],
        allowed_methods=["GET", "HEAD"],  # retry chỉ cho fetch feed
        respect_retry_after_header=True,
    )
    adapter = HTTPAdapter(
        max_retries=retry,
        pool_connections=64,
        pool_maxsize=128
    )
    s.mount("http://", adapter)
    s.mount("https://", adapter)
    s.headers.update({"User-Agent": "converter-crawler/1.0"})
    return s

http = make_session()

# ---------------------------
# Helpers (time, headers, text)
# ---------------------------
def _get_host(url: str) -> str:
    try:
        return urlparse(url).netloc.lower()
    except Exception:
        return ""

def _get_semaphore_for_host(host: str) -> Semaphore:
    with _SEM_LOCK:
        sem = _SEMAPHORES.get(host)
        if sem is None:
            sem = _SEMAPHORES[host] = Semaphore(HOST_LIMITS[host])
        return sem

def _polite_spacing(host: str):
    now = time.monotonic()
    dt = now - _LAST_CALL[host]
    need = _MIN_SPACING[host]
    if dt < need:
        time.sleep(need - dt)
        now = time.monotonic()
    _LAST_CALL[host] = now

def to_iso(dt):
    if isinstance(dt, datetime):
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=timezone.utc)
        return dt.astimezone(timezone.utc).isoformat()
    return dt

def http_date(dt: datetime) -> str:
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return format_datetime(dt.astimezone(timezone.utc))

def parse_http_date(s: str) -> Optional[datetime]:
    try:
        return parsedate_to_datetime(s)
    except Exception:
        return None

def parse_published(entry):
    tm = getattr(entry, "published_parsed", None) or getattr(entry, "updated_parsed", None)
    if tm:
        return datetime(*tm[:6], tzinfo=timezone.utc)
    return datetime.now(timezone.utc)

def hash_url(url):
    return hashlib.sha256(url.encode("utf-8")).hexdigest()

def source_domain(url):
    try:
        return urlparse(url).netloc or ""
    except Exception:
        return ""

TAG_RE = re.compile(r"<[^>]+>")

def strip_html(s: str) -> str:
    return TAG_RE.sub(" ", s or "").strip()

def normalize_text(s: str) -> str:
    s = html.unescape(s)
    s = unicodedata.normalize("NFKC", s)
    s = s.lower()
    s = re.sub(r"[\u2010-\u2015\-–—]", " ", s)
    s_noacc = "".join(c for c in unicodedata.normalize("NFD", s) if unicodedata.category(c) != "Mn")
    s_noacc = re.sub(r"\s+", " ", s_noacc).strip()
    return s_noacc

# ---------------------------
# Feed selector
# ---------------------------
def build_feed_catalog(groups_param: Optional[str]):
    if RSS_FEEDS_ENV:
        feeds = [u.strip() for u in RSS_FEEDS_ENV.split(",") if u.strip()]
        return feeds, {u: "env_overrides" for u in feeds}

    selected_groups = DEFAULT_GROUPS
    if groups_param:
        selected_groups = [g.strip() for g in groups_param.split(",") if g.strip()]

    feed_list, feed2group = [], {}
    for g in selected_groups:
        urls = SOURCE_GROUPS.get(g, [])
        for u in urls:
            if u not in feed2group:
                feed_list.append(u)
                feed2group[u] = g
    return feed_list, feed2group

# ---------------------------
# Per-feed conditional cache (ETag/LM)
# ---------------------------
_FEED_ETAG: Dict[str, str] = {}
_FEED_LM: Dict[str, str] = {}

def fetch_feed_bytes(feed_url: str) -> Optional[bytes]:
    host = _get_host(feed_url)
    sem = _get_semaphore_for_host(host)

    time.sleep(random.random() * 0.05)  # jitter

    with sem:
        _polite_spacing(host)

        headers = {}
        if feed_url in _FEED_ETAG:
            headers["If-None-Match"] = _FEED_ETAG[feed_url]
        if feed_url in _FEED_LM:
            headers["If-Modified-Since"] = _FEED_LM[feed_url]

        time.sleep(random.random() * 0.2)  

        r = http.get(feed_url, timeout=(10, 30), headers=headers)
        if r.status_code == 304:
            return b""
        r.raise_for_status()

        if et := r.headers.get("ETag"):
            _FEED_ETAG[feed_url] = et
        if lm := r.headers.get("Last-Modified"):
            _FEED_LM[feed_url] = lm

        return r.content

# ---------------------------
# Local NLP fallback
# ---------------------------
def classify_local(title, content):
    text = normalize_text(title + " " + content)
    for cat, kws in EXPANDED_RULES.items():
        for kw in kws:
            pattern = r'\b' + re.escape(kw) + r'\b' if kw.isalnum() else re.escape(kw)
            if re.search(pattern, text):
                return cat
    return "general"

def summarize_local(content):
    parts = [p.strip() for p in content.split(". ") if p.strip()]
    if not parts:
        return ""
    s = ". ".join(parts[:2])
    return s if s.endswith(".") else s + "."

# ---------------------------
# Batch utilities
# ---------------------------
def _chunked(lst, size):
    for i in range(0, len(lst), size):
        yield lst[i:i+size]

def call_nlp_batch(articles: List[dict], batch_size: int = 100) -> List[Optional[dict]]:
    results: Dict[int, dict] = {}
    try:
        idx_articles = [{"id": idx, "title": a.get("title",""), "content": a.get("content","")}
                        for idx, a in enumerate(articles)]
        for chunk in _chunked(idx_articles, batch_size):
            r = http.post(f"{NLP_URL}/nlp/batch", json={"articles": chunk}, timeout=15)
            if not (r.ok and r.headers.get("content-type","").startswith("application/json")):
                log.warning("NLP batch bad response: %s", getattr(r, "status_code", "?"))
                continue
            data = r.json()
            for item in data.get("results", []):
                results[item.get("id")] = {
                    "category": item.get("category", "general"),
                    "summary": item.get("summary"),
                    "pairs": item.get("pairs", []),
                }
    except Exception as e:
        log.warning("NLP batch fail: %s", e)
    return [results.get(i) for i in range(len(articles))]

def call_sentiment_batch(texts: List[str], batch_size: int = 200) -> List[Optional[float]]:
    if not texts:
        return []
    results: Dict[int, Optional[float]] = {}
    try:
        idx_items = [{"id": i, "text": t or ""} for i, t in enumerate(texts)]
        for chunk in _chunked(idx_items, batch_size):
            r = http.post(f"{SENTI_URL}/sentiment/batch", json={"items": chunk}, timeout=15)
            if not (r.ok and r.headers.get("content-type","").startswith("application/json")):
                log.warning("Sentiment batch bad response: %s", getattr(r, "status_code", "?"))
                for it in chunk:
                    results[it["id"]] = None
                continue
            data = r.json()
            for item in data.get("results", []):
                _id = item.get("id")
                score = item.get("score")
                try:
                    results[_id] = float(score)
                except Exception:
                    results[_id] = None
    except Exception as e:
        log.warning("Sentiment batch fail: %s", e)
        return [None for _ in range(len(texts))]
    return [results.get(i) for i in range(len(texts))]

# ---------------------------
# Core crawling
# ---------------------------
def crawl_latest(window_hours=12, limit=50, groups_param=None):
    """
    Crawl RSS feeds theo nhóm, lấy tin trong N giờ gần nhất, dedup theo URL, sort mới → cũ.
    """
    feed_list, feed2group = build_feed_catalog(groups_param)
    since = datetime.now(timezone.utc) - timedelta(hours=window_hours)

    raw_entries = []
    with ThreadPoolExecutor(max_workers=int(os.getenv("FETCH_WORKERS", "32"))) as pool:
        futs = {pool.submit(fetch_feed_bytes, u): u for u in feed_list}
        for fut in as_completed(futs):
            feed_url = futs[fut]
            try:
                data = fut.result()
                if not data:
                    continue
                feed = feedparser.parse(data)
                for entry in getattr(feed, "entries", []):
                    raw_entries.append((feed_url, entry))
            except Exception as e:
                log.warning("RSS fail %s: %s", feed_url, e)

    seen = set()
    out = []
    for feed_url, entry in raw_entries:
        published = parse_published(entry)
        if published < since:
            continue
        link = getattr(entry, "link", None) or ""
        if not link:
            continue
        h = hash_url(link)
        if h in seen:
            continue
        seen.add(h)

        title = getattr(entry, "title", "").strip()
        content = ""
        if "content" in entry and getattr(entry, "content", None):
            content = " ".join([getattr(c, "value", "") for c in entry.content])
        elif getattr(entry, "summary", None):
            content = entry.summary

        content = strip_html(content) or title


        out.append({
            "title": title,
            "content": content.strip(),
            "url": link,
            "published_at": to_iso(published),
            "source_group": feed2group.get(feed_url, "unknown"),
            "source_feed": feed_url,
            "source_domain": source_domain(link) or source_domain(feed_url),
        })

    out.sort(key=lambda x: x["published_at"], reverse=True)
    if limit:
        out = out[:int(limit)]
    return out

# ---------------------------
# Enrich (BATCH: NLP + Sentiment)
# ---------------------------
def enrich_articles_batch(articles: List[dict]) -> List[dict]:
    """
    - NLP batch: category, summary, pairs
    - Sentiment batch: score [-1,1]
    - Fallback local khi thiếu kết quả
    """
    if not articles:
        return []
    nlp_out = call_nlp_batch(articles, batch_size=int(os.getenv("NLP_BATCH", "100")))
    
    # Tạo text phong phú hơn cho sentiment analysis
    texts = []
    for a in articles:
        title = a.get('title', '').strip()
        content = a.get('content', '').strip()
        description = a.get('description', '').strip()
        
        # Kết hợp title + content + description để có text đầy đủ nhất
        combined_parts = [title, content, description]
        combined_text = '. '.join([part for part in combined_parts if part and len(part) > 10])
        
        # Nếu vẫn quá ngắn, chỉ dùng title
        if len(combined_text) < 20:
            combined_text = title
            
        texts.append(combined_text)
    
    senti_scores = call_sentiment_batch(texts, batch_size=int(os.getenv("SENTI_BATCH", "200")))

    enriched = []
    for i, a in enumerate(articles):
        o = nlp_out[i] if nlp_out and i < len(nlp_out) else None
        cat = (o and o.get("category")) or classify_local(a["title"], a["content"])
        summary = (o and o.get("summary")) or summarize_local(a["content"])
        pairs = (o and o.get("pairs")) or []
        s = senti_scores[i] if senti_scores and i < len(senti_scores) else None
        senti = 0.0 if (s is None) else float(s)

        enriched.append({
            **a,
            "category": cat,
            "summary": summary,
            "pairs": pairs,
            "sentiment_score": senti,
        })

    enriched.sort(key=lambda x: x["published_at"], reverse=True)
    return enriched

# ---------------------------
# Persist hook
# ---------------------------
def persist_articles(articles):
    return len(articles)

# ---------------------------
# HTTP caching helpers
# ---------------------------
def make_etag(payload: dict):
    blob = json.dumps(payload, sort_keys=True, ensure_ascii=False).encode("utf-8")
    return hashlib.sha256(blob).hexdigest()

def newest_iso(items):
    if not items:
        return to_iso(datetime.now(timezone.utc))
    mx = max(datetime.fromisoformat(i["published_at"]) for i in items)
    return to_iso(mx)

def conditional_response(req, etag_value, last_modified_iso):
    lm_dt = datetime.fromisoformat(last_modified_iso.replace("Z", "+00:00"))
    lm_http = http_date(lm_dt)

    inm = req.headers.get("If-None-Match")
    if inm and inm == etag_value:
        resp = make_response("", 304)
        resp.headers["ETag"] = etag_value
        resp.headers["Last-Modified"] = lm_http
        resp.headers["Cache-Control"] = "public, max-age=300"
        return resp

    ims_raw = req.headers.get("If-Modified-Since")
    if ims_raw:
        ims_dt = parse_http_date(ims_raw)
        if ims_dt and lm_dt <= ims_dt:
            resp = make_response("", 304)
            resp.headers["ETag"] = etag_value
            resp.headers["Last-Modified"] = lm_http
            resp.headers["Cache-Control"] = "public, max-age=300"
            return resp
    return None

# ---------------------------
# Predict (v2: có điều chỉnh sentiment)
# ---------------------------
def call_predict_v2(base_currency, target_currency, horizon_days=7, sentiment_adjust=None):
    try:
        payload = {
            "base_currency": base_currency,
            "target_currency": target_currency,
            "horizon_days": int(horizon_days),
        }
        if sentiment_adjust is not None:
            payload["sentiment_adjust"] = float(sentiment_adjust)
        r = http.post(f"{PREDICT_URL}/predict", json=payload, timeout=10)
        return bool(r.ok)
    except Exception as e:
        log.info("Predict v2 call failed (non-blocking): %s", e)
    return False

# ---------------------------
# Endpoints
# ---------------------------
@app.route("/sources", methods=["GET"])
def list_sources():
    groups_param = request.args.get("groups")
    feed_list, feed2group = build_feed_catalog(groups_param)
    catalog = {}
    for u, g in feed2group.items():
        catalog.setdefault(g, []).append(u)

    payload = {
        "status": "success",
        "groups": [{"name": g, "feeds": urls} for g, urls in sorted(catalog.items())],
        "generated_at": to_iso(datetime.now(timezone.utc)),
    }
    etag = make_etag(payload)

    cached = conditional_response(request, etag, payload["generated_at"])
    if cached:
        return cached

    resp = jsonify(payload)
    resp.headers["ETag"] = etag
    resp.headers["Last-Modified"] = http_date(datetime.now(timezone.utc))
    resp.headers["Cache-Control"] = "public, max-age=300"
    return resp

@app.route("/crawl", methods=["GET"])
def crawl_endpoint():
    """
    /crawl?window_hours=12&limit=50&base=USD&quote=VND&groups=central_banks,energy
    """
    try:
        window_hours = int(request.args.get("window_hours", 12))
        limit = int(request.args.get("limit", 50))
        base = request.args.get("base")
        quote = request.args.get("quote")
        groups_param = request.args.get("groups")

        # Short-circuit 304 trước khi crawl
        key = _meta_key(window_hours, limit, groups_param, base, quote)
        sc = try_short_circuit_304(request, key)
        if sc:
            return sc

        log.info("Crawl start: window=%sh, limit=%s, groups=%s, base=%s, quote=%s",
                 window_hours, limit, groups_param, base, quote)

        # 1) Crawl raw
        articles = crawl_latest(window_hours=window_hours, limit=limit, groups_param=groups_param)
        log.info("Crawled %s raw articles", len(articles))

        # 2) Enrich batch (NLP + Sentiment)
        processed = enrich_articles_batch(articles)

        # 3) Lọc theo base/quote (ưu tiên cặp từ NLP)
        if base and quote:
            B, Q = base.upper(), quote.upper()
            def has_pair(a):
                ps = a.get("pairs") or []
                for p in ps:
                    b, q = (p.get("base") or "").upper(), (p.get("quote") or "").upper()
                    if (b == B and q == Q) or (b == Q and q == B):
                        return True
                text = (a["title"] + " " + a["content"]).upper()
                return (f"{B}/{Q}" in text) or (f"{Q}/{B}" in text) or (B in text and Q in text)
            processed = [a for a in processed if has_pair(a)]
            log.info("After base/quote filter: %s articles", len(processed))

        # 4) Persist
        saved = persist_articles(processed)
        log.info("Processed=%s, persisted=%s", len(processed), saved)

        # 5) Predict (non-blocking) với sentiment điều chỉnh (trung bình, kẹp ±0.2)
        if base and quote:
            if processed:
                avg_senti = sum(a.get("sentiment_score", 0.0) for a in processed) / len(processed)
                adj = max(-0.2, min(0.2, float(avg_senti)))
            else:
                adj = 0.0
            call_predict_v2(base_currency=base, target_currency=quote, horizon_days=7, sentiment_adjust=adj)

        # 6) Payload
        payload = {
            "status": "success",
            "count": len(processed),
            "news": processed,
            "window_hours": window_hours,
            "groups": groups_param or ",".join(DEFAULT_GROUPS),
            "base": base,
            "quote": quote,
        }

        # 7) ETag/Last-Modified
        last_modified = newest_iso(processed)
        etag = make_etag({
            "urls": [a["url"] for a in processed],
            "count": len(processed),
            "last_modified": last_modified,
            "groups": payload["groups"],
            "base": (base or "").upper(),
            "quote": (quote or "").upper(),
            "window_hours": window_hours,
            "limit": limit,
        })

        cached = conditional_response(request, etag, last_modified)
        if cached:
            update_meta_after_crawl(key, etag, last_modified)
            return cached

        resp = jsonify(payload)
        resp.headers["ETag"] = etag
        resp.headers["Last-Modified"] = http_date(datetime.fromisoformat(last_modified.replace("Z","+00:00")))
        resp.headers["Cache-Control"] = "public, max-age=120"
        resp.headers["Vary"] = "Accept, If-None-Match, If-Modified-Since"
        update_meta_after_crawl(key, etag, last_modified)
        return resp

    except Exception as e:
        log.exception("Crawl error")
        return jsonify({"status": "error", "message": str(e)}), 500

@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "healthy", "service": "crawl-service"})

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5003, debug=False)

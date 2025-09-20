# app.py — sentiment-service (lexicon+negation+percent, sigmoid+finance ctx), tuned low-sensitivity
from flask import Flask, request, jsonify
from flask_cors import CORS
import logging, os, re, math
from typing import List, Tuple

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("sentiment-service")

app = Flask(__name__)
CORS(app)

app.config["MAX_CONTENT_LENGTH"] = int(os.getenv("MAX_CONTENT_LENGTH", str(2 * 1024 * 1024)))

# ===== Lexicons =====
POSITIVE = {
    "positive","good","great","excellent","strong","growth","profit","gain","rise","up","higher","high",
    "bullish","optimistic","recovery","surge","rally","boom","success","win","wins","beat","beats","outperform",
    "upgrade","upgraded","record","improve","improves","improved","expansion","expand","expands",
    "rises","rising","soar","soars","soaring","jump","jumps","jumped","spike","spiked","advance",
    "advances","advancing","breakthrough","breakout","momentum","strength","robust","solid",
    "exceed","exceeds","exceeded","outpace","outpaces","accelerate","accelerating","rebound","rebounds"
}
NEGATIVE = {
    "negative","bad","poor","weak","loss","decline","fall","down","lower","low",
    "bearish","pessimistic","crash","drop","plunge","slump","failure","lose","loses","miss","misses","underperform",
    "downgrade","downgraded","slowdown","contract","contracts","contracted","warning","risk","risks","danger",
    "falls","falling","tumble","tumbles","tumbling","sink","sinks","sinking","collapse","collapses",
    "slide","slides","sliding","weaken","weakens","weakening","struggle","struggles","struggling",
    "concern","concerns","uncertainty","volatile","volatility","pressure","pressures","deficit","debt"
}
NEUTRAL = {"stable","steady","maintain","maintains","hold","unchanged","flat","neutral","mixed","balanced","sideways"}

INTENSIFIERS = {"very":1.35, "extremely":1.5, "significantly":1.45, "strongly":1.3, "sharply":1.45, "dramatically":1.55}
DIMINISHERS  = {"slightly":0.65, "somewhat":0.75, "marginally":0.75, "barely":0.55}
NEGATIONS    = {"not","no","never","none","hardly","without","less","unlikely"}

# Heuristics
PCT_INLINE   = re.compile(r"([+\-]?\d+(?:\.\d+)?)\s*%")
UP_PATTERN   = re.compile(r"\b(up|rise|rises|gains?|surge|rally|higher|increase|increases|grew|growth)\b", re.I)
DOWN_PATTERN = re.compile(r"\b(down|fall|falls|decline|declines|drop|plunge|lower|decrease|decreases|shrinks?)\b", re.I)
TOKEN_RE     = re.compile(r"[A-Za-z']+|[+\-]?\d+(?:\.\d+)?%?")

FINANCE_CTX = {
    "fed","ecb","boj","rate","rates","interest","inflation","cpi","ppi",
    "gdp","jobs","unemployment","tariff","trade","sanction","bond","yield",
    "usd","eur","jpy","gbp","cny","vnd","fx","forex","currency","exchange"
}

def _tokenize(text: str) -> List[str]:
    return [t.lower() for t in TOKEN_RE.findall(text)]

def _apply_window_negation(tokens: List[str], idx: int, window: int = 3) -> bool:
    start = max(0, idx - window)
    return any(t in NEGATIONS for t in tokens[start:idx])

def _word_score(token: str) -> float:
    if token in POSITIVE: return 1.0
    if token in NEGATIVE: return -1.0
    if token in NEUTRAL:  return 0.0
    return 0.0

def _intensity_multiplier(prev_token: str) -> float:
    if prev_token in INTENSIFIERS: return INTENSIFIERS[prev_token]
    if prev_token in DIMINISHERS:  return DIMINISHERS[prev_token]
    return 1.0

def _percent_effect(text: str) -> float:
    """
    Map % movement near up/down verbs to a bounded signal (softer: up to ±0.5).
    """
    bonus = 0.0
    for m in PCT_INLINE.finditer(text):
        try:
            val = float(m.group(1))
        except:
            continue
        mag = min(abs(val) / 12.0, 0.5)  # nhẹ hơn (chia 12, max 0.5)
        span_start = max(0, m.start() - 20)
        span_end   = min(len(text), m.end() + 20)
        ctx = text[span_start:span_end]
        up   = bool(UP_PATTERN.search(ctx))
        down = bool(DOWN_PATTERN.search(ctx))
        if val > 0 and up:       bonus += mag
        elif val < 0 and down:   bonus -= mag
        elif val > 0 and down:   bonus -= mag * 0.4
        elif val < 0 and up:     bonus += mag * 0.4
    return bonus

def _sigmoid(x: float) -> float:
    return (2 / (1 + math.exp(-1.2 * x))) - 1.0

def analyze_text(text: str) -> Tuple[str, float]:
    if not text:
        return ("neutral", 0.0)
    tokens = _tokenize(text)
    if not tokens:
        return ("neutral", 0.0)

    base_score = 0.0
    finance_hits = 0
    for i, tok in enumerate(tokens):
        base = _word_score(tok)
        if tok in FINANCE_CTX:
            finance_hits += 1
        if base == 0.0:
            continue
        negated = _apply_window_negation(tokens, i, window=3)
        val = -base if negated else base
        prev_tok = tokens[i-1] if i > 0 else ""
        val *= _intensity_multiplier(prev_tok)
        base_score += val

    pct_bonus = _percent_effect(text)
    raw = base_score + pct_bonus
    raw = _sigmoid(raw)  # nén

    wc = len([t for t in tokens if t.isalpha()])
    if wc <= 3:
        raw *= 0.45
    elif wc <= 10:
        raw *= 0.65
    else:
        raw *= 0.85

    if finance_hits > 0:
        raw *= min(1.0, 0.9 + 0.04 * finance_hits)  # boost nhẹ hơn
        raw = max(-1.0, min(1.0, raw))

    label = "positive" if raw > 0.06 else ("negative" if raw < -0.06 else "neutral")
    return (label, round(raw, 4))

# ===== Endpoints =====
@app.route('/sentiment', methods=['POST'])
def sentiment():
    data = request.get_json(silent=True) or {}
    text = (data.get('text') or '').strip()
    if not text:
        return jsonify({'error':'text is required'}), 400
    label, score = analyze_text(text)
    return jsonify({'text': text, 'label': label, 'score': score})

@app.route('/sentiment/batch', methods=['POST'])
def sentiment_batch():
    data = request.get_json(silent=True) or {}
    items = data.get('items') or []
    out = []
    for it in items:
        _id = it.get('id')
        text = (it.get('text') or '').strip()
        if not text:
            out.append({'id': _id, 'label':'neutral','score':0.0})
        else:
            label, score = analyze_text(text)
            out.append({'id': _id, 'label': label, 'score': score})
    return jsonify({'results': out, 'count': len(out)})

@app.route('/health', methods=['GET'])
def health():
    return jsonify({'status':'healthy','service':'sentiment-service'})

@app.after_request
def add_version_header(resp):
    resp.headers["X-Model-Version"] = "lexicon-v2.1-lowgain"
    return resp

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5002, debug=False)

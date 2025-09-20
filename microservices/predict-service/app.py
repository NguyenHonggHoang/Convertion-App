# app.py — predict-service (Holt+damping, sentiment→slope+impulse), tuned low-sensitivity
from flask import Flask, request, jsonify
from flask_cors import CORS
import pandas as pd
import numpy as np
from datetime import datetime, timedelta
import logging, json, hashlib, math, os
import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry
from auth_client import get_internal_api_headers

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("predict-service")

app = Flask(__name__)
CORS(app)

# ===== Config =====
app.config["MAX_CONTENT_LENGTH"] = int(os.getenv("MAX_CONTENT_LENGTH", str(2 * 1024 * 1024)))
_PRED_CACHE = {}
_PRED_CACHE_TTL = int(os.getenv("PRED_CACHE_TTL", "300"))  # seconds

# External history (optional)
HISTORY_SOURCE_URL = os.getenv("HISTORY_SOURCE_URL", "").strip()
HISTORY_SOURCE_AUTH = os.getenv("HISTORY_SOURCE_AUTH", "none").lower()   # none|apikey|bearer
HISTORY_SOURCE_API_KEY_HEADER = os.getenv("HISTORY_SOURCE_API_KEY_HEADER", "X-API-Key")
HISTORY_SOURCE_API_KEY = os.getenv("HISTORY_SOURCE_API_KEY", "").strip()
HISTORY_SOURCE_BEARER = os.getenv("HISTORY_SOURCE_BEARER", "").strip()

# Preprocess controls
FILL_MISSING_DAYS = os.getenv("FILL_MISSING_DAYS", "false").lower() == "true"
MAD_K = float(os.getenv("MAD_K", "6.0"))
USE_LOG_VOL_THRESHOLD = float(os.getenv("USE_LOG_VOL_THRESHOLD", "0.12"))  # hạ ngưỡng chút

# Soft clamp percentage controls
# Base fallback clamp pct (used when sentiment is not provided)
SOFT_CLAMP_PCT = float(os.getenv("SOFT_CLAMP_PCT", "0.005"))
# Sentiment-based clamp gain: pct = abs(sentiment) * SENTI_CLAMP_GAIN (e.g., 0.6 -> 0.06 when gain=0.1)
SENTI_CLAMP_GAIN = float(os.getenv("SENTI_CLAMP_GAIN", "0.1"))
# Minimum and maximum clamp bounds for stability
MIN_SOFT_CLAMP_PCT = float(os.getenv("MIN_SOFT_CLAMP_PCT", "0.005"))
MAX_SOFT_CLAMP_PCT = float(os.getenv("MAX_SOFT_CLAMP_PCT", "0.10"))

# Time-decay weights (giữ để tương thích — không dùng trong Holt)
WEIGHT_HALF_LIFE_DAYS = int(os.getenv("WEIGHT_HALF_LIFE_DAYS", "14"))

# --- Holt’s Linear smoothing (độ nhạy thấp hơn) ---
HOLT_ALPHA = float(os.getenv("HOLT_ALPHA", "0.30"))       # level
HOLT_BETA  = float(os.getenv("HOLT_BETA",  "0.20"))       # trend
HOLT_DAMPING = float(os.getenv("HOLT_DAMPING", "0.92"))   # <1.0

# --- Sentiment mapping (độ nhạy thấp hơn) ---
SENTI_SLOPE_GAIN   = float(os.getenv("SENTI_SLOPE_GAIN", "0.35"))  # ảnh hưởng lên độ dốc
SENTI_IMPULSE_GAIN = float(os.getenv("SENTI_IMPULSE_GAIN", "0.50"))# xung ngắn hạn
SENTI_HALF_LIFE_DAYS = int(os.getenv("SENTI_HALF_LIFE_DAYS", "2")) # xung tắt nhanh

# ===== Cache helpers =====
def _cache_key(base, quote, horizon, sentiment_adj, hist_hash):
    return f"{base}|{quote}|{horizon}|{sentiment_adj}|{hist_hash}"

def _get_cached(key):
    item = _PRED_CACHE.get(key)
    if not item:
        return None
    if (datetime.utcnow().timestamp() - item["ts"]) > _PRED_CACHE_TTL:
        return None
    return item["preds"]

def _put_cache(key, preds):
    _PRED_CACHE[key] = {"preds": preds, "ts": datetime.utcnow().timestamp()}

# ===== Utils =====
def _history_hash(df: pd.DataFrame) -> str:
    recs = [{"d": d.strftime("%Y-%m-%d"), "r": float(r)} for d, r in zip(df["date"], df["rate"])]
    blob = json.dumps(recs, separators=(",", ":"), sort_keys=True).encode("utf-8")
    return hashlib.sha1(blob).hexdigest()

def _requests_session():
    s = requests.Session()
    retry = Retry(total=2, backoff_factor=0.2, status_forcelist=[429, 500, 502, 503, 504])
    s.mount("http://", HTTPAdapter(max_retries=retry))
    s.mount("https://", HTTPAdapter(max_retries=retry))
    return s

def _fetch_history_external(base: str, quote: str):
    if not HISTORY_SOURCE_URL:
        return []
    url = HISTORY_SOURCE_URL.format(base=base, quote=quote)
    headers = {"Accept": "application/json"}

    auth_headers = get_internal_api_headers()
    headers.update(auth_headers or {})
    if not auth_headers and HISTORY_SOURCE_AUTH == "apikey" and HISTORY_SOURCE_API_KEY:
        headers[HISTORY_SOURCE_API_KEY_HEADER] = HISTORY_SOURCE_API_KEY
        logger.warning("Using legacy API key authentication")
    elif not auth_headers and HISTORY_SOURCE_AUTH == "bearer" and HISTORY_SOURCE_BEARER:
        headers["Authorization"] = f"Bearer {HISTORY_SOURCE_BEARER}"
        logger.warning("Using legacy Bearer token authentication")

    try:
        s = _requests_session()
        r = s.get(url, headers=headers, timeout=10)
        if r.status_code == 304:
            logger.info("History not modified (304) for %s->%s", base, quote)
            return []
        if r.status_code != 200:
            red_headers = {k: v for k, v in headers.items() if k != HISTORY_SOURCE_API_KEY_HEADER}
            logger.warning("History fetch non-200 for %s->%s: %s (headers: %s)", base, quote, r.status_code, red_headers)
            return []
        j = r.json()
        if isinstance(j, list):
            return j
        if isinstance(j, dict):
            if "history" in j and isinstance(j["history"], list):
                return [{"date": it["date"], "rate": float(it["rate"])} for it in j["history"]]
            for key in ("data", "results"):
                v = j.get(key)
                if isinstance(v, list):
                    return v
        return []
    except Exception as e:
        logger.warning("History fetch failed for %s->%s: %s", base, quote, str(e))
        return []

def _preprocess_history(df: pd.DataFrame) -> pd.DataFrame:
    df = df.copy()
    df["date"] = pd.to_datetime(df["date"], errors="raise")
    df = df.sort_values("date").drop_duplicates(subset=["date"], keep="last")

    if "rate" not in df or df["rate"].isnull().any():
        raise ValueError("Historical data contains null/missing rate values")

    df["rate"] = pd.to_numeric(df["rate"], errors="raise")
    if (df["rate"] <= 0).any():
        raise ValueError("All exchange rates must be positive")

    if FILL_MISSING_DAYS:
        df = df.set_index("date").asfreq("D").ffill().reset_index()

    # MAD clipping
    med = df["rate"].median()
    mad = (df["rate"] - med).abs().median()
    if mad and mad > 0:
        lo = med - MAD_K * 1.4826 * mad
        hi = med + MAD_K * 1.4826 * mad
        df["rate"] = df["rate"].clip(lower=max(lo, 1e-12), upper=hi)

    return df

def _log_returns_std(df: pd.DataFrame) -> float:
    r = np.log(df["rate"].values.astype(float))
    if len(r) < 2:
        return 0.0
    lr = np.diff(r)
    med = np.median(lr)
    mad = np.median(np.abs(lr - med))
    if mad > 0:
        return 1.4826 * mad
    return float(np.std(lr, ddof=1)) if len(lr) > 1 else 0.0

def _holt_linear_predict(df: pd.DataFrame, horizon: int, sentiment_adj: float, use_log: bool):
    series = df["rate"].values.astype(float)
    y = np.log(series) if use_log else series
    n = len(y)
    if n < 2:
        raise ValueError("Need at least 2 data points for Holt smoothing")

    l = y[0]
    b = y[1] - y[0] if n >= 2 else 0.0

    alpha = min(max(HOLT_ALPHA, 1e-6), 0.9999)
    beta  = min(max(HOLT_BETA,  1e-6), 0.9999)
    phi   = min(max(HOLT_DAMPING, 0.80), 0.9999)

    one_step_err = []
    for t in range(1, n):
        f = l + phi * b
        e = y[t] - f
        one_step_err.append(e)
        l_new = alpha * y[t] + (1 - alpha) * f
        b_new = beta * (l_new - l) + (1 - beta) * (phi * b)
        l, b = l_new, b_new

    resid_std = float(np.std(one_step_err, ddof=1)) if len(one_step_err) > 1 else 0.0
    ci_z = 1.96

    s = float(sentiment_adj) if sentiment_adj is not None else 0.0
    s = max(-1.0, min(1.0, s))

    sigma_lr = _log_returns_std(df)
    sigma_scale = max(sigma_lr, 1e-4)  # tối thiểu để không “0 hóa” impulse

    b_eff = b * (1.0 + SENTI_SLOPE_GAIN * s)
    impulse_gain = SENTI_IMPULSE_GAIN * s * sigma_scale

    if SENTI_HALF_LIFE_DAYS <= 0:
        tau = 1e9
    else:
        tau = SENTI_HALF_LIFE_DAYS / math.log(2.0)

    last_date = df["date"].max()
    preds = []
    for m in range(1, horizon + 1):
        if phi == 1.0:
            trend_term = m * b_eff
        else:
            trend_term = b_eff * (phi * (1 - phi**m) / (1 - phi))
        impulse = impulse_gain * math.exp(-m / tau)

        yhat = l + trend_term + impulse
        ci = ci_z * resid_std * math.sqrt(m)

        if use_log:
            mid = math.exp(yhat)
            lo  = math.exp(yhat - ci)
            hi  = math.exp(yhat + ci)
        else:
            mid = yhat
            lo  = yhat - ci
            hi  = yhat + ci

        mid = max(mid, 1e-12)
        lo  = max(lo,  1e-12)

        preds.append({
            "date": (last_date + timedelta(days=m)).strftime("%Y-%m-%d"),
            "predicted_rate": round(float(mid), 6),
            "ci_low": round(float(lo), 6),
            "ci_high": round(float(hi), 6),
        })
    return preds

def _fit_predict(df: pd.DataFrame, horizon: int, sentiment_adj):
    df = _preprocess_history(df)
    if len(df) < 2:
        raise ValueError("Need at least 2 data points for trend analysis")

    rates = df["rate"].values.astype(float)
    vol = float(np.std(rates, ddof=1)) / float(np.mean(rates)) if np.mean(rates) > 0 else 0.0
    use_log = vol > USE_LOG_VOL_THRESHOLD

    if sentiment_adj is not None:
        sentiment_adj = float(sentiment_adj)
        if sentiment_adj < -1.0 or sentiment_adj > 1.0:
            raise ValueError("sentiment_adjust must be between -1.0 and 1.0")

    return _holt_linear_predict(df, horizon, sentiment_adj, use_log)

# Removed USD/VND hard-coded clamp; we apply a global soft clamp for all pairs

def _clamp_near_last(df: pd.DataFrame, preds: list, pct: float) -> list:
    try:
        last_rate = float(df["rate"].iloc[-1])
    except Exception:
        return preds
    if last_rate <= 0 or pct <= 0:
        return preds
    lo = last_rate * (1.0 - pct)
    hi = last_rate * (1.0 + pct)
    out = []
    for p in preds:
        pr = float(p.get('predicted_rate', 0))
        l  = float(p.get('ci_low', 0))
        h  = float(p.get('ci_high', 0))
        pr = max(lo, min(hi, pr))
        l  = max(lo, min(hi, l))
        h  = max(lo, min(hi, h))
        out.append({**p, 'predicted_rate': round(pr, 6), 'ci_low': round(l, 6), 'ci_high': round(h, 6)})
    return out

def _compute_soft_clamp_pct(sentiment_adj) -> float:
    """Compute soft clamp pct using sentiment when available.
    - If sentiment is provided in [-1, 1], use pct = clip(abs(s) * SENTI_CLAMP_GAIN, MIN, MAX)
    - Otherwise, use SOFT_CLAMP_PCT
    """
    try:
        if sentiment_adj is None:
            return SOFT_CLAMP_PCT
        s = float(sentiment_adj)
        s = max(-1.0, min(1.0, s))
        pct = abs(s) * SENTI_CLAMP_GAIN
        pct = max(MIN_SOFT_CLAMP_PCT, min(MAX_SOFT_CLAMP_PCT, pct))
        return pct
    except Exception:
        return SOFT_CLAMP_PCT

# ===== Endpoints =====
@app.route('/predict', methods=['POST'])
def predict():
    try:
        data = request.get_json(silent=True) or {}
        base = (data.get('base_currency') or '').strip().upper()
        quote = (data.get('target_currency') or '').strip().upper()
        horizon = int(data.get('horizon_days', 7))
        # Force horizon to 2 days max as requested
        horizon = max(1, min(2, horizon))
        sentiment_adj = data.get('sentiment_adjust', None)

        news_data = data.get('news_data', [])
        if news_data and isinstance(news_data, list):
            scores = []
            for news in news_data:
                if isinstance(news, dict) and 'sentiment_score' in news:
                    try:
                        s = float(news.get('sentiment_score', 0))
                        if -1 <= s <= 1:
                            scores.append(s)
                    except (ValueError, TypeError):
                        continue
            if scores:
                weights = [1.0 / (i + 1) for i in range(len(scores))]
                weighted_avg = sum(s * w for s, w in zip(scores, weights)) / sum(weights)
                if sentiment_adj is not None:
                    sentiment_adj = (float(sentiment_adj) + weighted_avg) / 2
                else:
                    sentiment_adj = weighted_avg * 0.5
                logger.info("News sentiment applied: avg=%.3f, final_adj=%.3f", weighted_avg, sentiment_adj)

        if sentiment_adj is not None:
            try:
                sentiment_adj = float(sentiment_adj)
            except (ValueError, TypeError):
                return jsonify({'error': 'sentiment_adjust must be a valid number'}), 400

        if not base or not quote:
            return jsonify({'error':'base_currency and target_currency are required'}), 400
        if horizon <= 0 or horizon > 60:
            return jsonify({'error':'horizon_days must be in (0, 60]'}), 400

        logger.info("Predict %s->%s horizon=%s adj=%s", base, quote, horizon, sentiment_adj)

        history = data.get('history')
        if history and isinstance(history, list):
            if len(history) < 2:
                return jsonify({'error': 'Need at least 2 historical data points for prediction'}), 400
            df = pd.DataFrame(history)
            if 'date' not in df.columns or 'rate' not in df.columns:
                return jsonify({'error':'Historical data must contain "date" and "rate" fields'}), 400
        else:
            ext = _fetch_history_external(base, quote)
            if not ext:
                return jsonify({'error': f'No historical data available for {base}->{quote}. '
                                         f'Provide "history" in request or set HISTORY_SOURCE_URL env.'}), 404
            df = pd.DataFrame(ext)

        try:
            df_clean = _preprocess_history(df)
        except ValueError as e:
            return jsonify({'error': str(e)}), 400

        hist_hash = _history_hash(df_clean)
        key = _cache_key(base, quote, horizon, sentiment_adj, hist_hash)
        cached = _get_cached(key)
        if cached:
            clamp_pct = _compute_soft_clamp_pct(sentiment_adj)
            clamped = _clamp_near_last(df_clean, cached, clamp_pct)
            return jsonify({'base_currency': base, 'target_currency': quote, 'predictions': clamped})

        try:
            preds = _fit_predict(df_clean, horizon, sentiment_adj)
            preds = preds[:horizon]
        except ValueError as e:
            return jsonify({'error': f'Prediction failed: {str(e)}'}), 400
        except Exception as e:
            logger.exception("Prediction model failed")
            return jsonify({'error': f'Prediction model error: {str(e)}'}), 500

        clamp_pct = _compute_soft_clamp_pct(sentiment_adj)
        preds = _clamp_near_last(df_clean, preds, clamp_pct)
        _put_cache(key, preds)
        return jsonify({'base_currency': base, 'target_currency': quote, 'predictions': preds})

    except Exception as e:
        logger.exception("Prediction failed")
        return jsonify({'error':'Internal server error', 'detail': str(e)}), 500

@app.route('/predict/batch', methods=['POST'])
def predict_batch():
    try:
        data = request.get_json(silent=True) or {}
        reqs = data.get("requests") or []
        results = []

        for req in reqs:
            try:
                base = (req.get('base_currency') or '').strip().upper()
                quote = (req.get('target_currency') or '').strip().upper()
                horizon = int(req.get('horizon_days', 7))
                horizon = max(1, min(2, horizon))
                sentiment_adj = req.get('sentiment_adjust', None)
                if sentiment_adj is not None:
                    sentiment_adj = float(sentiment_adj)

                if not base or not quote:
                    results.append({"error": "base_currency and target_currency are required"}); continue
                if horizon <= 0 or horizon > 60:
                    results.append({"error": "horizon_days must be in (0, 60]"}); continue

                history = req.get('history')
                if history and isinstance(history, list):
                    if len(history) < 2:
                        results.append({"error": "Need at least 2 historical data points for prediction"}); continue
                    df = pd.DataFrame(history)
                    if 'date' not in df.columns or 'rate' not in df.columns:
                        results.append({"error": 'Historical data must contain "date" and "rate" fields'}); continue
                else:
                    ext = _fetch_history_external(base, quote)
                    if not ext:
                        results.append({"error": f'No historical data for {base}->{quote}'}); continue
                    df = pd.DataFrame(ext)

                try:
                    df_clean = _preprocess_history(df)
                except ValueError as e:
                    results.append({"error": str(e)}); continue

                hist_hash = _history_hash(df_clean)
                key = _cache_key(base, quote, horizon, sentiment_adj, hist_hash)
                cached = _get_cached(key)
                if cached:
                    clamp_pct = _compute_soft_clamp_pct(sentiment_adj)
                    soft = _clamp_near_last(df_clean, cached, clamp_pct)
                    results.append({"base_currency": base, "target_currency": quote, "predictions": soft})
                    continue

                preds = _fit_predict(df_clean, horizon, sentiment_adj)
                preds = preds[:horizon]
                clamp_pct = _compute_soft_clamp_pct(sentiment_adj)
                preds = _clamp_near_last(df_clean, preds, clamp_pct)
                _put_cache(key, preds)
                results.append({"base_currency": base, "target_currency": quote, "predictions": preds})
            except Exception as e:
                results.append({"error": f"failed: {str(e)}"})

        return jsonify({"results": results, "count": len(results)})
    except Exception as e:
        logger.exception("Batch predict failed")
        return jsonify({'error':'Internal server error', 'detail': str(e)}), 500

@app.route('/health', methods=['GET'])
def health():
    return jsonify({'status':'healthy', 'service':'predict-service'})

@app.after_request
def add_version_header(resp):
    resp.headers["X-Model-Version"] = "holt-v1.5-h2-softclamp-sentiment"
    return resp

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5001, debug=False)

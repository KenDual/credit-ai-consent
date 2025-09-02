import os, json
from pathlib import Path
from typing import Dict, List, Tuple, Optional

import numpy as np
import pandas as pd
import joblib

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

APP_DIR = Path(__file__).resolve().parent
ART_DIR = (APP_DIR.parent / "models").resolve()

MODEL_PATH = ART_DIR / "model.pkl"
SCHEMA_PATH = ART_DIR / "feature_schema.json"
EXPLAINER_PATH = ART_DIR / "shap_explainer.pkl"

# ---------- Load artifacts ----------
if not MODEL_PATH.exists() or not SCHEMA_PATH.exists():
    raise FileNotFoundError(f"Artifacts not found. Expect {MODEL_PATH} & {SCHEMA_PATH}")

model = joblib.load(MODEL_PATH)

with open(SCHEMA_PATH, "r", encoding="utf-8") as f:
    schema = json.load(f)

# Read features from schema; tolerate legacy key "names"
FEAT_ORDER: List[str] = list(schema.get("features") or schema.get("names") or [])
if not FEAT_ORDER:
    raise ValueError("feature_schema.json missing 'features'")

# Strip illegal columns instead of crashing (safer for dev)
ILLEGAL = {"user_id", "default_90d", "pd_true"}
present_illegal = [c for c in FEAT_ORDER if c in ILLEGAL]
if present_illegal:
    print(f"[WARN] Removing illegal columns from FEAT_ORDER: {present_illegal}")
    FEAT_ORDER = [c for c in FEAT_ORDER if c not in ILLEGAL]

# If model exposes feature names, align ordering **robustly** (LightGBM safe)
model_feats_note = None
feature_sync_note = None
MODEL_FEATURES: List[str] = []

# Prefer LightGBM booster feature names
try:
    if hasattr(model, "booster_") and model.booster_ is not None:
        MODEL_FEATURES = list(model.booster_.feature_name())
except Exception:
    MODEL_FEATURES = []

# Fallbacks
if not MODEL_FEATURES and hasattr(model, "feature_name_"):
    MODEL_FEATURES = list(getattr(model, "feature_name_"))
if not MODEL_FEATURES and hasattr(model, "feature_names_in_"):
    MODEL_FEATURES = list(getattr(model, "feature_names_in_"))

if MODEL_FEATURES:
    mset, sset = set(MODEL_FEATURES), set(FEAT_ORDER)
    if mset == sset:
        FEAT_ORDER = MODEL_FEATURES  # exact match, keep model order
    elif mset.issuperset(sset):
        missing = [c for c in MODEL_FEATURES if c not in sset]
        FEAT_ORDER = MODEL_FEATURES   # extend to model order; ensure_vector will pad zeros
        feature_sync_note = f"Schema missing {len(missing)} model features (padded with 0): {missing[:5]}..."
    elif mset.issubset(sset):
        extra = [c for c in FEAT_ORDER if c not in mset]
        FEAT_ORDER = MODEL_FEATURES
        feature_sync_note = f"Schema has {len(extra)} extra features not used by model (ignored): {extra[:5]}..."
    else:
        model_feats_note = "Model & schema feature sets differ (not subset/superset). Using schema order; may error."
else:
    # As a minimum, check count only
    if hasattr(model, "n_features_in_") and len(FEAT_ORDER) != int(getattr(model, "n_features_in_")):
        model_feats_note = "feature count differs from model.n_features_in_"

# SHAP explainer (optional)
try:
    explainer = joblib.load(EXPLAINER_PATH)
except Exception:
    explainer = None

# ---------- Versioning (for auditability) ----------
MODEL_VERSION  = os.getenv("MODEL_VERSION", str(int(MODEL_PATH.stat().st_mtime)))
SCHEMA_VERSION = schema.get("created_at") or os.getenv("FEATURE_SCHEMA_VERSION", "")

# ---------- Decision config ----------
THRESHOLD = float(os.getenv("THRESHOLD", "0.50"))      # for PD mode
DECISION_MODE = os.getenv("DECISION_MODE", "score")     # 'pd' | 'score' (default to score per MVP)
SCORE_APPROVE_MIN = int(os.getenv("SCORE_APPROVE_MIN", "700"))
SCORE_REVIEW_MIN  = int(os.getenv("SCORE_REVIEW_MIN",  "650"))
MIN_NONZERO_FEATURES = int(os.getenv("MIN_NONZERO_FEATURES", "10"))
# PD bands (used only when DECISION_MODE='pd')
PD_APPROVE_MAX = float(os.getenv("PD_APPROVE_MAX", os.getenv("PD_APPROVE", "0.01235")))
PD_REVIEW_MAX  = float(os.getenv("PD_REVIEW_MAX",  os.getenv("PD_REVIEW",  "0.02439")))

# ---------- Helpers ----------
def prob_to_score(pd_prob: np.ndarray) -> np.ndarray:
    """Map PD -> credit score (300-900)."""
    pd_prob = np.clip(pd_prob, 1e-6, 1 - 1e-6)
    odds = (1 - pd_prob) / pd_prob
    score = 600 + 50 * (np.log2(odds / 20))
    return np.clip(score, 300, 900).astype(int)


def ensure_vector(features: Dict[str, float]) -> Tuple[pd.DataFrame, List[str]]:
    """
    Build a 1-row DataFrame in FEAT_ORDER; fill missing with 0.0.
    Cast values to float safely (True/False -> 1/0).
    """
    missing = [c for c in FEAT_ORDER if c not in features]
    row = {}
    for k in FEAT_ORDER:
        v = features.get(k, 0.0)
        if isinstance(v, bool):
            v = 1.0 if v else 0.0
        try:
            row[k] = float(v)
        except Exception:
            row[k] = 0.0
    X = pd.DataFrame([row], columns=FEAT_ORDER)
    return X, missing


def shap_topk(X: pd.DataFrame, k: int = 5):
    """Return top-k SHAP contributions as list of dicts. Empty list if explainer not available.
    Filters out ILLEGAL features (e.g., pd_true) from display.
    """
    if explainer is None:
        return []
    try:
        out = explainer(X)
        sv = out.values if hasattr(out, "values") else np.array(out)
    except Exception:
        sv = explainer.shap_values(X)
        if isinstance(sv, list):
            sv = sv[-1]
    sv = np.array(sv)[0]
    vals = X.iloc[0].to_dict()
    idx = np.argsort(np.abs(sv))[::-1]
    top = []
    for i in idx:
        name = FEAT_ORDER[i]
        if name in ILLEGAL:
            continue
        top.append({
            "feature": name,
            "value": float(vals.get(name, 0.0)),
            "shap": float(sv[i]),
            "abs_shap": float(abs(sv[i])),
            "direction": "up" if sv[i] > 0 else "down"
        })
        if len(top) >= k:
            break
    return top

# ---------- Schemas ----------
class ShapItem(BaseModel):
    feature: str
    value: float
    shap: float
    abs_shap: float
    direction: str


class ScoreIn(BaseModel):
    features: Dict[str, float] = Field(..., description="Feature dictionary")


class ScoreOut(BaseModel):
    pd: float
    score: int
    decision: str
    threshold: float
    missing_features: List[str]
    shapTopK: List[ShapItem]
    decision_mode: str
    score_threshold: Optional[int] = None
    score_approve_min: Optional[int] = None
    score_review_min: Optional[int] = None
    used_nonzero_features: Optional[int] = None
    model_feats_note: Optional[str] = None
    stripped_illegal: Optional[List[str]] = None
    # versions for audit
    model_version: Optional[str] = None
    feature_schema_version: Optional[str] = None
    # pd bands (only when DECISION_MODE='pd')
    pd_approve_max: Optional[float] = None
    pd_review_max: Optional[float] = None

# ---------- App ----------
app = FastAPI(title="Credit AI Scoring Service", version="1.2.2")

# (Optional) CORS if needed by browser clients
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"], allow_credentials=True, allow_methods=["*"], allow_headers=["*"],
)


@app.get("/health")
def health():
    return {
        "status": "ok",
        "model_path": str(MODEL_PATH),
        "feature_count": len(FEAT_ORDER),
        "threshold": THRESHOLD,
        "decision_mode": DECISION_MODE,
        "score_approve_min": SCORE_APPROVE_MIN,
        "score_review_min": SCORE_REVIEW_MIN,
        "pd_approve_max": PD_APPROVE_MAX,
        "pd_review_max": PD_REVIEW_MAX,
        "model_feats_note": model_feats_note,
        "feature_sync_note": feature_sync_note,
        "model_feature_count": len(MODEL_FEATURES) if MODEL_FEATURES else None,
        "schema_feature_count": len(FEAT_ORDER),
        "stripped_illegal": present_illegal,
        "model_version": MODEL_VERSION,
        "feature_schema_version": SCHEMA_VERSION,
        "pd_approve_max": PD_APPROVE_MAX if DECISION_MODE == "pd" else None,
        "pd_review_max": PD_REVIEW_MAX if DECISION_MODE == "pd" else None,
    }


@app.get("/schema/features")
def schema_features():
    return {
        "features": FEAT_ORDER,
        "count": len(FEAT_ORDER),
        "model_features": MODEL_FEATURES if MODEL_FEATURES else None,
        "feature_schema_version": SCHEMA_VERSION
    }


@app.post("/score", response_model=ScoreOut)
def score(inp: ScoreIn):
    X, missing = ensure_vector(inp.features)
    pd_prob = float(model.predict_proba(X)[:, 1][0])
    score_val = int(prob_to_score(np.array([pd_prob]))[0])

    # Primary decision
    if DECISION_MODE == "pd":
        if pd_prob < PD_APPROVE_MAX:
            decision = "approve"
        elif pd_prob < PD_REVIEW_MAX:
            decision = "review"
        else:
            decision = "reject"
        score_threshold = None
        score_approve_min = None
        score_review_min  = None
    else:
        s = score_val
        if s >= SCORE_APPROVE_MIN:
            decision = "approve"
        elif s >= SCORE_REVIEW_MIN:
            decision = "review"
        else:
            decision = "reject"
        score_threshold = None
        score_approve_min = SCORE_APPROVE_MIN
        score_review_min  = SCORE_REVIEW_MIN

    # Gate: avoid auto-approve if data too sparse
    nonzero = int((X.values != 0).sum())
    if nonzero < MIN_NONZERO_FEATURES and decision == "approve":
        decision = "review"

    try:
        topk = shap_topk(X, k=3)  # top-3 per MVP
    except Exception:
        topk = []

    return {
        "pd": pd_prob,
        "score": score_val,
        "decision": decision,
        "threshold": THRESHOLD,
        "missing_features": missing,
        "shapTopK": topk,
        "decision_mode": DECISION_MODE,
        "score_threshold": score_threshold,
        "score_approve_min": score_approve_min,
        "score_review_min": score_review_min,
        "used_nonzero_features": nonzero,
        "model_feats_note": model_feats_note,
        "stripped_illegal": present_illegal,
        "model_version": MODEL_VERSION,
        "feature_schema_version": SCHEMA_VERSION,
    }

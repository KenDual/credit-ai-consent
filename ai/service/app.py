import os, json
from pathlib import Path
from typing import Dict, List, Tuple

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
FEAT_ORDER: List[str] = list(schema["names"])

try:
    explainer = joblib.load(EXPLAINER_PATH)
except Exception:
    explainer = None  # SHAP optional

THRESHOLD = float(os.getenv("THRESHOLD", "0.50"))

# ---------- Helpers ----------
def prob_to_score(pd_prob: np.ndarray) -> np.ndarray:
    """Map PD -> credit score (300-900)."""
    pd_prob = np.clip(pd_prob, 1e-6, 1 - 1e-6)
    odds = (1 - pd_prob) / pd_prob
    score = 600 + 50 * (np.log2(odds / 20))
    return score.astype(int)

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
    """Return top-k SHAP contributions as list of dicts. Empty list if explainer not available."""
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
    idx = np.argsort(np.abs(sv))[::-1][:k]
    top = []
    for i in idx:
        top.append({
            "feature": FEAT_ORDER[i],
            "value": float(vals[FEAT_ORDER[i]]),
            "shap": float(sv[i]),
            "abs_shap": float(abs(sv[i])),
            "direction": "up" if sv[i] > 0 else "down"
        })
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

# ---------- App ----------
app = FastAPI(title="Credit AI Scoring Service", version="1.0.0")

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
    }

@app.get("/schema/features")
def schema_features():
    return {"features": FEAT_ORDER, "count": len(FEAT_ORDER)}

@app.post("/score", response_model=ScoreOut)
def score(inp: ScoreIn):
    X, missing = ensure_vector(inp.features)
    pd_prob = float(model.predict_proba(X)[:, 1][0])
    decision = "approve" if pd_prob < THRESHOLD else "reject"
    try:
        topk = shap_topk(X, k=5)
    except Exception:
        topk = []
    return {
        "pd": pd_prob,
        "score": int(prob_to_score(np.array([pd_prob]))[0]),
        "decision": decision,
        "threshold": THRESHOLD,
        "missing_features": missing,
        "shapTopK": topk
    }

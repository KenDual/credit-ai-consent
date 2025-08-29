# ai/service/app.py
import os, json
from pathlib import Path
from typing import Dict, List, Optional
import numpy as np
import pandas as pd
import joblib
from fastapi import FastAPI
from pydantic import BaseModel, Field

APP_DIR = Path(__file__).resolve().parent
ART_DIR = (APP_DIR.parent / "models").resolve()

MODEL_PATH = ART_DIR / "model.pkl"
SCHEMA_PATH = ART_DIR / "feature_schema.json"
EXPLAINER_PATH = ART_DIR / "shap_explainer.pkl"

# ---------- Load artifacts ----------
model = joblib.load(MODEL_PATH)
with open(SCHEMA_PATH, "r", encoding="utf-8") as f:
    schema = json.load(f)
FEAT_ORDER: List[str] = list(schema["names"])
try:
    explainer = joblib.load(EXPLAINER_PATH)
except Exception:
    explainer = None

THRESHOLD = float(os.getenv("THRESHOLD", "0.50"))  # PD threshold (default 0.50)

def prob_to_score(pd_prob: np.ndarray) -> np.ndarray:
    # Map PD -> score (300-900). Same formula as training script.
    pd_prob = np.clip(pd_prob, 1e-6, 1 - 1e-6)
    odds = (1 - pd_prob) / pd_prob
    score = 600 + 50 * (np.log2(odds / 20))
    return score.astype(int)

def ensure_vector(features: Dict[str, float]) -> (pd.DataFrame, List[str]):
    """Return 1-row DataFrame in the exact FEAT_ORDER; fill missing with 0.0."""
    missing = [c for c in FEAT_ORDER if c not in features]
    row = {k: float(features.get(k, 0.0)) for k in FEAT_ORDER}
    X = pd.DataFrame([row], columns=FEAT_ORDER)
    return X, missing

def shap_topk(X: pd.DataFrame, k: int = 5):
    if explainer is None:
        return []
    try:
        out = explainer(X)  # new SHAP API
        sv = out.values if hasattr(out, "values") else np.array(out)
    except Exception:
        sv = explainer.shap_values(X)  # legacy API
        # binary: sometimes returns list of arrays
        if isinstance(sv, list) and len(sv) == 2:
            sv = sv[1]
    sv = np.array(sv)[0]  # (n_features,)
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

class ScoreIn(BaseModel):
    # map<feature_name, value>; thiếu feature sẽ tự điền 0
    features: Dict[str, float] = Field(..., description="Feature dictionary")

class ScoreOut(BaseModel):
    pd: float
    score: int
    decision: str
    threshold: float
    missing_features: List[str]
    shapTopK: List[Dict[str, float]]

app = FastAPI(title="Credit AI Scoring Service", version="1.0.0")

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
    # NOTE: model's positive class is "default=1", so pd = risk of default
    pd_prob = float(model.predict_proba(X)[:, 1][0])
    decision = "approve" if pd_prob < THRESHOLD else "reject"
    topk = shap_topk(X, k=5)
    return {
        "pd": pd_prob,
        "score": int(prob_to_score(np.array([pd_prob]))[0]),
        "decision": decision,
        "threshold": THRESHOLD,
        "missing_features": missing,
        "shapTopK": topk
    }

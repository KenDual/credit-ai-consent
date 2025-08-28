# ai/service/app.py
from fastapi import FastAPI
from pydantic import BaseModel
from typing import Optional, Dict

app = FastAPI(title="Credit AI Scoring Service", version="0.1.0")

class ScoreRequest(BaseModel):
    features: Dict[str, float]
    return_explain: bool = True

@app.get("/health")
def health():
    return {"status": "ok"}

@app.post("/score")
def score(req: ScoreRequest):
    # Stub logic (chạy được ngay, sẽ thay bằng model thật sau)
    x = req.features
    base = 0.1
    pd = base \
        + 0.6 * float(x.get("sms_overdue_ratio", 0)) \
        + 0.2 * float(x.get("web_risky_visits", 0)) \
        + (-0.00001) * float(x.get("ecom_total_spend", 0))
    pd = max(0.0, min(1.0, pd))
    score = 1 - pd
    decision = "approve" if score >= 0.5 else ("review" if score >= 0.35 else "reject")
    return {
        "score": round(score, 4),
        "pd": round(pd, 4),
        "decision": decision,
        "shap_values": None
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("service.app:app", host="127.0.0.1", port=8001, reload=True)

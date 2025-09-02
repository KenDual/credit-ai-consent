# ai/train/train_lgbm.py (prior-aware calibrated, leakage-free, clean schema)
import argparse, json, os, sys, warnings
from pathlib import Path
warnings.filterwarnings("ignore")

import numpy as np
import pandas as pd
from sklearn.metrics import roc_auc_score, brier_score_loss, log_loss, f1_score, confusion_matrix
from sklearn.model_selection import train_test_split
from sklearn.utils import class_weight
from sklearn.calibration import CalibratedClassifierCV
import lightgbm as lgb
import joblib
import shap
from datetime import datetime

TARGET_CANDIDATES = ["label", "target", "default", "bad_flag", "pd_12m", "y", "default_90d"]
ID_CANDIDATES = ["app_id", "application_id", "id", "appId", "applicationId", "user_id"]
# Columns that must NEVER be used as model features
ILLEGAL = {"pd_true", "user_id", "default_90d", "tx_hash", "consent_id"}

def parse_args():
    ap = argparse.ArgumentParser(description="Train LightGBM credit model (prior-aware calibration)")
    ap.add_argument("--in", dest="in_path", required=True, help="Path to features parquet/csv")
    ap.add_argument("--out", dest="out_dir", required=True, help="Output folder for artifacts")
    ap.add_argument("--target", dest="target", default=None, help="Target column (0/1). If omitted, auto-detect")
    ap.add_argument("--id", dest="id_col", default=None, help="ID column. If omitted, auto-detect or create")
    ap.add_argument("--seed", type=int, default=42)
    ap.add_argument("--test_size", type=float, default=0.15)
    ap.add_argument("--val_size", type=float, default=0.15)
    ap.add_argument("--n_estimators", type=int, default=2000)
    ap.add_argument("--lr", type=float, default=0.03)
    ap.add_argument("--num_leaves", type=int, default=63)
    ap.add_argument("--csv", action="store_true", help="Input is CSV instead of Parquet")
    ap.add_argument("--target_base_pd", type=float, default=None, help="Desired base PD used for calibration (e.g., 0.08)")
    return ap.parse_args()

def autodetect(colnames, candidates):
    for c in candidates:
        if c in colnames:
            return c
    return None

def ks_stat(y_true, y_prob):
    d = pd.DataFrame({"y": y_true, "p": y_prob}).sort_values("p")
    n_bad = max(1, int((d["y"] == 1).sum()))
    n_good = max(1, int((d["y"] == 0).sum()))
    cum_bad = (d["y"] == 1).cumsum() / n_bad
    cum_good = (d["y"] == 0).cumsum() / n_good
    return float((cum_bad - cum_good).abs().max())

def prob_to_score(pd_prob):
    # Bank-grade mapping kept: 600 @ odds=20, PDO=50
    pd_prob = np.clip(pd_prob, 1e-6, 1 - 1e-6)
    odds = (1 - pd_prob) / pd_prob
    return (600 + 50 * np.log2(odds / 20)).astype(int)

def read_table(in_path: Path, csv: bool) -> pd.DataFrame:
    if csv or in_path.suffix.lower() == ".csv":
        return pd.read_csv(in_path)
    try:
        return pd.read_parquet(in_path)
    except Exception as e:
        print("⚠️ Không đọc được Parquet. Cài thêm pyarrow: pip install pyarrow", file=sys.stderr)
        raise e

def sanitize_frame(df: pd.DataFrame) -> pd.DataFrame:
    # Replace inf/nan, cast bool to 0/1
    for c in df.columns:
        if df[c].dtype == bool:
            df[c] = df[c].astype(int)
    df = df.replace([np.inf, -np.inf], np.nan).fillna(0.0)
    return df

def main():
    args = parse_args()
    in_path = Path(args.in_path)
    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    # 1) Load data
    df = read_table(in_path, args.csv)
    df = sanitize_frame(df)

    cols = df.columns.tolist()
    target = args.target or autodetect(cols, TARGET_CANDIDATES)
    if target is None:
        raise ValueError(f"Không tìm thấy cột target. Truyền --target. Cột hiện có: {cols[:30]}... (total {len(cols)})")

    id_col = args.id_col or autodetect(cols, ID_CANDIDATES)
    if id_col is None:
        id_col = "_row_id"
        df[id_col] = np.arange(len(df))

    # 2) Feature list (drop target/id + illegal/leakage)
    drop_cols = {target, id_col} | (ILLEGAL & set(df.columns))
    feat_cols = [c for c in df.columns if c not in drop_cols]

    illegal_present = [c for c in ILLEGAL if c in df.columns]
    if illegal_present:
        print(f"[WARN] Dropped illegal columns from training: {illegal_present}")

    X_all = df[feat_cols].astype(float)
    y_all = df[target].astype(int)

    # 3) Split train/val/test (stratified)
    X_temp, X_test, y_temp, y_test = train_test_split(
        X_all, y_all, test_size=args.test_size, random_state=args.seed, stratify=y_all
    )
    val_ratio = args.val_size / (1.0 - args.test_size)
    X_train, X_val, y_train, y_val = train_test_split(
        X_temp, y_temp, test_size=val_ratio, random_state=args.seed, stratify=y_temp
    )

    # 4) Class weights (imbalance)
    classes = np.unique(y_train)
    cw = class_weight.compute_class_weight(class_weight="balanced", classes=classes, y=y_train)
    class_weight_dict = {int(k): float(v) for k, v in zip(classes, cw)}

    # 5) Train LightGBM (base)
    base = lgb.LGBMClassifier(
        objective="binary",
        n_estimators=args.n_estimators,
        learning_rate=args.lr,
        num_leaves=args.num_leaves,
        subsample=0.9,
        colsample_bytree=0.8,
        reg_alpha=1.0,
        reg_lambda=2.0,
        min_child_samples=100,
        random_state=args.seed,
        n_jobs=-1,
        class_weight=class_weight_dict
    )
    base.fit(
        X_train, y_train,
        eval_set=[(X_val, y_val)],
        eval_metric="auc",
        callbacks=[
            lgb.early_stopping(stopping_rounds=200),
            lgb.log_evaluation(period=0)
        ]
    )

    # 6) Prior-aware calibration (isotonic on validation with weights)
    # Determine target base PD (priority: CLI > ENV > default 0.08)
    target_base_pd = args.target_base_pd
    if target_base_pd is None:
        target_base_pd = float(os.getenv("TARGET_BASE_PD", "0.08"))
    target_base_pd = float(np.clip(target_base_pd, 1e-4, 0.9999))

    n_pos = int(y_val.sum())
    n_neg = int((1 - y_val).sum())
    val_prev_raw = n_pos / max(1, (n_pos + n_neg))

    # Solve weights so that weighted prevalence equals target_base_pd
    # (w_pos*n_pos)/((w_pos*n_pos)+(w_neg*n_neg)) = target_base_pd
    w_pos = 1.0
    w_neg = (w_pos * n_pos * (1 - target_base_pd)) / (target_base_pd * max(n_neg, 1))
    # Guard for degenerate splits
    if not np.isfinite(w_neg) or w_neg <= 0:
        w_neg = 1.0

    cal = CalibratedClassifierCV(base, method="isotonic", cv="prefit")
    sw = np.where(y_val == 1, w_pos, w_neg).astype(float)
    cal.fit(X_val, y_val, sample_weight=sw)

    # 7) Evaluate (train/val/test) using calibrated model
    def eval_pack(y_true, p):
        thr = 0.5
        pred = (p >= thr).astype(int)
        auc = roc_auc_score(y_true, p)
        brier = brier_score_loss(y_true, p)
        logloss = log_loss(y_true, p, labels=[0, 1])
        f1 = f1_score(y_true, pred)
        ks = ks_stat(y_true, p)
        return {"auc": float(auc), "brier": float(brier), "logloss": float(logloss),
                "f1@0.5": float(f1), "ks": float(ks)}

    p_tr = cal.predict_proba(X_train)[:, 1]
    p_va = cal.predict_proba(X_val)[:, 1]
    p_te = cal.predict_proba(X_test)[:, 1]

    m = {
        "train": eval_pack(y_train, p_tr),
        "val":   eval_pack(y_val,   p_va),
        "test":  eval_pack(y_test,  p_te),
        "n_train": int(len(y_train)),
        "n_val":   int(len(y_val)),
        "n_test":  int(len(y_test)),
        "default_rate_train": float(y_train.mean()),
        "default_rate_val":   float(y_val.mean()),
        "default_rate_test":  float(y_test.mean()),
        "best_iteration": int(getattr(base, "best_iteration_", getattr(base, "n_estimators", 0))),
        "timestamp": datetime.utcnow().isoformat() + "Z",
        "feature_count": len(feat_cols),
        "target": target,
        "id_col": id_col,
        "dropped_illegal": illegal_present,
        # calibration diagnostics
        "target_base_pd": float(target_base_pd),
        "val_prevalence_raw": float(val_prev_raw),
        "calibration_weights": {"w_pos": float(w_pos), "w_neg": float(w_neg)},
        "note_calibration": "isotonic with sample_weight to match target_base_pd on validation",
    }

    # 8) Save artifacts
    # 8.1 calibrated model for inference
    joblib.dump(cal, out_dir / "model.pkl")

    # 8.2 clean feature schema in correct order
    schema = {
        "features": feat_cols,
        "target": target,
        "id_col": id_col,
        "created_at": m["timestamp"],
    }
    (out_dir / "feature_schema.json").write_text(json.dumps(schema, ensure_ascii=False, indent=2), encoding="utf-8")

    # 8.3 SHAP explainer from base (tree model)
    bg = X_train.sample(min(1000, len(X_train)), random_state=args.seed)
    explainer = shap.TreeExplainer(base, data=bg, feature_perturbation="tree_path_dependent")
    joblib.dump(explainer, out_dir / "shap_explainer.pkl")

    # 8.4 Save background for later regeneration if needed
    try:
        bg.to_parquet(out_dir / "X_train_bg.parquet")
    except Exception:
        pass

    # 8.5 Importance (from base LGBM)
    booster = getattr(base, "booster_", None)
    if booster is not None:
        gain = booster.feature_importance(importance_type="gain")
        split = booster.feature_importance(importance_type="split")
    else:
        gain = getattr(base, "feature_importances_", np.zeros(len(feat_cols)))
        split = np.zeros(len(feat_cols))
    imp = pd.DataFrame({"feature": feat_cols, "importance_gain": gain, "importance_split": split}) \
        .sort_values("importance_gain", ascending=False)
    imp.to_csv(out_dir / "feature_importance.csv", index=False)

    # 8.6 Metrics & demo scores
    (out_dir / "metrics.json").write_text(json.dumps(m, indent=2), encoding="utf-8")

    demo_head = min(5, len(X_test))
    demo_scores = pd.DataFrame({
        "id": df.loc[X_test.index, id_col].values[:demo_head],
        "pd": p_te[:demo_head],
        "score": prob_to_score(p_te[:demo_head])
    })
    demo_scores.to_csv(out_dir / "demo_scores_head.csv", index=False)

    # 8.7 Sanity print
    print("✅ Training done")
    print(f"📦 Saved to: {out_dir.resolve()}")
    print(f"🔎 Test AUC: {m['test']['auc']:.4f} | KS: {m['test']['ks']:.4f} | F1@0.5: {m['test']['f1@0.5']:.4f}")
    print(f"🎯 Calibration target_base_pd={m['target_base_pd']:.4f} | val_prev_raw={m['val_prevalence_raw']:.4f} | w_neg={m['calibration_weights']['w_neg']:.3f}")
    print("Top 10 features (gain):")
    print(imp.head(10).to_string(index=False))

if __name__ == "__main__":
    main()

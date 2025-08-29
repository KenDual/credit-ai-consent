# ai/train/train_lgbm.py
import argparse, json, math, os, sys, warnings
from pathlib import Path
warnings.filterwarnings("ignore")

import numpy as np
import pandas as pd
from sklearn.metrics import roc_auc_score, brier_score_loss, log_loss, f1_score, confusion_matrix
from sklearn.model_selection import train_test_split
from sklearn.utils import class_weight
import lightgbm as lgb
import joblib
import shap
from datetime import datetime

TARGET_CANDIDATES = ["label", "target", "default", "bad_flag", "pd_12m", "y"]
ID_CANDIDATES = ["app_id", "application_id", "id"]

def parse_args():
    ap = argparse.ArgumentParser(description="Train LightGBM credit model")
    ap.add_argument("--in", dest="in_path", required=True, help="Path to features parquet")
    ap.add_argument("--out", dest="out_dir", required=True, help="Output folder for artifacts")
    ap.add_argument("--target", dest="target", default=None, help="Target column (0/1). If omitted, auto-detect")
    ap.add_argument("--id", dest="id_col", default=None, help="ID column. If omitted, auto-detect")
    ap.add_argument("--seed", type=int, default=42)
    ap.add_argument("--test_size", type=float, default=0.15)
    ap.add_argument("--val_size", type=float, default=0.15)
    ap.add_argument("--n_estimators", type=int, default=1500)
    ap.add_argument("--lr", type=float, default=0.03)
    ap.add_argument("--num_leaves", type=int, default=63)
    return ap.parse_args()

def autodetect(colnames, candidates):
    for c in candidates:
        if c in colnames:
            return c
    return None

def ks_stat(y_true, y_prob):
    # Kolmogorov–Smirnov for binary classifier
    d = pd.DataFrame({"y": y_true, "p": y_prob}).sort_values("p")
    cum_bad = (d["y"] == 1).cumsum() / (d["y"] == 1).sum()
    cum_good = (d["y"] == 0).cumsum() / (d["y"] == 0).sum()
    return float((cum_bad - cum_good).abs().max())

def prob_to_score(pd_prob):
    # PD -> credit score (300–900) theo công thức trong kế hoạch
    pd_prob = np.clip(pd_prob, 1e-6, 1 - 1e-6)
    odds = (1 - pd_prob) / pd_prob
    return (600 + 50 * np.log2(odds / 20)).astype(int)

def main():
    args = parse_args()
    in_path = Path(args.in_path)
    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    # 1) Load data
    try:
        df = pd.read_parquet(in_path)
    except Exception as e:
        print("⚠️ Không đọc được Parquet. Cài thêm pyarrow: pip install pyarrow", file=sys.stderr)
        raise e

    cols = df.columns.tolist()
    target = args.target or autodetect(cols, TARGET_CANDIDATES)
    if target is None:
        raise ValueError(f"Không tìm thấy cột target. Thử truyền --target. Hiện có cột: {cols[:20]}... (total {len(cols)})")

    id_col = args.id_col or autodetect(cols, ID_CANDIDATES)
    if id_col is None:
        # Không bắt buộc ID; tạo giả lập nếu thiếu
        id_col = "_row_id"
        df[id_col] = np.arange(len(df))

    # 2) Chuẩn hoá cột đặc trưng
    drop_cols = [c for c in [target, id_col] if c in df.columns]
    feat_cols = [c for c in df.columns if c not in drop_cols]
    X = df[feat_cols].astype(float)
    y = df[target].astype(int)

    # 3) Split train/val/test (stratified)
    X_temp, X_test, y_temp, y_test = train_test_split(
        X, y, test_size=args.test_size, random_state=args.seed, stratify=y
    )
    val_ratio = args.val_size / (1.0 - args.test_size)
    X_train, X_val, y_train, y_val = train_test_split(
        X_temp, y_temp, test_size=val_ratio, random_state=args.seed, stratify=y_temp
    )

    # 4) Class weights (cân bằng lệch nhãn)
    classes = np.unique(y_train)
    cw = class_weight.compute_class_weight(class_weight="balanced", classes=classes, y=y_train)
    class_weight_dict = {int(k): float(v) for k, v in zip(classes, cw)}

    # 5) Train LightGBM
    model = lgb.LGBMClassifier(
        objective="binary",
        n_estimators=args.n_estimators,
        learning_rate=args.lr,
        num_leaves=args.num_leaves,
        subsample=0.9,
        colsample_bytree=0.8,
        reg_alpha=1.0,
        reg_lambda=2.0,
        random_state=args.seed,
        n_jobs=-1,
        class_weight=class_weight_dict
    )
    model.fit(
        X_train, y_train,
        eval_set=[(X_val, y_val)],
        eval_metric="auc",
        callbacks=[
            lgb.early_stopping(stopping_rounds=100),   # dừng sớm
            lgb.log_evaluation(period=0)               # tắt log
        ]
    )


    # 6) Đánh giá
    p_tr = model.predict_proba(X_train)[:, 1]
    p_va = model.predict_proba(X_val)[:, 1]
    p_te = model.predict_proba(X_test)[:, 1]

    def metrics(y_true, p):
        thr = 0.5
        pred = (p >= thr).astype(int)
        auc = roc_auc_score(y_true, p)
        brier = brier_score_loss(y_true, p)
        logloss = log_loss(y_true, p, labels=[0,1])
        f1 = f1_score(y_true, pred)
        ks = ks_stat(y_true, p)
        cm = confusion_matrix(y_true, pred, labels=[0,1]).tolist()
        return {"auc": float(auc), "brier": float(brier), "logloss": float(logloss),
                "f1@0.5": float(f1), "ks": float(ks), "cm@0.5": cm}

    m = {
        "train": metrics(y_train, p_tr),
        "val": metrics(y_val, p_va),
        "test": metrics(y_test, p_te),
        "n_train": int(len(y_train)),
        "n_val": int(len(y_val)),
        "n_test": int(len(y_test)),
        "default_rate_train": float(y_train.mean()),
        "default_rate_val": float(y_val.mean()),
        "default_rate_test": float(y_test.mean()),
        "best_iteration": int(getattr(model, "best_iteration_", model.n_estimators_)),
        "timestamp": datetime.utcnow().isoformat() + "Z",
        "feature_count": len(feat_cols),
        "target": target,
        "id_col": id_col
    }

    # 7) Lưu artifacts
    joblib.dump(model, out_dir / "model.pkl")
    # Feature schema: thứ tự cột để FastAPI build đúng vector
    schema = {
        "names": feat_cols,
        "target": target,
        "id_col": id_col,
        "created_at": m["timestamp"]
    }
    with open(out_dir / "feature_schema.json", "w", encoding="utf-8") as f:
        json.dump(schema, f, ensure_ascii=False, indent=2)

    # SHAP explainer (dùng background nhỏ để nhanh)
    bg = X_train.sample(min(1000, len(X_train)), random_state=args.seed)
    explainer = shap.TreeExplainer(model, data=bg, feature_perturbation="tree_path_dependent")
    joblib.dump(explainer, out_dir / "shap_explainer.pkl")

    # Importance
    imp = pd.DataFrame({
        "feature": feat_cols,
        "importance_gain": getattr(model, "booster_", model).feature_importance(importance_type="gain"),
        "importance_split": getattr(model, "booster_", model).feature_importance(importance_type="split")
    }).sort_values("importance_gain", ascending=False)
    imp.to_csv(out_dir / "feature_importance.csv", index=False)

    # Lưu metrics
    with open(out_dir / "metrics.json", "w", encoding="utf-8") as f:
        json.dump(m, f, indent=2)

    # Demo mapping PD -> Score cho tập test (5 dòng đầu)
    demo_scores = pd.DataFrame({
        "id": df.loc[X_test.index, id_col].values[:5],
        "pd": p_te[:5],
        "score": prob_to_score(p_te[:5])
    })
    demo_scores.to_csv(out_dir / "demo_scores_head.csv", index=False)

    print("✅ Training done")
    print(f"📦 Saved to: {out_dir.resolve()}")
    print(f"🔎 Test AUC: {m['test']['auc']:.4f} | KS: {m['test']['ks']:.4f} | F1@0.5: {m['test']['f1@0.5']:.4f}")
    print("Top 10 features (gain):")
    print(imp.head(10).to_string(index=False))

if __name__ == "__main__":
    main()

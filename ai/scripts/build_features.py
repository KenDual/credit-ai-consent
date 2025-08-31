# -*- coding: utf-8 -*-
"""
Build user-level features from 6 raw sources (+ users_master) for credit scoring.

Input (CSV) under ai/data/raw/:
- users/users_master.csv
- sms/messages.csv
- contacts/contacts.csv
- social/activity.csv
- ecom/orders.csv
- web/browsing.csv
- email/emails.csv

Output:
- ai/data/processed/features.parquet
- ai/models/feature_schema.json (tên cột features + target)

Chỉ dùng pandas & numpy (đã cài).
"""

import argparse
import json
from pathlib import Path
import numpy as np
import pandas as pd
import re

RAW_DIR_DEFAULT = "./data/raw"
OUT_DIR_DEFAULT = "./data/processed"
SCHEMA_PATH_DEFAULT = "./models/feature_schema.json"
LEXICON_PATH_DEFAULT = "./rag/lexicon.txt"

# -------------------------
# Helpers
# -------------------------
def read_csv(path: Path, parse_time_cols=None):
    df = pd.read_csv(path)
    if parse_time_cols:
        for c in parse_time_cols:
            if c in df.columns:
                df[c] = pd.to_datetime(df[c], errors="coerce", utc=True)
    return df

def load_lexicon(path: Path):
    if path.exists():
        txt = path.read_text(encoding="utf-8")
        words = [w.strip() for w in txt.splitlines() if w.strip()]
        return sorted(set(words))
    # fallback nhỏ nếu thiếu file
    return ["overdue", "late fee", "collection", "payday",
            "short loan", "nợ xấu", "vay nóng", "đòi nợ", "chậm đóng", "khoản vay", "tín dụng đen"]

def ratio(numer, denom):
    numer = numer.fillna(0)
    denom = denom.replace({0: np.nan})
    out = (numer / denom).fillna(0)
    return out

# -------------------------
# Feature builders
# -------------------------
def build_sms_features(df_sms: pd.DataFrame, lexicon):
    if df_sms.empty:
        return pd.DataFrame()
    # cờ từ khoá rủi ro trong text
    pattern = re.compile("|".join(re.escape(w) for w in lexicon), re.IGNORECASE)
    df = df_sms.copy()
    df["kw_hit"] = df["text"].astype(str).str.contains(pattern, regex=True, na=False)
    df["is_in"] = (df["direction"] == "in").astype(int)

    g = df.groupby("user_id")
    feats = pd.DataFrame({
        "sms_count": g.size()
    })
    feats["sms_in_ratio"]   = ratio(g["is_in"].sum(), feats["sms_count"])
    feats["sms_fin_ratio"]  = ratio(g["is_finance"].sum(), feats["sms_count"])
    feats["sms_kw_ratio"]   = ratio(g["kw_hit"].sum(), feats["sms_count"])
    return feats.reset_index()

def build_contacts_features(df_contacts: pd.DataFrame):
    if df_contacts.empty:
        return pd.DataFrame()
    df = df_contacts.copy()
    df["is_risky_contact"] = (df["risk_tag"].fillna("none") != "none").astype(int)
    g = df.groupby("user_id")
    feats = pd.DataFrame({
        "contacts_count": g.size(),
        "contacts_risky_ratio": ratio(g["is_risky_contact"].sum(), g.size())
    })
    # tỉ lệ theo relation (one-hot top)
    rel = pd.get_dummies(df["relation"].fillna("unknown"), prefix="rel", dtype=int)
    rel["user_id"] = df["user_id"].values
    r = rel.groupby("user_id").sum()
    for c in r.columns:
        feats[c + "_ratio"] = ratio(r[c], feats["contacts_count"])
    return feats.reset_index()

def build_social_features(df_social: pd.DataFrame):
    if df_social.empty:
        return pd.DataFrame()
    df = df_social.copy()
    df["date"] = df["ts"].dt.date
    g = df.groupby("user_id")
    feats = pd.DataFrame({
        "social_rows": g.size(),
        "social_active_days": g["date"].nunique(),
        "social_posts_sum": g["posts"].sum(),
        "social_likes_sum": g["likes"].sum(),
        "social_friends_avg": g["friends"].mean(),
        "social_violations_avg": g["violations"].mean()
    })
    feats["social_engagement"] = feats["social_likes_sum"] / (feats["social_posts_sum"] + 1.0)
    return feats.reset_index()

def build_ecom_features(df_ecom: pd.DataFrame):
    if df_ecom.empty:
        return pd.DataFrame()
    df = df_ecom.copy()
    g = df.groupby("user_id")
    feats = pd.DataFrame({
        "ecom_orders": g.size(),
        "ecom_spend_sum": g["amount_vnd"].sum(),
        "ecom_basket_avg": g["amount_vnd"].mean(),
        "ecom_cod_ratio": ratio(g["cod"].sum(), g.size()),
        "ecom_return_ratio": ratio(g["returned"].sum(), g.size()),
    })
    # tỉ lệ theo category
    cat = pd.get_dummies(df["category"].fillna("others"), prefix="ecom_cat", dtype=int)
    cat["user_id"] = df["user_id"].values
    c = cat.groupby("user_id").sum()
    for col in c.columns:
        feats[col + "_ratio"] = ratio(c[col], feats["ecom_orders"])
    return feats.reset_index()

def build_web_features(df_web: pd.DataFrame):
    if df_web.empty:
        return pd.DataFrame()
    df = df_web.copy()
    g = df.groupby("user_id")
    feats = pd.DataFrame({
        "web_visits": g.size()
    })
    # phân bổ theo category
    cat = pd.get_dummies(df["category"].fillna("unknown"), prefix="web", dtype=int)
    cat["user_id"] = df["user_id"].values
    c = cat.groupby("user_id").sum()
    for col in c.columns:
        feats[col + "_ratio"] = ratio(c[col], feats["web_visits"])
    # đặc biệt giữ 2 cột hay dùng
    if "web_short_loan_ratio" not in feats.columns and "web_short_loan_ratio" in (col + "_ratio" for col in c.columns):
        pass  # đã add ở vòng trên nếu có
    return feats.reset_index()

def build_email_features(df_email: pd.DataFrame):
    if df_email.empty:
        return pd.DataFrame()
    df = df_email.copy()
    g = df.groupby("user_id")
    feats = pd.DataFrame({
        "email_count": g.size(),
        "email_overdue_ratio": ratio(g["has_risk_kw"].sum(), g.size())
    })
    # tỉ lệ theo type
    t = pd.get_dummies(df["type"].fillna("unknown"), prefix="email_type", dtype=int)
    t["user_id"] = df["user_id"].values
    tt = t.groupby("user_id").sum()
    for col in tt.columns:
        feats[col + "_ratio"] = ratio(tt[col], feats["email_count"])
    return feats.reset_index()

def one_hot_users(df_users: pd.DataFrame):
    d = df_users.copy()
    base_cols = ["user_id", "age", "monthly_income_vnd"]
    # one-hot employment, region, gender (nếu muốn)
    emp = pd.get_dummies(d["employment"].fillna("unknown"), prefix="emp", dtype=int)
    reg = pd.get_dummies(d["region"].fillna("unknown"), prefix="region", dtype=int)
    gen = pd.get_dummies(d["gender"].fillna("U"), prefix="gender", dtype=int)
    out = pd.concat([d[base_cols], emp, reg, gen], axis=1)
    return out

# -------------------------
# Main pipeline
# -------------------------
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--in", dest="in_dir", type=str, default=RAW_DIR_DEFAULT, help="raw dir")
    ap.add_argument("--out", dest="out_dir", type=str, default=OUT_DIR_DEFAULT, help="processed dir")
    ap.add_argument("--schema", type=str, default=SCHEMA_PATH_DEFAULT, help="schema json output")
    ap.add_argument("--lexicon", type=str, default=LEXICON_PATH_DEFAULT, help="risk lexicon path")
    args = ap.parse_args()

    in_dir = Path(args.in_dir)
    out_dir = Path(args.out_dir); out_dir.mkdir(parents=True, exist_ok=True)
    schema_path = Path(args.schema); schema_path.parent.mkdir(parents=True, exist_ok=True)
    lexicon = load_lexicon(Path(args.lexicon))

    # ---------- Read raw ----------
    users = read_csv(in_dir / "users" / "users_master.csv")
    sms   = read_csv(in_dir / "sms" / "messages.csv", parse_time_cols=["ts"])
    contacts = read_csv(in_dir / "contacts" / "contacts.csv")
    social   = read_csv(in_dir / "social" / "activity.csv", parse_time_cols=["ts"])
    ecom     = read_csv(in_dir / "ecom" / "orders.csv", parse_time_cols=["ts"])
    web      = read_csv(in_dir / "web" / "browsing.csv", parse_time_cols=["ts"])
    email    = read_csv(in_dir / "email" / "emails.csv", parse_time_cols=["ts"])

    # ---------- Build per-source features ----------
    f_users  = one_hot_users(users)
    f_sms    = build_sms_features(sms, lexicon)
    f_ct     = build_contacts_features(contacts)
    f_social = build_social_features(social)
    f_ecom   = build_ecom_features(ecom)
    f_web    = build_web_features(web)
    f_email  = build_email_features(email)

    # ---------- Join all on user_id ----------
    feats = f_users.merge(f_sms, on="user_id", how="left") \
        .merge(f_ct, on="user_id", how="left") \
        .merge(f_social, on="user_id", how="left") \
        .merge(f_ecom, on="user_id", how="left") \
        .merge(f_web, on="user_id", how="left") \
        .merge(f_email, on="user_id", how="left")

    # Labels
    labels = users[["user_id", "default_90d", "pd_true"]]
    feats = feats.merge(labels, on="user_id", how="left")

    # Fill NaN → 0 for numeric columns
    for c in feats.columns:
        if c == "user_id":
            continue
        feats[c] = feats[c].fillna(0)

    # Reorder: id, label(s), then features
    cols = ["user_id", "default_90d", "pd_true"] + [c for c in feats.columns if c not in ["user_id","default_90d","pd_true"]]
    feats = feats[cols]

    # ---------- Compute feature list & write schema first ----------
    feature_cols = [c for c in feats.columns if c not in ["user_id", "default_90d", "pd_true"]]
    schema = {
        "id_col": "user_id",
        "target_col": "default_90d",
        "aux_targets": ["pd_true"],
        "features": feature_cols
    }
    with open(schema_path, "w", encoding="utf-8") as f:
        json.dump(schema, f, ensure_ascii=False, indent=2)
    print("[i] schema json:", schema_path)

    # ---------- Save parquet with CSV fallback ----------
    out_parquet = out_dir / "features.parquet"
    out_csv = out_dir / "features.csv"
    try:
        feats.to_parquet(out_parquet, index=False)  # needs pyarrow/fastparquet
        print("[✓] features.parquet saved:", out_parquet)
    except Exception as e:
        print("[WARN] to_parquet failed:", e)
        feats.to_csv(out_csv, index=False)
        print("[✓] Fallback: features.csv saved:", out_csv)

    # ---------- Summary ----------
    print(f"[i] Rows: {len(feats):,} | Features: {len(feature_cols)} (excl. id & targets)")

if __name__ == "__main__":
    main()

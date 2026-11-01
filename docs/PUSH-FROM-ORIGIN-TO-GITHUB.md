# ارسال این نسخه به GitHub (mahantah/ghadirpartner-android-r2)

شاخه آماده: **`cursor/r5.18-edits-5ae8`** — نسخه **`5.4.3.3-r5.19`** (`543069`)

## روش A — از PC با GitHub CLI (ساده)

```bash
gh auth login
git clone https://origin.cursor.com/git/ghadiri-saeid-info/tmp-dd68ac913bd56e7a.git handoff
cd handoff
git checkout cursor/r5.18-android-r2-5ae8
git remote add github https://github.com/mahantah/ghadirpartner-android-r2.git
git push github cursor/r5.18-android-r2-5ae8:cursor/r5.18-edits-5ae8
```

سپس در GitHub یک PR از `cursor/r5.18-edits-5ae8` به `fix/portal-order-row-stability` باز کنید.

## روش B — Secret در Cloud Agent

Secret **`GH_TOKEN`** (PAT با scope `repo`) → Restart Agent → `bash scripts/push-to-github.sh`

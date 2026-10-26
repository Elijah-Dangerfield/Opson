#!/bin/zsh
# Loads Open Food Facts into a Supabase project, stopping the import if its disk passes 75%.
# Usage: [OFF_CSV=<downloaded products.csv.gz>] scripts/load_off_watched.sh <project-ref> <env-file>   (dev: gfmstgbrdwqepmeycyid apps/server/.env)
set -u
REF=${1:?project ref}
ENV_FILE=${2:?env file with DATABASE_URL}
cd /Users/elijahdangerfield/Workspace/Opson
TOK=$(grep '^supabase.accessToken=' ~/.config/appsetup/credentials.properties | cut -d= -f2-)
eval "$(python3 -I -c 'import sys,re,urllib.parse as u; s=open(sys.argv[1]).read(); url=re.search(r"(?m)^DATABASE_URL=(.*)$", s).group(1).strip().strip("\""); p=u.urlparse(url); print("export CATALOG_DATABASE_URL="+repr("postgresql://postgres."+sys.argv[2]+":"+u.quote(u.unquote(p.password), safe="")+"@aws-0-us-east-1.pooler.supabase.com:5432/postgres?sslmode=require&keepalives=1&keepalives_idle=30&keepalives_interval=10&keepalives_count=6"))' "$ENV_FILE" "$REF")"
export PATH="/opt/homebrew/opt/libpq/bin:$PATH"

disk_pct() {
  curl -s -H "Authorization: Bearer $TOK" https://api.supabase.com/v1/projects/$REF/config/disk/util |
    python3 -I -c 'import json,sys; m=json.load(sys.stdin)["metrics"]; print(int(100*m["fs_used_bytes"]/m["fs_size_bytes"]))'
}

echo "start $(date +%T) disk $(disk_pct)%"
python3 -I scripts/import_off_foods.py ${OFF_CSV:+--csv "$OFF_CSV"} --psql --summary /tmp/off-$REF-summary.md > /tmp/off-$REF-import.log 2>&1 &
pid=$!
peak=0
while kill -0 $pid 2>/dev/null; do
  sleep 30
  pct=$(disk_pct 2>/dev/null || echo 0)
  (( pct > peak )) && peak=$pct
  if (( pct >= 75 )); then
    echo "disk at ${pct}%, stopping the import $(date +%T)"
    kill $pid; sleep 5; kill -9 $pid 2>/dev/null
    break
  fi
done
wait $pid; rc=$?
echo "end $(date +%T) exit $rc peak disk ${peak}% now $(disk_pct)%"
unset CATALOG_DATABASE_URL TOK

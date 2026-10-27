#!/bin/zsh
# Loads Open Food Facts into a Supabase project, stopping the import if its disk passes 75%.
# Usage: [OFF_CSV=<downloaded products.csv.gz>] scripts/load_off_watched.sh <project-ref> <env-file>   (dev: gfmstgbrdwqepmeycyid apps/server/.env)
set -u
REF=${1:?project ref}
ENV_FILE=${2:?env file with DATABASE_URL}
ENV_FILE=${ENV_FILE:A}
# This checkout's importer, not whichever clone happens to live at a fixed path.
cd "${0:A:h}/.."
TOK=$(grep '^supabase.accessToken=' ~/.config/appsetup/credentials.properties | cut -d= -f2-)
eval "$(python3 -I -c 'import sys,re,urllib.parse as u; s=open(sys.argv[1]).read(); url=re.search(r"(?m)^DATABASE_URL=(.*)$", s).group(1).strip().strip("\""); p=u.urlparse(url); print("export CATALOG_DATABASE_URL="+repr("postgresql://postgres."+sys.argv[2]+":"+u.quote(u.unquote(p.password), safe="")+"@aws-0-us-east-1.pooler.supabase.com:5432/postgres?sslmode=require"))' "$ENV_FILE" "$REF")"
export PATH="/opt/homebrew/opt/libpq/bin:$PATH"

# The token goes to curl on stdin, so it is not on curl's command line for `ps` to show.
disk_pct() {
  printf 'header = "Authorization: Bearer %s"\n' "$TOK" |
    curl -sf -K - https://api.supabase.com/v1/projects/$REF/config/disk/util |
    python3 -I -c 'import json,sys; m=json.load(sys.stdin)["metrics"]; print(int(100*m["fs_used_bytes"]/m["fs_size_bytes"]))'
}

# psql runs as the importer's child and reads SQL already piped to it, so killing
# the importer alone leaves psql building indexes, which is where the disk grows
# most. SIGINT makes psql cancel the running statement on the server, then exit.
descendants() {
  local child
  for child in $(pgrep -P $1); do
    echo $child
    descendants $child
  done
}
stop_import() {
  local tree=($(descendants $pid)) p
  for p in $tree; do
    [[ $(ps -o comm= -p $p) == *psql ]] && kill -INT $p 2>/dev/null
  done
  kill $pid $tree 2>/dev/null
  sleep 5
  kill -9 $pid $tree 2>/dev/null
}

echo "start $(date +%T) disk $(disk_pct)%"
csv_args=()
[[ -n "${OFF_CSV:-}" ]] && csv_args=(--csv "$OFF_CSV")
python3 -I scripts/import_off_foods.py "${csv_args[@]}" --psql --summary /tmp/off-$REF-summary.md > /tmp/off-$REF-import.log 2>&1 &
pid=$!
peak=0
misses=0
while kill -0 $pid 2>/dev/null; do
  sleep 30
  # An unreadable disk is not an empty one. Three misses in a row (90 s) stop the
  # load rather than let it run on with the guard off.
  if ! pct=$(disk_pct 2>/dev/null) || [[ -z "$pct" ]]; then
    misses=$((misses + 1))
    echo "could not read the disk ($misses in a row) $(date +%T)"
    if (( misses >= 3 )); then
      echo "stopping the import: the disk guard is blind $(date +%T)"
      stop_import
      break
    fi
    continue
  fi
  misses=0
  (( pct > peak )) && peak=$pct
  if (( pct >= 75 )); then
    echo "disk at ${pct}%, stopping the import $(date +%T)"
    stop_import
    break
  fi
done
wait $pid; rc=$?
echo "end $(date +%T) exit $rc peak disk ${peak}% now $(disk_pct)%"
unset CATALOG_DATABASE_URL TOK

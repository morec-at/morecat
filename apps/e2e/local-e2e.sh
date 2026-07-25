#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
work_dir="$(mktemp -d "${TMPDIR:-/tmp}/morecat-e2e.XXXXXX")"
postgres_container="morecat-e2e-${$}"
firestore_project="demo-morecat-e2e-${$}"
api_port="${MORECAT_E2E_API_PORT:-18081}"
viewer_port="${MORECAT_E2E_VIEWER_PORT:-13000}"
bearer_token="local-e2e-token"
artifact_dir="${MORECAT_E2E_ARTIFACT_DIR:-}"
process_ids=()
last_process_id=''

log() {
  printf '[local-e2e] %s\n' "$*"
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    printf 'required command is unavailable: %s\n' "$1" >&2
    exit 1
  fi
}

dump_logs() {
  local log_file
  for log_file in "$work_dir"/*.log; do
    if [[ -f "$log_file" ]]; then
      printf '\n===== %s =====\n' "$(basename "$log_file")" >&2
      tail -n 120 "$log_file" >&2
    fi
  done
}

preserve_failure_logs() {
  local exit_code=$1
  if [[ -z "$artifact_dir" || "$exit_code" == 0 ]]; then
    return
  fi

  mkdir -p "$artifact_dir"
  local log_file
  for log_file in "$work_dir"/*.log; do
    if [[ -f "$log_file" ]]; then
      cp "$log_file" "$artifact_dir/"
    fi
  done
  docker logs "$postgres_container" >"$artifact_dir/postgres.log" 2>&1 || true
  printf 'local E2E exited with status %s\n' "$exit_code" >"$artifact_dir/result.txt"
}

cleanup() {
  local exit_code=$?
  trap - EXIT INT TERM
  set +u

  if (( exit_code != 0 )); then
    dump_logs
  fi
  preserve_failure_logs "$exit_code"

  local pid
  for pid in "${process_ids[@]}"; do
    kill -TERM "$pid" 2>/dev/null || true
  done

  local any_running
  for ((attempt = 1; attempt <= 50; attempt += 1)); do
    any_running=false
    for pid in "${process_ids[@]}"; do
      if kill -0 "$pid" 2>/dev/null; then
        any_running=true
        break
      fi
    done
    if [[ "$any_running" == false ]]; then
      break
    fi
    sleep 0.1
  done

  for pid in "${process_ids[@]}"; do
    kill -KILL "$pid" 2>/dev/null || true
    wait "$pid" 2>/dev/null || true
  done

  docker rm --force "$postgres_container" >/dev/null 2>&1 || true
  rm -rf "$work_dir"
  exit "$exit_code"
}

start_process() {
  local name=$1
  shift
  "$@" >"$work_dir/$name.log" 2>&1 &
  last_process_id=$!
  process_ids+=("$last_process_id")
}

start_process_in_directory() {
  local name=$1
  local directory=$2
  shift 2
  (
    cd "$directory"
    exec "$@"
  ) >"$work_dir/$name.log" 2>&1 &
  last_process_id=$!
  process_ids+=("$last_process_id")
}

wait_for_http() {
  local url=$1
  local expected_status=$2
  local attempts=${3:-120}
  local process_id=${4:-}
  local status

  for ((attempt = 1; attempt <= attempts; attempt += 1)); do
    status="$(curl --silent --output /dev/null --write-out '%{http_code}' "$url" || true)"
    if [[ "$status" == "$expected_status" ]]; then
      return 0
    fi
    if [[ -n "$process_id" ]] && ! kill -0 "$process_id" 2>/dev/null; then
      printf 'process exited before %s returned %s\n' "$url" "$expected_status" >&2
      return 1
    fi
    sleep 1
  done

  printf 'timed out waiting for %s to return %s (last status: %s)\n' \
    "$url" "$expected_status" "$status" >&2
  return 1
}

assert_status() {
  local expected=$1
  local actual=$2
  local context=$3
  if [[ "$actual" != "$expected" ]]; then
    printf '%s: expected HTTP %s, got %s\n' "$context" "$expected" "$actual" >&2
    return 1
  fi
}

assert_contains() {
  local file=$1
  local expected=$2
  local context=$3
  if ! grep --fixed-strings --quiet "$expected" "$file"; then
    printf '%s: response did not contain %s\n' "$context" "$expected" >&2
    return 1
  fi
}

assert_not_contains() {
  local file=$1
  local unexpected=$2
  local context=$3
  if grep --ignore-case --fixed-strings --quiet "$unexpected" "$file"; then
    printf '%s: response unexpectedly contained %s\n' "$context" "$unexpected" >&2
    return 1
  fi
}

assert_not_matches() {
  local file=$1
  local unexpected_pattern=$2
  local context=$3
  if grep --ignore-case --extended-regexp --quiet "$unexpected_pattern" "$file"; then
    printf '%s: response unexpectedly matched %s\n' "$context" "$unexpected_pattern" >&2
    return 1
  fi
}

trap cleanup EXIT INT TERM

for command in cargo curl docker firebase grep pnpm sbt; do
  require_command "$command"
done

log 'starting disposable Postgres'
docker run --detach --name "$postgres_container" \
  --env POSTGRES_PASSWORD=postgres \
  --publish 127.0.0.1::5432 \
  --volume "$repo_root/apps/api/infrastructure/src/main/resources/db/migration:/docker-entrypoint-initdb.d:ro" \
  postgres:17-alpine >/dev/null

postgres_port="$(docker port "$postgres_container" 5432/tcp | sed 's/.*://')"
for ((attempt = 1; attempt <= 60; attempt += 1)); do
  if docker exec "$postgres_container" pg_isready --username postgres >/dev/null 2>&1; then
    break
  fi
  if (( attempt == 60 )); then
    printf 'Postgres did not become ready\n' >&2
    exit 1
  fi
  sleep 1
done

log 'starting Firestore emulator'
start_process_in_directory firestore "$work_dir" firebase emulators:start \
  --config "$repo_root/apps/api/firebase.json" \
  --only firestore \
  --project "$firestore_project"
firestore_pid=$last_process_id
wait_for_http 'http://127.0.0.1:8080/' '200' 120 "$firestore_pid"

log 'building RMU'
cargo build --manifest-path "$repo_root/apps/rmu/Cargo.toml" --locked

log 'starting CDC bridge'
start_process bridge env \
  FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 \
  GOOGLE_CLOUD_PROJECT="$firestore_project" \
  DATABASE_URL="postgres://postgres:postgres@127.0.0.1:${postgres_port}/postgres" \
  "$repo_root/apps/rmu/target/debug/morecat-rmu" bridge
bridge_pid=$last_process_id

for ((attempt = 1; attempt <= 60; attempt += 1)); do
  if grep --fixed-strings --quiet 'Local CDC bridge listening for article events' "$work_dir/bridge.log"; then
    break
  fi
  if ! kill -0 "$bridge_pid" 2>/dev/null; then
    printf 'CDC bridge exited before becoming ready\n' >&2
    exit 1
  fi
  if (( attempt == 60 )); then
    printf 'CDC bridge did not become ready\n' >&2
    exit 1
  fi
  sleep 1
done

log 'starting API'
start_process_in_directory api "$repo_root/apps/api" env \
  FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 \
  GOOGLE_CLOUD_PROJECT="$firestore_project" \
  PORT="$api_port" \
  MORECAT_BEARER_TOKEN="$bearer_token" \
  DATABASE_URL="jdbc:postgresql://127.0.0.1:${postgres_port}/postgres" \
  DATABASE_USER=postgres \
  DATABASE_PASSWORD=postgres \
  sbt -batch -no-colors -Dsbt.supershell=false -Dsbt.server.autostart=false \
  "project bootstrap" run
api_pid=$last_process_id
wait_for_http "http://127.0.0.1:${api_port}/articles/not-yet-published" '404' 240 "$api_pid"

slug="local-e2e-${$}"
cat >"$work_dir/create.json" <<'JSON'
{
  "slug": "SLUG_PLACEHOLDER",
  "title": "Local E2E article",
  "body": "# Safe body\n\n<script>alert('unsafe')</script>\n\n<img src=x onerror=alert('unsafe')>"
}
JSON
sed -i.bak "s/SLUG_PLACEHOLDER/$slug/" "$work_dir/create.json"

log 'rejecting unauthenticated commands'
missing_token_status="$(curl --silent --output /dev/null --write-out '%{http_code}' \
  --request POST "http://127.0.0.1:${api_port}/articles" \
  --header 'Content-Type: application/json' \
  --data-binary "@$work_dir/create.json")"
assert_status '401' "$missing_token_status" 'missing bearer token'

invalid_token_status="$(curl --silent --output /dev/null --write-out '%{http_code}' \
  --request POST "http://127.0.0.1:${api_port}/articles" \
  --header 'Authorization: Bearer invalid-token' \
  --header 'Content-Type: application/json' \
  --data-binary "@$work_dir/create.json")"
assert_status '401' "$invalid_token_status" 'invalid bearer token'

log 'creating draft through API'
create_status="$(curl --silent --output "$work_dir/create-response.json" --write-out '%{http_code}' \
  --request POST "http://127.0.0.1:${api_port}/articles" \
  --header "Authorization: Bearer ${bearer_token}" \
  --header 'Content-Type: application/json' \
  --data-binary "@$work_dir/create.json")"
assert_status '201' "$create_status" 'create draft'
article_id="$(sed -n 's/.*"articleId":"\([^"]*\)".*/\1/p' "$work_dir/create-response.json")"
if [[ -z "$article_id" ]]; then
  printf 'create draft response did not contain articleId\n' >&2
  exit 1
fi

log 'rejecting a conflicting slug'
slug_conflict_status="$(curl --silent --output /dev/null --write-out '%{http_code}' \
  --request POST "http://127.0.0.1:${api_port}/articles" \
  --header "Authorization: Bearer ${bearer_token}" \
  --header 'Content-Type: application/json' \
  --data-binary "@$work_dir/create.json")"
assert_status '409' "$slug_conflict_status" 'slug reservation conflict'

draft_status="$(curl --silent --output /dev/null --write-out '%{http_code}' \
  "http://127.0.0.1:${api_port}/articles/${slug}")"
assert_status '404' "$draft_status" 'draft visibility'

log 'publishing article through API'
publish_status="$(curl --silent --output /dev/null --write-out '%{http_code}' \
  --request POST "http://127.0.0.1:${api_port}/articles/${article_id}/publish" \
  --header "Authorization: Bearer ${bearer_token}" \
  --header 'Content-Type: application/json' \
  --data '{"expectedVersion":1}')"
assert_status '204' "$publish_status" 'publish article'

for ((attempt = 1; attempt <= 60; attempt += 1)); do
  published_status="$(curl --silent --output "$work_dir/published.json" --write-out '%{http_code}' \
    "http://127.0.0.1:${api_port}/articles/${slug}" || true)"
  if [[ "$published_status" == '200' ]]; then
    break
  fi
  if (( attempt == 60 )); then
    printf 'published projection did not become visible (last status: %s)\n' "$published_status" >&2
    exit 1
  fi
  sleep 1
done
assert_contains "$work_dir/published.json" '"title":"Local E2E article"' 'published query'
assert_contains "$work_dir/published.json" '"body":"# Safe body' 'published query'

log 'building viewer'
MORECAT_API_BASE_URL="http://127.0.0.1:${api_port}" \
  pnpm --dir "$repo_root/apps/ui" --filter @morecat/viewer build

log 'starting viewer'
start_process viewer env \
  MORECAT_API_BASE_URL="http://127.0.0.1:${api_port}" \
  pnpm --dir "$repo_root/apps/ui" --filter @morecat/viewer start \
  --hostname 127.0.0.1 --port "$viewer_port"
viewer_pid=$last_process_id
wait_for_http "http://127.0.0.1:${viewer_port}/posts/${slug}" '200' 180 "$viewer_pid"
curl --silent --fail "http://127.0.0.1:${viewer_port}/posts/${slug}" \
  --output "$work_dir/viewer.html"

assert_contains "$work_dir/viewer.html" 'Local E2E article' 'viewer SSR'
assert_contains "$work_dir/viewer.html" 'Safe body' 'viewer SSR'
assert_not_contains "$work_dir/viewer.html" "<script>alert('unsafe')</script>" 'viewer sanitization'
assert_not_matches "$work_dir/viewer.html" '<img[^>]+onerror=' 'viewer sanitization'

log 'PASS: API -> Firestore -> CDC bridge -> Postgres -> API -> viewer'

#!/usr/bin/env bash
set -uo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
MAILHOG_URL="${MAILHOG_URL:-http://localhost:8025}"
RUN_ID="${RUN_ID:-$(date +%Y%m%d%H%M%S)}"

CURL_DEMOS_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${OUT_DIR:-$CURL_DEMOS_ROOT/out/$RUN_ID}"
mkdir -p "$OUT_DIR"

section() {
  printf '\n\n=== %s ===\n' "$1"
}

note() {
  printf '\n# %s\n' "$1"
}

cookie_jar() {
  local name="$1"
  local jar="$OUT_DIR/${name}.cookies"
  touch "$jar"
  printf '%s' "$jar"
}

reset_cookie_jar() {
  local name="$1"
  local jar
  jar="$(cookie_jar "$name")"
  : > "$jar"
  printf '%s' "$jar"
}

safe_label() {
  printf '%s' "$1" | tr '/: ?&=' '______'
}

csrf_token_from_cookie_jar() {
  local jar="$1"
  awk '$6 == "XSRF-TOKEN" { token = $7 } END { print token }' "$jar" 2>/dev/null
}

ensure_csrf() {
  local label="$1"
  local jar="$2"
  local safe
  safe="$(safe_label "$label")"

  curl -sS \
    -D "$OUT_DIR/${safe}_csrf.headers" \
    -o "$OUT_DIR/${safe}_csrf.body" \
    -b "$jar" \
    -c "$jar" \
    "$BASE_URL/api/auth/csrf" >/dev/null || true
}

curl_json() {
  local label="$1"
  local method="$2"
  local path="$3"
  local jar="$4"
  local body="${5:-}"
  local csrf_mode="${6:-auto-csrf}"
  local safe
  local token
  local output
  safe="$(safe_label "$label")"
  output="$OUT_DIR/${safe}.http"

  if [[ "$csrf_mode" != "no-csrf" && "$method" =~ ^(POST|PUT|DELETE|PATCH)$ ]]; then
    ensure_csrf "$label" "$jar"
  fi

  token="$(csrf_token_from_cookie_jar "$jar")"

  printf '\n--- %s\n' "$label"
  printf '+ curl -i -X %s %s%s\n' "$method" "$BASE_URL" "$path"

  if [[ -n "$body" ]]; then
    if [[ -n "$token" && "$csrf_mode" != "no-csrf" ]]; then
      curl -sS -i \
        -X "$method" "$BASE_URL$path" \
        -H "Accept: application/json" \
        -H "Content-Type: application/json" \
        -H "X-XSRF-TOKEN: $token" \
        -b "$jar" \
        -c "$jar" \
        --data "$body" | tee "$output"
    else
      curl -sS -i \
        -X "$method" "$BASE_URL$path" \
        -H "Accept: application/json" \
        -H "Content-Type: application/json" \
        -b "$jar" \
        -c "$jar" \
        --data "$body" | tee "$output"
    fi
  else
    if [[ -n "$token" && "$csrf_mode" != "no-csrf" && "$method" =~ ^(POST|PUT|DELETE|PATCH)$ ]]; then
      curl -sS -i \
        -X "$method" "$BASE_URL$path" \
        -H "Accept: application/json" \
        -H "X-XSRF-TOKEN: $token" \
        -b "$jar" \
        -c "$jar" | tee "$output"
    else
      curl -sS -i \
        -X "$method" "$BASE_URL$path" \
        -H "Accept: application/json" \
        -b "$jar" \
        -c "$jar" | tee "$output"
    fi
  fi
}

curl_headers() {
  local label="$1"
  local path="$2"
  local safe
  safe="$(safe_label "$label")"

  printf '\n--- %s\n' "$label"
  printf '+ curl -i -X GET %s%s\n' "$BASE_URL" "$path"
  curl -sS -i -X GET "$BASE_URL$path" -H "Accept: text/html,application/json" | tee "$OUT_DIR/${safe}.http"
}

json_string_from_file() {
  local file="$1"
  local field="$2"
  { grep -o "\"$field\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" "$file" 2>/dev/null || true; } \
    | head -n 1 \
    | sed -E 's/.*:[[:space:]]*"([^"]*)".*/\1/'
}

reset_token_from_response_or_mailhog() {
  local response_file="$1"
  local email="$2"
  local token

  token="$(json_string_from_file "$response_file" "token")"
  if [[ -n "$token" ]]; then
    printf '%s' "$token"
    return 0
  fi

  token="$(
    curl -sS "$MAILHOG_URL/api/v2/messages" 2>/dev/null \
      | grep -A 30 -F "$email" \
      | grep -Eo 'Reset token: [A-Za-z0-9_-]+' \
      | tail -n 1 \
      | awk '{ print $3 }' \
      || true
  )"

  if [[ -z "$token" ]]; then
    token="$(
      curl -sS "$MAILHOG_URL/api/v2/messages" 2>/dev/null \
        | grep -Eo 'token=[A-Za-z0-9_-]+' \
        | tail -n 1 \
        | cut -d= -f2 \
        || true
    )"
  fi

  printf '%s' "$token"
}

ticket_id_from_response() {
  local response_file="$1"
  json_string_from_file "$response_file" "id"
}

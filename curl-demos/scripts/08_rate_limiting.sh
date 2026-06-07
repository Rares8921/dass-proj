#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

section "08 - Login rate limiting"

jar="$(reset_cookie_jar rate_limit)"
email="burst-${RUN_ID}@authx.local"

for attempt in 1 2 3 4 5 6; do
  curl_json "08_login_attempt_${attempt}" "POST" "/api/auth/login" "$jar" \
    "{\"email\":\"$email\",\"password\":\"WrongPassword123!\"}"
done
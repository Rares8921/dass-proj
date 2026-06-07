#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

section "03 - Session cookie hardening"

jar="$(reset_cookie_jar session_cookie)"

curl_json "03_login_extract_cookie" "POST" "/api/auth/login" "$jar" \
  '{"email":"analyst@authx.local","password":"Analyst123!"}'
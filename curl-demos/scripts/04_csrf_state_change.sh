#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

section "04 - CSRF on state-changing requests"

jar="$(reset_cookie_jar csrf_demo)"

curl_json "04_login_for_csrf_demo" "POST" "/api/auth/login" "$jar" \
  '{"email":"analyst@authx.local","password":"Analyst123!"}'
echo

curl_json "04_create_ticket_without_csrf" "POST" "/api/tickets" "$jar" \
  '{"title":"CSRF missing token","description":"State change without CSRF token","severity":"LOW","status":"OPEN"}' \
  "no-csrf"
echo
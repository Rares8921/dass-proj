#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

section "06 - Audit endpoint authorization"

analyst_jar="$(reset_cookie_jar audit_analyst)"
manager_jar="$(reset_cookie_jar audit_manager)"

curl_json "06_login_analyst" "POST" "/api/auth/login" "$analyst_jar" \
  '{"email":"analyst@authx.local","password":"Analyst123!"}'

curl_json "06_analyst_get_audit" "GET" "/api/audit" "$analyst_jar"

curl_json "06_login_manager" "POST" "/api/auth/login" "$manager_jar" \
  '{"email":"manager@authx.local","password":"Manager123!"}'

curl_json "06_manager_get_audit" "GET" "/api/audit" "$manager_jar"
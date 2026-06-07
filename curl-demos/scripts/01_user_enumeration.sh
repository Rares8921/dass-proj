#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

section "01 - User enumeration via login errors"

jar="$(reset_cookie_jar enumeration)"

curl_json "01_missing_user_login" "POST" "/api/auth/login" "$jar" \
  '{"email":"missing-user@authx.local","password":"WrongPassword123!"}'

curl_json "01_existing_user_wrong_password" "POST" "/api/auth/login" "$jar" \
  '{"email":"analyst@authx.local","password":"WrongPassword123!"}'
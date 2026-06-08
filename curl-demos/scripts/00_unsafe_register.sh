#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

section "01 - Registration with weak vs strong password"

jar="$(reset_cookie_jar register_demo)"

curl_json "02_register_weak_password" "POST" "/api/auth/register" "$jar" \
  '{"email":"weak-user@authx.local","password":"123"}'
echo

curl_json "03_register_strong_password" "POST" "/api/auth/register" "$jar" \
  '{"email":"strong-user@authx.local","password":"T5X$m9#kL2@vQ8!zW1"}'
echo
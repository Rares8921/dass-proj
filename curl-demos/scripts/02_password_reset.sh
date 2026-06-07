#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

section "02 - Password reset token exposure and reuse"

email="reset-${RUN_ID}@authx.local"
old_password="CurlDemo123!"
new_password="CurlDemo456!"
reuse_password="CurlDemo789!"
jar="$(reset_cookie_jar reset_user)"

curl_json "02_register_reset_user" "POST" "/api/auth/register" "$jar" \
  "{\"email\":\"$email\",\"password\":\"$old_password\"}"

curl_json "02_forgot_password" "POST" "/api/auth/forgot-password" "$jar" \
  "{\"email\":\"$email\"}"

forgot_file="$OUT_DIR/02_forgot_password.http"
token="$(reset_token_from_response_or_mailhog "$forgot_file" "$email")"

if [[ -z "$token" ]]; then
  echo "Nu s-a putut extrage tokenul automat."
  echo " -> Verifica raspunsul salvat din: $forgot_file"
  echo " -> Verifica MailHog la: $MAILHOG_URL"
  exit 0
fi

note "Token extras: $token"

curl_json "02_reset_password_first_use" "POST" "/api/auth/reset-password" "$jar" \
  "{\"token\":\"$token\",\"newPassword\":\"$new_password\"}"

curl_json "02_reset_password_reuse_same_token" "POST" "/api/auth/reset-password" "$jar" \
  "{\"token\":\"$token\",\"newPassword\":\"$reuse_password\"}"
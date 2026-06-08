curl_json "02_register_weak_password" "POST" "/api/auth/register" "$jar" \
  '{"email":"weak-user@authx.local","password":"123"}'
echo

curl_json "03_register_strong_password" "POST" "/api/auth/register" "$jar" \
  '{"email":"strong-user@authx.local","password":"T5X$m9#kL2@vQ8!zW1"}'
echo
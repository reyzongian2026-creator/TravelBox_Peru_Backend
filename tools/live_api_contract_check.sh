#!/usr/bin/env bash
set -euo pipefail

require_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "Missing required env: $name" >&2
    exit 1
  fi
}

require_env "LIVE_API_BASE_URL"
require_env "E2E_ADMIN_EMAIL"
require_env "E2E_ADMIN_PASSWORD"
require_env "E2E_CLIENT_EMAIL"
require_env "E2E_CLIENT_PASSWORD"

BASE_URL="${LIVE_API_BASE_URL%/}"
ROOT_URL="${BASE_URL%/api/v1}"

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required for contract assertions." >&2
  exit 1
fi

json_request() {
  local method="$1"
  local url="$2"
  local bearer="${3:-}"
  local body="${4:-}"

  local headers=(
    -H "Accept: application/json"
  )
  if [[ -n "$bearer" ]]; then
    headers+=(-H "Authorization: Bearer $bearer")
  fi
  if [[ -n "$body" ]]; then
    headers+=(-H "Content-Type: application/json")
  fi

  local response
  if [[ -n "$body" ]]; then
    response="$(curl -sS -X "$method" "${headers[@]}" --data "$body" "$url" -w $'\n%{http_code}')"
  else
    response="$(curl -sS -X "$method" "${headers[@]}" "$url" -w $'\n%{http_code}')"
  fi

  local status
  status="$(echo "$response" | tail -n 1)"
  local payload
  payload="$(echo "$response" | sed '$d')"

  echo "$status"
  echo "$payload"
}

expect_status() {
  local name="$1"
  local expected="$2"
  local actual="$3"
  if [[ "$actual" != "$expected" ]]; then
    echo "[$name] Expected status $expected but got $actual" >&2
    exit 1
  fi
}

assert_json() {
  local name="$1"
  local payload="$2"
  local jq_expr="$3"
  if ! echo "$payload" | jq -e "$jq_expr" >/dev/null; then
    echo "[$name] Contract assertion failed: $jq_expr" >&2
    echo "[$name] Payload: $payload" >&2
    exit 1
  fi
}

login_token() {
  local login_name="$1"
  local email="$2"
  local password="$3"
  local login_body
  login_body="$(jq -n --arg email "$email" --arg password "$password" '{email:$email,password:$password}')"

  mapfile -t out < <(json_request "POST" "$BASE_URL/auth/login" "" "$login_body")
  local status="${out[0]}"
  local payload="${out[1]}"

  expect_status "$login_name" "200" "$status"
  assert_json "$login_name" "$payload" '
    (.accessToken | type == "string" and length > 20) and
    (.refreshToken | type == "string" and length > 20) and
    (.user | type == "object") and
    (.roles | type == "array")
  '

  echo "$payload" | jq -r '.accessToken'
}

echo "Running live API contract checks against: $BASE_URL"

if [[ "$ROOT_URL" != "$BASE_URL" ]]; then
  mapfile -t health_out < <(json_request "GET" "$ROOT_URL/actuator/health")
  expect_status "health" "200" "${health_out[0]}"
  assert_json "health" "${health_out[1]}" '.status == "UP"'
fi

admin_token="$(login_token "admin_login" "$E2E_ADMIN_EMAIL" "$E2E_ADMIN_PASSWORD")"
client_token="$(login_token "client_login" "$E2E_CLIENT_EMAIL" "$E2E_CLIENT_PASSWORD")"

mapfile -t dashboard_out < <(json_request "GET" "$BASE_URL/admin/dashboard?period=month" "$admin_token")
expect_status "admin_dashboard" "200" "${dashboard_out[0]}"
assert_json "admin_dashboard" "${dashboard_out[1]}" '
  (.periodLabel | type == "string") and
  (.summary | type == "object") and
  (.summary.reservations | type == "number") and
  (.summary.confirmedRevenue | type == "number") and
  (.topWarehouses | type == "array") and
  (.topCities | type == "array") and
  (.statusBreakdown | type == "array") and
  (.trend | type == "array")
'

mapfile -t warehouses_out < <(json_request "GET" "$BASE_URL/admin/warehouses" "$admin_token")
expect_status "admin_warehouses" "200" "${warehouses_out[0]}"
assert_json "admin_warehouses" "${warehouses_out[1]}" '
  (type == "array") and
  (length >= 1) and
  (.[0].id != null) and
  (.[0].name | type == "string") and
  (.[0].active | type == "boolean")
'

mapfile -t users_out < <(json_request "GET" "$BASE_URL/admin/users" "$admin_token")
expect_status "admin_users" "200" "${users_out[0]}"
assert_json "admin_users" "${users_out[1]}" '
  (type == "array") and
  (length >= 1) and
  (.[0].id != null) and
  (.[0].email | type == "string") and
  (.[0].roles | type == "array")
'

mapfile -t public_warehouses_out < <(json_request "GET" "$BASE_URL/warehouses/search")
expect_status "public_warehouses_search" "200" "${public_warehouses_out[0]}"
assert_json "public_warehouses_search" "${public_warehouses_out[1]}" '
  (type == "array") and
  (length >= 1) and
  (.[0].id != null) and
  (.[0].name | type == "string")
'

mapfile -t reservations_out < <(json_request "GET" "$BASE_URL/reservations/page?page=0&size=1" "$client_token")
expect_status "client_reservations_page" "200" "${reservations_out[0]}"
assert_json "client_reservations_page" "${reservations_out[1]}" '
  (type == "object") and
  ((.items | type == "array") or (.content | type == "array") or (.data | type == "array"))
'

echo "Live API contract checks completed successfully."

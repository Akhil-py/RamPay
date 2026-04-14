#!/usr/bin/env bash
# =============================================================================
#  RamPay Demo Script
#  Starts the full stack, seeds data, runs a transaction sequence that
#  exercises every fraud rule, and prints Grafana / Jaeger URLs.
#
#  Usage:
#    ./demo.sh             — run the demo
#    ./demo.sh --teardown  — bring the stack down and remove volumes
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/../infra/docker-compose.yml"

# -----------------------------------------------------------------------------
# ANSI colours
# -----------------------------------------------------------------------------
BOLD="\033[1m"
RESET="\033[0m"
GREEN="\033[0;32m"
RED="\033[0;31m"
YELLOW="\033[0;33m"
CYAN="\033[0;36m"
WHITE="\033[1;37m"

# Counters for summary
PAYMENTS_SENT=0
PAYMENTS_FLAGGED=0
RULES_FIRED=()

# -----------------------------------------------------------------------------
# Helpers
# -----------------------------------------------------------------------------

banner() {
  echo ""
  echo -e "${BOLD}${WHITE}══════════════════════════════════════════════════════════════${RESET}"
  echo -e "${BOLD}${CYAN}  $1${RESET}"
  echo -e "${BOLD}${WHITE}══════════════════════════════════════════════════════════════${RESET}"
  echo ""
}

step() {
  echo -e "${BOLD}${YELLOW}▶ $1${RESET}"
}

ok() {
  echo -e "${GREEN}  ✓ $1${RESET}"
}

warn() {
  echo -e "${YELLOW}  ⚠ $1${RESET}"
}

fail_msg() {
  echo -e "${RED}  ✗ $1${RESET}"
}

# Generate a UUID — try uuidgen first, fall back to /proc
new_uuid() {
  if command -v uuidgen &>/dev/null; then
    uuidgen | tr '[:upper:]' '[:lower:]'
  elif [[ -r /proc/sys/kernel/random/uuid ]]; then
    cat /proc/sys/kernel/random/uuid
  else
    # Last resort: python3
    python3 -c "import uuid; print(uuid.uuid4())"
  fi
}

# trap for unexpected errors
trap 'echo -e "\n${RED}${BOLD}Demo failed unexpectedly at line $LINENO. Check the logs above.${RESET}\n" >&2' ERR

# -----------------------------------------------------------------------------
# --teardown mode
# -----------------------------------------------------------------------------
if [[ "${1:-}" == "--teardown" ]]; then
  banner "Tearing Down RamPay Stack"
  step "Removing all containers and volumes..."
  docker compose -f "$COMPOSE_FILE" down -v
  ok "Stack torn down. All data volumes removed."
  exit 0
fi

# -----------------------------------------------------------------------------
# Preflight: check jq
# -----------------------------------------------------------------------------
if ! command -v jq &>/dev/null; then
  echo -e "${RED}${BOLD}ERROR: jq is not installed.${RESET}"
  echo ""
  echo "  Install it with one of:"
  echo "    macOS:  brew install jq"
  echo "    Ubuntu: sudo apt-get install -y jq"
  echo "    Windows (WSL): sudo apt-get install -y jq"
  echo "    Windows (Chocolatey): choco install jq"
  echo ""
  exit 1
fi

# -----------------------------------------------------------------------------
# Fixed demo account UUIDs
# (UUIDs are deterministic so logs always look the same)
# -----------------------------------------------------------------------------
ACC_001="00000000-0000-0000-0000-000000000001"   # normal sender
ACC_002="00000000-0000-0000-0000-000000000002"   # normal receiver
ACC_003="00000000-0000-0000-0000-000000000003"   # large amount sender
ACC_004="00000000-0000-0000-0000-000000000004"   # large amount receiver
ACC_BURST="00000000-0000-0000-0000-000000000010" # velocity burst sender
ACC_BLOCKED="00000000-0000-0000-0000-000000000020" # blocklisted sender
ACC_VIP="00000000-0000-0000-0000-000000000030"   # very large amount sender

# Base URLs
PAYMENT_BASE="http://localhost:8080"
FRAUD_BASE="http://localhost:8001"
DYNAMO_BASE="http://localhost:8000"

# -----------------------------------------------------------------------------
# Task 1 — Start the stack
# -----------------------------------------------------------------------------
banner "Starting RamPay Stack"

step "Running: docker compose up -d --build"
docker compose -f "$COMPOSE_FILE" up -d --build
echo ""

# -----------------------------------------------------------------------------
# Task 2 — Wait for services to be healthy
# -----------------------------------------------------------------------------
banner "Waiting for Services to Become Healthy"

TIMEOUT=120
SPINNER_CHARS='⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏'

wait_for_http() {
  local service_name="$1"
  local url="$2"
  local expected_status="${3:-200}"
  local deadline=$(( $(date +%s) + TIMEOUT ))
  local spinner_idx=0

  printf "  Waiting for %-20s " "$service_name..."
  while true; do
    local ts
    ts=$(date +%s)
    if [[ $ts -ge $deadline ]]; then
      echo ""
      fail_msg "Timed out waiting for $service_name after ${TIMEOUT}s"
      echo -e "${YELLOW}  Hint: run 'docker compose -f $COMPOSE_FILE logs $service_name' to diagnose.${RESET}"
      exit 1
    fi

    local http_code
    http_code=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 2 "$url" 2>/dev/null || true)

    if [[ "$expected_status" == "any" && -n "$http_code" && "$http_code" != "000" ]]; then
      printf "\r  %-42s" " "
      printf "\r"
      ok "$service_name is ready (HTTP $http_code)"
      return 0
    elif [[ "$http_code" == "$expected_status" ]]; then
      printf "\r  %-42s" " "
      printf "\r"
      ok "$service_name is ready"
      return 0
    fi

    local spin_char="${SPINNER_CHARS:$spinner_idx:1}"
    printf "\r  %s Waiting for %-20s (HTTP %s)" "$spin_char" "$service_name..." "$http_code"
    spinner_idx=$(( (spinner_idx + 1) % ${#SPINNER_CHARS} ))
    sleep 2
  done
}

wait_for_http "dynamodb-local"    "$DYNAMO_BASE/"      "any"
wait_for_http "fraud-service"     "$FRAUD_BASE/health" "200"
wait_for_http "payment-service"   "$PAYMENT_BASE/health" "200"

echo ""
ok "All services are healthy."

# -----------------------------------------------------------------------------
# Task 3 — Seed the blocklist
# -----------------------------------------------------------------------------
banner "Seeding Demo Data"

step "Adding ACC-BLOCKED ($ACC_BLOCKED) to Redis blocklist..."
docker compose -f "$COMPOSE_FILE" exec -T redis \
  redis-cli SET "blocklist:$ACC_BLOCKED" "true" > /dev/null
ok "blocklist:$ACC_BLOCKED = true"

echo ""

# -----------------------------------------------------------------------------
# Helper: POST a payment
# Returns the raw JSON response
# -----------------------------------------------------------------------------
post_payment() {
  local from="$1"
  local to="$2"
  local amount="$3"
  local currency="${4:-USD}"
  local idempotency_key
  idempotency_key="$(new_uuid)"

  curl -s -X POST "$PAYMENT_BASE/payments" \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: $idempotency_key" \
    -d "{\"fromAccountId\":\"$from\",\"toAccountId\":\"$to\",\"amount\":$amount,\"currency\":\"$currency\"}"
}

# Helper: GET payments by account
get_payments_by_account() {
  local account_id="$1"
  curl -s "$PAYMENT_BASE/payments/account/$account_id"
}

# Helper: GET a single payment
get_payment() {
  local payment_id="$1"
  curl -s "$PAYMENT_BASE/payments/$payment_id"
}

# Helper: print a payment response with colour-coded status
print_payment() {
  local label="$1"
  local json="$2"

  local id status amount
  id=$(echo "$json"     | jq -r '.id          // "unknown"')
  status=$(echo "$json" | jq -r '.status      // "unknown"')
  amount=$(echo "$json" | jq -r '.amount      // "?"')
  currency=$(echo "$json" | jq -r '.currency  // ""')

  case "$status" in
    FAILED|FRAUD_FLAGGED)
      echo -e "  ${RED}${BOLD}[$label]${RESET} ${RED}ID: $id | Amount: $amount $currency | Status: $status${RESET}"
      ;;
    PENDING|PROCESSING)
      echo -e "  ${YELLOW}[$label]${RESET} ID: $id | Amount: $amount $currency | Status: $status"
      ;;
    COMPLETED|APPROVED)
      echo -e "  ${GREEN}[$label]${RESET} ID: $id | Amount: $amount $currency | Status: $status"
      ;;
    *)
      echo -e "  [$label] ID: $id | Amount: $amount $currency | Status: $status"
      ;;
  esac
}

# -----------------------------------------------------------------------------
# Batch 1 — Normal payments (should all succeed)
# -----------------------------------------------------------------------------
banner "Batch 1 — Normal Payments (ACC-001 → ACC-002)"
echo -e "  ${CYAN}Expected: PENDING / COMPLETED — no fraud rules triggered${RESET}"
echo ""

for amount in 150.00 250.00 75.00; do
  step "Sending $amount USD ..."
  RESP=$(post_payment "$ACC_001" "$ACC_002" "$amount")
  print_payment "Batch1" "$RESP"
  PAYMENTS_SENT=$(( PAYMENTS_SENT + 1 ))
  sleep 0.5
done

# -----------------------------------------------------------------------------
# Batch 2 — Large amount (MEDIUM risk, should not be blocked)
# -----------------------------------------------------------------------------
banner "Batch 2 — Large Amount (ACC-003 → ACC-004, \$15,000)"
echo -e "  ${CYAN}Expected: PENDING — triggers large_amount rule (MEDIUM risk, not blocked)${RESET}"
echo ""

step "Sending 15000.00 USD ..."
RESP=$(post_payment "$ACC_003" "$ACC_004" "15000.00")
print_payment "Batch2" "$RESP"
PAYMENTS_SENT=$(( PAYMENTS_SENT + 1 ))

BATCH2_STATUS=$(echo "$RESP" | jq -r '.status // "unknown"')
if [[ "$BATCH2_STATUS" != "FAILED" && "$BATCH2_STATUS" != "FRAUD_FLAGGED" ]]; then
  ok "Large amount payment accepted (MEDIUM risk does not auto-block)"
else
  warn "Large amount payment was blocked — fraud engine upgraded to HIGH?"
  PAYMENTS_FLAGGED=$(( PAYMENTS_FLAGGED + 1 ))
  RULES_FIRED+=("large_amount→blocked")
fi

# -----------------------------------------------------------------------------
# Batch 3 — Velocity burst (should trigger fraud on payment 6+)
# -----------------------------------------------------------------------------
banner "Batch 3 — Velocity Burst (ACC-BURST-001 → ACC-002, 7 × \$50)"
echo -e "  ${CYAN}Expected: payments 6+ flagged — velocity rule fires at >5 txns/60s${RESET}"
echo ""

for i in $(seq 1 7); do
  step "Velocity burst payment $i/7 ..."
  RESP=$(post_payment "$ACC_BURST" "$ACC_002" "50.00")
  print_payment "Burst-$i" "$RESP"
  PAYMENTS_SENT=$(( PAYMENTS_SENT + 1 ))
  # No sleep — fire fast to trigger velocity
done

step "Waiting 3 seconds for fraud evaluation to propagate..."
sleep 3

step "Checking status of all burst account payments..."
BURST_PAYMENTS=$(get_payments_by_account "$ACC_BURST")
BURST_FAILED=$(echo "$BURST_PAYMENTS" | jq '[.content[] | select(.status == "FAILED" or .status == "FRAUD_FLAGGED")] | length')
BURST_TOTAL=$(echo "$BURST_PAYMENTS" | jq '.content | length')

if [[ "$BURST_FAILED" -gt 0 ]]; then
  ok "Velocity fraud detected: $BURST_FAILED/$BURST_TOTAL payments were flagged/failed"
  PAYMENTS_FLAGGED=$(( PAYMENTS_FLAGGED + BURST_FAILED ))
  RULES_FIRED+=("velocity(${BURST_FAILED}x)")
else
  warn "No velocity fraud detected yet — fraud consumer may still be processing"
fi

# -----------------------------------------------------------------------------
# Batch 4 — Blocklisted account (should trigger immediately)
# -----------------------------------------------------------------------------
banner "Batch 4 — Blocklisted Account (ACC-BLOCKED → ACC-002, \$100)"
echo -e "  ${CYAN}Expected: FAILED — blocklist rule fires immediately (HIGH risk)${RESET}"
echo ""

step "Sending payment from blocklisted account..."
RESP=$(post_payment "$ACC_BLOCKED" "$ACC_002" "100.00")
print_payment "Blocked" "$RESP"
BLOCKLISTED_ID=$(echo "$RESP" | jq -r '.id // empty')
PAYMENTS_SENT=$(( PAYMENTS_SENT + 1 ))

step "Waiting 3 seconds for fraud evaluation..."
sleep 3

if [[ -n "$BLOCKLISTED_ID" ]]; then
  REFRESHED=$(get_payment "$BLOCKLISTED_ID")
  BLOCKED_STATUS=$(echo "$REFRESHED" | jq -r '.status // "unknown"')
  BLOCKED_REASON=$(echo "$REFRESHED" | jq -r '.failureReason // "none"')
  if [[ "$BLOCKED_STATUS" == "FAILED" || "$BLOCKED_STATUS" == "FRAUD_FLAGGED" ]]; then
    ok "Blocklist rule fired! Status: $BLOCKED_STATUS | Reason: $BLOCKED_REASON"
    PAYMENTS_FLAGGED=$(( PAYMENTS_FLAGGED + 1 ))
    RULES_FIRED+=("blocklist")
  else
    warn "Payment not yet failed (status: $BLOCKED_STATUS) — consumer may be delayed"
    print_payment "Blocked-check" "$REFRESHED"
  fi
fi

# -----------------------------------------------------------------------------
# Batch 5 — Very large amount (HIGH risk — should be flagged)
# -----------------------------------------------------------------------------
banner "Batch 5 — Very Large Amount (ACC-VIP → ACC-002, \$75,000)"
echo -e "  ${CYAN}Expected: FAILED — very_large_amount rule fires (HIGH risk, >50k)${RESET}"
echo ""

step "Sending 75000.00 USD ..."
RESP=$(post_payment "$ACC_VIP" "$ACC_002" "75000.00")
print_payment "VIP" "$RESP"
VIP_ID=$(echo "$RESP" | jq -r '.id // empty')
PAYMENTS_SENT=$(( PAYMENTS_SENT + 1 ))

step "Waiting 3 seconds for fraud evaluation..."
sleep 3

if [[ -n "$VIP_ID" ]]; then
  REFRESHED=$(get_payment "$VIP_ID")
  VIP_STATUS=$(echo "$REFRESHED" | jq -r '.status // "unknown"')
  VIP_REASON=$(echo "$REFRESHED" | jq -r '.failureReason // "none"')
  if [[ "$VIP_STATUS" == "FAILED" || "$VIP_STATUS" == "FRAUD_FLAGGED" ]]; then
    ok "Very large amount rule fired! Status: $VIP_STATUS | Reason: $VIP_REASON"
    PAYMENTS_FLAGGED=$(( PAYMENTS_FLAGGED + 1 ))
    RULES_FIRED+=("very_large_amount")
  else
    warn "Payment not yet failed (status: $VIP_STATUS) — consumer may be delayed"
    print_payment "VIP-check" "$REFRESHED"
  fi
fi

# -----------------------------------------------------------------------------
# Fraud service metrics
# -----------------------------------------------------------------------------
banner "Fraud Service Metrics"

echo -e "${BOLD}=== Fraud service metrics ===${RESET}"
echo ""
METRICS=$(curl -s "$FRAUD_BASE/metrics")
if echo "$METRICS" | grep -q "anomalies_detected_total"; then
  echo "$METRICS" | grep "anomalies_detected_total" | while read -r line; do
    echo -e "  ${CYAN}$line${RESET}"
  done
else
  warn "No anomalies_detected_total metric found yet — fraud consumer may still be warming up"
fi

echo ""
step "Fraud rules active (from /rules endpoint):"
curl -s "$FRAUD_BASE/rules" | jq -r '.rules[] | "  • \(.id): \(.description) [\(.risk)]"'

# -----------------------------------------------------------------------------
# Observability links
# -----------------------------------------------------------------------------
banner "Observability Endpoints"

echo -e "${BOLD}${GREEN}=== Grafana Dashboard ===${RESET}"
echo -e "  Open:      ${CYAN}http://localhost:3000${RESET}"
echo -e "  Creds:     ${CYAN}admin / admin${RESET}"
echo -e "  Dashboard: ${CYAN}RamPay Overview${RESET}"
echo ""
echo -e "${BOLD}${GREEN}=== Jaeger Traces ===${RESET}"
echo -e "  Open:      ${CYAN}http://localhost:16686${RESET}"
echo -e "  Service:   ${CYAN}payment-service${RESET}"
echo ""
echo -e "${BOLD}${GREEN}=== Prometheus ===${RESET}"
echo -e "  Open:      ${CYAN}http://localhost:9090${RESET}"
echo ""
echo -e "${BOLD}${GREEN}=== Fraud Service ===${RESET}"
echo -e "  Health:    ${CYAN}http://localhost:8001/health${RESET}"
echo -e "  Metrics:   ${CYAN}http://localhost:8001/metrics${RESET}"
echo -e "  Rules:     ${CYAN}http://localhost:8001/rules${RESET}"

# -----------------------------------------------------------------------------
# Final summary
# -----------------------------------------------------------------------------
banner "Demo Complete — Summary"

echo -e "  ${BOLD}Payments sent:   ${WHITE}$PAYMENTS_SENT${RESET}"

if [[ $PAYMENTS_FLAGGED -gt 0 ]]; then
  echo -e "  ${BOLD}Payments flagged: ${RED}$PAYMENTS_FLAGGED${RESET}"
else
  echo -e "  ${BOLD}Payments flagged: ${YELLOW}0 (fraud consumer may still be processing)${RESET}"
fi

if [[ ${#RULES_FIRED[@]} -gt 0 ]]; then
  echo -e "  ${BOLD}Rules fired:${RESET}"
  for rule in "${RULES_FIRED[@]}"; do
    echo -e "    ${RED}• $rule${RESET}"
  done
else
  echo -e "  ${BOLD}Rules fired:     ${YELLOW}(check fraud-service logs for async evaluations)${RESET}"
fi

echo ""
echo -e "  ${CYAN}Tip: watch fraud-service logs in real time with:${RESET}"
echo -e "  ${WHITE}  docker compose -f $COMPOSE_FILE logs -f fraud-service${RESET}"
echo ""
echo -e "  ${CYAN}Tear down when done:${RESET}"
echo -e "  ${WHITE}  ./demo.sh --teardown${RESET}"
echo ""
echo -e "${BOLD}${GREEN}  RamPay demo finished successfully.${RESET}"
echo ""

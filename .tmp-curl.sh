#!/bin/sh
set -e
TOK=$(curl -sS -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"rohit@coachsim.local","password":"fan1234"}' \
  | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
echo "GOT token len=$(echo -n "$TOK" | wc -c)"
WINDOWS=$(curl -sS -H "Authorization: Bearer $TOK" \
  "http://localhost:8080/api/decisions/windows/open?matchId=35")
WID=$(echo "$WINDOWS" | sed -n 's/.*"id":\([0-9][0-9]*\),"matchId":35,"type":"BOWLING_CHANGE".*/\1/p' | head -1)
echo "Using windowId=$WID"
echo "---"
echo "VERBOSE POST to /api/decisions:"
curl -i -X POST http://localhost:8080/api/decisions \
  -H "Authorization: Bearer $TOK" \
  -H 'Content-Type: application/json' \
  -d "{\"windowId\":$WID,\"payload\":{\"bowler\":\"J. Bumrah\",\"bowlerType\":\"PACE\"}}"
echo

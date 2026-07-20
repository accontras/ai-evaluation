#!/bin/bash
# S18 M2 verification: batch evaluation to accumulate 20+ comparison records

API="http://localhost:8080/api/v1/evaluation/execute"
SCENE="LOGISTICS-2026Q2"

# Test data: varied scenarios
declare -a BIZ_IDS=("LGS-010" "LGS-011" "LGS-012" "LGS-013" "LGS-014" "LGS-015" "LGS-016" "LGS-017" "LGS-018" "LGS-019")
declare -a COST_DEV=(5.1 8.3 12.7 15.8 3.2 18.9 7.5 11.0 20.5 9.8)
declare -a ABNORM=(0 1 3 5 2 7 1 4 8 2)
declare -a FILL_RATE=(95.0 88.5 78.2 72.0 97.3 65.5 90.1 82.0 60.8 85.0)

echo "=== S18 M2 Batch Evaluation ==="
echo "Target: 10 runs → accumulate comparison data"
echo ""

for i in ${!BIZ_IDS[@]}; do
  echo "--- Run $((i+1))/10: ${BIZ_IDS[$i]} ---"
  RESP=$(curl -s -X POST "$API" -H "Content-Type: application/json" \
    -d "{\"sceneCode\":\"$SCENE\",\"bizId\":\"${BIZ_IDS[$i]}\",\"data\":{\"cost_deviation\":${COST_DEV[$i]},\"abnormal_count\":${ABNORM[$i]},\"fill_rate\":${FILL_RATE[$i]}}}")
  
  SCORE=$(echo "$RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['data']['totalScore'])" 2>/dev/null)
  MODE=$(echo "$RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['data']['scoringMode'])" 2>/dev/null)
  echo "  totalScore=$SCORE  mode=$MODE"
done

echo ""
echo "=== Checking stats ==="
curl -s "http://localhost:8080/api/v1/evaluation/compare/stats"

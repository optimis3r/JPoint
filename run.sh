#!/bin/bash
set -e

echo "Booting Project JPoint..."

# Docker stack
echo "[*] Starting Docker Compose stack..."
docker-compose up -d --build

# Python worker
echo "[*] Booting Python Worker Node..."
cd worker-node
if [ ! -d "venv" ]; then
    python -m venv venv
    source venv/bin/activate
    pip install -r requirements.txt
else
    source venv/bin/activate
fi

# Load environment
if [ -f ".env" ]; then
    export $(grep -v '^#' .env | sed -E 's/^[[:space:]]*//;s/[[:space:]]*=[[:space:]]*/=/' | xargs -d '\n' 2>/dev/null) 2>/dev/null || true
elif [ -f "../.env" ]; then
    export $(grep -v '^#' ../.env | sed -E 's/^[[:space:]]*//;s/[[:space:]]*=[[:space:]]*/=/' | xargs -d '\n' 2>/dev/null) 2>/dev/null || true
fi

python worker.py > ../worker.log 2>&1 &
WORKER_PID=$!
cd ..
echo "[*] Worker Node running in background (PID: $WORKER_PID, logs -> worker.log)"

# Frontend deck
echo "[*] Booting Next.js Observer Deck..."
cd frontend
if [ ! -d "node_modules" ]; then
    npm install
fi
npm run dev > ../frontend.log 2>&1 &
FRONTEND_PID=$!
cd ..
echo "[*] Frontend running in background (PID: $FRONTEND_PID, logs -> frontend.log)"

echo "=================================================="
echo "Project JPoint operational!"
echo "Observer Deck: http://localhost:3000"
echo "API Docs:      http://localhost:8000/docs"
echo "=================================================="

# Save PIDs
echo $WORKER_PID > .worker.pid
echo $FRONTEND_PID > .frontend.pid
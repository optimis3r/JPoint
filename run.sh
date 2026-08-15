#!/bin/bash
set -e

echo "Booting Project JPoint Full-Stack Master Environment..."

# 1. Start Docker Infrastructure (MinIO, Redis, Jaeger, FastAPI)
echo "[*] Starting Docker Compose stack..."

docker-compose up -d --build

# 2. Start Python Worker Node in the background
echo "[*] Booting Python Worker Node..."
cd worker-node
if [ ! -d "venv" ]; then
    python -m venv venv
    source venv/bin/activate
    pip install -r requirements.txt
else
    source venv/bin/activate
fi

# Safely load local .env or root .env without xargs syntax errors
if [ -f ".env" ]; then
    export $(grep -v '^#' .env | sed -E 's/^[[:space:]]*//;s/[[:space:]]*=[[:space:]]*/=/' | xargs -d '\n' 2>/dev/null) 2>/dev/null || true
elif [ -f "../.env" ]; then
    export $(grep -v '^#' ../.env | sed -E 's/^[[:space:]]*//;s/[[:space:]]*=[[:space:]]*/=/' | xargs -d '\n' 2>/dev/null) 2>/dev/null || true
fi

python worker.py > ../worker.log 2>&1 &
WORKER_PID=$!
cd ..
echo "[*] Worker Node running in background (PID: $WORKER_PID, logs -> worker.log)"

# 3. Start Next.js Frontend Observer Deck in the background
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
echo "Project JPoint is fully operational!"
echo "Observer Deck: http://localhost:3000"
echo "API Docs:      http://localhost:8000/docs"
echo "=================================================="
echo "To stop everything, run: ./stop.sh"

# Save PIDs for graceful teardown
echo $WORKER_PID > .worker.pid
echo $FRONTEND_PID > .frontend.pid
#!/bin/bash
echo "Shutting down Project JPoint..."

if [ -f .worker.pid ]; then
    kill $(cat .worker.pid) 2>/dev/null || true
    rm .worker.pid
    echo "[*] Stopped Python worker node."
fi

if [ -f .frontend.pid ]; then
    kill $(cat .frontend.pid) 2>/dev/null || true
    rm .frontend.pid
    echo "[*] Stopped Next.js frontend."
fi

docker-compose down
echo "[*] Stopped Docker Compose stack."
echo "System clean and offline."
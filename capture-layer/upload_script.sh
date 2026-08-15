#!/bin/bash
# Project JPoint Capture & Upload Script (Fully Automated with Webhook Trigger)

DUMP_DIR="/tmp/dumps"
MINIO_BUCKET="s3://jpoint-raw-dumps"
MINIO_ENDPOINT="http://127.0.0.1:9000"
GATEWAY_WEBHOOK="http://localhost:8000/api/v1/webhooks/minio"

echo "[*] Scanning $DUMP_DIR for the latest heap dump..."

# Automatically find the most recently created .hprof file
DUMP_FILE=$(ls -t "$DUMP_DIR"/*.hprof 2>/dev/null | head -n 1)

if [ -z "$DUMP_FILE" ]; then
    echo "[!] Error: No .hprof heap dump found in $DUMP_DIR! Make sure you ran Java with -XX:+HeapDumpOnOutOfMemoryError"
    exit 1
fi

echo "[*] Found heap dump: $DUMP_FILE"

# Compressing using zstd
COMPRESSED_FILE="${DUMP_FILE}.zst"
echo "[*] Compressing with zstd..."
zstd -q -T0 -3 "$DUMP_FILE" -o "$COMPRESSED_FILE" --force

# Extract just the filename for object storage key
OBJECT_KEY=$(basename "$COMPRESSED_FILE")
TRACE_ID="trace-$(date +%s)-${RANDOM}"

# Stream to MinIO using AWS CLI
echo "[*] Uploading compressed dump to MinIO..."
AWS_ACCESS_KEY_ID="admin" AWS_SECRET_ACCESS_KEY="password123" \
aws --endpoint-url "$MINIO_ENDPOINT" --region us-east-1 s3 cp "$COMPRESSED_FILE" "$MINIO_BUCKET/$OBJECT_KEY"

echo "[*] Triggering FastAPI webhook gateway to notify worker queue..."
curl -X POST "$GATEWAY_WEBHOOK" \
  -H "Content-Type: application/json" \
  -d "{
    \"bucket\": \"jpoint-raw-dumps\",
    \"objectKey\": \"$OBJECT_KEY\",
    \"traceId\": \"$TRACE_ID\"
  }"

echo -e "\nUpload and webhook notification complete! Project JPoint capture successful."
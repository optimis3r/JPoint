#!/bin/bash
# Project JPoint Capture Script

# JVM passes PID and first arg
PID=$1
DUMP_DIR="/tmp/dumps"
MINIO_BUCKET="s3://jpoint-raw-dumps"
MINIO_ENDPOINT="http://127.0.0.1:9000"

echo "OOM Detected for PID: $PID. Starting capture process..."

# locating .hprod file by the PID
DUMP_FILE=$(find $DUMP_DIR -name "java_pid${PID}.hprof")

if [ -z "$DUMP_FILE" ]; then
    echo "Error: Could not find heap dump for PID $PID"
    exit 1
fi

echo "Found heap dump: $DUMP_FILE"

# compressing using zstd
COMPRESSED_FILE="${DUMP_FILE}.zst"
echo "Compressing with zstd"
zstd -q -T0 -3 $DUMP_FILE -o $COMPRESSED_FILE

# stream to MinIO using AWS CLI
echo "Uploading to MinIO"
AWS_ACCESS_KEY_ID="admin"  AWS_SECRET_ACCESS_KEY="password123" \
aws --endpoint-url $MINIO_ENDPOINT --region us-east-1 s3 cp $COMPRESSED_FILE $MINIO_BUCKET/$(basename $COMPRESSED_FILE)

echo "Upload complete! Project JPoint cpature successful."
from fastapi import FastAPI, Request
import redis
import json
import uuid
from datetime import datetime
from opentelemetry import trace
from opentelemetry.sdk.trace import TracerProvider

# OpenTelemetry setup
trace.set_tracer_provider(TracerProvider())
tracer = trace.get_tracer(__name__)

# FastAPI and Redis
app = FastAPI(title="JPoint API Gateway")
redis_client = redis.Redis(host='jpoint-redis', port=6379, db=0, decode_responses=True)

@app.post("/api/v1/webhooks/minio")
@app.post("/api/webhook")
async def minio_webhook(request: Request):
    payload = await request.json()

    bucket = None
    object_key = None
    custom_trace_id = None

    # Parse S3 event
    if isinstance(payload, dict) and "Records" in payload:
        try:
            record = payload.get("Records", [])[0]
            bucket = record["s3"]["bucket"]["name"]
            object_key = record["s3"]["object"]["key"]
        except (IndexError, KeyError):
            pass

    # Parse direct payload
    if not bucket and isinstance(payload, dict):
        bucket = payload.get("bucket")
        object_key = payload.get("objectKey")
        custom_trace_id = payload.get("traceId")

    if not bucket or not object_key:
        return {"status": "Ignored", "reason": "Not a valid upload event or payload structure"}

    # Ignore report JSONs
    if object_key.endswith("_report.json"):
        return {"status": "Ignored", "reason": "Report file ignored to prevent processing loop"}

    # Trace job creation
    with tracer.start_as_current_span("process_heap_dump_upload") as span:
        trace_id = custom_trace_id or hex(span.get_span_context().trace_id)[2:]
        job_id = str(uuid.uuid4())

        job_payload = {
            "jobId": job_id,
            "traceId": trace_id,
            "bucket": bucket,
            "objectKey": object_key,
            "status": "QUEUED",
            "timestamp": datetime.utcnow().isoformat() + "Z"
        }

        # Push to Redis
        redis_client.lpush("jpoint_parse_queue", json.dumps(job_payload))

        print(f"Routed new file '{object_key}' to queue with job ID '{job_id}'")

        return {"status": "QUEUED", "jobId": job_id, "traceID": trace_id}
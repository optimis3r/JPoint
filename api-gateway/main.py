from fastapi import FastAPI, Request
import redis
import json
import uuid
from datetime import datetime
from opentelemetry import trace
from opentelemetry.sdk.trace import TracerProvider

# initialize openTelemetry tracing
trace.set_tracer_provider(TracerProvider())
tracer = trace.get_tracer(__name__)

# Initilliza FastAPI and redis
app = FastAPI(title="JPoint API Gateway")
redis_client = redis.Redis(host='jpoint-redis', port=6379, db=0, decode_responses=True)

# Define webhook endpoint
@app.post("/api/v1/webhooks/minio")
async def minio_webhook(request: Request):
    # parse event from minIO
    payload = await request.json()


    # MinIO sends array of records. We extract bucket and fileName
    try:
        record = payload.get("Records", [])[0]
        bucket = record["s3"]["bucket"]["name"]
        object_key = record["s3"]["object"]["key"]
    except (IndexError, KeyError):
        return {"status": "Ignored", "reason": "Not a standard S3 upload event"}

    # Trace initiation and job creation
    with tracer.start_as_current_span("process_heap_dump_upload") as span:
        trace_id = hex(span.get_span_context().trace_id)[2:]
        job_id = str(uuid.uuid4())

        # extract json data from redis
        job_payload = {
            "jobId": job_id,
            "traceId": trace_id,
            "bucket": bucket,
            "objectKey": object_key,
            "status": "QUEUED",
            "timestamp": datetime.utcnow().isoformat() + "Z"
        }

        # push to redis
        redis_client.lpush("jpoint_parse_queue", json.dumps(job_payload))

        print(f"Traffic Cop: Routeed new file '{object_key}' to queue with job ID '{job_id}'")

        return {"status": "QUEUED", "jobId": job_id, "traceID": trace_id}
import redis
import json
import sys
sys.stdout.reconfigure(line_buffering=True)
import os
import subprocess
import zipfile
import xml.etree.ElementTree as ET
import glob
from bs4 import BeautifulSoup
import io
import re
import requests
from minio import Minio
import zstandard as zstd
from datetime import datetime
from dotenv import load_dotenv

load_dotenv()

# github config
GITHUB_TOKEN = os.getenv("GITHUB_TOKEN")
GITHUB_OWNER = "optimis3r"
GITHUB_REPO = "JPoint"
GITHUB_HEADERS = {
    "Authorization": f"Bearer {GITHUB_TOKEN}",
    "Accept": "application/vnd.github.v3+json"
}

# Connect to our local Dockerized Redis with explicit socket keepalive and timeout settings
try:
    redis_client = redis.Redis(
        host='127.0.0.1', 
        port=6379, 
        db=0, 
        decode_responses=True,
        socket_keepalive=True,
        socket_timeout=None  # Disable default blocking timeouts on the socket itself
    )
    # Ping Redis to test the connection immediately
    redis_client.ping()
    print("[*] Successfully connected to Redis broker!")
except Exception as e:
    print(f"[!] Failed to connect to Redis: {e}")
    sys.exit(1)

try:
    minio_client = Minio(
        "127.0.0.1:9000",
        access_key="admin",
        secret_key="password123",
        secure=False
    )

    print("[*] Successfully connected to MinIO")
except Exception as e:
    print(f"[!] Failed to connect to MinIO: {e}")


QUEUE_NAME = "jpoint_parse_queue"
print(f"[*] Worker Node booted up. Listening to '{QUEUE_NAME}'...")


def fetch_git_blame(class_name):
    """ searches github for the class file and last commit"""
    print(f"[*] Investigating Git history for class: {class_name}")

    try:
        search_query = f"filename:{class_name}.java repo:{GITHUB_OWNER}/{GITHUB_REPO}"
        search_url = f"https://api.github.com/search/code?q={search_query}"

        search_res = requests.get(search_url, headers=GITHUB_HEADERS)
        search_res.raise_for_status()
        search_data = search_res.json()

        if search_data.get("total_count", 0) == 0:
            print(f"[!] Could not find {class_name}.java in the repository.")
            return None

        file_path = search_data["items"][0]["path"]
        print(f"[*] Found file at: {file_path}")

        # get last commit of that file
        commits_url = f"https://api.github.com/repos/{GITHUB_OWNER}/{GITHUB_REPO}/commits"
        commits_res = requests.get(commits_url, headers=GITHUB_HEADERS, params={"path": file_path, "per_page": 1})
        commits_res.raise_for_status()
        commits_data = commits_res.json()

        if not commits_data:
            return None

        latest_commit = commits_data[0]

        return {
            "author": latest_commit["commit"]["author"]["name"],
            "username": latest_commit["author"]["login"] if latest_commit.get("author") else "Unknown",
            "avatarUrl": latest_commit["author"]["avatar_url"] if latest_commit.get("author") else "",
            "commitHash": latest_commit["sha"][:7],
            "commitMessage": latest_commit["commit"]["message"],
            "filePath": file_path
        }

    except Exception as e:
        print(f"[!] Github API Error: {e}")
        return None

while True:
    try:
        # timeout=0 means block indefinitely until a job appears
        result = redis_client.brpop(QUEUE_NAME, timeout=0)
        
        if result:
            queue, message = result
            job = json.loads(message)

            if not job['objectKey'].endswith('.zst'):
                print(f"[*] Ignoring non-dump file: {job['objectKey']}")
                continue
            
            print("\n" + "="*50)
            print(f"New Job Recieved: {job['jobId']}")
            print(f"Trace ID: {job['traceId']}")
            print(f"Target File: {job['bucket']}/{job['objectKey']}")
            print("="*50 + "\n")

            # setup local file path
            compressed_file_path = f"/tmp/{job['jobId']}.hprof.zst"
            raw_hprof_path = f"/tmp/{job['jobId']}.hprof"

            # download from MinIO
            print(f"[*] Downloading {job['objectKey']} from MinIO...")
            minio_client.fget_object(job['bucket'], job['objectKey'], compressed_file_path)

            # stream-decompress the file
            with open(compressed_file_path, 'rb') as compressed_file:
                dctx = zstd.ZstdDecompressor()
                with open(raw_hprof_path, 'wb') as raw_file:
                    dctx.copy_stream(compressed_file, raw_file)

            print(f"[*] Success! Raw dump ready at: {raw_hprof_path}")

            os.remove(compressed_file_path)

            # execute eclipse Mat
            print("[*] Firing up Eclipse MAT Headless Parser...")

            mat_script_path = os.path.join(os.getcwd(), "mat", "ParseHeapDump.sh")

            process = subprocess.run(
                [mat_script_path, raw_hprof_path, "org.eclipse.mat.api:suspects"],
                capture_output=True,
                text=True
            )

            if process.returncode == 0:
                print("[*] MAT Analysis Complete")
                expected_zip_path = raw_hprof_path.replace(".hprof", "_Leak_Suspects.zip")

                print("[*] Unzipping MAT report...")
                extract_dir = raw_hprof_path.replace(".hprof", "_extracted")

                with zipfile.ZipFile(expected_zip_path, 'r') as zip_ref:
                    zip_ref.extractall(extract_dir)

                print(f"[*] Extracted to: {extract_dir}")

                # Parse HTML using beautifulSoup
                print("[*] Parsing MAT HTML report with beautifulsoup...")
                
                # Search for HTML files instead of XML
                html_files = glob.glob(os.path.join(extract_dir, "**", "*.html"), recursive=True)

                suspects_data = []
                
                if html_files:
                    for html_file in html_files:
                        with open(html_file, 'r', encoding='utf-8', errors='ignore') as f:
                            soup = BeautifulSoup(f, 'html.parser')

                            clean_text = soup.get_text(separator=" ", strip=True)

                            matches = re.finditer(r'Problem Suspect \d+(.*?)Keywords(.*?)(?:Details|Table of Contents)', clean_text, re.IGNORECASE | re.DOTALL)

                            for match in matches:

                                raw_description = match.group(1).strip()

                                if "Skip to main content" in raw_description:

                                    parts = raw_description.split("Description")
                                    if(len(parts) > 1):
                                        raw_description = parts[-1].strip()
                                    else:
                                        raw_description = raw_description.split("Problem Suspect")[-1].strip()

                                raw_keywords = match.group(2).strip()

                                classes = [cls for cls in raw_keywords.split() if cls and not cls.startswith('jdk.') and not cls.startswith('sun.')]

                                clean_classes = list(set([c.replace('"', '') for c in classes]))
                                                                    
                                suspects_data.append({
                                    "description": raw_description,
                                    "suspectClasses": clean_classes,
                                    "sourceFile": os.path.basename(html_file)
                                })

                    unique_suspects = []
                    seen_desc = set()
                    for suspect in suspects_data:
                        if suspect['description'] not in seen_desc:
                            seen_desc.add(suspect['description'])
                            unique_suspects.append(suspect)

                    suspects_data = unique_suspects

                    if not suspects_data:
                            print("[!] Scanned all HTML files but couldn't find the \"Problem Suspect\"")
                else:
                    print("[!] No HTML files found either. Something is very wrong.")


                print("[*] Linking Leak Suspects to GitHub Commits...")
                enriched_suspects = []
                
                for suspect in suspects_data:
                    target_blame_class = None
                    for cls in suspect["suspectClasses"]:
                        # CLEANUP: Strip out line numbers (e.g., :27), arrays ([]), primitives, and java internals
                        base_cls = cls.split(".java")[0].split(":")[0]
                        
                        if (
                            base_cls 
                            and not base_cls.startswith("java.") 
                            and not base_cls.startswith("jdk.") 
                            and not base_cls.startswith("sun.")
                            and base_cls not in ["byte", "int", "long", "char", "boolean", "short", "float", "double", "Object", "String"]
                            and not "[]" in base_cls
                            and not "/" in base_cls
                        ):
                            target_blame_class = base_cls.strip()
                            break
                    
                    print(f"[*] Target clean class for Git Blame: {target_blame_class}")
                    git_blame = fetch_git_blame(target_blame_class) if target_blame_class else None
                    
                    enriched_suspects.append({
                        "description": suspect["description"],
                        "suspectClasses": suspect["suspectClasses"],
                        "sourceFile": suspect["sourceFile"],
                        "gitBlame": git_blame
                    })

                # package the json
                final_artifact = {
                    "jobId": job['jobId'],
                    "traceId": job['traceId'],
                    "targetFile": f"{job['bucket']}/{job['objectKey']}",
                    "status": "COMPLETED",
                    "timestamp": job.get('timestamp') or (datetime.utcnow().isoformat() + "Z"),
                    "leakSuspects": enriched_suspects
                }

                print("\n" + "="*25)
                print("Final json artifact generated:")
                print(json.dumps(final_artifact, indent=4))
                print("="*25 + "\n")

                print("[*] Saving structured JSON report back to MinIO...")

                report_key = job['objectKey'].replace('.hprof.zst', '_report.json')

                json_bytes = json.dumps(final_artifact, indent=4).encode('utf-8')
                json_stream = io.BytesIO(json_bytes)

                minio_client.put_object(
                    job['bucket'],
                    report_key,
                    data=json_stream,
                    length=len(json_bytes),
                    content_type='application/json'
                )

                print(f"[*] IR Report permanantly stored at: s3://{job['bucket']}/{report_key}")
                print("[*] Worker node ready for the next job \n")

            else:
                print(f"[!] MAT Analysis Failed, Return Code: {process.returncode}")
                print(f"[!] MAT Error Output:\n{process.stderr}")
            
    except redis.exceptions.ConnectionError:
        print("[!] Lost connection to Redis. Retrying in 2 seconds...")
        import time
        time.sleep(2)
    except Exception as e:
        print(f"[!] An error occurred: {e}")
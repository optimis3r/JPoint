from bs4 import BeautifulSoup
import glob
import os

# We use the exact folder from your latest worker terminal output
target_dir = "/tmp/e5166f9b-a628-4bb8-8e10-78fd9e3ed954_extracted"

if not os.path.exists(target_dir):
    print(f"[!] Directory {target_dir} not found. (If you restarted your PC, just run worker.py again to generate a new folder!)")
else:
    print(f"[*] Scanning MAT HTML files in {target_dir}...\n")
    
    # Check all HTML files in the report
    for html_file in glob.glob(target_dir + "/**/*.html", recursive=True):
        with open(html_file, 'r', encoding='utf-8', errors='ignore') as f:
            soup = BeautifulSoup(f, 'html.parser')
            text = soup.get_text(separator=" ", strip=True)
            
            # MAT always mentions memory sizes using "MB", "bytes", or "occupies"
            if " MB " in text or "occupies" in text or "bytes" in text:
                print("="*60)
                print(f"📄 File: {os.path.basename(html_file)}")
                print("="*60)
                # Print the first 800 characters so we can see the exact wording
                print(text[:800] + "...\n")
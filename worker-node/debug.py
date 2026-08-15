from bs4 import BeautifulSoup
import glob
import os

target_dir = "/tmp/e5166f9b-a628-4bb8-8e10-78fd9e3ed954_extracted"

if not os.path.exists(target_dir):
    print(f"[!] Directory {target_dir} not found.")
else:
    print(f"[*] Scanning MAT HTML files in {target_dir}...\n")
    
    # Parse HTML report
    for html_file in glob.glob(target_dir + "/**/*.html", recursive=True):
        with open(html_file, 'r', encoding='utf-8', errors='ignore') as f:
            soup = BeautifulSoup(f, 'html.parser')
            text = soup.get_text(separator=" ", strip=True)
            
            if " MB " in text or "occupies" in text or "bytes" in text:
                print("="*60)
                print(f"File: {os.path.basename(html_file)}")
                print("="*60)
                print(text[:800] + "...\n")
import os
import shutil
import subprocess

def run_cmd(cmd):
    result = subprocess.run(cmd, shell=True, text=True, capture_output=True)
    return result.returncode, result.stdout, result.stderr

# Paths
upstream_dir = "upstream"
src_dir = "src"
paths_to_sync_always = [
    "core", 
    "lib", 
    "lib-multisrc", 
    "gradle/build-logic", 
    "gradle/kei.versions.toml", 
    "gradle/libs.versions.toml"
]

print("Finding active extensions...")
active_extensions = []
if os.path.exists(src_dir):
    for lang in os.listdir(src_dir):
        lang_path = os.path.join(src_dir, lang)
        if os.path.isdir(lang_path):
            for ext in os.listdir(lang_path):
                ext_path = os.path.join(lang_path, ext)
                if os.path.isdir(ext_path):
                    active_extensions.append((lang, ext))

# Read .syncignore
ignored_paths = set()
if os.path.exists(".syncignore"):
    with open(".syncignore", "r") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#"):
                ignored_paths.add(os.path.normpath(line))

print(f"Loaded ignored paths: {ignored_paths}")
print(f"Found {len(active_extensions)} active extensions in our repository.")

# Copy shared components
for p in paths_to_sync_always:
    normalized_p = os.path.normpath(p)
    if normalized_p in ignored_paths:
        print(f"Skipping ignored shared component: {p}")
        continue
        
    upstream_path = os.path.join(upstream_dir, p)
    if os.path.exists(upstream_path):
        print(f"Syncing shared component: {p}")
        # Make sure parent directory exists if it's a file
        parent_dir = os.path.dirname(p)
        if parent_dir:
            os.makedirs(parent_dir, exist_ok=True)
            
        if os.path.isdir(upstream_path):
            shutil.rmtree(p, ignore_errors=True)
            shutil.copytree(upstream_path, p)
        else:
            if os.path.exists(p):
                os.remove(p)
            shutil.copy2(upstream_path, p)

# Copy active extensions
for lang, ext in active_extensions:
    local_ext_path = os.path.join(src_dir, lang, ext)
    normalized_ext_path = os.path.normpath(local_ext_path)
    if normalized_ext_path in ignored_paths:
        print(f"Skipping ignored extension: {lang}/{ext}")
        continue
        
    upstream_ext_path = os.path.join(upstream_dir, src_dir, lang, ext)
    if os.path.exists(upstream_ext_path):
        print(f"Syncing extension: {lang}/{ext}")
        shutil.rmtree(local_ext_path, ignore_errors=True)
        shutil.copytree(upstream_ext_path, local_ext_path)

# Check git status
code, out, err = run_cmd("git status --porcelain")
if code != 0:
    print("Error running git status:", err)
    exit(1)

if not out.strip():
    print("No changes detected.")
    with open("sync_status.txt", "w") as f:
        f.write("no_changes")
    exit(0)

# Identify which extensions/libraries actually changed
lines = out.strip().split("\n")
changed_extensions = set()
changed_shared = set()

for line in lines:
    parts = line.strip().split(None, 1)
    if len(parts) < 2:
        continue
    filepath = parts[1]
    if filepath.startswith("src/"):
        # src/<lang>/<ext_name>/...
        path_parts = filepath.split("/")
        if len(path_parts) >= 3:
            changed_extensions.add(f"{path_parts[1]}/{path_parts[2]}")
    else:
        for p in paths_to_sync_always:
            if filepath.startswith(p):
                changed_shared.add(p)
                break

print("\nChanges detected:")
if changed_shared:
    print("Shared components changed:", ", ".join(changed_shared))
if changed_extensions:
    print("Extensions changed:", ", ".join(changed_extensions))

# Write details for GH Action steps
with open("sync_status.txt", "w") as f:
    f.write("changes\n")
    f.write(f"Shared components: {', '.join(changed_shared)}\n")
    f.write(f"Extensions: {', '.join(changed_extensions)}\n")

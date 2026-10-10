#!/bin/sh
# Regenerate data + strings and copy into both apps.
set -e
cd "$(dirname "$0")"
python3 build_data.py --check
python3 strings.py
cp out/materials.json out/sections.json ../ios/WeightKit/Resources/
cp out/materials.json out/sections.json ../android/app/src/main/assets/
echo "synced"

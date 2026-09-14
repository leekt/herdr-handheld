#!/bin/sh
set -eu
PDX_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$PDX_ROOT/terminal-assets"
npm ci --ignore-scripts
npm run build
npm test
cd "$PDX_ROOT"
./scripts/env.sh :app:assembleDebug
python3 - <<'PY'
from pathlib import Path
import hashlib
import shutil

source = Path('app/build/outputs/apk/debug/app-debug.apk')
destination = Path('artifacts/pdx-debug.apk')
destination.parent.mkdir(parents=True, exist_ok=True)
shutil.copyfile(source, destination)
digest = hashlib.sha256(destination.read_bytes()).hexdigest()
checksum = destination.with_suffix('.apk.sha256')
checksum.write_text(f'{digest}  {destination.name}\n')
print(f'Packaged {destination} ({destination.stat().st_size} bytes)')
print(f'SHA-256: {digest}')
PY

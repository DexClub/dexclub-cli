#!/bin/sh
""":"
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
exec bash "${SCRIPT_DIR}/run_latest_release_impl.sh" "$@"
":"""
import os
import sys

script = os.path.basename(__file__)
print(
    f"{script} is a shell script. Run it with `bash {script} ...`, not `python3 {script}`.",
    file=sys.stderr,
)
sys.exit(1)

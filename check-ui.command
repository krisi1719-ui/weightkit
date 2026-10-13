#!/bin/bash
# Only the export check (no AAB build).
cd "$(dirname "$0")"
./check-pdf.command --no-build

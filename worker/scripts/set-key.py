#!/usr/bin/env python3
"""Enter the OpenRouter key locally without echoing it or placing it in command arguments."""
import getpass
import os
from pathlib import Path

key = getpass.getpass("OpenRouter API key (hidden): ").strip()
if not key.startswith("sk-or-") or any(char.isspace() for char in key):
    raise SystemExit("That does not look like an OpenRouter API key. No file was written.")
path = Path(__file__).resolve().parent.parent / ".dev.vars"
descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
os.fchmod(descriptor, 0o600)
with os.fdopen(descriptor, "w") as file:
    file.write("OPENROUTER_API_KEY=" + key + "\n")
print("Key saved privately in worker/.dev.vars (git-ignored). Ready to deploy.")

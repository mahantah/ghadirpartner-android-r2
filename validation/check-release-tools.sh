#!/usr/bin/env bash
set -eu
python -m ruff check --config release_tools/pyproject.toml release_tools tests/release_tools
python -m ruff format --check --config release_tools/pyproject.toml release_tools tests/release_tools
python -m mypy --config-file release_tools/pyproject.toml release_tools tests/release_tools
python -m unittest discover -s tests/release_tools -v

#!/bin/bash
# setupANDrun.sh
# Initializes the virtual environment, installs dependencies,
# and runs all Python scripts in the project.
# TFE26-093. Jérôme Lechat, UCLouvain, 2025-2026.

set -e  # stop on first error

# Initializing the Python environment
if [ ! -d "venv" ]; then
    echo "[1/3] Creating virtual environment..."
    python3 -m venv venv
else
    echo "[1/3] Virtual environment already exists, skipping."
fi

# Installing the dependencies
echo "[2/3] Installing dependencies..."
source venv/bin/activate
pip install --quiet --upgrade pip
pip install --quiet pandas numpy matplotlib seaborn

# Running the scripts
echo "[3/3] Running scripts..."

for script in *.py; do
    echo "  > Running $script..."
    python "$script"
done

echo "Done."
import re
import matplotlib.pyplot as plt
import numpy as np
import os

"""
Win rate against Random agent plotter for Ludii tournament results.
Compares each agent's win rate against Random across Tournament 1 and 2,
highlighting the effect of the determinization layer.

This script was developed with the assistance of Claude AI.
By Jérôme Lechat, UCLouvain, 2025-2026.

"""

# INPUTs
T1_FILE = "results/T1/results.txt"
T2_FILE = "results/T2/results.txt"

# SETTINGs
RANDOM_NAME = "Random"

# OUTPUTs
OUTPUT_DIR  = "figs"
os.makedirs(OUTPUT_DIR, exist_ok=True)

# === FUNCTIONs ===

# Parser
def parse_vs_random(filepath, random_name):
    results = {}
    with open(filepath, "r") as f:
        content = f.read()

    pattern = r"(\S[\S ]*?) vs (\S[\S ]*?) \(\d+ games\) : \S+=([0-9.]+)% \| \S+=([0-9.]+)%"
    for match in re.finditer(pattern, content):
        a, b = match.group(1).strip(), match.group(2).strip()
        wr_a = float(match.group(3))
        wr_b = float(match.group(4))

        if a == random_name and b != random_name:
            results[b] = wr_b
        elif b == random_name and a != random_name:
            results[a] = wr_a

    return results


# Loader
t1_data = parse_vs_random(T1_FILE, RANDOM_NAME)
t2_data = parse_vs_random(T2_FILE, RANDOM_NAME)

print(f"T1: {t1_data}")
print(f"T2: {t2_data}")

# T1 & T2 Plotter (separated)
def plot_single(data, title, filename, color):
    agents = list(data.keys())
    values = list(data.values())

    fig, ax = plt.subplots(figsize=(8, 5))
    bars = ax.bar(agents, values, color=color, edgecolor="white", width=0.6)

    for bar, val in zip(bars, values):
        ax.text(
            bar.get_x() + bar.get_width() / 2,
            bar.get_height() + 1,
            f"{val:.0f}%",
            ha="center", va="bottom", fontsize=9
        )

    ax.set_ylim(0, 115)
    ax.set_ylabel("Win rate against Random (%)", fontsize=11)
    ax.set_title(title, fontsize=12)
    ax.axhline(50, color="black", linestyle="--", linewidth=0.8, alpha=0.4)
    ax.grid(axis="y", linestyle="--", alpha=0.3)
    plt.tight_layout()

    path = os.path.join(OUTPUT_DIR, filename)
    fig.savefig(path, dpi=150, bbox_inches="tight")
    plt.close(fig)
    print(f"Saved: {path}")


plot_single(t1_data,
            "Win rate against Random - Tournament 1 (native agents)",
            "winrate_vs_random_t1.pdf",
            "#d62728")

plot_single(t2_data,
            "Win rate against Random - Tournament 2 (determinized agents)",
            "winrate_vs_random_t2.pdf",
            "#1f77b4")

print("\nDone.")
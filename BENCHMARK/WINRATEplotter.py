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
AGENT_MAPPING = {
    "UCT": "UCT+Det",
    "MAST": "MAST+Det",
    "FlatMC": "FlatMC+Det",
}
AGENTS_TO_PLOT = ["UCT", "MAST", "FlatMC"]
RANDOM_NAME = "Random"

# OUTPUTs
OUTPUT_DIR  = "figs"
os.makedirs(OUTPUT_DIR, exist_ok=True)

# === FUNCTIONs ===

# Parser
def parse_vs_random(filepath, random_name):

    """
    Returns dict {agent: win rate against Random %}

    """

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

t1_values = [t1_data.get(a, 0) for a in AGENTS_TO_PLOT]
t2_values = [t2_data.get(AGENT_MAPPING[a], 0) for a in AGENTS_TO_PLOT]
labels = [f"{a}\nvs\n{AGENT_MAPPING[a]}" for a in AGENTS_TO_PLOT]

# Plotter
fig, ax = plt.subplots(figsize=(8, 5))
x = np.arange(len(AGENTS_TO_PLOT))
width = 0.35

bars1 = ax.bar(x - width/2, t1_values, width=width,
               label="Tournament 1 - native (leakage)",
               color="#d62728", edgecolor="white")
bars2 = ax.bar(x + width/2, t2_values, width=width,
               label="Tournament 2 - determinized",
               color="#1f77b4", edgecolor="white")

for bar, val in zip(list(bars1) + list(bars2), t1_values + t2_values):
    ax.text(
        bar.get_x() + bar.get_width() / 2,
        bar.get_height() + 1,
        f"{val:.0f}%",
        ha="center", va="bottom", fontsize=9
    )

ax.set_xticks(x)
ax.set_xticklabels(labels, fontsize=10)
ax.set_ylim(0, 115)
ax.set_ylabel("Win rate against Random (%)", fontsize=11)
ax.set_title("Win rate against Random agent\nNative (leakage) vs Determinized",
             fontsize=12)
ax.axhline(50, color="black", linestyle="--", linewidth=0.8, alpha=0.4)
ax.legend(fontsize=9)
ax.grid(axis="y", linestyle="--", alpha=0.3)
plt.tight_layout()

path = os.path.join(OUTPUT_DIR, "winrate_vs_random.pdf")
fig.savefig(path, dpi=150, bbox_inches="tight")
plt.close(fig)
print(f"\nSaved: {path}")

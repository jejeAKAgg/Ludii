import re
import numpy as np
import matplotlib.pyplot as plt
import seaborn as sns
import pandas as pd
import os

"""
Ludii tournament results plotter.
Reads tournament result files and generates:
  - Bar charts of win rates per agent
  - Head-to-head heatmaps

This script was developed with the assistance of Claude AI.
By Jérôme Lechat, UCLouvain, 2025-2026.

"""

# INPUTs
TXT_FILES = {
    "Tournament 1 (native agents)": "results/T1/results.txt",
    "Tournament 2 (determinized agents)": "results/T2/results.txt",
}

# OUTPUTs
OUTPUT_DIR = "figs"
os.makedirs(OUTPUT_DIR, exist_ok=True)

# === FUNCTIONs ===

# Parser
def parse_tournament(filepath):
    
    """
    Parses lines like:
    '  Random vs UCT (200 games) : Random=0.5% | UCT=99.5%'
    Returns:
      - agents: list of agent names in order
      - winrates: dict {agent: aggregate win rate %}
      - matrix: DataFrame of head-to-head win rates
    
    """
    
    with open(filepath, "r") as f:
        content = f.read()

    # Parse pairwise results
    pairwise = {}
    pattern = r"(\S[\S ]*?) vs (\S[\S ]*?) \(\d+ games\) : \S+=([0-9.]+)% \| \S+=([0-9.]+)%"
    for match in re.finditer(pattern, content):
        a = match.group(1).strip()
        b = match.group(2).strip()
        wr_a = float(match.group(3))
        wr_b = float(match.group(4))
        pairwise[(a, b)] = wr_a
        pairwise[(b, a)] = wr_b

    # Parse ranking
    winrates = {}
    agents = []
    rank_pattern = r"(\S[\S+]*?)\s+: \d+/\d+ \(([0-9.]+)%\)"
    for match in re.finditer(rank_pattern, content):
        agent = match.group(1).strip()
        wr = float(match.group(2))
        winrates[agent] = wr
        agents.append(agent)

    # Build head-to-head matrix
    matrix = pd.DataFrame(np.nan, index=agents, columns=agents)
    for (a, b), wr in pairwise.items():
        if a in agents and b in agents:
            matrix.loc[a, b] = wr

    # Sort by win rate descending
    agents_sorted = sorted(agents, key=lambda a: winrates.get(a, 0), reverse=True)
    matrix = matrix.loc[agents_sorted, agents_sorted]
    winrates_sorted = {a: winrates[a] for a in agents_sorted}

    return agents_sorted, winrates_sorted, matrix


# Bar Chart Plots
def plot_winrates(agents, winrates, title, filename):
    colors = ["#1f77b4", "#ff7f0e", "#2ca02c", "#9467bd", "#7f7f7f",
              "#d62728", "#8c564b"]

    fig, ax = plt.subplots(figsize=(8, 5))
    values = [winrates[a] for a in agents]

    bars = ax.bar(agents, values,
                  color=colors[:len(agents)],
                  edgecolor="white", width=0.6)

    for bar, val in zip(bars, values):
        ax.text(
            bar.get_x() + bar.get_width() / 2,
            bar.get_height() + 1,
            f"{val:.1f}%",
            ha="center", va="bottom", fontsize=9
        )

    ax.set_ylim(0, 110)
    ax.set_ylabel("Win rate (%)", fontsize=11)
    ax.set_title(title, fontsize=12)
    ax.axhline(50, color="black", linestyle="--", linewidth=0.8, alpha=0.5)
    ax.grid(axis="y", linestyle="--", alpha=0.3)
    plt.tight_layout()

    path = os.path.join(OUTPUT_DIR, filename)
    fig.savefig(path, dpi=150, bbox_inches="tight")
    plt.close(fig)
    print(f"Saved: {path}")


# Heatmap Plots
def plot_heatmap(matrix, title, filename):
    annot = matrix.copy().astype(object)
    for r in matrix.index:
        for c in matrix.columns:
            v = matrix.loc[r, c]
            annot.loc[r, c] = "—" if pd.isna(v) else f"{v:.0f}%"

    fig, ax = plt.subplots(figsize=(7, 5))
    sns.heatmap(
        matrix,
        annot=annot,
        fmt="",
        cmap="RdBu_r",
        vmin=0, vmax=100,
        linewidths=0.5,
        linecolor="white",
        cbar_kws={"label": "Win rate (%)"},
        ax=ax,
        mask=matrix.isna(),
    )
    ax.set_title(title, fontsize=13, pad=12)
    ax.set_xlabel("Opponent", fontsize=10)
    ax.set_ylabel("Agent (row wins against col)", fontsize=10)
    ax.tick_params(axis="both", labelsize=9)
    plt.tight_layout()

    path = os.path.join(OUTPUT_DIR, filename)
    fig.savefig(path, dpi=150, bbox_inches="tight")
    plt.close(fig)
    print(f"Saved: {path}")


# === MAIN ===
for title, filepath in TXT_FILES.items():
    print(f"\nParsing: {filepath}")
    agents, winrates, matrix = parse_tournament(filepath)
    print(f"  Agents: {agents}")
    print(f"  Win rates: {winrates}")

    safe = title.lower().replace(" ", "_").replace("(", "").replace(")", "").replace("/", "")
    plot_winrates(agents, winrates, title, f"winrate_{safe}.pdf")
    plot_heatmap(matrix, f"Head-to-head - {title}", f"heatmap_{safe}.pdf")

print("\nDone.")
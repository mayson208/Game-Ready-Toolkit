# ⚡ Game Ready Toolkit

A Windows desktop app that optimizes your PC for gaming in one click — retro 80s synthwave UI, per-game profiles, and research-backed tweaks.

![Game Ready Toolkit Screenshot](screenshot.png)

---

## Features

- **One-click system optimization** — power plan, CPU scheduling, network tweaks, service management, and more
- **Game profiles** — per-game settings for Rainbow Six Siege X, Valorant, Apex Legends, CS2, Fortnite, Warzone, and Overwatch 2
- **Rainbow Six Siege X deep optimization** — DX12 stutter fix, MaxGPUBufferedFrames, CPU affinity fix, Ubisoft overlay disable, audio pipeline optimization, shader cache, and more
- **Status indicators** — green dot = already applied, grey = not yet applied, updates after every run
- **Debug log tab** — full PowerShell output for every action so you can see exactly what ran and what failed
- **Live system stats** — CPU % and RAM usage in the header, updates every 2 seconds
- **Session timer** — tracks how long you've been gaming after optimizations run
- **Restore mode** — undo all optimizations and return your PC to normal after gaming
- **Retro 80s synthwave UI** — neon pink, cyan, and yellow on deep purple/black with glowing borders

---

## Optimization Categories

| Category | What it does |
|---|---|
| **Power** | Enables Ultimate Performance power plan |
| **CPU** | Win32PrioritySeparation, Paging Executive, game process priority |
| **Display** | HAGS, GPU high performance mode, mouse acceleration off |
| **Network** | Nagle's algorithm, Cloudflare DNS, network throttling *(use at your own risk)* |
| **Services** | Kills Xbox Game Bar, SysMain, DiagTrack, Windows Search, Windows Update |
| **Memory** | RAM working set clear, temp file cleanup |
| **Game** | Per-game specific tweaks (R6 Siege X, Valorant, Apex, CS2, Fortnite, Warzone, OW2) |

---

## Requirements

- **Windows 10 / 11** (64-bit)
- **Java 21+** — [Download from Adoptium](https://adoptium.net/)
- **Administrator privileges** — required to apply system registry and service tweaks

---

## Installation & Run

### Option 1 — Download Release (recommended)
1. Go to [Releases](../../releases) and download `GameReadyToolkit.jar` and `launch.bat`
2. Put both files in the same folder
3. Right-click `launch.bat` → **Run as administrator**

### Option 2 — Build from source
```bash
git clone https://github.com/mayson208/Game-Ready-Toolkit.git
cd Game-Ready-Toolkit
mvn package
java -jar target/GameReadyToolkit.jar
```
Requires Maven 3.6+ and Java 21+.

---

## Disclaimer

Some optimizations modify system registry keys and stop Windows services. All changes are reversible using **Restore Mode** or by rebooting. Network tweaks are labeled **"Use at your own risk"** — test on your own setup before using competitively.

---

## Built With

- Java 21 + JavaFX 21
- Maven

---

*Built by [mayson208](https://github.com/mayson208)*

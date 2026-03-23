# Game Ready Toolkit

A JavaFX desktop app that prepares your Windows PC for gaming with one click — and restores normal settings when you're done.

![Java](https://img.shields.io/badge/Java-21-blue) ![JavaFX](https://img.shields.io/badge/JavaFX-21-orange) ![Maven](https://img.shields.io/badge/Maven-build-red)

## Features

### Prepare Mode
Select and apply optimisations before a gaming session:

| Action | What It Does |
|--------|-------------|
| High Performance Power Plan | Maximises CPU/GPU clock speeds |
| Enable Windows Game Mode | Dedicates more resources to the active game |
| Set GPU to High Performance | Writes Windows graphics preference to registry |
| Silence Notifications | Blocks popups via Focus Assist registry key |
| Stop Xbox Game Bar | Kills overlay process to reduce CPU/GPU overhead |
| Disable Mouse Acceleration | Raw linear input — recommended for FPS games |
| Flush DNS Cache | Clears stale DNS for better matchmaking latency |
| Clear Temp Files | Frees disk space and reduces background I/O |
| Pause Windows Update | Stops wuauserv from stealing bandwidth |
| Stop Windows Search Indexer | Pauses disk indexing during gameplay |
| Pause OneDrive Sync | Frees bandwidth and CPU from background uploads |
| Check Display Refresh Rate | Reports current and max monitor refresh rate |

### Restore Mode
Undo all optimisations after your session — matching restore action for every prepare action.

### Quality of Life
- **Admin warning banner** — orange alert if the app isn't running as Administrator, so you know which actions may fail
- **Session timer** — live `HH:MM:SS` counter starts after a successful Prepare run
- **Select All / Deselect All** — bulk-check the entire list in one click
- **Persistent selections** — checkbox state remembered via Java Preferences across restarts
- **Preview dialog** — shows exactly what will run before you confirm
- **Scrollable checklist** — handles any number of actions without overflow
- **Detailed results dialog** — monospace TextArea with per-action pass/fail and command output
- **30-second process timeout** — runaway processes are killed automatically
- **Background threading** — UI stays responsive during execution (JavaFX `Task<>`)

## Running

```bash
# Run directly with Maven
mvn javafx:run

# Or build a JAR first
mvn package
```

> **Tip:** Right-click and run as Administrator to enable all actions, especially service control and registry writes.

## Requirements

- Java 21+
- Maven 3.9+
- Windows 10/11

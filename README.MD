# Game Ready Toolkit

A JavaFX desktop app that prepares your Windows PC for gaming with one click — and restores normal settings when you're done.

![Java](https://img.shields.io/badge/Java-21-blue) ![JavaFX](https://img.shields.io/badge/JavaFX-21-orange) ![Maven](https://img.shields.io/badge/Maven-build-red)

## Features

- **Prepare Mode** — select and apply system optimisations before a gaming session:
  - Enable High Performance power plan
  - Silence notifications (Focus Assist)
  - Flush DNS cache
  - Clear temp files
  - Pause Windows Update
  - Check display refresh rate

- **Restore Mode** — undo the optimisations after your session:
  - Restore Balanced power plan
  - Re-enable notifications
  - Restart Windows Update
  - Flush DNS

- **Persistent checkbox selections** — remembered via Java Preferences across restarts
- **Preview dialog** before running so you know exactly what will execute
- **Background threading** with a live progress bar
- **Per-action results dialog** showing pass/fail and command output for each action
- **Tooltip descriptions** on every checkbox

## How It Works

Actions run via `ProcessBuilder` and PowerShell. No third-party tools required. All actions are transparent and reversible.

## Running

```bash
mvn javafx:run
```

## Requirements

- Java 21+
- Maven 3.9+
- Windows 10/11

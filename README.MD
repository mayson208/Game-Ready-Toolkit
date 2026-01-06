# Game Ready Toolkit

Game Ready Toolkit is an AI-assisted JavaFX desktop application I’m building to help prepare a Windows system for a gaming session **before launching a game**.

The focus of this project is on safe, transparent, and user-controlled preparation steps that improve focus and system readiness without interacting with games or anti-cheat systems.

This is an actively evolving project and will continue to grow as new features are designed and tested.

---

## Project Goal

I wanted to create a tool that acts as a **pre-game assistant**, not a background optimizer.

Instead of modifying games or running during gameplay, the application:
- Analyzes basic system state
- Explains potential preparation steps
- Lets the user decide what actions to take
- Exits before the game starts

Safety and clarity are prioritized over automation.

---

## Current State

- JavaFX desktop application framework
- Basic preparation action structure
- Explicit pre-launch scope
- Clear separation between analysis and execution

---

## Planned Features

- Pre-game preparation checklist
- Power plan and notification awareness
- Background application awareness
- Session readiness summary
- Dark mode by default
- AI-assisted explanations and recommendations

---

## AI-Assisted Design

AI is used to help generate explanations and recommendations, but all system interactions and safety constraints are explicitly controlled by the application.

The tool does not:
- Interact with running games
- Inspect or modify game memory
- Run persistently in the background

---

## Running the Application

```bash
mvn clean javafx:run

Hope you Enjoy!  
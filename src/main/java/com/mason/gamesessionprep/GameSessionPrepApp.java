package com.mason.gamesessionprep;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

public class GameSessionPrepApp extends Application {

    private static final Preferences PREFS =
            Preferences.userRoot().node("com/mason/gamesessionprep");

    private final List<PrepAction> prepActions    = new ArrayList<>();
    private final List<PrepAction> restoreActions = new ArrayList<>();

    private boolean restoreMode = false;

    // Checklist container — instance var so Select All/None can refresh it
    private VBox checklistBox;

    // Session timer state
    private long sessionStartMs = -1;
    private Thread sessionTimerThread;

    // ── Admin elevation check ─────────────────────────────────────────────────

    private static boolean isElevated() {
        try {
            ProcessBuilder pb = new ProcessBuilder("net", "session");
            pb.redirectErrorStream(true);
            return pb.start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    // ── Application entry point ───────────────────────────────────────────────

    @Override
    public void start(Stage stage) {
        boolean elevated = isElevated();

        // ── Prepare actions ───────────────────────────────────────────────────
        prepActions.add(new PrepAction(
                "High Performance Power Plan",
                "Prioritize system performance over power savings",
                () -> runPowerShell("powercfg /setactive 8c5e7fda-e8bf-4a96-9a85-a6e23a8c635c")
        ));
        prepActions.add(new PrepAction(
                "Enable Windows Game Mode",
                "Dedicates more CPU and GPU resources to the active game",
                () -> runPowerShell(
                    "if (-not (Test-Path 'HKCU:\\Software\\Microsoft\\GameBar')) {" +
                    "  New-Item -Path 'HKCU:\\Software\\Microsoft\\GameBar' -Force | Out-Null };" +
                    "Set-ItemProperty -Path 'HKCU:\\Software\\Microsoft\\GameBar' " +
                    "  -Name 'AutoGameModeEnabled' -Value 1 -Type DWord -Force;" +
                    "Write-Output 'Windows Game Mode enabled'")
        ));
        prepActions.add(new PrepAction(
                "Set GPU to High Performance",
                "Configure Windows graphics settings to prefer the high-performance GPU for all apps",
                () -> runPowerShell(
                    "$p = 'HKCU:\\Software\\Microsoft\\DirectX\\UserGpuPreferences';" +
                    "if (-not (Test-Path $p)) { New-Item -Path $p -Force | Out-Null };" +
                    "Set-ItemProperty -Path $p -Name 'DirectXUserGlobalSettings' " +
                    "  -Value 'VRROptimizeEnable=0;' -Type String -Force;" +
                    "Write-Output 'GPU preference set to High Performance'")
        ));
        prepActions.add(new PrepAction(
                "Silence Notifications (Focus Assist)",
                "Block popup notifications and distractions during gameplay",
                () -> runPowerShell(
                    "Set-ItemProperty " +
                    "  -Path 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Notifications\\Settings' " +
                    "  -Name 'NOC_GLOBAL_SETTING_TOASTS_ENABLED' -Value 0 -Force")
        ));
        prepActions.add(new PrepAction(
                "Stop Xbox Game Bar",
                "Kill the Xbox Game Bar overlay — reduces CPU/GPU overhead and input lag",
                () -> runPowerShell(
                    "Stop-Process -Name GameBar -Force -ErrorAction SilentlyContinue;" +
                    "Stop-Process -Name GameBarFTServer -Force -ErrorAction SilentlyContinue;" +
                    "Write-Output 'Xbox Game Bar stopped'")
        ));
        prepActions.add(new PrepAction(
                "Disable Mouse Acceleration",
                "Turn off Enhanced Pointer Precision for raw, linear mouse input (recommended for FPS)",
                () -> runPowerShell(
                    "Set-ItemProperty -Path 'HKCU:\\Control Panel\\Mouse' -Name 'MouseSpeed' -Value 0 -Force;" +
                    "Set-ItemProperty -Path 'HKCU:\\Control Panel\\Mouse' -Name 'MouseThreshold1' -Value 0 -Force;" +
                    "Set-ItemProperty -Path 'HKCU:\\Control Panel\\Mouse' -Name 'MouseThreshold2' -Value 0 -Force;" +
                    "Write-Output 'Mouse acceleration disabled'")
        ));
        prepActions.add(new PrepAction(
                "Flush DNS Cache",
                "Clear stale DNS entries for better matchmaking and connection latency",
                () -> runCommand("ipconfig", "/flushdns")
        ));
        prepActions.add(new PrepAction(
                "Clear Temp Files",
                "Remove temporary files to free disk space and reduce background I/O",
                () -> runPowerShell(
                    "Remove-Item -Path $env:TEMP\\* -Recurse -Force -ErrorAction SilentlyContinue;" +
                    "Write-Output 'Temp files cleared'")
        ));
        prepActions.add(new PrepAction(
                "Pause Windows Update",
                "Stop Windows Update from consuming bandwidth and CPU during your session",
                () -> runPowerShell("Stop-Service -Name wuauserv -Force -ErrorAction SilentlyContinue")
        ));
        prepActions.add(new PrepAction(
                "Stop Windows Search Indexer",
                "Pause background disk indexing to reduce I/O load during gaming",
                () -> runPowerShell(
                    "Stop-Service -Name WSearch -Force -ErrorAction SilentlyContinue;" +
                    "Write-Output 'Windows Search indexer stopped'")
        ));
        prepActions.add(new PrepAction(
                "Pause OneDrive Sync",
                "Pause OneDrive background uploads to free bandwidth and CPU",
                () -> runPowerShell(
                    "$od = Get-Process -Name OneDrive -ErrorAction SilentlyContinue;" +
                    "if ($od) {" +
                    "  & \"$env:LOCALAPPDATA\\Microsoft\\OneDrive\\OneDrive.exe\" /pause;" +
                    "  Write-Output 'OneDrive sync paused'" +
                    "} else { Write-Output 'OneDrive not running' }")
        ));
        prepActions.add(new PrepAction(
                "Check Display Refresh Rate",
                "Report current and maximum monitor refresh rate",
                () -> runPowerShell(
                    "$d = Get-CimInstance Win32_VideoController;" +
                    "Write-Output \"Refresh: $($d.CurrentRefreshRate)Hz / Max: $($d.MaxRefreshRate)Hz\"")
        ));

        // ── Restore actions ───────────────────────────────────────────────────
        restoreActions.add(new PrepAction(
                "Restore Balanced Power Plan",
                "Return power settings to the default balanced profile",
                () -> runPowerShell("powercfg /setactive 381b4222-f694-41f0-9685-ff5bb260df2e")
        ));
        restoreActions.add(new PrepAction(
                "Restore Default GPU Preference",
                "Remove the High Performance GPU override from Windows graphics settings",
                () -> runPowerShell(
                    "$p = 'HKCU:\\Software\\Microsoft\\DirectX\\UserGpuPreferences';" +
                    "if (Test-Path $p) {" +
                    "  Remove-ItemProperty -Path $p -Name 'DirectXUserGlobalSettings' -ErrorAction SilentlyContinue };" +
                    "Write-Output 'GPU preference restored to default'")
        ));
        restoreActions.add(new PrepAction(
                "Re-enable Notifications",
                "Turn toast notifications back on after your session",
                () -> runPowerShell(
                    "Set-ItemProperty " +
                    "  -Path 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Notifications\\Settings' " +
                    "  -Name 'NOC_GLOBAL_SETTING_TOASTS_ENABLED' -Value 1 -Force")
        ));
        restoreActions.add(new PrepAction(
                "Re-enable Mouse Acceleration",
                "Restore the default Windows pointer precision settings",
                () -> runPowerShell(
                    "Set-ItemProperty -Path 'HKCU:\\Control Panel\\Mouse' -Name 'MouseSpeed' -Value 1 -Force;" +
                    "Set-ItemProperty -Path 'HKCU:\\Control Panel\\Mouse' -Name 'MouseThreshold1' -Value 6 -Force;" +
                    "Set-ItemProperty -Path 'HKCU:\\Control Panel\\Mouse' -Name 'MouseThreshold2' -Value 10 -Force;" +
                    "Write-Output 'Mouse acceleration restored'")
        ));
        restoreActions.add(new PrepAction(
                "Re-enable Windows Update",
                "Allow Windows Update to resume normal operation",
                () -> runPowerShell("Start-Service -Name wuauserv -ErrorAction SilentlyContinue")
        ));
        restoreActions.add(new PrepAction(
                "Restart Windows Search Indexer",
                "Resume background disk indexing",
                () -> runPowerShell(
                    "Start-Service -Name WSearch -ErrorAction SilentlyContinue;" +
                    "Write-Output 'Windows Search indexer restarted'")
        ));
        restoreActions.add(new PrepAction(
                "Resume OneDrive Sync",
                "Resume OneDrive background synchronisation",
                () -> runPowerShell(
                    "$od = Get-Process -Name OneDrive -ErrorAction SilentlyContinue;" +
                    "if ($od) {" +
                    "  & \"$env:LOCALAPPDATA\\Microsoft\\OneDrive\\OneDrive.exe\" /resume;" +
                    "  Write-Output 'OneDrive sync resumed'" +
                    "} else {" +
                    "  & \"$env:LOCALAPPDATA\\Microsoft\\OneDrive\\OneDrive.exe\";" +
                    "  Write-Output 'OneDrive started' }")
        ));
        restoreActions.add(new PrepAction(
                "Flush DNS Cache",
                "Clear any stale DNS entries accumulated during the session",
                () -> runCommand("ipconfig", "/flushdns")
        ));

        // Load saved checkbox preferences
        loadPreferences(prepActions,    "prep_");
        loadPreferences(restoreActions, "restore_");

        // ── Build UI ──────────────────────────────────────────────────────────

        // Admin warning banner (only shown when not elevated)
        Label adminBanner = null;
        if (!elevated) {
            adminBanner = new Label(
                "⚠  Not running as Administrator — some actions may fail. " +
                "Right-click the app and choose 'Run as administrator'.");
            adminBanner.setWrapText(true);
            adminBanner.setStyle(
                "-fx-background-color: #3a2000; -fx-border-color: #f39c12; " +
                "-fx-border-radius: 4; -fx-background-radius: 4; " +
                "-fx-text-fill: #f39c12; -fx-font-size: 12px; -fx-padding: 8 12;");
            adminBanner.setMaxWidth(Double.MAX_VALUE);
        }

        Label titleLabel    = new Label("Game Ready Toolkit");
        Label subtitleLabel = new Label("Select optimisations to apply");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #e8e8e8;");
        subtitleLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 13px;");

        // Session timer — shown after Prepare runs
        Label timerLabel = new Label();
        timerLabel.setStyle("-fx-text-fill: #2ecc71; -fx-font-size: 12px; -fx-font-weight: bold;");
        timerLabel.setVisible(false);

        Region titleSpacer = new Region();
        HBox.setHgrow(titleSpacer, Priority.ALWAYS);
        HBox headerRow = new HBox(titleLabel, titleSpacer, timerLabel);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        // Select All / Deselect All
        String smallBtn = "-fx-background-color: #252525; -fx-text-fill: #aaa; " +
                          "-fx-border-color: #363636; -fx-border-radius: 4; -fx-background-radius: 4; " +
                          "-fx-padding: 3 10; -fx-cursor: hand; -fx-font-size: 11px;";
        Button selectAllBtn   = new Button("Select All");
        Button deselectAllBtn = new Button("Deselect All");
        selectAllBtn.setStyle(smallBtn);
        deselectAllBtn.setStyle(smallBtn);
        selectAllBtn.setOnAction(e   -> setAllSelected(true));
        deselectAllBtn.setOnAction(e -> setAllSelected(false));

        HBox selectRow = new HBox(8, selectAllBtn, deselectAllBtn);
        selectRow.setAlignment(Pos.CENTER_LEFT);

        // Scrollable checklist
        checklistBox = new VBox(10);
        checklistBox.setStyle("-fx-background-color: #1a1a1a;");
        checklistBox.setPadding(new Insets(2, 4, 2, 4));
        buildChecklist(checklistBox, prepActions, "prep_");

        ScrollPane scrollPane = new ScrollPane(checklistBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(260);
        scrollPane.setStyle(
            "-fx-background: #1a1a1a; -fx-background-color: #1a1a1a; " +
            "-fx-border-color: #2a2a2a; -fx-border-radius: 4;");

        // Status and progress
        Label statusLabel = new Label();
        statusLabel.setStyle("-fx-text-fill: #aaa; -fx-font-size: 12px;");
        statusLabel.setWrapText(true);

        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(Double.MAX_VALUE);
        progressBar.setVisible(false);

        // Main buttons
        String btnStyle = "-fx-background-color: #2a2a2a; -fx-text-fill: #e0e0e0; " +
                          "-fx-border-color: #444; -fx-border-radius: 6; -fx-background-radius: 6; " +
                          "-fx-padding: 7 18; -fx-cursor: hand; -fx-font-size: 13px;";

        Button runButton     = new Button("Prepare System");
        Button modeToggleBtn = new Button("Switch to Restore Mode");
        Button exitButton    = new Button("Exit");

        for (Button b : new Button[]{runButton, modeToggleBtn, exitButton}) b.setStyle(btnStyle);
        modeToggleBtn.setStyle(btnStyle.replace("#2a2a2a", "#1a3a1a").replace("#444", "#2a5a2a"));

        runButton.setOnAction(e ->
            showPreview(stage, statusLabel, progressBar, runButton, timerLabel));

        modeToggleBtn.setOnAction(e -> {
            restoreMode = !restoreMode;
            if (restoreMode) {
                subtitleLabel.setText("Select restore actions to undo optimisations");
                modeToggleBtn.setText("Switch to Prepare Mode");
                modeToggleBtn.setStyle(btnStyle.replace("#2a2a2a", "#3a1a1a").replace("#444", "#5a2a2a"));
                runButton.setText("Restore System");
                buildChecklist(checklistBox, restoreActions, "restore_");
            } else {
                subtitleLabel.setText("Select optimisations to apply");
                modeToggleBtn.setText("Switch to Restore Mode");
                modeToggleBtn.setStyle(btnStyle.replace("#2a2a2a", "#1a3a1a").replace("#444", "#2a5a2a"));
                runButton.setText("Prepare System");
                buildChecklist(checklistBox, prepActions, "prep_");
            }
            statusLabel.setText("");
            progressBar.setVisible(false);
        });

        exitButton.setOnAction(e -> stage.close());

        HBox buttonRow = new HBox(10, runButton, modeToggleBtn, exitButton);
        buttonRow.setAlignment(Pos.CENTER_LEFT);

        // Assemble root layout
        VBox root = new VBox(12);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #1a1a1a;");

        if (adminBanner != null) root.getChildren().add(adminBanner);
        root.getChildren().addAll(
                headerRow,
                subtitleLabel,
                new Separator(),
                selectRow,
                scrollPane,
                new Separator(),
                buttonRow,
                progressBar,
                statusLabel
        );

        Scene scene = new Scene(root, 530, elevated ? 560 : 610);
        stage.setTitle("Game Ready Toolkit");
        stage.setMinWidth(420);
        stage.setScene(scene);
        stage.show();
    }

    // ── Checklist helpers ─────────────────────────────────────────────────────

    private void buildChecklist(VBox box, List<PrepAction> actions, String prefPrefix) {
        box.getChildren().clear();
        for (PrepAction action : actions) {
            CheckBox cb = new CheckBox(action.getName());
            cb.setSelected(action.isSelected());
            cb.setStyle("-fx-text-fill: #d0d0d0; -fx-font-size: 13px;");

            Tooltip tip = new Tooltip(action.getDescription());
            tip.setStyle("-fx-font-size: 12px;");
            cb.setTooltip(tip);

            cb.selectedProperty().addListener((obs, o, n) -> {
                action.setSelected(n);
                PREFS.putBoolean(prefPrefix + action.getName(), n);
            });
            box.getChildren().add(cb);
        }
    }

    private void setAllSelected(boolean selected) {
        List<PrepAction> active = restoreMode ? restoreActions : prepActions;
        String prefix = restoreMode ? "restore_" : "prep_";
        for (PrepAction a : active) {
            a.setSelected(selected);
            PREFS.putBoolean(prefix + a.getName(), selected);
        }
        checklistBox.getChildren().stream()
                .filter(n -> n instanceof CheckBox)
                .map(n -> (CheckBox) n)
                .forEach(cb -> cb.setSelected(selected));
    }

    private void loadPreferences(List<PrepAction> actions, String prefPrefix) {
        for (PrepAction action : actions) {
            action.setSelected(PREFS.getBoolean(prefPrefix + action.getName(), true));
        }
    }

    // ── Run flow ──────────────────────────────────────────────────────────────

    private void showPreview(Stage owner, Label statusLabel, ProgressBar progressBar,
                             Button runButton, Label timerLabel) {
        List<PrepAction> active   = restoreMode ? restoreActions : prepActions;
        List<PrepAction> selected = active.stream()
                .filter(PrepAction::isSelected)
                .collect(Collectors.toList());

        if (selected.isEmpty()) {
            statusLabel.setStyle("-fx-text-fill: #f39c12; -fx-font-size: 12px;");
            statusLabel.setText("No actions selected.");
            return;
        }

        Alert preview = new Alert(Alert.AlertType.CONFIRMATION);
        preview.initOwner(owner);
        preview.setTitle("Confirm " + (restoreMode ? "Restore" : "Preparation"));
        preview.setHeaderText("The following actions will run:");
        preview.setContentText(selected.stream()
                .map(a -> "  \u2022 " + a.getName())
                .collect(Collectors.joining("\n")));

        ButtonType confirm = new ButtonType("Run Now");
        ButtonType cancel  = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        preview.getButtonTypes().setAll(confirm, cancel);

        preview.showAndWait().ifPresent(result -> {
            if (result == confirm) runActions(selected, statusLabel, progressBar, runButton, timerLabel);
        });
    }

    private void runActions(List<PrepAction> selected, Label statusLabel,
                            ProgressBar progressBar, Button runButton, Label timerLabel) {
        runButton.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(0);

        Task<List<PrepActionResult>> task = new Task<>() {
            @Override
            protected List<PrepActionResult> call() {
                List<PrepActionResult> results = new ArrayList<>();
                for (int i = 0; i < selected.size(); i++) {
                    PrepAction action = selected.get(i);
                    updateProgress(i, selected.size());
                    Platform.runLater(() -> statusLabel.setText("Running: " + action.getName() + "..."));
                    results.add(action.execute());
                }
                return results;
            }
        };

        progressBar.progressProperty().bind(task.progressProperty());

        task.setOnSucceeded(e -> {
            progressBar.progressProperty().unbind();
            progressBar.setProgress(1.0);
            runButton.setDisable(false);

            List<PrepActionResult> results = task.getValue();
            long passed = results.stream().filter(PrepActionResult::isSuccess).count();
            long failed  = results.size() - passed;

            // Start session timer after a successful Prepare run
            if (!restoreMode && failed == 0) {
                startSessionTimer(timerLabel);
            } else if (restoreMode) {
                stopSessionTimer(timerLabel);
            }

            writeLog(results);
            showResultDialog(results);

            if (failed == 0) {
                statusLabel.setStyle("-fx-text-fill: #2ecc71; -fx-font-size: 12px;");
                statusLabel.setText("All " + passed + " actions completed successfully.");
            } else {
                statusLabel.setStyle("-fx-text-fill: #e67e22; -fx-font-size: 12px;");
                statusLabel.setText(passed + " succeeded, " + failed + " failed — check results for details.");
            }
        });

        task.setOnFailed(e -> {
            progressBar.progressProperty().unbind();
            runButton.setDisable(false);
            statusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 12px;");
            statusLabel.setText("Unexpected error: " + task.getException().getMessage());
        });

        Thread t = new Thread(task, "prep-runner");
        t.setDaemon(true);
        t.start();
    }

    // ── Session log ───────────────────────────────────────────────────────────

    private static final Path LOG_FILE = Paths.get(
            System.getProperty("user.home"), "GameReadyToolkit-sessions.log");

    private void writeLog(List<PrepActionResult> results) {
        try {
            String ts = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(ts).append("] ")
              .append(restoreMode ? "RESTORE" : "PREPARE").append("\n");
            for (PrepActionResult r : results) {
                sb.append("  ").append(r.isSuccess() ? "OK" : "FAIL")
                  .append("  ").append(r.getActionName());
                String msg = r.getMessage();
                if (msg != null && !msg.isBlank() && !msg.equals("OK")) {
                    sb.append(" — ").append(msg.replace("\n", " | "));
                }
                sb.append("\n");
            }
            sb.append("\n");
            Files.writeString(LOG_FILE, sb.toString(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // Log write failures are non-fatal
        }
    }

    // ── Result dialog ─────────────────────────────────────────────────────────

    private void showResultDialog(List<PrepActionResult> results) {
        StringBuilder sb = new StringBuilder();
        for (PrepActionResult r : results) {
            sb.append(r.isSuccess() ? "\u2713 " : "\u2717 ").append(r.getActionName()).append("\n");
            String msg = r.getMessage();
            if (msg != null && !msg.isBlank() && !msg.equals("OK")) {
                sb.append("    \u2192 ").append(msg.replace("\n", "\n    ")).append("\n");
            }
            sb.append("\n");
        }

        TextArea ta = new TextArea(sb.toString().trim());
        ta.setEditable(false);
        ta.setWrapText(true);
        ta.setPrefWidth(460);
        ta.setPrefHeight(300);
        ta.setStyle(
            "-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 12px; " +
            "-fx-control-inner-background: #1e1e1e; -fx-text-fill: #d0d0d0;");

        ButtonType openLogBtn = new ButtonType("Open Log", ButtonBar.ButtonData.LEFT);
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(restoreMode ? "Restore Results" : "Preparation Results");
        dialog.setHeaderText("Completed " + results.size() + " action(s)");
        dialog.getDialogPane().setContent(ta);
        dialog.getDialogPane().setPrefWidth(500);
        dialog.getDialogPane().getButtonTypes().addAll(openLogBtn, ButtonType.OK);

        dialog.resultConverterProperty().set(bt -> {
            if (bt == openLogBtn && Files.exists(LOG_FILE) && Desktop.isDesktopSupported()) {
                try { Desktop.getDesktop().open(LOG_FILE.toFile()); } catch (IOException ignored) {}
            }
            return null;
        });
        dialog.showAndWait();
    }

    // ── Session timer ─────────────────────────────────────────────────────────

    private void startSessionTimer(Label timerLabel) {
        stopSessionTimer(timerLabel); // cancel any previous timer
        sessionStartMs = System.currentTimeMillis();
        timerLabel.setVisible(true);

        sessionTimerThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted() && sessionStartMs > 0) {
                long secs = (System.currentTimeMillis() - sessionStartMs) / 1000;
                String text = String.format("Session: %02d:%02d:%02d",
                        secs / 3600, (secs % 3600) / 60, secs % 60);
                Platform.runLater(() -> timerLabel.setText(text));
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "session-timer");
        sessionTimerThread.setDaemon(true);
        sessionTimerThread.start();
    }

    private void stopSessionTimer(Label timerLabel) {
        sessionStartMs = -1;
        if (sessionTimerThread != null) {
            sessionTimerThread.interrupt();
            sessionTimerThread = null;
        }
        Platform.runLater(() -> timerLabel.setVisible(false));
    }

    // ── Process execution ─────────────────────────────────────────────────────

    private PrepActionResult runPowerShell(String script) {
        return runProcess("powershell", "-NoProfile", "-NonInteractive", "-Command", script);
    }

    private PrepActionResult runCommand(String... args) {
        return runProcess(args);
    }

    private PrepActionResult runProcess(String... cmd) {
        String name = cmd[0];
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Read output first, then wait — avoids buffer-full deadlock
            String output = new String(process.getInputStream().readAllBytes()).trim();

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new PrepActionResult(name, false, "Timed out after 30 seconds");
            }

            int exitCode = process.exitValue();
            return new PrepActionResult(name, exitCode == 0, output.isEmpty() ? "OK" : output);
        } catch (Exception e) {
            return new PrepActionResult(name, false, e.getMessage());
        }
    }

    public static void main(String[] args) {
        launch();
    }
}

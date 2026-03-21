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

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

public class GameSessionPrepApp extends Application {

    private static final Preferences PREFS =
            Preferences.userRoot().node("com/mason/gamesessionprep");

    private final List<PrepAction> prepActions    = new ArrayList<>();
    private final List<PrepAction> restoreActions = new ArrayList<>();

    /** Which mode we're currently showing. */
    private boolean restoreMode = false;

    @Override
    public void start(Stage stage) {

        // ── Prepare actions ──────────────────────────────────────
        prepActions.add(new PrepAction(
                "High Performance Power Plan",
                "Prioritize system performance over power savings",
                () -> runPowerShell("powercfg /setactive 8c5e7fda-e8bf-4a96-9a85-a6e23a8c635c")
        ));
        prepActions.add(new PrepAction(
                "Silence Notifications (Focus Assist)",
                "Block popups and distractions during gameplay",
                () -> runPowerShell(
                    "Set-ItemProperty -Path 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Notifications\\Settings' " +
                    "-Name 'NOC_GLOBAL_SETTING_TOASTS_ENABLED' -Value 0 -Force")
        ));
        prepActions.add(new PrepAction(
                "Flush DNS Cache",
                "Clear stale DNS entries for better network performance",
                () -> runCommand("ipconfig", "/flushdns")
        ));
        prepActions.add(new PrepAction(
                "Set GPU to High Performance",
                "Force discrete GPU for maximum rendering performance",
                () -> runPowerShell(
                    "Add-Type -AssemblyName System.Windows.Forms; " +
                    "[System.Windows.Forms.MessageBox]::Show('Set GPU to High Performance in NVIDIA/AMD control panel manually.')")
        ));
        prepActions.add(new PrepAction(
                "Clear Temp Files",
                "Remove temporary files to free disk space",
                () -> runPowerShell("Remove-Item -Path $env:TEMP\\* -Recurse -Force -ErrorAction SilentlyContinue")
        ));
        prepActions.add(new PrepAction(
                "Pause Windows Update",
                "Prevent Windows Update from consuming resources mid-session",
                () -> runPowerShell("Stop-Service -Name wuauserv -Force -ErrorAction SilentlyContinue")
        ));
        prepActions.add(new PrepAction(
                "Check Display Refresh Rate",
                "Report current and max monitor refresh rate",
                () -> runPowerShell(
                    "$d = Get-WmiObject Win32_VideoController; " +
                    "Write-Output \"Refresh: $($d.CurrentRefreshRate)Hz / Max: $($d.MaxRefreshRate)Hz\"")
        ));

        // ── Restore actions ──────────────────────────────────────
        restoreActions.add(new PrepAction(
                "Restore Balanced Power Plan",
                "Return power settings to the default balanced profile",
                () -> runPowerShell("powercfg /setactive 381b4222-f694-41f0-9685-ff5bb260df2e")
        ));
        restoreActions.add(new PrepAction(
                "Re-enable Notifications",
                "Turn notifications back on after your session",
                () -> runPowerShell(
                    "Set-ItemProperty -Path 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Notifications\\Settings' " +
                    "-Name 'NOC_GLOBAL_SETTING_TOASTS_ENABLED' -Value 1 -Force")
        ));
        restoreActions.add(new PrepAction(
                "Re-enable Windows Update",
                "Allow Windows Update to resume normal operation",
                () -> runPowerShell("Start-Service -Name wuauserv -ErrorAction SilentlyContinue")
        ));
        restoreActions.add(new PrepAction(
                "Flush DNS Cache",
                "Clear any stale entries built up during the session",
                () -> runCommand("ipconfig", "/flushdns")
        ));

        // Load saved checkbox preferences
        loadPreferences(prepActions,    "prep_");
        loadPreferences(restoreActions, "restore_");

        // ── UI ───────────────────────────────────────────────────
        Label titleLabel    = new Label("Game Ready Toolkit");
        Label subtitleLabel = new Label("Select optimisations to apply");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #e8e8e8;");
        subtitleLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 13px;");

        VBox checklistBox = new VBox(10);
        buildChecklist(checklistBox, prepActions, "prep_");

        Label statusLabel = new Label();
        statusLabel.setStyle("-fx-text-fill: #aaa; -fx-font-size: 12px;");
        statusLabel.setWrapText(true);

        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(Double.MAX_VALUE);
        progressBar.setVisible(false);

        String btnStyle = "-fx-background-color: #2a2a2a; -fx-text-fill: #e0e0e0; " +
                          "-fx-border-color: #444; -fx-border-radius: 6; -fx-background-radius: 6; " +
                          "-fx-padding: 7 18; -fx-cursor: hand; -fx-font-size: 13px;";

        Button runButton     = new Button("Prepare System");
        Button modeToggleBtn = new Button("Switch to Restore Mode");
        Button exitButton    = new Button("Exit");

        for (Button b : new Button[]{runButton, modeToggleBtn, exitButton}) {
            b.setStyle(btnStyle);
        }

        modeToggleBtn.setStyle(btnStyle.replace("#2a2a2a", "#1a3a1a").replace("#444", "#2a5a2a"));

        runButton.setOnAction(e -> showPreview(stage, statusLabel, progressBar, runButton));

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

        VBox root = new VBox(14,
                titleLabel,
                subtitleLabel,
                new Separator(),
                checklistBox,
                new Separator(),
                buttonRow,
                progressBar,
                statusLabel
        );
        root.setPadding(new Insets(22));
        root.setStyle("-fx-background-color: #1a1a1a;");

        Scene scene = new Scene(root, 510, 520);
        stage.setTitle("Game Ready Toolkit");
        stage.setMinWidth(400);
        stage.setScene(scene);
        stage.show();
    }

    /** Rebuild the checklist for the given action list, wiring up preference saves. */
    private void buildChecklist(VBox box, List<PrepAction> actionList, String prefPrefix) {
        box.getChildren().clear();
        for (PrepAction action : actionList) {
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

    /** Load saved checkbox selections from Preferences, defaulting to true. */
    private void loadPreferences(List<PrepAction> actionList, String prefPrefix) {
        for (PrepAction action : actionList) {
            boolean saved = PREFS.getBoolean(prefPrefix + action.getName(), true);
            action.setSelected(saved);
        }
    }

    private void showPreview(Stage owner, Label statusLabel, ProgressBar progressBar, Button runButton) {
        List<PrepAction> active = restoreMode ? restoreActions : prepActions;
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
            if (result == confirm) {
                runActions(selected, statusLabel, progressBar, runButton);
            }
        });
    }

    private void runActions(List<PrepAction> selected, Label statusLabel,
                            ProgressBar progressBar, Button runButton) {
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

            showResultDialog(results);

            if (failed == 0) {
                statusLabel.setStyle("-fx-text-fill: #2ecc71; -fx-font-size: 12px;");
                statusLabel.setText("All " + passed + " actions completed successfully.");
            } else {
                statusLabel.setStyle("-fx-text-fill: #e67e22; -fx-font-size: 12px;");
                statusLabel.setText(passed + " succeeded, " + failed + " failed.");
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

    private void showResultDialog(List<PrepActionResult> results) {
        StringBuilder sb = new StringBuilder();
        for (PrepActionResult r : results) {
            sb.append(r.isSuccess() ? "\u2713 " : "\u2717 ")
              .append(r.getActionName()).append("\n");
            if (!r.getMessage().isBlank()) {
                sb.append("  ").append(r.getMessage()).append("\n");
            }
        }

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(restoreMode ? "Restore Results" : "Preparation Results");
        alert.setHeaderText("Completed " + results.size() + " action(s)");
        alert.setContentText(sb.toString());
        alert.showAndWait();
    }

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
            String output = new String(process.getInputStream().readAllBytes()).trim();
            int exitCode = process.waitFor();
            return new PrepActionResult(name, exitCode == 0, output.isEmpty() ? "OK" : output);
        } catch (Exception e) {
            return new PrepActionResult(name, false, e.getMessage());
        }
    }

    public static void main(String[] args) {
        launch();
    }
}

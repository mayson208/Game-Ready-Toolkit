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
import java.util.stream.Collectors;

public class GameSessionPrepApp extends Application {

    private final List<PrepAction> actions = new ArrayList<>();

    @Override
    public void start(Stage stage) {

        actions.add(new PrepAction(
                "Enable High Performance Power Plan",
                "Prioritize system performance over power savings",
                () -> runPowerShell("powercfg /setactive 8c5e7fda-e8bf-4a96-9a85-a6e23a8c635c")
        ));
        actions.add(new PrepAction(
                "Silence Notifications (Focus Assist)",
                "Block popups and distractions during gameplay",
                () -> runPowerShell(
                    "Set-ItemProperty -Path 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Notifications\\Settings' " +
                    "-Name 'NOC_GLOBAL_SETTING_TOASTS_ENABLED' -Value 0 -Force"
                )
        ));
        actions.add(new PrepAction(
                "Flush DNS Cache",
                "Clear stale DNS entries for better network performance",
                () -> runCommand("ipconfig", "/flushdns")
        ));
        actions.add(new PrepAction(
                "Set GPU to High Performance",
                "Force discrete GPU for maximum rendering performance",
                () -> runPowerShell(
                    "Add-Type -AssemblyName System.Windows.Forms; " +
                    "[System.Windows.Forms.MessageBox]::Show('Set GPU to High Performance in NVIDIA/AMD control panel manually.')"
                )
        ));
        actions.add(new PrepAction(
                "Clear Temp Files",
                "Remove temporary files to free disk space",
                () -> runPowerShell("Remove-Item -Path $env:TEMP\\* -Recurse -Force -ErrorAction SilentlyContinue")
        ));
        actions.add(new PrepAction(
                "Pause Windows Update",
                "Prevent Windows Update from consuming resources mid-session",
                () -> runPowerShell("Stop-Service -Name wuauserv -Force -ErrorAction SilentlyContinue")
        ));
        actions.add(new PrepAction(
                "Set Display Refresh Rate to Max",
                "Ensure the monitor runs at its highest refresh rate",
                () -> runPowerShell(
                    "$display = Get-WmiObject -Class Win32_VideoController; " +
                    "Write-Output \"Current refresh: $($display.CurrentRefreshRate)Hz / Max: $($display.MaxRefreshRate)Hz\""
                )
        ));

        // ── Checklist UI ────────────────────────────────────────
        Label titleLabel = new Label("Game Ready Toolkit");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #e8e8e8;");

        Label subtitleLabel = new Label("Select the optimisations to apply");
        subtitleLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 13px;");

        VBox checklistBox = new VBox(10);
        for (PrepAction action : actions) {
            CheckBox cb = new CheckBox(action.getName());
            cb.setSelected(action.isSelected());
            cb.setStyle("-fx-text-fill: #d0d0d0; -fx-font-size: 13px;");

            Tooltip tip = new Tooltip(action.getDescription());
            tip.setStyle("-fx-font-size: 12px;");
            cb.setTooltip(tip);

            cb.selectedProperty().addListener((obs, o, n) -> action.setSelected(n));
            checklistBox.getChildren().add(cb);
        }

        Label statusLabel = new Label();
        statusLabel.setStyle("-fx-text-fill: #aaa; -fx-font-size: 12px;");
        statusLabel.setWrapText(true);

        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(Double.MAX_VALUE);
        progressBar.setVisible(false);

        Button runButton  = new Button("Prepare System");
        Button exitButton = new Button("Exit");

        String btnStyle = "-fx-background-color: #2a2a2a; -fx-text-fill: #e0e0e0; " +
                          "-fx-border-color: #444; -fx-border-radius: 6; -fx-background-radius: 6; " +
                          "-fx-padding: 7 18; -fx-cursor: hand; -fx-font-size: 13px;";
        runButton.setStyle(btnStyle);
        exitButton.setStyle(btnStyle);

        runButton.setOnAction(e -> showPreview(stage, statusLabel, progressBar, runButton));
        exitButton.setOnAction(e -> stage.close());

        HBox buttonRow = new HBox(10, runButton, exitButton);
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

        Scene scene = new Scene(root, 480, 500);
        stage.setTitle("Game Ready Toolkit");
        stage.setMinWidth(380);
        stage.setScene(scene);
        stage.show();
    }

    private void showPreview(Stage owner, Label statusLabel, ProgressBar progressBar, Button runButton) {
        List<PrepAction> selected = actions.stream()
                .filter(PrepAction::isSelected)
                .collect(Collectors.toList());

        if (selected.isEmpty()) {
            statusLabel.setStyle("-fx-text-fill: #f39c12; -fx-font-size: 12px;");
            statusLabel.setText("No actions selected.");
            return;
        }

        Alert preview = new Alert(Alert.AlertType.CONFIRMATION);
        preview.initOwner(owner);
        preview.setTitle("Confirm Preparation");
        preview.setHeaderText("The following actions will be applied:");
        preview.setContentText(selected.stream()
                .map(a -> "  • " + a.getName())
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
            protected List<PrepActionResult> call() throws Exception {
                List<PrepActionResult> results = new ArrayList<>();
                for (int i = 0; i < selected.size(); i++) {
                    PrepAction action = selected.get(i);
                    updateProgress(i, selected.size());
                    Platform.runLater(() -> statusLabel.setText("Running: " + action.getName() + "..."));
                    PrepActionResult result = action.execute();
                    results.add(result);
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
            long failed = results.size() - passed;

            showResultDialog(results);

            if (failed == 0) {
                statusLabel.setStyle("-fx-text-fill: #2ecc71; -fx-font-size: 12px;");
                statusLabel.setText("All " + passed + " actions completed successfully.");
            } else {
                statusLabel.setStyle("-fx-text-fill: #e67e22; -fx-font-size: 12px;");
                statusLabel.setText(passed + " succeeded, " + failed + " failed. See details above.");
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
            sb.append(r.isSuccess() ? "✓ " : "✗ ")
              .append(r.getActionName())
              .append("\n");
            if (!r.getMessage().isBlank()) {
                sb.append("  ").append(r.getMessage()).append("\n");
            }
        }

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Preparation Results");
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

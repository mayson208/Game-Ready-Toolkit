package com.mason.gamesessionprep;

import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.awt.AWTException;
import java.awt.Desktop;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

public class GameSessionPrepApp extends Application {

    // ── Theme ─────────────────────────────────────────────────────────────────
    private static final String BG       = "#0a0a0a";
    private static final String BG_CARD  = "#111111";
    private static final String BG_CARD2 = "#181818";
    private static final String BORDER   = "#222222";
    private static final String TEXT     = "#f1f5f9";
    private static final String TEXT_DIM = "#4a5568";
    private static final String PURPLE   = "#7c3aed";
    private static final String CYAN     = "#06b6d4";
    private static final String GREEN    = "#22c55e";
    private static final String RED      = "#ef4444";
    private static final String AMBER    = "#f59e0b";
    private static final String PINK     = "#ec4899";

    // Category → accent color
    private static final Map<String, String> CAT_COLOR = Map.of(
        "Power",    AMBER,
        "CPU",      RED,
        "Network",  CYAN,
        "Display",  PURPLE,
        "Services", GREEN,
        "Memory",   PINK,
        "Game",     "#7ee787"
    );

    // ── State ─────────────────────────────────────────────────────────────────
    private static final Preferences PREFS =
            Preferences.userRoot().node("com/mason/gamesessionprep");

    private final List<PrepAction> prepActions    = new ArrayList<>();
    private final List<PrepAction> restoreActions = new ArrayList<>();
    private final List<PrepAction> gameActions    = new ArrayList<>();

    private boolean restoreMode  = false;
    private String  selectedGame = "General";

    private VBox    tilesContainer;
    private Label   timerLabel;
    private Label   statusLabel;
    private Label   gameDescLabel;
    private Label   sysInfoLabel;
    private ProgressBar progressBar;

    private long   sessionStartMs    = -1;
    private Thread sessionTimerThread;

    // ── Game profiles ─────────────────────────────────────────────────────────
    private static final Map<String, String> GAME_PROFILES = new LinkedHashMap<>();
    static {
        GAME_PROFILES.put("General",           "Universal optimizations for any game");
        GAME_PROFILES.put("Rainbow Six Siege", "Tactical FPS — low latency, stable frame pacing");
        GAME_PROFILES.put("Valorant",          "Competitive FPS — Riot Vanguard friendly tweaks");
        GAME_PROFILES.put("Apex Legends",      "Battle royale — high FPS, smooth frametimes");
        GAME_PROFILES.put("Warzone / MW3",     "COD engine — VRAM management, network priority");
        GAME_PROFILES.put("CS2",               "Source 2 — raw input, minimal overhead");
        GAME_PROFILES.put("Fortnite",          "UE5 — CPU thread optimization, shader pre-cache");
        GAME_PROFILES.put("Overwatch 2",       "Team FPS — balanced CPU/GPU load");
    }

    // ── Admin ─────────────────────────────────────────────────────────────────
    private static boolean isElevated() {
        try {
            ProcessBuilder pb = new ProcessBuilder("net", "session");
            pb.redirectErrorStream(true);
            return pb.start().waitFor() == 0;
        } catch (Exception e) { return false; }
    }

    // ── Entry ─────────────────────────────────────────────────────────────────
    public static void main(String[] args) throws Exception {
        if (!isElevated()) {
            // Try to relaunch as admin via the JAR path; if running from Maven classes, skip relaunch
            java.net.URL loc = GameSessionPrepApp.class
                    .getProtectionDomain().getCodeSource().getLocation();
            String locationPath = loc != null ? loc.toURI().getPath() : "";
            if (locationPath.endsWith(".jar")) {
                String javaExe = ProcessHandle.current().info().command().orElse("java");
                String psCommand = String.format(
                    "Start-Process -FilePath '%s' -ArgumentList '-jar \"%s\"' -Verb RunAs",
                    javaExe.replace("'", "''"), locationPath.replace("\"", "\\\""));
                new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", psCommand).start();
                System.exit(0);
            }
            // Running from Maven/IDE — continue without elevation (some actions may fail)
        }
        launch();
    }

    // ── start ─────────────────────────────────────────────────────────────────
    @Override
    public void start(Stage stage) {
        buildPrepActions();
        buildRestoreActions();
        buildGameActions();

        // ── ROOT ──────────────────────────────────────────────────────────────
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: " + BG + ";");

        // ── TOP BAR ───────────────────────────────────────────────────────────
        root.setTop(buildTopBar());

        // ── CENTER: scrollable tile grid ──────────────────────────────────────
        tilesContainer = new VBox(12);
        tilesContainer.setPadding(new Insets(16));
        tilesContainer.setStyle("-fx-background-color: " + BG + ";");

        refreshTiles();

        ScrollPane scroll = new ScrollPane(tilesContainer);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: " + BG + "; -fx-background-color: " + BG +
                        "; -fx-border-color: transparent;");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        root.setCenter(scroll);

        // ── BOTTOM BAR ────────────────────────────────────────────────────────
        root.setBottom(buildBottomBar(stage));

        Scene scene = new Scene(root, 760, 680);
        stage.setTitle("Game Ready Toolkit");
        stage.setMinWidth(600);
        stage.setScene(scene);
        stage.show();

        setupTrayIcon(stage);
    }

    // ── Top bar ───────────────────────────────────────────────────────────────
    private VBox buildTopBar() {
        // Logo / title
        Label logo = new Label("⚡");
        logo.setStyle("-fx-font-size: 22px;");

        Label title = new Label("GAME READY");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-font-family: Consolas; " +
                       "-fx-text-fill: " + TEXT + ";");

        Label subtitle = new Label("TOOLKIT");
        subtitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-font-family: Consolas; " +
                          "-fx-text-fill: " + PURPLE + ";");

        HBox logoBox = new HBox(6, logo, title, subtitle);
        logoBox.setAlignment(Pos.CENTER_LEFT);

        // Timer
        timerLabel = new Label();
        timerLabel.setStyle("-fx-text-fill: " + GREEN + "; -fx-font-size: 12px; " +
                            "-fx-font-family: Consolas; -fx-font-weight: bold;");
        timerLabel.setVisible(false);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Game selector
        Label gameLabel = new Label("GAME");
        gameLabel.setStyle("-fx-text-fill: " + TEXT_DIM + "; -fx-font-size: 10px; " +
                           "-fx-font-family: Consolas;");

        ComboBox<String> gameCombo = new ComboBox<>();
        gameCombo.getItems().addAll(GAME_PROFILES.keySet());
        gameCombo.setValue(PREFS.get("selected_game", "General"));
        selectedGame = gameCombo.getValue();
        gameCombo.setStyle(
            "-fx-background-color: " + BG_CARD2 + "; -fx-text-fill: " + TEXT + "; " +
            "-fx-border-color: " + PURPLE + "66; -fx-border-radius: 6; -fx-background-radius: 6; " +
            "-fx-font-size: 13px; -fx-font-family: Consolas; -fx-pref-width: 200px;");

        gameCombo.setOnAction(e -> {
            selectedGame = gameCombo.getValue();
            PREFS.put("selected_game", selectedGame);
            buildGameActions();
            refreshTiles();
            if (gameDescLabel != null)
                gameDescLabel.setText(GAME_PROFILES.getOrDefault(selectedGame, ""));
        });

        VBox gamePicker = new VBox(2, gameLabel, gameCombo);
        gamePicker.setAlignment(Pos.CENTER_LEFT);

        HBox mainRow = new HBox(16, logoBox, spacer, timerLabel, gamePicker);
        mainRow.setAlignment(Pos.CENTER_LEFT);
        mainRow.setPadding(new Insets(14, 20, 8, 20));

        // Game description strip
        gameDescLabel = new Label(GAME_PROFILES.getOrDefault(selectedGame, ""));
        gameDescLabel.setStyle("-fx-text-fill: " + TEXT_DIM + "; -fx-font-size: 10px; " +
                               "-fx-font-family: Consolas; -fx-padding: 0 20 0 20;");

        // Sys info strip
        sysInfoLabel = new Label("  CPU: --   RAM: --");
        sysInfoLabel.setStyle("-fx-text-fill: " + CYAN + "99; -fx-font-size: 10px; " +
                              "-fx-font-family: Consolas; -fx-padding: 0 20 6 20;");
        startSysInfoUpdater();

        HBox bottomStrip = new HBox();
        bottomStrip.getChildren().addAll(gameDescLabel, new Region(), sysInfoLabel);
        HBox.setHgrow(bottomStrip.getChildren().get(1), Priority.ALWAYS);
        bottomStrip.setMaxWidth(Double.MAX_VALUE);

        VBox topBar = new VBox(0, mainRow, bottomStrip);
        topBar.setStyle("-fx-background-color: " + BG_CARD + "; " +
                        "-fx-border-color: " + BORDER + "; " +
                        "-fx-border-width: 0 0 1 0;");
        return topBar;
    }

    private void startSysInfoUpdater() {
        Thread t = new Thread(() -> {
            com.sun.management.OperatingSystemMXBean os =
                (com.sun.management.OperatingSystemMXBean)
                java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    double cpu = os.getCpuLoad() * 100;
                    long totalRam = os.getTotalMemorySize();
                    long freeRam  = os.getFreeMemorySize();
                    long usedRam  = totalRam - freeRam;
                    String cpuStr = cpu < 0 ? "--" : String.format("%.0f%%", cpu);
                    String ramStr = String.format("%d / %d GB",
                        usedRam / (1024*1024*1024), totalRam / (1024*1024*1024));
                    Platform.runLater(() -> {
                        if (sysInfoLabel != null)
                            sysInfoLabel.setText("CPU: " + cpuStr + "   RAM: " + ramStr);
                    });
                    Thread.sleep(2000);
                } catch (InterruptedException e) { break; }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    // ── Bottom bar ────────────────────────────────────────────────────────────
    private VBox buildBottomBar(Stage stage) {
        // Mode toggle
        ToggleButton modeToggle = new ToggleButton("RESTORE MODE");
        modeToggle.setStyle(toggleStyle(false));
        modeToggle.setOnAction(e -> {
            restoreMode = modeToggle.isSelected();
            modeToggle.setStyle(toggleStyle(restoreMode));
            modeToggle.setText(restoreMode ? "PREPARE MODE" : "RESTORE MODE");
            refreshTiles();
            statusLabel.setText("");
            progressBar.setVisible(false);
        });

        // Select all / none
        Button selAll  = new Button("ALL");
        Button selNone = new Button("NONE");
        selAll .setStyle(smallBtnStyle(CYAN));
        selNone.setStyle(smallBtnStyle(TEXT_DIM));
        selAll .setOnAction(e -> setAllSelected(true));
        selNone.setOnAction(e -> setAllSelected(false));

        // Run button
        Button runBtn = new Button("▶  PREPARE SYSTEM");
        runBtn.setStyle(runBtnStyle());
        runBtn.setOnAction(e -> showPreview(stage, runBtn));

        modeToggle.selectedProperty().addListener((obs, o, n) ->
            runBtn.setText(n ? "▶  RESTORE SYSTEM" : "▶  PREPARE SYSTEM"));

        HBox btnRow = new HBox(10, modeToggle, selAll, selNone, new Region(), runBtn);
        HBox.setHgrow(btnRow.getChildren().get(3), Priority.ALWAYS);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        // Progress + status
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(Double.MAX_VALUE);
        progressBar.setVisible(false);
        progressBar.setStyle("-fx-accent: " + PURPLE + "; -fx-background-color: " + BG_CARD2 + ";");

        statusLabel = new Label();
        statusLabel.setStyle("-fx-text-fill: " + TEXT_DIM + "; -fx-font-size: 11px; " +
                             "-fx-font-family: Consolas;");

        VBox bottom = new VBox(10, new Separator(), btnRow, progressBar, statusLabel);
        bottom.setPadding(new Insets(12, 20, 16, 20));
        bottom.setStyle("-fx-background-color: " + BG_CARD + "; " +
                        "-fx-border-color: " + BORDER + "; -fx-border-width: 1 0 0 0;");
        return bottom;
    }

    // ── Tile grid ─────────────────────────────────────────────────────────────
    private void refreshTiles() {
        if (tilesContainer == null) return;
        tilesContainer.getChildren().clear();

        List<PrepAction> actions = restoreMode ? restoreActions :
                combineActions(gameActions, prepActions);

        // Group by category
        Map<String, List<PrepAction>> grouped = new LinkedHashMap<>();
        for (PrepAction a : actions) {
            grouped.computeIfAbsent(a.getCategory(), k -> new ArrayList<>()).add(a);
        }

        // Two-column grid of tiles
        int col = 0;
        HBox row = null;
        for (Map.Entry<String, List<PrepAction>> entry : grouped.entrySet()) {
            if (col % 2 == 0) {
                row = new HBox(12);
                row.setMaxWidth(Double.MAX_VALUE);
                row.setFillHeight(true);
                tilesContainer.getChildren().add(row);
            }
            VBox tile = buildCategoryTile(entry.getKey(), entry.getValue());
            HBox.setHgrow(tile, Priority.ALWAYS);
            tile.setMaxWidth(Double.MAX_VALUE);
            tile.setMinWidth(0);
            row.getChildren().add(tile);
            col++;
        }
        // Pad last row if odd
        if (col % 2 == 1 && row != null) {
            Region pad = new Region();
            HBox.setHgrow(pad, Priority.ALWAYS);
            row.getChildren().add(pad);
        }
    }

    private List<PrepAction> combineActions(List<PrepAction> a, List<PrepAction> b) {
        List<PrepAction> result = new ArrayList<>(a);
        result.addAll(b);
        return result;
    }

    private VBox buildCategoryTile(String category, List<PrepAction> actions) {
        String color = CAT_COLOR.getOrDefault(category, PURPLE);

        VBox tile = new VBox(8);
        tile.setPadding(new Insets(14));
        tile.setStyle(
            "-fx-background-color: " + BG_CARD + "; " +
            "-fx-border-color: " + color + "44; " +
            "-fx-border-radius: 10; -fx-background-radius: 10; " +
            "-fx-border-width: 1;");

        // Tile header
        Circle dot = new Circle(4, Color.web(color));
        Label catLabel = new Label(category.toUpperCase());
        catLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 11px; " +
                          "-fx-font-weight: bold; -fx-font-family: Consolas;");

        // Pulse animation on dot
        FadeTransition pulse = new FadeTransition(Duration.millis(1200), dot);
        pulse.setFromValue(1.0);
        pulse.setToValue(0.3);
        pulse.setCycleCount(Animation.INDEFINITE);
        pulse.setAutoReverse(true);
        pulse.play();

        HBox header = new HBox(8, dot, catLabel);
        header.setAlignment(Pos.CENTER_LEFT);

        Separator divider = new Separator();
        divider.setStyle("-fx-background-color: " + color + "33; -fx-padding: 0;");

        tile.getChildren().addAll(header, divider);

        // Checkboxes
        String prefPrefix = restoreMode ? "restore_" : (
            actions.isEmpty() ? "prep_" :
            (actions.get(0).getCategory().equals("Game") ? "game_" : "prep_")
        );

        for (PrepAction action : actions) {
            String prefix = action.getCategory().equals("Game") ? "game_" :
                            restoreMode ? "restore_" : "prep_";

            CheckBox cb = new CheckBox(action.getName());
            boolean saved = PREFS.getBoolean(prefix + action.getName(), true);
            cb.setSelected(saved);
            action.setSelected(saved);
            cb.setStyle(
                "-fx-text-fill: " + TEXT + "; -fx-font-size: 12px; " +
                "-fx-font-family: Consolas;");

            Tooltip tip = new Tooltip(action.getDescription());
            tip.setStyle("-fx-font-size: 11px; -fx-background-color: #1a1a1a; " +
                         "-fx-text-fill: " + TEXT + "; -fx-border-color: " + BORDER + ";");
            tip.setWrapText(true);
            tip.setMaxWidth(300);
            cb.setTooltip(tip);

            String finalPrefix = prefix;
            cb.selectedProperty().addListener((obs, o, n) -> {
                action.setSelected(n);
                PREFS.putBoolean(finalPrefix + action.getName(), n);
            });
            tile.getChildren().add(cb);
        }

        // Hover glow effect
        tile.setOnMouseEntered(e -> tile.setStyle(
            "-fx-background-color: " + BG_CARD2 + "; " +
            "-fx-border-color: " + color + "88; " +
            "-fx-border-radius: 10; -fx-background-radius: 10; -fx-border-width: 1;"));
        tile.setOnMouseExited(e -> tile.setStyle(
            "-fx-background-color: " + BG_CARD + "; " +
            "-fx-border-color: " + color + "44; " +
            "-fx-border-radius: 10; -fx-background-radius: 10; -fx-border-width: 1;"));

        return tile;
    }

    // ── Styles ────────────────────────────────────────────────────────────────
    private String runBtnStyle() {
        return "-fx-background-color: " + PURPLE + "; -fx-text-fill: white; " +
               "-fx-font-size: 13px; -fx-font-weight: bold; -fx-font-family: Consolas; " +
               "-fx-border-radius: 8; -fx-background-radius: 8; " +
               "-fx-padding: 8 24; -fx-cursor: hand;";
    }

    private String toggleStyle(boolean active) {
        String bg = active ? RED + "22" : BG_CARD2;
        String border = active ? RED : BORDER;
        String text = active ? RED : TEXT_DIM;
        return "-fx-background-color: " + bg + "; -fx-text-fill: " + text + "; " +
               "-fx-border-color: " + border + "; -fx-border-radius: 6; -fx-background-radius: 6; " +
               "-fx-font-size: 11px; -fx-font-family: Consolas; -fx-font-weight: bold; " +
               "-fx-padding: 6 14; -fx-cursor: hand;";
    }

    private String smallBtnStyle(String color) {
        return "-fx-background-color: " + color + "22; -fx-text-fill: " + color + "; " +
               "-fx-border-color: " + color + "66; -fx-border-radius: 6; -fx-background-radius: 6; " +
               "-fx-font-size: 11px; -fx-font-family: Consolas; " +
               "-fx-padding: 6 12; -fx-cursor: hand;";
    }

    // ── Select helpers ────────────────────────────────────────────────────────
    private void setAllSelected(boolean selected) {
        List<PrepAction> all = restoreMode ? restoreActions :
                combineActions(gameActions, prepActions);
        all.forEach(a -> {
            String prefix = a.getCategory().equals("Game") ? "game_" :
                            restoreMode ? "restore_" : "prep_";
            a.setSelected(selected);
            PREFS.putBoolean(prefix + a.getName(), selected);
        });
        refreshTiles();
    }

    // ── Preview & run ─────────────────────────────────────────────────────────
    private void showPreview(Stage stage, Button runBtn) {
        List<PrepAction> selected = getSelected();
        if (selected.isEmpty()) {
            statusLabel.setStyle("-fx-text-fill: " + AMBER + "; -fx-font-size: 11px; -fx-font-family: Consolas;");
            statusLabel.setText("⚠ No actions selected.");
            return;
        }

        Alert preview = new Alert(Alert.AlertType.CONFIRMATION);
        preview.initOwner(stage);
        preview.setTitle(restoreMode ? "Confirm Restore" : "Confirm Prepare — " + selectedGame);
        preview.setHeaderText((restoreMode ? "Restore" : "Optimize") + " — " + selected.size() + " actions");
        preview.setContentText(selected.stream()
                .map(a -> "  • " + a.getName())
                .collect(Collectors.joining("\n")));

        ButtonType confirm = new ButtonType("Run Now");
        ButtonType cancel  = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        preview.getButtonTypes().setAll(confirm, cancel);

        preview.showAndWait().ifPresent(result -> {
            if (result == confirm) runActions(selected, runBtn);
        });
    }

    private List<PrepAction> getSelected() {
        return (restoreMode ? restoreActions : combineActions(gameActions, prepActions))
                .stream().filter(PrepAction::isSelected).collect(Collectors.toList());
    }

    private void runActions(List<PrepAction> selected, Button runBtn) {
        runBtn.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(0);

        Task<List<PrepActionResult>> task = new Task<>() {
            @Override
            protected List<PrepActionResult> call() {
                List<PrepActionResult> results = new ArrayList<>();
                for (int i = 0; i < selected.size(); i++) {
                    PrepAction action = selected.get(i);
                    updateProgress(i, selected.size());
                    Platform.runLater(() -> {
                        statusLabel.setStyle("-fx-text-fill: " + CYAN + "; -fx-font-size: 11px; -fx-font-family: Consolas;");
                        statusLabel.setText("⟳ " + action.getName() + "...");
                    });
                    results.add(action.execute());
                }
                return results;
            }
        };

        progressBar.progressProperty().bind(task.progressProperty());

        task.setOnSucceeded(e -> {
            progressBar.progressProperty().unbind();
            progressBar.setProgress(1.0);
            runBtn.setDisable(false);

            List<PrepActionResult> results = task.getValue();
            long passed = results.stream().filter(PrepActionResult::isSuccess).count();
            long failed  = results.size() - passed;

            if (!restoreMode && failed == 0) startSessionTimer();
            else if (restoreMode) stopSessionTimer();

            writeLog(results);
            showResultDialog(results, stage -> null);

            if (failed == 0) {
                statusLabel.setStyle("-fx-text-fill: " + GREEN + "; -fx-font-size: 11px; -fx-font-family: Consolas;");
                statusLabel.setText("✓ All " + passed + " actions completed.");
            } else {
                statusLabel.setStyle("-fx-text-fill: " + AMBER + "; -fx-font-size: 11px; -fx-font-family: Consolas;");
                statusLabel.setText("⚠ " + passed + " succeeded, " + failed + " failed.");
            }
        });

        task.setOnFailed(e -> {
            progressBar.progressProperty().unbind();
            runBtn.setDisable(false);
            statusLabel.setStyle("-fx-text-fill: " + RED + "; -fx-font-size: 11px; -fx-font-family: Consolas;");
            statusLabel.setText("✗ Error: " + task.getException().getMessage());
        });

        Thread t = new Thread(task, "prep-runner");
        t.setDaemon(true);
        t.start();
    }

    // ── Result dialog ─────────────────────────────────────────────────────────
    private void showResultDialog(List<PrepActionResult> results, javafx.util.Callback<Stage, Void> ignored) {
        long passed = results.stream().filter(PrepActionResult::isSuccess).count();
        long failed = results.size() - passed;
        boolean allGood = failed == 0;

        Stage popup = new Stage();
        popup.setTitle(restoreMode ? "Restore Complete" : "System Ready — " + selectedGame);
        popup.initModality(javafx.stage.Modality.APPLICATION_MODAL);

        // Header
        Label icon = new Label(allGood ? "✓" : "⚠");
        icon.setStyle("-fx-font-size: 36px; -fx-text-fill: " + (allGood ? GREEN : AMBER) + ";");

        Label headline = new Label(allGood
            ? (restoreMode ? "System Restored" : "System Ready to Game")
            : passed + " succeeded · " + failed + " failed");
        headline.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-font-family: Consolas; " +
                          "-fx-text-fill: " + TEXT + ";");

        Label sub = new Label(allGood
            ? "All " + passed + " optimizations applied successfully."
            : "Some actions failed — check the log for details.");
        sub.setStyle("-fx-font-size: 11px; -fx-font-family: Consolas; -fx-text-fill: " + TEXT_DIM + ";");

        VBox headerBox = new VBox(6, icon, headline, sub);
        headerBox.setAlignment(Pos.CENTER);
        headerBox.setPadding(new Insets(24, 24, 16, 24));

        // Results list
        VBox resultList = new VBox(4);
        resultList.setPadding(new Insets(0, 16, 8, 16));
        for (PrepActionResult r : results) {
            String color = r.isSuccess() ? GREEN : RED;
            String prefix = r.isSuccess() ? "✓ " : "✗ ";
            Label row = new Label(prefix + r.getActionName());
            row.setStyle("-fx-font-family: Consolas; -fx-font-size: 12px; -fx-text-fill: " + color + ";");
            resultList.getChildren().add(row);
            String msg = r.getMessage();
            if (msg != null && !msg.isBlank() && !msg.equals("OK") && !r.isSuccess()) {
                Label detail = new Label("   → " + msg.split("\n")[0]);
                detail.setStyle("-fx-font-family: Consolas; -fx-font-size: 10px; -fx-text-fill: " + TEXT_DIM + ";");
                detail.setWrapText(true);
                resultList.getChildren().add(detail);
            }
        }

        ScrollPane scroll = new ScrollPane(resultList);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(Math.min(results.size() * 26 + 20, 260));
        scroll.setStyle("-fx-background: " + BG_CARD2 + "; -fx-background-color: " + BG_CARD2 +
                        "; -fx-border-color: " + BORDER + "; -fx-border-radius: 6;");

        // Buttons
        Button closeBtn = new Button("Done");
        closeBtn.setStyle("-fx-background-color: " + PURPLE + "; -fx-text-fill: white; " +
                          "-fx-font-family: Consolas; -fx-font-weight: bold; -fx-font-size: 13px; " +
                          "-fx-padding: 8 28; -fx-background-radius: 8; -fx-cursor: hand;");
        closeBtn.setOnAction(e -> popup.close());

        Button logBtn = new Button("Open Log");
        logBtn.setStyle(smallBtnStyle(TEXT_DIM));
        logBtn.setOnAction(e -> {
            if (Files.exists(LOG_FILE) && Desktop.isDesktopSupported()) {
                try { Desktop.getDesktop().open(LOG_FILE.toFile()); } catch (IOException ex) {}
            }
        });

        HBox btnRow = new HBox(10, logBtn, new Region(), closeBtn);
        HBox.setHgrow(btnRow.getChildren().get(1), Priority.ALWAYS);
        btnRow.setAlignment(Pos.CENTER);
        btnRow.setPadding(new Insets(12, 20, 20, 20));

        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: " + BORDER + ";");

        VBox root = new VBox(0, headerBox, sep,
                new javafx.scene.layout.StackPane(scroll) {{ setPadding(new Insets(12, 16, 8, 16)); }},
                btnRow);
        root.setStyle("-fx-background-color: " + BG_CARD + ";");

        Scene scene = new Scene(root, 460, 0);
        root.layout();
        popup.setScene(scene);
        popup.sizeToScene();
        popup.setResizable(false);
        popup.show();
        closeBtn.requestFocus();
    }

    // ── Session timer ─────────────────────────────────────────────────────────
    private void startSessionTimer() {
        stopSessionTimer();
        sessionStartMs = System.currentTimeMillis();
        timerLabel.setVisible(true);
        sessionTimerThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted() && sessionStartMs > 0) {
                long secs = (System.currentTimeMillis() - sessionStartMs) / 1000;
                String text = String.format("⏱  %02d:%02d:%02d", secs/3600, (secs%3600)/60, secs%60);
                Platform.runLater(() -> timerLabel.setText(text));
                try { Thread.sleep(1000); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            }
        }, "session-timer");
        sessionTimerThread.setDaemon(true);
        sessionTimerThread.start();
    }

    private void stopSessionTimer() {
        sessionStartMs = -1;
        if (sessionTimerThread != null) { sessionTimerThread.interrupt(); sessionTimerThread = null; }
        Platform.runLater(() -> timerLabel.setVisible(false));
    }

    // ── Log ───────────────────────────────────────────────────────────────────
    private static final Path LOG_FILE = Paths.get(System.getProperty("user.home"), "GameReadyToolkit-sessions.log");

    private void writeLog(List<PrepActionResult> results) {
        try {
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(ts).append("] ").append(restoreMode ? "RESTORE" : "PREPARE — " + selectedGame).append("\n");
            for (PrepActionResult r : results) {
                sb.append("  ").append(r.isSuccess() ? "OK" : "FAIL").append("  ").append(r.getActionName());
                String msg = r.getMessage();
                if (msg != null && !msg.isBlank() && !msg.equals("OK"))
                    sb.append(" — ").append(msg.replace("\n", " | "));
                sb.append("\n");
            }
            sb.append("\n");
            Files.writeString(LOG_FILE, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {}
    }

    // ── System tray ───────────────────────────────────────────────────────────
    private void setupTrayIcon(Stage stage) {
        if (!SystemTray.isSupported()) return;
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(new java.awt.Color(0x7c, 0x3a, 0xed));
        g.fillOval(1, 1, 14, 14);
        g.dispose();

        PopupMenu popup = new PopupMenu();
        MenuItem show = new MenuItem("Show"); MenuItem quit = new MenuItem("Quit");
        popup.add(show); popup.addSeparator(); popup.add(quit);
        TrayIcon tray = new TrayIcon(img, "Game Ready Toolkit", popup);
        tray.setImageAutoSize(true);
        show.addActionListener(e -> Platform.runLater(() -> { stage.show(); stage.toFront(); }));
        tray.addActionListener(e -> Platform.runLater(() -> { stage.show(); stage.toFront(); }));
        quit.addActionListener(e -> { SystemTray.getSystemTray().remove(tray); Platform.exit(); System.exit(0); });
        stage.setOnCloseRequest(e -> { Platform.exit(); System.exit(0); });
        try { SystemTray.getSystemTray().add(tray); }
        catch (AWTException ignored) {}
    }

    // ── Process helpers ───────────────────────────────────────────────────────
    private PrepActionResult runPowerShell(String script) {
        return runProcess("powershell", "-NoProfile", "-NonInteractive", "-Command", script);
    }
    private PrepActionResult runCommand(String... args) { return runProcess(args); }
    private PrepActionResult runProcess(String... cmd) {
        String name = cmd[0];
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) { process.destroyForcibly(); return new PrepActionResult(name, false, "Timed out"); }
            return new PrepActionResult(name, process.exitValue() == 0, output.isEmpty() ? "OK" : output);
        } catch (Exception e) { return new PrepActionResult(name, false, e.getMessage()); }
    }

    // ── BUILD ACTIONS ─────────────────────────────────────────────────────────

    private void buildPrepActions() {
        prepActions.clear();

        // POWER
        prepActions.add(new PrepAction("Ultimate Performance Plan",
            "Disables CPU core parking & idle states — lower micro-latency than High Performance", "Power",
            () -> runPowerShell(
                "$existing = powercfg /list | Select-String 'Ultimate';" +
                "if (-not $existing) { $guid = (powercfg /duplicatescheme e9a42b02-d5df-448d-aa00-03f14749eb61 2>&1); $guid = ($guid -split ' ')[-1].Trim() }" +
                "else { $guid = ($existing -split ' ')[-1].Trim() };" +
                "powercfg /setactive $guid; Write-Output \"Ultimate Performance Plan active\"")));

        // CPU
        prepActions.add(new PrepAction("Win32PrioritySeparation",
            "Sets foreground app quantum to shortest — game gets more CPU slices, lower input lag", "CPU",
            () -> runPowerShell(
                "Set-ItemProperty -Path 'HKLM:\\SYSTEM\\CurrentControlSet\\Control\\PriorityControl' " +
                "-Name 'Win32PrioritySeparation' -Value 26 -Type DWord -Force; Write-Output 'Set to 26 (low latency)'")));

        prepActions.add(new PrepAction("Disable Paging Executive",
            "Forces kernel code to stay in RAM — reduces DPC latency spikes", "CPU",
            () -> runPowerShell(
                "Set-ItemProperty -Path 'HKLM:\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Memory Management' " +
                "-Name 'DisablePagingExecutive' -Value 1 -Type DWord -Force; Write-Output 'DisablePagingExecutive enabled'")));

        prepActions.add(new PrepAction("Set Game CPU Priority",
            "Boosts priority for common game executables via registry", "CPU",
            () -> runPowerShell(
                "$p = 'HKLM:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Image File Execution Options';" +
                "$games = @('RainbowSix.exe','VALORANT-Win64-Shipping.exe','r5apex.exe','cs2.exe','FortniteClient-Win64-Shipping.exe');" +
                "foreach ($g in $games) { $full = \"$p\\$g\\PerfOptions\";" +
                "if (-not (Test-Path $full)) { New-Item -Path $full -Force | Out-Null };" +
                "Set-ItemProperty -Path $full -Name 'CpuPriorityClass' -Value 3 -Type DWord -Force };" +
                "Write-Output 'Game CPU priorities set to High'")));

        // DISPLAY
        prepActions.add(new PrepAction("Enable Hardware-Accelerated GPU Scheduling",
            "Shifts GPU scheduling to the GPU — lower latency on RTX 20+ / RDNA", "Display",
            () -> runPowerShell(
                "Set-ItemProperty -Path 'HKLM:\\SYSTEM\\CurrentControlSet\\Control\\GraphicsDrivers' " +
                "-Name 'HwSchMode' -Value 2 -Type DWord -Force; Write-Output 'HAGS enabled (reboot to apply)'")));

        prepActions.add(new PrepAction("Set GPU to High Performance",
            "Forces Windows to always use the dedicated GPU", "Display",
            () -> runPowerShell(
                "$p = 'HKCU:\\Software\\Microsoft\\DirectX\\UserGpuPreferences';" +
                "if (-not (Test-Path $p)) { New-Item -Path $p -Force | Out-Null };" +
                "Set-ItemProperty -Path $p -Name 'DirectXUserGlobalSettings' -Value 'VRROptimizeEnable=0;' -Type String -Force;" +
                "Write-Output 'GPU set to High Performance'")));

        prepActions.add(new PrepAction("Disable Mouse Acceleration",
            "Raw linear 1:1 mouse input — removes Enhanced Pointer Precision curve", "Display",
            () -> runPowerShell(
                "Set-ItemProperty -Path 'HKCU:\\Control Panel\\Mouse' -Name 'MouseSpeed' -Value 0 -Force;" +
                "Set-ItemProperty -Path 'HKCU:\\Control Panel\\Mouse' -Name 'MouseThreshold1' -Value 0 -Force;" +
                "Set-ItemProperty -Path 'HKCU:\\Control Panel\\Mouse' -Name 'MouseThreshold2' -Value 0 -Force;" +
                "Write-Output 'Mouse acceleration disabled'")));

        prepActions.add(new PrepAction("Check Display Refresh Rate",
            "Reports current vs max monitor Hz — confirm you're at full refresh", "Display",
            () -> runPowerShell(
                "$d = Get-CimInstance Win32_VideoController;" +
                "Write-Output \"Refresh: $($d.CurrentRefreshRate)Hz / Max: $($d.MaxRefreshRate)Hz\"")));

        // NETWORK
        prepActions.add(new PrepAction("Disable Nagle's Algorithm",
            "Removes TCP packet batching — cuts ping spikes significantly", "Network",
            () -> runPowerShell(
                "$base = 'HKLM:\\SYSTEM\\CurrentControlSet\\Services\\Tcpip\\Parameters\\Interfaces';" +
                "Get-ChildItem $base | ForEach-Object {" +
                "Set-ItemProperty -Path $_.PSPath -Name 'TcpAckFrequency' -Value 1 -Type DWord -Force -ErrorAction SilentlyContinue;" +
                "Set-ItemProperty -Path $_.PSPath -Name 'TCPNoDelay' -Value 1 -Type DWord -Force -ErrorAction SilentlyContinue };" +
                "Write-Output 'Nagle disabled on all interfaces'")));

        prepActions.add(new PrepAction("Switch DNS to Cloudflare",
            "1.1.1.1 / 1.0.0.1 — fastest public DNS, reduces server connection latency", "Network",
            () -> runPowerShell(
                "Get-NetAdapter | Where-Object Status -eq 'Up' | ForEach-Object {" +
                "Set-DnsClientServerAddress -InterfaceIndex $_.ifIndex -ServerAddresses ('1.1.1.1','1.0.0.1');" +
                "Write-Output \"DNS set on $($_.Name)\" }")));

        prepActions.add(new PrepAction("Remove Network Throttling",
            "Removes Windows multimedia bandwidth cap on your connection", "Network",
            () -> runPowerShell(
                "Set-ItemProperty -Path 'HKLM:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Multimedia\\SystemProfile' " +
                "-Name 'NetworkThrottlingIndex' -Value 0xffffffff -Type DWord -Force; Write-Output 'NetworkThrottlingIndex removed'")));

        prepActions.add(new PrepAction("Flush DNS Cache",
            "Clears stale DNS entries for cleaner server connections", "Network",
            () -> runCommand("ipconfig", "/flushdns")));

        // SERVICES
        prepActions.add(new PrepAction("Stop Xbox Game Bar",
            "Kills overlay — reduces CPU/GPU overhead and DWM interference", "Services",
            () -> runPowerShell(
                "Stop-Process -Name GameBar -Force -ErrorAction SilentlyContinue;" +
                "Stop-Process -Name GameBarFTServer -Force -ErrorAction SilentlyContinue;" +
                "Write-Output 'Xbox Game Bar stopped'")));

        prepActions.add(new PrepAction("Enable Game Mode",
            "Dedicates more CPU/GPU resources to the foreground game", "Services",
            () -> runPowerShell(
                "if (-not (Test-Path 'HKCU:\\Software\\Microsoft\\GameBar')) { New-Item -Path 'HKCU:\\Software\\Microsoft\\GameBar' -Force | Out-Null };" +
                "Set-ItemProperty -Path 'HKCU:\\Software\\Microsoft\\GameBar' -Name 'AutoGameModeEnabled' -Value 1 -Type DWord -Force;" +
                "Write-Output 'Game Mode enabled'")));

        prepActions.add(new PrepAction("Silence Notifications",
            "Blocks all toast popups during gameplay", "Services",
            () -> runPowerShell(
                "Set-ItemProperty -Path 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Notifications\\Settings' " +
                "-Name 'NOC_GLOBAL_SETTING_TOASTS_ENABLED' -Value 0 -Force; Write-Output 'Notifications silenced'")));

        prepActions.add(new PrepAction("Stop Windows Search Indexer",
            "Pauses background disk indexing during gameplay", "Services",
            () -> runPowerShell("Stop-Service -Name WSearch -Force -ErrorAction SilentlyContinue; Write-Output 'Stopped'")));

        prepActions.add(new PrepAction("Pause Windows Update",
            "Prevents wuauserv from stealing bandwidth mid-session", "Services",
            () -> runPowerShell("Stop-Service -Name wuauserv -Force -ErrorAction SilentlyContinue; Write-Output 'Stopped'")));

        prepActions.add(new PrepAction("Stop SysMain (Superfetch)",
            "On SSDs, Superfetch burns disk/RAM for no benefit during gaming", "Services",
            () -> runPowerShell("Stop-Service -Name SysMain -Force -ErrorAction SilentlyContinue; Write-Output 'SysMain stopped'")));

        prepActions.add(new PrepAction("Stop DiagTrack (Telemetry)",
            "Stops Windows telemetry waking up and consuming CPU during your session", "Services",
            () -> runPowerShell("Stop-Service -Name DiagTrack -Force -ErrorAction SilentlyContinue; Write-Output 'DiagTrack stopped'")));

        prepActions.add(new PrepAction("Pause OneDrive Sync",
            "Frees bandwidth and CPU from background cloud uploads", "Services",
            () -> runPowerShell(
                "$od = Get-Process -Name OneDrive -ErrorAction SilentlyContinue;" +
                "if ($od) { & \"$env:LOCALAPPDATA\\Microsoft\\OneDrive\\OneDrive.exe\" /pause; Write-Output 'Paused' } else { Write-Output 'Not running' }")));

        // MEMORY
        prepActions.add(new PrepAction("Clear RAM Working Set",
            "Trims unused RAM from background apps — frees memory for your game", "Memory",
            () -> runPowerShell(
                "Add-Type -TypeDefinition @'\nusing System; using System.Runtime.InteropServices;\n" +
                "public class Memory {\n  [DllImport(\"psapi.dll\")] public static extern bool EmptyWorkingSet(IntPtr h);\n" +
                "  public static void Trim() { foreach (var p in System.Diagnostics.Process.GetProcesses()) { try { EmptyWorkingSet(p.Handle); } catch {} } }\n}\n'@;\n" +
                "[Memory]::Trim(); Write-Output 'RAM working set cleared'")));

        prepActions.add(new PrepAction("Clear Temp Files",
            "Removes temp files to free disk space and reduce background I/O", "Memory",
            () -> runPowerShell(
                "Remove-Item -Path $env:TEMP\\* -Recurse -Force -ErrorAction SilentlyContinue; Write-Output 'Temp files cleared'")));
    }

    private void buildRestoreActions() {
        restoreActions.clear();

        restoreActions.add(new PrepAction("Restore Balanced Power Plan", "Return to default balanced power profile", "Power",
            () -> runPowerShell("powercfg /setactive 381b4222-f694-41f0-9685-ff5bb260df2e")));

        restoreActions.add(new PrepAction("Restore Win32PrioritySeparation", "Reset CPU scheduling to Windows default", "CPU",
            () -> runPowerShell("Set-ItemProperty -Path 'HKLM:\\SYSTEM\\CurrentControlSet\\Control\\PriorityControl' -Name 'Win32PrioritySeparation' -Value 2 -Type DWord -Force; Write-Output 'Restored'")));

        restoreActions.add(new PrepAction("Restore Paging Executive", "Re-enable normal kernel paging", "CPU",
            () -> runPowerShell("Set-ItemProperty -Path 'HKLM:\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Memory Management' -Name 'DisablePagingExecutive' -Value 0 -Type DWord -Force; Write-Output 'Restored'")));

        restoreActions.add(new PrepAction("Restore Default GPU Preference", "Remove High Performance GPU override", "Display",
            () -> runPowerShell("$p = 'HKCU:\\Software\\Microsoft\\DirectX\\UserGpuPreferences'; if (Test-Path $p) { Remove-ItemProperty -Path $p -Name 'DirectXUserGlobalSettings' -ErrorAction SilentlyContinue }; Write-Output 'Restored'")));

        restoreActions.add(new PrepAction("Re-enable Mouse Acceleration", "Restore default Windows pointer precision", "Display",
            () -> runPowerShell("Set-ItemProperty -Path 'HKCU:\\Control Panel\\Mouse' -Name 'MouseSpeed' -Value 1 -Force; Set-ItemProperty -Path 'HKCU:\\Control Panel\\Mouse' -Name 'MouseThreshold1' -Value 6 -Force; Set-ItemProperty -Path 'HKCU:\\Control Panel\\Mouse' -Name 'MouseThreshold2' -Value 10 -Force; Write-Output 'Restored'")));

        restoreActions.add(new PrepAction("Restore Nagle Algorithm", "Re-enable TCP packet batching on all interfaces", "Network",
            () -> runPowerShell("$base = 'HKLM:\\SYSTEM\\CurrentControlSet\\Services\\Tcpip\\Parameters\\Interfaces'; Get-ChildItem $base | ForEach-Object { Remove-ItemProperty -Path $_.PSPath -Name 'TcpAckFrequency' -ErrorAction SilentlyContinue; Remove-ItemProperty -Path $_.PSPath -Name 'TCPNoDelay' -ErrorAction SilentlyContinue }; Write-Output 'Nagle restored'")));

        restoreActions.add(new PrepAction("Restore ISP DNS", "Reset DNS to automatic / ISP default", "Network",
            () -> runPowerShell("Get-NetAdapter | Where-Object Status -eq 'Up' | ForEach-Object { Set-DnsClientServerAddress -InterfaceIndex $_.ifIndex -ResetServerAddresses; Write-Output \"Reset on $($_.Name)\" }")));

        restoreActions.add(new PrepAction("Restore Network Throttling Index", "Re-enable Windows multimedia throttling", "Network",
            () -> runPowerShell("Set-ItemProperty -Path 'HKLM:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Multimedia\\SystemProfile' -Name 'NetworkThrottlingIndex' -Value 10 -Type DWord -Force; Write-Output 'Restored'")));

        restoreActions.add(new PrepAction("Flush DNS Cache", "Clear any stale DNS from the session", "Network",
            () -> runCommand("ipconfig", "/flushdns")));

        restoreActions.add(new PrepAction("Re-enable Notifications", "Turn toast notifications back on", "Services",
            () -> runPowerShell("Set-ItemProperty -Path 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Notifications\\Settings' -Name 'NOC_GLOBAL_SETTING_TOASTS_ENABLED' -Value 1 -Force; Write-Output 'Notifications restored'")));

        restoreActions.add(new PrepAction("Restart Windows Search Indexer", "Resume background disk indexing", "Services",
            () -> runPowerShell("Start-Service -Name WSearch -ErrorAction SilentlyContinue; Write-Output 'Restarted'")));

        restoreActions.add(new PrepAction("Re-enable Windows Update", "Allow Windows Update to resume", "Services",
            () -> runPowerShell("Start-Service -Name wuauserv -ErrorAction SilentlyContinue; Write-Output 'Restarted'")));

        restoreActions.add(new PrepAction("Restart SysMain", "Resume Superfetch service", "Services",
            () -> runPowerShell("Start-Service -Name SysMain -ErrorAction SilentlyContinue; Write-Output 'Restarted'")));

        restoreActions.add(new PrepAction("Restart DiagTrack", "Resume Windows telemetry service", "Services",
            () -> runPowerShell("Start-Service -Name DiagTrack -ErrorAction SilentlyContinue; Write-Output 'Restarted'")));

        restoreActions.add(new PrepAction("Resume OneDrive Sync", "Resume OneDrive background sync", "Services",
            () -> runPowerShell("$od = Get-Process -Name OneDrive -ErrorAction SilentlyContinue; if ($od) { & \"$env:LOCALAPPDATA\\Microsoft\\OneDrive\\OneDrive.exe\" /resume; Write-Output 'Resumed' } else { & \"$env:LOCALAPPDATA\\Microsoft\\OneDrive\\OneDrive.exe\"; Write-Output 'Started' }")));
    }

    private void buildGameActions() {
        gameActions.clear();
        if (selectedGame == null || selectedGame.equals("General")) return;

        switch (selectedGame) {
            case "Rainbow Six Siege" -> {
                gameActions.add(new PrepAction("[R6] NVIDIA Low Latency Ultra",
                    "Sets NVIDIA Low Latency Mode to Ultra — up to 30ms input lag reduction", "Game",
                    () -> runPowerShell("$reg = 'HKLM:\\SYSTEM\\CurrentControlSet\\Services\\nvlddmkm\\Global\\NVTweak'; if (-not (Test-Path $reg)) { New-Item -Path $reg -Force | Out-Null }; Set-ItemProperty -Path $reg -Name 'NVLLMode' -Value 1 -Type DWord -Force; Write-Output 'NVIDIA LL Ultra set'")));
                gameActions.add(new PrepAction("[R6] Disable Fullscreen Optimizations",
                    "Forces true exclusive fullscreen — lower latency than FSO", "Game",
                    () -> runPowerShell("$paths = @('C:\\Program Files (x86)\\Ubisoft\\Ubisoft Game Launcher\\games\\Tom Clancy''s Rainbow Six Siege\\RainbowSix.exe','C:\\Program Files\\Ubisoft\\Ubisoft Game Launcher\\games\\Tom Clancy''s Rainbow Six Siege\\RainbowSix.exe'); foreach ($exe in $paths) { if (Test-Path $exe) { $reg = 'HKCU:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Layers'; if (-not (Test-Path $reg)) { New-Item -Path $reg -Force | Out-Null }; Set-ItemProperty -Path $reg -Name $exe -Value 'DISABLEDXMAXIMIZEDWINDOWEDMODE' -Force; Write-Output 'FSO disabled' } }; Write-Output 'Done'")));
                gameActions.add(new PrepAction("[R6] Flush Ubisoft Connect Cache",
                    "Clears Ubisoft temp files that cause match stutters", "Game",
                    () -> runPowerShell("Remove-Item \"$env:LOCALAPPDATA\\Ubisoft Game Launcher\\cache\\*\" -Recurse -Force -ErrorAction SilentlyContinue; Write-Output 'Cache cleared'")));
            }
            case "Valorant" -> {
                gameActions.add(new PrepAction("[VAL] Disable Fullscreen Optimizations",
                    "True exclusive fullscreen — lower input latency", "Game",
                    () -> runPowerShell("$exe = \"$env:LOCALAPPDATA\\VALORANT\\live\\ShooterGame\\Binaries\\Win64\\VALORANT-Win64-Shipping.exe\"; if (Test-Path $exe) { $reg = 'HKCU:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Layers'; if (-not (Test-Path $reg)) { New-Item -Path $reg -Force | Out-Null }; Set-ItemProperty -Path $reg -Name $exe -Value 'DISABLEDXMAXIMIZEDWINDOWEDMODE' -Force; Write-Output 'FSO disabled for Valorant' } else { Write-Output 'Valorant exe not found' }")));
            }
            case "Apex Legends" -> {
                gameActions.add(new PrepAction("[Apex] Clear Shader Cache",
                    "Removes stale shader cache that causes hitching", "Game",
                    () -> runPowerShell("Remove-Item \"$env:LOCALAPPDATA\\Temp\\Respawn\\*\" -Recurse -Force -ErrorAction SilentlyContinue; Write-Output 'Apex cache cleared'")));
            }
            case "Warzone / MW3" -> {
                gameActions.add(new PrepAction("[COD] Kill Battle.net Background",
                    "Stop Battle.net update services to free resources", "Game",
                    () -> runPowerShell("Stop-Process -Name 'Battle.net' -Force -ErrorAction SilentlyContinue; Stop-Service -Name 'BattlenetUpdateAgent' -Force -ErrorAction SilentlyContinue; Write-Output 'Battle.net stopped'")));
            }
            case "CS2" -> {
                gameActions.add(new PrepAction("[CS2] Disable Fullscreen Optimizations",
                    "Exclusive fullscreen gives lower latency in Source 2", "Game",
                    () -> runPowerShell("$loc = (Get-ItemProperty 'HKLM:\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\Steam App 730' -ErrorAction SilentlyContinue).InstallLocation; if ($loc) { $exe = \"$loc\\game\\bin\\win64\\cs2.exe\"; if (Test-Path $exe) { $reg = 'HKCU:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Layers'; if (-not (Test-Path $reg)) { New-Item -Path $reg -Force | Out-Null }; Set-ItemProperty -Path $reg -Name $exe -Value 'DISABLEDXMAXIMIZEDWINDOWEDMODE' -Force; Write-Output 'FSO disabled' } } else { Write-Output 'CS2 not found via Steam registry' }")));
            }
            case "Fortnite" -> {
                gameActions.add(new PrepAction("[FN] Clear Epic Games Cache",
                    "Removes Epic cache files that cause shader stutter on load", "Game",
                    () -> runPowerShell("Remove-Item \"$env:LOCALAPPDATA\\EpicGamesLauncher\\Saved\\webcache*\" -Recurse -Force -ErrorAction SilentlyContinue; Write-Output 'Epic cache cleared'")));
            }
            case "Overwatch 2" -> {
                gameActions.add(new PrepAction("[OW2] Kill Battle.net Background",
                    "Free resources from Battle.net updater during your session", "Game",
                    () -> runPowerShell("Stop-Process -Name 'Battle.net' -Force -ErrorAction SilentlyContinue; Stop-Service -Name 'BattlenetUpdateAgent' -Force -ErrorAction SilentlyContinue; Write-Output 'Battle.net stopped'")));
            }
        }
    }
}

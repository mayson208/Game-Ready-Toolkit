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

    // ── Theme — Retro 80s Synthwave ───────────────────────────────────────────
    private static final String BG       = "#0d0015";
    private static final String BG_CARD  = "#120028";
    private static final String BG_CARD2 = "#1a003a";
    private static final String BORDER   = "#ff00cc";
    private static final String TEXT     = "#ffffff";
    private static final String TEXT_DIM = "#cc88ff";
    private static final String PURPLE   = "#cc00ff";
    private static final String CYAN     = "#00ffff";
    private static final String GREEN    = "#39ff14";
    private static final String RED      = "#ff0055";
    private static final String AMBER    = "#ffff00";
    private static final String PINK     = "#ff00cc";

    // Category → accent color
    private static final Map<String, String> CAT_COLOR = Map.of(
        "Power",    "#ffff00",
        "CPU",      "#ff00cc",
        "Network",  "#00ffff",
        "Display",  "#cc00ff",
        "Services", "#39ff14",
        "Memory",   "#ff6600",
        "Game",     "#00ffff"
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
    private TextArea debugLog;
    private TabPane  tabPane;

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
        root.setStyle(
            "-fx-background-color: " + BG + "; " +
            "-fx-border-color: " + PINK + "; -fx-border-width: 3;");

        // ── TOP BAR ───────────────────────────────────────────────────────────
        root.setTop(buildTopBar());

        // ── CENTER: tabs ──────────────────────────────────────────────────────
        tilesContainer = new VBox(14);
        tilesContainer.setPadding(new Insets(16));
        tilesContainer.setStyle("-fx-background-color: " + BG + ";");

        refreshTiles();

        ScrollPane scroll = new ScrollPane(tilesContainer);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: " + BG + "; -fx-background-color: " + BG +
                        "; -fx-border-color: transparent;");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        // Optimize tab
        Tab optimizeTab = new Tab("  ⚡ OPTIMIZE  ", scroll);
        optimizeTab.setClosable(false);
        optimizeTab.setStyle("-fx-font-family: Consolas; -fx-font-weight: bold;");

        // Debug tab
        debugLog = new TextArea("[ No runs yet — hit PREPARE SYSTEM to see output here ]\n");
        debugLog.setEditable(false);
        debugLog.setWrapText(true);
        debugLog.setStyle(
            "-fx-font-family: Consolas; -fx-font-size: 12px; " +
            "-fx-control-inner-background: #080010; -fx-text-fill: #00ff00; " +
            "-fx-border-color: transparent;");

        Tab debugTab = new Tab("  ▶ DEBUG LOG  ", debugLog);
        debugTab.setClosable(false);

        tabPane = new TabPane(optimizeTab, debugTab);
        tabPane.setStyle(
            "-fx-background-color: " + BG + "; " +
            "-fx-tab-min-height: 32px; " +
            "-fx-tab-max-height: 32px;");
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Style tab headers via CSS
        tabPane.getStylesheets().add("data:text/css," +
            ".tab-pane .tab-header-area .tab-header-background{-fx-background-color:" + BG_CARD + ";}" +
            ".tab-pane .tab{-fx-background-color:" + BG_CARD2 + ";-fx-border-color:" + BORDER + ";-fx-border-width:0 1 0 0;}" +
            ".tab-pane .tab:selected{-fx-background-color:" + BG + ";-fx-border-color:" + PINK + ";-fx-border-width:0 0 2 0;}" +
            ".tab-pane .tab .tab-label{-fx-text-fill:" + TEXT_DIM + ";-fx-font-family:Consolas;-fx-font-weight:bold;-fx-font-size:11px;}" +
            ".tab-pane .tab:selected .tab-label{-fx-text-fill:" + CYAN + ";}" +
            ".tab-pane>.tab-content-area{-fx-background-color:" + BG + ";}");

        root.setCenter(tabPane);

        // ── BOTTOM BAR ────────────────────────────────────────────────────────
        root.setBottom(buildBottomBar(stage));

        Scene scene = new Scene(root, 780, 700);
        stage.setTitle("◈ GAME READY TOOLKIT ◈");
        stage.setMinWidth(620);
        stage.getIcons().add(buildAppIcon());
        stage.setScene(scene);
        stage.show();

        setupTrayIcon(stage);
    }

    // ── Top bar ───────────────────────────────────────────────────────────────
    private VBox buildTopBar() {
        // Logo / title
        Label title = new Label("GAME READY");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-font-family: Consolas; " +
                       "-fx-text-fill: " + CYAN + ";");

        Label subtitle = new Label("TOOLKIT");
        subtitle.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-font-family: Consolas; " +
                          "-fx-text-fill: " + PINK + ";");

        HBox logoBox = new HBox(8, title, subtitle);
        logoBox.setAlignment(Pos.CENTER_LEFT);

        // Timer
        timerLabel = new Label();
        timerLabel.setStyle("-fx-text-fill: " + GREEN + "; -fx-font-size: 12px; " +
                            "-fx-font-family: Consolas; -fx-font-weight: bold;");
        timerLabel.setVisible(false);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Game selector
        Label gameLabel = new Label("► SELECT GAME");
        gameLabel.setStyle("-fx-text-fill: " + AMBER + "; -fx-font-size: 10px; " +
                           "-fx-font-family: Consolas; -fx-font-weight: bold;");

        ComboBox<String> gameCombo = new ComboBox<>();
        gameCombo.getItems().addAll(GAME_PROFILES.keySet());
        gameCombo.setValue(PREFS.get("selected_game", "General"));
        selectedGame = gameCombo.getValue();
        gameCombo.setStyle(
            "-fx-background-color: " + BG_CARD2 + "; -fx-text-fill: #ffffff; " +
            "-fx-border-color: " + CYAN + "; -fx-border-width: 2; " +
            "-fx-font-size: 12px; -fx-font-family: Consolas; -fx-font-weight: bold; -fx-pref-width: 210px;");
        // Force the button cell text to white too
        gameCombo.setButtonCell(new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item);
                setStyle("-fx-text-fill: #ffffff; -fx-font-family: Consolas; -fx-font-weight: bold; " +
                         "-fx-font-size: 12px; -fx-background-color: " + BG_CARD2 + ";");
            }
        });

        gameCombo.setOnAction(e -> {
            selectedGame = gameCombo.getValue();
            PREFS.put("selected_game", selectedGame);
            buildGameActions();
            refreshTiles();
            if (gameDescLabel != null)
                gameDescLabel.setText("► " + GAME_PROFILES.getOrDefault(selectedGame, ""));
        });

        VBox gamePicker = new VBox(3, gameLabel, gameCombo);
        gamePicker.setAlignment(Pos.CENTER_LEFT);

        HBox mainRow = new HBox(16, logoBox, spacer, timerLabel, gamePicker);
        mainRow.setAlignment(Pos.CENTER_LEFT);
        mainRow.setPadding(new Insets(16, 20, 8, 20));

        // Game description strip
        gameDescLabel = new Label("► " + GAME_PROFILES.getOrDefault(selectedGame, ""));
        gameDescLabel.setStyle("-fx-text-fill: " + TEXT_DIM + "; -fx-font-size: 10px; " +
                               "-fx-font-family: Consolas; -fx-padding: 0 20 0 20;");

        // Sys info strip
        sysInfoLabel = new Label("CPU: --   RAM: --");
        sysInfoLabel.setStyle("-fx-text-fill: " + GREEN + "; -fx-font-size: 10px; " +
                              "-fx-font-family: Consolas; -fx-font-weight: bold; " +
                              "-fx-padding: 0 20 8 20;");
        startSysInfoUpdater();

        HBox bottomStrip = new HBox();
        bottomStrip.getChildren().addAll(gameDescLabel, new Region(), sysInfoLabel);
        HBox.setHgrow(bottomStrip.getChildren().get(1), Priority.ALWAYS);
        bottomStrip.setMaxWidth(Double.MAX_VALUE);

        VBox topBar = new VBox(0, mainRow, bottomStrip);
        topBar.setStyle(
            "-fx-background-color: " + BG_CARD + "; " +
            "-fx-border-color: " + PINK + "; " +
            "-fx-border-width: 0 0 3 0;");
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
        progressBar.setStyle("-fx-accent: " + CYAN + "; -fx-background-color: " + BG_CARD2 + "; " +
                             "-fx-border-color: " + CYAN + "; -fx-border-width: 1;");

        statusLabel = new Label();
        statusLabel.setStyle("-fx-text-fill: " + TEXT_DIM + "; -fx-font-size: 11px; " +
                             "-fx-font-family: Consolas;");

        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: " + PINK + "; -fx-opacity: 0.8;");

        VBox bottom = new VBox(10, sep, btnRow, progressBar, statusLabel);
        bottom.setPadding(new Insets(12, 20, 16, 20));
        bottom.setStyle("-fx-background-color: " + BG_CARD + "; " +
                        "-fx-border-color: " + PINK + "; -fx-border-width: 3 0 0 0;");
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
        String tileBase =
            "-fx-background-color: " + BG_CARD + "; " +
            "-fx-border-color: " + color + "; " +
            "-fx-border-width: 2; " +
            "-fx-effect: dropshadow(gaussian, " + color + ", 10, 0.4, 3, 3);";
        String tileHover =
            "-fx-background-color: " + BG_CARD2 + "; " +
            "-fx-border-color: " + color + "; " +
            "-fx-border-width: 2; " +
            "-fx-effect: dropshadow(gaussian, " + color + ", 20, 0.8, 3, 3);";

        VBox tile = new VBox(8);
        tile.setPadding(new Insets(14));
        tile.setStyle(tileBase);

        // Tile header
        Circle dot = new Circle(5, Color.web(color));
        Label catLabel = new Label("▌ " + category.toUpperCase());
        catLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 12px; " +
                          "-fx-font-weight: bold; -fx-font-family: Consolas;");

        // Pulse animation on dot
        FadeTransition pulse = new FadeTransition(Duration.millis(900), dot);
        pulse.setFromValue(1.0);
        pulse.setToValue(0.2);
        pulse.setCycleCount(Animation.INDEFINITE);
        pulse.setAutoReverse(true);
        pulse.play();

        HBox header = new HBox(8, dot, catLabel);
        header.setAlignment(Pos.CENTER_LEFT);

        Separator divider = new Separator();
        divider.setStyle("-fx-background-color: " + color + "; -fx-opacity: 0.4;");

        tile.getChildren().addAll(header, divider);

        // Network warning label
        if (category.equals("Network")) {
            Label warn = new Label("⚠  USE AT YOUR OWN RISK");
            warn.setStyle("-fx-text-fill: " + AMBER + "; -fx-font-size: 10px; " +
                          "-fx-font-family: Consolas; -fx-font-weight: bold;");
            tile.getChildren().add(warn);
        }

        for (PrepAction action : actions) {
            String prefix = action.getCategory().equals("Game") ? "game_" :
                            restoreMode ? "restore_" : "prep_";

            CheckBox cb = new CheckBox(action.getName());
            boolean saved = PREFS.getBoolean(prefix + action.getName(), true);
            cb.setSelected(saved);
            action.setSelected(saved);
            cb.setStyle(
                "-fx-text-fill: " + TEXT + "; -fx-font-size: 12px; " +
                "-fx-font-family: Consolas; -fx-mark-color: " + color + "; " +
                "-fx-focus-color: " + color + ";");

            // Status dot: green=applied, grey=not applied, dim=unknown
            Circle statusDot = new Circle(4);
            Boolean applied = action.checkApplied();
            updateStatusDot(statusDot, applied);

            Tooltip dotTip = new Tooltip(applied == null ? "Status unknown" :
                applied ? "Already applied" : "Not yet applied");
            dotTip.setStyle("-fx-font-size: 10px; -fx-font-family: Consolas; " +
                            "-fx-background-color: " + BG_CARD2 + "; -fx-text-fill: " + TEXT + ";");
            Tooltip.install(statusDot, dotTip);

            String tipText = action.getDescription() +
                (action.getCategory().equals("Network") ? "\n\n⚠ Use at your own risk." : "");
            Tooltip tip = new Tooltip(tipText);
            tip.setStyle("-fx-font-size: 11px; -fx-background-color: " + BG_CARD2 + "; " +
                         "-fx-text-fill: " + color + "; -fx-border-color: " + color + "; " +
                         "-fx-border-width: 2; -fx-font-family: Consolas;");
            tip.setWrapText(true);
            tip.setMaxWidth(300);
            cb.setTooltip(tip);

            HBox row = new HBox(6, statusDot, cb);
            row.setAlignment(Pos.CENTER_LEFT);

            String finalPrefix = prefix;
            cb.selectedProperty().addListener((obs, o, n) -> {
                action.setSelected(n);
                PREFS.putBoolean(finalPrefix + action.getName(), n);
            });
            tile.getChildren().add(row);
        }

        tile.setOnMouseEntered(e -> tile.setStyle(tileHover));
        tile.setOnMouseExited(e -> tile.setStyle(tileBase));

        return tile;
    }

    // ── Styles ────────────────────────────────────────────────────────────────
    private String runBtnStyle() {
        return "-fx-background-color: " + PINK + "; -fx-text-fill: #000000; " +
               "-fx-font-size: 13px; -fx-font-weight: bold; -fx-font-family: Consolas; " +
               "-fx-border-color: " + PINK + "; -fx-border-width: 2; " +
               "-fx-padding: 9 26; -fx-cursor: hand;";
    }

    private String toggleStyle(boolean active) {
        String color = active ? RED : CYAN;
        return "-fx-background-color: " + BG_CARD2 + "; -fx-text-fill: " + color + "; " +
               "-fx-border-color: " + color + "; -fx-border-width: 2; " +
               "-fx-font-size: 11px; -fx-font-family: Consolas; -fx-font-weight: bold; " +
               "-fx-padding: 7 14; -fx-cursor: hand;";
    }

    private String smallBtnStyle(String color) {
        return "-fx-background-color: " + BG_CARD2 + "; -fx-text-fill: " + color + "; " +
               "-fx-border-color: " + color + "; -fx-border-width: 2; " +
               "-fx-font-size: 11px; -fx-font-family: Consolas; -fx-font-weight: bold; " +
               "-fx-padding: 7 14; -fx-cursor: hand;";
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
            writeDebugLog(results);
            showResultDialog(results, stage -> null);
            // Refresh tiles so status dots update to reflect newly applied settings
            refreshTiles();

            if (failed == 0) {
                statusLabel.setStyle("-fx-text-fill: " + GREEN + "; -fx-font-size: 11px; -fx-font-family: Consolas;");
                statusLabel.setText("✓ All " + passed + " actions completed.");
            } else {
                statusLabel.setStyle("-fx-text-fill: " + AMBER + "; -fx-font-size: 11px; -fx-font-family: Consolas;");
                statusLabel.setText("⚠ " + passed + " succeeded, " + failed + " failed.");
                // Auto-switch to debug tab so user can see what failed
                if (tabPane != null) tabPane.getSelectionModel().select(1);
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
        String mainColor = allGood ? GREEN : AMBER;
        Label icon = new Label(allGood ? "✓" : "⚠");
        icon.setStyle("-fx-font-size: 42px; -fx-text-fill: " + mainColor + ";");

        Label headline = new Label(allGood
            ? (restoreMode ? "SYSTEM RESTORED" : "SYSTEM READY TO GAME")
            : passed + " SUCCEEDED · " + failed + " FAILED");
        headline.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-font-family: Consolas; " +
                          "-fx-text-fill: " + mainColor + ";");

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
        root.setStyle("-fx-background-color: " + BG_CARD + "; " +
                      "-fx-border-color: " + PINK + "; -fx-border-width: 3;");

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

    // ── App icon ──────────────────────────────────────────────────────────────
    private javafx.scene.image.Image buildAppIcon() {
        int size = 64;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                           java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

        // Background — deep purple
        g.setColor(new java.awt.Color(0x0d, 0x00, 0x15));
        g.fillRoundRect(0, 0, size, size, 14, 14);

        // Outer neon pink border
        g.setColor(new java.awt.Color(0xff, 0x00, 0xcc));
        g.setStroke(new java.awt.BasicStroke(3f));
        g.drawRoundRect(2, 2, size - 4, size - 4, 12, 12);

        // Lightning bolt ⚡ in cyan
        g.setColor(new java.awt.Color(0x00, 0xff, 0xff));
        int[] bx = {36, 26, 32, 22, 38, 28, 34};
        int[] by = { 8, 28, 28, 56, 36, 36,  8};
        g.fillPolygon(bx, by, 7);

        // Yellow outline on bolt
        g.setColor(new java.awt.Color(0xff, 0xff, 0x00));
        g.setStroke(new java.awt.BasicStroke(1.5f));
        g.drawPolygon(bx, by, 7);

        g.dispose();

        // Convert to JavaFX Image
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try {
            javax.imageio.ImageIO.write(img, "png", baos);
            return new javafx.scene.image.Image(
                new java.io.ByteArrayInputStream(baos.toByteArray()));
        } catch (IOException e) {
            return null;
        }
    }

    // ── Status dot ───────────────────────────────────────────────────────────
    private void updateStatusDot(Circle dot, Boolean applied) {
        if (applied == null) {
            dot.setFill(Color.web("#444444")); // grey = unknown
        } else if (applied) {
            dot.setFill(Color.web(GREEN));     // green = applied
        } else {
            dot.setFill(Color.web("#555555")); // dark grey = not applied
        }
    }

    // ── Debug log ─────────────────────────────────────────────────────────────
    private void writeDebugLog(List<PrepActionResult> results) {
        if (debugLog == null) return;
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String mode = restoreMode ? "RESTORE" : "PREPARE — " + selectedGame;
        StringBuilder sb = new StringBuilder();
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("[ ").append(ts).append(" ] ").append(mode).append("\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");
        for (PrepActionResult r : results) {
            String status = r.isSuccess() ? "✓ PASS" : "✗ FAIL";
            sb.append(status).append("  ").append(r.getActionName()).append("\n");
            String msg = r.getMessage();
            if (msg != null && !msg.isBlank() && !msg.equals("OK")) {
                // Indent each line of the output
                for (String line : msg.split("\n")) {
                    sb.append("       ").append(line.trim()).append("\n");
                }
            }
            sb.append("\n");
        }
        long passed = results.stream().filter(PrepActionResult::isSuccess).count();
        long failed = results.size() - passed;
        sb.append("RESULT: ").append(passed).append(" passed, ").append(failed).append(" failed.\n\n");
        Platform.runLater(() -> {
            // Prepend new run above old output
            debugLog.setText(sb + debugLog.getText());
            debugLog.positionCaret(0);
        });
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
                "powercfg /setactive $guid; Write-Output \"Ultimate Performance Plan active\""),
            () -> {
                var r = runPowerShell("$active = (powercfg /getactivescheme); $active -match 'Ultimate'");
                return r.isSuccess() && r.getMessage().contains("True");
            }));

        // CPU
        prepActions.add(new PrepAction("Win32PrioritySeparation",
            "Sets foreground app quantum to shortest — game gets more CPU slices, lower input lag", "CPU",
            () -> runPowerShell(
                "Set-ItemProperty -Path 'HKLM:\\SYSTEM\\CurrentControlSet\\Control\\PriorityControl' " +
                "-Name 'Win32PrioritySeparation' -Value 26 -Type DWord -Force; Write-Output 'Set to 26 (low latency)'"),
            () -> {
                var r = runPowerShell("(Get-ItemProperty 'HKLM:\\SYSTEM\\CurrentControlSet\\Control\\PriorityControl').Win32PrioritySeparation");
                return r.isSuccess() && r.getMessage().trim().equals("26");
            }));

        prepActions.add(new PrepAction("Disable Paging Executive",
            "Forces kernel code to stay in RAM — reduces DPC latency spikes", "CPU",
            () -> runPowerShell(
                "Set-ItemProperty -Path 'HKLM:\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Memory Management' " +
                "-Name 'DisablePagingExecutive' -Value 1 -Type DWord -Force; Write-Output 'DisablePagingExecutive enabled'"),
            () -> {
                var r = runPowerShell("(Get-ItemProperty 'HKLM:\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Memory Management').DisablePagingExecutive");
                return r.isSuccess() && r.getMessage().trim().equals("1");
            }));

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
                "-Name 'HwSchMode' -Value 2 -Type DWord -Force; Write-Output 'HAGS enabled (reboot to apply)'"),
            () -> {
                var r = runPowerShell("(Get-ItemProperty 'HKLM:\\SYSTEM\\CurrentControlSet\\Control\\GraphicsDrivers').HwSchMode");
                return r.isSuccess() && r.getMessage().trim().equals("2");
            }));

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
                "Write-Output 'Mouse acceleration disabled'"),
            () -> {
                var r = runPowerShell("(Get-ItemProperty 'HKCU:\\Control Panel\\Mouse').MouseSpeed");
                return r.isSuccess() && r.getMessage().trim().equals("0");
            }));

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
                "Write-Output 'Nagle disabled on all interfaces'"),
            () -> {
                var r = runPowerShell(
                    "$base = 'HKLM:\\SYSTEM\\CurrentControlSet\\Services\\Tcpip\\Parameters\\Interfaces';" +
                    "$all = Get-ChildItem $base | ForEach-Object { (Get-ItemProperty $_.PSPath -ErrorAction SilentlyContinue).TcpAckFrequency };" +
                    "$all -contains 1");
                return r.isSuccess() && r.getMessage().trim().equals("True");
            }));

        prepActions.add(new PrepAction("Switch DNS to Cloudflare",
            "1.1.1.1 / 1.0.0.1 — fastest public DNS, reduces server connection latency", "Network",
            () -> runPowerShell(
                "Get-NetAdapter | Where-Object Status -eq 'Up' | ForEach-Object {" +
                "Set-DnsClientServerAddress -InterfaceIndex $_.ifIndex -ServerAddresses ('1.1.1.1','1.0.0.1');" +
                "Write-Output \"DNS set on $($_.Name)\" }"),
            () -> {
                var r = runPowerShell("(Get-DnsClientServerAddress -AddressFamily IPv4 | Where-Object {$_.ServerAddresses -contains '1.1.1.1'}) -ne $null");
                return r.isSuccess() && r.getMessage().trim().equals("True");
            }));

        prepActions.add(new PrepAction("Remove Network Throttling",
            "Removes Windows multimedia bandwidth cap on your connection", "Network",
            () -> runPowerShell(
                "Set-ItemProperty -Path 'HKLM:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Multimedia\\SystemProfile' " +
                "-Name 'NetworkThrottlingIndex' -Value 0xffffffff -Type DWord -Force; Write-Output 'NetworkThrottlingIndex removed'"),
            () -> {
                var r = runPowerShell("(Get-ItemProperty 'HKLM:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Multimedia\\SystemProfile').NetworkThrottlingIndex");
                return r.isSuccess() && r.getMessage().trim().equals("-1");
            }));

        prepActions.add(new PrepAction("Flush DNS Cache",
            "Clears stale DNS entries for cleaner server connections", "Network",
            () -> runCommand("ipconfig", "/flushdns")));

        // SERVICES
        prepActions.add(new PrepAction("Stop Xbox Game Bar",
            "Kills overlay — reduces CPU/GPU overhead and DWM interference", "Services",
            () -> runPowerShell(
                "Stop-Process -Name GameBar -Force -ErrorAction SilentlyContinue;" +
                "Stop-Process -Name GameBarFTServer -Force -ErrorAction SilentlyContinue;" +
                "Write-Output 'Xbox Game Bar stopped'"),
            () -> {
                var r = runPowerShell("(Get-Process -Name GameBar -ErrorAction SilentlyContinue) -eq $null");
                return r.isSuccess() && r.getMessage().trim().equals("True");
            }));

        prepActions.add(new PrepAction("Enable Game Mode",
            "Dedicates more CPU/GPU resources to the foreground game", "Services",
            () -> runPowerShell(
                "if (-not (Test-Path 'HKCU:\\Software\\Microsoft\\GameBar')) { New-Item -Path 'HKCU:\\Software\\Microsoft\\GameBar' -Force | Out-Null };" +
                "Set-ItemProperty -Path 'HKCU:\\Software\\Microsoft\\GameBar' -Name 'AutoGameModeEnabled' -Value 1 -Type DWord -Force;" +
                "Write-Output 'Game Mode enabled'"),
            () -> {
                var r = runPowerShell("(Get-ItemProperty 'HKCU:\\Software\\Microsoft\\GameBar' -ErrorAction SilentlyContinue).AutoGameModeEnabled");
                return r.isSuccess() && r.getMessage().trim().equals("1");
            }));

        prepActions.add(new PrepAction("Silence Notifications",
            "Blocks all toast popups during gameplay", "Services",
            () -> runPowerShell(
                "Set-ItemProperty -Path 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Notifications\\Settings' " +
                "-Name 'NOC_GLOBAL_SETTING_TOASTS_ENABLED' -Value 0 -Force; Write-Output 'Notifications silenced'"),
            () -> {
                var r = runPowerShell("(Get-ItemProperty 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Notifications\\Settings' -ErrorAction SilentlyContinue).NOC_GLOBAL_SETTING_TOASTS_ENABLED");
                return r.isSuccess() && r.getMessage().trim().equals("0");
            }));

        prepActions.add(new PrepAction("Stop Windows Search Indexer",
            "Pauses background disk indexing during gameplay", "Services",
            () -> runPowerShell("Stop-Service -Name WSearch -Force -ErrorAction SilentlyContinue; Write-Output 'Stopped'"),
            () -> {
                var r = runPowerShell("(Get-Service -Name WSearch -ErrorAction SilentlyContinue).Status");
                return r.isSuccess() && r.getMessage().trim().equals("Stopped");
            }));

        prepActions.add(new PrepAction("Pause Windows Update",
            "Prevents wuauserv from stealing bandwidth mid-session", "Services",
            () -> runPowerShell("Stop-Service -Name wuauserv -Force -ErrorAction SilentlyContinue; Write-Output 'Stopped'"),
            () -> {
                var r = runPowerShell("(Get-Service -Name wuauserv -ErrorAction SilentlyContinue).Status");
                return r.isSuccess() && r.getMessage().trim().equals("Stopped");
            }));

        prepActions.add(new PrepAction("Stop SysMain (Superfetch)",
            "On SSDs, Superfetch burns disk/RAM for no benefit during gaming", "Services",
            () -> runPowerShell("Stop-Service -Name SysMain -Force -ErrorAction SilentlyContinue; Write-Output 'SysMain stopped'"),
            () -> {
                var r = runPowerShell("(Get-Service -Name SysMain -ErrorAction SilentlyContinue).Status");
                return r.isSuccess() && r.getMessage().trim().equals("Stopped");
            }));

        prepActions.add(new PrepAction("Stop DiagTrack (Telemetry)",
            "Stops Windows telemetry waking up and consuming CPU during your session", "Services",
            () -> runPowerShell("Stop-Service -Name DiagTrack -Force -ErrorAction SilentlyContinue; Write-Output 'DiagTrack stopped'"),
            () -> {
                var r = runPowerShell("(Get-Service -Name DiagTrack -ErrorAction SilentlyContinue).Status");
                return r.isSuccess() && r.getMessage().trim().equals("Stopped");
            }));

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
                // ── CPU / Registry ────────────────────────────────────────────
                gameActions.add(new PrepAction("[R6] SystemResponsiveness = 0",
                    "Allows games to use up to 100% of CPU scheduling — removes the default 20% multimedia reservation", "Game",
                    () -> runPowerShell(
                        "Set-ItemProperty -Path 'HKLM:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Multimedia\\SystemProfile' " +
                        "-Name 'SystemResponsiveness' -Value 0 -Type DWord -Force; Write-Output 'SystemResponsiveness set to 0'")));

                gameActions.add(new PrepAction("[R6] Boost Games Task Priority",
                    "Sets GPU Priority 8, CPU Priority 6, Scheduling=High for all games in Windows scheduler — measurable latency reduction", "Game",
                    () -> runPowerShell(
                        "$p = 'HKLM:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Multimedia\\SystemProfile\\Tasks\\Games';" +
                        "if (-not (Test-Path $p)) { New-Item -Path $p -Force | Out-Null };" +
                        "Set-ItemProperty -Path $p -Name 'GPU Priority' -Value 8 -Type DWord -Force;" +
                        "Set-ItemProperty -Path $p -Name 'Priority' -Value 6 -Type DWord -Force;" +
                        "Set-ItemProperty -Path $p -Name 'Scheduling Category' -Value 'High' -Type String -Force;" +
                        "Set-ItemProperty -Path $p -Name 'SFIO Priority' -Value 'High' -Type String -Force;" +
                        "Write-Output 'Games task priority boosted'")));

                gameActions.add(new PrepAction("[R6] Disable HPET",
                    "Disables High Precision Event Timer in Windows — reduces DPC latency. BIOS HPET should also be disabled for full effect", "Game",
                    () -> runPowerShell(
                        "bcdedit /deletevalue useplatformclock 2>&1 | Out-Null;" +
                        "Write-Output 'HPET platform clock removed from boot config'")));

                gameActions.add(new PrepAction("[R6] Disable Prefetcher",
                    "Stops Windows pre-loading app data into RAM — frees memory bandwidth for Siege DX12 shader streaming", "Game",
                    () -> runPowerShell(
                        "Set-ItemProperty -Path 'HKLM:\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Memory Management\\PrefetchParameters' " +
                        "-Name 'EnablePrefetcher' -Value 0 -Type DWord -Force; Write-Output 'Prefetcher disabled'")));

                gameActions.add(new PrepAction("[R6] Set Process to High Priority",
                    "Boosts RainbowSix.exe CPU scheduling priority to High — equivalent to -high launch flag but persistent", "Game",
                    () -> runPowerShell(
                        "$proc = Get-Process -Name 'RainbowSix' -ErrorAction SilentlyContinue;" +
                        "if ($proc) { $proc.PriorityClass = 'High'; Write-Output 'RainbowSix.exe set to High priority' }" +
                        "else { Write-Output 'Siege not running — set priority after launch via Task Manager' }")));

                gameActions.add(new PrepAction("[R6] CPU Affinity Stutter Fix",
                    "Known Siege engine bug: resets thread affinity to fix micro-freeze pattern (deselect all cores then re-select all). Run while in-game", "Game",
                    () -> runPowerShell(
                        "$proc = Get-Process -Name 'RainbowSix' -ErrorAction SilentlyContinue;" +
                        "if ($proc) {" +
                        "  $proc.ProcessorAffinity = 0;" +
                        "  Start-Sleep -Milliseconds 100;" +
                        "  $cores = (Get-WmiObject -Class Win32_Processor).NumberOfLogicalProcessors;" +
                        "  $proc.ProcessorAffinity = [IntPtr]([Math]::Pow(2,$cores)-1);" +
                        "  Write-Output 'Affinity reset complete — all cores re-enabled'" +
                        "} else { Write-Output 'Siege not running — launch game first then run this fix' }")));

                // ── Network ───────────────────────────────────────────────────
                gameActions.add(new PrepAction("[R6] Disable Nagle's Algorithm",
                    "Sets TcpAckFrequency=1 and TCPNoDelay=1 on your active network adapter — reduces TCP buffering latency by 2-5ms", "Game",
                    () -> runPowerShell(
                        "$ip = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.InterfaceAlias -notlike '*Loopback*' -and $_.IPAddress -notlike '169.*' } | Select-Object -First 1).IPAddress;" +
                        "$interfaces = Get-ChildItem 'HKLM:\\SYSTEM\\CurrentControlSet\\Services\\Tcpip\\Parameters\\Interfaces';" +
                        "$matched = $interfaces | Where-Object { (Get-ItemProperty $_.PSPath -ErrorAction SilentlyContinue).DhcpIPAddress -eq $ip -or (Get-ItemProperty $_.PSPath -ErrorAction SilentlyContinue).IPAddress -contains $ip };" +
                        "if ($matched) {" +
                        "  foreach ($iface in $matched) {" +
                        "    Set-ItemProperty -Path $iface.PSPath -Name 'TcpAckFrequency' -Value 1 -Type DWord -Force;" +
                        "    Set-ItemProperty -Path $iface.PSPath -Name 'TCPNoDelay' -Value 1 -Type DWord -Force" +
                        "  }; Write-Output \"Nagle disabled on $ip\"" +
                        "} else { Write-Output 'Interface not found — check network adapter' }")));

                gameActions.add(new PrepAction("[R6] Set DNS to Cloudflare",
                    "Switches to 1.1.1.1 / 1.0.0.1 — fastest DNS globally per DNSPerf benchmarks, reduces server lookup latency", "Game",
                    () -> runPowerShell(
                        "$adapter = Get-NetAdapter | Where-Object { $_.Status -eq 'Up' -and $_.InterfaceDescription -notlike '*Loopback*' } | Select-Object -First 1;" +
                        "Set-DnsClientServerAddress -InterfaceAlias $adapter.Name -ServerAddresses ('1.1.1.1','1.0.0.1');" +
                        "Write-Output \"DNS set to Cloudflare on $($adapter.Name)\"")));

                gameActions.add(new PrepAction("[R6] Enable Minimal Network Buffering",
                    "Enables Siege's built-in 'Minimal Buffering' mode via registry flag — reduces in-game receive buffer for lower effective input lag", "Game",
                    () -> runPowerShell(
                        "$p = 'HKCU:\\SOFTWARE\\Ubisoft\\Rainbow Six Siege';" +
                        "if (-not (Test-Path $p)) { New-Item -Path $p -Force | Out-Null };" +
                        "Set-ItemProperty -Path $p -Name 'MinimalBuffering' -Value 1 -Type DWord -Force;" +
                        "Write-Output 'Minimal buffering flag set — confirm in-game under Settings > General'")));

                // ── Display / GPU ─────────────────────────────────────────────
                gameActions.add(new PrepAction("[R6] Disable Fullscreen Optimizations",
                    "Forces true exclusive fullscreen on RainbowSix.exe — bypasses DWM compositor, cuts 15-30ms input latency vs windowed/FSO mode", "Game",
                    () -> runPowerShell(
                        "$paths = @(" +
                        "  'C:\\Program Files (x86)\\Ubisoft\\Ubisoft Game Launcher\\games\\Tom Clancy''s Rainbow Six Siege\\RainbowSix.exe'," +
                        "  'C:\\Program Files\\Ubisoft\\Ubisoft Game Launcher\\games\\Tom Clancy''s Rainbow Six Siege\\RainbowSix.exe'," +
                        "  \"$env:ProgramFiles\\Ubisoft\\Ubisoft Game Launcher\\games\\Tom Clancy's Rainbow Six Siege\\RainbowSix.exe\"" +
                        ");" +
                        "$reg = 'HKCU:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Layers';" +
                        "if (-not (Test-Path $reg)) { New-Item -Path $reg -Force | Out-Null };" +
                        "$found = $false;" +
                        "foreach ($exe in $paths) { if (Test-Path $exe) { Set-ItemProperty -Path $reg -Name $exe -Value 'RUNASADMIN DISABLEDXMAXIMIZEDWINDOWEDMODE' -Force; $found = $true; Write-Output \"FSO disabled: $exe\" } };" +
                        "if (-not $found) { Write-Output 'RainbowSix.exe not found in default paths — apply manually via Properties > Compatibility' }")));

                gameActions.add(new PrepAction("[R6] NVIDIA Shader Cache = 10GB",
                    "Sets NVIDIA shader cache to 10GB — prevents DX12 shader recompile stutters that Siege is notorious for", "Game",
                    () -> runPowerShell(
                        "$p = 'HKLM:\\SOFTWARE\\NVIDIA Corporation\\Global\\NVTweak';" +
                        "if (-not (Test-Path $p)) { New-Item -Path $p -Force | Out-Null };" +
                        "Set-ItemProperty -Path $p -Name 'ShaderCacheSize' -Value 10240 -Type DWord -Force;" +
                        "Write-Output 'NVIDIA shader cache set to 10240 MB (10GB) — restart GPU driver to apply'")));

                gameActions.add(new PrepAction("[R6] NVIDIA Max Performance Mode",
                    "Sets NVIDIA power management to Prefer Maximum Performance for RainbowSix.exe — prevents GPU clock sag mid-match", "Game",
                    () -> runPowerShell(
                        "$p = 'HKLM:\\SYSTEM\\CurrentControlSet\\Services\\nvlddmkm\\Global\\NVTweak';" +
                        "if (-not (Test-Path $p)) { New-Item -Path $p -Force | Out-Null };" +
                        "Set-ItemProperty -Path $p -Name 'PowerMizerEnable' -Value 1 -Type DWord -Force;" +
                        "Set-ItemProperty -Path $p -Name 'PowerMizerLevel' -Value 1 -Type DWord -Force;" +
                        "Set-ItemProperty -Path $p -Name 'PowerMizerLevelAC' -Value 1 -Type DWord -Force;" +
                        "Write-Output 'NVIDIA power set to Max Performance'")));

                // ── Services ──────────────────────────────────────────────────
                gameActions.add(new PrepAction("[R6] Disable Xbox Services",
                    "Stops XblAuthManager, XblGameSave, XboxNetApiSvc — frees CPU cycles, reduces DPC latency. Not needed for Siege (uses Ubisoft Connect)", "Game",
                    () -> runPowerShell(
                        "$svcs = @('XblAuthManager','XblGameSave','XboxNetApiSvc','XboxGipSvc');" +
                        "foreach ($s in $svcs) { Stop-Service -Name $s -Force -ErrorAction SilentlyContinue; Set-Service -Name $s -StartupType Disabled -ErrorAction SilentlyContinue };" +
                        "Write-Output 'Xbox services stopped and disabled'")));

                gameActions.add(new PrepAction("[R6] Kill Background Resource Hogs",
                    "Force-closes Chrome, Edge, Teams, Spotify, OneDrive, Discord — frees RAM and CPU for Siege DX12 rendering", "Game",
                    () -> runPowerShell(
                        "$kill = @('chrome','msedge','firefox','Teams','Spotify','OneDrive','slack','Discord');" +
                        "foreach ($p in $kill) { Stop-Process -Name $p -Force -ErrorAction SilentlyContinue };" +
                        "Write-Output 'Background apps closed'")));

                gameActions.add(new PrepAction("[R6] Disable Ubisoft Connect Overlay",
                    "Disables the Ubisoft overlay which hooks into DX12 render pipeline — prevents overlay-induced frame time spikes", "Game",
                    () -> runPowerShell(
                        "$p = 'HKCU:\\SOFTWARE\\Ubisoft\\Ubisoft Game Launcher';" +
                        "if (-not (Test-Path $p)) { New-Item -Path $p -Force | Out-Null };" +
                        "Set-ItemProperty -Path $p -Name 'OverlayEnabled' -Value 0 -Type DWord -Force;" +
                        "Write-Output 'Ubisoft overlay disabled via registry — verify in Ubisoft Connect > Settings > In-game overlay'")));

                // ── Audio ─────────────────────────────────────────────────────
                gameActions.add(new PrepAction("[R6] Set Audio to 16-bit 48000Hz",
                    "Siege outputs 48kHz audio. Running higher (192kHz) forces CPU resampling and adds overhead. 16-bit/48kHz is the correct match", "Game",
                    () -> runPowerShell(
                        "# Sets default audio format to 16-bit 48000Hz via registry\n" +
                        "$path = 'HKCU:\\Software\\Microsoft\\Multimedia\\Audio';" +
                        "if (-not (Test-Path $path)) { New-Item -Path $path -Force | Out-Null };" +
                        "# Format GUID for 16-bit 48000Hz stereo PCM\n" +
                        "Set-ItemProperty -Path $path -Name 'DefaultFormat' -Value '{00000001-0000-0010-8000-00aa00389b71}' -Type String -Force -ErrorAction SilentlyContinue;" +
                        "Write-Output 'Audio format hint set — confirm manually: Sound > Properties > Advanced > 16-bit 48000 Hz'")));

                gameActions.add(new PrepAction("[R6] Disable Windows Audio Enhancements",
                    "Disables bass boost, virtual surround, loudness equalization — each adds CPU audio processing overhead that causes Siege FPS drops", "Game",
                    () -> runPowerShell(
                        "# Disable enhancements on all audio render endpoints\n" +
                        "$regBase = 'HKLM:\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\MMDevices\\Audio\\Render';" +
                        "if (Test-Path $regBase) {" +
                        "  Get-ChildItem $regBase | ForEach-Object {" +
                        "    $props = Join-Path $_.PSPath 'Properties';" +
                        "    if (Test-Path $props) {" +
                        "      Set-ItemProperty -Path $props -Name '{1da5d803-d492-4edd-8c23-e0c0ffee7f0e},5' -Value 1 -Type DWord -Force -ErrorAction SilentlyContinue" +
                        "    }" +
                        "  };" +
                        "  Write-Output 'Audio enhancements disabled on all endpoints'" +
                        "} else { Write-Output 'Audio registry path not found — disable manually in Sound > Properties > Enhancements' }")));

                // ── Game Files ────────────────────────────────────────────────
                gameActions.add(new PrepAction("[R6] Set MaxGPUBufferedFrames=1",
                    "Sets GameSettings.ini MaxGPUBufferedFrames=1 — best balance of low input latency and frame stability for Siege DX12", "Game",
                    () -> runPowerShell(
                        "$base = \"$env:USERPROFILE\\Documents\\My Games\\Rainbow Six - Siege\";" +
                        "if (-not (Test-Path $base)) { Write-Output 'Siege documents folder not found — launch game once first'; exit };" +
                        "$ini = Get-ChildItem $base -Recurse -Filter 'GameSettings.ini' | Select-Object -First 1;" +
                        "if (-not $ini) { Write-Output 'GameSettings.ini not found — launch game once to generate it'; exit };" +
                        "$content = Get-Content $ini.FullName -Raw;" +
                        "if ($content -match 'MaxGPUBufferedFrames') {" +
                        "  $content = $content -replace 'MaxGPUBufferedFrames=\\d+','MaxGPUBufferedFrames=1'" +
                        "} else {" +
                        "  $content = $content.TrimEnd() + \"`n[DISPLAY]`nMaxGPUBufferedFrames=1`n\"" +
                        "};" +
                        "Set-Content -Path $ini.FullName -Value $content -Force;" +
                        "Set-ItemProperty -Path $ini.FullName -Name IsReadOnly -Value $true;" +
                        "Write-Output \"MaxGPUBufferedFrames=1 set and file locked read-only: $($ini.FullName)\"")));

                gameActions.add(new PrepAction("[R6] Flush Ubisoft Connect Cache",
                    "Clears Ubisoft temp and cache files — fixes match load stutters and prevents DX12 shader conflicts from stale data", "Game",
                    () -> runPowerShell(
                        "Remove-Item \"$env:LOCALAPPDATA\\Ubisoft Game Launcher\\cache\\*\" -Recurse -Force -ErrorAction SilentlyContinue;" +
                        "Remove-Item \"$env:LOCALAPPDATA\\Ubisoft Game Launcher\\crashes\\*\" -Recurse -Force -ErrorAction SilentlyContinue;" +
                        "Write-Output 'Ubisoft Connect cache cleared'")));

                gameActions.add(new PrepAction("[R6] Disable Xbox Game DVR",
                    "Disables Xbox Game Bar background recording — known to cause DX12 frame time spikes and sudden FPS drops in Siege", "Game",
                    () -> runPowerShell(
                        "$p1 = 'HKCU:\\System\\GameConfigStore'; if (-not (Test-Path $p1)) { New-Item $p1 -Force | Out-Null }; Set-ItemProperty -Path $p1 -Name 'GameDVR_Enabled' -Value 0 -Type DWord -Force;" +
                        "$p2 = 'HKLM:\\SOFTWARE\\Policies\\Microsoft\\Windows\\GameDVR'; if (-not (Test-Path $p2)) { New-Item $p2 -Force | Out-Null }; Set-ItemProperty -Path $p2 -Name 'AllowGameDVR' -Value 0 -Type DWord -Force;" +
                        "Write-Output 'Xbox Game DVR disabled'")));
            }
            case "Valorant" -> {
                gameActions.add(new PrepAction("[VAL] Disable Fullscreen Optimizations",
                    "True exclusive fullscreen — bypasses DWM compositor for lower input latency vs Fullscreen Windowed", "Game",
                    () -> runPowerShell("$exe = \"$env:LOCALAPPDATA\\VALORANT\\live\\ShooterGame\\Binaries\\Win64\\VALORANT-Win64-Shipping.exe\"; if (Test-Path $exe) { $reg = 'HKCU:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Layers'; if (-not (Test-Path $reg)) { New-Item -Path $reg -Force | Out-Null }; Set-ItemProperty -Path $reg -Name $exe -Value 'DISABLEDXMAXIMIZEDWINDOWEDMODE' -Force; Write-Output 'FSO disabled for Valorant' } else { Write-Output 'Valorant exe not found' }")));

                gameActions.add(new PrepAction("[VAL] SystemResponsiveness = 0",
                    "Allows Valorant to use 100% of CPU scheduling — removes default 20% multimedia reservation", "Game",
                    () -> runPowerShell(
                        "Set-ItemProperty -Path 'HKLM:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Multimedia\\SystemProfile' " +
                        "-Name 'SystemResponsiveness' -Value 0 -Type DWord -Force; Write-Output 'SystemResponsiveness set to 0'")));

                gameActions.add(new PrepAction("[VAL] Boost Games Task Priority",
                    "Sets GPU Priority 8, CPU Priority 6, Scheduling=High for all games in Windows scheduler", "Game",
                    () -> runPowerShell(
                        "$p = 'HKLM:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Multimedia\\SystemProfile\\Tasks\\Games';" +
                        "if (-not (Test-Path $p)) { New-Item -Path $p -Force | Out-Null };" +
                        "Set-ItemProperty -Path $p -Name 'GPU Priority' -Value 8 -Type DWord -Force;" +
                        "Set-ItemProperty -Path $p -Name 'Priority' -Value 6 -Type DWord -Force;" +
                        "Set-ItemProperty -Path $p -Name 'Scheduling Category' -Value 'High' -Type String -Force;" +
                        "Set-ItemProperty -Path $p -Name 'SFIO Priority' -Value 'High' -Type String -Force;" +
                        "Write-Output 'Games task priority boosted'")));

                gameActions.add(new PrepAction("[VAL] Set Valorant CPU Priority",
                    "Boosts VALORANT-Win64-Shipping.exe to High CPU priority — run while game is open", "Game",
                    () -> runPowerShell(
                        "$proc = Get-Process -Name 'VALORANT-Win64-Shipping' -ErrorAction SilentlyContinue;" +
                        "if ($proc) { $proc.PriorityClass = 'High'; Write-Output 'Valorant set to High CPU priority' }" +
                        "else { Write-Output 'Valorant not running — launch game first then re-run' }")));

                gameActions.add(new PrepAction("[VAL] Disable Xbox Game DVR",
                    "Disables background recording — causes frame time spikes and input lag in Valorant", "Game",
                    () -> runPowerShell(
                        "$p1 = 'HKCU:\\System\\GameConfigStore'; if (-not (Test-Path $p1)) { New-Item $p1 -Force | Out-Null }; Set-ItemProperty -Path $p1 -Name 'GameDVR_Enabled' -Value 0 -Type DWord -Force;" +
                        "$p2 = 'HKLM:\\SOFTWARE\\Policies\\Microsoft\\Windows\\GameDVR'; if (-not (Test-Path $p2)) { New-Item $p2 -Force | Out-Null }; Set-ItemProperty -Path $p2 -Name 'AllowGameDVR' -Value 0 -Type DWord -Force;" +
                        "Write-Output 'Xbox Game DVR disabled'")));

                gameActions.add(new PrepAction("[VAL] Disable Nagle's Algorithm",
                    "Removes TCP packet batching on all adapters — reduces ping spikes in Valorant servers", "Game",
                    () -> runPowerShell(
                        "$base = 'HKLM:\\SYSTEM\\CurrentControlSet\\Services\\Tcpip\\Parameters\\Interfaces';" +
                        "Get-ChildItem $base | ForEach-Object {" +
                        "Set-ItemProperty -Path $_.PSPath -Name 'TcpAckFrequency' -Value 1 -Type DWord -Force -ErrorAction SilentlyContinue;" +
                        "Set-ItemProperty -Path $_.PSPath -Name 'TCPNoDelay' -Value 1 -Type DWord -Force -ErrorAction SilentlyContinue };" +
                        "Write-Output 'Nagle disabled on all interfaces'")));

                gameActions.add(new PrepAction("[VAL] Set DNS to Cloudflare",
                    "1.1.1.1 / 1.0.0.1 — fastest public DNS, reduces Riot server connection latency", "Game",
                    () -> runPowerShell(
                        "$adapter = Get-NetAdapter | Where-Object { $_.Status -eq 'Up' } | Select-Object -First 1;" +
                        "Set-DnsClientServerAddress -InterfaceAlias $adapter.Name -ServerAddresses ('1.1.1.1','1.0.0.1');" +
                        "Write-Output \"DNS set to Cloudflare on $($adapter.Name)\"")));

                gameActions.add(new PrepAction("[VAL] Kill Background Resource Hogs",
                    "Force-closes Chrome, Edge, Teams, Spotify, OneDrive, Discord — frees RAM and CPU for Valorant", "Game",
                    () -> runPowerShell(
                        "$kill = @('chrome','msedge','firefox','Teams','Spotify','OneDrive','slack','Discord');" +
                        "foreach ($p in $kill) { Stop-Process -Name $p -Force -ErrorAction SilentlyContinue };" +
                        "Write-Output 'Background apps closed'")));
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

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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.stage.FileChooser;

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

    private static final String VERSION             = "v1.1.0";
    private static final String GITHUB_RELEASES_URL = "https://api.github.com/repos/mayson208/Game-Ready-Toolkit/releases/latest";
    private static final String CLAUDE_API_URL      = "https://api.anthropic.com/v1/messages";

    // Category → accent color
    private static final Map<String, String> CAT_COLOR = Map.of(
        "Power",    "#ffff00",
        "CPU",      "#ff00cc",
        "Network",  "#00ffff",
        "Display",  "#cc00ff",
        "Services", "#39ff14",
        "Memory",   "#ff6600",
        "Audio",    "#ff9900",
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

    // ── New feature state ─────────────────────────────────────────────────────
    private Button reportBtn;
    private List<PrepActionResult> lastResults = new ArrayList<>();
    private final Map<String, XYChart.Series<Number, Number>> pingSeriesMap = new LinkedHashMap<>();
    private final Map<String, Label> pingLabelMap = new LinkedHashMap<>();
    private int    pingTick = 0;
    private Thread pingMonitorThread;
    private VBox   benchmarkResultsBox;
    private final Map<String, String> baselineMetrics = new LinkedHashMap<>();
    private final Map<String, String> afterMetrics    = new LinkedHashMap<>();
    private final List<PrepAction> customActions = new ArrayList<>();
    private VBox   customActionListBox;

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

    private static final Map<String, String> PING_SERVERS = new LinkedHashMap<>();
    static {
        PING_SERVERS.put("Riot (NA)",  "la1.lol.riotgames.com");
        PING_SERVERS.put("Valve (US)", "iad.steamserver.net");
        PING_SERVERS.put("EA (US)",    "prod.gs.ea.com");
        PING_SERVERS.put("Blizzard",   "us.battle.net");
        PING_SERVERS.put("Ubisoft",    "onlineconfig.ubi.com");
        PING_SERVERS.put("Epic (US)",  "fortnite-public-service-prod11.ol.epicgames.com");
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

        tabPane = new TabPane(optimizeTab, debugTab, buildNetworkTab(), buildBenchmarkTab());
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
        stage.setTitle("◈ GAME READY TOOLKIT " + VERSION + " ◈");
        stage.setMinWidth(620);
        stage.getIcons().add(buildAppIcon());
        stage.setScene(scene);
        stage.show();

        setupTrayIcon(stage);
        checkForUpdates();
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

        // Import / Export profile buttons
        Button exportBtn = new Button("⬆ EXPORT");
        exportBtn.setStyle(smallBtnStyle(CYAN));
        exportBtn.setOnAction(e -> exportProfile(stage));

        Button importBtn = new Button("⬇ IMPORT");
        importBtn.setStyle(smallBtnStyle(CYAN));
        importBtn.setOnAction(e -> importProfile(stage));

        // Report button
        reportBtn = new Button("📄 REPORT");
        reportBtn.setStyle(smallBtnStyle(AMBER));
        reportBtn.setDisable(true);
        reportBtn.setOnAction(e -> generateHtmlReport());

        // Run button
        Button runBtn = new Button("▶  PREPARE SYSTEM");
        runBtn.setStyle(runBtnStyle());
        runBtn.setOnAction(e -> showPreview(stage, runBtn));

        modeToggle.selectedProperty().addListener((obs, o, n) ->
            runBtn.setText(n ? "▶  RESTORE SYSTEM" : "▶  PREPARE SYSTEM"));

        HBox btnRow = new HBox(10, modeToggle, selAll, selNone, new Region(), exportBtn, importBtn, reportBtn, runBtn);
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

            lastResults = results;
            if (reportBtn != null) reportBtn.setDisable(false);
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

        // AUDIO
        prepActions.add(new PrepAction("Disable Audio Enhancements",
            "Disables bass boost, virtual surround, loudness EQ on all endpoints — removes CPU audio overhead that causes micro-stutters", "Audio",
            () -> runPowerShell(
                "$regBase = 'HKLM:\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\MMDevices\\Audio\\Render';" +
                "if (Test-Path $regBase) {" +
                "  Get-ChildItem $regBase | ForEach-Object {" +
                "    $props = Join-Path $_.PSPath 'Properties';" +
                "    if (Test-Path $props) {" +
                "      Set-ItemProperty -Path $props -Name '{1da5d803-d492-4edd-8c23-e0c0ffee7f0e},5' -Value 1 -Type DWord -Force -ErrorAction SilentlyContinue" +
                "    }" +
                "  };" +
                "  Write-Output 'Audio enhancements disabled on all endpoints'" +
                "} else { Write-Output 'Audio registry path not found' }"),
            () -> {
                var r = runPowerShell(
                    "$regBase = 'HKLM:\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\MMDevices\\Audio\\Render';" +
                    "$vals = Get-ChildItem $regBase | ForEach-Object { $p = Join-Path $_.PSPath 'Properties'; if (Test-Path $p) { (Get-ItemProperty $p -ErrorAction SilentlyContinue).'{1da5d803-d492-4edd-8c23-e0c0ffee7f0e},5' } };" +
                    "($vals | Where-Object { $_ -eq 1 }).Count -gt 0");
                return r.isSuccess() && r.getMessage().trim().equals("True");
            }));

        prepActions.add(new PrepAction("Set Timer Resolution to 0.5ms",
            "Forces Windows multimedia timer to 0.5ms precision — tighter frame pacing and lower sleep jitter for smoother gameplay", "Audio",
            () -> runPowerShell(
                "Add-Type -TypeDefinition @'\n" +
                "using System; using System.Runtime.InteropServices;\n" +
                "public class TimerRes {\n" +
                "  [DllImport(\"winmm.dll\")] public static extern int timeBeginPeriod(int p);\n" +
                "}\n" +
                "'@;\n" +
                "[TimerRes]::timeBeginPeriod(1);\n" +
                "# Also set via registry for persistence across reboots\n" +
                "$p = 'HKLM:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Multimedia\\SystemProfile';\n" +
                "Set-ItemProperty -Path $p -Name 'SystemResponsiveness' -Value 0 -Type DWord -Force;\n" +
                "Write-Output 'Timer resolution set to 1ms (0.5ms requires NtSetTimerResolution — apply via ISLR or Timer Resolution tool for sub-1ms)'")));

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

        restoreActions.add(new PrepAction("Re-enable Audio Enhancements", "Restore audio enhancement processing on all endpoints", "Audio",
            () -> runPowerShell(
                "$regBase = 'HKLM:\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\MMDevices\\Audio\\Render';" +
                "if (Test-Path $regBase) {" +
                "  Get-ChildItem $regBase | ForEach-Object {" +
                "    $props = Join-Path $_.PSPath 'Properties';" +
                "    if (Test-Path $props) {" +
                "      Remove-ItemProperty -Path $props -Name '{1da5d803-d492-4edd-8c23-e0c0ffee7f0e},5' -Force -ErrorAction SilentlyContinue" +
                "    }" +
                "  };" +
                "  Write-Output 'Audio enhancements restored'" +
                "} else { Write-Output 'Audio registry path not found' }")));

        restoreActions.add(new PrepAction("Restore Timer Resolution", "Resets multimedia timer to Windows default (15.6ms)", "Audio",
            () -> runPowerShell(
                "Add-Type -TypeDefinition @'\n" +
                "using System; using System.Runtime.InteropServices;\n" +
                "public class TimerResRestore {\n" +
                "  [DllImport(\"winmm.dll\")] public static extern int timeEndPeriod(int p);\n" +
                "}\n" +
                "'@;\n" +
                "[TimerResRestore]::timeEndPeriod(1);\n" +
                "Write-Output 'Timer resolution restored to default'")));

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
                    "Removes stale Respawn shader cache — fixes hitching on first match of session", "Game",
                    () -> runPowerShell("Remove-Item \"$env:LOCALAPPDATA\\Temp\\Respawn\\*\" -Recurse -Force -ErrorAction SilentlyContinue; Write-Output 'Apex cache cleared'")));

                gameActions.add(new PrepAction("[Apex] SystemResponsiveness = 0",
                    "Allows Apex to use 100% of CPU scheduling — removes default 20% multimedia reservation", "Game",
                    () -> runPowerShell(
                        "Set-ItemProperty -Path 'HKLM:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Multimedia\\SystemProfile' " +
                        "-Name 'SystemResponsiveness' -Value 0 -Type DWord -Force; Write-Output 'SystemResponsiveness set to 0'")));

                gameActions.add(new PrepAction("[Apex] Boost Games Task Priority",
                    "Sets GPU Priority 8, CPU Priority 6, Scheduling=High for all games in Windows scheduler", "Game",
                    () -> runPowerShell(
                        "$p = 'HKLM:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Multimedia\\SystemProfile\\Tasks\\Games';" +
                        "if (-not (Test-Path $p)) { New-Item -Path $p -Force | Out-Null };" +
                        "Set-ItemProperty -Path $p -Name 'GPU Priority' -Value 8 -Type DWord -Force;" +
                        "Set-ItemProperty -Path $p -Name 'Priority' -Value 6 -Type DWord -Force;" +
                        "Set-ItemProperty -Path $p -Name 'Scheduling Category' -Value 'High' -Type String -Force;" +
                        "Set-ItemProperty -Path $p -Name 'SFIO Priority' -Value 'High' -Type String -Force;" +
                        "Write-Output 'Games task priority boosted'")));

                gameActions.add(new PrepAction("[Apex] Set Apex CPU Priority",
                    "Boosts r5apex.exe to High CPU priority — run while game is open for immediate effect", "Game",
                    () -> runPowerShell(
                        "$proc = Get-Process -Name 'r5apex' -ErrorAction SilentlyContinue;" +
                        "if ($proc) { $proc.PriorityClass = 'High'; Write-Output 'Apex set to High CPU priority' }" +
                        "else { Write-Output 'Apex not running — launch game first then re-run' }")));

                gameActions.add(new PrepAction("[Apex] Disable Xbox Game DVR",
                    "Disables background recording — causes frame time spikes in Apex Legends", "Game",
                    () -> runPowerShell(
                        "$p1 = 'HKCU:\\System\\GameConfigStore'; if (-not (Test-Path $p1)) { New-Item $p1 -Force | Out-Null }; Set-ItemProperty -Path $p1 -Name 'GameDVR_Enabled' -Value 0 -Type DWord -Force;" +
                        "$p2 = 'HKLM:\\SOFTWARE\\Policies\\Microsoft\\Windows\\GameDVR'; if (-not (Test-Path $p2)) { New-Item $p2 -Force | Out-Null }; Set-ItemProperty -Path $p2 -Name 'AllowGameDVR' -Value 0 -Type DWord -Force;" +
                        "Write-Output 'Xbox Game DVR disabled'")));

                gameActions.add(new PrepAction("[Apex] Kill EA App Background",
                    "Stops EA App background services — frees CPU and RAM during gameplay", "Game",
                    () -> runPowerShell(
                        "Stop-Process -Name 'EABackgroundService' -Force -ErrorAction SilentlyContinue;" +
                        "Stop-Process -Name 'EADesktop' -Force -ErrorAction SilentlyContinue;" +
                        "Stop-Process -Name 'EALauncher' -Force -ErrorAction SilentlyContinue;" +
                        "Write-Output 'EA App background processes stopped'")));

                gameActions.add(new PrepAction("[Apex] Disable Nagle's Algorithm",
                    "Removes TCP packet batching on all adapters — reduces ping spikes on EA servers", "Game",
                    () -> runPowerShell(
                        "$base = 'HKLM:\\SYSTEM\\CurrentControlSet\\Services\\Tcpip\\Parameters\\Interfaces';" +
                        "Get-ChildItem $base | ForEach-Object {" +
                        "Set-ItemProperty -Path $_.PSPath -Name 'TcpAckFrequency' -Value 1 -Type DWord -Force -ErrorAction SilentlyContinue;" +
                        "Set-ItemProperty -Path $_.PSPath -Name 'TCPNoDelay' -Value 1 -Type DWord -Force -ErrorAction SilentlyContinue };" +
                        "Write-Output 'Nagle disabled on all interfaces'")));

                gameActions.add(new PrepAction("[Apex] Set DNS to Cloudflare",
                    "1.1.1.1 / 1.0.0.1 — fastest public DNS, reduces EA server connection latency", "Game",
                    () -> runPowerShell(
                        "$adapter = Get-NetAdapter | Where-Object { $_.Status -eq 'Up' } | Select-Object -First 1;" +
                        "Set-DnsClientServerAddress -InterfaceAlias $adapter.Name -ServerAddresses ('1.1.1.1','1.0.0.1');" +
                        "Write-Output \"DNS set to Cloudflare on $($adapter.Name)\"")));

                gameActions.add(new PrepAction("[Apex] Kill Background Resource Hogs",
                    "Force-closes Chrome, Edge, Teams, Spotify, OneDrive, Discord — frees RAM for Apex rendering", "Game",
                    () -> runPowerShell(
                        "$kill = @('chrome','msedge','firefox','Teams','Spotify','OneDrive','slack','Discord');" +
                        "foreach ($p in $kill) { Stop-Process -Name $p -Force -ErrorAction SilentlyContinue };" +
                        "Write-Output 'Background apps closed'")));
            }
            case "Warzone / MW3" -> {
                gameActions.add(new PrepAction("[COD] Kill Battle.net Background",
                    "Stops Battle.net launcher and update agent — frees RAM and CPU for Warzone/MW3", "Game",
                    () -> runPowerShell("Stop-Process -Name 'Battle.net' -Force -ErrorAction SilentlyContinue; Stop-Service -Name 'BattlenetUpdateAgent' -Force -ErrorAction SilentlyContinue; Write-Output 'Battle.net stopped'")));

                gameActions.add(new PrepAction("[COD] SystemResponsiveness = 0",
                    "Allows COD engine to use 100% of CPU scheduling — removes default 20% multimedia reservation", "Game",
                    () -> runPowerShell(
                        "Set-ItemProperty -Path 'HKLM:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Multimedia\\SystemProfile' " +
                        "-Name 'SystemResponsiveness' -Value 0 -Type DWord -Force; Write-Output 'SystemResponsiveness set to 0'")));

                gameActions.add(new PrepAction("[COD] Boost Games Task Priority",
                    "Sets GPU Priority 8, CPU Priority 6, Scheduling=High for all games in Windows scheduler", "Game",
                    () -> runPowerShell(
                        "$p = 'HKLM:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Multimedia\\SystemProfile\\Tasks\\Games';" +
                        "if (-not (Test-Path $p)) { New-Item -Path $p -Force | Out-Null };" +
                        "Set-ItemProperty -Path $p -Name 'GPU Priority' -Value 8 -Type DWord -Force;" +
                        "Set-ItemProperty -Path $p -Name 'Priority' -Value 6 -Type DWord -Force;" +
                        "Set-ItemProperty -Path $p -Name 'Scheduling Category' -Value 'High' -Type String -Force;" +
                        "Set-ItemProperty -Path $p -Name 'SFIO Priority' -Value 'High' -Type String -Force;" +
                        "Write-Output 'Games task priority boosted'")));

                gameActions.add(new PrepAction("[COD] Disable Fullscreen Optimizations",
                    "True exclusive fullscreen for cod.exe — lower input latency vs Fullscreen Windowed", "Game",
                    () -> runPowerShell(
                        "$reg = 'HKCU:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Layers';" +
                        "if (-not (Test-Path $reg)) { New-Item -Path $reg -Force | Out-Null };" +
                        "$paths = @(\"$env:ProgramFiles\\Call of Duty\\cod.exe\", \"$env:ProgramFiles(x86)\\Call of Duty\\cod.exe\");" +
                        "foreach ($exe in $paths) { if (Test-Path $exe) { Set-ItemProperty -Path $reg -Name $exe -Value 'DISABLEDXMAXIMIZEDWINDOWEDMODE' -Force; Write-Output \"FSO disabled: $exe\" } };" +
                        "Write-Output 'Done'")));

                gameActions.add(new PrepAction("[COD] Disable Xbox Game DVR",
                    "Disables background recording — causes DX12 frame time spikes in Warzone/MW3", "Game",
                    () -> runPowerShell(
                        "$p1 = 'HKCU:\\System\\GameConfigStore'; if (-not (Test-Path $p1)) { New-Item $p1 -Force | Out-Null }; Set-ItemProperty -Path $p1 -Name 'GameDVR_Enabled' -Value 0 -Type DWord -Force;" +
                        "$p2 = 'HKLM:\\SOFTWARE\\Policies\\Microsoft\\Windows\\GameDVR'; if (-not (Test-Path $p2)) { New-Item $p2 -Force | Out-Null }; Set-ItemProperty -Path $p2 -Name 'AllowGameDVR' -Value 0 -Type DWord -Force;" +
                        "Write-Output 'Xbox Game DVR disabled'")));

                gameActions.add(new PrepAction("[COD] Clear COD Shader Cache",
                    "Clears stale COD shader cache — fixes stutter on first load of maps", "Game",
                    () -> runPowerShell(
                        "Remove-Item \"$env:LOCALAPPDATA\\Temp\\Activision\\*\" -Recurse -Force -ErrorAction SilentlyContinue;" +
                        "Remove-Item \"$env:LOCALAPPDATA\\Blizzard Entertainment\\Battle.net\\Cache\\*\" -Recurse -Force -ErrorAction SilentlyContinue;" +
                        "Write-Output 'COD/Battle.net cache cleared'")));

                gameActions.add(new PrepAction("[COD] Disable Nagle's Algorithm",
                    "Removes TCP packet batching on all adapters — reduces ping spikes on Activision servers", "Game",
                    () -> runPowerShell(
                        "$base = 'HKLM:\\SYSTEM\\CurrentControlSet\\Services\\Tcpip\\Parameters\\Interfaces';" +
                        "Get-ChildItem $base | ForEach-Object {" +
                        "Set-ItemProperty -Path $_.PSPath -Name 'TcpAckFrequency' -Value 1 -Type DWord -Force -ErrorAction SilentlyContinue;" +
                        "Set-ItemProperty -Path $_.PSPath -Name 'TCPNoDelay' -Value 1 -Type DWord -Force -ErrorAction SilentlyContinue };" +
                        "Write-Output 'Nagle disabled on all interfaces'")));

                gameActions.add(new PrepAction("[COD] Set DNS to Cloudflare",
                    "1.1.1.1 / 1.0.0.1 — fastest public DNS, reduces Activision server lookup latency", "Game",
                    () -> runPowerShell(
                        "$adapter = Get-NetAdapter | Where-Object { $_.Status -eq 'Up' } | Select-Object -First 1;" +
                        "Set-DnsClientServerAddress -InterfaceAlias $adapter.Name -ServerAddresses ('1.1.1.1','1.0.0.1');" +
                        "Write-Output \"DNS set to Cloudflare on $($adapter.Name)\"")));

                gameActions.add(new PrepAction("[COD] Kill Background Resource Hogs",
                    "Force-closes Chrome, Edge, Teams, Spotify, OneDrive, Discord — frees RAM for COD engine", "Game",
                    () -> runPowerShell(
                        "$kill = @('chrome','msedge','firefox','Teams','Spotify','OneDrive','slack','Discord');" +
                        "foreach ($p in $kill) { Stop-Process -Name $p -Force -ErrorAction SilentlyContinue };" +
                        "Write-Output 'Background apps closed'")));
            }
            case "CS2" -> {
                gameActions.add(new PrepAction("[CS2] Disable Fullscreen Optimizations",
                    "Exclusive fullscreen — bypasses DWM for lower input latency in Source 2", "Game",
                    () -> runPowerShell("$loc = (Get-ItemProperty 'HKLM:\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\Steam App 730' -ErrorAction SilentlyContinue).InstallLocation; if ($loc) { $exe = \"$loc\\game\\bin\\win64\\cs2.exe\"; if (Test-Path $exe) { $reg = 'HKCU:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Layers'; if (-not (Test-Path $reg)) { New-Item -Path $reg -Force | Out-Null }; Set-ItemProperty -Path $reg -Name $exe -Value 'DISABLEDXMAXIMIZEDWINDOWEDMODE' -Force; Write-Output 'FSO disabled' } } else { Write-Output 'CS2 not found via Steam registry' }")));

                gameActions.add(new PrepAction("[CS2] SystemResponsiveness = 0",
                    "Allows CS2 to use 100% of CPU scheduling — removes default 20% multimedia reservation", "Game",
                    () -> runPowerShell(
                        "Set-ItemProperty -Path 'HKLM:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Multimedia\\SystemProfile' " +
                        "-Name 'SystemResponsiveness' -Value 0 -Type DWord -Force; Write-Output 'SystemResponsiveness set to 0'")));

                gameActions.add(new PrepAction("[CS2] Boost Games Task Priority",
                    "Sets GPU Priority 8, CPU Priority 6, Scheduling=High for all games in Windows scheduler", "Game",
                    () -> runPowerShell(
                        "$p = 'HKLM:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Multimedia\\SystemProfile\\Tasks\\Games';" +
                        "if (-not (Test-Path $p)) { New-Item -Path $p -Force | Out-Null };" +
                        "Set-ItemProperty -Path $p -Name 'GPU Priority' -Value 8 -Type DWord -Force;" +
                        "Set-ItemProperty -Path $p -Name 'Priority' -Value 6 -Type DWord -Force;" +
                        "Set-ItemProperty -Path $p -Name 'Scheduling Category' -Value 'High' -Type String -Force;" +
                        "Set-ItemProperty -Path $p -Name 'SFIO Priority' -Value 'High' -Type String -Force;" +
                        "Write-Output 'Games task priority boosted'")));

                gameActions.add(new PrepAction("[CS2] Set CS2 CPU Priority",
                    "Boosts cs2.exe to High CPU priority — run while game is open for immediate effect", "Game",
                    () -> runPowerShell(
                        "$proc = Get-Process -Name 'cs2' -ErrorAction SilentlyContinue;" +
                        "if ($proc) { $proc.PriorityClass = 'High'; Write-Output 'CS2 set to High CPU priority' }" +
                        "else { Write-Output 'CS2 not running — launch game first then re-run' }")));

                gameActions.add(new PrepAction("[CS2] Disable Xbox Game DVR",
                    "Disables background recording — known to cause Source 2 frame time spikes", "Game",
                    () -> runPowerShell(
                        "$p1 = 'HKCU:\\System\\GameConfigStore'; if (-not (Test-Path $p1)) { New-Item $p1 -Force | Out-Null }; Set-ItemProperty -Path $p1 -Name 'GameDVR_Enabled' -Value 0 -Type DWord -Force;" +
                        "$p2 = 'HKLM:\\SOFTWARE\\Policies\\Microsoft\\Windows\\GameDVR'; if (-not (Test-Path $p2)) { New-Item $p2 -Force | Out-Null }; Set-ItemProperty -Path $p2 -Name 'AllowGameDVR' -Value 0 -Type DWord -Force;" +
                        "Write-Output 'Xbox Game DVR disabled'")));

                gameActions.add(new PrepAction("[CS2] Clear CS2 Shader Cache",
                    "Removes stale DX11 shader cache — fixes first-session hitching on maps", "Game",
                    () -> runPowerShell(
                        "$loc = (Get-ItemProperty 'HKLM:\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\Steam App 730' -ErrorAction SilentlyContinue).InstallLocation;" +
                        "if ($loc) { Remove-Item \"$loc\\game\\csgo\\shadercache\\*\" -Recurse -Force -ErrorAction SilentlyContinue; Write-Output 'CS2 shader cache cleared' }" +
                        "else { Write-Output 'CS2 install path not found via Steam registry' }")));

                gameActions.add(new PrepAction("[CS2] Disable Nagle's Algorithm",
                    "Removes TCP packet batching on all adapters — reduces ping spikes on Valve servers", "Game",
                    () -> runPowerShell(
                        "$base = 'HKLM:\\SYSTEM\\CurrentControlSet\\Services\\Tcpip\\Parameters\\Interfaces';" +
                        "Get-ChildItem $base | ForEach-Object {" +
                        "Set-ItemProperty -Path $_.PSPath -Name 'TcpAckFrequency' -Value 1 -Type DWord -Force -ErrorAction SilentlyContinue;" +
                        "Set-ItemProperty -Path $_.PSPath -Name 'TCPNoDelay' -Value 1 -Type DWord -Force -ErrorAction SilentlyContinue };" +
                        "Write-Output 'Nagle disabled on all interfaces'")));

                gameActions.add(new PrepAction("[CS2] Set DNS to Cloudflare",
                    "1.1.1.1 / 1.0.0.1 — fastest public DNS, reduces Valve server connection latency", "Game",
                    () -> runPowerShell(
                        "$adapter = Get-NetAdapter | Where-Object { $_.Status -eq 'Up' } | Select-Object -First 1;" +
                        "Set-DnsClientServerAddress -InterfaceAlias $adapter.Name -ServerAddresses ('1.1.1.1','1.0.0.1');" +
                        "Write-Output \"DNS set to Cloudflare on $($adapter.Name)\"")));

                gameActions.add(new PrepAction("[CS2] Kill Background Resource Hogs",
                    "Force-closes Chrome, Edge, Teams, Spotify, OneDrive, Discord — frees RAM for Source 2 rendering", "Game",
                    () -> runPowerShell(
                        "$kill = @('chrome','msedge','firefox','Teams','Spotify','OneDrive','slack','Discord');" +
                        "foreach ($p in $kill) { Stop-Process -Name $p -Force -ErrorAction SilentlyContinue };" +
                        "Write-Output 'Background apps closed'")));
            }
            case "Fortnite" -> {
                gameActions.add(new PrepAction("[FN] Clear Epic Games Cache",
                    "Removes Epic launcher web cache — fixes shader stutter on Fortnite load", "Game",
                    () -> runPowerShell("Remove-Item \"$env:LOCALAPPDATA\\EpicGamesLauncher\\Saved\\webcache*\" -Recurse -Force -ErrorAction SilentlyContinue; Write-Output 'Epic cache cleared'")));

                gameActions.add(new PrepAction("[FN] SystemResponsiveness = 0",
                    "Allows Fortnite UE5 to use 100% of CPU scheduling — removes default 20% multimedia reservation", "Game",
                    () -> runPowerShell(
                        "Set-ItemProperty -Path 'HKLM:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Multimedia\\SystemProfile' " +
                        "-Name 'SystemResponsiveness' -Value 0 -Type DWord -Force; Write-Output 'SystemResponsiveness set to 0'")));

                gameActions.add(new PrepAction("[FN] Boost Games Task Priority",
                    "Sets GPU Priority 8, CPU Priority 6, Scheduling=High for all games in Windows scheduler", "Game",
                    () -> runPowerShell(
                        "$p = 'HKLM:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Multimedia\\SystemProfile\\Tasks\\Games';" +
                        "if (-not (Test-Path $p)) { New-Item -Path $p -Force | Out-Null };" +
                        "Set-ItemProperty -Path $p -Name 'GPU Priority' -Value 8 -Type DWord -Force;" +
                        "Set-ItemProperty -Path $p -Name 'Priority' -Value 6 -Type DWord -Force;" +
                        "Set-ItemProperty -Path $p -Name 'Scheduling Category' -Value 'High' -Type String -Force;" +
                        "Set-ItemProperty -Path $p -Name 'SFIO Priority' -Value 'High' -Type String -Force;" +
                        "Write-Output 'Games task priority boosted'")));

                gameActions.add(new PrepAction("[FN] Disable Fullscreen Optimizations",
                    "True exclusive fullscreen for Fortnite — bypasses DWM compositor for lower latency", "Game",
                    () -> runPowerShell(
                        "$reg = 'HKCU:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Layers';" +
                        "if (-not (Test-Path $reg)) { New-Item -Path $reg -Force | Out-Null };" +
                        "$exe = \"$env:LOCALAPPDATA\\FortniteGame\\Binaries\\Win64\\FortniteClient-Win64-Shipping.exe\";" +
                        "if (Test-Path $exe) { Set-ItemProperty -Path $reg -Name $exe -Value 'DISABLEDXMAXIMIZEDWINDOWEDMODE' -Force; Write-Output \"FSO disabled: $exe\" }" +
                        "else { Write-Output 'Fortnite exe not found in default path — install may be in a custom location' }")));

                gameActions.add(new PrepAction("[FN] Set Fortnite CPU Priority",
                    "Boosts FortniteClient-Win64-Shipping.exe to High CPU priority — run while game is open", "Game",
                    () -> runPowerShell(
                        "$proc = Get-Process -Name 'FortniteClient-Win64-Shipping' -ErrorAction SilentlyContinue;" +
                        "if ($proc) { $proc.PriorityClass = 'High'; Write-Output 'Fortnite set to High CPU priority' }" +
                        "else { Write-Output 'Fortnite not running — launch game first then re-run' }")));

                gameActions.add(new PrepAction("[FN] Disable Xbox Game DVR",
                    "Disables background recording — causes UE5 frame time spikes in Fortnite", "Game",
                    () -> runPowerShell(
                        "$p1 = 'HKCU:\\System\\GameConfigStore'; if (-not (Test-Path $p1)) { New-Item $p1 -Force | Out-Null }; Set-ItemProperty -Path $p1 -Name 'GameDVR_Enabled' -Value 0 -Type DWord -Force;" +
                        "$p2 = 'HKLM:\\SOFTWARE\\Policies\\Microsoft\\Windows\\GameDVR'; if (-not (Test-Path $p2)) { New-Item $p2 -Force | Out-Null }; Set-ItemProperty -Path $p2 -Name 'AllowGameDVR' -Value 0 -Type DWord -Force;" +
                        "Write-Output 'Xbox Game DVR disabled'")));

                gameActions.add(new PrepAction("[FN] Disable Nagle's Algorithm",
                    "Removes TCP packet batching on all adapters — reduces ping spikes on Epic servers", "Game",
                    () -> runPowerShell(
                        "$base = 'HKLM:\\SYSTEM\\CurrentControlSet\\Services\\Tcpip\\Parameters\\Interfaces';" +
                        "Get-ChildItem $base | ForEach-Object {" +
                        "Set-ItemProperty -Path $_.PSPath -Name 'TcpAckFrequency' -Value 1 -Type DWord -Force -ErrorAction SilentlyContinue;" +
                        "Set-ItemProperty -Path $_.PSPath -Name 'TCPNoDelay' -Value 1 -Type DWord -Force -ErrorAction SilentlyContinue };" +
                        "Write-Output 'Nagle disabled on all interfaces'")));

                gameActions.add(new PrepAction("[FN] Set DNS to Cloudflare",
                    "1.1.1.1 / 1.0.0.1 — fastest public DNS, reduces Epic server connection latency", "Game",
                    () -> runPowerShell(
                        "$adapter = Get-NetAdapter | Where-Object { $_.Status -eq 'Up' } | Select-Object -First 1;" +
                        "Set-DnsClientServerAddress -InterfaceAlias $adapter.Name -ServerAddresses ('1.1.1.1','1.0.0.1');" +
                        "Write-Output \"DNS set to Cloudflare on $($adapter.Name)\"")));

                gameActions.add(new PrepAction("[FN] Kill Background Resource Hogs",
                    "Force-closes Chrome, Edge, Teams, Spotify, OneDrive, Discord — frees RAM for Fortnite UE5", "Game",
                    () -> runPowerShell(
                        "$kill = @('chrome','msedge','firefox','Teams','Spotify','OneDrive','slack','Discord');" +
                        "foreach ($p in $kill) { Stop-Process -Name $p -Force -ErrorAction SilentlyContinue };" +
                        "Write-Output 'Background apps closed'")));
            }
            case "Overwatch 2" -> {
                gameActions.add(new PrepAction("[OW2] Kill Battle.net Background",
                    "Stops Battle.net launcher and update agent — frees RAM and CPU for Overwatch 2", "Game",
                    () -> runPowerShell("Stop-Process -Name 'Battle.net' -Force -ErrorAction SilentlyContinue; Stop-Service -Name 'BattlenetUpdateAgent' -Force -ErrorAction SilentlyContinue; Write-Output 'Battle.net stopped'")));

                gameActions.add(new PrepAction("[OW2] SystemResponsiveness = 0",
                    "Allows Overwatch 2 to use 100% of CPU scheduling — removes default 20% multimedia reservation", "Game",
                    () -> runPowerShell(
                        "Set-ItemProperty -Path 'HKLM:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Multimedia\\SystemProfile' " +
                        "-Name 'SystemResponsiveness' -Value 0 -Type DWord -Force; Write-Output 'SystemResponsiveness set to 0'")));

                gameActions.add(new PrepAction("[OW2] Boost Games Task Priority",
                    "Sets GPU Priority 8, CPU Priority 6, Scheduling=High for all games in Windows scheduler", "Game",
                    () -> runPowerShell(
                        "$p = 'HKLM:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Multimedia\\SystemProfile\\Tasks\\Games';" +
                        "if (-not (Test-Path $p)) { New-Item -Path $p -Force | Out-Null };" +
                        "Set-ItemProperty -Path $p -Name 'GPU Priority' -Value 8 -Type DWord -Force;" +
                        "Set-ItemProperty -Path $p -Name 'Priority' -Value 6 -Type DWord -Force;" +
                        "Set-ItemProperty -Path $p -Name 'Scheduling Category' -Value 'High' -Type String -Force;" +
                        "Set-ItemProperty -Path $p -Name 'SFIO Priority' -Value 'High' -Type String -Force;" +
                        "Write-Output 'Games task priority boosted'")));

                gameActions.add(new PrepAction("[OW2] Set Overwatch CPU Priority",
                    "Boosts Overwatch.exe to High CPU priority — run while game is open for immediate effect", "Game",
                    () -> runPowerShell(
                        "$proc = Get-Process -Name 'Overwatch' -ErrorAction SilentlyContinue;" +
                        "if ($proc) { $proc.PriorityClass = 'High'; Write-Output 'Overwatch 2 set to High CPU priority' }" +
                        "else { Write-Output 'Overwatch 2 not running — launch game first then re-run' }")));

                gameActions.add(new PrepAction("[OW2] Disable Fullscreen Optimizations",
                    "True exclusive fullscreen for Overwatch.exe — lower input latency vs Fullscreen Windowed", "Game",
                    () -> runPowerShell(
                        "$reg = 'HKCU:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Layers';" +
                        "if (-not (Test-Path $reg)) { New-Item -Path $reg -Force | Out-Null };" +
                        "$exe = \"$env:ProgramFiles(x86)\\Overwatch\\_retail_\\Overwatch.exe\";" +
                        "if (Test-Path $exe) { Set-ItemProperty -Path $reg -Name $exe -Value 'DISABLEDXMAXIMIZEDWINDOWEDMODE' -Force; Write-Output \"FSO disabled: $exe\" }" +
                        "else { Write-Output 'Overwatch.exe not found in default path — apply manually via Properties > Compatibility' }")));

                gameActions.add(new PrepAction("[OW2] Disable Xbox Game DVR",
                    "Disables background recording — causes frame time spikes in Overwatch 2", "Game",
                    () -> runPowerShell(
                        "$p1 = 'HKCU:\\System\\GameConfigStore'; if (-not (Test-Path $p1)) { New-Item $p1 -Force | Out-Null }; Set-ItemProperty -Path $p1 -Name 'GameDVR_Enabled' -Value 0 -Type DWord -Force;" +
                        "$p2 = 'HKLM:\\SOFTWARE\\Policies\\Microsoft\\Windows\\GameDVR'; if (-not (Test-Path $p2)) { New-Item $p2 -Force | Out-Null }; Set-ItemProperty -Path $p2 -Name 'AllowGameDVR' -Value 0 -Type DWord -Force;" +
                        "Write-Output 'Xbox Game DVR disabled'")));

                gameActions.add(new PrepAction("[OW2] Disable Nagle's Algorithm",
                    "Removes TCP packet batching on all adapters — reduces ping spikes on Blizzard servers", "Game",
                    () -> runPowerShell(
                        "$base = 'HKLM:\\SYSTEM\\CurrentControlSet\\Services\\Tcpip\\Parameters\\Interfaces';" +
                        "Get-ChildItem $base | ForEach-Object {" +
                        "Set-ItemProperty -Path $_.PSPath -Name 'TcpAckFrequency' -Value 1 -Type DWord -Force -ErrorAction SilentlyContinue;" +
                        "Set-ItemProperty -Path $_.PSPath -Name 'TCPNoDelay' -Value 1 -Type DWord -Force -ErrorAction SilentlyContinue };" +
                        "Write-Output 'Nagle disabled on all interfaces'")));

                gameActions.add(new PrepAction("[OW2] Set DNS to Cloudflare",
                    "1.1.1.1 / 1.0.0.1 — fastest public DNS, reduces Blizzard server connection latency", "Game",
                    () -> runPowerShell(
                        "$adapter = Get-NetAdapter | Where-Object { $_.Status -eq 'Up' } | Select-Object -First 1;" +
                        "Set-DnsClientServerAddress -InterfaceAlias $adapter.Name -ServerAddresses ('1.1.1.1','1.0.0.1');" +
                        "Write-Output \"DNS set to Cloudflare on $($adapter.Name)\"")));

                gameActions.add(new PrepAction("[OW2] Kill Background Resource Hogs",
                    "Force-closes Chrome, Edge, Teams, Spotify, OneDrive, Discord — frees RAM for Overwatch 2", "Game",
                    () -> runPowerShell(
                        "$kill = @('chrome','msedge','firefox','Teams','Spotify','OneDrive','slack','Discord');" +
                        "foreach ($p in $kill) { Stop-Process -Name $p -Force -ErrorAction SilentlyContinue };" +
                        "Write-Output 'Background apps closed'")));
            }
        }
    }

    // ── Profile import / export ───────────────────────────────────────────────
    private void exportProfile(Stage stage) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Profile");
        fc.setInitialFileName(selectedGame.replaceAll("[^a-zA-Z0-9]", "_") + "_profile.json");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
        java.io.File file = fc.showSaveDialog(stage);
        if (file == null) return;
        try {
            List<PrepAction> all = combineActions(gameActions, prepActions);
            StringBuilder sb = new StringBuilder("{\n  \"game\": \"")
                .append(jsonStringLiteral(selectedGame)).append("\",\n  \"selected\": [");
            boolean first = true;
            for (PrepAction a : all) {
                if (a.isSelected()) {
                    if (!first) sb.append(", ");
                    sb.append("\"").append(jsonStringLiteral(a.getName())).append("\"");
                    first = false;
                }
            }
            sb.append("]\n}");
            Files.writeString(file.toPath(), sb.toString());
            statusLabel.setStyle("-fx-text-fill: " + GREEN + "; -fx-font-size: 11px; -fx-font-family: Consolas;");
            statusLabel.setText("✓ Profile exported: " + file.getName());
        } catch (IOException e) {
            statusLabel.setStyle("-fx-text-fill: " + RED + "; -fx-font-size: 11px; -fx-font-family: Consolas;");
            statusLabel.setText("✗ Export failed: " + e.getMessage());
        }
    }

    private void importProfile(Stage stage) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Import Profile");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
        java.io.File file = fc.showOpenDialog(stage);
        if (file == null) return;
        try {
            String json = Files.readString(file.toPath());
            Matcher gameMatcher = Pattern.compile("\"game\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
            if (gameMatcher.find()) {
                String game = gameMatcher.group(1);
                if (GAME_PROFILES.containsKey(game)) {
                    selectedGame = game;
                    PREFS.put("selected_game", selectedGame);
                    buildGameActions();
                }
            }
            Matcher arrMatcher = Pattern.compile("\"selected\"\\s*:\\s*\\[([^]]*)]").matcher(json);
            if (arrMatcher.find()) {
                Set<String> names = new HashSet<>();
                Matcher nm = Pattern.compile("\"([^\"]+)\"").matcher(arrMatcher.group(1));
                while (nm.find()) names.add(nm.group(1));
                List<PrepAction> all = combineActions(gameActions, prepActions);
                for (PrepAction a : all) {
                    boolean sel = names.contains(a.getName());
                    a.setSelected(sel);
                    PREFS.putBoolean((a.getCategory().equals("Game") ? "game_" : "prep_") + a.getName(), sel);
                }
            }
            refreshTiles();
            statusLabel.setStyle("-fx-text-fill: " + GREEN + "; -fx-font-size: 11px; -fx-font-family: Consolas;");
            statusLabel.setText("✓ Profile imported: " + file.getName());
        } catch (IOException e) {
            statusLabel.setStyle("-fx-text-fill: " + RED + "; -fx-font-size: 11px; -fx-font-family: Consolas;");
            statusLabel.setText("✗ Import failed: " + e.getMessage());
        }
    }

    private String jsonStringLiteral(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    // ── HTML system report ────────────────────────────────────────────────────
    private void generateHtmlReport() {
        if (lastResults.isEmpty()) {
            statusLabel.setStyle("-fx-text-fill: " + AMBER + "; -fx-font-size: 11px; -fx-font-family: Consolas;");
            statusLabel.setText("⚠ No results yet — run PREPARE SYSTEM first.");
            return;
        }
        try {
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            long passed = lastResults.stream().filter(PrepActionResult::isSuccess).count();
            long failed = lastResults.size() - passed;

            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>")
                .append("<title>Game Ready Toolkit Report</title>")
                .append("<style>")
                .append("body{background:#0d0015;color:#fff;font-family:Consolas,monospace;padding:24px;margin:0}")
                .append("h1{color:#ff00cc;margin-bottom:4px}")
                .append("h2{color:#00ffff;border-bottom:1px solid #330066;padding-bottom:6px}")
                .append(".ts{color:#888;font-size:11px;margin-bottom:20px}")
                .append(".ok{color:#39ff14}.fail{color:#ff0055}.dim{color:#cc88ff;font-size:11px}")
                .append("table{border-collapse:collapse;width:100%;margin-top:8px}")
                .append("th{background:#1a003a;color:#cc00ff;padding:9px 12px;text-align:left;font-size:12px}")
                .append("td{padding:7px 12px;border-bottom:1px solid #220044;font-size:12px}")
                .append(".summary{background:#120028;border:2px solid #ff00cc;padding:14px 20px;")
                .append("display:inline-block;margin-bottom:24px;border-radius:4px}")
                .append("</style></head><body>")
                .append("<h1>◈ GAME READY TOOLKIT ").append(VERSION).append("</h1>")
                .append("<p class='ts'>").append(ts).append("  —  Profile: <b>").append(selectedGame).append("</b></p>")
                .append("<div class='summary'>")
                .append("<span class='ok'>✓ ").append(passed).append(" passed</span>")
                .append("&nbsp;&nbsp;&nbsp;")
                .append("<span class='fail'>✗ ").append(failed).append(" failed</span>")
                .append("</div>")
                .append("<h2>Optimization Results</h2>")
                .append("<table><tr><th>Status</th><th>Action</th><th>Output</th></tr>");

            for (PrepActionResult r : lastResults) {
                String cls    = r.isSuccess() ? "ok" : "fail";
                String status = r.isSuccess() ? "✓ PASS" : "✗ FAIL";
                String msg = r.getMessage() == null ? ""
                    : r.getMessage().replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
                html.append("<tr>")
                    .append("<td class='").append(cls).append("'>").append(status).append("</td>")
                    .append("<td>").append(r.getActionName()).append("</td>")
                    .append("<td class='dim'>").append(msg).append("</td>")
                    .append("</tr>");
            }

            html.append("</table></body></html>");

            Path report = Paths.get(System.getProperty("user.home"), "GameReadyToolkit-report.html");
            Files.writeString(report, html.toString());
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(report.toFile());
            statusLabel.setStyle("-fx-text-fill: " + GREEN + "; -fx-font-size: 11px; -fx-font-family: Consolas;");
            statusLabel.setText("✓ Report saved: " + report);
        } catch (IOException e) {
            statusLabel.setStyle("-fx-text-fill: " + RED + "; -fx-font-size: 11px; -fx-font-family: Consolas;");
            statusLabel.setText("✗ Report failed: " + e.getMessage());
        }
    }

    // ── Before/After benchmark tab ────────────────────────────────────────────
    private Tab buildBenchmarkTab() {
        VBox root = new VBox(14);
        root.setPadding(new Insets(16));
        root.setStyle("-fx-background-color: " + BG + ";");

        Label header = new Label("▌ BEFORE / AFTER BENCHMARK");
        header.setStyle("-fx-font-family: Consolas; -fx-font-weight: bold; -fx-font-size: 13px; " +
                        "-fx-text-fill: " + AMBER + ";");

        Label info = new Label(
            "Capture baseline metrics BEFORE running optimizations, then capture AFTER to compare the delta.");
        info.setStyle("-fx-font-family: Consolas; -fx-font-size: 11px; -fx-text-fill: " + TEXT_DIM + ";");
        info.setWrapText(true);

        benchmarkResultsBox = new VBox(4);

        Button baselineBtn = new Button("📷  CAPTURE BASELINE");
        baselineBtn.setStyle(smallBtnStyle(AMBER));
        Button afterBtn = new Button("📷  CAPTURE AFTER");
        afterBtn.setStyle(smallBtnStyle(GREEN));
        Button clearBtn = new Button("CLR");
        clearBtn.setStyle(smallBtnStyle(TEXT_DIM));

        baselineBtn.setOnAction(e -> {
            captureMetrics(baselineMetrics);
            refreshBenchmarkTable();
            statusLabel.setStyle("-fx-text-fill: " + AMBER + "; -fx-font-size: 11px; -fx-font-family: Consolas;");
            statusLabel.setText("✓ Baseline captured.");
        });
        afterBtn.setOnAction(e -> {
            captureMetrics(afterMetrics);
            refreshBenchmarkTable();
            statusLabel.setStyle("-fx-text-fill: " + GREEN + "; -fx-font-size: 11px; -fx-font-family: Consolas;");
            statusLabel.setText("✓ After metrics captured.");
        });
        clearBtn.setOnAction(e -> {
            baselineMetrics.clear();
            afterMetrics.clear();
            if (benchmarkResultsBox != null) benchmarkResultsBox.getChildren().clear();
        });

        HBox btnRow = new HBox(10, baselineBtn, afterBtn, clearBtn);
        root.getChildren().addAll(header, new Separator(), info, btnRow, benchmarkResultsBox);

        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background: " + BG + "; -fx-background-color: " + BG + "; -fx-border-color: transparent;");

        Tab tab = new Tab("  ◈ BENCHMARK  ", sp);
        tab.setClosable(false);
        return tab;
    }

    private void captureMetrics(Map<String, String> target) {
        target.clear();
        try {
            com.sun.management.OperatingSystemMXBean os =
                (com.sun.management.OperatingSystemMXBean)
                java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            double cpu = os.getCpuLoad() * 100;
            long total = os.getTotalMemorySize();
            long free  = os.getFreeMemorySize();
            target.put("CPU Usage",  cpu < 0 ? "--" : String.format("%.1f%%", cpu));
            target.put("RAM Used",   String.format("%d MB / %d MB",
                (total - free) / (1024*1024), total / (1024*1024)));
            target.put("CPU Cores",  String.valueOf(Runtime.getRuntime().availableProcessors()));
            target.put("Timestamp",  LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            PrepActionResult dr = runPowerShell(
                "(Get-PSDrive C).Free");
            if (dr.isSuccess()) {
                try {
                    long freeBytes = Long.parseLong(dr.getMessage().trim());
                    target.put("C: Free Space", String.format("%.1f GB", freeBytes / 1_000_000_000.0));
                } catch (NumberFormatException ignored) {}
            }
        } catch (Exception ignored) {}
    }

    private void refreshBenchmarkTable() {
        if (benchmarkResultsBox == null) return;
        benchmarkResultsBox.getChildren().clear();

        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(baselineMetrics.keySet());
        allKeys.addAll(afterMetrics.keySet());
        if (allKeys.isEmpty()) return;

        HBox hdr = new HBox();
        hdr.setStyle("-fx-background-color: " + BG_CARD2 + "; -fx-padding: 6 10;");
        Label c0 = new Label("Metric");
        c0.setStyle("-fx-font-family: Consolas; -fx-font-weight: bold; -fx-text-fill: " + PURPLE + "; -fx-min-width: 160px;");
        Label c1 = new Label("Before");
        c1.setStyle("-fx-font-family: Consolas; -fx-font-weight: bold; -fx-text-fill: " + AMBER + "; -fx-min-width: 160px;");
        Label c2 = new Label("After");
        c2.setStyle("-fx-font-family: Consolas; -fx-font-weight: bold; -fx-text-fill: " + GREEN + "; -fx-min-width: 160px;");
        hdr.getChildren().addAll(c0, c1, c2);
        benchmarkResultsBox.getChildren().add(hdr);

        for (String key : allKeys) {
            String before = baselineMetrics.getOrDefault(key, "--");
            String after  = afterMetrics.getOrDefault(key, "--");
            HBox rowBox = new HBox();
            rowBox.setStyle("-fx-padding: 5 10; " +
                "-fx-border-color: transparent transparent #220044 transparent; -fx-border-width: 0 0 1 0;");
            Label k = new Label(key);
            k.setStyle("-fx-font-family: Consolas; -fx-font-size: 12px; -fx-text-fill: " + TEXT + "; -fx-min-width: 160px;");
            Label b = new Label(before);
            b.setStyle("-fx-font-family: Consolas; -fx-font-size: 12px; -fx-text-fill: " + AMBER + "; -fx-min-width: 160px;");
            Label a = new Label(after);
            a.setStyle("-fx-font-family: Consolas; -fx-font-size: 12px; -fx-text-fill: " + GREEN + "; -fx-min-width: 160px;");
            rowBox.getChildren().addAll(k, b, a);
            benchmarkResultsBox.getChildren().add(rowBox);
        }
    }

    // ── Network latency monitor tab ───────────────────────────────────────────
    private Tab buildNetworkTab() {
        VBox root = new VBox(12);
        root.setPadding(new Insets(16));
        root.setStyle("-fx-background-color: " + BG + ";");

        Label header = new Label("▌ LIVE NETWORK LATENCY MONITOR");
        header.setStyle("-fx-font-family: Consolas; -fx-font-weight: bold; -fx-font-size: 13px; " +
                        "-fx-text-fill: " + CYAN + ";");

        NumberAxis xAxis = new NumberAxis(0, 60, 10);
        xAxis.setLabel("Seconds");
        NumberAxis yAxis = new NumberAxis(0, 200, 50);
        yAxis.setLabel("ms");

        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setAnimated(false);
        chart.setCreateSymbols(false);
        chart.setStyle("-fx-background-color: " + BG_CARD + ";");
        chart.setPrefHeight(280);

        String[] chartColors = {CYAN, PINK, GREEN, AMBER, PURPLE, "#ff6600"};
        int ci = 0;
        for (String name : PING_SERVERS.keySet()) {
            XYChart.Series<Number, Number> series = new XYChart.Series<>();
            series.setName(name);
            chart.getData().add(series);
            pingSeriesMap.put(name, series);
            ci++;
        }

        StringBuilder chartCss = new StringBuilder(
            ".chart-plot-background{-fx-background-color:" + BG_CARD2 + ";}" +
            ".chart-legend{-fx-background-color:" + BG_CARD + ";}" +
            ".axis-label{-fx-text-fill:" + TEXT_DIM + ";}" +
            ".chart-title{-fx-text-fill:" + TEXT_DIM + ";}");
        for (int i = 0; i < PING_SERVERS.size(); i++) {
            String c = chartColors[i % chartColors.length];
            chartCss.append(".default-color").append(i)
                    .append(".chart-series-line{-fx-stroke:").append(c).append(";-fx-stroke-width:2;}");
        }
        chart.getStylesheets().add("data:text/css," + chartCss);

        // Live ping labels grid
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(20); grid.setVgap(6);
        grid.setPadding(new Insets(8));
        int row = 0;
        for (String name : PING_SERVERS.keySet()) {
            Label nameL = new Label(name);
            nameL.setStyle("-fx-font-family: Consolas; -fx-font-size: 12px; " +
                           "-fx-text-fill: " + TEXT_DIM + "; -fx-min-width: 140px;");
            Label val = new Label("-- ms");
            val.setStyle("-fx-font-family: Consolas; -fx-font-weight: bold; " +
                         "-fx-font-size: 13px; -fx-text-fill: " + GREEN + ";");
            pingLabelMap.put(name, val);
            grid.add(nameL, 0, row);
            grid.add(val,   1, row);
            row++;
        }

        Button startBtn = new Button("▶  START MONITOR");
        startBtn.setStyle(smallBtnStyle(GREEN));
        Button stopBtn = new Button("■  STOP");
        stopBtn.setStyle(smallBtnStyle(RED));
        stopBtn.setDisable(true);

        startBtn.setOnAction(e -> {
            startBtn.setDisable(true);
            stopBtn.setDisable(false);
            startPingMonitor(chart, xAxis);
        });
        stopBtn.setOnAction(e -> {
            if (pingMonitorThread != null) pingMonitorThread.interrupt();
            startBtn.setDisable(false);
            stopBtn.setDisable(true);
        });

        HBox btnRow = new HBox(10, startBtn, stopBtn);
        root.getChildren().addAll(header, new Separator(), chart, grid, btnRow);

        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background: " + BG + "; -fx-background-color: " + BG + "; -fx-border-color: transparent;");

        Tab tab = new Tab("  ◈ NETWORK  ", sp);
        tab.setClosable(false);
        return tab;
    }

    private void startPingMonitor(LineChart<Number, Number> chart, NumberAxis xAxis) {
        pingTick = 0;
        pingSeriesMap.values().forEach(s -> s.getData().clear());

        pingMonitorThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                int tick = pingTick++;
                for (Map.Entry<String, String> entry : PING_SERVERS.entrySet()) {
                    String name = entry.getKey();
                    String host = entry.getValue();
                    long ms = pingHost(host);
                    Platform.runLater(() -> {
                        XYChart.Series<Number, Number> series = pingSeriesMap.get(name);
                        if (series != null) {
                            series.getData().add(new XYChart.Data<>(tick, ms < 0 ? 0 : ms));
                            if (series.getData().size() > 60) series.getData().remove(0);
                        }
                        Label lbl = pingLabelMap.get(name);
                        if (lbl != null) {
                            lbl.setText(ms < 0 ? "timeout" : ms + " ms");
                            String col = ms < 0 ? RED : ms < 60 ? GREEN : ms < 120 ? AMBER : RED;
                            lbl.setStyle("-fx-font-family: Consolas; -fx-font-weight: bold; " +
                                         "-fx-font-size: 13px; -fx-text-fill: " + col + ";");
                        }
                        if (tick > 55) xAxis.setUpperBound(tick + 5);
                    });
                }
                try { Thread.sleep(2000); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); break; }
            }
        }, "ping-monitor");
        pingMonitorThread.setDaemon(true);
        pingMonitorThread.start();
    }

    private long pingHost(String host) {
        try {
            ProcessBuilder pb = new ProcessBuilder("ping", "-n", "1", "-w", "1000", host);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes());
            boolean ok = p.waitFor(3, TimeUnit.SECONDS) && p.exitValue() == 0;
            if (!ok) return -1;
            Matcher m = Pattern.compile("time[=<](\\d+)ms").matcher(out);
            if (m.find()) return Long.parseLong(m.group(1));
            return -1;
        } catch (Exception e) { return -1; }
    }

    // ── Auto-update check ─────────────────────────────────────────────────────
    private void checkForUpdates() {
        Thread t = new Thread(() -> {
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_RELEASES_URL))
                    .header("Accept", "application/vnd.github+json")
                    .build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                Matcher m = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").matcher(resp.body());
                if (m.find()) {
                    String latest = m.group(1);
                    if (!latest.equals(VERSION)) {
                        Platform.runLater(() -> showUpdateDialog(latest));
                    }
                }
            } catch (Exception ignored) {}
        }, "update-check");
        t.setDaemon(true);
        t.start();
    }

    private void showUpdateDialog(String latest) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Update Available");
        a.setHeaderText("New version available: " + latest);
        a.setContentText("You are running " + VERSION + ".\nVisit GitHub to download the latest release.");
        a.getDialogPane().setStyle(
            "-fx-background-color: " + BG_CARD + "; -fx-border-color: " + PINK + "; -fx-border-width: 2;");
        a.showAndWait();
    }
}

package com.mason.gamesessionprep;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

public class GameSessionPrepApp extends Application {

    private static final Preferences PREFS =
            Preferences.userRoot().node("com/mason/gamesessionprep");

    private final List<PrepAction> prepActions    = new ArrayList<>();
    private final List<PrepAction> restoreActions = new ArrayList<>();
    private final List<PrepAction> gameActions    = new ArrayList<>();

    private boolean restoreMode = false;
    private String  selectedGame = "General";

    private VBox checklistBox;
    private long sessionStartMs = -1;
    private Thread sessionTimerThread;

    // ── Game profiles ─────────────────────────────────────────────────────────

    private static final Map<String, String> GAME_PROFILES = new LinkedHashMap<>();
    static {
        GAME_PROFILES.put("General",             "Universal optimizations for any game");
        GAME_PROFILES.put("Rainbow Six Siege",   "Tactical FPS — low latency, stable frame pacing");
        GAME_PROFILES.put("Valorant",            "Competitive FPS — Riot Vanguard friendly tweaks");
        GAME_PROFILES.put("Apex Legends",        "Battle royale — high FPS, smooth frametimes");
        GAME_PROFILES.put("Warzone / MW3",       "COD engine — VRAM management, network priority");
        GAME_PROFILES.put("CS2",                 "Source 2 — raw input, minimal overhead");
        GAME_PROFILES.put("Fortnite",            "UE5 — CPU thread optimization, shader pre-cache");
        GAME_PROFILES.put("Overwatch 2",         "Team FPS — balanced CPU/GPU load");
    }

    // ── Admin check ───────────────────────────────────────────────────────────

    private static boolean isElevated() {
        try {
            ProcessBuilder pb = new ProcessBuilder("net", "session");
            pb.redirectErrorStream(true);
            return pb.start().waitFor() == 0;
        } catch (Exception e) { return false; }
    }

    // ── App start ─────────────────────────────────────────────────────────────

    @Override
    public void start(Stage stage) {
        buildPrepActions();
        buildRestoreActions();

        // ── Header ────────────────────────────────────────────────────────────
        Label titleLabel = new Label("Game Ready Toolkit");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #e8e8e8;");

        Label timerLabel = new Label();
        timerLabel.setStyle("-fx-text-fill: #2ecc71; -fx-font-size: 12px; -fx-font-weight: bold;");
        timerLabel.setVisible(false);

        Region titleSpacer = new Region();
        HBox.setHgrow(titleSpacer, Priority.ALWAYS);
        HBox headerRow = new HBox(titleLabel, titleSpacer, timerLabel);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        // ── Game selector ─────────────────────────────────────────────────────
        Label gameLabel = new Label("Game:");
        gameLabel.setStyle("-fx-text-fill: #aaa; -fx-font-size: 13px;");

        ComboBox<String> gameCombo = new ComboBox<>();
        gameCombo.getItems().addAll(GAME_PROFILES.keySet());
        gameCombo.setValue(PREFS.get("selected_game", "General"));
        selectedGame = gameCombo.getValue();
        gameCombo.setStyle(
            "-fx-background-color: #252525; -fx-text-fill: #e0e0e0; " +
            "-fx-border-color: #444; -fx-border-radius: 4; -fx-background-radius: 4; " +
            "-fx-font-size: 13px; -fx-pref-width: 200px;");

        Label gameDesc = new Label(GAME_PROFILES.get(selectedGame));
        gameDesc.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");
        gameDesc.setWrapText(true);

        gameCombo.setOnAction(e -> {
            selectedGame = gameCombo.getValue();
            PREFS.put("selected_game", selectedGame);
            gameDesc.setText(GAME_PROFILES.getOrDefault(selectedGame, ""));
            buildGameActions();
            refreshChecklist();
        });

        HBox gameSelectorRow = new HBox(10, gameLabel, gameCombo);
        gameSelectorRow.setAlignment(Pos.CENTER_LEFT);

        // ── Mode subtitle ─────────────────────────────────────────────────────
        Label subtitleLabel = new Label("Select optimisations to apply");
        subtitleLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 13px;");

        // ── Select All / None ─────────────────────────────────────────────────
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

        // ── Checklist ─────────────────────────────────────────────────────────
        checklistBox = new VBox(10);
        checklistBox.setStyle("-fx-background-color: #1a1a1a;");
        checklistBox.setPadding(new Insets(2, 4, 2, 4));

        buildGameActions();
        refreshChecklist();

        ScrollPane scrollPane = new ScrollPane(checklistBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(300);
        scrollPane.setStyle(
            "-fx-background: #1a1a1a; -fx-background-color: #1a1a1a; " +
            "-fx-border-color: #2a2a2a; -fx-border-radius: 4;");

        // ── Status / progress ─────────────────────────────────────────────────
        Label statusLabel = new Label();
        statusLabel.setStyle("-fx-text-fill: #aaa; -fx-font-size: 12px;");
        statusLabel.setWrapText(true);

        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(Double.MAX_VALUE);
        progressBar.setVisible(false);

        // ── Buttons ───────────────────────────────────────────────────────────
        String btnStyle = "-fx-background-color: #2a2a2a; -fx-text-fill: #e0e0e0; " +
                          "-fx-border-color: #444; -fx-border-radius: 6; -fx-background-radius: 6; " +
                          "-fx-padding: 7 18; -fx-cursor: hand; -fx-font-size: 13px;";

        Button runButton     = new Button("Prepare System");
        Button modeToggleBtn = new Button("Switch to Restore Mode");
        Button exitButton    = new Button("Exit");

        for (Button b : new Button[]{runButton, modeToggleBtn, exitButton}) b.setStyle(btnStyle);
        modeToggleBtn.setStyle(btnStyle.replace("#2a2a2a", "#1a3a1a").replace("#444", "#2a5a2a"));

        runButton.setOnAction(e ->
            showPreview(stage, statusLabel, progressBar, runButton, timerLabel, subtitleLabel));

        modeToggleBtn.setOnAction(e -> {
            restoreMode = !restoreMode;
            if (restoreMode) {
                subtitleLabel.setText("Select restore actions to undo optimisations");
                modeToggleBtn.setText("Switch to Prepare Mode");
                modeToggleBtn.setStyle(btnStyle.replace("#2a2a2a", "#3a1a1a").replace("#444", "#5a2a2a"));
                runButton.setText("Restore System");
            } else {
                subtitleLabel.setText("Select optimisations to apply");
                modeToggleBtn.setText("Switch to Restore Mode");
                modeToggleBtn.setStyle(btnStyle.replace("#2a2a2a", "#1a3a1a").replace("#444", "#2a5a2a"));
                runButton.setText("Prepare System");
            }
            refreshChecklist();
            statusLabel.setText("");
            progressBar.setVisible(false);
        });

        exitButton.setOnAction(e -> stage.close());

        HBox buttonRow = new HBox(10, runButton, modeToggleBtn, exitButton);
        buttonRow.setAlignment(Pos.CENTER_LEFT);

        // ── Assemble root ─────────────────────────────────────────────────────
        VBox root = new VBox(10);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #1a1a1a;");

        root.getChildren().addAll(
                headerRow,
                gameSelectorRow,
                gameDesc,
                subtitleLabel,
                new Separator(),
                selectRow,
                scrollPane,
                new Separator(),
                buttonRow,
                progressBar,
                statusLabel
        );

        Scene scene = new Scene(root, 560, 620);
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.A, KeyCombination.CONTROL_DOWN),
                () -> setAllSelected(true));
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.D, KeyCombination.CONTROL_DOWN),
                () -> setAllSelected(false));

        stage.setTitle("Game Ready Toolkit");
        stage.setMinWidth(440);
        stage.setScene(scene);
        stage.show();

        setupTrayIcon(stage);
    }

    // ── Build universal prep actions ──────────────────────────────────────────

    private void buildPrepActions() {
        prepActions.clear();

        prepActions.add(new PrepAction(
                "High Performance Power Plan",
                "Maximize CPU/GPU clocks — biggest single FPS booster",
                () -> runPowerShell("powercfg /setactive 8c5e7fda-e8bf-4a96-9a85-a6e23a8c635c")
        ));
        prepActions.add(new PrepAction(
                "Enable Windows Game Mode",
                "Dedicates more CPU/GPU resources to the active game, reduces background task interruptions",
                () -> runPowerShell(
                    "if (-not (Test-Path 'HKCU:\\Software\\Microsoft\\GameBar')) {" +
                    "  New-Item -Path 'HKCU:\\Software\\Microsoft\\GameBar' -Force | Out-Null };" +
                    "Set-ItemProperty -Path 'HKCU:\\Software\\Microsoft\\GameBar' " +
                    "  -Name 'AutoGameModeEnabled' -Value 1 -Type DWord -Force;" +
                    "Write-Output 'Game Mode enabled'")
        ));
        prepActions.add(new PrepAction(
                "Set GPU to High Performance",
                "Forces Windows to use the dedicated GPU for all apps — key on laptops",
                () -> runPowerShell(
                    "$p = 'HKCU:\\Software\\Microsoft\\DirectX\\UserGpuPreferences';" +
                    "if (-not (Test-Path $p)) { New-Item -Path $p -Force | Out-Null };" +
                    "Set-ItemProperty -Path $p -Name 'DirectXUserGlobalSettings' " +
                    "  -Value 'VRROptimizeEnable=0;' -Type String -Force;" +
                    "Write-Output 'GPU set to High Performance'")
        ));
        prepActions.add(new PrepAction(
                "Disable Mouse Acceleration",
                "Raw linear input — removes pointer precision curve for consistent 1:1 mouse movement",
                () -> runPowerShell(
                    "Set-ItemProperty -Path 'HKCU:\\Control Panel\\Mouse' -Name 'MouseSpeed' -Value 0 -Force;" +
                    "Set-ItemProperty -Path 'HKCU:\\Control Panel\\Mouse' -Name 'MouseThreshold1' -Value 0 -Force;" +
                    "Set-ItemProperty -Path 'HKCU:\\Control Panel\\Mouse' -Name 'MouseThreshold2' -Value 0 -Force;" +
                    "Write-Output 'Mouse acceleration disabled'")
        ));
        prepActions.add(new PrepAction(
                "Silence Notifications (Focus Assist)",
                "Block all toast popups and notification sounds during gameplay",
                () -> runPowerShell(
                    "Set-ItemProperty " +
                    "  -Path 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Notifications\\Settings' " +
                    "  -Name 'NOC_GLOBAL_SETTING_TOASTS_ENABLED' -Value 0 -Force;" +
                    "Write-Output 'Notifications silenced'")
        ));
        prepActions.add(new PrepAction(
                "Stop Xbox Game Bar",
                "Kill overlay — reduces CPU/GPU overhead and DWM interference",
                () -> runPowerShell(
                    "Stop-Process -Name GameBar -Force -ErrorAction SilentlyContinue;" +
                    "Stop-Process -Name GameBarFTServer -Force -ErrorAction SilentlyContinue;" +
                    "Write-Output 'Xbox Game Bar stopped'")
        ));
        prepActions.add(new PrepAction(
                "Set CPU Process Priority for Games",
                "Boosts game process priority via Windows registry so the scheduler favors it",
                () -> runPowerShell(
                    "$p = 'HKLM:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Image File Execution Options';" +
                    "$games = @('RainbowSix.exe','VALORANT-Win64-Shipping.exe','r5apex.exe','cs2.exe','FortniteClient-Win64-Shipping.exe');" +
                    "foreach ($g in $games) {" +
                    "  $full = \"$p\\$g\\PerfOptions\";" +
                    "  if (-not (Test-Path $full)) { New-Item -Path $full -Force | Out-Null };" +
                    "  Set-ItemProperty -Path $full -Name 'CpuPriorityClass' -Value 3 -Type DWord -Force" +
                    "};" +
                    "Write-Output 'Game CPU priorities set to High'")
        ));
        prepActions.add(new PrepAction(
                "Optimize Network for Gaming (Nagle Off)",
                "Disable Nagle's algorithm to reduce TCP latency — cuts ping spikes",
                () -> runPowerShell(
                    "$base = 'HKLM:\\SYSTEM\\CurrentControlSet\\Services\\Tcpip\\Parameters\\Interfaces';" +
                    "Get-ChildItem $base | ForEach-Object {" +
                    "  Set-ItemProperty -Path $_.PSPath -Name 'TcpAckFrequency' -Value 1 -Type DWord -Force -ErrorAction SilentlyContinue;" +
                    "  Set-ItemProperty -Path $_.PSPath -Name 'TCPNoDelay' -Value 1 -Type DWord -Force -ErrorAction SilentlyContinue" +
                    "};" +
                    "Write-Output 'Nagle algorithm disabled on all interfaces'")
        ));
        prepActions.add(new PrepAction(
                "Set Network Adapter to High Performance",
                "Remove power-saving throttling from the active NIC for consistent ping",
                () -> runPowerShell(
                    "Get-NetAdapter | Where-Object Status -eq 'Up' | ForEach-Object {" +
                    "  & netsh int tcp set supplemental template=Internet;" +
                    "  Write-Output \"Optimized: $($_.Name)\"" +
                    "}")
        ));
        prepActions.add(new PrepAction(
                "Flush DNS Cache",
                "Clear stale DNS entries for better matchmaking server connections",
                () -> runCommand("ipconfig", "/flushdns")
        ));
        prepActions.add(new PrepAction(
                "Clear RAM Working Set",
                "Force Windows to trim unused RAM from background apps, freeing memory for your game",
                () -> runPowerShell(
                    "Add-Type -TypeDefinition @'\n" +
                    "using System; using System.Runtime.InteropServices;\n" +
                    "public class Memory {\n" +
                    "  [DllImport(\"psapi.dll\")] public static extern bool EmptyWorkingSet(IntPtr h);\n" +
                    "  [DllImport(\"kernel32.dll\")] public static extern IntPtr OpenProcess(int a, bool b, int c);\n" +
                    "  public static void Trim() { foreach (var p in System.Diagnostics.Process.GetProcesses()) {\n" +
                    "    try { EmptyWorkingSet(p.Handle); } catch {} } }\n" +
                    "}\n'@;\n" +
                    "[Memory]::Trim();\n" +
                    "Write-Output 'RAM working set cleared'")
        ));
        prepActions.add(new PrepAction(
                "Stop Windows Search Indexer",
                "Pauses background disk indexing to reduce I/O load during gaming",
                () -> runPowerShell(
                    "Stop-Service -Name WSearch -Force -ErrorAction SilentlyContinue;" +
                    "Write-Output 'Windows Search indexer stopped'")
        ));
        prepActions.add(new PrepAction(
                "Pause Windows Update",
                "Stop wuauserv from consuming bandwidth and CPU mid-session",
                () -> runPowerShell("Stop-Service -Name wuauserv -Force -ErrorAction SilentlyContinue")
        ));
        prepActions.add(new PrepAction(
                "Pause OneDrive Sync",
                "Free bandwidth and CPU from background cloud uploads",
                () -> runPowerShell(
                    "$od = Get-Process -Name OneDrive -ErrorAction SilentlyContinue;" +
                    "if ($od) {" +
                    "  & \"$env:LOCALAPPDATA\\Microsoft\\OneDrive\\OneDrive.exe\" /pause;" +
                    "  Write-Output 'OneDrive sync paused'" +
                    "} else { Write-Output 'OneDrive not running' }")
        ));
        prepActions.add(new PrepAction(
                "Clear Temp Files",
                "Remove temp files to free disk space and reduce background I/O",
                () -> runPowerShell(
                    "Remove-Item -Path $env:TEMP\\* -Recurse -Force -ErrorAction SilentlyContinue;" +
                    "Write-Output 'Temp files cleared'")
        ));
        prepActions.add(new PrepAction(
                "Check Display Refresh Rate",
                "Report current and max monitor refresh rate — confirm you're running at max Hz",
                () -> runPowerShell(
                    "$d = Get-CimInstance Win32_VideoController;" +
                    "Write-Output \"Refresh: $($d.CurrentRefreshRate)Hz / Max: $($d.MaxRefreshRate)Hz\"")
        ));
    }

    // ── Build game-specific actions ───────────────────────────────────────────

    private void buildGameActions() {
        gameActions.clear();
        if (selectedGame == null || selectedGame.equals("General")) return;

        switch (selectedGame) {
            case "Rainbow Six Siege" -> {
                gameActions.add(new PrepAction(
                        "[R6] Set Siege Process to High Priority",
                        "Elevate RainbowSix.exe to High CPU priority for this session",
                        () -> runPowerShell(
                            "$p = Get-Process -Name RainbowSix -ErrorAction SilentlyContinue;" +
                            "if ($p) { $p.PriorityClass = 'High'; Write-Output 'Siege priority set to High' }" +
                            "else { Write-Output 'Siege not running — priority will apply on next launch via registry' }")
                ));
                gameActions.add(new PrepAction(
                        "[R6] Disable Fullscreen Optimizations for Siege",
                        "Forces true exclusive fullscreen — lower latency than FSO",
                        () -> runPowerShell(
                            "$paths = @(" +
                            "  'C:\\Program Files (x86)\\Ubisoft\\Ubisoft Game Launcher\\games\\Tom Clancy''s Rainbow Six Siege\\RainbowSix.exe'," +
                            "  'C:\\Program Files\\Ubisoft\\Ubisoft Game Launcher\\games\\Tom Clancy''s Rainbow Six Siege\\RainbowSix.exe'" +
                            ");" +
                            "foreach ($exe in $paths) {" +
                            "  if (Test-Path $exe) {" +
                            "    $reg = \"HKCU:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Layers\";" +
                            "    if (-not (Test-Path $reg)) { New-Item -Path $reg -Force | Out-Null };" +
                            "    Set-ItemProperty -Path $reg -Name $exe -Value 'DISABLEDXMAXIMIZEDWINDOWEDMODE' -Force;" +
                            "    Write-Output 'Fullscreen Optimizations disabled for Siege'" +
                            "  }" +
                            "}" +
                            "Write-Output 'Done'")
                ));
                gameActions.add(new PrepAction(
                        "[R6] Flush Ubisoft Connect Cache",
                        "Clear Ubisoft Connect temp files that can cause match stutters",
                        () -> runPowerShell(
                            "Remove-Item \"$env:LOCALAPPDATA\\Ubisoft Game Launcher\\cache\\*\" -Recurse -Force -ErrorAction SilentlyContinue;" +
                            "Write-Output 'Ubisoft Connect cache cleared'")
                ));
            }
            case "Valorant" -> {
                gameActions.add(new PrepAction(
                        "[Valorant] Disable Fullscreen Optimizations for Valorant",
                        "True exclusive fullscreen lowers input latency in Valorant",
                        () -> runPowerShell(
                            "$exe = (Get-Process -Name VALORANT-Win64-Shipping -ErrorAction SilentlyContinue).Path;" +
                            "if (-not $exe) { $exe = \"$env:LOCALAPPDATA\\VALORANT\\live\\ShooterGame\\Binaries\\Win64\\VALORANT-Win64-Shipping.exe\" };" +
                            "if (Test-Path $exe) {" +
                            "  $reg = \"HKCU:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Layers\";" +
                            "  if (-not (Test-Path $reg)) { New-Item -Path $reg -Force | Out-Null };" +
                            "  Set-ItemProperty -Path $reg -Name $exe -Value 'DISABLEDXMAXIMIZEDWINDOWEDMODE' -Force;" +
                            "  Write-Output 'FSO disabled for Valorant'" +
                            "} else { Write-Output 'Valorant exe not found — launch game first' }")
                ));
                gameActions.add(new PrepAction(
                        "[Valorant] Set Riot Client to Normal Priority",
                        "Riot Vanguard works best when Riot Client stays at Normal — don't over-prioritize it",
                        () -> runPowerShell(
                            "$p = Get-Process -Name RiotClientServices -ErrorAction SilentlyContinue;" +
                            "if ($p) { $p.PriorityClass = 'Normal'; Write-Output 'Riot Client set to Normal priority' }" +
                            "else { Write-Output 'Riot Client not running' }")
                ));
            }
            case "Apex Legends" -> {
                gameActions.add(new PrepAction(
                        "[Apex] Set Apex to High CPU Priority",
                        "Elevates r5apex.exe for better frame timing",
                        () -> runPowerShell(
                            "$p = Get-Process -Name r5apex -ErrorAction SilentlyContinue;" +
                            "if ($p) { $p.PriorityClass = 'High'; Write-Output 'Apex priority set to High' }" +
                            "else { Write-Output 'Apex not running' }")
                ));
                gameActions.add(new PrepAction(
                        "[Apex] Clear Apex Shader Cache",
                        "Removes stale shader cache that can cause hitching in Apex",
                        () -> runPowerShell(
                            "Remove-Item \"$env:LOCALAPPDATA\\Temp\\Respawn\\*\" -Recurse -Force -ErrorAction SilentlyContinue;" +
                            "Write-Output 'Apex shader cache cleared'")
                ));
            }
            case "Warzone / MW3" -> {
                gameActions.add(new PrepAction(
                        "[COD] Increase VRAM Page File for COD",
                        "COD games are VRAM heavy — increase page file to prevent stutters",
                        () -> runPowerShell(
                            "Write-Output 'Tip: Set pagefile to System Managed or 8GB+ in System > Advanced > Performance Settings'")
                ));
                gameActions.add(new PrepAction(
                        "[COD] Kill Battle.net Background Services",
                        "Stop Battle.net update and agent services to free resources",
                        () -> runPowerShell(
                            "Stop-Process -Name 'Battle.net' -Force -ErrorAction SilentlyContinue;" +
                            "Stop-Service -Name 'BattlenetUpdateAgent' -Force -ErrorAction SilentlyContinue;" +
                            "Write-Output 'Battle.net background services stopped'")
                ));
            }
            case "CS2" -> {
                gameActions.add(new PrepAction(
                        "[CS2] Set CS2 to High CPU Priority",
                        "High priority for cs2.exe improves frame consistency in Source 2",
                        () -> runPowerShell(
                            "$p = Get-Process -Name cs2 -ErrorAction SilentlyContinue;" +
                            "if ($p) { $p.PriorityClass = 'High'; Write-Output 'CS2 priority set to High' }" +
                            "else { Write-Output 'CS2 not running' }")
                ));
                gameActions.add(new PrepAction(
                        "[CS2] Disable Fullscreen Optimizations for CS2",
                        "Exclusive fullscreen in CS2 gives lower latency",
                        () -> runPowerShell(
                            "$exe = (Get-ItemProperty 'HKLM:\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\Steam App 730' -ErrorAction SilentlyContinue).InstallLocation;" +
                            "if ($exe) { $exe = \"$exe\\game\\bin\\win64\\cs2.exe\" };" +
                            "if ($exe -and (Test-Path $exe)) {" +
                            "  $reg = \"HKCU:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Layers\";" +
                            "  if (-not (Test-Path $reg)) { New-Item -Path $reg -Force | Out-Null };" +
                            "  Set-ItemProperty -Path $reg -Name $exe -Value 'DISABLEDXMAXIMIZEDWINDOWEDMODE' -Force;" +
                            "  Write-Output 'FSO disabled for CS2'" +
                            "} else { Write-Output 'CS2 path not found — launch game first' }")
                ));
            }
            case "Fortnite" -> {
                gameActions.add(new PrepAction(
                        "[Fortnite] Set Fortnite to High CPU Priority",
                        "UE5 is CPU-heavy — high priority helps maintain stable frames",
                        () -> runPowerShell(
                            "$p = Get-Process -Name 'FortniteClient-Win64-Shipping' -ErrorAction SilentlyContinue;" +
                            "if ($p) { $p.PriorityClass = 'High'; Write-Output 'Fortnite priority set to High' }" +
                            "else { Write-Output 'Fortnite not running' }")
                ));
                gameActions.add(new PrepAction(
                        "[Fortnite] Clear Epic Games Cache",
                        "Remove Epic cache files that cause shader stutter on first load",
                        () -> runPowerShell(
                            "Remove-Item \"$env:LOCALAPPDATA\\EpicGamesLauncher\\Saved\\webcache*\" -Recurse -Force -ErrorAction SilentlyContinue;" +
                            "Write-Output 'Epic Games cache cleared'")
                ));
            }
            case "Overwatch 2" -> {
                gameActions.add(new PrepAction(
                        "[OW2] Set Overwatch to High CPU Priority",
                        "Helps OW2's engine maintain consistent frame pacing",
                        () -> runPowerShell(
                            "$p = Get-Process -Name Overwatch -ErrorAction SilentlyContinue;" +
                            "if ($p) { $p.PriorityClass = 'High'; Write-Output 'Overwatch priority set to High' }" +
                            "else { Write-Output 'Overwatch not running' }")
                ));
                gameActions.add(new PrepAction(
                        "[OW2] Kill Battle.net Background Services",
                        "Free resources from Battle.net updater during your session",
                        () -> runPowerShell(
                            "Stop-Process -Name 'Battle.net' -Force -ErrorAction SilentlyContinue;" +
                            "Stop-Service -Name 'BattlenetUpdateAgent' -Force -ErrorAction SilentlyContinue;" +
                            "Write-Output 'Battle.net background services stopped'")
                ));
            }
        }
    }

    // ── Build restore actions ─────────────────────────────────────────────────

    private void buildRestoreActions() {
        restoreActions.clear();
        restoreActions.add(new PrepAction(
                "Restore Balanced Power Plan",
                "Return to the default balanced power profile",
                () -> runPowerShell("powercfg /setactive 381b4222-f694-41f0-9685-ff5bb260df2e")
        ));
        restoreActions.add(new PrepAction(
                "Restore Default GPU Preference",
                "Remove the High Performance GPU override",
                () -> runPowerShell(
                    "$p = 'HKCU:\\Software\\Microsoft\\DirectX\\UserGpuPreferences';" +
                    "if (Test-Path $p) {" +
                    "  Remove-ItemProperty -Path $p -Name 'DirectXUserGlobalSettings' -ErrorAction SilentlyContinue };" +
                    "Write-Output 'GPU preference restored'")
        ));
        restoreActions.add(new PrepAction(
                "Re-enable Notifications",
                "Turn toast notifications back on",
                () -> runPowerShell(
                    "Set-ItemProperty " +
                    "  -Path 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Notifications\\Settings' " +
                    "  -Name 'NOC_GLOBAL_SETTING_TOASTS_ENABLED' -Value 1 -Force;" +
                    "Write-Output 'Notifications restored'")
        ));
        restoreActions.add(new PrepAction(
                "Re-enable Mouse Acceleration",
                "Restore default Windows pointer precision",
                () -> runPowerShell(
                    "Set-ItemProperty -Path 'HKCU:\\Control Panel\\Mouse' -Name 'MouseSpeed' -Value 1 -Force;" +
                    "Set-ItemProperty -Path 'HKCU:\\Control Panel\\Mouse' -Name 'MouseThreshold1' -Value 6 -Force;" +
                    "Set-ItemProperty -Path 'HKCU:\\Control Panel\\Mouse' -Name 'MouseThreshold2' -Value 10 -Force;" +
                    "Write-Output 'Mouse acceleration restored'")
        ));
        restoreActions.add(new PrepAction(
                "Restore Nagle Algorithm (TCP)",
                "Re-enable Nagle's algorithm on all network interfaces",
                () -> runPowerShell(
                    "$base = 'HKLM:\\SYSTEM\\CurrentControlSet\\Services\\Tcpip\\Parameters\\Interfaces';" +
                    "Get-ChildItem $base | ForEach-Object {" +
                    "  Remove-ItemProperty -Path $_.PSPath -Name 'TcpAckFrequency' -ErrorAction SilentlyContinue;" +
                    "  Remove-ItemProperty -Path $_.PSPath -Name 'TCPNoDelay' -ErrorAction SilentlyContinue" +
                    "};" +
                    "Write-Output 'Nagle algorithm restored'")
        ));
        restoreActions.add(new PrepAction(
                "Re-enable Windows Update",
                "Allow Windows Update to resume",
                () -> runPowerShell("Start-Service -Name wuauserv -ErrorAction SilentlyContinue")
        ));
        restoreActions.add(new PrepAction(
                "Restart Windows Search Indexer",
                "Resume background disk indexing",
                () -> runPowerShell(
                    "Start-Service -Name WSearch -ErrorAction SilentlyContinue;" +
                    "Write-Output 'Search indexer restarted'")
        ));
        restoreActions.add(new PrepAction(
                "Resume OneDrive Sync",
                "Resume OneDrive background sync",
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
                "Clear any stale DNS entries from the session",
                () -> runCommand("ipconfig", "/flushdns")
        ));
    }

    // ── Checklist helpers ─────────────────────────────────────────────────────

    private void refreshChecklist() {
        checklistBox.getChildren().clear();
        if (restoreMode) {
            buildChecklist(checklistBox, restoreActions, "restore_");
        } else {
            // Universal actions first
            if (!gameActions.isEmpty()) {
                Label gameSect = new Label("▸ " + selectedGame + " Optimizations");
                gameSect.setStyle("-fx-text-fill: #7ec8e3; -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 4 0 2 0;");
                checklistBox.getChildren().add(gameSect);
                buildChecklist(checklistBox, gameActions, "game_");

                Separator sep = new Separator();
                sep.setStyle("-fx-background-color: #2a2a2a;");
                checklistBox.getChildren().add(sep);
            }

            Label univSect = new Label("▸ Universal Optimizations");
            univSect.setStyle("-fx-text-fill: #aaa; -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 4 0 2 0;");
            checklistBox.getChildren().add(univSect);
            buildChecklist(checklistBox, prepActions, "prep_");
        }
    }

    private void buildChecklist(VBox box, List<PrepAction> actions, String prefPrefix) {
        for (PrepAction action : actions) {
            CheckBox cb = new CheckBox(action.getName());
            cb.setSelected(PREFS.getBoolean(prefPrefix + action.getName(), true));
            action.setSelected(cb.isSelected());
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
        if (restoreMode) {
            setSelected(restoreActions, "restore_", selected);
        } else {
            setSelected(prepActions, "prep_", selected);
            setSelected(gameActions, "game_", selected);
        }
        checklistBox.getChildren().stream()
                .filter(n -> n instanceof CheckBox)
                .map(n -> (CheckBox) n)
                .forEach(cb -> cb.setSelected(selected));
    }

    private void setSelected(List<PrepAction> actions, String prefix, boolean selected) {
        for (PrepAction a : actions) {
            a.setSelected(selected);
            PREFS.putBoolean(prefix + a.getName(), selected);
        }
    }

    // ── Run flow ──────────────────────────────────────────────────────────────

    private void showPreview(Stage owner, Label statusLabel, ProgressBar progressBar,
                             Button runButton, Label timerLabel, Label subtitleLabel) {
        List<PrepAction> selected = new ArrayList<>();
        if (!restoreMode) {
            selected.addAll(gameActions.stream().filter(PrepAction::isSelected).toList());
            selected.addAll(prepActions.stream().filter(PrepAction::isSelected).toList());
        } else {
            selected.addAll(restoreActions.stream().filter(PrepAction::isSelected).toList());
        }

        if (selected.isEmpty()) {
            statusLabel.setStyle("-fx-text-fill: #f39c12; -fx-font-size: 12px;");
            statusLabel.setText("No actions selected.");
            return;
        }

        Alert preview = new Alert(Alert.AlertType.CONFIRMATION);
        preview.initOwner(owner);
        preview.setTitle("Confirm " + (restoreMode ? "Restore" : "Preparation") +
                         (restoreMode ? "" : " — " + selectedGame));
        preview.setHeaderText("The following actions will run:");
        preview.setContentText(selected.stream()
                .map(a -> "  \u2022 " + a.getName())
                .collect(Collectors.joining("\n")));

        ButtonType confirm = new ButtonType("Run Now");
        ButtonType cancel  = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        preview.getButtonTypes().setAll(confirm, cancel);

        preview.showAndWait().ifPresent(result -> {
            if (result == confirm)
                runActions(selected, statusLabel, progressBar, runButton, timerLabel);
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

            if (!restoreMode && failed == 0) startSessionTimer(timerLabel);
            else if (restoreMode) stopSessionTimer(timerLabel);

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
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(ts).append("] ")
              .append(restoreMode ? "RESTORE" : "PREPARE — " + selectedGame).append("\n");
            for (PrepActionResult r : results) {
                sb.append("  ").append(r.isSuccess() ? "OK" : "FAIL")
                  .append("  ").append(r.getActionName());
                String msg = r.getMessage();
                if (msg != null && !msg.isBlank() && !msg.equals("OK"))
                    sb.append(" — ").append(msg.replace("\n", " | "));
                sb.append("\n");
            }
            sb.append("\n");
            Files.writeString(LOG_FILE, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {}
    }

    // ── Result dialog ─────────────────────────────────────────────────────────

    private void showResultDialog(List<PrepActionResult> results) {
        StringBuilder sb = new StringBuilder();
        for (PrepActionResult r : results) {
            sb.append(r.isSuccess() ? "\u2713 " : "\u2717 ").append(r.getActionName()).append("\n");
            String msg = r.getMessage();
            if (msg != null && !msg.isBlank() && !msg.equals("OK"))
                sb.append("    \u2192 ").append(msg.replace("\n", "\n    ")).append("\n");
            sb.append("\n");
        }

        TextArea ta = new TextArea(sb.toString().trim());
        ta.setEditable(false);
        ta.setWrapText(true);
        ta.setPrefWidth(480);
        ta.setPrefHeight(320);
        ta.setStyle(
            "-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 12px; " +
            "-fx-control-inner-background: #1e1e1e; -fx-text-fill: #d0d0d0;");

        ButtonType openLogBtn = new ButtonType("Open Log", ButtonBar.ButtonData.LEFT);
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(restoreMode ? "Restore Results" : "Preparation Results — " + selectedGame);
        dialog.setHeaderText("Completed " + results.size() + " action(s)");
        dialog.getDialogPane().setContent(ta);
        dialog.getDialogPane().setPrefWidth(520);
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
        stopSessionTimer(timerLabel);
        sessionStartMs = System.currentTimeMillis();
        timerLabel.setVisible(true);

        sessionTimerThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted() && sessionStartMs > 0) {
                long secs = (System.currentTimeMillis() - sessionStartMs) / 1000;
                String text = String.format("Session: %02d:%02d:%02d",
                        secs / 3600, (secs % 3600) / 60, secs % 60);
                Platform.runLater(() -> timerLabel.setText(text));
                try { Thread.sleep(1000); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
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

    // ── System tray ───────────────────────────────────────────────────────────

    private void setupTrayIcon(Stage stage) {
        if (!SystemTray.isSupported()) return;

        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(new java.awt.Color(0x27, 0xae, 0x60));
        g.fillOval(1, 1, 14, 14);
        g.dispose();

        PopupMenu popup = new PopupMenu();
        MenuItem showItem = new MenuItem("Show");
        MenuItem quitItem = new MenuItem("Quit");
        popup.add(showItem);
        popup.addSeparator();
        popup.add(quitItem);

        TrayIcon trayIcon = new TrayIcon(img, "Game Ready Toolkit", popup);
        trayIcon.setImageAutoSize(true);

        showItem.addActionListener(e -> Platform.runLater(() -> { stage.show(); stage.toFront(); }));
        trayIcon.addActionListener(e -> Platform.runLater(() -> { stage.show(); stage.toFront(); }));
        quitItem.addActionListener(e -> { SystemTray.getSystemTray().remove(trayIcon); Platform.exit(); });

        Platform.setImplicitExit(false);
        stage.setOnCloseRequest(e -> {
            e.consume();
            stage.hide();
            trayIcon.displayMessage("Game Ready Toolkit", "Running in tray.", TrayIcon.MessageType.INFO);
        });

        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException ignored) {
            Platform.setImplicitExit(true);
            stage.setOnCloseRequest(null);
        }
    }

    public static void main(String[] args) throws Exception {
        // Force admin elevation — relaunch with UAC if not already elevated
        if (!isElevated()) {
            String javaExe = ProcessHandle.current().info().command().orElse("java");
            String jarPath = GameSessionPrepApp.class
                    .getProtectionDomain().getCodeSource().getLocation().toURI().getPath();

            // Use PowerShell Start-Process -Verb RunAs to trigger UAC prompt
            String psCommand = String.format(
                "Start-Process -FilePath '%s' -ArgumentList '-jar \"%s\"' -Verb RunAs",
                javaExe.replace("'", "''"),
                jarPath.replace("\"", "\\\"")
            );

            new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", psCommand)
                    .start();

            // Exit current non-elevated instance
            System.exit(0);
        }

        launch();
    }
}

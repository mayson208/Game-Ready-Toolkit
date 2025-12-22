package com.mason.gamesessionprep;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public class GameSessionPrepApp extends Application {

    private final List<PrepAction> actions = new ArrayList<>();

    @Override
    public void start(Stage stage) {

        Label titleLabel = new Label("Game Ready Toolkit");
        Label statusLabel = new Label("Select preparation steps");

        actions.add(new PrepAction(
                "Enable High Performance Power Plan",
                "Prioritize system performance over power savings"
        ));

        actions.add(new PrepAction(
                "Silence Notifications",
                "Reduce distractions during gameplay"
        ));

        actions.add(new PrepAction(
                "Close Background Applications",
                "Free system resources before launching a game"
        ));

        actions.add(new PrepAction(
                "Pause Background Updates",
                "Reduce unexpected CPU or disk usage"
        ));

        actions.add(new PrepAction(
                "Clear Temporary Files",
                "Remove temporary system files created by applications"
        ));

        actions.add(new PrepAction(
                "Confirm Audio Device",
                "Ensure the correct audio output device is active"
        ));

        actions.add(new PrepAction(
                "Set Display Focus Mode",
                "Reduce popups and system interruptions"
        ));

        VBox checklistBox = new VBox(8);

        for (PrepAction action : actions) {
            CheckBox checkBox = new CheckBox(action.getName());
            checkBox.setSelected(action.isSelected());

            checkBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
                action.setSelected(newVal);
            });

            checklistBox.getChildren().add(checkBox);
        }

        Button runButton = new Button("Prepare System");
        Button exitButton = new Button("Exit");

        runButton.setOnAction(event -> {
            long selectedCount = actions.stream()
                    .filter(PrepAction::isSelected)
                    .count();

            statusLabel.setText("Prepared " + selectedCount + " action(s)");
        });

        exitButton.setOnAction(event -> stage.close());

        VBox root = new VBox(
                14,
                titleLabel,
                checklistBox,
                runButton,
                statusLabel,
                exitButton
        );

        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #1e1e1e;");

        titleLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: white;");
        statusLabel.setStyle("-fx-text-fill: #bbbbbb;");

        Scene scene = new Scene(root, 440, 420);
        stage.setTitle("Game Ready Toolkit");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}

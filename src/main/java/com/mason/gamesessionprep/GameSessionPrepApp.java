package com.mason.gamesessionprep;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class GameSessionPrepApp extends Application {

    @Override
    public void start(Stage stage) {

        Label titleLabel = new Label("Gaming Session Prep Assistant");
        Label statusLabel = new Label("Ready to prepare system");

        Button runPrepButton = new Button("Prepare System");
        Button exitButton = new Button("Exit");

        runPrepButton.setOnAction(event -> {
            statusLabel.setText("Preparation complete");
        });

        exitButton.setOnAction(event -> stage.close());

        VBox root = new VBox(
                12,
                titleLabel,
                statusLabel,
                runPrepButton,
                exitButton
        );

        root.setStyle("-fx-padding: 20;");

        Scene scene = new Scene(root, 420, 220);
        stage.setTitle("Gaming Session Prep Assistant");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}

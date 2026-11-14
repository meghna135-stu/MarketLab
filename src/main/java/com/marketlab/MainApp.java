package com.marketlab;

import com.marketlab.service.*;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) {
        stage.setTitle("MarketLab");
        stage.setScene(new Scene(new Label("Hello MarketLab"), 800, 500));
        stage.show();
    }

    public static void main(String[] args) {

        launch(args);
    }
}
package com.johnreah.mapster;

import com.johnreah.mapster.view.MainWindow;

import javafx.application.Application;
import javafx.stage.Stage;

public class App extends Application {

    private MainWindow mainWindow;

    @Override
    public void start(Stage stage) {
        mainWindow = new MainWindow();
        mainWindow.show(stage);
    }

    @Override
    public void stop() {
        if (mainWindow != null) {
            mainWindow.shutdown();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

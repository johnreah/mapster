package com.johnreah.mapster;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {

    private MapView mapView;

    @Override
    public void start(Stage stage) {
        mapView = new MapView();
        Scene scene = new Scene(mapView, 1024, 768);
        stage.setTitle("Mapster");
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        if (mapView != null) {
            mapView.shutdown();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

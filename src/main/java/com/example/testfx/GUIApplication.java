package com.example.testfx;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class GUIApplication extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(GUIApplication.class.getResource("GUI.fxml"));
        Scene scene = new Scene(fxmlLoader.load());
        primaryStage.setTitle("Modifying Existing Sort With Offset Values");
        primaryStage.setScene(scene);
        primaryStage.show();
    }}

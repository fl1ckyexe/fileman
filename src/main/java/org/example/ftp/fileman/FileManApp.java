package org.example.ftp.fileman;

import org.example.ftp.fileman.ui.MainView;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class FileManApp extends Application {

    @Override
    public void start(Stage stage) {
        stage.setTitle("FTP Fileman");


        stage.initStyle(javafx.stage.StageStyle.UNDECORATED);

        MainView mainView = new MainView(stage);
        Scene scene = new Scene(mainView, 1200, 650);
        String stylesheet = getClass().getResource("/styles.css").toExternalForm();

        var resource = getClass().getResource("/styles.css");
        if (resource != null) {
            scene.getStylesheets().add(resource.toExternalForm());
        } else {
            System.err.println("Предупреждение: styles.css не найден!");
        }
        scene.getStylesheets().add(stylesheet);
        stage.setScene(scene);
        stage.setMinWidth(800);
        stage.setMinHeight(600);
        stage.show();
    }
    public static void main(String[] args) {
        launch();
    }
}


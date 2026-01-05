package org.example.ftp.fileman.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

 
public class TitleBar extends HBox {
    
    private double xOffset = 0;
    private double yOffset = 0;
    
    public TitleBar(Stage stage, String title) {
        getStyleClass().add("title-bar");
        setSpacing(10);
        setPadding(new Insets(8, 12, 8, 12));
        
        
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("title-bar-title");
        HBox.setHgrow(titleLabel, Priority.ALWAYS);
        
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        
        Button minimizeButton = new Button("-");
        minimizeButton.getStyleClass().add("title-bar-button");
        minimizeButton.setMinWidth(40);
        minimizeButton.setMaxWidth(40);
        minimizeButton.setPrefHeight(30);
        minimizeButton.setOnAction(e -> stage.setIconified(true));
        
        
        Button maximizeButton = new Button("□");
        maximizeButton.getStyleClass().add("title-bar-button");
        maximizeButton.setMinWidth(40);
        maximizeButton.setMaxWidth(40);
        maximizeButton.setPrefHeight(30);
        maximizeButton.setOnAction(e -> {
            if (stage.isMaximized()) {
                stage.setMaximized(false);
                maximizeButton.setText("□");
            } else {
                stage.setMaximized(true);
                maximizeButton.setText("❐");
            }
        });
        
        
        Button closeButton = new Button("×");
        closeButton.getStyleClass().add("title-bar-button");
        closeButton.getStyleClass().add("title-bar-close-button");
        closeButton.setMinWidth(40);
        closeButton.setMaxWidth(40);
        closeButton.setPrefHeight(30);
        closeButton.setOnAction(e -> stage.close());
        
        getChildren().addAll(titleLabel, spacer, minimizeButton, maximizeButton, closeButton);
        
        
        setOnMousePressed(event -> {
            if (!stage.isMaximized()) {
                xOffset = event.getSceneX();
                yOffset = event.getSceneY();
            }
        });
        
        setOnMouseDragged(event -> {
            if (!stage.isMaximized()) {
                stage.setX(event.getScreenX() - xOffset);
                stage.setY(event.getScreenY() - yOffset);
            }
        });
        
        
        setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                if (stage.isMaximized()) {
                    stage.setMaximized(false);
                    maximizeButton.setText("□");
                } else {
                    stage.setMaximized(true);
                    maximizeButton.setText("❐");
                }
            }
        });
    }
}


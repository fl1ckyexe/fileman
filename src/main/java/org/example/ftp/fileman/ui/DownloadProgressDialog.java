package org.example.ftp.fileman.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;

public class DownloadProgressDialog extends Dialog<Void> {
    
    private ProgressBar progressBar;
    private Label percentLabel;
    private Label speedLabel;
    private boolean isCompleted = false;
    
    public DownloadProgressDialog(String fileName) {
        setTitle("");
        setHeaderText(null);
        
        getDialogPane().setHeader(null);
        
        ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().add(cancelButtonType);
        
        Label titleLabel = new Label("Downloading: " + fileName);
        titleLabel.getStyleClass().add("progress-dialog-title");
        
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(400);
        progressBar.getStyleClass().add("progress-dialog-bar");
        
        percentLabel = new Label("0%");
        percentLabel.getStyleClass().add("progress-dialog-percent");
        
        speedLabel = new Label("Speed: calculating...");
        speedLabel.getStyleClass().add("progress-dialog-speed");
        
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(20));
        vbox.getChildren().addAll(titleLabel, progressBar, percentLabel, speedLabel);
        
        getDialogPane().setContent(vbox);
        getDialogPane().getStyleClass().add("progress-dialog");
        setResizable(false);
        
        dialogPaneProperty().addListener((obs, oldPane, newPane) -> {
            if (newPane != null && newPane.getScene() != null) {
                javafx.stage.Window window = newPane.getScene().getWindow();
                if (window instanceof javafx.stage.Stage) {
                    javafx.stage.Stage stage = (javafx.stage.Stage) window;
                    if (stage.getStyle() != javafx.stage.StageStyle.UNDECORATED) {
                        stage.initStyle(javafx.stage.StageStyle.UNDECORATED);
                    }
                }
            }
        });
        
        setOnShowing(e -> {
            javafx.scene.Scene dialogScene = getDialogPane().getScene();
            if (dialogScene != null) {
                javafx.stage.Window window = dialogScene.getWindow();
                if (window instanceof javafx.stage.Stage) {
                    ((javafx.stage.Stage) window).initStyle(javafx.stage.StageStyle.UNDECORATED);
                    javafx.stage.Window owner = getOwner();
                    if (owner instanceof javafx.stage.Stage) {
                        javafx.scene.Scene ownerScene = ((javafx.stage.Stage) owner).getScene();
                        if (ownerScene != null && ownerScene.getStylesheets() != null && !ownerScene.getStylesheets().isEmpty()) {
                            dialogScene.getStylesheets().addAll(ownerScene.getStylesheets());
                        }
                    }
                }
            }
        });
    }
    
    public void updateProgress(double progress, String speed) {
        Platform.runLater(() -> {
            progressBar.setProgress(progress);
            percentLabel.setText(String.format("%.1f%%", progress * 100));
            if (speed != null && !speed.isEmpty()) {
                speedLabel.setText("Speed: " + speed);
            }
            
            if (progress >= 1.0 && !isCompleted) {
                isCompleted = true;
                new Thread(() -> {
                    try {
                        Thread.sleep(300);  
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    Platform.runLater(() -> {
                        close();
                    });
                }).start();
            }
        });
    }
    
    public void setCompleted() {
        updateProgress(1.0, "");
    }
}


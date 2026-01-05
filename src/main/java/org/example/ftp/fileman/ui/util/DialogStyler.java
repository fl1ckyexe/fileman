package org.example.ftp.fileman.ui.util;

import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;


public class DialogStyler {


    public static void applyStyles(Dialog<?> dialog, Window ownerWindow) {
        if (ownerWindow != null) {
            dialog.initOwner(ownerWindow);
        }

        if (ownerWindow instanceof Stage) {
            Scene ownerScene = ((Stage) ownerWindow).getScene();
            if (ownerScene != null && ownerScene.getStylesheets() != null && !ownerScene.getStylesheets().isEmpty()) {
                dialog.getDialogPane().getStylesheets().addAll(ownerScene.getStylesheets());
            }
        }


        dialog.dialogPaneProperty().addListener((obs, oldPane, newPane) -> {
            if (newPane != null && newPane.getScene() != null) {
                Window window = newPane.getScene().getWindow();
                if (window instanceof Stage) {
                    Stage stage = (Stage) window;

                    if (stage.getStyle() != StageStyle.UNDECORATED) {
                        stage.initStyle(StageStyle.UNDECORATED);
                    }
                }
            }
        });


        dialog.setOnShowing(e -> {
            Scene dialogScene = dialog.getDialogPane().getScene();
            if (dialogScene != null) {
                Window window = dialogScene.getWindow();
                if (window instanceof Stage) {
                    Stage stage = (Stage) window;
                    stage.initStyle(StageStyle.UNDECORATED);

                    if (ownerWindow instanceof Stage) {
                        Scene ownerScene = ((Stage) ownerWindow).getScene();
                        if (ownerScene != null && ownerScene.getStylesheets() != null && !ownerScene.getStylesheets().isEmpty()) {
                            dialogScene.getStylesheets().addAll(ownerScene.getStylesheets());
                        }
                    }
                }
            }
        });
    }


    public static void applyStyles(Dialog<?> dialog) {
        dialog.getDialogPane().sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                Window window = newScene.getWindow();
                if (window instanceof Stage) {
                    javafx.application.Platform.runLater(() -> {
                        try {
                            ((Stage) window).initStyle(StageStyle.UNDECORATED);
                        } catch (IllegalStateException e) {
                        }
                    });
                }
            }
        });

        dialog.setOnShowing(e -> {
            Scene dialogScene = dialog.getDialogPane().getScene();
            if (dialogScene != null) {
                Window window = dialogScene.getWindow();
                if (window instanceof Stage) {
                    try {
                        ((Stage) window).initStyle(StageStyle.UNDECORATED);
                    } catch (IllegalStateException ex) {
                    }
                }
            }
        });
    }
}


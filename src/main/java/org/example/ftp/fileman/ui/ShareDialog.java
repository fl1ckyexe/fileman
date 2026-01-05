package org.example.ftp.fileman.ui;

import org.example.ftp.fileman.ui.util.DialogStyler;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

public class ShareDialog extends Dialog<ShareDialog.ShareResult> {
    
    public static class ShareResult {
        private final String username;
        private final boolean write;
        private final boolean execute;
        
        public ShareResult(String username, boolean write, boolean execute) {
            this.username = username;
            this.write = write;
            this.execute = execute;
        }
        
        public String getUsername() { return username; }
        public boolean isWrite() { return write; }
        public boolean isExecute() { return execute; }
    }
    
    private TextField usernameField;
    private CheckBox readCheckBox;
    private CheckBox writeCheckBox;
    private CheckBox executeCheckBox;
    
    public ShareDialog(String folderPath, String folderName) {
        setTitle("Share Folder");
        setHeaderText(null);
        getDialogPane().setHeader(null);
        
        ButtonType shareButtonType = new ButtonType("Share", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(shareButtonType, cancelButtonType);
        
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(15);
        grid.setPadding(new Insets(25, 30, 15, 25));
        
        usernameField = new TextField();
        usernameField.setPromptText("Enter username");
        usernameField.setPrefWidth(200);
        
        readCheckBox = new CheckBox("Read");
        readCheckBox.setSelected(true);
        readCheckBox.setDisable(true); 
        
        writeCheckBox = new CheckBox("Write");
        executeCheckBox = new CheckBox("Execute");
        
        Label titleLabel = new Label("Share folder: " + folderPath);
        titleLabel.getStyleClass().add("dialog-title");
        grid.add(titleLabel, 0, 0, 2, 1);
        GridPane.setMargin(titleLabel, new Insets(0, 0, 15, 0));
        
        Label usernameLabel = new Label("Username:");
        grid.add(usernameLabel, 0, 1);
        grid.add(usernameField, 1, 1);
        
        Label permissionsLabel = new Label("Permissions:");
        grid.add(permissionsLabel, 0, 2);
        
        javafx.scene.layout.VBox permissionsBox = new javafx.scene.layout.VBox(8);
        permissionsBox.getChildren().addAll(readCheckBox, writeCheckBox, executeCheckBox);
        grid.add(permissionsBox, 1, 2);
        
        getDialogPane().setContent(grid);
        
        
        Button shareButton = (Button) getDialogPane().lookupButton(shareButtonType);
        shareButton.setDefaultButton(true);
        shareButton.setDisable(true);
        
        usernameField.textProperty().addListener((observable, oldValue, newValue) -> {
            shareButton.setDisable(newValue.trim().isEmpty());
        });
        
        setResultConverter(dialogButton -> {
            if (dialogButton == shareButtonType) {
                return new ShareResult(
                    usernameField.getText().trim(),
                    writeCheckBox.isSelected(),
                    executeCheckBox.isSelected()
                );
            }
            return null;
        });
    }
    
   
   
    public void applyStyles(javafx.stage.Window ownerWindow) {
        DialogStyler.applyStyles(this, ownerWindow);
    }
}


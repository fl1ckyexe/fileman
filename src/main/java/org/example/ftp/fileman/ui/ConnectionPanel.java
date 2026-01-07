package org.example.ftp.fileman.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Window;
import org.example.ftp.fileman.ftp.FtpClientService;
import org.example.ftp.fileman.ui.util.DialogStyler;

public class ConnectionPanel extends GridPane {
    
    private final FtpClientService ftpService;
    private final Runnable onConnectedCallback;
    
    private TextField hostField;
    private TextField portField;
    private TextField usernameField;
    private PasswordField passwordField;
    private Button connectButton;
    private Button disconnectButton;
    private Label statusLabel;
    
    private String currentUsername;
    private String currentHost;

    public ConnectionPanel(FtpClientService ftpService, Runnable onConnectedCallback) {
        this.ftpService = ftpService;
        this.onConnectedCallback = onConnectedCallback;
        
        getStyleClass().add("connection-panel");
        setPadding(new Insets(15));
        setHgap(15);
        setVgap(12);
        setAlignment(Pos.CENTER_LEFT);
        
        initComponents();
        layoutComponents();
    }

    private void initComponents() {
        hostField = new TextField();
        hostField.setPromptText("localhost");
        hostField.setPrefWidth(150);
        
        portField = new TextField();
        portField.setPromptText("2121");
        portField.setPrefWidth(80);
        portField.setText("2121");
        
        usernameField = new TextField();
        usernameField.setPromptText("Username");
        usernameField.setPrefWidth(120);
        
        passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.setPrefWidth(120);
        
        connectButton = new Button("Connect");
        connectButton.getStyleClass().add("primary");
        connectButton.setOnAction(e -> handleConnect());
        
        disconnectButton = new Button("Disconnect");
        disconnectButton.getStyleClass().add("danger");
        disconnectButton.setOnAction(e -> handleDisconnect());
        disconnectButton.setDisable(true);
        
        statusLabel = new Label("Not connected");
        statusLabel.getStyleClass().add("status-disconnected");
    }

    private void layoutComponents() {
        Label hostLabel = new Label("Host:");
        add(hostLabel, 0, 0);
        add(hostField, 1, 0);
        
        Label portLabel = new Label("Port:");
        add(portLabel, 2, 0);
        add(portField, 3, 0);
        
        Label usernameLabel = new Label("Username:");
        add(usernameLabel, 4, 0);
        add(usernameField, 5, 0);
        
        Label passwordLabel = new Label("Password:");
        add(passwordLabel, 6, 0);
        add(passwordField, 7, 0);
        
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_LEFT);
        buttonBox.getChildren().addAll(connectButton, disconnectButton, new Separator(), statusLabel);
        add(buttonBox, 8, 0);
    }

    private void handleConnect() {
        String host = hostField.getText().trim();
        String portText = portField.getText().trim();
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        
        if (host.isEmpty()) {
            showAlert("Error", "Please enter host address");
            return;
        }
        
        int port = 21;
        try {
            if (!portText.isEmpty()) {
                port = Integer.parseInt(portText);
            }
        } catch (NumberFormatException e) {
            showAlert("Error", "Invalid port number");
            return;
        }
        
        if (username.isEmpty()) {
            showAlert("Error", "Please enter username");
            return;
        }
        
        connectButton.setDisable(true);
        statusLabel.setText("Connecting...");
        statusLabel.getStyleClass().removeAll("status-disconnected", "status-connected", "status-error");
        statusLabel.getStyleClass().add("status-connecting");
        
        int finalPort = port;
        new Thread(() -> {
            boolean connected = ftpService.connect(host, finalPort, username, password);
            
            javafx.application.Platform.runLater(() -> {
                if (connected) {
                    currentUsername = username;
                    currentHost = host;
                    statusLabel.setText("Connected");
                    statusLabel.getStyleClass().removeAll("status-disconnected", "status-connecting", "status-error");
                    statusLabel.getStyleClass().add("status-connected");
                    connectButton.setDisable(true);
                    disconnectButton.setDisable(false);
                    hostField.setDisable(true);
                    portField.setDisable(false);
                    usernameField.setDisable(true);
                    passwordField.setDisable(true);
                    
                    if (onConnectedCallback != null) {
                        onConnectedCallback.run();
                    }
                } else {
                    statusLabel.setText("Connection failed");
                    statusLabel.getStyleClass().removeAll("status-disconnected", "status-connecting", "status-connected");
                    statusLabel.getStyleClass().add("status-error");
                    connectButton.setDisable(false);
                    String msg = ftpService.getLastErrorMessage();
                    if (msg == null || msg.isBlank()) {
                        msg = "Failed to connect to FTP server.";
                    }
                    showAlert("Connection Error", msg);
                }
            });
        }).start();
    }

    private void handleDisconnect() {
        ftpService.disconnect();
        currentUsername = null;
        currentHost = null;
        statusLabel.setText("Disconnected");
        statusLabel.getStyleClass().removeAll("status-connected", "status-connecting", "status-error");
        statusLabel.getStyleClass().add("status-disconnected");
        connectButton.setDisable(false);
        disconnectButton.setDisable(true);
        hostField.setDisable(false);
        portField.setDisable(false);
        usernameField.setDisable(false);
        passwordField.setDisable(false);
    }

    /**
     * Used when the server becomes unavailable (socket closed / port down) and we need to reflect it in UI.
     */
    public void forceDisconnect(String reason) {
        handleDisconnect();
        if (reason != null && !reason.isBlank()) {
            statusLabel.setText(reason);
        }
    }
    
    public String getCurrentUsername() {
        return currentUsername;
    }
    
    public String getCurrentHost() {
        return currentHost;
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.getDialogPane().setHeader(null);
        
        Window ownerWindow = hostField.getScene().getWindow();
        DialogStyler.applyStyles(alert, ownerWindow);
        
        alert.showAndWait();
    }
}

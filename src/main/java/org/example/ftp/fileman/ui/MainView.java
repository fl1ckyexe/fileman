package org.example.ftp.fileman.ui;

import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.example.ftp.fileman.ftp.FtpClientService;
 
public class MainView extends BorderPane {
    
    private final FtpClientService ftpService;
    private final ConnectionPanel connectionPanel;
    private final FileBrowserPanel fileBrowserPanel;

    public MainView(Stage stage) {
        this.ftpService = new FtpClientService();
        this.connectionPanel = new ConnectionPanel(ftpService, this::onConnected);
        this.fileBrowserPanel = new FileBrowserPanel(ftpService, connectionPanel);
        
        initializeLayout(stage);
    }
    
 
    private void initializeLayout(Stage stage) {
        
        TitleBar titleBar = new TitleBar(stage, "FTP Fileman");
        
        
        VBox topContainer = new VBox();
        topContainer.getChildren().addAll(titleBar, connectionPanel);
        
        
        setTop(topContainer);
        
        
        setCenter(fileBrowserPanel);
    }
 
    private void onConnected() {
        fileBrowserPanel.refresh();
    }
}


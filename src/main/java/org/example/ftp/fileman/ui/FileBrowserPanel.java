package org.example.ftp.fileman.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.stage.FileChooser.ExtensionFilter;
import org.example.ftp.fileman.api.FolderPermissionsApiClient;
import org.example.ftp.fileman.ftp.FtpClientService;
import org.example.ftp.fileman.ftp.FtpFileInfo;
import org.example.ftp.fileman.ftp.FolderType;
import org.example.ftp.fileman.service.NavigationService;
import org.example.ftp.fileman.ui.util.DialogStyler;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileBrowserPanel extends BorderPane {

    private final FtpClientService ftpService;
    private final ConnectionPanel connectionPanel;
    private final FolderPermissionsApiClient apiClient;

    private TableView<FtpFileInfo> globalTable;
    private TableView<FtpFileInfo> yourDirectoryTable;
    private TableView<FtpFileInfo> sharedTable;

    private Label globalPathLabel;
    private Label yourDirectoryPathLabel;
    private Label sharedPathLabel;
    private Button refreshButton;
    private Button shareButton;

    private volatile String globalCurrentPath = "/shared";
    private volatile String yourDirectoryCurrentPath = "/username";
    private volatile String sharedCurrentPath = "/";
    private String currentUsername;
    private String lastUsernameSeen;
    private FtpFileInfo selectedFolder;

    private volatile boolean currentSharedFolderWrite = false;
    private volatile boolean currentSharedFolderExecute = false;

    private volatile boolean currentGlobalFolderRead = false;
    private volatile boolean currentGlobalFolderWrite = false;
    private volatile boolean currentGlobalFolderExecute = false;

    public FileBrowserPanel(FtpClientService ftpService, ConnectionPanel connectionPanel) {
        this.ftpService = ftpService;
        this.connectionPanel = connectionPanel;

        String initialHost = connectionPanel.getCurrentHost();
        this.apiClient = new FolderPermissionsApiClient(initialHost != null ? initialHost : "localhost");

        initComponents();
        layoutComponents();
    }

    private void initComponents() {
        globalTable = createFileTable(FolderType.GLOBAL);
        yourDirectoryTable = createFileTable(FolderType.YOUR_DIRECTORY);
        sharedTable = createFileTable(FolderType.SHARED_BY_USER);

        setupTableSelection(globalTable);
        setupTableSelection(yourDirectoryTable);
        setupTableSelection(sharedTable);

        globalPathLabel = new Label("Global path: /shared");
        globalPathLabel.getStyleClass().add("path-label");

        yourDirectoryPathLabel = new Label("Your Directory path: /username");
        yourDirectoryPathLabel.getStyleClass().add("path-label");

        sharedPathLabel = new Label("Shared by User path: /");
        sharedPathLabel.getStyleClass().add("path-label");

        refreshButton = new Button("\uD83D\uDD04 Refresh");
        refreshButton.getStyleClass().add("primary");
        refreshButton.setOnAction(e -> refresh());

        shareButton = new Button("\uD83D\uDCE4 Share");
        shareButton.getStyleClass().add("success");
        shareButton.setDisable(true);
        shareButton.setOnAction(e -> handleShare());

    }

    private TableView<FtpFileInfo> createFileTable(FolderType folderType) {
        TableView<FtpFileInfo> table = new TableView<>();

        TableColumn<FtpFileInfo, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameColumn.setPrefWidth(250);

        TableColumn<FtpFileInfo, String> typeColumn = new TableColumn<>("Type");
        typeColumn.setCellValueFactory(cellData -> {
            FtpFileInfo file = cellData.getValue();
            return new javafx.beans.property.SimpleStringProperty(
                file.isDirectory() ? "Directory" : "File"
            );
        });
        typeColumn.setPrefWidth(100);

        TableColumn<FtpFileInfo, String> sizeColumn = new TableColumn<>("Size");
        sizeColumn.setCellValueFactory(cellData -> {
            FtpFileInfo file = cellData.getValue();
            return new javafx.beans.property.SimpleStringProperty(file.getFormattedSize());
        });
        sizeColumn.setPrefWidth(100);

        TableColumn<FtpFileInfo, String> dateColumn = new TableColumn<>("Modified");
        dateColumn.setCellValueFactory(cellData -> {
            FtpFileInfo file = cellData.getValue();
            return new javafx.beans.property.SimpleStringProperty(file.getFormattedDate());
        });
        dateColumn.setPrefWidth(150);

        @SuppressWarnings("unchecked")
        TableColumn<FtpFileInfo, String>[] columns = new TableColumn[] {nameColumn, typeColumn, sizeColumn, dateColumn};
        table.getColumns().addAll(columns);

        final FolderType finalFolderType = folderType;
        table.setRowFactory(tv -> {
            TableRow<FtpFileInfo> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    FtpFileInfo file = row.getItem();
                    if (file.isDirectory()) {
                        navigateToDirectoryInSection(finalFolderType, file);
                    }
                }
            });
            return row;
        });

        ContextMenu contextMenu = createContextMenu(folderType, table);
        table.setContextMenu(contextMenu);

        return table;
    }

    private ContextMenu createContextMenu(FolderType folderType, TableView<FtpFileInfo> table) {
        ContextMenu contextMenu = new ContextMenu();

        MenuItem createFolderItem = new MenuItem("Create folder");
        createFolderItem.setOnAction(e -> handleCreateDirectory(folderType));

        MenuItem uploadFileItem = new MenuItem("Upload file");
        uploadFileItem.setOnAction(e -> handleUploadFile(folderType));

        MenuItem downloadFileItem = new MenuItem("Download file");
        downloadFileItem.setOnAction(e -> {
            FtpFileInfo selected = table.getSelectionModel().getSelectedItem();
            if (selected != null && !selected.isDirectory()) {
                handleDownloadFile(folderType, selected);
            }
        });

        MenuItem deleteItem = new MenuItem("Delete");
        deleteItem.setOnAction(e -> {
            FtpFileInfo selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                handleDelete(folderType, selected);
            }
        });

        contextMenu.setOnShowing(e -> {
            FtpFileInfo selected = table.getSelectionModel().getSelectedItem();

            if (folderType == FolderType.SHARED_BY_USER) {
                boolean hasWrite = currentSharedFolderWrite;
                boolean hasExecute = currentSharedFolderExecute;


                if (!hasWrite && !hasExecute && !sharedCurrentPath.equals("/")) {
                    try {
                        String username = connectionPanel.getCurrentUsername();
                        if (username != null) {
                            List<FolderPermissionsApiClient.SharedFolder> sharedFoldersList = apiClient.getSharedFolders(username);
                            FolderPermissionsApiClient.SharedFolder bestMatch = null;
                            int bestMatchLength = -1;

                            String normalizedSharedCurrentPath = normalizePath(sharedCurrentPath);

                            for (FolderPermissionsApiClient.SharedFolder sharedFolder : sharedFoldersList) {
                                String folderPath = sharedFolder.getFolderPath();
                                String normalizedFolderPath = normalizePath(folderPath);
                                boolean matches = normalizedSharedCurrentPath.equals(normalizedFolderPath) || normalizedSharedCurrentPath.startsWith(normalizedFolderPath + "/");
                                if (matches) {
                                    if (normalizedFolderPath.length() > bestMatchLength) {
                                        bestMatch = sharedFolder;
                                        bestMatchLength = normalizedFolderPath.length();
                                    }
                                }
                            }

                            if (bestMatch != null) {
                                hasWrite = bestMatch.isWrite();
                                hasExecute = bestMatch.isExecute();
                                currentSharedFolderWrite = hasWrite;
                                currentSharedFolderExecute = hasExecute;
                            }
                        }
                    } catch (Exception ex) {
                    }
                }

                updateContextMenuItems(createFolderItem, uploadFileItem, downloadFileItem, deleteItem, selected, hasWrite, hasExecute);
            } else if (folderType == FolderType.GLOBAL) {
                boolean hasWrite = currentGlobalFolderWrite;
                boolean hasExecute = currentGlobalFolderExecute;


                updateContextMenuItems(createFolderItem, uploadFileItem, downloadFileItem, deleteItem, selected, hasWrite, hasExecute);
            } else if (folderType == FolderType.YOUR_DIRECTORY) {
                createFolderItem.setDisable(false);
                uploadFileItem.setDisable(false);

                if (selected != null && !selected.isDirectory()) {
                    downloadFileItem.setDisable(false);
                } else {
                    downloadFileItem.setDisable(true);
                }

                if (selected != null) {
                    deleteItem.setDisable(false);
                } else {
                    deleteItem.setDisable(true);
                }
            }
        });

        contextMenu.getItems().addAll(createFolderItem, uploadFileItem, new SeparatorMenuItem(), downloadFileItem, deleteItem);

        return contextMenu;
    }

    private String normalizePath(String path) {
        if (path == null || path.isEmpty() || path.equals("/")) {
            return path;
        }

        String[] parts = path.split("/");
        java.util.ArrayList<String> normalizedParts = new java.util.ArrayList<>();

        for (String part : parts) {
            if (part.isEmpty() || part.equals(".")) {
                continue;
            } else if (part.equals("..")) {
                if (!normalizedParts.isEmpty() && !normalizedParts.get(normalizedParts.size() - 1).equals("..")) {
                    normalizedParts.remove(normalizedParts.size() - 1);
                }
            } else {
                normalizedParts.add(part);
            }
        }

        if (normalizedParts.isEmpty()) {
            return "/";
        }

        StringBuilder result = new StringBuilder();
        for (String part : normalizedParts) {
            result.append("/").append(part);
        }

        return result.toString();
    }

    private void updateContextMenuItems(MenuItem createFolderItem, MenuItem uploadFileItem, MenuItem downloadFileItem, MenuItem deleteItem,
                                       FtpFileInfo selected, boolean hasWrite, boolean hasExecute) {
        createFolderItem.setDisable(!hasWrite);
        uploadFileItem.setDisable(!hasWrite);
        if (selected != null && !selected.isDirectory()) {
            downloadFileItem.setDisable(!hasExecute);
        } else {
            downloadFileItem.setDisable(true);
        }

        if (selected != null) {
            deleteItem.setDisable(!hasExecute);
        } else {
            deleteItem.setDisable(true);
        }
    }

    private void setupTableSelection(TableView<FtpFileInfo> table) {
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null && newSelection.isDirectory()) {
                if (newSelection.getFolderType() == FolderType.YOUR_DIRECTORY) {
                    selectedFolder = newSelection;
                    shareButton.setDisable(false);
                } else {
                    selectedFolder = null;
                    shareButton.setDisable(true);
                }
            } else {
                selectedFolder = null;
                shareButton.setDisable(true);
            }
        });
    }

    private void layoutComponents() {
        HBox topPanel = new HBox(15);
        topPanel.getStyleClass().add("top-panel");
        topPanel.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        topPanel.getChildren().addAll(refreshButton, shareButton);

        HBox columnsContainer = new HBox(15);
        columnsContainer.setPadding(new Insets(15));
        columnsContainer.setAlignment(javafx.geometry.Pos.TOP_LEFT);

        VBox globalColumn = createSectionColumn(
            "\uD83C\uDF10 Global",
            globalPathLabel,
            globalTable,
            FolderType.GLOBAL
        );
        globalColumn.getStyleClass().add("section-column");
        globalColumn.setMinWidth(300);
        globalColumn.setPrefWidth(Region.USE_COMPUTED_SIZE);

        VBox yourDirectoryColumn = createSectionColumn(
            "\uD83D\uDCC1 Your Directory",
            yourDirectoryPathLabel,
            yourDirectoryTable,
            FolderType.YOUR_DIRECTORY
        );
        yourDirectoryColumn.getStyleClass().add("section-column");
        yourDirectoryColumn.setMinWidth(300);
        yourDirectoryColumn.setPrefWidth(Region.USE_COMPUTED_SIZE);

        VBox sharedColumn = createSectionColumn(
            "\uD83D\uDC65 Shared by User",
            sharedPathLabel,
            sharedTable,
            FolderType.SHARED_BY_USER
        );
        sharedColumn.getStyleClass().add("section-column");
        sharedColumn.setMinWidth(300);
        sharedColumn.setPrefWidth(Region.USE_COMPUTED_SIZE);

        columnsContainer.getChildren().addAll(globalColumn, yourDirectoryColumn, sharedColumn);

        HBox.setHgrow(globalColumn, Priority.ALWAYS);
        HBox.setHgrow(yourDirectoryColumn, Priority.ALWAYS);
        HBox.setHgrow(sharedColumn, Priority.ALWAYS);

        ScrollPane scrollPane = new ScrollPane(columnsContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.getStyleClass().add("main-scroll-pane");

        setTop(topPanel);
        setCenter(scrollPane);
    }

    private VBox createSectionColumn(String title, Label pathLabel, TableView<FtpFileInfo> table, FolderType folderType) {
        VBox column = new VBox(10);
        column.setPadding(new Insets(0));

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("section-title");

        HBox pathPanel = new HBox(10);
        pathPanel.getStyleClass().add("section-path-panel");
        pathPanel.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Button upButton = new Button("\u2B06");
        upButton.getStyleClass().add("up-button");
        upButton.setTooltip(new Tooltip("Go up one level"));
        upButton.setOnAction(e -> navigateUpInSection(folderType));

        pathLabel.getStyleClass().add("path-label");

        if (folderType != FolderType.SHARED_BY_USER) {
            Button createDirButton = new Button("\u2795");
            createDirButton.getStyleClass().add("create-dir-button");
            createDirButton.setTooltip(new Tooltip("Create new directory"));
            createDirButton.setOnAction(e -> handleCreateDirectory(folderType));
            pathPanel.getChildren().addAll(upButton, createDirButton, pathLabel);
        } else {
            pathPanel.getChildren().addAll(upButton, pathLabel);
        }

        ScrollPane tableScrollPane = new ScrollPane(table);
        tableScrollPane.setFitToWidth(true);
        tableScrollPane.setFitToHeight(true);
        tableScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        tableScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        tableScrollPane.getStyleClass().add("table-scroll-pane");

        VBox sectionBox = new VBox(8);
        sectionBox.getStyleClass().add("section-box");
        sectionBox.getChildren().addAll(titleLabel, pathPanel, tableScrollPane);

        column.getChildren().add(sectionBox);

        return column;
    }

    private String formatSharedPath(String sharedPath) {
        if (sharedPath == null || sharedPath.equals("/")) {
            return "/";
        }

        String normalized = sharedPath.startsWith("/") ? sharedPath.substring(1) : sharedPath;
        String[] parts = normalized.split("/");

        if (parts.length < 2) {
        }

        String folderName = parts[1];
        StringBuilder result = new StringBuilder("/*");
        result.append(folderName);
        result.append("*");

        if (parts.length > 2) {
            result.append("/");
            for (int i = 2; i < parts.length; i++) {
                if (i > 2) {
                    result.append("/");
                }
                result.append(parts[i]);
            }
        }

        return result.toString();
    }

    public void refresh() {
        refresh(null, null, null);
    }

    private void refresh(String globalPathOverride, String yourDirectoryPathOverride, String sharedPathOverride) {
        final String normalizedSharedPathOverride = sharedPathOverride != null ? normalizePath(sharedPathOverride) : sharedPathOverride;
        if (!ftpService.isConnected()) {
            clearAllTables();
            globalPathLabel.setText("Global path: Not connected");
            yourDirectoryPathLabel.setText("Your Directory path: Not connected");
            sharedPathLabel.setText("Shared by User path: Not connected");
            return;
        }

        String currentHost = connectionPanel.getCurrentHost();
        if (currentHost != null && !currentHost.isEmpty()) {
            apiClient.setServerHost(currentHost);
        }

        currentUsername = connectionPanel.getCurrentUsername();
        if (currentUsername == null || currentUsername.isEmpty()) {
            clearAllTables();
            return;
        }

        if (lastUsernameSeen == null || !lastUsernameSeen.equals(currentUsername)) {
            lastUsernameSeen = currentUsername;

            globalCurrentPath = "/shared";
            yourDirectoryCurrentPath = "/" + currentUsername;
            sharedCurrentPath = "/";

            selectedFolder = null;
            Platform.runLater(() -> {
                shareButton.setDisable(true);
                globalPathLabel.setText("Global path: /shared");
                yourDirectoryPathLabel.setText("Your Directory path: /" + currentUsername);
                sharedPathLabel.setText("Shared by User path: /");
            });

            currentSharedFolderWrite = false;
            currentSharedFolderExecute = false;
            currentGlobalFolderRead = false;
            currentGlobalFolderWrite = false;
            currentGlobalFolderExecute = false;
        }

        String userRootPath = "/" + currentUsername;
        if (yourDirectoryCurrentPath == null
                || yourDirectoryCurrentPath.isEmpty()
                || yourDirectoryCurrentPath.equals("/admin")
                || yourDirectoryCurrentPath.equals("/username")
                || (!yourDirectoryCurrentPath.equals(userRootPath) && !yourDirectoryCurrentPath.startsWith(userRootPath + "/"))) {
            yourDirectoryCurrentPath = userRootPath;
        }

        new Thread(() -> {
            final String globalPath = globalPathOverride != null ? globalPathOverride : globalCurrentPath;
            final String yourDirectoryPath = yourDirectoryPathOverride != null ? yourDirectoryPathOverride : yourDirectoryCurrentPath;
            final String sharedPath = normalizedSharedPathOverride != null ? normalizedSharedPathOverride : sharedCurrentPath;

            try {
                String savedPath = ftpService.getCurrentDirectory();

                List<FtpFileInfo> globalFiles = new ArrayList<>();
                boolean globalHasRead = false;
                boolean globalHasWrite = false;
                boolean globalHasExecute = false;

                try {
                    FolderPermissionsApiClient.UserPermissions userPermissions = apiClient.getUserPermissions(currentUsername);
                    globalHasRead = userPermissions.isRead();
                    globalHasWrite = userPermissions.isWrite();
                    globalHasExecute = userPermissions.isExecute();
                } catch (Exception e) {
                }

                final boolean finalGlobalHasRead = globalHasRead;
                final boolean finalGlobalHasWrite = globalHasWrite;
                final boolean finalGlobalHasExecute = globalHasExecute;

                Platform.runLater(() -> {
                    currentGlobalFolderRead = finalGlobalHasRead;
                    currentGlobalFolderWrite = finalGlobalHasWrite;
                    currentGlobalFolderExecute = finalGlobalHasExecute;
                });

                try {
                    String currentDirBefore = ftpService.getCurrentDirectory();
                    String normalizedGlobalPath = globalPath.replace('\\', '/');
                    String normalizedCurrentDir = currentDirBefore.replace('\\', '/');
                    boolean alreadyInTargetDir = normalizedCurrentDir.equals(normalizedGlobalPath) ||
                                                 normalizedCurrentDir.equals(normalizedGlobalPath + "/") ||
                                                 normalizedGlobalPath.equals(normalizedCurrentDir + "/");

                    boolean changedSuccessfully = alreadyInTargetDir;
                    if (!alreadyInTargetDir) {
                        changedSuccessfully = ftpService.changeDirectory(globalPath);
                    }

                    if (changedSuccessfully) {
                        List<FtpFileInfo> filesList = ftpService.listFiles("");
                        if (filesList != null) {
                            for (FtpFileInfo file : filesList) {
                                file.setFolderType(FolderType.GLOBAL);
                                String fullPath = globalPath.equals("/shared")
                                    ? "/shared/" + file.getName()
                                    : globalPath + "/" + file.getName();
                                file.setFullPath(fullPath);
                                globalFiles.add(file);
                            }
                        }
                    }
                } catch (Exception e) {
                    if (!ftpService.isConnected()) {
                        throw e;
                    }
                }

                List<FtpFileInfo> yourDirectoryFiles = new ArrayList<>();
                try {

                    String currentDirBefore = ftpService.getCurrentDirectory();

                    String normalizedYourDirPath = yourDirectoryPath.replace('\\', '/');
                    String normalizedCurrentDir = currentDirBefore.replace('\\', '/');
                    boolean alreadyInTargetDir = normalizedCurrentDir.equals(normalizedYourDirPath) ||
                                                 normalizedCurrentDir.equals(normalizedYourDirPath + "/") ||
                                                 normalizedYourDirPath.equals(normalizedCurrentDir + "/");

                    boolean changedSuccessfully = alreadyInTargetDir;
                    if (!alreadyInTargetDir) {
                        changedSuccessfully = ftpService.changeDirectory(yourDirectoryPath);
                        if (changedSuccessfully) {
                            String actualPath = ftpService.getCurrentDirectory();
                        }
                    } else {
                    }

                    if (changedSuccessfully) {
                        List<FtpFileInfo> filesList = ftpService.listFiles("");

                        if (filesList != null) {
                            for (FtpFileInfo file : filesList) {
                                file.setFolderType(FolderType.YOUR_DIRECTORY);
                                String fullPath;
                                if (yourDirectoryPath.equals("/" + currentUsername)) {
                                    fullPath = "/" + currentUsername + "/" + file.getName();
                                } else {
                                    fullPath = yourDirectoryPath + "/" + file.getName();
                                }
                                file.setFullPath(fullPath);
                                yourDirectoryFiles.add(file);
                            }
                        } else {
                        }
                    } else {
                        String currentDirAfter = null;
                        try {
                            currentDirAfter = ftpService.getCurrentDirectory();
                        } catch (Exception e) {
                        }
                    }
                } catch (Exception e) {
                    if (!ftpService.isConnected()) {
                        throw e;
                    }
                }

                List<FtpFileInfo> sharedFiles = new ArrayList<>();
                if (sharedPath.equals("/")) {
                    currentSharedFolderWrite = false;
                    currentSharedFolderExecute = false;

                    try {
                        List<FolderPermissionsApiClient.SharedFolder> sharedFoldersList = apiClient.getSharedFolders(currentUsername);
                        for (FolderPermissionsApiClient.SharedFolder sharedFolder : sharedFoldersList) {
                            try {
                                FtpFileInfo folderInfo = new FtpFileInfo(
                                    sharedFolder.getFolderName(),
                                    true,
                                    0,
                                    null,
                                    FolderType.SHARED_BY_USER,
                                    sharedFolder.getFolderPath()
                                );
                                sharedFiles.add(folderInfo);
                            } catch (Exception e) {
                            }
                        }
                    } catch (Exception e) {
                        if (!ftpService.isConnected()) {
                            throw e;
                        }
                    }
                } else {

                    boolean hasWrite = false;
                    boolean hasExecute = false;
                    try {
                        List<FolderPermissionsApiClient.SharedFolder> sharedFoldersList = apiClient.getSharedFolders(currentUsername);
                        String normalizedSharedPath = normalizePath(sharedPath);

                        FolderPermissionsApiClient.SharedFolder bestMatch = null;
                        int bestMatchLength = -1;

                        for (FolderPermissionsApiClient.SharedFolder sharedFolder : sharedFoldersList) {
                            String folderPath = sharedFolder.getFolderPath();
                            String normalizedFolderPath = normalizePath(folderPath);
                            boolean matches = normalizedSharedPath.equals(normalizedFolderPath) || normalizedSharedPath.startsWith(normalizedFolderPath + "/");
                            if (matches) {
                                if (normalizedFolderPath.length() > bestMatchLength) {
                                    bestMatch = sharedFolder;
                                    bestMatchLength = normalizedFolderPath.length();
                                }
                            }
                        }

                        if (bestMatch != null) {
                            hasWrite = bestMatch.isWrite();
                            hasExecute = bestMatch.isExecute();
                        } else {
                        }
                    } catch (Exception e) {
                    }

                    final boolean finalHasWrite = hasWrite;
                    final boolean finalHasExecute = hasExecute;

                    try {
                        if (ftpService.changeDirectory(sharedPath)) {
                            List<FtpFileInfo> filesList = ftpService.listFiles("");
                            if (filesList != null) {

                                for (FtpFileInfo file : filesList) {
                                    file.setFolderType(FolderType.SHARED_BY_USER);
                                    String fileFullPath = sharedPath + "/" + file.getName();
                                    file.setFullPath(fileFullPath);
                                    sharedFiles.add(file);
                                }
                            } else {
                            }
                        } else {
                        }
                    } catch (Exception e) {
                    }

                    Platform.runLater(() -> {
                        currentSharedFolderWrite = finalHasWrite;
                        currentSharedFolderExecute = finalHasExecute;
                    });
                }

                try {
                    ftpService.changeDirectory(savedPath);
                } catch (Exception e) {
                    if (!ftpService.isConnected()) {
                        throw e;
                    }
                }


                Platform.runLater(() -> {
                    globalTable.getItems().setAll(globalFiles);
                    yourDirectoryTable.getItems().setAll(yourDirectoryFiles);
                    sharedTable.getItems().setAll(sharedFiles);
                    globalPathLabel.setText("Global path: " + globalPath);
                    yourDirectoryPathLabel.setText("Your Directory path: " + yourDirectoryPath);
                    String formattedSharedPath = formatSharedPath(sharedPath);
                    sharedPathLabel.setText("Shared by User path: " + formattedSharedPath);
                });

            } catch (Exception e) {
                if (!ftpService.isConnected()) {
                    Platform.runLater(() -> {
                        clearAllTables();
                        connectionPanel.forceDisconnect("Disconnected (server unavailable)");
                        globalPathLabel.setText("Global path: Not connected");
                        yourDirectoryPathLabel.setText("Your Directory path: Not connected");
                        sharedPathLabel.setText("Shared by User path: Not connected");
                    });
                } else {
                    Platform.runLater(() -> {
                        String reason = ftpService.getLastErrorMessage();
                        if (reason == null || reason.isBlank()) {
                            reason = "Failed to list files: " + e.getMessage();
                        }
                        showAlert("Error", reason);
                    });
                }
            }
        }).start();
    }

    private void clearAllTables() {
        Platform.runLater(() -> {
            globalTable.getItems().clear();
            yourDirectoryTable.getItems().clear();
            sharedTable.getItems().clear();
        });
    }

    private void navigateToDirectoryInSection(FolderType sectionType, FtpFileInfo file) {

        if (!ftpService.isConnected()) {
            return;
        }

        String currentPathForSection = null;
        switch (sectionType) {
            case GLOBAL:
                currentPathForSection = globalCurrentPath;
                break;
            case YOUR_DIRECTORY:
                String labelText = yourDirectoryPathLabel.getText();
                if (labelText.startsWith("Your Directory path: ")) {
                    currentPathForSection = labelText.substring("Your Directory path: ".length());
                } else {
                    currentPathForSection = yourDirectoryCurrentPath;
                }
                break;
            case SHARED_BY_USER:
                currentPathForSection = sharedCurrentPath;
                break;
        }

        final String finalCurrentPathForSection = currentPathForSection;
        final String targetPath = (sectionType == FolderType.SHARED_BY_USER && file.getFullPath() != null)
            ? file.getFullPath()
            : file.getName();

        new Thread(() -> {
            try {
                String savedCurrentDir = null;

                try {
                    savedCurrentDir = ftpService.getCurrentDirectory();
                } catch (Exception e) {
                }

                if (sectionType == FolderType.YOUR_DIRECTORY) {
                    currentUsername = connectionPanel.getCurrentUsername();
                    if (currentUsername == null || currentUsername.isEmpty()) {
                        Platform.runLater(() -> showAlert("Error", "Username not available"));
                        return;
                    }
                } else if (sectionType == FolderType.GLOBAL) {
                } else if (sectionType == FolderType.SHARED_BY_USER) {
                }

                String targetDirectoryPath;
                if (sectionType == FolderType.SHARED_BY_USER && file.getFullPath() != null) {
                    targetDirectoryPath = file.getFullPath();
                } else {
                    if (!ftpService.changeDirectory(finalCurrentPathForSection)) {
                        Platform.runLater(() -> {
                            String reason = ftpService.getLastErrorMessage();
                            if (reason == null || reason.isBlank()) {
                                reason = "Failed to change to directory: " + finalCurrentPathForSection;
                            }
                            showAlert("Error", reason);
                        });
                        return;
                    }

                    String afterStep1 = ftpService.getCurrentDirectory();

                    targetDirectoryPath = targetPath;
                }

                if (ftpService.changeDirectory(targetDirectoryPath)) {
                    String actualPath = ftpService.getCurrentDirectory();

                    final String finalActualPath = actualPath;

                    Platform.runLater(() -> {
                        String pathBeforeUpdate = null;

                        switch (sectionType) {
                            case GLOBAL:
                                pathBeforeUpdate = globalCurrentPath;
                                globalCurrentPath = finalActualPath;
                                break;
                            case YOUR_DIRECTORY:
                                pathBeforeUpdate = yourDirectoryCurrentPath;
                                yourDirectoryCurrentPath = finalActualPath;
                                break;
                            case SHARED_BY_USER:
                                pathBeforeUpdate = sharedCurrentPath;
                                sharedCurrentPath = normalizePath(finalActualPath);
                                break;
                        }

                        String refreshGlobalPath = sectionType == FolderType.GLOBAL ? finalActualPath : null;
                        String refreshYourDirectoryPath = sectionType == FolderType.YOUR_DIRECTORY ? finalActualPath : null;
                        String refreshSharedPath = sectionType == FolderType.SHARED_BY_USER ? finalActualPath : null;
                        refresh(refreshGlobalPath, refreshYourDirectoryPath, refreshSharedPath);
                    });
                } else {
                    Platform.runLater(() -> {
                        String reason = ftpService.getLastErrorMessage();
                        if (reason == null || reason.isBlank()) {
                            reason = "Failed to navigate to: " + targetPath;
                        }
                        showAlert("Error", reason);
                    });
                }

            } catch (Exception e) {
                Platform.runLater(() -> {
                    String reason = ftpService.getLastErrorMessage();
                    if (reason == null || reason.isBlank()) {
                        reason = "Failed to navigate: " + e.getMessage();
                    }
                    showAlert("Error", reason);
                });
            } finally {
            }
        }).start();
    }

    private void navigateUpInSection(FolderType sectionType) {

        if (!ftpService.isConnected()) {
            return;
        }

        String currentGlobalPath = globalCurrentPath;
        String currentYourDirectoryPath = yourDirectoryCurrentPath;
        String currentSharedPath = sharedCurrentPath;

        if (sectionType == FolderType.YOUR_DIRECTORY) {
            String labelText = yourDirectoryPathLabel.getText();
            if (labelText.startsWith("Your Directory path: ")) {
                String extractedPath = labelText.substring("Your Directory path: ".length());
                currentYourDirectoryPath = extractedPath;
            }
        }

        if (sectionType == FolderType.SHARED_BY_USER) {
            currentSharedPath = sharedCurrentPath;
        }

        final String finalCurrentGlobalPath = currentGlobalPath;
        final String finalCurrentYourDirectoryPath = currentYourDirectoryPath;
        final String finalCurrentSharedPath = currentSharedPath;

        new Thread(() -> {
            try {
                String newPath = null;
                String currentUsernameForCheck = connectionPanel.getCurrentUsername();

                switch (sectionType) {
                    case GLOBAL:
                        if (finalCurrentGlobalPath.equals("/shared")) {
                            return;
                        }
                        newPath = NavigationService.getParentPath(finalCurrentGlobalPath);
                        if (!newPath.startsWith("/shared")) {
                            newPath = "/shared";
                        }
                        break;
                    case YOUR_DIRECTORY:
                        if (currentUsernameForCheck == null || currentUsernameForCheck.isEmpty()) {
                            Platform.runLater(() -> showAlert("Error", "Username not available"));
                            return;
                        }
                        String userRootPath = "/" + currentUsernameForCheck;

                        if (finalCurrentYourDirectoryPath.equals(userRootPath)) {
                            return;
                        }
                        newPath = NavigationService.getParentPath(finalCurrentYourDirectoryPath);
                        if (!newPath.startsWith(userRootPath)) {
                            newPath = userRootPath;
                        }
                        break;
                    case SHARED_BY_USER:
                        if (finalCurrentSharedPath.equals("/")) {
                            return;
                        }
                        String parentPath = NavigationService.getParentPath(finalCurrentSharedPath);
                        String normalizedCurrent = normalizePath(finalCurrentSharedPath);
                        String normalizedParent = normalizePath(parentPath);

                        String sharedRoot = null;
                        try {
                            List<FolderPermissionsApiClient.SharedFolder> sharedFoldersList = apiClient.getSharedFolders(currentUsernameForCheck);
                            FolderPermissionsApiClient.SharedFolder bestMatch = null;
                            int bestMatchLength = -1;
                            for (FolderPermissionsApiClient.SharedFolder sharedFolder : sharedFoldersList) {
                                String folderPath = sharedFolder.getFolderPath();
                                String normalizedFolderPath = normalizePath(folderPath);
                                boolean matches = normalizedCurrent.equals(normalizedFolderPath) || normalizedCurrent.startsWith(normalizedFolderPath + "/");
                                if (matches && normalizedFolderPath.length() > bestMatchLength) {
                                    bestMatch = sharedFolder;
                                    bestMatchLength = normalizedFolderPath.length();
                                }
                            }
                            if (bestMatch != null) {
                                sharedRoot = normalizePath(bestMatch.getFolderPath());
                            }
                        } catch (Exception e) {
                        }

                        if (sharedRoot != null) {
                            boolean parentIsInsideSharedRoot = normalizedParent.equals(sharedRoot) || normalizedParent.startsWith(sharedRoot + "/");
                            if (normalizedCurrent.equals(sharedRoot) || !parentIsInsideSharedRoot) {
                                newPath = "/";
                            } else {
                                newPath = parentPath;
                            }
                        } else {
                            newPath = parentPath;
                        }
                        break;
                }

                if (newPath == null) {
                    return;
                }

                final String finalNewPath = newPath;

                Platform.runLater(() -> {
                    String pathBeforeUpdate = null;

                    switch (sectionType) {
                        case GLOBAL:
                            pathBeforeUpdate = globalCurrentPath;
                            globalCurrentPath = finalNewPath;
                            break;
                        case YOUR_DIRECTORY:
                            pathBeforeUpdate = yourDirectoryCurrentPath;
                            yourDirectoryCurrentPath = finalNewPath;
                            break;
                        case SHARED_BY_USER:
                            pathBeforeUpdate = sharedCurrentPath;
                            sharedCurrentPath = finalNewPath;
                            break;
                    }

                    String refreshGlobalPath = sectionType == FolderType.GLOBAL ? finalNewPath : null;
                    String refreshYourDirectoryPath = sectionType == FolderType.YOUR_DIRECTORY ? finalNewPath : null;
                    String refreshSharedPath = sectionType == FolderType.SHARED_BY_USER ? finalNewPath : null;
                    refresh(refreshGlobalPath, refreshYourDirectoryPath, refreshSharedPath);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    String reason = ftpService.getLastErrorMessage();
                    if (reason == null || reason.isBlank()) {
                        reason = "Failed to navigate up: " + e.getMessage();
                    }
                    showAlert("Error", reason);
                });
            } finally {
            }
        }).start();
    }

    private void handleCreateDirectory(FolderType folderType) {

        if (!ftpService.isConnected()) {
            showAlert("Error", "Not connected to FTP server");
            return;
        }

        if (folderType == FolderType.SHARED_BY_USER && !currentSharedFolderWrite) {
            showAlert("Error", "You don't have write permission to create folders in this shared folder");
            return;
        }
        if (folderType == FolderType.GLOBAL && !currentGlobalFolderWrite) {
            showAlert("Error", "You don't have write permission to create folders in Global directory");
            return;
        }


        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Create Directory");
        dialog.setHeaderText(null);
        dialog.setContentText("Enter directory name:");
        dialog.getDialogPane().setHeader(null);

        Window ownerWindow = sharedPathLabel.getScene().getWindow();
        DialogStyler.applyStyles(dialog, ownerWindow);

        dialog.showAndWait().ifPresent(dirName -> {

            if (dirName == null || dirName.trim().isEmpty()) {
                showAlert("Error", "Directory name cannot be empty");
                return;
            }

            String trimmedDirName = dirName.trim();

            String currentPathForSection = null;
            switch (folderType) {
                case GLOBAL:
                    currentPathForSection = globalCurrentPath;
                    break;
                case YOUR_DIRECTORY:
                    String labelText = yourDirectoryPathLabel.getText();
                    if (labelText.startsWith("Your Directory path: ")) {
                        currentPathForSection = labelText.substring("Your Directory path: ".length());
                    } else {
                        currentPathForSection = yourDirectoryCurrentPath;
                    }
                    break;
                case SHARED_BY_USER:
                    currentPathForSection = sharedCurrentPath;
                    break;
            }

            final String finalCurrentPath = currentPathForSection;

            new Thread(() -> {
                try {
                    String currentPath = finalCurrentPath;
                    String fullNewDirPath = NavigationService.joinPath(currentPath, trimmedDirName);


                    if (ftpService.createDirectory(fullNewDirPath)) {

                        final String refreshGlobalPath = folderType == FolderType.GLOBAL ? finalCurrentPath : null;
                        final String refreshYourDirectoryPath = folderType == FolderType.YOUR_DIRECTORY ? finalCurrentPath : null;
                        final String refreshSharedPath = folderType == FolderType.SHARED_BY_USER ? finalCurrentPath : null;
                        Platform.runLater(() -> {
                            refresh(refreshGlobalPath, refreshYourDirectoryPath, refreshSharedPath);
                        });
                    } else {
                        Platform.runLater(() -> showAlert("Error", "Failed to create directory: " + trimmedDirName));
                    }

                } catch (Exception e) {
                    Platform.runLater(() -> {
                        showAlert("Error", "Failed to create directory: " + e.getMessage());
                    });
                } finally {
                }
            }).start();
        });
    }

    private void handleUploadFile(FolderType folderType) {

        if (!ftpService.isConnected()) {
            showAlert("Error", "Not connected to FTP server");
            return;
        }

        if (folderType == FolderType.SHARED_BY_USER && !currentSharedFolderWrite) {
            showAlert("Error", "You don't have write permission to upload files to this shared folder");
            return;
        }

        if (folderType == FolderType.GLOBAL && !currentGlobalFolderWrite) {
            showAlert("Error", "You don't have write permission to upload files to Global directory");
            return;
        }


        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select File to Upload");
        Window window = sharedPathLabel.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(window);

        if (selectedFile == null) {
            return;
        }


        String currentPathForSection = null;
        switch (folderType) {
            case GLOBAL:
                currentPathForSection = globalCurrentPath;
                break;
            case YOUR_DIRECTORY:
                String labelText = yourDirectoryPathLabel.getText();
                if (labelText.startsWith("Your Directory path: ")) {
                    currentPathForSection = labelText.substring("Your Directory path: ".length());
                } else {
                    currentPathForSection = yourDirectoryCurrentPath;
                }
                break;
            case SHARED_BY_USER:
                currentPathForSection = sharedCurrentPath;
                break;
        }

        final String finalCurrentPathForSection = currentPathForSection;

        String remotePath = finalCurrentPathForSection + "/" + selectedFile.getName();
        final String finalRemotePath = remotePath;
        final String fileName = selectedFile.getName();
        final long fileSize = selectedFile.length();

        UploadProgressDialog progressDialog = new UploadProgressDialog(fileName);

        Window ownerWindow = sharedPathLabel.getScene().getWindow();
        progressDialog.initOwner(ownerWindow);

        final boolean[] cancelled = {false};
        progressDialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.CANCEL || buttonType.getButtonData() == ButtonBar.ButtonData.CANCEL_CLOSE) {
                cancelled[0] = true;
            }
            return null;
        });

        Platform.runLater(() -> {
            progressDialog.show();
        });

        new Thread(() -> {
            try {
                String savedPath = null;

                try {
                    savedPath = ftpService.getCurrentDirectory();
                } catch (Exception e) {
                }

                String username = currentUsername;
                Long rateLimit = null;
                if (username != null) {
                    try {
                        rateLimit = apiClient.getUserRateLimit(username);
                    } catch (Exception e) {
                    }
                }
                if (rateLimit == null || rateLimit <= 0) {
                    try {
                        rateLimit = apiClient.getGlobalUploadLimit();
                    } catch (Exception e) {
                    }
                }
                final Long finalRateLimit = rateLimit;

                String uploadPath = finalRemotePath;

                if (finalRemotePath.startsWith("/") && savedPath != null) {
                    int lastSlash = finalRemotePath.lastIndexOf('/');
                    if (lastSlash >= 0 && lastSlash < finalRemotePath.length() - 1) {
                        String fileToUpload = finalRemotePath.substring(lastSlash + 1);
                        String dirPath = finalRemotePath.substring(0, lastSlash);
                        if (savedPath.equals(dirPath)) {
                            uploadPath = fileToUpload;
                        } else {
                            if (ftpService.changeDirectory(dirPath)) {
                                uploadPath = fileToUpload;
                            }
                        }
                    }
                }

                FtpClientService.UploadProgressCallback progressCallback = (bytesTransferred, totalBytes, speedBytesPerSecond) -> {
                    if (cancelled[0]) {
                        return false;
                    }

                    double progress = totalBytes > 0 ? (double) bytesTransferred / totalBytes : 0.0;

                    String speedStr;
                    if (finalRateLimit != null && finalRateLimit > 0) {
                        speedStr = formatBytes(speedBytesPerSecond) + "/s (limit: " + formatBytes(finalRateLimit) + "/s)";
                    } else if (speedBytesPerSecond > 0) {
                        speedStr = formatBytes(speedBytesPerSecond) + "/s";
                    } else if (bytesTransferred > 0) {
                        speedStr = "calculating...";
                    } else {
                        speedStr = "calculating...";
                    }

                    progressDialog.updateProgress(progress, speedStr);
                    return true;
                };

                boolean success = ftpService.uploadFile(selectedFile, uploadPath, progressCallback, finalRateLimit);

                if (cancelled[0]) {
                    Platform.runLater(() -> {
                        progressDialog.close();
                        showAlert("Cancelled", "File upload was cancelled");
                    });
                    return;
                }

                if (success) {

                    progressDialog.setCompleted();

                    final String refreshGlobalPath = folderType == FolderType.GLOBAL ? finalCurrentPathForSection : null;
                    final String refreshYourDirectoryPath = folderType == FolderType.YOUR_DIRECTORY ? finalCurrentPathForSection : null;
                    final String refreshSharedPath = folderType == FolderType.SHARED_BY_USER ? finalCurrentPathForSection : null;
                    Platform.runLater(() -> refresh(refreshGlobalPath, refreshYourDirectoryPath, refreshSharedPath));
                } else {
                    Platform.runLater(() -> {
                        progressDialog.close();
                        String reason = ftpService.getLastErrorMessage();
                        if (reason == null || reason.isBlank()) {
                            reason = "Failed to upload file: " + fileName;
                        }
                        showAlert("Error", reason);
                    });
                }

                if (savedPath != null) {
                    try {
                        ftpService.changeDirectory(savedPath);
                    } catch (Exception e) {
                    }
                }

            } catch (Exception e) {
                Platform.runLater(() -> {
                    progressDialog.close();
                    String reason = ftpService.getLastErrorMessage();
                    if (reason == null || reason.isBlank()) {
                        reason = "Failed to upload file: " + e.getMessage();
                    }
                    showAlert("Error", reason);
                });
            } finally {
            }
        }).start();
    }

    private void handleDownloadFile(FolderType folderType, FtpFileInfo fileInfo) {

        if (!ftpService.isConnected()) {
            showAlert("Error", "Not connected to FTP server");
            return;
        }

        if (folderType == FolderType.SHARED_BY_USER && !currentSharedFolderExecute) {
            showAlert("Error", "You don't have execute permission to download files from this folder");
            return;
        }

        if (folderType == FolderType.GLOBAL && !currentGlobalFolderExecute) {
            showAlert("Error", "You don't have execute permission to download files from Global directory");
            return;
        }


        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save File As");
        fileChooser.setInitialFileName(fileInfo.getName());

        String fileName = fileInfo.getName();
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            String extension = fileName.substring(lastDot + 1).toLowerCase();
            String extensionUpper = extension.toUpperCase();
            fileChooser.getExtensionFilters().add(
                new ExtensionFilter(extensionUpper + " Files (*." + extension + ")", "*." + extension)
            );
            fileChooser.setSelectedExtensionFilter(fileChooser.getExtensionFilters().get(0));
        }
        fileChooser.getExtensionFilters().add(new ExtensionFilter("All Files (*.*)", "*.*"));

        Window window = sharedPathLabel.getScene().getWindow();
        File targetFile = fileChooser.showSaveDialog(window);

        if (targetFile == null) {
            return;
        }

        String remotePath = null;
        if (fileInfo.getFullPath() != null && !fileInfo.getFullPath().isEmpty()) {
            remotePath = fileInfo.getFullPath();
        } else {
            String currentPathForSection = null;
            switch (folderType) {
                case GLOBAL:
                    currentPathForSection = globalCurrentPath;
                    break;
                case YOUR_DIRECTORY:
                    String labelText = yourDirectoryPathLabel.getText();
                    if (labelText.startsWith("Your Directory path: ")) {
                        currentPathForSection = labelText.substring("Your Directory path: ".length());
                    } else {
                        currentPathForSection = yourDirectoryCurrentPath;
                    }
                    break;
                case SHARED_BY_USER:
                    currentPathForSection = sharedCurrentPath;
                    break;
            }
            remotePath = currentPathForSection + "/" + fileInfo.getName();
        }

        final String finalRemotePath = remotePath;

        long fileSize = fileInfo.getSize();

        DownloadProgressDialog progressDialog = new DownloadProgressDialog(fileInfo.getName());

        Window ownerWindow = sharedPathLabel.getScene().getWindow();
        progressDialog.initOwner(ownerWindow);

        final boolean[] cancelled = {false};
        progressDialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.CANCEL || buttonType.getButtonData() == ButtonBar.ButtonData.CANCEL_CLOSE) {
                cancelled[0] = true;
            }
            return null;
        });

        Platform.runLater(() -> {
            progressDialog.show();
        });

        new Thread(() -> {
            try {
                String savedPath = null;

                try {
                    savedPath = ftpService.getCurrentDirectory();
                } catch (Exception e) {
                }

                String downloadPath = finalRemotePath;

                if (finalRemotePath.startsWith("/") && savedPath != null) {
                    int lastSlash = finalRemotePath.lastIndexOf('/');
                    if (lastSlash >= 0 && lastSlash < finalRemotePath.length() - 1) {
                        String fileToDownload = finalRemotePath.substring(lastSlash + 1);
                        String dirPath = finalRemotePath.substring(0, lastSlash);
                        if (savedPath.equals(dirPath)) {
                            downloadPath = fileToDownload;
                        } else {
                            if (ftpService.changeDirectory(dirPath)) {
                                downloadPath = fileToDownload;
                            }
                        }
                    }
                }

                FtpClientService.DownloadProgressCallback progressCallback = (bytesTransferred, totalBytes, speedBytesPerSecond) -> {
                    if (cancelled[0]) {
                        return false;
                    }

                    double progress = totalBytes > 0 ? (double) bytesTransferred / totalBytes : 0.0;

                    String speedStr;
                    if (speedBytesPerSecond > 0) {
                        speedStr = formatBytes(speedBytesPerSecond) + "/s";
                    } else if (bytesTransferred > 0) {
                        speedStr = "calculating...";
                    } else {
                        speedStr = "calculating...";
                    }

                    progressDialog.updateProgress(progress, speedStr);
                    return true;
                };

                boolean success = ftpService.downloadFile(downloadPath, targetFile, progressCallback, fileSize);

                if (cancelled[0]) {
                    Platform.runLater(() -> {
                        progressDialog.close();
                        try {
                            if (targetFile.exists()) {
                                targetFile.delete();
                            }
                        } catch (Exception e) {
                        }
                        showAlert("Cancelled", "File download was cancelled");
                    });
                    return;
                }

                if (success) {

                    progressDialog.setCompleted();
                } else {
                    Platform.runLater(() -> {
                        progressDialog.close();
                        String reason = ftpService.getLastErrorMessage();
                        if (reason == null || reason.isBlank()) {
                            reason = "Failed to download file: " + fileInfo.getName();
                        }
                        showAlert("Error", reason);
                    });
                }

                if (savedPath != null) {
                    try {
                        ftpService.changeDirectory(savedPath);
                    } catch (Exception e) {
                    }
                }

            } catch (Exception e) {
                Platform.runLater(() -> {
                    String reason = ftpService.getLastErrorMessage();
                    if (reason == null || reason.isBlank()) {
                        reason = "Failed to download file: " + e.getMessage();
                    }
                    showAlert("Error", reason);
                });
            } finally {
            }
        }).start();
    }

    private void handleDelete(FolderType folderType, FtpFileInfo fileInfo) {

        if (!ftpService.isConnected()) {
            showAlert("Error", "Not connected to FTP server");
            return;
        }

        if (folderType == FolderType.SHARED_BY_USER && !currentSharedFolderExecute) {
            showAlert("Error", "You don't have execute permission to delete files from this folder");
            return;
        }

        if (folderType == FolderType.GLOBAL && !currentGlobalFolderExecute) {
            showAlert("Error", "You don't have execute permission to delete files from Global directory");
            return;
        }

        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("");
        confirmDialog.setHeaderText(null);
        confirmDialog.setContentText("Are you sure you want to delete this " + (fileInfo.isDirectory() ? "directory" : "file") + "?\n\nName: " + fileInfo.getName() + "\n\nThis action cannot be undone.");
        confirmDialog.getDialogPane().setHeader(null);

        Window ownerWindow = sharedPathLabel.getScene().getWindow();
        DialogStyler.applyStyles(confirmDialog, ownerWindow);

        confirmDialog.showAndWait().ifPresent(result -> {
            if (result != ButtonType.OK) {
                return;
            }

            String currentPathForSection = null;
            switch (folderType) {
                case GLOBAL:
                    currentPathForSection = globalCurrentPath;
                    break;
                case YOUR_DIRECTORY:
                    String labelText = yourDirectoryPathLabel.getText();
                    if (labelText.startsWith("Your Directory path: ")) {
                        currentPathForSection = labelText.substring("Your Directory path: ".length());
                    } else {
                        currentPathForSection = yourDirectoryCurrentPath;
                    }
                    break;
                case SHARED_BY_USER:
                    currentPathForSection = sharedCurrentPath;
                    break;
            }

            final String finalCurrentPath = currentPathForSection;
            final String itemName = fileInfo.getName();
            final boolean isDirectory = fileInfo.isDirectory();
            final FtpFileInfo finalFileInfo = fileInfo;


            new Thread(() -> {
                try {
                    String deletePath;
                    if (finalFileInfo.getFullPath() != null && !finalFileInfo.getFullPath().isEmpty()) {
                        deletePath = finalFileInfo.getFullPath();
                    } else {
                        deletePath = NavigationService.joinPath(finalCurrentPath, itemName);
                    }


                    boolean deleteResult = isDirectory
                            ? ftpService.deleteDirectory(deletePath)
                            : ftpService.deleteFile(deletePath);

                    if (deleteResult) {

                        if (isDirectory && folderType == FolderType.YOUR_DIRECTORY) {
                            try {
                                apiClient.deleteSharedFolder(deletePath);
                            } catch (Exception e) {
                            }
                        }

                        final String refreshGlobalPath = folderType == FolderType.GLOBAL ? finalCurrentPath : null;
                        final String refreshYourDirectoryPath = folderType == FolderType.YOUR_DIRECTORY ? finalCurrentPath : null;
                        final String refreshSharedPath = folderType == FolderType.SHARED_BY_USER ? finalCurrentPath : null;
                        Platform.runLater(() -> {
                            refresh(refreshGlobalPath, refreshYourDirectoryPath, refreshSharedPath);
                        });
                    } else {
                        Platform.runLater(() -> {
                            String reason = ftpService.getLastErrorMessage();
                            if (reason == null || reason.isBlank()) {
                                reason = "Failed to delete " + (isDirectory ? "directory" : "file") + ": " + itemName;
                            }
                            showAlert("Error", reason);
                        });
                    }

                } catch (Exception e) {
                    Platform.runLater(() -> {
                        String reason = ftpService.getLastErrorMessage();
                        if (reason == null || reason.isBlank()) {
                            reason = "Failed to delete " + (isDirectory ? "directory" : "file") + ": " + e.getMessage();
                        }
                        showAlert("Error", reason);
                    });
                } finally {
                }
            }).start();
        });
    }

    private void handleShare() {
        if (selectedFolder == null) {
            return;
        }

        String folderPath = selectedFolder.getFullPath() != null ? selectedFolder.getFullPath() : "/" + selectedFolder.getName();
        String folderName = selectedFolder.getName();
        String currentUsername = connectionPanel.getCurrentUsername();

        if (currentUsername == null || currentUsername.isEmpty()) {
            showAlert("Error", "Username not available");
            return;
        }

        ShareDialog dialog = new ShareDialog(folderPath, folderName);
        Window ownerWindow = sharedPathLabel.getScene().getWindow();
        dialog.applyStyles(ownerWindow);
        dialog.showAndWait().ifPresent(result -> {
            new Thread(() -> {
                try {
                    apiClient.shareFolder(
                        currentUsername,
                        result.getUsername(),
                        folderName,
                        folderPath,
                        result.isWrite(),
                        result.isExecute()
                    );

                    Platform.runLater(() -> {
                        Alert success = new Alert(Alert.AlertType.INFORMATION);
                        success.setTitle("");
                        success.setHeaderText(null);
                        success.setContentText("Folder shared successfully with " + result.getUsername());
                        success.getDialogPane().setHeader(null);

                        Window successOwnerWindow = sharedPathLabel.getScene().getWindow();
                        DialogStyler.applyStyles(success, successOwnerWindow);

                        success.showAndWait();
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        showAlert("Error", "Failed to share folder: " + e.getMessage());
                    });
                }
            }).start();
        });
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.getDialogPane().setHeader(null);

        Window ownerWindow = sharedPathLabel.getScene().getWindow();
        DialogStyler.applyStyles(alert, ownerWindow);

        alert.showAndWait();
    }

    private String formatBytes(double bytes) {
        if (bytes < 1024) {
            return String.format("%.0f B", bytes);
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024 * 1024));
        } else {
            return String.format("%.2f GB", bytes / (1024 * 1024 * 1024));
        }
    }
}

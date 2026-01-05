package org.example.ftp.fileman.ui;

import org.example.ftp.fileman.ftp.FolderType;
import org.example.ftp.fileman.ftp.FtpFileInfo;
import org.example.ftp.fileman.ui.viewmodel.FileTableViewModel;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
 
public class FileTableSection extends VBox {
    
    private final FileTableViewModel viewModel;
    private final TableView<FtpFileInfo> table;
    private final Label pathLabel;
    private final Button upButton;
    private final Button createDirButton;
    
  
    @FunctionalInterface
    public interface OnDirectoryDoubleClick {
        void onDoubleClick(FtpFileInfo file);
    }
    
    @FunctionalInterface
    public interface OnCreateDirectory {
        void onCreate();
    }
    
    @FunctionalInterface
    public interface OnNavigateUp {
        void onNavigateUp();
    }
    
    @FunctionalInterface
    public interface OnFileAction {
        void onAction(FtpFileInfo file, String action);
    }
    
    private OnDirectoryDoubleClick onDirectoryDoubleClick;
    private OnCreateDirectory onCreateDirectory;
    private OnNavigateUp onNavigateUp;
    
    @SuppressWarnings("unused")
    private OnFileAction onFileAction;
    
    public FileTableSection(FileTableViewModel viewModel) {
        this.viewModel = viewModel;
        
        
        this.table = createFileTable();
        this.pathLabel = new Label();
        this.pathLabel.getStyleClass().add("path-label");
        
        this.upButton = new Button("⬆");
        this.upButton.getStyleClass().add("up-button");
        this.upButton.setTooltip(new Tooltip("Go up one level"));
        this.upButton.setOnAction(e -> {
            if (onNavigateUp != null) {
                onNavigateUp.onNavigateUp();
            }
        });
        
        this.createDirButton = new Button("➕");
        this.createDirButton.getStyleClass().add("create-dir-button");
        this.createDirButton.setTooltip(new Tooltip("Create new directory"));
        this.createDirButton.setOnAction(e -> {
            if (onCreateDirectory != null) {
                onCreateDirectory.onCreate();
            }
        });
        
        
        table.setItems(viewModel.getFiles());
        pathLabel.textProperty().bind(viewModel.currentPathProperty());
        
        
        if (viewModel.getFolderType() == FolderType.SHARED_BY_USER) {
            createDirButton.setVisible(false);
            createDirButton.setManaged(false);
        }
        
        layoutComponents();
    }
    
    private TableView<FtpFileInfo> createFileTable() {
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
        
        
        table.setRowFactory(tv -> {
            TableRow<FtpFileInfo> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    FtpFileInfo file = row.getItem();
                    if (file.isDirectory() && onDirectoryDoubleClick != null) {
                        onDirectoryDoubleClick.onDoubleClick(file);
                    }
                }
            });
            return row;
        });
        
        return table;
    }
    
    private void layoutComponents() {
        
        Label titleLabel = new Label(getTitleForFolderType(viewModel.getFolderType()));
        titleLabel.getStyleClass().add("section-title");
        
        
        javafx.scene.layout.HBox pathPanel = new javafx.scene.layout.HBox(10);
        pathPanel.getStyleClass().add("section-path-panel");
        pathPanel.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        pathPanel.getChildren().addAll(upButton, createDirButton, pathLabel);
        
        
        ScrollPane tableScrollPane = new ScrollPane(table);
        tableScrollPane.setFitToWidth(true);
        tableScrollPane.setFitToHeight(true);
        tableScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        tableScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        tableScrollPane.getStyleClass().add("table-scroll-pane");
        
        
        VBox sectionBox = new VBox(8);
        sectionBox.getStyleClass().add("section-box");
        sectionBox.getChildren().addAll(titleLabel, pathPanel, tableScrollPane);
        
        getChildren().add(sectionBox);
    }
    
    private String getTitleForFolderType(FolderType folderType) {
        switch (folderType) {
            case GLOBAL:
                return "\uD83C\uDF10 Global";
            case YOUR_DIRECTORY:
                return "\uD83D\uDCC1 Your Directory";
            case SHARED_BY_USER:
                return "\uD83D\uDC65 Shared by User";
            default:
                return "";
        }
    }
    
    
    public void setOnDirectoryDoubleClick(OnDirectoryDoubleClick callback) {
        this.onDirectoryDoubleClick = callback;
    }
    
    public void setOnCreateDirectory(OnCreateDirectory callback) {
        this.onCreateDirectory = callback;
    }
    
    public void setOnNavigateUp(OnNavigateUp callback) {
        this.onNavigateUp = callback;
    }
    
    public void setOnFileAction(OnFileAction callback) {
        this.onFileAction = callback;
    }
    
    
    public TableView<FtpFileInfo> getTable() {
        return table;
    }
    
    public FileTableViewModel getViewModel() {
        return viewModel;
    }
}


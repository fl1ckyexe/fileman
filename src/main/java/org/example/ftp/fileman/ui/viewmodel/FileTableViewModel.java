package org.example.ftp.fileman.ui.viewmodel;

import org.example.ftp.fileman.ftp.FolderType;
import org.example.ftp.fileman.ftp.FtpFileInfo;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
 
public class FileTableViewModel {
    
    private final FolderType folderType;
    private final StringProperty currentPath = new SimpleStringProperty("/");
    private final ObservableList<FtpFileInfo> files = FXCollections.observableArrayList();
    
    private boolean readPermission = false;
    private boolean writePermission = false;
    private boolean executePermission = false;
    
    public FileTableViewModel(FolderType folderType, String initialPath) {
        this.folderType = folderType;
        this.currentPath.set(initialPath);
    }
    
    public FolderType getFolderType() {
        return folderType;
    }
    
    public StringProperty currentPathProperty() {
        return currentPath;
    }
    
    public String getCurrentPath() {
        return currentPath.get();
    }
    
    public void setCurrentPath(String path) {
        this.currentPath.set(path);
    }
    
    public ObservableList<FtpFileInfo> getFiles() {
        return files;
    }
    
    public void setFiles(java.util.List<FtpFileInfo> newFiles) {
        files.clear();
        files.addAll(newFiles);
    }
    
    public void clearFiles() {
        files.clear();
    }
    
    public boolean hasReadPermission() {
        return readPermission;
    }
    
    public void setReadPermission(boolean readPermission) {
        this.readPermission = readPermission;
    }
    
    public boolean hasWritePermission() {
        return writePermission;
    }
    
    public void setWritePermission(boolean writePermission) {
        this.writePermission = writePermission;
    }
    
    public boolean hasExecutePermission() {
        return executePermission;
    }
    
    public void setExecutePermission(boolean executePermission) {
        this.executePermission = executePermission;
    }
    
    public void setPermissions(boolean read, boolean write, boolean execute) {
        this.readPermission = read;
        this.writePermission = write;
        this.executePermission = execute;
    }
    
    public boolean canCreate() {
        if (folderType == FolderType.YOUR_DIRECTORY) {
            return true;
        }
        return writePermission;
    }
    
    public boolean canDelete() {
        if (folderType == FolderType.YOUR_DIRECTORY) {
            return true;
        }
        return executePermission;
    }
    
    public boolean canUpload() {
        return canCreate();
    }
    
    public boolean canDownload() {
        if (folderType == FolderType.YOUR_DIRECTORY) {
            return true;
        }
        return readPermission;
    }
}


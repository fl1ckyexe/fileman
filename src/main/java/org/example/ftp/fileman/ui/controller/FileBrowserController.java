package org.example.ftp.fileman.ui.controller;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.example.ftp.fileman.ftp.FolderType;
import org.example.ftp.fileman.ftp.FtpFileInfo;
import org.example.ftp.fileman.service.IFtpConnectionService;
import org.example.ftp.fileman.service.IFtpDirectoryService;
import org.example.ftp.fileman.service.IFtpFileService;
import org.example.ftp.fileman.service.IPermissionService;
import org.example.ftp.fileman.service.NavigationService;
import org.example.ftp.fileman.ui.viewmodel.FileTableViewModel;

import javafx.application.Platform;
 
public class FileBrowserController {
    
    private final IFtpConnectionService connectionService;
    private final IFtpDirectoryService directoryService;
    private final IFtpFileService fileService;
    private final IPermissionService permissionService;
    
    private final FileTableViewModel globalViewModel;
    private final FileTableViewModel yourDirectoryViewModel;
    private final FileTableViewModel sharedViewModel;
    
    private String currentUsername;
    
    public FileBrowserController(IFtpConnectionService connectionService,
                                IFtpDirectoryService directoryService,
                                IFtpFileService fileService,
                                IPermissionService permissionService,
                                String initialUsername) {
        this.connectionService = connectionService;
        this.directoryService = directoryService;
        this.fileService = fileService;
        this.permissionService = permissionService;
        this.currentUsername = initialUsername;
        
        this.globalViewModel = new FileTableViewModel(
            FolderType.GLOBAL,
            NavigationService.getInitialPath(FolderType.GLOBAL, null)
        );
        
        this.yourDirectoryViewModel = new FileTableViewModel(
            FolderType.YOUR_DIRECTORY,
            NavigationService.getInitialPath(FolderType.YOUR_DIRECTORY, initialUsername)
        );
        
        this.sharedViewModel = new FileTableViewModel(
            FolderType.SHARED_BY_USER,
            NavigationService.getInitialPath(FolderType.SHARED_BY_USER, null)
        );
    }
    
    public FileTableViewModel getGlobalViewModel() {
        return globalViewModel;
    }
    
    public FileTableViewModel getYourDirectoryViewModel() {
        return yourDirectoryViewModel;
    }
    
    public FileTableViewModel getSharedViewModel() {
        return sharedViewModel;
    }
    
    public void setCurrentUsername(String username) {
        this.currentUsername = username;
        String newPath = NavigationService.getInitialPath(FolderType.YOUR_DIRECTORY, username);
        yourDirectoryViewModel.setCurrentPath(newPath);
    }
    
    
    public void refreshAll() {
        refreshSection(FolderType.GLOBAL);
        refreshSection(FolderType.YOUR_DIRECTORY);
        refreshSection(FolderType.SHARED_BY_USER);
    }
    
 
    public void refreshSection(FolderType folderType) {
        new Thread(() -> {
            try {
                FileTableViewModel viewModel = getViewModel(folderType);
                String currentPath = viewModel.getCurrentPath();
                
                List<FtpFileInfo> files = loadFilesForSection(folderType, currentPath);
                
                loadPermissions(folderType, currentPath);
                
                Platform.runLater(() -> {
                    viewModel.setFiles(files);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                });
            }
        }).start();
    }
    
   
    private List<FtpFileInfo> loadFilesForSection(FolderType folderType, String path) throws IOException {
        if (!connectionService.isConnected()) {
            return new ArrayList<>();
        }
        
        switch (folderType) {
            case GLOBAL:
                return loadGlobalFiles(path);
            case YOUR_DIRECTORY:
                return loadYourDirectoryFiles(path);
            case SHARED_BY_USER:
                return loadSharedFiles(path);
            default:
                return new ArrayList<>();
        }
    }
    
    private List<FtpFileInfo> loadGlobalFiles(String path) throws IOException {
        String savedPath = null;
        try {
            savedPath = connectionService.getCurrentDirectory();
        } catch (Exception e) {
        }
        
        try {
            if (directoryService.changeDirectory(path)) {
                List<FtpFileInfo> files = directoryService.listFiles("");
                for (FtpFileInfo file : files) {
                    file.setFolderType(FolderType.GLOBAL);
                    file.setFullPath(NavigationService.joinPath(path, file.getName()));
                }
                return files;
            }
        } finally {
            if (savedPath != null) {
                try {
                    directoryService.changeDirectory(savedPath);
                } catch (Exception e) {
                }
            }
        }
        return new ArrayList<>();
    }
    
    private List<FtpFileInfo> loadYourDirectoryFiles(String path) throws IOException {
        String savedPath = null;
        try {
            savedPath = connectionService.getCurrentDirectory();
        } catch (Exception e) {
        }
        
        try {
            if (directoryService.changeDirectory(path)) {
                List<FtpFileInfo> files = directoryService.listFiles("");
                for (FtpFileInfo file : files) {
                    file.setFolderType(FolderType.YOUR_DIRECTORY);
                    file.setFullPath(NavigationService.joinPath(path, file.getName()));
                }
                return files;
            }
        } finally {
            if (savedPath != null) {
                try {
                    directoryService.changeDirectory(savedPath);
                } catch (Exception e) {
                }
            }
        }
        return new ArrayList<>();
    }
    
    private List<FtpFileInfo> loadSharedFiles(String path) throws IOException {
        if (path.equals("/")) {
            return loadSharedFoldersList();
        } else {
            String savedPath = null;
            try {
                savedPath = connectionService.getCurrentDirectory();
            } catch (Exception e) {
            }
            
            try {
                if (directoryService.changeDirectory(path)) {
                    List<FtpFileInfo> files = directoryService.listFiles("");
                    for (FtpFileInfo file : files) {
                        file.setFolderType(FolderType.SHARED_BY_USER);
                        file.setFullPath(NavigationService.joinPath(path, file.getName()));
                    }
                    return files;
                }
            } finally {
                if (savedPath != null) {
                    try {
                        directoryService.changeDirectory(savedPath);
                    } catch (Exception e) {
                    }
                }
            }
        }
        return new ArrayList<>();
    }
    
    private List<FtpFileInfo> loadSharedFoldersList() {
        List<FtpFileInfo> result = new ArrayList<>();
        try {
            List<org.example.ftp.fileman.model.SharedFolder> sharedFolders = 
                permissionService.getSharedFolders(currentUsername);
            for (org.example.ftp.fileman.model.SharedFolder sharedFolder : sharedFolders) {
                FtpFileInfo folderInfo = new FtpFileInfo(
                    sharedFolder.getFolderName(),
                    true,
                    0,
                    null,
                    FolderType.SHARED_BY_USER,
                    sharedFolder.getFolderPath()
                );
                result.add(folderInfo);
            }
        } catch (Exception e) {
        }
        return result;
    }
    
 
    private void loadPermissions(FolderType folderType, String path) {
        try {
            switch (folderType) {
                case GLOBAL:
                    loadGlobalPermissions();
                    break;
                case SHARED_BY_USER:
                    if (!path.equals("/")) {
                        loadSharedPermissions(path);
                    }
                    break;
                case YOUR_DIRECTORY:
                    getViewModel(folderType).setPermissions(true, true, true);
                    break;
            }
        } catch (Exception e) {
        }
    }
    
    private void loadGlobalPermissions() {
        try {
            org.example.ftp.fileman.model.UserPermissions perms = 
                permissionService.getUserPermissions(currentUsername);
            globalViewModel.setPermissions(perms.isRead(), perms.isWrite(), perms.isExecute());
        } catch (Exception e) {
        }
    }
    
    private void loadSharedPermissions(String path) {
        try {
            List<org.example.ftp.fileman.model.SharedFolder> sharedFolders = 
                permissionService.getSharedFolders(currentUsername);
            
            String normalizedPath = NavigationService.normalizePath(path);
            org.example.ftp.fileman.model.SharedFolder bestMatch = null;
            int bestMatchLength = -1;
            
            for (org.example.ftp.fileman.model.SharedFolder sharedFolder : sharedFolders) {
                String folderPath = NavigationService.normalizePath(sharedFolder.getFolderPath());
                if (normalizedPath.equals(folderPath) || normalizedPath.startsWith(folderPath + "/")) {
                    if (folderPath.length() > bestMatchLength) {
                        bestMatch = sharedFolder;
                        bestMatchLength = folderPath.length();
                    }
                }
            }
            
            if (bestMatch != null) {
                sharedViewModel.setPermissions(
                    bestMatch.isRead(),
                    bestMatch.isWrite(),
                    bestMatch.isExecute()
                );
            }
        } catch (Exception e) {
        }
    }
    
    
    public void navigateToDirectory(FolderType folderType, FtpFileInfo file) {
        if (!file.isDirectory()) {
            return;
        }
        
        FileTableViewModel viewModel = getViewModel(folderType);
        String currentPath = viewModel.getCurrentPath();
        
        String newPath;
        if (folderType == FolderType.SHARED_BY_USER && file.getFullPath() != null) {
            newPath = file.getFullPath();
        } else {
            newPath = NavigationService.joinPath(currentPath, file.getName());
        }
        
        viewModel.setCurrentPath(newPath);
        refreshSection(folderType);
    }
    
    
    public void navigateUp(FolderType folderType) {
        FileTableViewModel viewModel = getViewModel(folderType);
        String currentPath = viewModel.getCurrentPath();
        String parentPath = NavigationService.getParentPath(currentPath);
        
        if (folderType == FolderType.SHARED_BY_USER && parentPath.equals("/")) {
            viewModel.setCurrentPath("/");
        } else {
            viewModel.setCurrentPath(parentPath);
        }
        
        refreshSection(folderType);
    }
    
 
    public void createDirectory(FolderType folderType, String directoryName) throws IOException {
        FileTableViewModel viewModel = getViewModel(folderType);
        String currentPath = viewModel.getCurrentPath();
        
        String savedPath = null;
        try {
            savedPath = connectionService.getCurrentDirectory();
        } catch (Exception e) {
        }
        
        try {
            if (directoryService.changeDirectory(currentPath)) {
                directoryService.createDirectory(directoryName);
            }
        } finally {
            if (savedPath != null) {
                try {
                    directoryService.changeDirectory(savedPath);
                } catch (Exception e) {
                }
            }
        }
        
        refreshSection(folderType);
    }
    
    
    public void uploadFile(FolderType folderType, File localFile, 
                          IFtpFileService.UploadProgressCallback progressCallback) throws IOException {
        FileTableViewModel viewModel = getViewModel(folderType);
        String currentPath = viewModel.getCurrentPath();
        String fileName = localFile.getName();
        
        String savedPath = null;
        try {
            savedPath = connectionService.getCurrentDirectory();
        } catch (Exception e) {
        }
        
        try {
            if (directoryService.changeDirectory(currentPath)) {
                fileService.uploadFile(fileName, localFile, progressCallback);
            }
        } finally {
            if (savedPath != null) {
                try {
                    directoryService.changeDirectory(savedPath);
                } catch (Exception e) {
                }
            }
        }
        
        refreshSection(folderType);
    }
 
    public void downloadFile(FolderType folderType, FtpFileInfo file, File targetFile,
                            IFtpFileService.DownloadProgressCallback progressCallback) throws IOException {
        String remotePath;
        if (file.getFullPath() != null) {
            remotePath = file.getFullPath();
        } else {
            FileTableViewModel viewModel = getViewModel(folderType);
            remotePath = NavigationService.joinPath(viewModel.getCurrentPath(), file.getName());
        }
        
        String savedPath = null;
        try {
            savedPath = connectionService.getCurrentDirectory();
        } catch (Exception e) {
        }
        
        try {
            fileService.downloadFile(remotePath, targetFile, progressCallback, file.getSize());
        } finally {
            if (savedPath != null) {
                try {
                    directoryService.changeDirectory(savedPath);
                } catch (Exception e) {
                }
            }
        }
    }
    
 
    public void delete(FolderType folderType, FtpFileInfo file) throws IOException {
        String path;
        if (file.getFullPath() != null) {
            path = file.getFullPath();
        } else {
            FileTableViewModel viewModel = getViewModel(folderType);
            path = NavigationService.joinPath(viewModel.getCurrentPath(), file.getName());
        }
        
        String savedPath = null;
        try {
            savedPath = connectionService.getCurrentDirectory();
        } catch (Exception e) {
        }
        
        try {
            if (file.isDirectory()) {
                directoryService.deleteDirectory(path);
            } else {
                fileService.deleteFile(path);
            }
        } finally {
            if (savedPath != null) {
                try {
                    directoryService.changeDirectory(savedPath);
                } catch (Exception e) {
                }
            }
        }
        
        refreshSection(folderType);
    }
    
    private FileTableViewModel getViewModel(FolderType folderType) {
        switch (folderType) {
            case GLOBAL:
                return globalViewModel;
            case YOUR_DIRECTORY:
                return yourDirectoryViewModel;
            case SHARED_BY_USER:
                return sharedViewModel;
            default:
                throw new IllegalArgumentException("Unknown folder type: " + folderType);
        }
    }
}


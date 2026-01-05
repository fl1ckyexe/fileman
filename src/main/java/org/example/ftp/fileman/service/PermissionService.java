package org.example.ftp.fileman.service;

import org.example.ftp.fileman.api.FolderPermissionsApiClient;
import org.example.ftp.fileman.model.FolderPermission;
import org.example.ftp.fileman.model.SharedFolder;
import org.example.ftp.fileman.model.UserPermissions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
 
public class PermissionService implements IPermissionService {
    
    private final FolderPermissionsApiClient apiClient;
    
    public PermissionService() {
        this.apiClient = new FolderPermissionsApiClient();
    }
    
    @Override
    public List<FolderPermission> getFolderPermissions(String username) throws IOException, InterruptedException {
        List<FolderPermissionsApiClient.FolderPermission> apiPermissions = apiClient.getFolderPermissions(username);
        List<FolderPermission> result = new ArrayList<>();
        for (FolderPermissionsApiClient.FolderPermission apiPerm : apiPermissions) {
            result.add(new FolderPermission(
                apiPerm.getFolder(),
                apiPerm.isRead(),
                apiPerm.isWrite(),
                apiPerm.isExecute()
            ));
        }
        return result;
    }
    
    @Override
    public void saveFolderPermission(String user, String folder, boolean read, 
                                   boolean write, boolean execute) throws IOException, InterruptedException {
        apiClient.saveFolderPermission(user, folder, read, write, execute);
    }
    
    @Override
    public List<SharedFolder> getSharedFolders(String username) throws IOException, InterruptedException {
        List<FolderPermissionsApiClient.SharedFolder> apiFolders = apiClient.getSharedFolders(username);
        List<SharedFolder> result = new ArrayList<>();
        for (FolderPermissionsApiClient.SharedFolder apiFolder : apiFolders) {
            result.add(new SharedFolder(
                apiFolder.getFolderName(),
                apiFolder.getFolderPath(),
                apiFolder.getOwnerUsername(),
                apiFolder.isRead(),
                apiFolder.isWrite(),
                apiFolder.isExecute()
            ));
        }
        return result;
    }
    
    @Override
    public void shareFolder(String owner, String userToShare, String folderName, 
                          String folderPath, boolean write, boolean execute) 
                          throws IOException, InterruptedException {
        apiClient.shareFolder(owner, userToShare, folderName, folderPath, write, execute);
    }
    
    @Override
    public void deleteSharedFolder(String folderPath) throws IOException, InterruptedException {
        apiClient.deleteSharedFolder(folderPath);
    }
    
    @Override
    public UserPermissions getUserPermissions(String username) throws IOException, InterruptedException {
        FolderPermissionsApiClient.UserPermissions apiPerms = apiClient.getUserPermissions(username);
        return new UserPermissions(
            apiPerms.isRead(),
            apiPerms.isWrite(),
            apiPerms.isExecute()
        );
    }
}


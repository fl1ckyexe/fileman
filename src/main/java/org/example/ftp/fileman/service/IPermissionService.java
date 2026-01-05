package org.example.ftp.fileman.service;

import java.io.IOException;
import java.util.List;

import org.example.ftp.fileman.model.FolderPermission;
import org.example.ftp.fileman.model.SharedFolder;
import org.example.ftp.fileman.model.UserPermissions;


public interface IPermissionService {

    List<FolderPermission> getFolderPermissions(String username) throws IOException, InterruptedException;
    
  
    void saveFolderPermission(String user, String folder, boolean read, 
                             boolean write, boolean execute) throws IOException, InterruptedException;
    

    List<SharedFolder> getSharedFolders(String username) throws IOException, InterruptedException;
    

    void shareFolder(String owner, String userToShare, String folderName, 
                    String folderPath, boolean write, boolean execute) 
                    throws IOException, InterruptedException;
    
     
    void deleteSharedFolder(String folderPath) throws IOException, InterruptedException;
    
  
    UserPermissions getUserPermissions(String username) throws IOException, InterruptedException;
}


package org.example.ftp.fileman.model;


public class SharedFolder {
    private final String folderName;
    private final String folderPath;
    private final String ownerUsername;
    private final boolean read;
    private final boolean write;
    private final boolean execute;
    
    public SharedFolder(String folderName, String folderPath, String ownerUsername, 
                       boolean read, boolean write, boolean execute) {
        this.folderName = folderName;
        this.folderPath = folderPath;
        this.ownerUsername = ownerUsername;
        this.read = read;
        this.write = write;
        this.execute = execute;
    }
    
    public String getFolderName() { 
        return folderName; 
    }
    
    public String getFolderPath() { 
        return folderPath; 
    }
    
    public String getOwnerUsername() { 
        return ownerUsername; 
    }
    
    public boolean isRead() { 
        return read; 
    }
    
    public boolean isWrite() { 
        return write; 
    }
    
    public boolean isExecute() { 
        return execute; 
    }
}


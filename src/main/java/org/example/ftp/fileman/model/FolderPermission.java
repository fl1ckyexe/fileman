package org.example.ftp.fileman.model;


public class FolderPermission {
    private final String folder;
    private final boolean read;
    private final boolean write;
    private final boolean execute;
    
    public FolderPermission(String folder, boolean read, boolean write, boolean execute) {
        this.folder = folder;
        this.read = read;
        this.write = write;
        this.execute = execute;
    }
    
    public String getFolder() { 
        return folder; 
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


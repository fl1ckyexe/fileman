package org.example.ftp.fileman.model;


public class UserPermissions {
    private final boolean read;
    private final boolean write;
    private final boolean execute;
    
    public UserPermissions(boolean read, boolean write, boolean execute) {
        this.read = read;
        this.write = write;
        this.execute = execute;
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


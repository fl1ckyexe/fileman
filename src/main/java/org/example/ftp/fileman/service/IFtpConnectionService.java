package org.example.ftp.fileman.service;


public interface IFtpConnectionService {
   
    boolean connect(String host, int port, String username, String password);
    
    
    void disconnect();
    
    
    boolean isConnected();
    
   
    String getCurrentDirectory() throws Exception;
}


package org.example.ftp.fileman.service;

import org.example.ftp.fileman.ftp.FtpClientService;


public class FtpConnectionServiceAdapter implements IFtpConnectionService {
    
    private final FtpClientService ftpClientService;
    
    public FtpConnectionServiceAdapter(FtpClientService ftpClientService) {
        this.ftpClientService = ftpClientService;
    }
    
    @Override
    public boolean connect(String host, int port, String username, String password) {
        return ftpClientService.connect(host, port, username, password);
    }
    
    @Override
    public void disconnect() {
        ftpClientService.disconnect();
    }
    
    @Override
    public boolean isConnected() {
        return ftpClientService.isConnected();
    }
    
    @Override
    public String getCurrentDirectory() throws Exception {
        return ftpClientService.getCurrentDirectory();
    }
}


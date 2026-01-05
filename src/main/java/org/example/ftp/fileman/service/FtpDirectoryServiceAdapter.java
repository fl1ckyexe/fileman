package org.example.ftp.fileman.service;

import org.example.ftp.fileman.ftp.FtpClientService;
import org.example.ftp.fileman.ftp.FtpFileInfo;

import java.io.IOException;
import java.util.List;


public class FtpDirectoryServiceAdapter implements IFtpDirectoryService {
    
    private final FtpClientService ftpClientService;
    
    public FtpDirectoryServiceAdapter(FtpClientService ftpClientService) {
        this.ftpClientService = ftpClientService;
    }
    
    @Override
    public boolean changeDirectory(String path) throws IOException {
        return ftpClientService.changeDirectory(path);
    }
    
    @Override
    public List<FtpFileInfo> listFiles(String path) throws IOException {
        return ftpClientService.listFiles(path);
    }
    
    @Override
    public boolean createDirectory(String path) throws IOException {
        return ftpClientService.createDirectory(path);
    }
    
    @Override
    public boolean deleteDirectory(String path) throws IOException {
        return ftpClientService.deleteDirectory(path);
    }
}


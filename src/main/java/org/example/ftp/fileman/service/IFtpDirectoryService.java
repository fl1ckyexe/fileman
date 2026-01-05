package org.example.ftp.fileman.service;

import java.io.IOException;
import java.util.List;

import org.example.ftp.fileman.ftp.FtpFileInfo;


public interface IFtpDirectoryService {
    
    boolean changeDirectory(String path) throws IOException;
    
  
    List<FtpFileInfo> listFiles(String path) throws IOException;
    

    boolean createDirectory(String path) throws IOException;
    
 
    boolean deleteDirectory(String path) throws IOException;
}


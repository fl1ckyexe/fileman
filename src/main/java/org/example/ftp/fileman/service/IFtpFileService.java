package org.example.ftp.fileman.service;

import java.io.File;
import java.io.IOException;


public interface IFtpFileService {
  
    boolean uploadFile(String remotePath, File localFile, 
                      UploadProgressCallback progressCallback) throws IOException;
 
    boolean downloadFile(String remotePath, File localFile, 
                        DownloadProgressCallback progressCallback, 
                        long fileSize) throws IOException;
    

    boolean deleteFile(String path) throws IOException;
    
 
    @FunctionalInterface
    interface UploadProgressCallback {
        boolean onProgress(long bytesTransferred, long totalBytes, long speedBytesPerSecond);
    }
    

    @FunctionalInterface
    interface DownloadProgressCallback {
        boolean onProgress(long bytesTransferred, long totalBytes, long speedBytesPerSecond);
    }
}


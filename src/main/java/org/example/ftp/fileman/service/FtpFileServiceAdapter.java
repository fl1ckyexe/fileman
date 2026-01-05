package org.example.ftp.fileman.service;

import org.example.ftp.fileman.ftp.FtpClientService;

import java.io.File;
import java.io.IOException;


public class FtpFileServiceAdapter implements IFtpFileService {
    
    private final FtpClientService ftpClientService;
    
    public FtpFileServiceAdapter(FtpClientService ftpClientService) {
        this.ftpClientService = ftpClientService;
    }
    
    @Override
    public boolean uploadFile(String remotePath, File localFile, 
                             UploadProgressCallback progressCallback) throws IOException {
        if (progressCallback != null) {
            FtpClientService.UploadProgressCallback adapterCallback = 
                (bytesTransferred, totalBytes, speedBytesPerSecond) -> {
                    return progressCallback.onProgress(bytesTransferred, totalBytes, (long)speedBytesPerSecond);
                };
            return ftpClientService.uploadFile(localFile, remotePath, adapterCallback, null);
        } else {
            return ftpClientService.uploadFile(localFile, remotePath, null, null);
        }
    }
    
    @Override
    public boolean downloadFile(String remotePath, File localFile, 
                               DownloadProgressCallback progressCallback, 
                               long fileSize) throws IOException {
        if (progressCallback != null) {
            FtpClientService.DownloadProgressCallback adapterCallback = 
                (bytesTransferred, totalBytes, speedBytesPerSecond) -> {
                    return progressCallback.onProgress(bytesTransferred, totalBytes, (long)speedBytesPerSecond);
                };
            return ftpClientService.downloadFile(remotePath, localFile, adapterCallback, fileSize);
        } else {
            return ftpClientService.downloadFile(remotePath, localFile, null, fileSize);
        }
    }
    
    @Override
    public boolean deleteFile(String path) throws IOException {
        return ftpClientService.deleteFile(path);
    }
}


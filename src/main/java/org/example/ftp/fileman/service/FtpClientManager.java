package org.example.ftp.fileman.service;

import java.io.IOException;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.parser.DefaultFTPFileEntryParserFactory;
import org.apache.commons.net.ftp.parser.FTPFileEntryParserFactory;


public class FtpClientManager {
    private FTPClient ftpClient;
    private boolean connected = false;

    public FtpClientManager() {
        this.ftpClient = new FTPClient();
        FTPFileEntryParserFactory parserFactory = new DefaultFTPFileEntryParserFactory();
        ftpClient.setParserFactory(parserFactory);
    }

    public boolean connect(String host, int port, String username, String password) {
        try {
            if (ftpClient.isConnected()) {
                try {
                    ftpClient.disconnect();
                } catch (IOException e) {
                }
            }

            connected = false;

            ftpClient = new FTPClient();
            FTPFileEntryParserFactory parserFactory = new DefaultFTPFileEntryParserFactory();
            ftpClient.setParserFactory(parserFactory);

            ftpClient.connect(host, port);
            int replyCode = ftpClient.getReplyCode();

            if (!FTPReply.isPositiveCompletion(replyCode)) {
                try {
                    ftpClient.disconnect();
                } catch (IOException e) {
                }
                return false;
            }

            boolean loggedIn = ftpClient.login(username, password);
            if (loggedIn) {
                ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
                connected = true;
                return true;
            } else {
                try {
                    ftpClient.disconnect();
                } catch (IOException e) {
                }
                return false;
            }
        } catch (IOException e) {
            try {
                if (ftpClient.isConnected()) {
                    ftpClient.disconnect();
                }
            } catch (IOException e2) {
            }
            return false;
        }
    }

    public void disconnect() {
        try {
            if (ftpClient.isConnected()) {
                ftpClient.logout();
                ftpClient.disconnect();
            }
            connected = false;
        } catch (IOException e) {
        }
    }

    public boolean isConnected() {
        return connected && ftpClient.isConnected();
    }

    
    public FTPClient getFtpClient() {
        return ftpClient;
    }

    public String getCurrentDirectory() throws IOException {
        if (!isConnected()) {
            return "/";
        }
        String pwd = ftpClient.printWorkingDirectory();
        return pwd;
    }
}


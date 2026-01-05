package org.example.ftp.fileman.ftp;

import java.util.Calendar;

public class FtpFileInfo {
    private String name;
    private boolean isDirectory;
    private long size;
    private Calendar timestamp;
    private FolderType folderType;
    private String fullPath;  

    public FtpFileInfo(String name, boolean isDirectory, long size, Calendar timestamp) {
        this(name, isDirectory, size, timestamp, FolderType.GLOBAL, null);
    }

    public FtpFileInfo(String name, boolean isDirectory, long size, Calendar timestamp, FolderType folderType, String fullPath) {
        this.name = name;
        this.isDirectory = isDirectory;
        this.size = size;
        this.timestamp = timestamp;
        this.folderType = folderType;
        this.fullPath = fullPath;
    }

    public String getName() {
        return name;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public long getSize() {
        return size;
    }

    public Calendar getTimestamp() {
        return timestamp;
    }

    public FolderType getFolderType() {
        return folderType;
    }

    public void setFolderType(FolderType folderType) {
        this.folderType = folderType;
    }

    public String getFullPath() {
        return fullPath;
    }

    public void setFullPath(String fullPath) {
        this.fullPath = fullPath;
    }

    public String getFormattedSize() {
        if (isDirectory) {
            return "<DIR>";
        }
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else {
            return String.format("%.2f MB", size / (1024.0 * 1024.0));
        }
    }

    public String getFormattedDate() {
        if (timestamp == null) {
            return "";
        }
        return String.format("%04d-%02d-%02d %02d:%02d",
            timestamp.get(Calendar.YEAR),
            timestamp.get(Calendar.MONTH) + 1,
            timestamp.get(Calendar.DAY_OF_MONTH),
            timestamp.get(Calendar.HOUR_OF_DAY),
            timestamp.get(Calendar.MINUTE));
    }
}


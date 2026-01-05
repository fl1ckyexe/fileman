package org.example.ftp.fileman.service;

import org.example.ftp.fileman.ftp.FolderType;

 
public class NavigationService {
    
    public static String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        
        
        path = path.replace('\\', '/');
        
         
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        
       
        String[] parts = path.split("/");
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".")) {
                continue;
            } else if (part.equals("..")) {
                if (!result.isEmpty()) {
                    result.remove(result.size() - 1);
                }
            } else {
                result.add(part);
            }
        }
        
        if (result.isEmpty()) {
            return "/";
        }
        
        StringBuilder sb = new StringBuilder("/");
        for (int i = 0; i < result.size(); i++) {
            if (i > 0) {
                sb.append("/");
            }
            sb.append(result.get(i));
        }
        
        return sb.toString();
    }
    
 
    public static String getParentPath(String path) {
        if (path == null || path.isEmpty() || path.equals("/")) {
            return "/";
        }
        
        path = normalizePath(path);
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash <= 0) {
            return "/";
        }
        
        return path.substring(0, lastSlash);
    }
    

    public static String joinPath(String basePath, String... parts) {
        StringBuilder sb = new StringBuilder(basePath == null ? "" : basePath);
        
        if (!sb.toString().endsWith("/") && sb.length() > 0) {
            sb.append("/");
        }
        
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part == null || part.isEmpty()) {
                continue;
            }
            
            if (part.startsWith("/")) {
                part = part.substring(1);
            }
            
            if (i > 0) {
                sb.append("/");
            }
            sb.append(part);
        }
        
        return normalizePath(sb.toString());
    }

    public static String getFileName(String path) {
        if (path == null || path.isEmpty() || path.equals("/")) {
            return "";
        }
        
        path = normalizePath(path);
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash < 0) {
            return path;
        }
        
        return path.substring(lastSlash + 1);
    }
    

    public static String formatSharedPath(String sharedPath) {
        if (sharedPath == null || sharedPath.equals("/")) {
            return "/";
        }
        
        String normalized = normalizePath(sharedPath);
        String[] parts = normalized.split("/");
        
        if (parts.length < 2) {
            return sharedPath;
        }
        
        String folderName = parts[1];
        StringBuilder result = new StringBuilder("/*");
        result.append(folderName);
        result.append("*");
        
        if (parts.length > 2) {
            result.append("/");
            for (int i = 2; i < parts.length; i++) {
                if (i > 2) {
                    result.append("/");
                }
                result.append(parts[i]);
            }
        }
        
        return result.toString();
    }

    public static String getInitialPath(FolderType folderType, String username) {
        switch (folderType) {
            case GLOBAL:
                return "/shared";
            case YOUR_DIRECTORY:
                return "/" + (username != null ? username : "admin");
            case SHARED_BY_USER:
                return "/";
            default:
                return "/";
        }
    }
}


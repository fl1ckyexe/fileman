package org.example.ftp.fileman.ftp;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.parser.DefaultFTPFileEntryParserFactory;
import org.apache.commons.net.ftp.parser.FTPFileEntryParserFactory;

import java.io.*;
import java.net.Socket;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FtpClientService {
    private FTPClient ftpClient;
    private boolean connected = false;
    private static final int SOCKET_TIMEOUT_MS = 5000;

    private volatile FtpErrorType lastErrorType = FtpErrorType.NONE;
    private volatile String lastErrorMessage = "";

    public FtpClientService() {
        this.ftpClient = new FTPClient();
        FTPFileEntryParserFactory parserFactory = new DefaultFTPFileEntryParserFactory();
        ftpClient.setParserFactory(parserFactory);
    }

    public FtpErrorType getLastErrorType() {
        return lastErrorType;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage == null ? "" : lastErrorMessage;
    }

    private void clearLastError() {
        lastErrorType = FtpErrorType.NONE;
        lastErrorMessage = "";
    }

    private boolean fail(FtpErrorType type, String message) {
        lastErrorType = type == null ? FtpErrorType.UNKNOWN : type;
        lastErrorMessage = message == null ? "" : message;
        return false;
    }

    private String replySummary() {
        try {
            int code = ftpClient.getReplyCode();
            String rs = ftpClient.getReplyString();
            if (rs == null) rs = "";
            rs = rs.trim();
            return rs.isEmpty() ? String.valueOf(code) : (code + " " + rs);
        } catch (Exception e) {
            return "";
        }
    }

    private FtpErrorType mapReplyCode(int code) {
        return switch (code) {
            case 530 -> FtpErrorType.INVALID_CREDENTIALS;
            case 550 -> FtpErrorType.PERMISSION_DENIED;
            case 425, 426 -> FtpErrorType.SERVER_UNAVAILABLE;
            default -> FtpErrorType.UNKNOWN;
        };
    }

    private FtpErrorType mapReplyForPath(String reply) {
        if (reply == null) return FtpErrorType.UNKNOWN;
        String r = reply.toLowerCase();
        if (r.contains("not found") || r.contains("no such file") || r.contains("file not found") || r.contains("can't find")) {
            return FtpErrorType.PATH_NOT_FOUND;
        }
        if (r.contains("permission") || r.contains("denied") || r.contains("access")) {
            return FtpErrorType.PERMISSION_DENIED;
        }
        return FtpErrorType.UNKNOWN;
    }

    public boolean connect(String host, int port, String username, String password) {
        try {
            clearLastError();
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

            ftpClient.setConnectTimeout(SOCKET_TIMEOUT_MS);
            ftpClient.setDefaultTimeout(SOCKET_TIMEOUT_MS);
            ftpClient.setDataTimeout(SOCKET_TIMEOUT_MS);

            ftpClient.connect(host, port);
            ftpClient.setSoTimeout(SOCKET_TIMEOUT_MS);
            int replyCode = ftpClient.getReplyCode();

            if (!FTPReply.isPositiveCompletion(replyCode)) {
                try {
                ftpClient.disconnect();
                } catch (IOException e) {
                }
                return fail(FtpErrorType.SERVER_UNAVAILABLE, "Server refused connection (" + replySummary() + ")");
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
                return fail(FtpErrorType.INVALID_CREDENTIALS, "Invalid username/password (" + replySummary() + ")");
            }
        } catch (IOException e) {
            try {
                if (ftpClient.isConnected()) {
                    ftpClient.disconnect();
                }
            } catch (IOException e2) {
            }
            return fail(FtpErrorType.SERVER_UNAVAILABLE, "Server went offline / unreachable");
        }
    }

    public void disconnect() {
        try {
            if (ftpClient.isConnected()) {
                ftpClient.logout();
                ftpClient.disconnect();
            }
            connected = false;
            clearLastError();
        } catch (IOException e) {
        }
    }

    private void disconnectSilently() {
        try {
            if (ftpClient != null && ftpClient.isConnected()) {
                try {
                    ftpClient.logout();
                } catch (Exception ignored) {
                }
                ftpClient.disconnect();
            }
        } catch (Exception ignored) {
        } finally {
            connected = false;
        }
    }

    /**
     * ABOR + best-effort cleanup of pending data transfer on control connection.
     * If cleanup fails, we mark the connection as dead and disconnect.
     */
    private void abortAndReset() {
        try {
            ftpClient.abort();
        } catch (Exception e) {
            fail(FtpErrorType.SERVER_UNAVAILABLE, "Server went offline during transfer");
            disconnectSilently();
            return;
        }

        try {
            ftpClient.completePendingCommand();
        } catch (Exception e) {
            fail(FtpErrorType.PROTOCOL_ERROR, "Transfer cancelled, but session needs reconnect");
            disconnectSilently();
        }
    }

    public boolean isConnected() {
        return connected && ftpClient.isConnected();
    }

    public List<FtpFileInfo> listFiles(String path) throws IOException {
        List<FtpFileInfo> files = new ArrayList<>();

        if (!isConnected()) {
            fail(FtpErrorType.SERVER_UNAVAILABLE, "Not connected");
            return files;
        }

        try {
            files = parseListManually(path);
        } catch (IOException e) {
            fail(FtpErrorType.SERVER_UNAVAILABLE, "Server went offline / unreachable");
            disconnectSilently();
            throw e;
        }

        return files;
    }

    private boolean allFilesHaveEmptyNames(FTPFile[] ftpFiles) {
        if (ftpFiles == null || ftpFiles.length == 0) {
            return true;
        }
        for (FTPFile file : ftpFiles) {
            if (file.getName() != null && !file.getName().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private List<FtpFileInfo> parseListManually(String path) throws IOException {
        List<FtpFileInfo> files = new ArrayList<>();

        try {
            int pasvReply = ftpClient.sendCommand("PASV");
            if (!FTPReply.isPositiveCompletion(pasvReply)) {
                return fallbackListNames(path);
            }

            String pasvResponse = ftpClient.getReplyString();
            Pattern pasvPattern = Pattern.compile("\\(([0-9]+),([0-9]+),([0-9]+),([0-9]+),([0-9]+),([0-9]+)\\)");
            Matcher pasvMatcher = pasvPattern.matcher(pasvResponse);

            if (!pasvMatcher.find()) {
                return fallbackListNames(path);
            }

            String ip = pasvMatcher.group(1) + "." + pasvMatcher.group(2) + "." +
                       pasvMatcher.group(3) + "." + pasvMatcher.group(4);
            int port = Integer.parseInt(pasvMatcher.group(5)) * 256 + Integer.parseInt(pasvMatcher.group(6));

            Socket dataSocket = new Socket(ip, port);

            String listCommand = path == null || path.isEmpty() ? "LIST" : "LIST " + path;
            int listReply = ftpClient.sendCommand(listCommand);

            if (listReply == 226) {
            } else if (!FTPReply.isPositivePreliminary(listReply)) {
                dataSocket.close();
                return fallbackListNames(path);
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(dataSocket.getInputStream(), "UTF-8"))) {

                Pattern unixPattern = Pattern.compile(
                    "^([d-])([rwx-]{9})\\s+\\d+\\s+\\S+\\s+\\S+\\s+(\\d+)\\s+(\\w{3}\\s+\\d{1,2}(?:\\s+\\d{1,2}:\\d{2}|\\s+\\d{4}))\\s+(.+)$"
                );

                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null) {
                    lineCount++;
                    line = line.trim();
                    if (line.isEmpty()) {
                        continue;
                    }

                    Matcher matcher = unixPattern.matcher(line);
                    if (matcher.matches()) {
                        String type = matcher.group(1);
                        String sizeStr = matcher.group(3);
                        String dateStr = matcher.group(4);
                        String name = matcher.group(5);

                        if (name.equals(".") || name.equals("..")) {
                            continue;
                        }

                        boolean isDir = type.equals("d");
                        long size = 0;
                        try {
                            size = Long.parseLong(sizeStr);
                        } catch (NumberFormatException e) {
                        }

                        Calendar timestamp = parseDate(dateStr);

                        files.add(new FtpFileInfo(name, isDir, size, timestamp));
                    } else {
                        String[] parts = line.split("\\s+");
                        if (parts.length >= 2) {
                            String name = parts[parts.length - 1];
                            if (!name.equals(".") && !name.equals("..") && !name.isEmpty()) {
                                boolean isDir = line.startsWith("d");
                                files.add(new FtpFileInfo(name, isDir, 0, null));
                            }
                        }
                    }
                }
            }

            dataSocket.close();

            if (listReply != 226) {
                ftpClient.getReply();
            }

        } catch (Exception e) {
            return fallbackListNames(path);
        }

        return files;
    }

    private List<FtpFileInfo> fallbackListNames(String path) throws IOException {
        List<FtpFileInfo> files = new ArrayList<>();

        String[] names;
        try {
            if (path == null || path.isEmpty()) {
                names = ftpClient.listNames();
            } else {
                names = ftpClient.listNames(path);
            }
        } catch (Exception e) {
            return files;
        }

        if (names == null || names.length == 0) {
            return files;
        }

        for (String name : names) {
            if (name == null || name.equals(".") || name.equals("..")) {
                continue;
            }

            boolean isDir = false;
            try {
                String currentDir = ftpClient.printWorkingDirectory();
                if (ftpClient.changeWorkingDirectory(name)) {
                    isDir = true;
                    ftpClient.changeWorkingDirectory(currentDir);
                }
            } catch (Exception e) {
                isDir = false;
            }

            files.add(new FtpFileInfo(name, isDir, 0, null));
        }

        return files;
    }

    private List<FtpFileInfo> parseRawListResponse(String path) throws IOException {
        List<FtpFileInfo> files = new ArrayList<>();

        String[] names;
        if (path == null || path.isEmpty()) {
            names = ftpClient.listNames();
        } else {
            names = ftpClient.listNames(path);
        }

        if (names != null) {
            for (String name : names) {
                if (name.equals(".") || name.equals("..")) {
                    continue;
                }
                boolean isDir = false;
                try {
                    String currentDir = ftpClient.printWorkingDirectory();
                    if (ftpClient.changeWorkingDirectory(name)) {
                        isDir = true;
                        ftpClient.changeWorkingDirectory(currentDir);
                    }
                } catch (Exception e) {
                    isDir = false;
                }
                files.add(new FtpFileInfo(name, isDir, 0, null));
            }
        }

        return files;
    }

    private Calendar parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }

        try {
            SimpleDateFormat format1 = new SimpleDateFormat("MMM dd HH:mm", Locale.ENGLISH);
            SimpleDateFormat format2 = new SimpleDateFormat("MMM dd yyyy", Locale.ENGLISH);

            Calendar cal = Calendar.getInstance();
            try {
                cal.setTime(format1.parse(dateStr));
                if (dateStr.matches("\\w{3}\\s+\\d{1,2}\\s+\\d{1,2}:\\d{2}")) {
                    int currentYear = Calendar.getInstance().get(Calendar.YEAR);
                    cal.set(Calendar.YEAR, currentYear);
                }
            } catch (ParseException e) {
                try {
                    cal.setTime(format2.parse(dateStr));
                } catch (ParseException e2) {
                    return null;
                }
            }
            return cal;
        } catch (Exception e) {
            return null;
        }
    }

    public String getCurrentDirectory() throws IOException {
        if (!isConnected()) {
            fail(FtpErrorType.SERVER_UNAVAILABLE, "Not connected");
            return "/";
        }
        try {
            return ftpClient.printWorkingDirectory();
        } catch (IOException e) {
            fail(FtpErrorType.SERVER_UNAVAILABLE, "Server went offline / unreachable");
            disconnectSilently();
            throw e;
        }
    }

    public boolean changeDirectory(String path) throws IOException {

        if (!isConnected()) {
            fail(FtpErrorType.SERVER_UNAVAILABLE, "Not connected");
            return false;
        }

        String currentDirBefore = null;
        try {
            currentDirBefore = ftpClient.printWorkingDirectory();
        } catch (Exception e) {
        }

        boolean result;
        try {
            result = ftpClient.changeWorkingDirectory(path);
        } catch (IOException e) {
            fail(FtpErrorType.SERVER_UNAVAILABLE, "Server went offline / unreachable");
            disconnectSilently();
            throw e;
        }

        if (result) {
            try {
                String currentDirAfter = ftpClient.printWorkingDirectory();
            } catch (Exception e) {
            }
        } else {
            int code = ftpClient.getReplyCode();
            String rep = replySummary();
            FtpErrorType t = mapReplyCode(code);
            if (t == FtpErrorType.PERMISSION_DENIED) {
                t = mapReplyForPath(rep);
            }
            if (t == FtpErrorType.PATH_NOT_FOUND) {
                fail(t, "Folder not found: " + path + " (" + rep + ")");
            } else if (t == FtpErrorType.PERMISSION_DENIED) {
                fail(t, "Permission denied: " + path + " (" + rep + ")");
            } else {
                fail(FtpErrorType.UNKNOWN, "Failed to change directory: " + path + " (" + rep + ")");
            }
            try {
                String currentDirAfter = ftpClient.printWorkingDirectory();
            } catch (Exception e) {
            }
        }

        return result;
    }

    public boolean createDirectory(String path) throws IOException {

        if (!isConnected()) {
            return false;
        }

        String currentDirBefore = null;
        try {
            currentDirBefore = ftpClient.printWorkingDirectory();
        } catch (Exception e) {
        }

        boolean result = ftpClient.makeDirectory(path);

        if (result) {
            try {
                String currentDirAfter = ftpClient.printWorkingDirectory();
            } catch (Exception e) {
            }
        } else {
        }

        return result;
    }

    public boolean deleteFile(String path) throws IOException {
        if (!isConnected()) {
            fail(FtpErrorType.SERVER_UNAVAILABLE, "Not connected");
            return false;
        }
        boolean ok;
        try {
            ok = ftpClient.deleteFile(path);
        } catch (IOException e) {
            fail(FtpErrorType.SERVER_UNAVAILABLE, "Server went offline / unreachable");
            disconnectSilently();
            throw e;
        }
        if (!ok) {
            String rep = replySummary();
            FtpErrorType t = mapReplyForPath(rep);
            if (t == FtpErrorType.PERMISSION_DENIED) {
                fail(t, "Permission denied: cannot delete " + path + " (" + rep + ")");
            } else if (t == FtpErrorType.PATH_NOT_FOUND) {
                fail(t, "File not found: " + path + " (" + rep + ")");
            } else {
                fail(FtpErrorType.UNKNOWN, "Failed to delete " + path + " (" + rep + ")");
            }
        }
        return ok;
    }

    public boolean deleteDirectory(String path) throws IOException {
        if (!isConnected()) {
            fail(FtpErrorType.SERVER_UNAVAILABLE, "Not connected");
            return false;
        }
        boolean ok;
        try {
            ok = ftpClient.removeDirectory(path);
        } catch (IOException e) {
            fail(FtpErrorType.SERVER_UNAVAILABLE, "Server went offline / unreachable");
            disconnectSilently();
            throw e;
        }
        if (!ok) {
            String rep = replySummary();
            FtpErrorType t = mapReplyForPath(rep);
            if (t == FtpErrorType.PERMISSION_DENIED) {
                fail(t, "Permission denied: cannot delete folder " + path + " (" + rep + ")");
            } else if (t == FtpErrorType.PATH_NOT_FOUND) {
                fail(t, "Folder not found: " + path + " (" + rep + ")");
            } else {
                fail(FtpErrorType.UNKNOWN, "Failed to delete folder " + path + " (" + rep + ")");
            }
        }
        return ok;
    }

    public interface UploadProgressCallback {
        /**
         * Р’С‹Р·С‹РІР°РµС‚СЃСЏ РґР»СЏ РѕР±РЅРѕРІР»РµРЅРёСЏ РїСЂРѕРіСЂРµСЃСЃР° Р·Р°РіСЂСѓР·РєРё.
         * @param bytesTransferred РєРѕР»РёС‡РµСЃС‚РІРѕ РїРµСЂРµРґР°РЅРЅС‹С… Р±Р°Р№С‚
         * @param totalBytes РѕР±С‰РёР№ СЂР°Р·РјРµСЂ С„Р°Р№Р»Р°
         * @param speedBytesPerSecond СЃРєРѕСЂРѕСЃС‚СЊ РїРµСЂРµРґР°С‡Рё РІ Р±Р°Р№С‚Р°С… РІ СЃРµРєСѓРЅРґСѓ
         * @return true, РµСЃР»Рё РїРµСЂРµРґР°С‡Сѓ РЅСѓР¶РЅРѕ РїСЂРѕРґРѕР»Р¶РёС‚СЊ, false - РµСЃР»Рё РѕС‚РјРµРЅРёС‚СЊ
         */
        boolean onProgress(long bytesTransferred, long totalBytes, double speedBytesPerSecond);
    }

    /**
     * РџСЂРѕСЃС‚РѕР№ RateLimiter РґР»СЏ РєР»РёРµРЅС‚Р°, Р°РЅР°Р»РѕРіРёС‡РЅС‹Р№ СЃРµСЂРІРµСЂРЅРѕРјСѓ
     */
    private static class ClientRateLimiter {
        private volatile long bytesPerSecond;
        private long available;
        private long lastCheck;

        public ClientRateLimiter(long bytesPerSecond) {
            this.bytesPerSecond = bytesPerSecond;
            this.available = bytesPerSecond;
            this.lastCheck = System.nanoTime();
        }

        public synchronized void acquire(int bytes) {
            refill();

            while (available < bytes) {
                try {
                    long nanosToWait = (bytes - available) * 1_000_000_000L / bytesPerSecond;
                    Thread.sleep(Math.max(1, nanosToWait / 1_000_000));
                    refill();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            available -= bytes;
        }

        private void refill() {
            long now = System.nanoTime();
            long elapsed = now - lastCheck;

            long refill = elapsed * bytesPerSecond / 1_000_000_000L;

            if (refill > 0) {
                available = Math.min(bytesPerSecond, available + refill);
                lastCheck = now;
            }
        }

        public long getLimit() {
            return bytesPerSecond;
        }
    }

    /**
     * ThrottledInputStream РґР»СЏ РєР»РёРµРЅС‚Р°, РѕРіСЂР°РЅРёС‡РёРІР°СЋС‰РёР№ СЃРєРѕСЂРѕСЃС‚СЊ С‡С‚РµРЅРёСЏ
     * (Р°РЅР°Р»РѕРіРёС‡РЅРѕ СЃРµСЂРІРµСЂРЅРѕРјСѓ ThrottledInputStream РїСЂРё СЃРєР°С‡РёРІР°РЅРёРё)
     */
    private static class ThrottledInputStream extends java.io.FilterInputStream {
        private final ClientRateLimiter limiter;

        public ThrottledInputStream(java.io.InputStream in, ClientRateLimiter limiter) {
            super(in);
            this.limiter = limiter;
        }

        @Override
        public int read(byte[] b, int off, int len) throws java.io.IOException {
            int bytesRead = super.read(b, off, len);
            if (bytesRead > 0) {
                limiter.acquire(bytesRead);
            }
            return bytesRead;
        }

        @Override
        public int read() throws java.io.IOException {
            limiter.acquire(1);
            return super.read();
        }
    }

    public interface DownloadProgressCallback {
        /**
         * Р’С‹Р·С‹РІР°РµС‚СЃСЏ РґР»СЏ РѕР±РЅРѕРІР»РµРЅРёСЏ РїСЂРѕРіСЂРµСЃСЃР° СЃРєР°С‡РёРІР°РЅРёСЏ.
         * @param bytesTransferred РєРѕР»РёС‡РµСЃС‚РІРѕ РїРµСЂРµРґР°РЅРЅС‹С… Р±Р°Р№С‚
         * @param totalBytes РѕР±С‰РёР№ СЂР°Р·РјРµСЂ С„Р°Р№Р»Р°
         * @param speedBytesPerSecond СЃРєРѕСЂРѕСЃС‚СЊ РїРµСЂРµРґР°С‡Рё РІ Р±Р°Р№С‚Р°С… РІ СЃРµРєСѓРЅРґСѓ
         * @return true, РµСЃР»Рё РїРµСЂРµРґР°С‡Сѓ РЅСѓР¶РЅРѕ РїСЂРѕРґРѕР»Р¶РёС‚СЊ, false - РµСЃР»Рё РѕС‚РјРµРЅРёС‚СЊ
         */
        boolean onProgress(long bytesTransferred, long totalBytes, double speedBytesPerSecond);
    }

    public boolean uploadFile(File localFile, String remotePath) throws IOException {
        return uploadFile(localFile, remotePath, null, null);
    }

    public boolean uploadFile(File localFile, String remotePath, UploadProgressCallback progressCallback) throws IOException {
        return uploadFile(localFile, remotePath, progressCallback, null);
    }

    public boolean uploadFile(File localFile, String remotePath, UploadProgressCallback progressCallback, Long rateLimitBytesPerSecond) throws IOException {

        if (!isConnected()) {
            return false;
        }

        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

        ftpClient.enterLocalPassiveMode();

        long fileSize = localFile.length();

        FileInputStream fileInputStream = null;
        OutputStream outputStream = null;
        OutputStream outputStreamToUse = null;
        boolean success = false;
        boolean wasCancelled = false;

        try {

            outputStream = ftpClient.storeFileStream(remotePath);
            if (outputStream == null) {
                int replyCode = ftpClient.getReplyCode();
                String replyString = ftpClient.getReplyString();
                return false;
            }

            outputStreamToUse = outputStream;

            fileInputStream = new FileInputStream(localFile);

            InputStream baseInputStream = fileInputStream;
            if (rateLimitBytesPerSecond != null && rateLimitBytesPerSecond > 0) {
                ClientRateLimiter limiter = new ClientRateLimiter(rateLimitBytesPerSecond);
                baseInputStream = new ThrottledInputStream(fileInputStream, limiter);
            } else {
            }

            InputStream inputStreamToUse;
            if (progressCallback != null && fileSize > 0) {
                inputStreamToUse = new ProgressTrackingInputStream(baseInputStream, fileSize, progressCallback);
            } else {
                inputStreamToUse = baseInputStream;
            }

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStreamToUse.read(buffer)) != -1) {
                outputStreamToUse.write(buffer, 0, bytesRead);

                if (inputStreamToUse instanceof ProgressTrackingInputStream && ((ProgressTrackingInputStream) inputStreamToUse).isCancelled()) {
                    wasCancelled = true;
                    break;
                }
            }

            if (!wasCancelled && inputStreamToUse instanceof ProgressTrackingInputStream) {
                wasCancelled = ((ProgressTrackingInputStream) inputStreamToUse).isCancelled();
            }

            if (wasCancelled) {
                try {
                    inputStreamToUse.close();
                } catch (IOException e) {
                }
                try {
                    outputStreamToUse.close();
                } catch (IOException e) {
                }
                abortAndReset();
                return false;
            }

            inputStreamToUse.close();
            inputStreamToUse = null;
            fileInputStream.close();
            fileInputStream = null;

            outputStreamToUse.close();
            outputStreamToUse = null;
            outputStream.close();
            outputStream = null;

            success = ftpClient.completePendingCommand();
            int replyCode = ftpClient.getReplyCode();
            String replyString = ftpClient.getReplyString();

            if (success && FTPReply.isPositiveCompletion(replyCode)) {
                return true;
            } else {
                return false;
            }
        } catch (IOException e) {
            disconnectSilently();
            return false;
        } finally {
            try {
                if (fileInputStream != null) {
                    fileInputStream.close();
                }
                if (outputStreamToUse != null && outputStreamToUse != outputStream) {
                    outputStreamToUse.close();
                }
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (IOException e) {
            }
        }
    }

    private static class ProgressTrackingInputStream extends InputStream {
        private final InputStream delegate;
        private final long totalBytes;
        private final UploadProgressCallback callback;
        private long bytesRead = 0;
        private long lastUpdateTime = System.currentTimeMillis();
        private long lastBytesRead = 0;
        private double lastSpeed = 0.0;
        private volatile boolean cancelled = false;

        public ProgressTrackingInputStream(InputStream delegate, long totalBytes, UploadProgressCallback callback) {
            this.delegate = delegate;
            this.totalBytes = totalBytes;
            this.callback = callback;
        }

        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public int read() throws IOException {
            int result = delegate.read();
            if (result != -1) {
                bytesRead++;
                updateProgress();
            }
            return result;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (cancelled) {
                return -1;
            }

            int result = delegate.read(b, off, len);
            if (result > 0) {
                bytesRead += result;
                if (!updateProgress(false)) {
                    cancelled = true;
                    try {
                        delegate.close();
                    } catch (IOException e) {
                    }
                    return -1;
                }
            } else if (result == -1) {
                if (bytesRead > 0) {
                    updateProgress(true);
                }
            }
            return result;
        }

        private boolean updateProgress() {
            return updateProgress(false);
        }


        private boolean updateProgress(boolean force) {
            long currentTime = System.currentTimeMillis();
            long timeDelta = currentTime - lastUpdateTime;
            long bytesDelta = bytesRead - lastBytesRead;

            boolean shouldUpdate = force ||
                                   timeDelta >= 10 ||
                                   bytesDelta >= 2048 ||
                                   (bytesRead > 0 && lastBytesRead == 0);

            if (shouldUpdate) {
                double speedBytesPerSecond = lastSpeed;
                if (timeDelta > 0 && bytesDelta > 0) {
                    speedBytesPerSecond = (bytesDelta * 1000.0) / timeDelta;
                    lastSpeed = speedBytesPerSecond;
                } else if (bytesRead > 0 && lastBytesRead == 0) {
                    speedBytesPerSecond = 0.0;
                }

                try {
                    boolean shouldContinue = callback.onProgress(bytesRead, totalBytes, speedBytesPerSecond);
                    if (!shouldContinue) {
                        return false;
                    }
                } catch (Exception e) {
                }

                lastUpdateTime = currentTime;
                lastBytesRead = bytesRead;
            }
            return true;
        }

        @Override
        public void close() throws IOException {
            if (bytesRead > 0) {
                updateProgress(true);
            }
            delegate.close();
        }

        @Override
        public int available() throws IOException {
            return delegate.available();
        }
    }

    public boolean downloadFile(String remotePath, File localFile) throws IOException {
        return downloadFile(remotePath, localFile, null, 0);
    }

    public boolean downloadFile(String remotePath, File localFile, DownloadProgressCallback progressCallback) throws IOException {
        return downloadFile(remotePath, localFile, progressCallback, 0);
    }

    public boolean downloadFile(String remotePath, File localFile, DownloadProgressCallback progressCallback, long knownFileSize) throws IOException {

        if (!isConnected()) {
            return false;
        }

        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

        ftpClient.enterLocalPassiveMode();

        long fileSize = knownFileSize;
        if (fileSize <= 0) {
            try {
                FTPFile[] files = ftpClient.listFiles(remotePath);
                if (files != null && files.length > 0 && files[0].isFile()) {
                    fileSize = files[0].getSize();
                }
            } catch (IOException e) {
            }
        } else {
        }

        InputStream inputStream = null;
        FileOutputStream fileOutputStream = null;
        boolean success = false;
        try {

            inputStream = ftpClient.retrieveFileStream(remotePath);
            if (inputStream == null) {
                int replyCode = ftpClient.getReplyCode();
                String replyString = ftpClient.getReplyString();
                return false;
            }

            fileOutputStream = new FileOutputStream(localFile);

            InputStream inputStreamToUse;
            if (progressCallback != null && fileSize > 0) {
                inputStreamToUse = new ProgressTrackingDownloadInputStream(inputStream, fileSize, progressCallback);
            } else {
                inputStreamToUse = inputStream;
            }

            byte[] buffer = new byte[8192];
            int bytesRead;
            boolean wasCancelled = false;
            try {
                while ((bytesRead = inputStreamToUse.read(buffer)) != -1) {
                    fileOutputStream.write(buffer, 0, bytesRead);
                }
            } catch (IOException e) {
                wasCancelled = true;
            }

            if (!wasCancelled && inputStreamToUse instanceof ProgressTrackingDownloadInputStream) {
                wasCancelled = ((ProgressTrackingDownloadInputStream) inputStreamToUse).isCancelled();
            }

            if (wasCancelled) {
                try {
                    inputStreamToUse.close();
                } catch (IOException e) {
                }
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                }

                try {
                    if (localFile.exists()) {
                        localFile.delete();
                    }
                } catch (Exception e) {
                }

                abortAndReset();
                return false;
            }


            inputStreamToUse.close();
            inputStream = null;

            fileOutputStream.close();
            fileOutputStream = null;


            success = ftpClient.completePendingCommand();
            int replyCode = ftpClient.getReplyCode();
            String replyString = ftpClient.getReplyString();

            if (success && FTPReply.isPositiveCompletion(replyCode)) {
                return true;
            } else {
                return false;
            }

        } catch (IOException e) {
            disconnectSilently();
            return false;
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
                if (fileOutputStream != null) {
                    fileOutputStream.close();
                }
            } catch (IOException e) {
            }
        }
    }

    private static class ProgressTrackingDownloadInputStream extends InputStream {
        private final InputStream delegate;
        private final long totalBytes;
        private final DownloadProgressCallback callback;
        private long bytesRead = 0;
        private long lastUpdateTime = System.currentTimeMillis();
        private long lastBytesRead = 0;
        private double lastSpeed = 0.0;
        private volatile boolean cancelled = false;

        public ProgressTrackingDownloadInputStream(InputStream delegate, long totalBytes, DownloadProgressCallback callback) {
            this.delegate = delegate;
            this.totalBytes = totalBytes;
            this.callback = callback;
        }

        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public int read() throws IOException {
            int result = delegate.read();
            if (result != -1) {
                bytesRead++;
                updateProgress();
            }
            return result;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (cancelled) {
                return -1;
            }

            int result = delegate.read(b, off, len);
            if (result > 0) {
                bytesRead += result;
                if (!updateProgress(false)) {
                    cancelled = true;
                    try {
                        delegate.close();
                    } catch (IOException e) {
                    }
                    return -1;
                }
            } else if (result == -1) {
                if (bytesRead > 0) {
                    updateProgress(true);
                }
            }
            return result;
        }

        private boolean updateProgress() {
            return updateProgress(false);
        }


        private boolean updateProgress(boolean force) {
            long currentTime = System.currentTimeMillis();
            long timeDelta = currentTime - lastUpdateTime;
            long bytesDelta = bytesRead - lastBytesRead;

            boolean shouldUpdate = force ||
                                   timeDelta >= 100 ||
                                   bytesDelta >= 4096 ||
                                   (bytesRead > 0 && lastBytesRead == 0);

            if (shouldUpdate) {
                double speedBytesPerSecond = lastSpeed;
                if (timeDelta > 0 && bytesDelta > 0) {
                    speedBytesPerSecond = (bytesDelta * 1000.0) / timeDelta;
                    lastSpeed = speedBytesPerSecond;
                } else if (bytesRead > 0 && lastBytesRead == 0) {
                    speedBytesPerSecond = 0.0;
                }

                try {
                    boolean shouldContinue = callback.onProgress(bytesRead, totalBytes, speedBytesPerSecond);
                    if (!shouldContinue) {
                        return false;
                    }
                } catch (Exception e) {
                }

                lastUpdateTime = currentTime;
                lastBytesRead = bytesRead;
            }
            return true;
        }

        @Override
        public void close() throws IOException {
            if (bytesRead > 0) {
                updateProgress(true);
            }
            delegate.close();
        }

        @Override
        public int available() throws IOException {
            return delegate.available();
        }
    }

    private static class ProgressTrackingOutputStream extends OutputStream {
        private final OutputStream delegate;
        private final long totalBytes;
        private final DownloadProgressCallback callback;
        private long bytesWritten = 0;
        private long lastUpdateTime = System.currentTimeMillis();
        private long lastBytesWritten = 0;

        public ProgressTrackingOutputStream(OutputStream delegate, long totalBytes, DownloadProgressCallback callback) {
            this.delegate = delegate;
            this.totalBytes = totalBytes;
            this.callback = callback;
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
            bytesWritten++;
            updateProgress();
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
            bytesWritten += len;
            updateProgress(false);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            if (bytesWritten > 0) {
                updateProgress(true);
            }
            delegate.close();
        }

        private void updateProgress() {
            updateProgress(false);
        }

        private void updateProgress(boolean force) {
            long currentTime = System.currentTimeMillis();
            long timeDelta = currentTime - lastUpdateTime;
            long bytesDelta = bytesWritten - lastBytesWritten;

            boolean shouldUpdate = force ||
                                   timeDelta >= 50 ||
                                   bytesDelta >= 2048 ||
                                   (bytesWritten > 0 && lastBytesWritten == 0);

            if (shouldUpdate) {
                double speedBytesPerSecond = timeDelta > 0 ? (bytesDelta * 1000.0) / timeDelta : 0.0;

                try {
                    callback.onProgress(bytesWritten, totalBytes, speedBytesPerSecond);
                } catch (Exception e) {
                }

                lastUpdateTime = currentTime;
                lastBytesWritten = bytesWritten;
            }
        }
    }

    public FTPClient getFtpClient() {
        return ftpClient;
    }
}


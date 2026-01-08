package org.example.ftp.fileman.api;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Base64;

public class FolderPermissionsApiClient {

    private static final String DEFAULT_HOST = "localhost";

    private volatile String baseUrl;
    private volatile String username;
    private volatile String password;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public FolderPermissionsApiClient() {
        this(DEFAULT_HOST);
    }

    public FolderPermissionsApiClient(String serverHost) {
        setServerHost(serverHost);
    }

    public void setServerHost(String serverHost) {
        String protocol = "http";
        String host = serverHost;

        if (host == null || host.trim().isEmpty()) {
            host = DEFAULT_HOST;
        }

        this.baseUrl = protocol + "://" + host + ":9090";
    }

    public void setCredentials(String username, String password) {
        this.username = username;
        this.password = password;
    }

    private HttpRequest.Builder request(String path, boolean requireAuth) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(10));

        if (requireAuth && username != null && password != null) {
            String credentials = username + ":" + password;
            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + encoded);
        }

        return builder;
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static IOException httpError(HttpResponse<String> resp) {
        String body = resp.body();
        String msg = "HTTP " + resp.statusCode() + (body == null || body.isBlank() ? "" : (": " + body));
        return new IOException(msg);
    }

    public static class FolderPermission {
        private final String folder;
        private final boolean read;
        private final boolean write;
        private final boolean execute;

        public FolderPermission(String folder, boolean read, boolean write, boolean execute) {
            this.folder = folder;
            this.read = read;
            this.write = write;
            this.execute = execute;
        }

        public String getFolder() { return folder; }
        public boolean isRead() { return read; }
        public boolean isWrite() { return write; }
        public boolean isExecute() { return execute; }
    }

    public List<FolderPermission> getFolderPermissions(String username)
            throws IOException, InterruptedException {

        HttpRequest req = request("/api/folders/permissions?username=" + urlEncode(username), false)
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) throw httpError(resp);

        return parseFolderPermissions(resp.body());
    }

    public void saveFolderPermission(String user, String folder, boolean r, boolean w, boolean e)
            throws IOException, InterruptedException {

        String json = String.format(
            "{\"user\":\"%s\",\"folder\":\"%s\",\"r\":%s,\"w\":%s,\"e\":%s}",
            escapeJson(user),
            escapeJson(folder),
            r, w, e
        );

        HttpRequest req = request("/api/folders/permissions/save", false)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 204) throw httpError(resp);
    }

    private List<FolderPermission> parseFolderPermissions(String json) {
        List<FolderPermission> out = new ArrayList<>();
        if (json == null || json.trim().isEmpty() || json.equals("[]")) {
            return out;
        }

        String s = json.trim();
        if (!s.startsWith("[") || !s.endsWith("]")) {
            return out;
        }

        String body = s.substring(1, s.length() - 1).trim();
        if (body.isEmpty()) {
            return out;
        }

        String[] items = body.split("\\},\\s*\\{");
        for (String it : items) {
            String obj = it;
            if (!obj.startsWith("{")) obj = "{" + obj;
            if (!obj.endsWith("}")) obj = obj + "}";

            String folder = extractString(obj, "folder");
            boolean read = extractBoolean(obj, "read");
            boolean write = extractBoolean(obj, "write");
            boolean execute = extractBoolean(obj, "execute");

            if (folder != null) {
                out.add(new FolderPermission(folder, read, write, execute));
            }
        }
        return out;
    }

    private String extractString(String json, String key) {
        String p = "\"" + key + "\":\"";
        int i = json.indexOf(p);
        if (i < 0) return null;
        i += p.length();
        int j = json.indexOf('"', i);
        return j < 0 ? null : json.substring(i, j);
    }

    private boolean extractBoolean(String json, String key) {
        String p = "\"" + key + "\":";
        int i = json.indexOf(p);
        if (i < 0) return false;
        i += p.length();
        return json.startsWith("true", i);
    }

    private Long extractLong(String json, String key) {
        String p = "\"" + key + "\":";
        int i = json.indexOf(p);
        if (i < 0) return null;
        i += p.length();

        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }

        if (json.startsWith("null", i)) {
            return null;
        }

        int end = i;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-' || json.charAt(end) == '+')) {
            end++;
        }

        if (end == i) return null;

        try {
            return Long.parseLong(json.substring(i, end));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }


    public static class SharedFolder {
        private final String folderName;
        private final String folderPath;
        private final String ownerUsername;
        private final boolean read;
        private final boolean write;
        private final boolean execute;

        public SharedFolder(String folderName, String folderPath, String ownerUsername, boolean read, boolean write, boolean execute) {
            this.folderName = folderName;
            this.folderPath = folderPath;
            this.ownerUsername = ownerUsername;
            this.read = read;
            this.write = write;
            this.execute = execute;
        }

        public String getFolderName() { return folderName; }
        public String getFolderPath() { return folderPath; }
        public String getOwnerUsername() { return ownerUsername; }
        public boolean isRead() { return read; }
        public boolean isWrite() { return write; }
        public boolean isExecute() { return execute; }
    }

    public List<SharedFolder> getSharedFolders(String username)
            throws IOException, InterruptedException {

        HttpRequest req = request("/api/shared-folders?username=" + urlEncode(username), true)
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) throw httpError(resp);

        return parseSharedFolders(resp.body());
    }

    public void shareFolder(String owner, String userToShare, String folderName, String folderPath, boolean write, boolean execute)
            throws IOException, InterruptedException {

        String json = String.format(
            "{\"owner\":\"%s\",\"userToShare\":\"%s\",\"folderName\":\"%s\",\"folderPath\":\"%s\",\"write\":%s,\"execute\":%s}",
            escapeJson(owner),
            escapeJson(userToShare),
            escapeJson(folderName),
            escapeJson(folderPath),
            write,
            execute
        );

        HttpRequest req = request("/api/shared-folders/share", true)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 204) throw httpError(resp);
    }

    public void deleteSharedFolder(String folderPath)
            throws IOException, InterruptedException {

        HttpRequest req = request("/api/shared-folders/delete?folderPath=" + urlEncode(folderPath), true)
                .DELETE()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 204) throw httpError(resp);
    }

    public Long getUserRateLimit(String username)
            throws IOException, InterruptedException {

        HttpRequest req = request("/api/users/" + urlEncode(username), false)
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) throw httpError(resp);

        String json = resp.body();
        return extractLong(json, "rateLimit");
    }


    public Long getGlobalUploadLimit()
            throws IOException, InterruptedException {

        HttpRequest req = request("/api/limits", false)
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) throw httpError(resp);

        String json = resp.body();
        Long upload = extractLong(json, "globalUploadLimit");
        if (upload != null) return upload;
        return extractLong(json, "globalRateLimit");
    }

    public static class UserPermissions {
        private final boolean read;
        private final boolean write;
        private final boolean execute;

        public UserPermissions(boolean read, boolean write, boolean execute) {
            this.read = read;
            this.write = write;
            this.execute = execute;
        }

        public boolean isRead() { return read; }
        public boolean isWrite() { return write; }
        public boolean isExecute() { return execute; }
    }

    public UserPermissions getUserPermissions(String username)
            throws IOException, InterruptedException {

        HttpRequest req = request("/api/user-permissions?user=" + urlEncode(username), false)
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) throw httpError(resp);

        String json = resp.body();
        boolean read = extractBoolean(json, "read");
        boolean write = extractBoolean(json, "write");
        boolean execute = extractBoolean(json, "execute");

        return new UserPermissions(read, write, execute);
    }

    private List<SharedFolder> parseSharedFolders(String json) {
        List<SharedFolder> out = new ArrayList<>();
        if (json == null || json.trim().isEmpty() || json.equals("[]")) {
            return out;
        }

        String s = json.trim();
        if (!s.startsWith("[") || !s.endsWith("]")) {
            return out;
        }

        String body = s.substring(1, s.length() - 1).trim();
        if (body.isEmpty()) {
            return out;
        }


        String[] items = body.split("\\}\\s*,\\s*\\{");

        for (int i = 0; i < items.length; i++) {
            String it = items[i];
            String obj = it;
            if (!obj.startsWith("{")) obj = "{" + obj;
            if (!obj.endsWith("}")) obj = obj + "}";


            String folderName = extractString(obj, "folderName");
            String folderPath = extractString(obj, "folderPath");
            String ownerUsername = extractString(obj, "ownerUsername");
            boolean read = extractBoolean(obj, "read");
            boolean write = extractBoolean(obj, "write");
            boolean execute = extractBoolean(obj, "execute");

            if (folderName != null && folderPath != null) {
                out.add(new SharedFolder(folderName, folderPath, ownerUsername, read, write, execute));
            } else {
            }
        }
        return out;
    }
}


package com.docreader.models;

/**
 * Model class representing a recently opened file.
 */
public class RecentFile {
    private String name;
    private String path;
    private String type;
    private long lastOpened;

    public RecentFile() {}

    public RecentFile(String name, String path, String type, long lastOpened) {
        this.name = name;
        this.path = path;
        this.type = type;
        this.lastOpened = lastOpened;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public long getLastOpened() {
        return lastOpened;
    }

    public void setLastOpened(long lastOpened) {
        this.lastOpened = lastOpened;
    }

    /**
     * Get file type from file name.
     */
    public static String getFileType(String fileName) {
        if (fileName == null) return "unknown";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "pdf";
        if (lower.endsWith(".docx")) return "docx";
        if (lower.endsWith(".doc")) return "doc";
        return "unknown";
    }
}

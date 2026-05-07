package com.remote.dto;

public class StoredFileInfo {

    private String fileId;
    private String fileName;
    private long fileSize;
    private String downloadUrl;

    public StoredFileInfo(String fileId, String fileName, long fileSize, String downloadUrl) {
        this.fileId = fileId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.downloadUrl = downloadUrl;
    }

    public String getFileId() {
        return fileId;
    }

    public String getFileName() {
        return fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }
}
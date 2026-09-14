package com.firefly.store.model;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;
import java.time.LocalDateTime;

@Entity
@Table(name = "file_info")
public class FileInfo {
    @Id
    private String code;
    private String originalName;
    private String objectName;
    private String contentType;
    private long size;
    private LocalDateTime uploadTime;
    private LocalDateTime expiryTime;
    private long accessCount;

    public FileInfo() {}

    public FileInfo(String code, String originalName, String objectName, String contentType, long size,
                    LocalDateTime uploadTime, LocalDateTime expiryTime) {
        this.code = code;
        this.originalName = originalName;
        this.objectName = objectName;
        this.contentType = contentType;
        this.size = size;
        this.uploadTime = uploadTime;
        this.expiryTime = expiryTime;
        this.accessCount = 0;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    public String getObjectName() { return objectName; }
    public void setObjectName(String objectName) { this.objectName = objectName; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public LocalDateTime getUploadTime() { return uploadTime; }
    public void setUploadTime(LocalDateTime uploadTime) { this.uploadTime = uploadTime; }
    public LocalDateTime getExpiryTime() { return expiryTime; }
    public void setExpiryTime(LocalDateTime expiryTime) { this.expiryTime = expiryTime; }
    public long getAccessCount() { return accessCount; }
    public void setAccessCount(long accessCount) { this.accessCount = accessCount; }

    /**
     * 永久保存：过期时间为 null 即永不销毁。
     * 非持久化字段，仅在 JSON 序列化时向前端暴露 permanent 标识。
     */
    @Transient
    public boolean isPermanent() {
        return expiryTime == null;
    }
}

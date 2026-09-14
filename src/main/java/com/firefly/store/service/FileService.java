package com.firefly.store.service;

import com.firefly.store.config.MinioConfig;
import com.firefly.store.model.FileInfo;
import com.firefly.store.repository.FileRepository;
import io.minio.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    /** 临时文件目录前缀 */
    private static final String TMP_PREFIX = "tmp/";

    /** 永久文件按「年月」归档，如 2026-09 */
    private static final DateTimeFormatter MONTH_FOLDER = DateTimeFormatter.ofPattern("yyyy-MM");

    /** 可读名片段（slug）的最大长度，超出截断，避免对象键过长 */
    private static final int MAX_SLUG_LENGTH = 60;

    /** URL 路径中无需百分号编码的字符（RFC 3986 unreserved） */
    private static final String URL_UNRESERVED =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private MinioConfig minioConfig;

    @PostConstruct
    public void init() {
        try {
            String bucket = minioConfig.getBucket();
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                String policy = "{"
                    + "\"Version\": \"2012-10-17\","
                    + "\"Statement\": [{"
                    + "\"Effect\": \"Allow\","
                    + "\"Principal\": {\"AWS\": [\"*\"]},"
                    + "\"Action\": [\"s3:GetObject\"],"
                    + "\"Resource\": [\"arn:aws:s3:::" + bucket + "/*\"]"
                    + "}]"
                    + "}";
                minioClient.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucket).config(policy).build());
                log.info("Bucket '{}' created with public read policy", bucket);
            }
        } catch (Exception e) {
            log.error("Failed to initialize MinIO bucket", e);
        }
    }

    /**
     * 上传临时文件（到期自动销毁）
     */
    public FileInfo upload(MultipartFile file, int expiryDays, int expiryHours) throws Exception {
        return upload(file, expiryDays, expiryHours, false);
    }

    /**
     * 上传文件
     *
     * 对象键形如 {可读名}-{code}{ext}：临时文件放 uploads/tmp/ 下，永久文件按年月归档到 uploads/{yyyy-MM}/ 下。
     * 可读名由原始文件名派生（见 {@link #buildSlug}），code 为 12 位随机串，负责防重名与防遍历。
     *
     * @param permanent true = 永久保存，过期时间为 null，永不被清理；
     *                  false = 临时文件，到期由定时任务清理
     */
    public FileInfo upload(MultipartFile file, int expiryDays, int expiryHours, boolean permanent) throws Exception {
        String code = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String ext = getExtension(file.getOriginalFilename());

        // 可读名片段：让直链自带人类可读的名字，code 仍保留用于防重名与防遍历
        String slug = buildSlug(file.getOriginalFilename());
        String readablePrefix = slug.isEmpty() ? "" : slug + "-";

        LocalDateTime now = LocalDateTime.now();
        String objectName;
        LocalDateTime expiry;

        if (permanent) {
            // 永久文件：uploads/2026-09/柳州夜景-00d16b55ec4d.jpg
            objectName = now.format(MONTH_FOLDER) + "/" + readablePrefix + code + ext;
            expiry = null;
        } else {
            // 临时文件：uploads/tmp/柳州夜景-00d16b55ec4d.jpg
            objectName = TMP_PREFIX + readablePrefix + code + ext;
            expiry = now.plusDays(Math.max(0, expiryDays)).plusHours(Math.max(0, expiryHours));
        }

        minioClient.putObject(PutObjectArgs.builder()
                .bucket(minioConfig.getBucket())
                .object(objectName)
                .stream(file.getInputStream(), file.getSize(), -1)
                .contentType(file.getContentType())
                .build());

        FileInfo info = new FileInfo(code, file.getOriginalFilename(), objectName, file.getContentType(),
                file.getSize(), now, expiry);
        fileRepository.save(info);

        log.info("File uploaded: {} -> {} ({})", file.getOriginalFilename(), objectName,
                permanent ? "permanent" : "expires: " + expiry);
        return info;
    }

    public FileInfo getFileInfo(String code) {
        return fileRepository.findById(code).orElse(null);
    }

    public InputStream getFile(String code) throws Exception {
        FileInfo info = fileRepository.findById(code).orElse(null);
        if (info == null) {
            throw new RuntimeException("File not found: " + code);
        }

        if (info.getExpiryTime() != null && info.getExpiryTime().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("File expired: " + code);
        }

        info.setAccessCount(info.getAccessCount() + 1);
        fileRepository.save(info);

        return minioClient.getObject(GetObjectArgs.builder()
                .bucket(minioConfig.getBucket())
                .object(info.getObjectName())
                .build());
    }

    public String getFileUrl(String code, String originalName) {
        FileInfo info = fileRepository.findById(code).orElse(null);
        if (info == null) return "";
        // objectName 可能含中文/空格，URL 里必须做百分号编码；MinIO 侧仍用原始键访问
        return minioConfig.getUrlPrefix() + "/" + encodePath(info.getObjectName());
    }

    public boolean deleteFile(String code) {
        FileInfo info = fileRepository.findById(code).orElse(null);
        if (info == null) {
            return false;
        }

        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(info.getObjectName())
                    .build());
            fileRepository.delete(info);
            log.info("File deleted: {}", code);
            return true;
        } catch (Exception e) {
            log.error("Failed to delete file from MinIO: {}", code, e);
            return false;
        }
    }

    private String getExtension(String filename) {
        if (filename == null || filename.isEmpty()) return "";
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex == -1 ? "" : filename.substring(dotIndex);
    }

    /**
     * 从原始文件名派生可读名片段（不含扩展名与目录）。
     *
     * 规则：去掉目录与扩展名 → 保留字母/数字/中文等可读字符 →
     * 其余（空格、斜杠、引号、控制符、URL 保留字等）统一折叠为单个 "-" →
     * 去掉首尾的 "-" 与 "." → 截断到 {@link #MAX_SLUG_LENGTH}。
     * 全部不可读时返回空串，由调用方退化为纯 code 的键。
     */
    static String buildSlug(String originalName) {
        if (originalName == null) return "";

        String base = originalName;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) base = base.substring(slash + 1);
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);

        StringBuilder sb = new StringBuilder();
        boolean lastWasDash = false;
        for (int i = 0; i < base.length() && sb.length() < MAX_SLUG_LENGTH; i++) {
            char c = base.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '.' || c == '_') {
                sb.append(c);
                lastWasDash = false;
            } else if (!lastWasDash) {
                sb.append('-');
                lastWasDash = true;
            }
        }

        int start = 0;
        int end = sb.length();
        while (start < end && (sb.charAt(start) == '-' || sb.charAt(start) == '.')) start++;
        while (end > start && (sb.charAt(end - 1) == '-' || sb.charAt(end - 1) == '.')) end--;
        return sb.substring(start, end);
    }

    /**
     * 对对象键做 URL 路径编码：按 UTF-8 逐字节百分号编码，
     * 保留 "/" 作为路径分隔符，unreserved 字符原样输出。
     */
    static String encodePath(String path) {
        if (path == null) return "";
        StringBuilder sb = new StringBuilder(path.length() * 2);
        for (byte raw : path.getBytes(StandardCharsets.UTF_8)) {
            int b = raw & 0xFF;
            char c = (char) b;
            if (b < 0x80 && (URL_UNRESERVED.indexOf(c) >= 0 || c == '/')) {
                sb.append(c);
            } else {
                sb.append('%')
                  .append(Character.toUpperCase(Character.forDigit((b >> 4) & 0xF, 16)))
                  .append(Character.toUpperCase(Character.forDigit(b & 0xF, 16)));
            }
        }
        return sb.toString();
    }

    public void cleanupExpiredFiles() {
        // 永久文件 expiryTime 为 null，不会被 findByExpiryTimeBefore 命中，此处再做一次防御
        List<FileInfo> expired = fileRepository.findByExpiryTimeBefore(LocalDateTime.now());
        int removed = 0;
        for (FileInfo info : expired) {
            if (info.isPermanent()) continue;
            if (deleteFile(info.getCode())) removed++;
        }
        if (removed > 0) {
            log.info("Cleaned up {} expired files", removed);
        }
    }

    public List<FileInfo> getAllFiles() {
        return fileRepository.findAllByOrderByUploadTimeDesc();
    }
}

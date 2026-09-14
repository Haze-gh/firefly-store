package com.firefly.store.controller;

import com.firefly.store.model.FileInfo;
import com.firefly.store.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping
public class FileController {

    @Autowired
    private FileService fileService;

    /**
     * 永久保存密钥（后端强制校验）。
     * 由 application.yml 的 firefly.permanent-key 提供，实际值不写入代码库。
     */
    @Value("${firefly.permanent-key}")
    private String permanentKey;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "expiryDays", defaultValue = "3") int expiryDays,
            @RequestParam(value = "expiryHours", defaultValue = "0") int expiryHours,
            @RequestParam(value = "permanent", defaultValue = "false") boolean permanent,
            @RequestParam(value = "permKey", required = false) String permKey) throws Exception {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(fail("未接收到文件"));
        }

        // 永久保存必须携带正确密钥，后端强制校验
        if (permanent && !isValidPermanentKey(permKey)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(fail("永久保存密钥错误"));
        }

        FileInfo info = fileService.upload(file, expiryDays, expiryHours, permanent);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("code", info.getCode());
        result.put("url", fileService.getFileUrl(info.getCode(), info.getOriginalName()));
        result.put("name", info.getOriginalName());
        result.put("size", info.getSize());
        result.put("permanent", info.isPermanent());
        result.put("expiryTime", info.getExpiryTime());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/files/{code}")
    public Map<String, Object> getFileInfo(@PathVariable String code) {
        FileInfo info = fileService.getFileInfo(code);
        Map<String, Object> result = new HashMap<>();
        if (info != null) {
            result.put("success", true);
            result.put("code", info.getCode());
            result.put("name", info.getOriginalName());
            result.put("contentType", info.getContentType());
            result.put("size", info.getSize());
            result.put("uploadTime", info.getUploadTime());
            result.put("permanent", info.isPermanent());
            result.put("expiryTime", info.getExpiryTime());
            result.put("accessCount", info.getAccessCount());
            result.put("url", fileService.getFileUrl(info.getCode(), info.getOriginalName()));
        } else {
            result.put("success", false);
            result.put("message", "File not found or expired");
        }
        return result;
    }

    @GetMapping("/files")
    public List<FileInfo> listFiles() {
        return fileService.getAllFiles();
    }

    @DeleteMapping("/files/{code}")
    public Map<String, Object> deleteFile(@PathVariable String code) {
        boolean deleted = fileService.deleteFile(code);
        Map<String, Object> result = new HashMap<>();
        result.put("success", deleted);
        result.put("message", deleted ? "File deleted" : "File not found");
        return result;
    }

    @GetMapping("/f/{code}")
    public ResponseEntity<InputStreamResource> download(@PathVariable String code) throws Exception {
        FileInfo info = fileService.getFileInfo(code);
        if (info == null) {
            return ResponseEntity.notFound().build();
        }

        // Check if expired (永久文件 expiryTime 为 null，永不失效)
        LocalDateTime expiry = info.getExpiryTime();
        if (expiry != null && expiry.isBefore(LocalDateTime.now())) {
            return ResponseEntity.status(410).build();
        }

        InputStream stream = fileService.getFile(code);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + info.getOriginalName() + "\"")
                .contentType(MediaType.parseMediaType(info.getContentType()))
                .body(new InputStreamResource(stream));
    }

    /**
     * 校验永久保存密钥（密钥暂时写死，后续可替换为数据库/环境变量校验）
     */
    private boolean isValidPermanentKey(String key) {
        return key != null && permanentKey.equals(key.trim());
    }

    private Map<String, Object> fail(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", message);
        return result;
    }
}

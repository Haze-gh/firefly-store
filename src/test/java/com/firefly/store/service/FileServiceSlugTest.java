package com.firefly.store.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 可读名片段与 URL 编码的纯逻辑测试。
 * 这两段决定了直链长什么样，属于对外契约，必须有回归保护。
 */
class FileServiceSlugTest {

    // ---------- buildSlug ----------

    @Test
    void slug_nullIsEmpty() {
        assertEquals("", FileService.buildSlug(null));
    }

    @Test
    void slug_plainName() {
        assertEquals("photo", FileService.buildSlug("photo.jpg"));
    }

    @Test
    void slug_keepsChinese() {
        assertEquals("柳州夜景", FileService.buildSlug("柳州夜景.jpg"));
    }

    @Test
    void slug_noExtension() {
        assertEquals("无标题", FileService.buildSlug("无标题"));
    }

    @Test
    void slug_foldsIllegalCharsIntoSingleDash() {
        assertEquals("my-file-1", FileService.buildSlug("my file (1).jpg"));
    }

    @Test
    void slug_keepsInnerDots() {
        assertEquals("Screenshot-2026-09-14-at-11.06.47",
                FileService.buildSlug("Screenshot 2026-09-14 at 11.06.47.png"));
    }

    @Test
    void slug_stripsDirectoryTraversal() {
        assertEquals("passwd", FileService.buildSlug("/tmp/evil/../../etc/passwd"));
    }

    @Test
    void slug_stripsWindowsPath() {
        assertEquals("report", FileService.buildSlug("C:\\Users\\me\\report.pdf"));
    }

    @Test
    void slug_trimsLeadingDots() {
        assertEquals("env", FileService.buildSlug(".env"));
    }

    @Test
    void slug_trimsTrailingDashesAndDots() {
        assertEquals("name", FileService.buildSlug("name...jpg"));
        assertEquals("name", FileService.buildSlug("name  .jpg"));
    }

    @Test
    void slug_allIllegalBecomesEmpty() {
        assertEquals("", FileService.buildSlug("!!!.png"));
        assertEquals("", FileService.buildSlug("   .png"));
    }

    @Test
    void slug_isLengthCapped() {
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 200; i++) longName.append('a');
        longName.append(".png");
        assertEquals(60, FileService.buildSlug(longName.toString()).length());
    }

    @Test
    void slug_urlReservedCharsAreFolded() {
        assertEquals("q-1", FileService.buildSlug("q?1.png"));
        assertEquals("a-b", FileService.buildSlug("a#b.png"));
        assertEquals("a-b", FileService.buildSlug("a&b.png"));
    }

    // ---------- encodePath ----------

    @Test
    void encode_asciiPassesThrough() {
        assertEquals("tmp/photo-abc123.jpg",
                FileService.encodePath("tmp/photo-abc123.jpg"));
    }

    @Test
    void encode_keepsSlashAsSeparatorAndEscapesChinese() {
        assertEquals("tmp/%E6%9F%B3%E5%B7%9E%E5%A4%9C%E6%99%AF-abc123.jpg",
                FileService.encodePath("tmp/柳州夜景-abc123.jpg"));
    }

    @Test
    void encode_escapesSpaceAndKeepsDateFolder() {
        assertEquals("2026-09/%E9%A3%8E%E6%99%AF%20%E7%85%A7-abc.jpg",
                FileService.encodePath("2026-09/风景 照-abc.jpg"));
    }

    @Test
    void encode_nestedPathsKeepAllSlashes() {
        assertEquals("a/b/c/d.txt", FileService.encodePath("a/b/c/d.txt"));
    }

    @Test
    void encode_nullIsEmpty() {
        assertEquals("", FileService.encodePath(null));
    }

    @Test
    void encode_isIdempotentFriendlyForAsciiOnly() {
        assertEquals("tmp/00d16b55ec4d.jpg",
                FileService.encodePath("tmp/00d16b55ec4d.jpg"));
    }
}

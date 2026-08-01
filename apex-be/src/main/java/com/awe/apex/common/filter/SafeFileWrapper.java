package com.awe.apex.common.filter;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * 安全文件包装类，用于处理异步任务文件被清理的问题
 *
 * @author Awe
 * @since 2025/6/24 15:55
 */
public class SafeFileWrapper {

    private final String filename;
    private final String contentType;
    private final byte[] content;

    public SafeFileWrapper(String filename, String contentType, byte[] content) {
        this.filename = filename;
        this.contentType = contentType;
        this.content = content;
    }

    public String getFilename() {
        return filename;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getContent() {
        return content;
    }

    public InputStream getInputStream() {
        return new ByteArrayInputStream(content);
    }

}

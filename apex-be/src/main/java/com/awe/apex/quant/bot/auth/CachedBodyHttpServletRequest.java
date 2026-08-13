package com.awe.apex.quant.bot.auth;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 可重复读取请求体的 HTTP 请求。
 */
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] cachedBody;

    /**
     * 缓存原始请求体。
     *
     * @param request 原始请求
     * @param maxBodyBytes 最大请求体字节数
     * @throws IOException 读取失败
     */
    public CachedBodyHttpServletRequest(HttpServletRequest request, int maxBodyBytes) throws IOException {
        super(request);
        this.cachedBody = request.getInputStream().readNBytes(maxBodyBytes + 1);
        if (cachedBody.length > maxBodyBytes) {
            throw new BotRequestBodyTooLargeException("Bot API 请求体不能超过 " + maxBodyBytes + " 字节");
        }
    }

    /**
     * 获取原始请求体。
     *
     * @return 请求体字节
     */
    public byte[] getCachedBody() {
        return cachedBody.clone();
    }

    /**
     * 获取可重复读取的输入流。
     *
     * @return 输入流
     */
    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(cachedBody);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return inputStream.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
            }

            @Override
            public int read() {
                return inputStream.read();
            }
        };
    }

    /**
     * 获取可重复读取的字符流。
     *
     * @return 字符流
     */
    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }
}

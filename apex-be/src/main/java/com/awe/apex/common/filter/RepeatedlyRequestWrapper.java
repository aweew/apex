package com.awe.apex.common.filter;

import cn.hutool.core.io.IoUtil;
import com.awe.apex.common.constant.Constants;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 构建可重复读取inputStream的request
 *
 * @author Awe
 * @since 2025/7/30 13:22
 */
public class RepeatedlyRequestWrapper extends HttpServletRequestWrapper {

    private final byte[] body;

    /**
     * 缓存请求体，供日志与 Controller 重复读取。
     *
     * @param request HTTP 请求
     * @throws IOException 请求体读取异常
     */
    public RepeatedlyRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        request.setCharacterEncoding(Constants.UTF8);
        body = IoUtil.readBytes(request.getInputStream(), false);
    }

    @Override
    public BufferedReader getReader() throws IOException {
        String characterEncoding = getCharacterEncoding();
        Charset charset = characterEncoding == null
                ? StandardCharsets.UTF_8 : Charset.forName(characterEncoding);
        return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        final ByteArrayInputStream bais = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override
            public int read() throws IOException {
                return bais.read();
            }

            @Override
            public int available() throws IOException {
                return body.length;
            }

            @Override
            public boolean isFinished() {
                return bais.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                // 当前包装器基于内存字节数组，容器可同步读取全部数据。
            }
        };
    }

    byte[] getBody() {
        return body.clone();
    }

}

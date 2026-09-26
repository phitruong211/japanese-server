package com.japaneselearning.japanese_learning_api.deck;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.*;

/** Bound the actual JSON body, including chunked requests, before deserialization. */
@Component
public class ImportSizeFilter extends OncePerRequestFilter {
    private static final int MAX = 20 * 1024 * 1024;
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getMethod().equals("POST") || !(request.getRequestURI().equals("/api/v1/decks") || request.getRequestURI().equals("/api/v1/decks/import"));
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        if (request.getContentLengthLong() > MAX) { reject(response); return; }
        byte[] body = request.getInputStream().readNBytes(MAX + 1);
        if (body.length > MAX) { reject(response); return; }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public ServletInputStream getInputStream() {
                var bytes = new ByteArrayInputStream(body);
                return new ServletInputStream() {
                    @Override public int read() { return bytes.read(); }
                    @Override public int read(byte[] b, int off, int len) { return bytes.read(b, off, len); }
                    @Override public boolean isFinished() { return bytes.available() == 0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException("Synchronous request body"); }
                };
            }
            @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), java.nio.charset.StandardCharsets.UTF_8)); }
        }, response);
    }
    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(413); response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"Dữ liệu vượt 20 MB\"}");
    }
}

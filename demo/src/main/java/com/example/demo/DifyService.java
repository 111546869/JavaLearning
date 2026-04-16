package com.example.demo;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class DifyService {

    private static final Logger logger = LoggerFactory.getLogger(DifyService.class);

    @Value("${dify.api.url:https://api.dify.ai/v1}")
    private String apiUrl;

    @Value("${dify.api.key:}")
    private String apiKey;

    @Value("${dify.model:}")
    private String model;

    /**
     * Send the user's message to Dify and stream the raw response chunks to the provided SseEmitter.
     * This implementation uses a simple HttpURLConnection and forwards bytes as UTF-8 strings.
     * The exact request JSON may need adjusting depending on your Dify setup; see README below.
     */
    public void streamChat(String userMessage, SseEmitter emitter) throws Exception {
        // Build payload using Dify /v1/chat-messages shape from your example
        java.util.concurrent.atomic.AtomicBoolean clientConnected = new java.util.concurrent.atomic.AtomicBoolean(true);
        emitter.onCompletion(() -> {
            clientConnected.set(false);
            logger.info("SSE client completed");
        });
        emitter.onTimeout(() -> {
            clientConnected.set(false);
            logger.warn("SSE client timeout");
            emitter.complete();
        });
        emitter.onError((err) -> {
            clientConnected.set(false);
            logger.warn("SSE client error", err);
        });
        String payload = String.format(
                "{\"inputs\":{},\"query\":\"%s\",\"response_mode\":\"streaming\",\"conversation_id\":\"\",\"user\":\"web-user\",\"files\":[]}",
                escape(userMessage)
        );

        // Build list of candidate endpoints to try (prefer chat-messages for this payload shape)
        String base = apiUrl;
        if (base.endsWith("/")) base = base.substring(0, base.length()-1);
        String[] candidates = new String[] {
                base.endsWith("/chat-messages") ? base : base + "/chat-messages",
                base.endsWith("/responses") ? base : base + "/responses",
                base.endsWith("/chat") ? base : base + "/chat"
        };

        boolean streamed = false;
        Exception lastException = null;
        for (String endpoint : candidates) {
            HttpURLConnection attemptConn = null;
            try {
                URL url = new URL(endpoint);
                attemptConn = (HttpURLConnection) url.openConnection();
                attemptConn.setRequestMethod("POST");
                attemptConn.setDoOutput(true);
                attemptConn.setDoInput(true);
                attemptConn.setRequestProperty("Accept", "text/event-stream, application/json, */*");
                attemptConn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                if (apiKey != null && !apiKey.isBlank()) {
                    attemptConn.setRequestProperty("Authorization", "Bearer " + apiKey);
                }

                logger.info("Trying Dify endpoint={}", endpoint);

                try (OutputStream os = attemptConn.getOutputStream()) {
                    os.write(payload.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }

                int status = attemptConn.getResponseCode();
                logger.info("Dify responded status={} for {}", status, endpoint);
                InputStream is = (status >= 400) ? attemptConn.getErrorStream() : attemptConn.getInputStream();
                if (is == null) {
                    if (status == 404) continue;
                    emitter.send(SseEmitter.event().data("[error] empty response from Dify at " + endpoint));
                    emitter.complete();
                    return;
                }

                // If authentication failed with Bearer, retry with x-api-key header (some deployments expect that)
                if (status == 401 || status == 403) {
                    StringBuilder authSb = new StringBuilder();
                    byte[] authBuf = new byte[1024];
                    int authR;
                    while ((authR = is.read(authBuf)) != -1) authSb.append(new String(authBuf, 0, authR, StandardCharsets.UTF_8));
                    logger.warn("Auth failed with Bearer at {}: {}", endpoint, authSb.toString());
                    // try retry with x-api-key
                    HttpURLConnection attemptConn2 = null;
                    try {
                        URL url2 = new URL(endpoint);
                        attemptConn2 = (HttpURLConnection) url2.openConnection();
                        attemptConn2.setRequestMethod("POST");
                        attemptConn2.setDoOutput(true);
                        attemptConn2.setDoInput(true);
                        attemptConn2.setRequestProperty("Accept", "text/event-stream, application/json, */*");
                        attemptConn2.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                        attemptConn2.setRequestProperty("x-api-key", apiKey);

                        logger.info("Retrying {} with x-api-key header", endpoint);

                        try (OutputStream os2 = attemptConn2.getOutputStream()) {
                            os2.write(payload.getBytes(StandardCharsets.UTF_8));
                            os2.flush();
                        }

                        int status2 = attemptConn2.getResponseCode();
                        logger.info("Dify responded status={} for {} with x-api-key", status2, endpoint);
                        InputStream is2 = (status2 >= 400) ? attemptConn2.getErrorStream() : attemptConn2.getInputStream();
                        if (is2 == null) {
                            if (status2 == 404) continue;
                            emitter.send(SseEmitter.event().data("[error] empty response from Dify at " + endpoint));
                            emitter.complete();
                            return;
                        }
                        if (status2 == 404) {
                            StringBuilder sb2 = new StringBuilder();
                            byte[] buf2 = new byte[1024];
                            int r2;
                            while ((r2 = is2.read(buf2)) != -1) sb2.append(new String(buf2, 0, r2, StandardCharsets.UTF_8));
                            logger.warn("Dify 404 body at {}: {}", endpoint, sb2.toString());
                            continue;
                        }
                        if (status2 >= 400) {
                            StringBuilder sb2 = new StringBuilder();
                            byte[] buf2 = new byte[1024];
                            int r2;
                            while ((r2 = is2.read(buf2)) != -1) sb2.append(new String(buf2, 0, r2, StandardCharsets.UTF_8));
                            logger.warn("Dify error body={}", sb2.toString());
                            emitter.send(SseEmitter.event().data("[error] Dify responded status=" + status2 + " body=" + sb2.toString()));
                            emitter.complete();
                            return;
                        }
                        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.startsWith("data:")) {
                                    String dataPayload = line.substring(5).trim();
                                    if (!dataPayload.isEmpty() && !"[DONE]".equals(dataPayload)) {
                                        try {
                                            // 直接把从 Dify 读取到的 data 原样发给前端
                                            emitter.send(SseEmitter.event().data(dataPayload));
                                        } catch (Exception e) {
                                            logger.warn("SSE 发送失败, 客户端可能已断开", e);
                                            break; // 客户端断开即可跳出
                                        }
                                    }
                                }
                            }
                    }
                        
                        emitter.complete();
                        streamed = true;
                        break;
                    }   catch (java.net.SocketTimeoutException e2) {
                        lastException = e2;
                        logger.warn("Socket timeout when calling {} with x-api-key", endpoint, e2);
                    } catch (java.io.IOException e2) {
                        lastException = e2;
                        logger.warn("IO error when calling {} with x-api-key", endpoint, e2);
                    } finally {
                        if (attemptConn2 != null) attemptConn2.disconnect();
                    }
                }

                if (status == 404) {
                    StringBuilder sb = new StringBuilder();
                    byte[] buf = new byte[1024];
                    int r;
                    while ((r = is.read(buf)) != -1) sb.append(new String(buf, 0, r, StandardCharsets.UTF_8));
                    logger.warn("Dify 404 body at {}: {}", endpoint, sb.toString());
                    continue;
                }

                if (status >= 400) {
                    StringBuilder sb = new StringBuilder();
                    byte[] buf = new byte[1024];
                    int r;
                    while ((r = is.read(buf)) != -1) sb.append(new String(buf, 0, r, StandardCharsets.UTF_8));
                    logger.warn("Dify error body={}", sb.toString());
                    emitter.send(SseEmitter.event().data("[error] Dify responded status=" + status + " body=" + sb.toString()));
                    emitter.complete();
                    return;
                }
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data:")) {
                            String dataPayload = line.substring(5).trim();
                            if (!dataPayload.isEmpty() && !"[DONE]".equals(dataPayload)) {
                                try {
                                    // 直接把从 Dify 读取到的 data 原样发给前端
                                    emitter.send(SseEmitter.event().data(dataPayload));
                                } catch (Exception e) {
                                    logger.warn("SSE 发送失败, 客户端可能已断开", e);
                                    break; // 客户端断开即可跳出
                                }
                            }
                        }
                    }
                }
                
                emitter.complete();
                streamed = true;
                break;
            } catch (java.net.SocketTimeoutException e) {
                lastException = e;
                logger.warn("Socket timeout when calling {}", endpoint, e);
            } catch (java.io.IOException e) {
                lastException = e;
                logger.warn("IO error when calling {}", endpoint, e);
            } finally {
                if (attemptConn != null) attemptConn.disconnect();
            }
        }

        if (!streamed) {
            if (lastException != null) {
                emitter.completeWithError(lastException);
                throw lastException;
            }
            emitter.send(SseEmitter.event().data("[error] No Dify endpoint returned a successful response (checked chat-messages, responses, chat)."));
            emitter.complete();
        }
    }
    //处理用户输入特殊符号，在前面加转义符，防止破坏json结构
    private static String escape(String s){
        if (s == null) return "";
        return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n");
    }

    // Accumulate JSON fragments into buf. When a complete JSON object/array is formed (balanced braces/brackets),
    // emit it via the provided SseEmitter. This handles cases where JSON is split across multiple network chunks.
}

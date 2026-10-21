package com.campus.competition.service;

import com.campus.competition.config.LlmProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/** 真 HTTP、模拟 DeepSeek，不需要真实密钥或外网。 */
class LlmTransportTest {
    HttpServer server;
    ExecutorService workers;
    LlmProperties props;
    LlmService llm;
    AtomicReference<String> request = new AtomicReference<>();
    AtomicReference<String> auth = new AtomicReference<>();
    String body;
    int status = 200;
    ObjectMapper mapper = new ObjectMapper();
    @BeforeEach void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        workers = Executors.newCachedThreadPool(); server.setExecutor(workers);
        server.createContext("/chat/completions", e -> {
            request.set(new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            auth.set(e.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            e.sendResponseHeaders(status, bytes.length);
            try (var out=e.getResponseBody()) { for (byte b:bytes) { out.write(b); out.flush(); } }
        });
        server.start();
        props = new LlmProperties(); props.setProvider("deepseek"); props.setModel("deepseek-flash");
        props.setApiKey("test-only"); props.setBaseUrl("http://127.0.0.1:"+server.getAddress().getPort());
        props.setTimeoutSeconds(2); props.setAssistantTimeoutSeconds(2);
        llm = new LlmService(props,mapper);
        body = "{\"choices\":[{\"message\":{\"content\":\"{}\"},\"finish_reason\":\"stop\"}]}";
    }
    @AfterEach void stop() { server.stop(0); workers.shutdownNow(); }
    @Test void structuredUsesJsonObjectAndIncludesSchemaWithoutLeakingCredentials() throws Exception {
        var result=llm.generateStructured("候选 ID 14", "{\"required\":[\"teams\"]}", 200);
        assertTrue(result.isOk()); var json=mapper.readTree(request.get());
        assertEquals("json_object",json.path("response_format").path("type").asText());
        assertEquals("disabled",json.path("thinking").path("type").asText());
        assertEquals(0.2, json.path("temperature").asDouble());
        assertTrue(json.path("messages").path(1).path("content").asText().contains("required"));
        assertFalse(request.get().contains("test-only")); assertEquals("Bearer test-only",auth.get());
    }
    @Test void http401IsClassifiedAndBodyNotExposed() { status=401;body="private upstream body";var r=llm.generateText("x",20); assertEquals("HTTP_401",r.getError()); assertFalse(r.getMessage().contains("private")); }
    @Test void http429IsClassified() { status=429;var r=llm.generateText("x",20); assertEquals("HTTP_429",r.getError()); }
    @Test void emptyContentIsNotSuccess() { body="{\"choices\":[{\"message\":{\"content\":\"\"},\"finish_reason\":\"stop\"}]}";assertEquals("EMPTY_RESPONSE",llm.generateText("x",20).getError()); }
    @Test void truncatedJsonIsNotSuccess() { body="{\"choices\":[{\"message\":{\"content\":\"{}\"},\"finish_reason\":\"length\"}]}";assertEquals("TRUNCATED",llm.generateText("x",20).getError()); }
    @Test void deepseekSseCommentsReasoningAndDone() throws Exception {
        body=": heartbeat\r\n\r\ndata: {\"choices\":[{\"delta\":{\"reasoning_content\":\"private\"}}]}\r\n\r\ndata: {\"choices\":[{\"delta\":{\"content\":\"学习答案\"}}]}\r\n\r\ndata: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\r\n\r\ndata: [DONE]\r\n\r\n";
        StringBuilder text=new StringBuilder();llm.stream(mapper.createArrayNode(),text::append,()->false);
        assertEquals("学习答案",text.toString()); assertTrue(mapper.readTree(request.get()).path("stream").asBoolean());
    }
    @Test void sseWithoutDoneIsFailure() {
        body="data: {\"choices\":[{\"delta\":{\"content\":\"部分\"}}]}\n\n";
        assertThrows(IllegalStateException.class,()->llm.stream(mapper.createArrayNode(),s->{},()->false));
    }
    @Test void sseErrorIsFailure() {
        body="data: {\"error\":{\"message\":\"private\"}}\n\n";
        var e=assertThrows(IllegalStateException.class,()->llm.stream(mapper.createArrayNode(),s->{},()->false));assertFalse(e.getMessage().contains("private"));
    }
    @Test void disabledAndMissingKeyNeverCallNetwork() {
        props.setApiKey("");assertFalse(llm.generateText("x",20).isOk());assertNull(request.get());
        props.setEnabled(false);assertFalse(llm.generateText("x",20).isOk());assertNull(request.get());
    }
    @Test void ollamaNdjsonStillWorks() throws Exception {
        server.createContext("/api/chat",e->{ byte[] bytes="{\"message\":{\"content\":\"本地回答\"},\"done\":false}\n{\"done\":true}\n".getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);e.close(); });
        props.setProvider("ollama");StringBuilder text=new StringBuilder();llm.stream(mapper.createArrayNode(),text::append,()->false);assertEquals("本地回答",text.toString());
    }
    @Test void cancelledBudgetNeverStartsAnotherCall() {
        try (var budget=new LlmCallBudget(10)) { budget.cancel(); assertFalse(llm.generateText("x",20).isOk());assertNull(request.get()); }
    }
    @Test void timeoutHasItsOwnCategoryAndRespectsWholeTurnBudget() {
        server.removeContext("/chat/completions");
        server.createContext("/chat/completions", e -> {
            try { Thread.sleep(3000); } catch (InterruptedException ignored) { }
            e.close();
        });
        long start = System.nanoTime();
        try (var budget = new LlmCallBudget(1)) {
            assertEquals("LLM_TIMEOUT", llm.generateText("x",20).getError());
            assertEquals("LLM_TIMEOUT", llm.generateText("x",20).getError());
        }
        assertTrue((System.nanoTime()-start)/1_000_000 < 2400);
    }
    @Test void cancelledBlockedStreamClosesPromptly() throws Exception {
        server.removeContext("/chat/completions");
        CountDownLatch headers = new CountDownLatch(1);
        server.createContext("/chat/completions", e -> {
            e.sendResponseHeaders(200,0); e.getResponseBody().write(": keepalive\n\n".getBytes(StandardCharsets.UTF_8)); e.getResponseBody().flush(); headers.countDown();
            try { Thread.sleep(5000); } catch (InterruptedException ignored) { }
            e.close();
        });
        AtomicBoolean cancelled = new AtomicBoolean();
        Future<?> job = workers.submit(() -> { try { llm.stream(mapper.createArrayNode(),s -> {},cancelled::get); } catch (Exception ignored) { } });
        assertTrue(headers.await(2,TimeUnit.SECONDS)); cancelled.set(true);
        job.get(1500,TimeUnit.MILLISECONDS);
    }
    @Test void streamingHasTotalDeadlineEvenWithHeadersReceived() {
        props.setAssistantTimeoutSeconds(1); server.removeContext("/chat/completions");
        server.createContext("/chat/completions", e -> {
            e.sendResponseHeaders(200,0); e.getResponseBody().write(": heartbeat\n\n".getBytes(StandardCharsets.UTF_8)); e.getResponseBody().flush();
            try { Thread.sleep(4000); } catch (InterruptedException ignored) { }
            e.close();
        });
        long start=System.nanoTime();
        assertThrows(Exception.class, () -> llm.stream(mapper.createArrayNode(),s -> {},() -> false));
        assertTrue((System.nanoTime()-start)/1_000_000 < 2500);
    }
}

package org.alfresco.transform.ai;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class LlmService {

    @Value("${transform.ai.endpoint:http://model-runner.docker.internal/engines/llama.cpp/v1}")
    private String endpoint;

    @Value("${transform.ai.model:ai/bartowski/Llama-3.2-3B-Instruct-GGUF}")
    private String model;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * Extract text from the input stream based on the source MIME type.
     *
     * @param inputStream   the document input stream
     * @param sourceMimetype the source MIME type
     * @return extracted plain text
     * @throws IOException if extraction fails
     */
    public String extractText(InputStream inputStream, String sourceMimetype) throws IOException {
        if ("application/pdf".equals(sourceMimetype)) {
            try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
                PDFTextStripper stripper = new PDFTextStripper();
                return stripper.getText(document);
            }
        } else {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Call the LLM with a system prompt and user text.
     *
     * @param systemPrompt the system instruction for the LLM
     * @param userText     the user content (extracted document text)
     * @return the LLM's response content
     * @throws IOException          if HTTP request fails
     * @throws InterruptedException if the request is interrupted
     */
    public String call(String systemPrompt, String userText) throws IOException, InterruptedException {
        String requestBody = String.format("""
                {
                  "model": "%s",
                  "messages": [
                    {"role": "system", "content": %s},
                    {"role": "user", "content": %s}
                  ],
                  "temperature": 0.3,
                  "max_tokens": 500
                }
                """,
                model,
                escapeJson(systemPrompt),
                escapeJson(userText.length() > 4000 ? userText.substring(0, 4000) : userText)
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint + "/chat/completions"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        log.debug("Calling LLM at {} with model {}", endpoint, model);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("LLM API returned HTTP " + response.statusCode() + ": " + response.body());
        }

        return parseResponse(response.body());
    }

    private String parseResponse(String jsonResponse) {
        // Simple JSON parsing for choices[0].message.content
        // Format: {"choices":[{"message":{"content":"..."}}]}
        int contentStart = jsonResponse.indexOf("\"content\":");
        if (contentStart == -1) {
            throw new RuntimeException("LLM response missing 'content' field: " + jsonResponse);
        }
        contentStart = jsonResponse.indexOf("\"", contentStart + 10) + 1;
        int contentEnd = jsonResponse.indexOf("\"", contentStart);
        return jsonResponse.substring(contentStart, contentEnd)
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .trim();
    }

    private String escapeJson(String text) {
        return "\"" + text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }
}

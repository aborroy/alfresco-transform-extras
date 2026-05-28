package org.alfresco.transform.html2md;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@Testcontainers
class Html2MdTransformIT {

    @Container
    static final GenericContainer<?> engine =
            new GenericContainer<>("angelborroy/alf-tengine-html2md:latest")
                    .withExposedPorts(8090)
                    .withEnv("MANAGEMENT_HEALTH_JMS_ENABLED", "false")
                    .waitingFor(Wait.forHttp("/actuator/health").forStatusCode(200));

    @Test
    void transform_htmlToMarkdown() throws Exception {
        byte[] fileBytes;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("sample.html")) {
            assertThat(in).as("sample resource sample.html not found").isNotNull();
            fileBytes = in.readAllBytes();
        }

        String boundary = UUID.randomUUID().toString();
        String url = "http://localhost:" + engine.getMappedPort(8090) + "/transform";

        String body = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"sample.html\"\r\n"
                + "Content-Type: text/html\r\n\r\n";
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        String tail = "\r\n--" + boundary
                + "\r\nContent-Disposition: form-data; name=\"sourceMimetype\"\r\n\r\ntext/html"
                + "\r\n--" + boundary
                + "\r\nContent-Disposition: form-data; name=\"targetMimetype\"\r\n\r\ntext/markdown"
                + "\r\n--" + boundary + "--\r\n";
        byte[] tailBytes = tail.getBytes(StandardCharsets.UTF_8);

        byte[] multipart = new byte[bodyBytes.length + fileBytes.length + tailBytes.length];
        System.arraycopy(bodyBytes, 0, multipart, 0, bodyBytes.length);
        System.arraycopy(fileBytes, 0, multipart, bodyBytes.length, fileBytes.length);
        System.arraycopy(tailBytes, 0, multipart, bodyBytes.length + fileBytes.length, tailBytes.length);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipart))
                .build();

        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isNotEmpty();
    }
}

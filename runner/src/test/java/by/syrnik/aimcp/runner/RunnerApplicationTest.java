package by.syrnik.aimcp.runner;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "aimcp.runner.token=0123456789abcdef0123456789abcdef-app-test",
        "aimcp.runner.profile=../docs/examples/at-profile.yaml"
})
class RunnerApplicationTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    @Value("${server.address}")
    String address;

    @Autowired
    BuildProperties build;

    private HttpResponse<String> send(String method, String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    @Test
    void listensOnLoopbackByDefault() {
        assertThat(address).isEqualTo("127.0.0.1");
    }

    @Test
    void infoReportsProjectFromProfileAndVersionFromBuild() throws Exception {
        HttpResponse<String> r = send("GET", "/v1/info");

        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.headers().firstValue("Content-Type")).hasValueSatisfying(ct -> assertThat(ct).startsWith("application/json"));
        assertThat(r.body())
                .contains("\"ok\":true")
                .contains("\"project\":\"demo-is\"")
                .contains("\"agentVersion\":\"" + build.getVersion() + "\"");
    }

    @Test
    void unknownPathIsEnvelope404() throws Exception {
        HttpResponse<String> r = send("GET", "/no/such/path");

        assertThat(r.statusCode()).isEqualTo(404);
        assertThat(r.headers().firstValue("Content-Type")).hasValueSatisfying(ct -> assertThat(ct).startsWith("application/json"));
        assertThat(r.body()).contains("\"ok\":false").contains("\"code\":\"not_found\"").doesNotContain("/no/such/path");
    }

    @Test
    void wrongMethodIsEnvelope405WithAllow() throws Exception {
        HttpResponse<String> r = send("POST", "/v1/info");

        assertThat(r.statusCode()).isEqualTo(405);
        assertThat(r.headers().firstValue("Allow")).hasValue("GET");
        assertThat(r.body()).contains("\"code\":\"method_not_allowed\"");
    }

    @Test
    void unknownToolIsEnvelope404() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/v1/tools/absent"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        HttpResponse<String> r = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertThat(r.statusCode()).isEqualTo(404);
        assertThat(r.body()).contains("\"code\":\"unknown_tool\"");
    }
}

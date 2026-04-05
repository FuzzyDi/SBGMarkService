package uz.sbg.marking.plugin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import uz.sbg.marking.contracts.MarkOperationRequest;
import uz.sbg.marking.contracts.OperationResponse;
import uz.sbg.marking.contracts.ResolveAndReserveRequest;
import uz.sbg.marking.contracts.ResolveAndReserveResponse;
import uz.sbg.marking.contracts.ReturnResolveAndReserveRequest;
import uz.sbg.marking.contracts.ReturnResolveAndReserveResponse;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class SbgMarkingApiClient {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final Duration requestTimeout;

    public SbgMarkingApiClient(PluginConfig config, ObjectMapper objectMapper) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(config.getConnectTimeout())
                .build();
        this.objectMapper = objectMapper;
        this.baseUrl = config.getBaseUrl();
        this.requestTimeout = config.getReadTimeout();
    }

    public ResolveAndReserveResponse resolveAndReserve(ResolveAndReserveRequest request) throws IOException, InterruptedException {
        return post("/api/v1/marking/resolve-and-reserve", request, ResolveAndReserveResponse.class);
    }

    public ReturnResolveAndReserveResponse returnResolveAndReserve(ReturnResolveAndReserveRequest request) throws IOException, InterruptedException {
        return post("/api/v1/marking/return-resolve-and-reserve", request, ReturnResolveAndReserveResponse.class);
    }

    public OperationResponse soldConfirm(MarkOperationRequest request) throws IOException, InterruptedException {
        return post("/api/v1/marking/sold-confirm", request, OperationResponse.class);
    }

    public OperationResponse saleRelease(MarkOperationRequest request) throws IOException, InterruptedException {
        return post("/api/v1/marking/sale-release", request, OperationResponse.class);
    }

    public OperationResponse returnConfirm(MarkOperationRequest request) throws IOException, InterruptedException {
        return post("/api/v1/marking/return-confirm", request, OperationResponse.class);
    }

    public OperationResponse returnRelease(MarkOperationRequest request) throws IOException, InterruptedException {
        return post("/api/v1/marking/return-release", request, OperationResponse.class);
    }

    public String toJson(Object payload) throws JsonProcessingException {
        return objectMapper.writeValueAsString(payload);
    }

    public <T> T fromJson(String payload, Class<T> type) throws JsonProcessingException {
        return objectMapper.readValue(payload, type);
    }

    private <T> T post(String path, Object payload, Class<T> responseType) throws IOException, InterruptedException {
        String body = objectMapper.writeValueAsString(payload);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .timeout(requestTimeout)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + " for " + path + ": " + response.body());
        }

        return objectMapper.readValue(response.body(), responseType);
    }
}

package uz.sbg.marking.plugin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import uz.sbg.marking.plugin.dto.MarkOperationRequest;
import uz.sbg.marking.plugin.dto.OperationResponse;
import uz.sbg.marking.plugin.dto.ResolveAndReserveRequest;
import uz.sbg.marking.plugin.dto.ResolveAndReserveResponse;
import uz.sbg.marking.plugin.dto.ReturnResolveAndReserveRequest;
import uz.sbg.marking.plugin.dto.ReturnResolveAndReserveResponse;

import java.io.IOException;

/**
 * HTTP-клиент для backend-сервиса маркировки.
 * Использует Apache HttpClient 4.5.x (доступен в runtime Set Retail 10).
 * Java 8 совместим.
 */
public class SbgMarkingApiClient {

    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public SbgMarkingApiClient(PluginConfig config, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.baseUrl = config.getBaseUrl();
        this.connectTimeoutMs = config.getConnectTimeoutMs();
        this.readTimeoutMs = config.getReadTimeoutMs();
    }

    public ResolveAndReserveResponse resolveAndReserve(ResolveAndReserveRequest request) throws IOException {
        return post("/api/v1/marking/resolve-and-reserve", request, ResolveAndReserveResponse.class);
    }

    public ReturnResolveAndReserveResponse returnResolveAndReserve(ReturnResolveAndReserveRequest request) throws IOException {
        return post("/api/v1/marking/return-resolve-and-reserve", request, ReturnResolveAndReserveResponse.class);
    }

    public OperationResponse soldConfirm(MarkOperationRequest request) throws IOException {
        return post("/api/v1/marking/sold-confirm", request, OperationResponse.class);
    }

    public OperationResponse saleRelease(MarkOperationRequest request) throws IOException {
        return post("/api/v1/marking/sale-release", request, OperationResponse.class);
    }

    public OperationResponse returnConfirm(MarkOperationRequest request) throws IOException {
        return post("/api/v1/marking/return-confirm", request, OperationResponse.class);
    }

    public OperationResponse returnRelease(MarkOperationRequest request) throws IOException {
        return post("/api/v1/marking/return-release", request, OperationResponse.class);
    }

    public String toJson(Object payload) throws JsonProcessingException {
        return objectMapper.writeValueAsString(payload);
    }

    public <T> T fromJson(String payload, Class<T> type) throws JsonProcessingException {
        return objectMapper.readValue(payload, type);
    }

    private <T> T post(String path, Object payload, Class<T> responseType) throws IOException {
        String url = baseUrl + path;
        String body = objectMapper.writeValueAsString(payload);

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(connectTimeoutMs)
                .setSocketTimeout(readTimeoutMs)
                .build();

        try (CloseableHttpClient client = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build()) {

            HttpPost post = new HttpPost(url);
            post.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));

            try (CloseableHttpResponse response = client.execute(post)) {
                int statusCode = response.getStatusLine().getStatusCode();
                HttpEntity entity = response.getEntity();
                String responseBody = entity != null ? EntityUtils.toString(entity, "UTF-8") : "";

                if (statusCode < 200 || statusCode >= 300) {
                    throw new IOException("HTTP " + statusCode + " for " + path + ": " + responseBody);
                }

                return objectMapper.readValue(responseBody, responseType);
            }
        }
    }
}

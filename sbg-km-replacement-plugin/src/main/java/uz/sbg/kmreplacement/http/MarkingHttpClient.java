package uz.sbg.kmreplacement.http;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import uz.sbg.kmreplacement.config.KmReplacementConfig;
import uz.sbg.kmreplacement.http.dto.MarkOperationRequest;
import uz.sbg.kmreplacement.http.dto.OperationResponse;
import uz.sbg.kmreplacement.http.dto.ResolveAndReserveRequest;
import uz.sbg.kmreplacement.http.dto.ResolveAndReserveResponse;
import uz.sbg.kmreplacement.http.dto.ReturnResolveAndReserveRequest;
import uz.sbg.kmreplacement.http.dto.ReturnResolveAndReserveResponse;

import java.io.IOException;

/**
 * Минимальный HTTP-клиент {@code sbg-marking-server-py}. Apache HttpClient 4.5.x
 * ({@code provided}, доступен в runtime SR10) + Jackson ({@code provided}).
 * Java 8 совместим.
 *
 * <p>Клиент создаёт новый {@link CloseableHttpClient} на каждый вызов
 * и закрывает его через try-with-resources. Это безопасно для редких
 * кассовых операций (десятки в минуту) и избавляет от риска утечки
 * соединений при reload плагина.</p>
 *
 * <p>Таймауты берутся из {@link KmReplacementConfig} — те же лицензионные
 * ключи {@code marking.service.connect.timeout.ms / read.timeout.ms}.</p>
 */
public class MarkingHttpClient {

    private final ObjectMapper mapper;
    private final String baseUrl;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public MarkingHttpClient(KmReplacementConfig config) {
        this.mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.baseUrl = config.getBaseUrl();
        this.connectTimeoutMs = config.getConnectTimeoutMs();
        this.readTimeoutMs = config.getReadTimeoutMs();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    /** Возвращает тело {@code /health} либо бросает {@link IOException}. */
    public String healthCheck() throws IOException {
        RequestConfig rc = RequestConfig.custom()
                .setConnectTimeout(connectTimeoutMs)
                .setSocketTimeout(readTimeoutMs)
                .build();
        CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(rc).build();
        try {
            HttpGet get = new HttpGet(baseUrl + "/health");
            CloseableHttpResponse resp = client.execute(get);
            try {
                int code = resp.getStatusLine().getStatusCode();
                HttpEntity e = resp.getEntity();
                String body = e != null ? EntityUtils.toString(e, "UTF-8") : "";
                if (code < 200 || code >= 300) {
                    throw new IOException("Health HTTP " + code + ": " + body);
                }
                return body;
            } finally {
                resp.close();
            }
        } finally {
            client.close();
        }
    }

    public ResolveAndReserveResponse resolveAndReserve(ResolveAndReserveRequest req) throws IOException {
        return post("/api/v1/marking/resolve-and-reserve", req, ResolveAndReserveResponse.class);
    }

    public ReturnResolveAndReserveResponse returnResolveAndReserve(ReturnResolveAndReserveRequest req) throws IOException {
        return post("/api/v1/marking/return-resolve-and-reserve", req, ReturnResolveAndReserveResponse.class);
    }

    public OperationResponse soldConfirm(MarkOperationRequest req) throws IOException {
        return post("/api/v1/marking/sold-confirm", req, OperationResponse.class);
    }

    public OperationResponse saleRelease(MarkOperationRequest req) throws IOException {
        return post("/api/v1/marking/sale-release", req, OperationResponse.class);
    }

    public OperationResponse returnConfirm(MarkOperationRequest req) throws IOException {
        return post("/api/v1/marking/return-confirm", req, OperationResponse.class);
    }

    public OperationResponse returnRelease(MarkOperationRequest req) throws IOException {
        return post("/api/v1/marking/return-release", req, OperationResponse.class);
    }

    // ---------------------------------------------------------------
    private <T> T post(String path, Object payload, Class<T> respType) throws IOException {
        String url = baseUrl + path;
        String body = mapper.writeValueAsString(payload);

        RequestConfig rc = RequestConfig.custom()
                .setConnectTimeout(connectTimeoutMs)
                .setSocketTimeout(readTimeoutMs)
                .build();

        CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(rc).build();
        try {
            HttpPost post = new HttpPost(url);
            post.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
            CloseableHttpResponse resp = client.execute(post);
            try {
                int code = resp.getStatusLine().getStatusCode();
                HttpEntity e = resp.getEntity();
                String respBody = e != null ? EntityUtils.toString(e, "UTF-8") : "";
                if (code < 200 || code >= 300) {
                    throw new IOException("HTTP " + code + " for " + path + ": " + respBody);
                }
                return mapper.readValue(respBody, respType);
            } finally {
                resp.close();
            }
        } finally {
            client.close();
        }
    }
}

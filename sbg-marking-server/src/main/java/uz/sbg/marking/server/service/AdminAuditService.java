package uz.sbg.marking.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import uz.sbg.marking.contracts.AdminAuditEventView;
import uz.sbg.marking.contracts.AdminAuditQueryResponse;
import uz.sbg.marking.server.persistence.entity.AdminAuditEventEntity;
import uz.sbg.marking.server.persistence.repository.AdminAuditEventRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class AdminAuditService {
    public static final String HEADER_ROLE = "X-SBG-Role";
    public static final String HEADER_USER = "X-SBG-User";
    public static final String HEADER_REQUEST_ID = "X-Request-Id";

    private final AdminAuditEventRepository adminAuditEventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AdminAuditService(AdminAuditEventRepository adminAuditEventRepository,
                             ObjectMapper objectMapper) {
        this.adminAuditEventRepository = adminAuditEventRepository;
        this.objectMapper = objectMapper;
        this.clock = Clock.systemUTC();
    }

    public void record(HttpServletRequest httpRequest,
                       String action,
                       String targetMarkCode,
                       boolean success,
                       String message,
                       Object payload) {
        AdminAuditEventEntity entity = new AdminAuditEventEntity();
        entity.setEventTs(Instant.now(clock));
        entity.setAction(trimToSize(action, 64));
        entity.setTargetMarkCode(trimToSize(targetMarkCode, 512));
        entity.setSuccess(success);
        entity.setMessage(trimToSize(message, 1024));
        entity.setPayloadJson(serializePayload(payload));

        if (httpRequest != null) {
            entity.setActorRole(trimToSize(normalizeRole(httpRequest.getHeader(HEADER_ROLE)), 32));
            entity.setActorUser(trimToSize(firstNonBlank(httpRequest.getHeader(HEADER_USER), "anonymous"), 128));
            entity.setRequestId(trimToSize(httpRequest.getHeader(HEADER_REQUEST_ID), 128));
            entity.setRemoteAddr(trimToSize(httpRequest.getRemoteAddr(), 128));
            entity.setEndpoint(trimToSize(httpRequest.getMethod() + " " + httpRequest.getRequestURI(), 256));
        } else {
            entity.setActorRole("ANONYMOUS");
            entity.setActorUser("anonymous");
        }

        adminAuditEventRepository.save(entity);
    }

    public AdminAuditQueryResponse query(Integer limit,
                                         String action,
                                         Boolean success,
                                         String targetMarkCode,
                                         String actorUser) {
        int maxItems = limit == null ? 200 : Math.max(1, Math.min(5000, limit));

        List<AdminAuditEventEntity> filtered = adminAuditEventRepository.findAllByNewest().stream()
                .filter(event -> isBlank(action) || equalsIgnoreCase(event.getAction(), action))
                .filter(event -> success == null || success.booleanValue() == event.isSuccess())
                .filter(event -> isBlank(targetMarkCode) || containsIgnoreCase(event.getTargetMarkCode(), targetMarkCode))
                .filter(event -> isBlank(actorUser) || containsIgnoreCase(event.getActorUser(), actorUser))
                .collect(Collectors.toList());

        AdminAuditQueryResponse response = new AdminAuditQueryResponse();
        response.setTotal(filtered.size());
        response.setEvents(filtered.stream()
                .limit(maxItems)
                .map(this::toView)
                .collect(Collectors.toList()));
        return response;
    }

    private AdminAuditEventView toView(AdminAuditEventEntity entity) {
        AdminAuditEventView view = new AdminAuditEventView();
        view.setId(entity.getId() == null ? 0 : entity.getId());
        view.setTimestamp(entity.getEventTs());
        view.setActorUser(entity.getActorUser());
        view.setActorRole(entity.getActorRole());
        view.setAction(entity.getAction());
        view.setEndpoint(entity.getEndpoint());
        view.setRequestId(entity.getRequestId());
        view.setRemoteAddr(entity.getRemoteAddr());
        view.setTargetMarkCode(entity.getTargetMarkCode());
        view.setSuccess(entity.isSuccess());
        view.setMessage(entity.getMessage());
        view.setPayloadJson(entity.getPayloadJson());
        return view;
    }

    private String serializePayload(Object payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{\"error\":\"payload_serialization_failed\"}";
        }
    }

    private String normalizeRole(String value) {
        if (isBlank(value)) {
            return "ANONYMOUS";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean containsIgnoreCase(String source, String filter) {
        if (source == null || filter == null) {
            return false;
        }
        return source.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT));
    }

    private boolean equalsIgnoreCase(String left, String right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.equalsIgnoreCase(right);
    }

    private String firstNonBlank(String first, String second) {
        return isBlank(first) ? second : first;
    }

    private String trimToSize(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= max) {
            return trimmed;
        }
        return trimmed.substring(0, max);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

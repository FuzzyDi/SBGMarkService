package uz.sbg.kmreplacement.http.dto;

public enum ErrorCode {
    NONE,
    NO_CANDIDATE,
    INVALID_MARK_FOR_PRODUCT,
    SERVICE_UNAVAILABLE,
    RESERVE_CONFLICT,
    ALREADY_RETURNED,
    INVALID_STATE,
    VALIDATION_FAILED,
    INTERNAL_ERROR
}

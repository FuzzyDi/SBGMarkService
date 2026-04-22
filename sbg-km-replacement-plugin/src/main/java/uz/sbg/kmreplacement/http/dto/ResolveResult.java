package uz.sbg.kmreplacement.http.dto;

/** Совпадает с enum на backend {@code sbg-marking-server-py}. */
public enum ResolveResult {
    ACCEPT_SCANNED,
    ACCEPT_AUTO_SELECTED,
    REJECT_NO_CANDIDATE,
    HARD_REJECT
}

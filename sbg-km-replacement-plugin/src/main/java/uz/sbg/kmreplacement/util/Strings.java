package uz.sbg.kmreplacement.util;

/**
 * Java-8-совместимые хелперы для строк.
 * {@code String.isBlank()} — Java 11+, поэтому пользоваться им нельзя.
 */
public final class Strings {
    private Strings() {}

    public static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    public static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    public static String firstNonBlank(String a, String b) {
        return !isBlank(a) ? a : b;
    }
}

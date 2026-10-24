package com.walkmate.infrastructure.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Normalises {@code DB_URL} into a JDBC URL that the PostgreSQL driver accepts.
 *
 * <p>Neon (and most managed providers) hand out libpq-style URLs such as
 * {@code postgresql://user:password@host/db?sslmode=require&channel_binding=require}.
 * Those cannot be passed to Hikari unchanged, so this class converts them and applies
 * the Neon-specific defaults:</p>
 *
 * <ul>
 *   <li>drops {@code channel_binding} — a libpq-only option that pgjdbc rejects;</li>
 *   <li>adds {@code sslmode=require} when the host is {@code *.neon.tech};</li>
 *   <li>adds {@code prepareThreshold=0} for the {@code -pooler} endpoint (PgBouncer);</li>
 *   <li>reads credentials embedded in the URL, falling back to {@code DB_USERNAME}/{@code DB_PASSWORD}.</li>
 * </ul>
 */
public final class PostgresConnectionString {

    private static final String JDBC_PREFIX = "jdbc:postgresql://";
    private static final String POSTGRES_SCHEME = "postgres://";
    private static final String POSTGRESQL_SCHEME = "postgresql://";
    private static final String NEON_DOMAIN_SUFFIX = ".neon.tech";

    private PostgresConnectionString() {
    }

    public record Settings(String jdbcUrl, String username, String password) {

        @Override
        public String toString() {
            return "Settings[jdbcUrl=" + jdbcUrl + ", username=" + username + ", password=***]";
        }
    }

    /**
     * @param rawUrl   value of {@code DB_URL} (JDBC, {@code postgresql://} or {@code postgres://})
     * @param username value of {@code DB_USERNAME}, used only when the URL has no credentials
     * @param password value of {@code DB_PASSWORD}, used only when the URL has no credentials
     */
    public static Settings parse(String rawUrl, String username, String password) {
        String url = rawUrl == null ? "" : rawUrl.trim();
        if (url.isEmpty()) {
            throw new IllegalStateException(
                    "DB_URL is not set. Example for Neon:\n"
                            + "DB_URL=postgresql://<user>:<password>@<endpoint>.neon.tech/<database>?sslmode=require");
        }

        String uriText = url.regionMatches(true, 0, "jdbc:", 0, 5) ? url.substring(5) : url;
        if (!startsWithIgnoreCase(uriText, POSTGRES_SCHEME)
                && !startsWithIgnoreCase(uriText, POSTGRESQL_SCHEME)) {
            throw new IllegalStateException(
                    "DB_URL must start with jdbc:postgresql://, postgresql:// or postgres://, but was: "
                            + sanitize(url));
        }

        URI uri;
        try {
            uri = URI.create(uriText);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "DB_URL is not a valid URI. Percent-encode special characters in the password. "
                            + "Cause: " + ex.getMessage(), ex);
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalStateException("DB_URL has no host: " + sanitize(url));
        }

        String path = uri.getRawPath();
        String database = path == null ? "" : (path.startsWith("/") ? path.substring(1) : path);
        if (database.isBlank()) {
            throw new IllegalStateException("DB_URL has no database name: " + sanitize(url));
        }

        String[] urlCredentials = extractCredentials(uri.getRawUserInfo());
        String resolvedUsername = firstNonBlank(urlCredentials[0], username);
        String resolvedPassword = firstNonBlank(urlCredentials[1], password);
        if (resolvedUsername == null || resolvedPassword == null) {
            throw new IllegalStateException(
                    "Database credentials missing. Either include them in DB_URL "
                            + "(postgresql://user:password@host/db) or set DB_USERNAME and DB_PASSWORD.");
        }

        List<String> parameters = normaliseParameters(uri.getRawQuery(), host);

        StringBuilder jdbcUrl = new StringBuilder(JDBC_PREFIX).append(host);
        if (uri.getPort() != -1) {
            jdbcUrl.append(':').append(uri.getPort());
        }
        jdbcUrl.append('/').append(database);
        if (!parameters.isEmpty()) {
            jdbcUrl.append('?').append(String.join("&", parameters));
        }

        return new Settings(jdbcUrl.toString(), resolvedUsername, resolvedPassword);
    }

    private static List<String> normaliseParameters(String rawQuery, String host) {
        List<String> parameters = new ArrayList<>();
        boolean hasSslMode = false;
        boolean hasPrepareThreshold = false;

        if (rawQuery != null && !rawQuery.isBlank()) {
            for (String parameter : rawQuery.split("&")) {
                if (parameter.isBlank()) {
                    continue;
                }
                String key = parameter.split("=", 2)[0].toLowerCase(Locale.ROOT);
                if (key.equals("channel_binding") || key.equals("channelbinding")) {
                    continue;
                }
                hasSslMode |= key.equals("sslmode");
                hasPrepareThreshold |= key.equals("preparethreshold");
                parameters.add(parameter);
            }
        }

        String lowerHost = host.toLowerCase(Locale.ROOT);
        if (lowerHost.endsWith(NEON_DOMAIN_SUFFIX) && !hasSslMode) {
            parameters.add("sslmode=require");
        }
        if (lowerHost.contains("-pooler") && !hasPrepareThreshold) {
            parameters.add("prepareThreshold=0");
        }
        return parameters;
    }

    private static String[] extractCredentials(String rawUserInfo) {
        if (rawUserInfo == null || rawUserInfo.isBlank()) {
            return new String[]{null, null};
        }
        int separator = rawUserInfo.indexOf(':');
        if (separator < 0) {
            return new String[]{decode(rawUserInfo), null};
        }
        return new String[]{
                decode(rawUserInfo.substring(0, separator)),
                decode(rawUserInfo.substring(separator + 1))
        };
    }

    private static String decode(String value) {
        // URI userinfo does not treat '+' as a space, so keep literal plus signs.
        return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    private static String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return null;
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private static String sanitize(String url) {
        return url.replaceAll("(?<=//)[^@/]*@", "***@");
    }
}

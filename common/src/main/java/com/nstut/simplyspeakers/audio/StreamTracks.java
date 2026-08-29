package com.nstut.simplyspeakers.audio;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Validation helpers for direct HTTP(S) internet audio streams. 0.8.x supports
 * only direct file-style stream URLs; no playlist scraping or transcoding.
 *
 * Also hosts the SSRF policy applied before any client-side HTTP connection is
 * opened: loopback, link-local, private, and unspecified IP ranges as well as
 * reserved internal hostnames are rejected, and every redirect target must be
 * revalidated by the caller.
 *
 * <p>Security notes: the DNS-resolving variant used at connection time fails
 * CLOSED &mdash; when a hostname cannot be resolved or its resolved addresses
 * cannot be validated, the URL is rejected. The no-DNS variant used for
 * render-thread preflight stays permissive for hostnames because it must remain
 * robust in environments without DNS.</p>
 *
 * <p>Residual risk: validating a hostname and opening the connection are two
 * separate steps, so a hostile DNS server could answer with different addresses
 * between validation and connection (DNS rebinding / TOCTOU window). Callers
 * should revalidate every redirect target, but this window is only fully closed
 * by resolving once and connecting to the validated address (DNS pinning),
 * which the current connection code does not do.</p>
 */
public final class StreamTracks {

    /** Resolver seam so DNS-dependent policy is testable without real DNS. */
    public interface DnsResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private static final DnsResolver SYSTEM_DNS = InetAddress::getAllByName;

    public static final int MAX_URL_LENGTH = 2048;
    private static final String[] SUPPORTED_EXTENSIONS = {".mp3", ".wav"};
    private static final String[] BLOCKED_HOST_SUFFIXES = {".localhost", ".local", ".internal"};

    private StreamTracks() {
    }

    /** True when the id looks like a supported direct HTTP(S) audio URL. */
    public static boolean isHttpAudioUrl(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_URL_LENGTH) return false;
        String lower = value.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c <= 0x20 || c == 0x7F) return false;
        }
        try {
            URI uri = new URI(value);
            String host = uri.getHost();
            return host != null && !host.isBlank();
        } catch (Exception e) {
            return false;
        }
    }

    /** True when the URL path ends with an extension the client can decode. Query strings and fragments are ignored. */
    public static boolean hasSupportedExtension(String url) {
        if (!isHttpAudioUrl(url)) return false;
        String path = stripQueryAndFragment(url).toLowerCase(Locale.ROOT);
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (path.endsWith(ext)) return true;
        }
        return false;
    }

    /** True when the URL path (ignoring query and fragment) selects the MP3 decoder. */
    public static boolean isMp3Url(String url) {
        if (!isHttpAudioUrl(url)) return false;
        return stripQueryAndFragment(url).toLowerCase(Locale.ROOT).endsWith(".mp3");
    }

    private static String stripQueryAndFragment(String url) {
        int cut = url.length();
        int query = url.indexOf('?');
        if (query >= 0) cut = query;
        int fragment = url.indexOf('#');
        if (fragment >= 0 && fragment < cut) cut = fragment;
        return url.substring(0, cut);
    }

    /**
     * Clamps a requested start position to a usable value: non-finite (NaN or
     * infinite) and negative inputs become 0 so seek/resume values arriving over
     * the network can never corrupt transport math.
     */
    public static float sanitizeStartPosition(float seconds) {
        if (!Float.isFinite(seconds) || seconds < 0.0f) return 0.0f;
        return seconds;
    }

    /**
     * True when the URL may be opened by the client for playback. Applies the
     * HTTP(S) checks plus SSRF protection: loopback, link-local (including the
     * cloud metadata endpoint), RFC1918 private, and unspecified addresses, plus
     * reserved internal hostnames, are rejected.
     *
     * <p>Note: because this check runs before the connection is opened, a
     * hostile DNS server can still rebind the name between validation and
     * connection (TOCTOU window); see the class javadoc.</p>
     *
     * @param url       candidate stream URL
     * @param resolveDns when true, hostnames are resolved and every resolved
     *                   address is validated too, failing closed on resolution
     *                   errors; when false only literal-IP and name-based rules
     *                   are applied (safe for render threads and environments
     *                   without DNS)
     */
    public static boolean isRemoteStreamUrlAllowed(String url, boolean resolveDns) {
        return isRemoteStreamUrlAllowed(url, resolveDns, SYSTEM_DNS);
    }

    /** Resolver-injectable variant of {@link #isRemoteStreamUrlAllowed(String, boolean)}. */
    public static boolean isRemoteStreamUrlAllowed(String url, boolean resolveDns, DnsResolver resolver) {
        if (!isHttpAudioUrl(url)) return false;
        try {
            String host = new URI(url).getHost();
            return host != null && isHostAllowed(host, resolveDns, resolver);
        } catch (Exception e) {
            return false;
        }
    }

    /** Convenience overload that also resolves hostnames via DNS. */
    public static boolean isRemoteStreamUrlAllowed(String url) {
        return isRemoteStreamUrlAllowed(url, true);
    }

    /** Validates a raw host (literal IPv4/IPv6 or hostname) against the SSRF policy. */
    public static boolean isHostAllowed(String host, boolean resolveDns) {
        return isHostAllowed(host, resolveDns, SYSTEM_DNS);
    }

    /** Resolver-injectable variant of {@link #isHostAllowed(String, boolean)}. */
    static boolean isHostAllowed(String host, boolean resolveDns, DnsResolver resolver) {
        if (host == null || host.isBlank()) return false;
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        int zone = normalized.indexOf('%');
        if (zone >= 0) normalized = normalized.substring(0, zone);
        if (normalized.indexOf(':') >= 0) {
            return isIpv6Allowed(normalized);
        }
        if (looksLikeDottedOrNumericHost(normalized)) {
            // Anything digit/dot shaped must parse as a proper, allowed IPv4
            // literal; this also rejects decimal ("2130706433") and octal-style
            // ("0177.0.0.1") encodings of loopback/private addresses.
            return isIpv4Literal(normalized) && isIpv4Allowed(normalized);
        }
        if (isReservedHostname(normalized)) return false;
        if (!resolveDns) return true;
        return areResolvedAddressesAllowed(normalized, resolver);
    }

    private static boolean looksLikeDottedOrNumericHost(String host) {
        boolean hasDigit = false;
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            if (c >= '0' && c <= '9') {
                hasDigit = true;
            } else if (c != '.') {
                return false;
            }
        }
        return hasDigit;
    }

    private static boolean isReservedHostname(String host) {
        String fqdn = host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
        if (fqdn.equals("localhost")) return true;
        for (String suffix : BLOCKED_HOST_SUFFIXES) {
            if (fqdn.endsWith(suffix)) return true;
        }
        return false;
    }

    /**
     * Resolves the host and validates every resolved address against the SSRF
     * policy. Fails CLOSED: if resolution fails, the resolver errors, or no
     * usable addresses are returned, the host is rejected. This is the
     * connection-time check, so an unresolvable or unverifiable host must never
     * fall back to the weaker name-based rules.
     */
    private static boolean areResolvedAddressesAllowed(String host, DnsResolver resolver) {
        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(host);
        } catch (Exception e) {
            // Fail closed: a host we cannot verify must not be connected to.
            return false;
        }
        if (addresses == null || addresses.length == 0) return false;
        for (InetAddress address : addresses) {
            if (address == null) return false;
            String literal = address.getHostAddress();
            if (literal == null || literal.isEmpty()) return false;
            int zone = literal.indexOf('%');
            if (zone >= 0) literal = literal.substring(0, zone);
            boolean allowed = literal.indexOf(':') >= 0
                    ? isIpv6Allowed(literal.toLowerCase(Locale.ROOT))
                    : isIpv4Allowed(literal);
            if (!allowed) return false;
        }
        return true;
    }

    private static boolean isIpv4Literal(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) return false;
            for (int i = 0; i < part.length(); i++) {
                char c = part.charAt(i);
                if (c < '0' || c > '9') return false;
            }
            // Reject octal-looking groups so they can never be misread.
            if (part.length() > 1 && part.charAt(0) == '0') return false;
        }
        return true;
    }

    private static int[] ipv4Octets(String literal) {
        String[] parts = literal.split("\\.", -1);
        if (parts.length != 4) return null;
        int[] octets = new int[4];
        for (int i = 0; i < 4; i++) {
            int value;
            try {
                value = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                return null;
            }
            if (value < 0 || value > 255) return null;
            octets[i] = value;
        }
        return octets;
    }

    private static boolean isIpv4Allowed(String literal) {
        int[] octets = ipv4Octets(literal);
        if (octets == null) return false;
        int first = octets[0];
        int second = octets[1];
        if (first == 0) return false;                                        // 0.0.0.0/8 this network
        if (first == 10) return false;                                       // 10.0.0.0/8 private
        if (first == 127) return false;                                      // 127.0.0.0/8 loopback
        if (first == 169 && second == 254) return false;                     // 169.254.0.0/16 link-local (metadata endpoint)
        if (first == 172 && second >= 16 && second <= 31) return false;      // 172.16.0.0/12 private
        if (first == 192 && second == 168) return false;                     // 192.168.0.0/16 private
        return true;
    }

    private static boolean isIpv6Allowed(String literal) {
        if (literal.isEmpty()) return false;
        // Embedded IPv4 (e.g. "::ffff:10.0.0.1"): validate it, then swap in its
        // hexadecimal hextet form so the generic expansion can proceed.
        if (literal.lastIndexOf('.') >= 0) {
            int segmentStart = literal.lastIndexOf(':');
            if (segmentStart < 0) return false;
            String embedded = literal.substring(segmentStart + 1);
            int[] octets = ipv4Octets(embedded);
            if (octets == null || !isIpv4Allowed(embedded)) return false;
            literal = literal.substring(0, segmentStart + 1)
                    + String.format(Locale.ROOT, "%02x%02x:%02x%02x",
                            octets[0], octets[1], octets[2], octets[3]);
        }
        long[] hextets = expandIpv6(literal);
        if (hextets == null) return false;
        int first = (int) hextets[0];
        if (first >= 0xfe80 && first <= 0xfebf) return false;                // fe80::/10 link-local
        if (first >= 0xfc00 && first <= 0xfdff) return false;                // fc00::/7 unique local
        boolean allZero = true;
        for (long hextet : hextets) {
            if (hextet != 0) {
                allZero = false;
                break;
            }
        }
        if (allZero) return false;                                           // :: unspecified
        boolean loopback = hextets[7] == 1;
        for (int i = 0; i < 7; i++) {
            if (hextets[i] != 0) {
                loopback = false;
                break;
            }
        }
        return !loopback;                                                    // ::1 loopback
    }

    private static long[] expandIpv6(String literal) {
        String[] halves = literal.split("::", -1);
        if (halves.length > 2) return null;
        List<Long> left = new ArrayList<>();
        List<Long> right = new ArrayList<>();
        if (!halves[0].isEmpty() && !addHextets(halves[0], left)) return null;
        if (halves.length == 2 && !halves[1].isEmpty() && !addHextets(halves[1], right)) return null;
        if (left.size() + right.size() > 8) return null;
        if (halves.length == 1 && left.size() != 8) return null;
        long[] result = new long[8];
        for (int i = 0; i < left.size(); i++) {
            result[i] = left.get(i);
        }
        int offset = 8 - right.size();
        for (int i = 0; i < right.size(); i++) {
            result[offset + i] = right.get(i);
        }
        return result;
    }

    private static boolean addHextets(String group, List<Long> out) {
        String[] parts = group.split(":", -1);
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 4) return false;
            long value = 0;
            for (int i = 0; i < part.length(); i++) {
                int digit = Character.digit(part.charAt(i), 16);
                if (digit < 0) return false;
                value = (value << 4) | digit;
            }
            out.add(value);
        }
        return true;
    }
}

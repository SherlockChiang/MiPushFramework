package com.xiaomi.xmsf.push.notification;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;

/**
 * Pure-Java safety policy for Xiaomi focus-notification enhancements.
 *
 * <p>The private {@code miui.focus.*} protocol is always optional. This class keeps
 * the readable Android notification and the delivery identity independent from
 * that optional payload so the policy can be covered by local JVM tests.</p>
 */
public final class FocusNotificationSafety {
    public static final String FOCUS_EXTRA_PREFIX = "miui.focus.";
    public static final int MAX_PARAMETER_BYTES = 3_072;
    public static final long IMAGE_ENRICHMENT_BUDGET_MILLIS = 700L;

    private static final int MAX_FALLBACK_TITLE_CODE_POINTS = 160;
    private static final int MAX_FALLBACK_BODY_CODE_POINTS = 1_024;
    private static final String DEFAULT_TITLE = "MiPush notification";
    private static final String DEFAULT_BODY = "New notification";
    private static final String FOCUS_GROUP_MARKER = "#focus#";

    private FocusNotificationSafety() {
    }

    /**
     * Preserve normal title/body values and fill only missing fields from a bounded
     * focus JSON object. Invalid and oversized JSON is ignored safely.
     */
    public static ResolvedContent resolveReadableContent(
            String title,
            String body,
            String focusParameter,
            String fallbackTitle,
            String fallbackBody) {
        String focusTitle = null;
        String focusTicker = null;
        String focusBody = null;
        if (isParameterWithinLimit(focusParameter)) {
            try {
                JsonElement root = JsonParser.parseString(focusParameter);
                if (root.isJsonObject()) {
                    JsonObject object = root.getAsJsonObject();
                    focusTitle = boundedString(object, "title",
                            MAX_FALLBACK_TITLE_CODE_POINTS);
                    focusTicker = boundedString(object, "ticker",
                            MAX_FALLBACK_TITLE_CODE_POINTS);
                    focusBody = boundedString(object, "description",
                            MAX_FALLBACK_BODY_CODE_POINTS);
                }
            } catch (Throwable ignored) {
                // The ordinary notification remains authoritative.
            }
        }

        String resolvedTitle = hasText(title)
                ? title
                : firstText(focusTitle, focusTicker, focusBody,
                sanitizeFallback(fallbackTitle, MAX_FALLBACK_TITLE_CODE_POINTS),
                DEFAULT_TITLE);
        String resolvedBody = hasText(body)
                ? body
                : firstText(focusBody, focusTitle, focusTicker,
                sanitizeFallback(fallbackBody, MAX_FALLBACK_BODY_CODE_POINTS),
                DEFAULT_BODY);
        return new ResolvedContent(resolvedTitle, resolvedBody);
    }

    public static boolean isParameterWithinLimit(String parameter) {
        if (parameter == null || parameter.length() > MAX_PARAMETER_BYTES) {
            return false;
        }
        return parameter.getBytes(StandardCharsets.UTF_8).length <= MAX_PARAMETER_BYTES;
    }

    /**
     * Returns whether a bounded focus parameter is a JSON object that the
     * SystemUI focus renderer can consume.  This is intentionally a small
     * syntactic check; fields unknown to this bridge are still forwarded.
     *
     * <p>A malformed parameter must never make the ordinary Android
     * notification disappear. Callers can use this predicate to skip the
     * optional focus extras while retaining the URL/text fallback.</p>
     */
    public static boolean isWellFormedParameter(String parameter) {
        if (!isParameterWithinLimit(parameter)) {
            return false;
        }
        try {
            JsonElement root = JsonParser.parseString(parameter);
            return root != null && root.isJsonObject();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isFocusExtraKey(String key) {
        return key != null && key.startsWith(FOCUS_EXTRA_PREFIX);
    }

    public static String stableFocusGroup(String packageName) {
        String prefix = hasText(packageName) ? packageName : "mipush";
        return prefix + "_" + FOCUS_GROUP_MARKER;
    }

    /**
     * Focus messages without an explicit, official group must be isolated from
     * the SDK's historical package-wide default group.  The caller still owns
     * any official group prefixing and pass-through semantics; this method only
     * answers the ambiguity at the default boundary.
     */
    public static boolean shouldIsolateFocusGroup(
            String explicitGroup, boolean hasFocusPayload) {
        return hasFocusPayload && !hasText(explicitGroup);
    }

    /**
     * Try the focus-enhanced delivery once. If it throws, call the same delivery
     * exactly once more with the same package/tag/id and focus disabled.
     */
    public static <T> T deliverWithSingleFallback(
            String packageName,
            String tag,
            int notificationId,
            boolean attemptFocus,
            Delivery<T> delivery) {
        if (!attemptFocus) {
            return invoke(delivery, packageName, tag, notificationId, false, null);
        }
        try {
            return delivery.deliver(packageName, tag, notificationId, true, null);
        } catch (Throwable focusFailure) {
            try {
                return delivery.deliver(packageName, tag, notificationId,
                        false, focusFailure);
            } catch (Throwable fallbackFailure) {
                if (fallbackFailure != focusFailure) {
                    try {
                        fallbackFailure.addSuppressed(focusFailure);
                    } catch (Throwable ignored) {
                    }
                }
                return rethrow(fallbackFailure);
            }
        }
    }

    private static <T> T invoke(
            Delivery<T> delivery,
            String packageName,
            String tag,
            int notificationId,
            boolean includeFocus,
            Throwable focusFailure) {
        try {
            return delivery.deliver(packageName, tag, notificationId,
                    includeFocus, focusFailure);
        } catch (Throwable failure) {
            return rethrow(failure);
        }
    }

    private static <T> T rethrow(Throwable failure) {
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new IllegalStateException("Notification delivery failed", failure);
    }

    private static String boundedString(JsonObject object, String name, int maxCodePoints) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            return null;
        }
        return sanitizeFallback(value.getAsString(), maxCodePoints);
    }

    private static String sanitizeFallback(String value, int maxCodePoints) {
        if (!hasText(value)) {
            return null;
        }
        StringBuilder clean = new StringBuilder(Math.min(value.length(), maxCodePoints));
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (!Character.isISOControl(codePoint)
                    || codePoint == '\n' || codePoint == '\t') {
                clean.appendCodePoint(codePoint);
            }
        }
        String result = clean.toString().trim();
        if (!hasText(result)) {
            return null;
        }
        int count = result.codePointCount(0, result.length());
        if (count <= maxCodePoints) {
            return result;
        }
        int end = result.offsetByCodePoints(0, maxCodePoints);
        return result.substring(0, end);
    }

    private static String firstText(String... candidates) {
        for (String candidate : candidates) {
            if (hasText(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("At least one safe fallback must be present");
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    @FunctionalInterface
    public interface Delivery<T> {
        T deliver(String packageName, String tag, int notificationId,
                  boolean includeFocusExtras, Throwable focusFailure) throws Throwable;
    }

    public static final class ResolvedContent {
        private final String title;
        private final String body;

        private ResolvedContent(String title, String body) {
            this.title = title;
            this.body = body;
        }

        public String title() {
            return title;
        }

        public String body() {
            return body;
        }
    }
}

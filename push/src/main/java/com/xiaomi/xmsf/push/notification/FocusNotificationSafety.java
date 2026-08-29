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
    /**
     * Well-known image alias used by Xiaomi focus templates for the target
     * application's launcher icon.  The alias is referenced from nested
     * {@code param_v2} objects rather than necessarily being present as a
     * top-level PushMetaInfo extra.
     */
    public static final String FOCUS_APP_ICON_PICTURE = "miui.focus.pic_app_icon";
    public static final int MAX_PARAMETER_BYTES = 3_072;
    public static final long IMAGE_ENRICHMENT_BUDGET_MILLIS = 700L;
    public static final int PORTABLE_PROGRESS_MAX = 100;
    /** First observed progress value after a delivery merchant accepts an order. */
    public static final int PORTABLE_MERCHANT_STAGE_PROGRESS = 10;
    /** First observed progress value after the courier starts the delivery leg. */
    public static final int PORTABLE_COURIER_STAGE_PROGRESS = 75;
    /** Progress value at which the delivery timeline is complete. */
    public static final int PORTABLE_DELIVERED_STAGE_PROGRESS = 100;

    private static final int MAX_FALLBACK_TITLE_CODE_POINTS = 160;
    private static final int MAX_FALLBACK_BODY_CODE_POINTS = 1_024;
    private static final int MAX_FALLBACK_URL_CODE_POINTS = 2_048;
    private static final int MAX_SEQUENCE_CODE_POINTS = 128;
    private static final int MAX_SCENE_CODE_POINTS = 64;
    private static final String FOOD_DELIVERY_SCENE = "foodDelivery";
    private static final String FOOD_DELIVERY_BUSINESS = "food_delivery";
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
        PortableFocusData portable = parsePortableFocusData(focusParameter);

        String resolvedTitle = hasText(title)
                ? title
                : firstText(portable.title(), portable.body(),
                sanitizeFallback(fallbackTitle, MAX_FALLBACK_TITLE_CODE_POINTS),
                DEFAULT_TITLE);
        String resolvedBody = hasText(body)
                ? body
                : firstText(portable.body(), portable.title(),
                sanitizeFallback(fallbackBody, MAX_FALLBACK_BODY_CODE_POINTS),
                DEFAULT_BODY);
        return new ResolvedContent(resolvedTitle, resolvedBody);
    }

    /**
     * Parse the public scalar fields that have a direct, safe Android fallback.
     * The original JSON remains untouched for Xiaomi SystemUI, including all
     * unknown fields and picture aliases. Invalid input produces an empty value
     * so optional focus metadata can never suppress the ordinary notification.
     */
    public static PortableFocusData parsePortableFocusData(String focusParameter) {
        if (!isParameterWithinLimit(focusParameter)) {
            return PortableFocusData.EMPTY;
        }
        try {
            JsonElement parsed = JsonParser.parseString(focusParameter);
            if (parsed == null || !parsed.isJsonObject()) {
                return PortableFocusData.EMPTY;
            }

            JsonObject root = parsed.getAsJsonObject();
            JsonObject paramV2 = object(root, "param_v2");
            JsonObject baseInfo = object(paramV2, "baseInfo");
            JsonObject progressInfo = object(paramV2, "progressInfo");

            String scene = firstString(root, MAX_SCENE_CODE_POINTS, "scene");
            if (!hasText(scene)) {
                scene = firstString(paramV2, MAX_SCENE_CODE_POINTS, "scene");
            }
            String business = firstString(paramV2, MAX_SCENE_CODE_POINTS, "business");
            if (!hasText(business)) {
                business = firstString(root, MAX_SCENE_CODE_POINTS, "business");
            }

            String title = firstString(root, MAX_FALLBACK_TITLE_CODE_POINTS,
                    "title", "ticker");
            if (!hasText(title)) {
                title = firstString(baseInfo, MAX_FALLBACK_TITLE_CODE_POINTS,
                        "title");
            }
            if (!hasText(title)) {
                title = firstString(paramV2, MAX_FALLBACK_TITLE_CODE_POINTS,
                        "aodTitle");
            }

            String body = firstString(root, MAX_FALLBACK_BODY_CODE_POINTS,
                    "content", "description");
            if (!hasText(body)) {
                body = firstString(baseInfo, MAX_FALLBACK_BODY_CODE_POINTS,
                        "content", "description", "subContent");
            }

            String url = firstString(root, MAX_FALLBACK_URL_CODE_POINTS,
                    "url", "intent_uri", "web_uri");
            if (!hasText(url)) {
                url = firstString(paramV2, MAX_FALLBACK_URL_CODE_POINTS,
                        "url", "intent_uri", "web_uri");
            }

            int progress = firstNonNegativeInt(root, PORTABLE_PROGRESS_MAX,
                    "progress");
            if (progress < 0) {
                progress = firstNonNegativeInt(progressInfo, PORTABLE_PROGRESS_MAX,
                        "progress");
            }

            int progressCount = firstNonNegativeInt(root, PORTABLE_PROGRESS_MAX,
                    "progressCount");
            if (progressCount < 0) {
                progressCount = firstNonNegativeInt(paramV2, PORTABLE_PROGRESS_MAX,
                        "progressCount");
            }

            Boolean updatable = firstBoolean(root, "updatable");
            if (updatable == null) {
                updatable = firstBoolean(paramV2, "updatable");
            }

            String sequence = firstScalarString(root, MAX_SEQUENCE_CODE_POINTS,
                    "sequence");
            if (!hasText(sequence)) {
                sequence = firstScalarString(paramV2, MAX_SEQUENCE_CODE_POINTS,
                        "sequence");
            }

            // Xiaomi's delivery templates expose the accent in more than one
            // documented location. Keep the value as a bounded string here;
            // the Android notification renderer validates it before use.
            String accentColor = firstString(progressInfo, 32,
                    "colorProgress", "colorProgressEnd");
            if (!hasText(accentColor)) {
                JsonObject paramIsland = object(paramV2, "param_island");
                accentColor = firstString(paramIsland, 32, "highlightColor");
            }
            if (!hasText(accentColor)) {
                accentColor = firstString(baseInfo, 32, "colorSubTitle");
            }
            if (!hasText(accentColor)) {
                accentColor = firstString(root, 32, "colorDesc", "colorDescDark");
            }

            return new PortableFocusData(title, body, url, sequence, scene, business,
                    progress, progressCount, Boolean.TRUE.equals(updatable), accentColor);
        } catch (Throwable ignored) {
            return PortableFocusData.EMPTY;
        }
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

    /**
     * Returns whether a bounded focus JSON payload references a picture alias.
     *
     * <p>HyperOS commonly stores the alias in {@code param_v2} several levels
     * below the root (and some producers use an array).  Looking only at the
     * top-level picture map therefore misses the application-icon request.
     * This traversal is deliberately bounded so malformed/deep payloads cannot
     * affect ordinary notification delivery.</p>
     */
    public static boolean referencesPictureAlias(String parameter, String alias) {
        if (alias == null || alias.isEmpty() || !isWellFormedParameter(parameter)) {
            return false;
        }
        try {
            return referencesPictureAlias(JsonParser.parseString(parameter), alias, 0);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean referencesPictureAlias(
            JsonElement element, String alias, int depth) {
        // A focus parameter is capped at 3 KiB, but a malicious sender can
        // still construct thousands of nested arrays.  Keep this optional
        // enhancement cheap and fail closed at a modest depth.
        if (element == null || depth > 64) {
            return false;
        }
        if (element.isJsonPrimitive()) {
            try {
                return element.getAsJsonPrimitive().isString()
                        && alias.equals(element.getAsString());
            } catch (Throwable ignored) {
                return false;
            }
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                if (referencesPictureAlias(child, alias, depth + 1)) {
                    return true;
                }
            }
            return false;
        }
        if (element.isJsonObject()) {
            for (java.util.Map.Entry<String, JsonElement> entry
                    : element.getAsJsonObject().entrySet()) {
                if (alias.equals(entry.getKey())
                        || referencesPictureAlias(entry.getValue(), alias, depth + 1)) {
                    return true;
                }
            }
        }
        return false;
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
        if (object == null) {
            return null;
        }
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            return null;
        }
        return sanitizeFallback(value.getAsString(), maxCodePoints);
    }

    private static JsonObject object(JsonObject parent, String name) {
        if (parent == null || name == null) {
            return null;
        }
        JsonElement value = parent.get(name);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static String firstString(
            JsonObject object, int maxCodePoints, String... names) {
        if (object == null || names == null) {
            return null;
        }
        for (String name : names) {
            String value = boundedString(object, name, maxCodePoints);
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static String firstScalarString(
            JsonObject object, int maxCodePoints, String... names) {
        if (object == null || names == null) {
            return null;
        }
        for (String name : names) {
            JsonElement value = object.get(name);
            if (value == null || !value.isJsonPrimitive()) {
                continue;
            }
            try {
                String result = sanitizeFallback(value.getAsString(), maxCodePoints);
                if (hasText(result)) {
                    return result;
                }
            } catch (Throwable ignored) {
                // Try the next documented alias.
            }
        }
        return null;
    }

    private static int firstNonNegativeInt(
            JsonObject object, int maximum, String... names) {
        if (object == null || names == null) {
            return -1;
        }
        for (String name : names) {
            JsonElement value = object.get(name);
            if (value == null || !value.isJsonPrimitive()) {
                continue;
            }
            try {
                int parsed = value.getAsInt();
                if (parsed >= 0) {
                    return Math.min(maximum, parsed);
                }
            } catch (Throwable ignored) {
                // Try the next documented alias.
            }
        }
        return -1;
    }

    private static Boolean firstBoolean(JsonObject object, String... names) {
        if (object == null || names == null) {
            return null;
        }
        for (String name : names) {
            JsonElement value = object.get(name);
            if (value == null || !value.isJsonPrimitive()) {
                continue;
            }
            try {
                String raw = value.getAsString();
                if ("true".equalsIgnoreCase(raw)) {
                    return Boolean.TRUE;
                }
                if ("false".equalsIgnoreCase(raw)) {
                    return Boolean.FALSE;
                }
            } catch (Throwable ignored) {
                // Try the next documented alias.
            }
        }
        return null;
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

    public static final class PortableFocusData {
        private static final PortableFocusData EMPTY = new PortableFocusData(
                null, null, null, null, null, null, -1, -1, false, null);

        private final String title;
        private final String body;
        private final String url;
        private final String sequence;
        private final String scene;
        private final String business;
        private final int progress;
        private final int progressCount;
        private final boolean updatable;
        private final String accentColor;

        private PortableFocusData(
                String title,
                String body,
                String url,
                String sequence,
                String scene,
                String business,
                int progress,
                int progressCount,
                boolean updatable,
                String accentColor) {
            this.title = title;
            this.body = body;
            this.url = url;
            this.sequence = sequence;
            this.scene = scene;
            this.business = business;
            this.progress = progress;
            this.progressCount = progressCount;
            this.updatable = updatable;
            this.accentColor = accentColor;
        }

        public String title() {
            return title;
        }

        public String body() {
            return body;
        }

        public String url() {
            return url;
        }

        public String sequence() {
            return sequence;
        }

        public String scene() {
            return scene;
        }

        public String business() {
            return business;
        }

        public int progress() {
            return progress;
        }

        public int progressCount() {
            return progressCount;
        }

        public boolean updatable() {
            return updatable;
        }

        /** Optional sender-provided accent color for a portable timeline. */
        public String accentColor() {
            return accentColor;
        }

        public boolean hasProgress() {
            return progress >= 0;
        }

        /**
         * Select the delivery renderer by protocol identity, never by sender package.
         * The business alias is accepted only when scene is absent so another explicit
         * scene cannot accidentally inherit the food-delivery presentation.
         */
        public boolean isFoodDeliveryTimeline() {
            return FOOD_DELIVERY_SCENE.equals(scene)
                    || (!hasText(scene) && FOOD_DELIVERY_BUSINESS.equals(business));
        }

        /** Maps observed delivery progress to one of the three portable stages. */
        public int stageIndex() {
            if (!hasProgress() || progress < PORTABLE_MERCHANT_STAGE_PROGRESS) {
                return -1;
            }
            if (progress < PORTABLE_COURIER_STAGE_PROGRESS) {
                return 0;
            }
            if (progress < PORTABLE_DELIVERED_STAGE_PROGRESS) {
                return 1;
            }
            return 2;
        }

        /** Discrete timeline position used to draw the three-node indicator. */
        public int timelineProgress() {
            return Math.max(0, stageIndex()) * 50;
        }
    }
}

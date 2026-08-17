package top.trumeet.common.utils;

import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CustomConfiguration {
    private static String Config(String name) {
        return "__mi_push_" + name;
    }

    private static final String SUB_TEXT = Config("sub_text");
    private static final String ROUND_LARGE_ICON = Config("round_large_icon");
    private static final String USE_MESSAGING_STYLE = Config("use_messaging_style");
    private static final String CONVERSATION_TITLE = Config("conversation_title");
    private static final String CONVERSATION_ID = Config("conversation_id");
    private static final String CONVERSATION_ICON = Config("conversation_icon");
    private static final String CONVERSATION_IMPORTANT = Config("conversation_important");
    private static final String CONVERSATION_SENDER = Config("conversation_sender");
    private static final String CONVERSATION_SENDER_ID = Config("conversation_sender_id");
    private static final String CONVERSATION_SENDER_ICON = Config("conversation_sender_icon");
    private static final String CONVERSATION_MESSAGE = Config("conversation_message");
    private static final String CLEAR_GROUP = Config("clear_group");
    private static final String BORROW_CHANNEL_ID = Config("borrow_channel_id");
    private static final String TEXT_ICON = Config("text_icon");

    private static final String NOTIFICATION_LARGE_ICON_URI = "notification_large_icon_uri";
    private static final String CHANNEL_ID = "channel_id";
    private static final String CHANNEL_NAME = "channel_name";
    private static final String CHANNEL_DESCRIPTION = "channel_description";
    private static final String CHANNEL_IMPORTANCE = "channel_importance";
    private static final String SOUND_URL = "sound_url";
    private static final String SOUND_URI = "sound_uri";
    private static final String JOBKEY = "jobkey";
    private static final String USE_CLICKED_ACTIVITY = "use_clicked_activity";
    private static final String NOTIFICATION_GROUP = "notification_group";
    private static final String NOTIFICATION_BIGPIC_URI = "notification_bigPic_uri";
    private static final String NOTIFICATION_SHOW_WHEN = "notification_show_when";
    private static final String NOTIFICATION_STYLE_TYPE = "notification_style_type";
    private static final String NOTIFICATION_BANNER_IMAGE_URI = "notification_banner_image_uri";
    private static final String NOTIFICATION_BANNER_ICON_URI = "notification_banner_icon_uri";
    private static final String NOTIFICATION_COLORFUL_BUTTON_TEXT = "notification_colorful_button_text";
    private static final String NOTIFICATION_COLORFUL_BUTTON_BG_COLOR = "notification_colorful_button_bg_color";
    private static final String NOTIFICATION_COLORFUL_BG_COLOR = "notification_colorful_bg_color";
    private static final String NOTIFICATION_COLORFUL_BG_IMAGE_URI = "notification_colorful_bg_image_uri";
    // Kept only for payloads produced by older MiPush Framework versions.
    private static final String NOTIFICATION_COLORFUL_BUTTON_BG_IMAGE_URI = "notification_colorful_button_bg_image_uri";
    private static final String NOTIFICATION_CUSTOM_SMALL_ICON_URI = "notification_custom_small_icon_uri";
    private static final String NOTIFICATION_SMALL_ICON_URI = "notification_small_icon_uri";
    private static final String NOTIFICATION_SMALL_ICON_COLOR = "notification_small_icon_color";
    private static final String IMAGE_DESCRIPTION = "img_describe";
    private static final String NOTIFICATION_TIMEOUT = "notification_timeout";
    private static final String NOTIFICATION_BACKGROUND_COLOR = "background_color";
    private static final String ENABLE_KEYGUARD = "enable_keyguard";
    private static final String ENABLE_FLOAT = "enable_float";
    private static final String NOTIFICATION_FOLD = "notification_fold";
    private static final String MIUI_FOLD_TIMEOUT = "miui.fold.timeout";
    private static final String FOCUS_PARAM = "miui.focus.param";
    private static final String FOCUS_PICTURE_PREFIX = "miui.focus.pic_";

    /** Limits published by Xiaomi for the focus-notification protocol. */
    public static final int FOCUS_PARAM_MAX_BYTES = 3072;
    public static final int FOCUS_PICTURE_MAX_COUNT = 10;
    public static final int FOCUS_PICTURE_MAX_BYTES = 100 * 1024;

    private Map<String, String> mExtra = new HashMap<>();

    public CustomConfiguration(@Nullable Map<String, String> extra) {
        if (extra != null) {
            mExtra = extra;
        }
    }

    public String subText(String defaultValue) {
        return get(SUB_TEXT, defaultValue);
    }

    public boolean roundLargeIcon(boolean defaultValue) {
        return get(ROUND_LARGE_ICON, defaultValue);
    }

    public boolean useMessagingStyle(boolean defaultValue) {
        return get(USE_MESSAGING_STYLE, defaultValue);
    }

    public String conversationTitle(String defaultValue) {
        return get(CONVERSATION_TITLE, defaultValue);
    }

    public String conversationId(String defaultValue) {
        return get(CONVERSATION_ID, defaultValue);
    }

    public String conversationIcon(String defaultValue) {
        return get(CONVERSATION_ICON, defaultValue);
    }

    public boolean conversationImportant(boolean defaultValue) {
        return get(CONVERSATION_IMPORTANT, defaultValue);
    }

    public String conversationSender(String defaultValue) {
        return get(CONVERSATION_SENDER, defaultValue);
    }

    public String conversationSenderId(String defaultValue) {
        return get(CONVERSATION_SENDER_ID, defaultValue);
    }

    public String conversationSenderIcon(String defaultValue) {
        return get(CONVERSATION_SENDER_ICON, defaultValue);
    }

    public String conversationMessage(String defaultValue) {
        return get(CONVERSATION_MESSAGE, defaultValue);
    }

    public String notificationLargeIconUri(String defaultValue) {
        return get(NOTIFICATION_LARGE_ICON_URI, defaultValue);
    }

    public String channelId(String defaultValue) {
        return get(CHANNEL_ID, defaultValue);
    }

    public String channelName(String defaultValue) {
        return get(CHANNEL_NAME, defaultValue);
    }

    public String channelDescription(String defaultValue) {
        return get(CHANNEL_DESCRIPTION, defaultValue);
    }

    public int channelImportance(int defaultValue) {
        String value = get(CHANNEL_IMPORTANCE, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            int importance = Integer.parseInt(value);
            return importance >= 0 && importance <= 5 ? importance : defaultValue;
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    /**
     * Xiaomi's current protocol uses {@code sound_uri}. Keep accepting the older
     * {@code sound_url} spelling so existing local configuration files do not break.
     */
    public String soundUri(String defaultValue) {
        return get(SOUND_URI, get(SOUND_URL, defaultValue));
    }

    public String soundUrl(String defaultValue) {
        return soundUri(defaultValue);
    }

    public String jobkey(String defaultValue) {
        return get(JOBKEY, defaultValue);
    }

    public boolean useClickedActivity(boolean defaultValue) {
        return get(USE_CLICKED_ACTIVITY, defaultValue);
    }

    public String notificationGroup(String defaultValue) {
        return get(NOTIFICATION_GROUP, defaultValue);
    }

    public String notificationBigPicUri(String defaultValue) {
        return get(NOTIFICATION_BIGPIC_URI, defaultValue);
    }

    public boolean notificationShowWhen(boolean defaultValue) {
        return getBooleanValue(NOTIFICATION_SHOW_WHEN, defaultValue);
    }

    public String notificationStyleType(String defaultValue) {
        return get(NOTIFICATION_STYLE_TYPE, defaultValue);
    }

    public NotificationStyle notificationStyle() {
        return NotificationStyle.fromProtocolValue(notificationStyleType(null));
    }

    public String notificationBannerImageUri(String defaultValue) {
        return get(NOTIFICATION_BANNER_IMAGE_URI, defaultValue);
    }

    public String notificationBannerIconUri(String defaultValue) {
        return get(NOTIFICATION_BANNER_ICON_URI, defaultValue);
    }

    public String notificationColorfulButtonText(String defaultValue) {
        return get(NOTIFICATION_COLORFUL_BUTTON_TEXT, defaultValue);
    }

    public String notificationColorfulButtonBackgroundColor(String defaultValue) {
        return get(NOTIFICATION_COLORFUL_BUTTON_BG_COLOR, defaultValue);
    }

    public String notificationColorfulBackgroundColor(String defaultValue) {
        return get(NOTIFICATION_COLORFUL_BG_COLOR, defaultValue);
    }

    /**
     * Xiaomi's published key wins whenever it is present. The button-background
     * image spelling was previously used by this project for the whole colorful
     * background and remains an absent-key fallback for compatible old payloads.
     */
    public String notificationColorfulBackgroundImageUri(String defaultValue) {
        return get(NOTIFICATION_COLORFUL_BG_IMAGE_URI,
                get(NOTIFICATION_COLORFUL_BUTTON_BG_IMAGE_URI, defaultValue));
    }

    public String notificationColorfulButtonBackgroundImageUri(String defaultValue) {
        return notificationColorfulBackgroundImageUri(defaultValue);
    }

    public enum NotificationStyle {
        DEFAULT,
        BIG_TEXT,
        BIG_PICTURE,
        COLORFUL,
        BANNER;

        public static NotificationStyle fromProtocolValue(@Nullable String value) {
            if ("1".equals(value)) return BIG_TEXT;
            if ("2".equals(value)) return BIG_PICTURE;
            // Official XMSF mapping: 3 is Colorful and 4 is Banner.
            if ("3".equals(value)) return COLORFUL;
            if ("4".equals(value)) return BANNER;
            return DEFAULT;
        }
    }

    public String notificationCustomSmallIconUri(String defaultValue) {
        return get(NOTIFICATION_CUSTOM_SMALL_ICON_URI, defaultValue);
    }

    public String notificationSmallIconUri(String defaultValue) {
        return get(NOTIFICATION_SMALL_ICON_URI, defaultValue);
    }

    public String notificationSmallIconColor(String defaultValue) {
        return get(NOTIFICATION_SMALL_ICON_COLOR, defaultValue);
    }

    public String imageDescription(String defaultValue) {
        return get(IMAGE_DESCRIPTION, defaultValue);
    }

    public int notificationTimeoutSeconds(int defaultValue) {
        return boundedInt(NOTIFICATION_TIMEOUT, defaultValue, 0, 7 * 24 * 60 * 60);
    }

    public String notificationBackgroundColor(String defaultValue) {
        return get(NOTIFICATION_BACKGROUND_COLOR, defaultValue);
    }

    public boolean enableKeyguard(boolean defaultValue) {
        return getBooleanValue(ENABLE_KEYGUARD, defaultValue);
    }

    public boolean enableFloat(boolean defaultValue) {
        return getBooleanValue(ENABLE_FLOAT, defaultValue);
    }

    public boolean notificationFold(boolean defaultValue) {
        return getBooleanValue(NOTIFICATION_FOLD, defaultValue);
    }

    public int miuiFoldTimeoutSeconds(int defaultValue) {
        return boundedInt(MIUI_FOLD_TIMEOUT, defaultValue, 0, 7 * 24 * 60 * 60);
    }

    public boolean clearGroup(boolean defaultValue) {
        return get(CLEAR_GROUP, defaultValue);
    }
    public String borrowChannelId(String defaultValue) {
        return get(BORROW_CHANNEL_ID, defaultValue);
    }

    public String focusParam(String defaultValue) {
        return get(FOCUS_PARAM, defaultValue);
    }

    /**
     * Parse the documented, public part of Xiaomi's focus-notification payload.
     * Picture values are forwarded exactly like official XMSF. Only this
     * process' optional native-Icon downloads apply URI safety filtering.
     */
    public FocusNotificationPayload focusNotificationPayload() {
        String parameter = focusParam(null);
        if (!FocusNotificationPayload.isParameterWithinLimit(parameter)) {
            parameter = null;
        }

        List<Map.Entry<String, String>> pictureEntries = new ArrayList<>();
        for (Map.Entry<String, String> entry : mExtra.entrySet()) {
            String key = entry.getKey();
            if (key != null && key.startsWith(FOCUS_PICTURE_PREFIX)) {
                pictureEntries.add(entry);
            }
        }
        pictureEntries.sort(Comparator.comparing(Map.Entry::getKey,
                CustomConfiguration::compareNaturally));

        Map<String, String> pictures = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : pictureEntries) {
            // The URL part of the protocol is forwarded in full. The native Icon
            // bundle is deliberately capped separately by downloadPictureUrls().
            pictures.put(entry.getKey(), entry.getValue());
        }
        return new FocusNotificationPayload(parameter, pictures);
    }

    /** Compare digit runs by numeric value so pic_2 sorts before pic_10. */
    private static int compareNaturally(String left, String right) {
        int leftIndex = 0;
        int rightIndex = 0;
        while (leftIndex < left.length() && rightIndex < right.length()) {
            char leftChar = left.charAt(leftIndex);
            char rightChar = right.charAt(rightIndex);
            if (Character.isDigit(leftChar) && Character.isDigit(rightChar)) {
                int leftEnd = leftIndex;
                int rightEnd = rightIndex;
                while (leftEnd < left.length() && Character.isDigit(left.charAt(leftEnd))) {
                    leftEnd++;
                }
                while (rightEnd < right.length() && Character.isDigit(right.charAt(rightEnd))) {
                    rightEnd++;
                }

                int leftSignificant = leftIndex;
                int rightSignificant = rightIndex;
                while (leftSignificant < leftEnd - 1 && left.charAt(leftSignificant) == '0') {
                    leftSignificant++;
                }
                while (rightSignificant < rightEnd - 1 && right.charAt(rightSignificant) == '0') {
                    rightSignificant++;
                }

                int lengthComparison = Integer.compare(
                        leftEnd - leftSignificant, rightEnd - rightSignificant);
                if (lengthComparison != 0) {
                    return lengthComparison;
                }
                for (int i = 0; i < leftEnd - leftSignificant; i++) {
                    int digitComparison = Character.compare(
                            left.charAt(leftSignificant + i),
                            right.charAt(rightSignificant + i));
                    if (digitComparison != 0) {
                        return digitComparison;
                    }
                }

                int zeroPaddingComparison = Integer.compare(
                        leftEnd - leftIndex, rightEnd - rightIndex);
                if (zeroPaddingComparison != 0) {
                    return zeroPaddingComparison;
                }
                leftIndex = leftEnd;
                rightIndex = rightEnd;
                continue;
            }

            int charComparison = Character.compare(leftChar, rightChar);
            if (charComparison != 0) {
                return charComparison;
            }
            leftIndex++;
            rightIndex++;
        }
        return Integer.compare(left.length() - leftIndex, right.length() - rightIndex);
    }

    private static boolean isSupportedPictureValue(@Nullable String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        int authorityStart;
        if (lower.startsWith("https://")) {
            authorityStart = 8;
        } else if (lower.startsWith("content://")) {
            // Official XMSF accepts content/resource URIs and lets the platform
            // resolver enforce the caller's grants. Do not accept file:// paths.
            authorityStart = 10;
        } else if (lower.startsWith("android.resource://")) {
            authorityStart = 19;
        } else {
            return false;
        }
        int authorityEnd = value.length();
        for (char delimiter : new char[]{'/', '?', '#'}) {
            int index = value.indexOf(delimiter, authorityStart);
            if (index >= 0 && index < authorityEnd) {
                authorityEnd = index;
            }
        }
        if (authorityEnd <= authorityStart) {
            return false;
        }
        String authority = value.substring(authorityStart, authorityEnd);
        // User-info and whitespace are unnecessary for network/resource values and
        // can make an apparently valid URI resolve somewhere unexpected.
        return authority.indexOf('@') < 0 && !containsAsciiWhitespace(authority);
    }

    private static boolean containsAsciiWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) <= ' ') {
                return true;
            }
        }
        return false;
    }

    public static final class FocusNotificationPayload {
        private final String parameter;
        private final Map<String, String> pictureUrls;

        private FocusNotificationPayload(@Nullable String parameter,
                                         Map<String, String> pictureUrls) {
            this.parameter = parameter;
            this.pictureUrls = Collections.unmodifiableMap(
                    new LinkedHashMap<>(pictureUrls));
        }

        @Nullable
        public String parameter() {
            return parameter;
        }

        public Map<String, String> pictureUrls() {
            return pictureUrls;
        }

        /** URLs selected for native Icon downloads; the URL payload remains complete. */
        public Map<String, String> downloadPictureUrls() {
            Map<String, String> result = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : pictureUrls.entrySet()) {
                if (result.size() >= FOCUS_PICTURE_MAX_COUNT) {
                    break;
                }
                if (isSupportedPictureValue(entry.getValue())) {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
            return Collections.unmodifiableMap(result);
        }

        public boolean isUsable() {
            return parameter != null || !pictureUrls.isEmpty();
        }

        public static boolean isSupportedProtocolVersion(int version) {
            // The official client enables the focus payload for every positive
            // protocol value.  Future protocol revisions must continue to receive
            // the URL payload instead of being silently downgraded to a normal
            // notification.
            return version > 0;
        }

        public static boolean isParameterWithinLimit(@Nullable String parameter) {
            return parameter != null
                    && parameter.getBytes(StandardCharsets.UTF_8).length
                    <= FOCUS_PARAM_MAX_BYTES;
        }

        public static boolean isPictureSizeAllowed(long downloadSize) {
            return downloadSize >= 0 && downloadSize <= FOCUS_PICTURE_MAX_BYTES;
        }
    }

    public String textIcon(String defaultValue) {
        return get(TEXT_ICON, defaultValue);
    }

    public boolean get(String key, boolean defaultValue) {
        if (getExtraField(mExtra, key, null) != null) {
            return true;
        }
        return defaultValue;
    }

    public boolean getBooleanValue(String key, boolean defaultValue) {
        String value = getExtraField(mExtra, key, null);
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }

    public String get(String key, String defaultValue) {
        return getExtraField(mExtra, key, defaultValue);
    }

    public Set<String> keys() {
        if (mExtra == null) {
            return new HashSet<>();
        }
        return mExtra.keySet();
    }

    private int boundedInt(String key, int defaultValue, int min, int max) {
        String value = get(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed >= min && parsed <= max ? parsed : defaultValue;
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static String getExtraField(Map<String, String> extra, String extraChannelName, String defaultValue) {
        return extra != null && extra.containsKey(extraChannelName) ?
                extra.get(extraChannelName) : defaultValue;
    }
}

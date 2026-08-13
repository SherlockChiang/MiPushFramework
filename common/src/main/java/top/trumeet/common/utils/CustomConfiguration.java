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
     * Invalid or over-limit data is left out instead of being forwarded to SystemUI.
     */
    public FocusNotificationPayload focusNotificationPayload() {
        String parameter = focusParam(null);
        if (!FocusNotificationPayload.isParameterWithinLimit(parameter)) {
            parameter = null;
        }

        List<Map.Entry<String, String>> pictureEntries = new ArrayList<>();
        for (Map.Entry<String, String> entry : mExtra.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key != null && key.startsWith(FOCUS_PICTURE_PREFIX) && isHttpsUrl(value)) {
                pictureEntries.add(entry);
            }
        }
        pictureEntries.sort(Comparator.comparing(Map.Entry::getKey,
                CustomConfiguration::compareNaturally));

        Map<String, String> pictures = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : pictureEntries) {
            if (pictures.size() >= FOCUS_PICTURE_MAX_COUNT) {
                break;
            }
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

    private static boolean isHttpsUrl(@Nullable String value) {
        if (value == null || !value.regionMatches(true, 0, "https://", 0, 8)) {
            return false;
        }
        int authorityStart = 8;
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
        // User-info and whitespace are unnecessary for CDN image URLs and can make
        // an apparently HTTPS value resolve somewhere unexpected.
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

        public boolean isUsable() {
            return parameter != null;
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

    private static String getExtraField(Map<String, String> extra, String extraChannelName, String defaultValue) {
        return extra != null && extra.containsKey(extraChannelName) ?
                extra.get(extraChannelName) : defaultValue;
    }
}

package top.trumeet.mipushframework.main.subpage;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nihility.Global;
import com.nihility.XMPushUtils;
import com.nihility.service.XMPushServiceAbility;
import com.nihility.utils.MockMIPushMessage;
import com.xiaomi.xmpush.thrift.XmPushActionCommandResult;
import com.xiaomi.xmpush.thrift.XmPushActionContainer;
import com.xiaomi.xmpush.thrift.XmPushActionNotification;
import com.xiaomi.xmsf.R;
import com.xiaomi.xmsf.push.notification.NotificationChannelManager;
import com.xiaomi.xmsf.push.notification.NotificationController;
import com.xiaomi.xmsf.push.utils.Configurations;
import com.xiaomi.xmsf.push.utils.RegSecUtils;
import com.xiaomi.xmsf.utils.ConvertUtils;
import com.xiaomi.push.service.XMPushService;

import org.apache.thrift.TBase;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import top.trumeet.common.utils.CustomConfiguration;
import top.trumeet.common.utils.Utils;
import top.trumeet.mipush.provider.db.EventDb;
import top.trumeet.mipush.provider.entities.Event;
import top.trumeet.mipushframework.main.ApplicationInfoPage;

public class EventListPageUtils {
    private final Context context;

    /**
     * Result of the explicit replay action exposed from an event's detail dialog.
     *
     * <p>Keeping this result separate from the notification pipeline is intentional: replay is a
     * user initiated diagnostic action and must never alter the normal receive/notify path.</p>
     */
    public enum ReplayStatus {
        /** The event is not a notification (for example, a registration or command record). */
        UNSUPPORTED_EVENT,
        /** The record has no payload, or its payload cannot be decoded. */
        INVALID_PAYLOAD,
        /** The push service is not alive, so there is no safe dispatcher to invoke. */
        SERVICE_UNAVAILABLE,
        /** The record passed pre-flight checks and can be replayed. */
        READY,
        /** The replay request was handed to the push message processor. */
        DISPATCHED,
        /** The processor rejected the replay request synchronously. */
        FAILED,
    }

    public EventListPageUtils(Context context) {
        this.context = context;
    }

    static List<Event> getEventsById(@Nullable Long lastId, int size, String packetName, String query) {
        Set<Integer> types = null;
        if (!Global.ConfigCenter().isShowAllEvents()) {
            types = Set.of(
                    Event.Type.SendMessage,
                    Event.Type.Registration,
                    Event.Type.RegistrationResult,
                    Event.Type.UnRegistration);
        }
        return EventDb.queryById(lastId, size, types, packetName, query);
    }

    static List<Event> getEvents(int pageIndex, int pageSize, String packetName, String query) {
        Set<Integer> types = null;
        if (!Global.ConfigCenter().isShowAllEvents()) {
            types = Set.of(
                    Event.Type.SendMessage,
                    Event.Type.Registration,
                    Event.Type.RegistrationResult,
                    Event.Type.UnRegistration);
        }
        return EventDb.queryByPage(pageIndex, pageSize,
                types, packetName, query);
    }

    public static void copyToClipboard(Context context, CharSequence info) {
        ClipboardManager clipboardManager = (ClipboardManager)
                context.getSystemService(Context.CLIPBOARD_SERVICE);
        clipboardManager.setText(info);
    }

    public static void mockMessage(XmPushActionContainer containerWithRegSec) {
        MockMIPushMessage.mockProcessMIPushMessage(
                XMPushServiceAbility.xmPushService, containerWithRegSec.deepCopy());
    }

    /**
     * Returns whether an event represents a message that can be shown again as a notification.
     * Registration, command and diagnostic records deliberately remain display-only.
     */
    public static boolean isReplayableEvent(@Nullable Event event) {
        if (event == null) {
            return false;
        }
        return event.getType() == Event.Type.SendMessage
                || event.getType() == Event.Type.Notification;
    }

    /**
     * Pure pre-flight check used by the UI and unit tests.  The service argument is supplied by
     * the caller so this method remains deterministic and does not read process-global state.
     */
    public static @NonNull ReplayStatus getReplayStatus(
            @Nullable Event event, boolean serviceAvailable) {
        if (!isReplayableEvent(event)) {
            return ReplayStatus.UNSUPPORTED_EVENT;
        }
        if (event.getPayload() == null || event.getPayload().length == 0) {
            return ReplayStatus.INVALID_PAYLOAD;
        }
        if (!serviceAvailable) {
            return ReplayStatus.SERVICE_UNAVAILABLE;
        }
        return ReplayStatus.READY;
    }

    /**
     * Replays one stored notification through the existing mock-message entry point.
     *
     * <p>This method is intentionally defensive because records from older database schemas may
     * contain a missing or malformed payload, and the service can stop while the dialog is open.
     * A failed replay is reported to the caller instead of crashing the Compose page.</p>
     */
    public static @NonNull ReplayStatus replayEvent(@Nullable Event event) {
        XMPushService service = XMPushServiceAbility.xmPushService;
        ReplayStatus preflight = getReplayStatus(event, service != null);
        if (preflight != ReplayStatus.READY) {
            return preflight;
        }

        try {
            XmPushActionContainer container = RegSecUtils.getContainerWithRegSec(event);
            if (container == null) {
                return ReplayStatus.INVALID_PAYLOAD;
            }
            MockMIPushMessage.mockProcessMIPushMessage(service, container.deepCopy());
            return ReplayStatus.DISPATCHED;
        } catch (Throwable ignored) {
            // Replay is an optional diagnostic action.  Never let a decoder or service race take
            // down the event details dialog.
            return ReplayStatus.FAILED;
        }
    }

    public static @NonNull String getContent(Event event, XmPushActionContainer containerWithRegSec) {
        try {
            XmPushActionContainer newContainer = containerWithRegSec.deepCopy();
            Configurations.getInstance().handle(event.getPkg(), newContainer);
            return containerToJson(newContainer, event.getRegSec()).toString();
        } catch (Throwable e) {
            e.printStackTrace();
            return e.toString();
        }
    }

    static @Nullable CharSequence getJson(Event event) {
        return containerToJson(event.getContainer(), event.getRegSec());
    }

    public Set<String> getStatus(@Nullable XmPushActionContainer container) {
        if (container == null) {
            return new HashSet<>();
        }
        Set<String> ops = configureContainer(container.deepCopy());
        if (isNotificationDisabled(container)) {
            ops.add("disable");
        }
        return ops;
    }

    protected boolean isNotificationDisabled(XmPushActionContainer container) {
        return !Utils.isAppInstalled(container.getPackageName()) ||
                !NotificationChannelManager.isNotificationChannelEnabled(
                        container.getPackageName(),
                        NotificationController.getExistsChannelId(context,
                                container.metaInfo, container.packageName));
    }

    private static Set<String> configureContainer(XmPushActionContainer container) {
        try {
            return Configurations.getInstance().handle(container.getPackageName(), container);
        } catch (Throwable ignored) {
            return new HashSet<>();
        }
    }

    public static CharSequence containerToJson(XmPushActionContainer container, String regSec) {
        Gson gson = new GsonBuilder()
                .disableHtmlEscaping()
                .setPrettyPrinting()
                .create();
        return gson.toJson(ConvertUtils.toJson(container, regSec));
    }

    public static void startManagePermissions(Context context, String packageName) {
        startManagePermissions(context, packageName, false);
    }

    public static void startManagePermissions(Context context, String packageName, boolean IGNORE_NOT_REGISTERED) {
        if (context == null || TextUtils.isEmpty(packageName)) {
            return;
        }
        Intent intent = new Intent(context, ApplicationInfoPage.class)
                .putExtra(ApplicationInfoPage.EXTRA_PACKAGE_NAME, packageName);
        if (IGNORE_NOT_REGISTERED) {
            intent.putExtra(ApplicationInfoPage.EXTRA_IGNORE_NOT_REGISTERED, true);
        }
        context.startActivity(intent);
    }

    public static String getDecoratedSummary(String summary, XmPushActionContainer container) {
        if (container.isSetPushAction()) {
            TBase data = getContainer(container);
            if (data instanceof XmPushActionNotification) {
                return summary + ": "
                        + ((XmPushActionNotification) data).getType();
            } else if (data instanceof XmPushActionCommandResult) {
                return summary + ": "
                        + ((XmPushActionCommandResult) data).getCmdName();
            }
        }
        return summary;
    }

    @Nullable
    public static TBase getContainer(XmPushActionContainer container) {
        try {
            return ConvertUtils.getResponseMessageBodyFromContainer(container, RegSecUtils.getRegSec(container));
        } catch (Exception ignored) {
            return null;
        }
    }

    @NonNull
    String getStatusDescription(@NonNull Event item) {
        switch (item.getResult()) {
            case Event.ResultType.OK:
                return getStatusDescriptionByEvent(item);
            case Event.ResultType.DENY_DISABLED:
                return context.getString(R.string.status_deny_disable);
            case Event.ResultType.DENY_USER:
                return context.getString(R.string.status_deny_user);
            default:
                return "";
        }
    }

    @NonNull
    private String getStatusDescriptionByEvent(@NonNull Event item) {
        XmPushActionContainer container = RegSecUtils.getContainerWithRegSec(item);
        if (container != null) {
            if (container.metaInfo.passThrough == 1) {
                return context.getString(R.string.message_type_pass_through);
            }
            if (container.metaInfo.passThrough == 0) {
                configureContainer(container);
                CustomConfiguration configuration = XMPushUtils.getConfiguration(container);
                return configuration.channelName(context.getString(R.string.message_type_notification));
            }
        }
        return "";
    }

}

package top.trumeet.common.utils;

import androidx.annotation.Nullable;

/**
 * Decides which notification renderer owns a focus payload.
 *
 * <p>MIUI/HyperOS SystemUI understands Xiaomi's private {@code miui.focus.*}
 * extras. Other ROMs do not, so forwarding those extras there only produces a
 * normal notification (or, on some vendor builds, an empty custom view). The
 * portable renderer is therefore deliberately selected outside Xiaomi's
 * SystemUI environment.</p>
 */
public final class DeviceFocusPolicy {
    public enum Renderer {
        /** Let Xiaomi/HyperOS SystemUI consume the private focus protocol. */
        SYSTEM,
        /** Render a visible Android notification using portable styles. */
        PORTABLE
    }

    private DeviceFocusPolicy() {
    }

    /**
     * Resolve the renderer from package/build signals without depending on an
     * Android {@code Context}; this keeps policy deterministic and testable.
     *
     * @param systemUiPackage package hosting the active status-bar/SystemUI
     * @param manufacturer build manufacturer (for Xiaomi vendor variants)
     * @param focusProtocolVersion value of {@code notification_focus_protocol}
     */
    public static Renderer rendererFor(
            @Nullable String systemUiPackage,
            @Nullable String manufacturer,
            int focusProtocolVersion) {
        if (focusProtocolVersion > 0
                // A package name such as miui.systemui.plugin is not sufficient
                // evidence on its own: a compatibility module can expose that
                // namespace on an AOSP device. Require the vendor identity and
                // a known Xiaomi SystemUI host together.
                && isXiaomiManufacturer(manufacturer)
                && isXiaomiPackage(systemUiPackage)) {
            return Renderer.SYSTEM;
        }
        return Renderer.PORTABLE;
    }

    public static boolean isXiaomiPackage(@Nullable String packageName) {
        return "com.android.systemui".equals(packageName)
                || "miui.systemui.plugin".equals(packageName)
                || "com.miui.aod".equals(packageName);
    }

    public static boolean isXiaomiManufacturer(@Nullable String manufacturer) {
        if (manufacturer == null) {
            return false;
        }
        String normalized = manufacturer.trim().toLowerCase(java.util.Locale.ROOT);
        return "xiaomi".equals(normalized)
                || "redmi".equals(normalized)
                || "blackshark".equals(normalized)
                || "poco".equals(normalized);
    }
}

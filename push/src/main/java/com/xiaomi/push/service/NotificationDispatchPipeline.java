package com.xiaomi.push.service;

import com.xiaomi.xmsf.push.utils.PackageConfig;

import java.util.Set;

/**
 * Keeps the independent notification dispatch phases isolated from one another.
 *
 * <p>This class deliberately has no Android dependencies.  Configuration
 * evaluation happens before the pipeline is entered; a failed evaluation is
 * represented by {@code null} and produces the safe default of delivering the
 * notification.  A failure in wake, notification submission, or open is
 * reported to the caller and cannot prevent a later phase from running.</p>
 */
final class NotificationDispatchPipeline {
    static final String STAGE_WAKE = "wake";
    static final String STAGE_NOTIFY = "notify";
    static final String STAGE_OPEN = "open";

    private NotificationDispatchPipeline() {
    }

    static DispatchPlan planFromOperations(Set<String> operations) {
        if (operations == null) {
            // Configuration evaluation failed.  Do not silently lose the
            // standard/focus notification; skip optional side effects.
            return DispatchPlan.notifyOnly();
        }
        try {
            return new DispatchPlan(
                    operations.contains(PackageConfig.OPERATION_WAKE),
                    !operations.contains(PackageConfig.OPERATION_IGNORE),
                    operations.contains(PackageConfig.OPERATION_OPEN));
        } catch (RuntimeException ignored) {
            // A malformed/custom Set must not turn a push into a lost
            // notification.  The conservative fallback is still notify-only.
            return DispatchPlan.notifyOnly();
        }
    }

    static void dispatch(
            DispatchPlan plan,
            Stage wake,
            Stage notify,
            Stage open,
            FailureHandler failureHandler) {
        DispatchPlan effectivePlan = plan == null ? DispatchPlan.notifyOnly() : plan;
        runSafely(effectivePlan.wake, STAGE_WAKE, wake, failureHandler);
        runSafely(effectivePlan.notify, STAGE_NOTIFY, notify, failureHandler);
        runSafely(effectivePlan.open, STAGE_OPEN, open, failureHandler);
    }

    private static void runSafely(
            boolean enabled,
            String stage,
            Stage action,
            FailureHandler failureHandler) {
        if (!enabled || action == null) {
            return;
        }
        try {
            action.run();
        } catch (Exception e) {
            // Failure reporting is best-effort too: a logger must not break
            // isolation and prevent the next dispatch phase.
            try {
                if (failureHandler != null) {
                    failureHandler.onFailure(stage, e);
                }
            } catch (Exception ignored) {
                // Intentionally ignored.
            }
        }
    }

    interface Stage {
        void run() throws Exception;
    }

    interface FailureHandler {
        void onFailure(String stage, Exception exception);
    }

    static final class DispatchPlan {
        final boolean wake;
        final boolean notify;
        final boolean open;

        DispatchPlan(boolean wake, boolean notify, boolean open) {
            this.wake = wake;
            this.notify = notify;
            this.open = open;
        }

        static DispatchPlan notifyOnly() {
            return new DispatchPlan(false, true, false);
        }
    }
}

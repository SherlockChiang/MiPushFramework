package top.trumeet.mipushframework.main.subpage;

import static top.trumeet.mipush.provider.db.RegisteredApplicationDb.registerApplication;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.elvishew.xlog.Logger;
import com.elvishew.xlog.XLog;
import com.github.promeg.pinyinhelper.Pinyin;
import com.nihility.Global;
import com.xiaomi.xmsf.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import top.trumeet.common.utils.ElapsedTimer;
import top.trumeet.common.utils.Utils;
import top.trumeet.mipush.provider.db.EventDb;
import top.trumeet.mipush.provider.db.RegistrationEvidenceResolver;
import top.trumeet.mipush.provider.db.RegisteredApplicationDb;
import top.trumeet.mipush.provider.entities.RegisteredApplication;
import top.trumeet.mipushframework.utils.MiPushManifestChecker;

public class ApplicationPageOperation {
    private static final Logger logger = XLog.tag(ApplicationPageOperation.class.getSimpleName()).build();
    
    static public MiPushApplications getMiPushApplications() {
        MiPushApplications miPushApplications = new MiPushApplications();
        logger.d("[loadApp] start load app list");
        ElapsedTimer timer = new ElapsedTimer();
        Map<String, RegisteredApplication> registeredPkgs = getRegisteredApplicationMap(miPushApplications);
        logger.d("[loadApp] get registeredPkgs ms: %d", timer.restart());

        final List<PackageInfo> packageInfos = getPackagesOnDevice();
        miPushApplications.totalPkg = packageInfos.size();
        logger.d("[loadApp] get package info ms: %d", timer.restart());

        mergeActiveRegistrationPackages(
                Utils.getApplication(), packageInfos, registeredPkgs);
        logger.d("[loadApp] merge active registrations ms: %d", timer.restart());

        removePackagesThatNotSupportMiPushServices(packageInfos, registeredPkgs);
        logger.d("[loadApp] filter not service package ms: %d", timer.restart());

        List<RegisteredApplication> res = convertToRegisteredApplicationList(packageInfos, registeredPkgs);
        miPushApplications.res = res;
        logger.d("[loadApp] convert to application list ms: %d", timer.restart());

        addApplicationNameIfMissing(res);
        logger.d("[loadApp] query name ms: %d", timer.restart());

        addApplicationPinYinName(res);
        logger.d("[loadApp] query pinyin ms: %d", timer.restart());

        addLastReceiveTimeInfo(res);
        logger.d("[loadApp] query lastReceiveTime ms: %d", timer.restart());
        return miPushApplications;
    }

    public static void addLastReceiveTimeInfo(List<RegisteredApplication> res) {
        for (RegisteredApplication application : res) {
            application.lastReceiveTime = new Date(EventDb.getLastReceiveTime(application.getPackageName()));
        }
    }

    public static void addApplicationPinYinName(List<RegisteredApplication> res) {
        for (RegisteredApplication application : res) {
            application.appNamePinYin = Pinyin.toPinyin(application.appName, "");
        }
    }

    public static void addApplicationNameIfMissing(List<RegisteredApplication> res) {
        for (RegisteredApplication application : res) {
            if (!TextUtils.isEmpty(application.appName)) {
                continue;
            }
            application.appName = Global.ApplicationNameCache()
                    .getAppName(Utils.getApplication(), application.getPackageName()).toString();
        }
    }

    public static @NonNull List<RegisteredApplication> convertToRegisteredApplicationList(List<PackageInfo> packageInfos, Map<String, RegisteredApplication> registeredPkgs) {
        MiPushManifestChecker checker = getMiPushManifestChecker();
        List<RegisteredApplication> res = new ArrayList<RegisteredApplication>();
        for (PackageInfo info : packageInfos) {
            RegisteredApplication application = getRegisteredApplication(info, registeredPkgs, checker);
            res.add(application);
        }
        return res;
    }

    public static @NonNull RegisteredApplication getRegisteredApplication(PackageInfo info, Map<String, RegisteredApplication> registeredPkgs, MiPushManifestChecker checker) {
        String currentAppPkgName = info.packageName;
        RegisteredApplication application;
        if (registeredPkgs.containsKey(currentAppPkgName)) {
            application = registeredPkgs.get(currentAppPkgName);
        } else {
            application = registerApplication(currentAppPkgName);
        }
        RegisteredApplication.ServiceProbeState probeState = probeMiPushServices(checker, info);
        application.setServiceProbeState(probeState);
        // Keep the legacy boolean populated for Java/Kotlin callers and older UI code.
        application.existServices = probeState == RegisteredApplication.ServiceProbeState.PRESENT;
        return application;
    }

    public static void removePackagesThatNotSupportMiPushServices(List<PackageInfo> packageInfos, Map<String, RegisteredApplication> registeredPkgs) {
        MiPushManifestChecker checker = getMiPushManifestChecker();
        for (final Iterator<PackageInfo> iterator = packageInfos.iterator(); iterator.hasNext(); ) {
            PackageInfo info = iterator.next();
            if (!shouldShowInList(info, registeredPkgs, checker)) {
                iterator.remove();
            }
        }
    }

    public static boolean shouldShowInList(PackageInfo info, Map<String, RegisteredApplication> registeredPkgs, MiPushManifestChecker checker) {
        return isApplicationInstalled(info) &&
                (isPackageStoredInDB(registeredPkgs, info) ||
                        probeMiPushServices(checker, info) == RegisteredApplication.ServiceProbeState.PRESENT);
    }

    /**
     * Probe the target application's MiPush SDK services without collapsing a probe failure into
     * "missing".  PackageManager may hide service metadata for system apps or a ROM may not expose
     * the checker implementation; both cases are UNKNOWN and should not be rendered as an error.
     */
    public static RegisteredApplication.ServiceProbeState probeMiPushServices(
            MiPushManifestChecker checker, PackageInfo info) {
        if (checker == null || info == null || info.services == null) {
            return RegisteredApplication.ServiceProbeState.UNKNOWN;
        }
        try {
            return mapServiceCheckResult(checker.checkServicesState(info));
        } catch (Throwable ignored) {
            // A checker implementation is loaded from the system push package and may fail on
            // vendor-specific metadata.  Preserve that distinction for the UI.
            return RegisteredApplication.ServiceProbeState.UNKNOWN;
        }
    }

    /** Pure mapping kept separate so probe-state behavior can be tested without Android services. */
    public static RegisteredApplication.ServiceProbeState mapServiceCheckResult(
            MiPushManifestChecker.ServiceCheckResult result) {
        if (result == null) {
            return RegisteredApplication.ServiceProbeState.UNKNOWN;
        }
        switch (result) {
            case PRESENT:
                return RegisteredApplication.ServiceProbeState.PRESENT;
            case MISSING:
                return RegisteredApplication.ServiceProbeState.MISSING;
            case UNKNOWN:
            default:
                return RegisteredApplication.ServiceProbeState.UNKNOWN;
        }
    }

    /** Legacy boolean API retained for callers that only need a positive capability check. */
    public static boolean hasMiPushServices(MiPushManifestChecker checker, PackageInfo info) {
        return probeMiPushServices(checker, info) == RegisteredApplication.ServiceProbeState.PRESENT;
    }

    public static boolean isPackageStoredInDB(Map<String, RegisteredApplication> registeredPkgs, PackageInfo info) {
        return registeredPkgs.containsKey(info.applicationInfo.packageName);
    }

    public static boolean isApplicationInstalled(PackageInfo info) {
        return (info.applicationInfo.flags & ApplicationInfo.FLAG_INSTALLED) != 0;
    }

    public static @NonNull List<PackageInfo> getPackagesOnDevice() {
        return Utils.getApplication().getPackageManager().getInstalledPackages(
                PackageManager.GET_DISABLED_COMPONENTS |
                        PackageManager.GET_SERVICES |
                        PackageManager.GET_RECEIVERS);
    }

    public static @Nullable MiPushManifestChecker getMiPushManifestChecker() {
        MiPushManifestChecker checker = null;
        try {
            checker = MiPushManifestChecker.create(Utils.getApplication());
        } catch (PackageManager.NameNotFoundException | ClassNotFoundException |
                 NoSuchMethodException e) {
            logger.e("Create mi push checker", e);
        }
        return checker;
    }

    public static Map<String, RegisteredApplication> getRegisteredApplicationMap(MiPushApplications miPushApplications) {
        Map<String, RegisteredApplication> registeredPkgs = miPushApplications.registeredPkgs;
        for (RegisteredApplication application : RegisteredApplicationDb.getList(null)) {
            registeredPkgs.put(application.getPackageName(), application);
        }
        return registeredPkgs;
    }

    /**
     * System-integrated and receiver-only clients may be present in the SDK's active registry
     * without declaring the two standard client services. Include those installed packages by
     * runtime capability, not by vendor or package-name lists.
     */
    static void mergeActiveRegistrationPackages(
            Context context,
            List<PackageInfo> packageInfos,
            Map<String, RegisteredApplication> registeredPkgs) {
        if (context == null || packageInfos == null || registeredPkgs == null) {
            return;
        }
        Set<String> installedPackages = new HashSet<>();
        for (PackageInfo packageInfo : packageInfos) {
            if (packageInfo != null && packageInfo.applicationInfo != null
                    && isApplicationInstalled(packageInfo)) {
                installedPackages.add(packageInfo.packageName);
            }
        }
        for (String packageName : getActiveRegistryPackages(context)) {
            if (installedPackages.contains(packageName)
                    && !registeredPkgs.containsKey(packageName)) {
                registeredPkgs.put(packageName, registerApplication(packageName));
            }
        }
    }

    /** Reads the SDK registry once; absence is deliberately not treated as unregistration. */
    static Set<String> getActiveRegistryPackages(Context context) {
        Set<String> packages = new HashSet<>();
        if (context == null) {
            return packages;
        }
        SharedPreferences registry =
                context.getSharedPreferences("pref_registered_pkg_names", Context.MODE_PRIVATE);
        for (Map.Entry<String, ?> entry : registry.getAll().entrySet()) {
            Object value = entry.getValue();
            if (!TextUtils.isEmpty(entry.getKey())
                    && value != null
                    && !TextUtils.isEmpty(value.toString())) {
                packages.add(entry.getKey());
            }
        }
        return packages;
    }

    /** Snapshot actual delivery times once so list refresh never scans notification history. */
    static Map<String, Long> getLastReceiveTimes(Context context) {
        Map<String, Long> receiveTimes = new HashMap<>();
        if (context == null) {
            return receiveTimes;
        }
        SharedPreferences preferences =
                context.getSharedPreferences("last_receive_time", Context.MODE_PRIVATE);
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            if (entry.getValue() instanceof Long) {
                receiveTimes.put(entry.getKey(), (Long) entry.getValue());
            }
        }
        return receiveTimes;
    }

    /** Read the persisted SDK state directly; the vendored runtime's cold-start cache is lossy. */
    static Set<String> getExplicitlyUnregisteredPackages(Context context) {
        if (context == null) {
            return new HashSet<>();
        }
        String persisted = context.getSharedPreferences(
                "mipush_app_info", Context.MODE_PRIVATE)
                .getString("unregistered_pkg_names", "");
        return RegistrationEvidenceResolver.parsePersistedPackageSet(persisted);
    }

    static void removeApplicationsThatQueryNotMatched(MiPushApplications miPushApplications, String query) {
        for (final Iterator<RegisteredApplication> iterator = miPushApplications.res.iterator(); iterator.hasNext(); ) {
            RegisteredApplication info = iterator.next();
            if (!isQueryMatched(info, query)) {
                iterator.remove();
            }
        }
    }

    static void sortApplicationsForDisplay(MiPushApplications miPushApplications) {
        Collections.sort(miPushApplications.res, (o1, o2) -> {
            if (o1.getId() == null && o2.getId() == null ||
                    o1.getRegisteredType() == RegisteredApplication.RegisteredType.NotRegistered &&
                            o2.getRegisteredType() == RegisteredApplication.RegisteredType.NotRegistered) {
                return o1.appNamePinYin.compareTo(o2.appNamePinYin);
            }

            if (o1.getId() == null) {
                return 1;
            }

            if (o2.getId() == null) {
                return -1;
            }

            if (o1.getRegisteredType() == RegisteredApplication.RegisteredType.NotRegistered) {
                return 1;
            }

            if (o2.getRegisteredType() == RegisteredApplication.RegisteredType.NotRegistered) {
                return -1;
            }

            if (o1.getRegisteredType() != o2.getRegisteredType()) {
                return o1.getRegisteredType() - o2.getRegisteredType();
            }
            int cmp = o2.lastReceiveTime.compareTo(o1.lastReceiveTime);
            if (cmp != 0) {
                return cmp;
            }
            return o1.appNamePinYin.compareTo(o2.appNamePinYin);
        });
    }

    private static boolean isQueryMatched(RegisteredApplication info, String query) {
        return info.getPackageName().toLowerCase().contains(query) ||
                info.appName.toLowerCase().contains(query) ||
                info.appNamePinYin.contains(query);
    }

    static MiPushApplications getMiPushApplicationsThatQueryMatched(String query) {
        ElapsedTimer totalTimer = new ElapsedTimer();
        MiPushApplications miPushApplications = getMiPushApplications();

        ElapsedTimer timer = new ElapsedTimer();
        removeApplicationsThatQueryNotMatched(miPushApplications, query);
        logger.d("[loadApp] filter app search ms: %d", timer.restart());

        sortApplicationsForDisplay(miPushApplications);
        logger.d("[loadApp] sort application list will show ms: %d", timer.restart());
        logger.d("[loadApp] end load app list ms: %d", totalTimer.elapsed());
        return miPushApplications;
    }

    static void updateRegisteredApplicationDb(Context context, List<RegisteredApplication> list) {
        ElapsedTimer totalTimer = new ElapsedTimer();
        ElapsedTimer timer = new ElapsedTimer();
        EventDb.RegistrationInfo registrationInfo = EventDb.queryRegistered();
        Set<String> activeRegistryPackages = getActiveRegistryPackages(context);
        Set<String> explicitlyUnregisteredPackages =
                getExplicitlyUnregisteredPackages(context);
        Map<String, Long> lastReceiveTimes = getLastReceiveTimes(context);
        logger.d("[updateApp] get registeredPkgsFromEvents ms: %d", timer.restart());

        for (RegisteredApplication application : list) {
            String pkg = application.getPackageName();
            application.appName = Global.ApplicationNameCache()
                    .getAppName(context, pkg).toString();
            long lastReceiveTime = lastReceiveTimes.containsKey(pkg)
                    ? lastReceiveTimes.get(pkg) : 0L;
            boolean newerReceiveEvidence = RegistrationEvidenceResolver
                    .isReceiveEvidenceNewer(
                            lastReceiveTime,
                            registrationInfo.latestControlEvidenceTime.get(pkg));
            application.setRegisteredType(RegistrationEvidenceResolver.resolve(
                    explicitlyUnregisteredPackages.contains(pkg),
                    activeRegistryPackages.contains(pkg),
                    registrationInfo.registered.contains(pkg),
                    newerReceiveEvidence,
                    !newerReceiveEvidence && registrationInfo.unregistered.contains(pkg)));
            RegisteredApplicationDb.update(application);
        }
        logger.d("[updateApp] update app ms: %d", timer.restart());
        logger.d("[updateApp] updated ms: %d", totalTimer.elapsed());
    }

    static @NonNull String getNotSupportHint(Context context, int notUseMiPushCount) {
        return context.getString(R.string.footer_app_ignored_not_registered, Integer.toString(notUseMiPushCount));
    }

    public static class MiPushApplications {
        public Map<String, RegisteredApplication> registeredPkgs = new HashMap<String, RegisteredApplication>();
        public List<RegisteredApplication> res = new ArrayList<RegisteredApplication>();
        public int totalPkg = 0;
    }
}

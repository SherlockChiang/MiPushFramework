package top.trumeet.common.cache;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;

import androidx.annotation.Nullable;
import androidx.collection.LruCache;

/**
 * @author zts
 */
public class ApplicationNameCache {
    private volatile static ApplicationNameCache cache = null;
    private final LruCache<String, CharSequence> cacheInstance;


    private ApplicationNameCache() {
        cacheInstance = new LruCache<>(100);
    }

    public CharSequence getAppName(final Context ctx, final String pkg) {

        return new AbstractCacheAspect<>(cacheInstance) {
            @Override
            CharSequence gen() {
                return appName();
            }

            private @Nullable CharSequence appName() {
                PackageManager pm = ctx.getPackageManager();
                try {
                    return pm.getApplicationInfo(pkg, PackageManager.GET_UNINSTALLED_PACKAGES).loadLabel(pm);
                } catch (PackageManager.NameNotFoundException |
                         Resources.NotFoundException ignored) {
                    return pkg;
                }
            }
        }.get(pkg);
    }

    /** Names are cheap to reload and may be discarded when the process is under memory pressure. */
    public void clearMemory() {
        cacheInstance.evictAll();
    }

    /**
     * Apply the process memory-pressure policy to this small, UI-oriented cache.
     * Names are inexpensive to resolve, so dropping them is preferable to keeping
     * stale objects alive while the process is backgrounded.
     */
    public void trimMemory(int level) {
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            clearMemory();
        }
    }


}

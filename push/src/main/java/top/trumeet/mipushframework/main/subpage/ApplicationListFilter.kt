package top.trumeet.mipushframework.main.subpage

import top.trumeet.mipush.provider.entities.RegisteredApplication

/**
 * User-facing application categories.  The category is derived only from the registration
 * result: a missing SDK service is diagnostic metadata and must not turn a genuinely registered
 * application into another category.
 */
enum class ApplicationFilter {
    All,
    Registered,
    Unregistered,
    NotRegistered,
}

fun filterApplicationsForDisplay(
    applications: List<RegisteredApplication>,
    filter: ApplicationFilter,
): List<RegisteredApplication> = when (filter) {
    ApplicationFilter.All -> applications
    ApplicationFilter.Registered -> applications.filter {
        it.registeredType == RegisteredApplication.RegisteredType.Registered
    }
    ApplicationFilter.Unregistered -> applications.filter {
        it.registeredType == RegisteredApplication.RegisteredType.Unregistered
    }
    ApplicationFilter.NotRegistered -> applications.filter {
        it.registeredType == RegisteredApplication.RegisteredType.NotRegistered
    }
}

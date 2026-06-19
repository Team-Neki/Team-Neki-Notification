rootProject.name = "team-neki-notification"

include(
    "apps:batch",
    "domain",
    "modules:postgresql",
    "modules:fcm",
    "modules:scheduling",
)

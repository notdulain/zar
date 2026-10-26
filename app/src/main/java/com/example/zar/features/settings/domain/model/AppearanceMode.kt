package com.example.zar.features.settings.domain.model

/** Which colours the app uses. Only [SYSTEM] follows Android; [LIGHT] and [DARK] ignore it. */
enum class AppearanceMode {
    LIGHT,
    DARK,
    SYSTEM,
    ;

    /** Whether dark colours apply, given whether Android is currently dark. */
    fun resolveDark(systemDark: Boolean): Boolean = when (this) {
        LIGHT -> false
        DARK -> true
        SYSTEM -> systemDark
    }

    companion object {
        /** What an installation with no valid saved choice uses. Android's own setting is never inferred. */
        val Default = LIGHT

        /** Reads a saved name; anything missing or unknown (for example from a newer version) is [Default]. */
        fun fromStored(name: String?): AppearanceMode = entries.firstOrNull { it.name == name } ?: Default
    }
}

package com.chromaticnoise.multiplatformswiftpackage.domain

enum class LibraryType(val value: String) {
    STATIC("static"),
    DYNAMIC("dynamic");

    override fun toString(): String = value

    companion object {
        internal fun of(type: String): Either<PluginConfiguration.PluginConfigurationError, LibraryType> =
            when (type.lowercase()) {
                "static" -> Either.Right(STATIC)
                "dynamic" -> Either.Right(DYNAMIC)
                else -> Either.Left(PluginConfiguration.PluginConfigurationError.InvalidLibraryType(type))
            }

        internal fun from(type: String?): LibraryType? = when(type?.lowercase()) {
            "static" -> STATIC
            "dynamic" -> DYNAMIC
            null -> null
            else -> throw IllegalArgumentException("Library type must be either 'static' or 'dynamic', got '$type'")
        }
    }
}
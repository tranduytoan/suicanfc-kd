package com.example.suicanfcreader.model

enum class AppThemeMode(val label: String) {
    AMOLED("AMOLED Black"),
    DARK("Tối"),
    WHITE("Sáng"),
    CUSTOM_IMAGE("Ảnh nền tùy chỉnh");

    companion object {
        fun fromName(name: String?): AppThemeMode {
            return entries.firstOrNull { it.name == name } ?: AMOLED
        }
    }
}

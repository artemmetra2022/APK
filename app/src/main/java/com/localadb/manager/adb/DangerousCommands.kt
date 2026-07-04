package com.localadb.manager.adb

/**
 * Грубая эвристика для предупреждения перед выполнением команд, которые могут удалить данные
 * или приложения. Не претендует на полноту — это подстраховка от опечаток, а не песочница
 * безопасности (терминал и так даёт полный доступ shell-пользователя).
 */
private val DANGEROUS_PATTERNS = listOf(
    Regex("""\brm\s+-[a-z]*r[a-z]*f?\b""", RegexOption.IGNORE_CASE), // rm -r, rm -rf, rm -fr...
    Regex("""\brm\s+-f\b""", RegexOption.IGNORE_CASE),
    Regex("""\bpm\s+uninstall\b""", RegexOption.IGNORE_CASE),
    Regex("""\bpm\s+clear\b""", RegexOption.IGNORE_CASE),
    Regex("""\bformat\b""", RegexOption.IGNORE_CASE),
    Regex("""\breboot\b""", RegexOption.IGNORE_CASE),
    Regex("""\bwipe\b""", RegexOption.IGNORE_CASE),
    Regex("""\bmkfs\b""", RegexOption.IGNORE_CASE),
    Regex("""\bdd\s+if="""),
    Regex("""factory[_-]?reset""", RegexOption.IGNORE_CASE),
    Regex("""MASTER_CLEAR"""),
)

fun isDangerousCommand(command: String): Boolean =
    DANGEROUS_PATTERNS.any { it.containsMatchIn(command) }

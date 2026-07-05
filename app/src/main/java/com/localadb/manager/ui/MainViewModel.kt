@@
     fun disablePackage(packageName: String) {
         viewModelScope.launch {
             _optimizationStatus.value = _optimizationStatus.value + (packageName to "Отключаю…")
             val result = repository.disablePackage(packageName)
             val status = result.fold(
                 onSuccess = { if (it.contains("Success", ignoreCase = true)) "Отключено ✅" else it.trim() },
                 onFailure = { "Ошибка: ${it.message}" },
             )
             _optimizationStatus.value = _optimizationStatus.value + (packageName to status)
-            if (status == "Отключено ✅") {
-                _disabledPackages.value = _disabledPackages.value + packageName
-            }
+            // Refresh disabled set from device to ensure UI reflects actual state
+            loadDisabledPackages()
         }
     }
@@
     fun enablePackage(packageName: String) {
         viewModelScope.launch {
             _optimizationStatus.value = _optimizationStatus.value + (packageName to "Включаю…")
             val result = repository.enablePackage(packageName)
             val status = result.fold(
                 onSuccess = { if (it.contains("installed", ignoreCase = true) || it.contains("Success", ignoreCase = true)) "Включено ✅" else it.trim() },
                 onFailure = { "Ошибка: ${it.message}" },
             )
             _optimizationStatus.value = _optimizationStatus.value + (packageName to status)
-            if (status == "Включено ✅") {
-                _disabledPackages.value = _disabledPackages.value - packageName
-            }
+            // Refresh disabled packages list to reflect actual device state
+            loadDisabledPackages()
         }
     }

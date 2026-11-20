package com.luisfelipe.aquaticks

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PrivacyManifestTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    @Test fun mergedAppDoesNotRequestInternetAccess() {
        val permissions = context.packageManager.getPackageInfo(context.packageName,
            PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
        assertFalse(permissions.contains(Manifest.permission.INTERNET))
    }
    @Test fun automaticBackupIsDisabled() {
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }
}

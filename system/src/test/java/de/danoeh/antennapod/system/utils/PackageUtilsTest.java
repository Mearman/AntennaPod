package de.danoeh.antennapod.system.utils;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageInfo;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class PackageUtilsTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = context.getPackageName();
        packageInfo.versionName = "3.4.5";
        shadowOf(context.getPackageManager()).installPackage(packageInfo);
    }

    private Context contextOfUnknownPackage() {
        return new ContextWrapper(context) {
            @Override
            public String getPackageName() {
                return "com.example.not.installed";
            }
        };
    }

    @Test
    public void packageInfoOfOwnPackageIsFound() {
        PackageInfo info = PackageUtils.getPackageInfo(context);
        assertEquals(context.getPackageName(), info.packageName);
        assertEquals("3.4.5", info.versionName);
    }

    @Test
    public void packageInfoOfUnknownPackageIsNull() {
        assertNull(PackageUtils.getPackageInfo(contextOfUnknownPackage()));
    }

    @Test
    public void applicationVersionIsVersionNameOfOwnPackage() {
        assertEquals("3.4.5", PackageUtils.getApplicationVersion(context));
    }

    @Test
    public void applicationVersionOfUnknownPackageFailsWithExplanation() {
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> PackageUtils.getApplicationVersion(contextOfUnknownPackage()));
        assertEquals("Call to getPackageInfo() returned Null.", exception.getMessage());
    }
}

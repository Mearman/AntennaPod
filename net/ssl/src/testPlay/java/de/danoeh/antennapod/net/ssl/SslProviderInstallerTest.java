package de.danoeh.antennapod.net.ssl;

import android.content.Context;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.security.ProviderInstaller;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@RunWith(RobolectricTestRunner.class)
public class SslProviderInstallerTest {
    private static final int RESOLVABLE_ERROR_CODE = 2;

    private MockedStatic<ProviderInstaller> providerInstaller;
    private MockedStatic<GoogleApiAvailability> googleApiAvailabilityStatic;
    private GoogleApiAvailability googleApiAvailability;
    private Context context;

    @Before
    public void setUp() {
        context = mock(Context.class);
        googleApiAvailability = mock(GoogleApiAvailability.class);
        providerInstaller = mockStatic(ProviderInstaller.class);
        googleApiAvailabilityStatic = mockStatic(GoogleApiAvailability.class);
        googleApiAvailabilityStatic.when(GoogleApiAvailability::getInstance).thenReturn(googleApiAvailability);
    }

    @After
    public void tearDown() {
        googleApiAvailabilityStatic.close();
        providerInstaller.close();
    }

    @Test
    public void testInstallAsksPlayServicesToInstallUpdatedProviderIfNeeded() {
        SslProviderInstaller.install(context);
        providerInstaller.verify(() -> ProviderInstaller.installIfNeeded(context));
        verify(googleApiAvailability, never()).showErrorNotification(any(), anyInt());
    }

    @Test
    public void testInstallShowsErrorNotificationWhenPlayServicesCanBeRepaired() {
        providerInstaller.when(() -> ProviderInstaller.installIfNeeded(context)).thenThrow(
                new GooglePlayServicesRepairableException(RESOLVABLE_ERROR_CODE, "repairable", null));
        SslProviderInstaller.install(context);
        verify(googleApiAvailability).showErrorNotification(context, RESOLVABLE_ERROR_CODE);
    }

    @Test
    public void testInstallDoesNotNotifyWhenPlayServicesAreNotAvailable() {
        providerInstaller.when(() -> ProviderInstaller.installIfNeeded(context)).thenThrow(
                new GooglePlayServicesNotAvailableException(RESOLVABLE_ERROR_CODE));
        SslProviderInstaller.install(context);
        verify(googleApiAvailability, never()).showErrorNotification(any(), anyInt());
    }

    @Test
    public void testInstallDoesNotPropagateUnexpectedFailures() {
        providerInstaller.when(() -> ProviderInstaller.installIfNeeded(context)).thenThrow(
                new IllegalStateException("provider installation failed"));
        SslProviderInstaller.install(context);
        verify(googleApiAvailability, never()).showErrorNotification(any(), anyInt());
    }
}

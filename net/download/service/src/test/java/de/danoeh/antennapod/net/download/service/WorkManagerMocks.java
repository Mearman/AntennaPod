package de.danoeh.antennapod.net.download.service;

import android.content.Context;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

public final class WorkManagerMocks implements AutoCloseable {
    private final MockedStatic<WorkManager> statics = Mockito.mockStatic(WorkManager.class);
    private final WorkManager manager = Mockito.mock(WorkManager.class);

    public WorkManagerMocks() {
        statics.when(() -> WorkManager.getInstance(any(Context.class))).thenReturn(manager);
    }

    public WorkManager manager() {
        return manager;
    }

    public OneTimeWorkRequest enqueuedUniqueWork(String name, ExistingWorkPolicy policy) {
        ArgumentCaptor<OneTimeWorkRequest> captor = ArgumentCaptor.forClass(OneTimeWorkRequest.class);
        Mockito.verify(manager).enqueueUniqueWork(eq(name), eq(policy), captor.capture());
        return captor.getValue();
    }

    public PeriodicWorkRequest enqueuedUniquePeriodicWork(String name, ExistingPeriodicWorkPolicy policy) {
        ArgumentCaptor<PeriodicWorkRequest> captor = ArgumentCaptor.forClass(PeriodicWorkRequest.class);
        Mockito.verify(manager).enqueueUniquePeriodicWork(eq(name), eq(policy), captor.capture());
        return captor.getValue();
    }

    @Override
    public void close() {
        statics.close();
    }
}

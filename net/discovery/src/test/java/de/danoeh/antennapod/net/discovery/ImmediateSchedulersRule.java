package de.danoeh.antennapod.net.discovery;

import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.junit.rules.ExternalResource;

final class ImmediateSchedulersRule extends ExternalResource {
    @Override
    protected void before() {
        RxJavaPlugins.setIoSchedulerHandler(scheduler -> Schedulers.trampoline());
        RxJavaPlugins.setComputationSchedulerHandler(scheduler -> Schedulers.trampoline());
        RxAndroidPlugins.setMainThreadSchedulerHandler(scheduler -> Schedulers.trampoline());
    }

    @Override
    protected void after() {
        RxJavaPlugins.reset();
        RxAndroidPlugins.reset();
    }
}

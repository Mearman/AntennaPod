package de.danoeh.antennapod.net.discovery;

import io.reactivex.rxjava3.core.Single;

import java.util.concurrent.atomic.AtomicReference;

final class Errors {
    private Errors() {
    }

    static Throwable errorOf(Single<?> single) {
        AtomicReference<Throwable> error = new AtomicReference<>();
        single.subscribe(value -> {
            throw new AssertionError("Expected an error but got " + value);
        }, error::set);
        if (error.get() == null) {
            throw new AssertionError("Expected an error but the Single did not fail");
        }
        return error.get();
    }
}

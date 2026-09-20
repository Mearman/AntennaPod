package de.danoeh.antennapod.net.sync.service;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;

public class InMemoryPreferencesContext extends ContextWrapper {
    private final Map<String, FakeSharedPreferences> preferences = new HashMap<>();

    public InMemoryPreferencesContext(Context base) {
        super(base);
    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        return preferences(name);
    }

    public FakeSharedPreferences preferences(String name) {
        return preferences.computeIfAbsent(name, key -> new FakeSharedPreferences());
    }

    @Override
    public Context getApplicationContext() {
        return this;
    }
}

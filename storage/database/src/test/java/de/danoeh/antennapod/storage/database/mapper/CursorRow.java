package de.danoeh.antennapod.storage.database.mapper;

import android.database.Cursor;
import android.database.MatrixCursor;

import java.util.LinkedHashMap;
import java.util.Map;

final class CursorRow {
    private final Map<String, Object> values = new LinkedHashMap<>();

    CursorRow with(String column, Object value) {
        values.put(column, value);
        return this;
    }

    Cursor build() {
        MatrixCursor cursor = new MatrixCursor(values.keySet().toArray(new String[0]));
        cursor.addRow(values.values().toArray());
        cursor.moveToFirst();
        return cursor;
    }
}

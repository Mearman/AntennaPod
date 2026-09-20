package de.danoeh.antennapod.system;

import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class CrashReportWriterTest {
    private File folderWithReport;
    private File folderWithoutReport;
    private MockedStatic<UserPreferences> userPreferences;

    @Before
    public void setUp() {
        folderWithReport = new File(getClass().getClassLoader().getResource("crash-report.log").getFile())
                .getParentFile();
        folderWithoutReport = new File(folderWithReport, "no-such-folder");
        userPreferences = Mockito.mockStatic(UserPreferences.class);
    }

    @After
    public void tearDown() {
        userPreferences.close();
    }

    private void useDataFolder(File folder) {
        userPreferences.when(() -> UserPreferences.getDataFolder(null)).thenReturn(folder);
    }

    @Test
    public void reportFileIsNamedCrashReportInDataFolder() {
        useDataFolder(folderWithReport);
        assertEquals(new File(folderWithReport, "crash-report.log"), CrashReportWriter.getFile());
    }

    @Test
    public void readReturnsContentOfExistingReport() {
        useDataFolder(folderWithReport);
        assertEquals("java.lang.IllegalStateException: boom\n\tat example.Example.run(Example.java:1)\n",
                CrashReportWriter.read());
    }

    @Test
    public void readReturnsEmptyStringWithoutReport() {
        useDataFolder(folderWithoutReport);
        assertEquals("", CrashReportWriter.read());
    }

    @Test
    public void timestampIsModificationTimeOfReport() {
        useDataFolder(folderWithReport);
        long modified = new File(folderWithReport, "crash-report.log").lastModified();
        assertEquals(new Date(modified), CrashReportWriter.getTimestamp());
    }

    @Test
    public void timestampIsNullWithoutReport() {
        useDataFolder(folderWithoutReport);
        assertNull(CrashReportWriter.getTimestamp());
    }

    @Test
    public void writingIntoUnavailableFolderCreatesNoReportAndDoesNotThrow() {
        useDataFolder(folderWithoutReport);
        CrashReportWriter.write(new IllegalStateException("boom"));
        assertFalse(CrashReportWriter.getFile().exists());
        assertEquals("", CrashReportWriter.read());
    }

    @Test
    public void writingStoresStackTraceInReportFileAndClosesIt() {
        useDataFolder(folderWithReport);
        List<List<?>> constructorArguments = new ArrayList<>();
        IllegalStateException exception = new IllegalStateException("boom");
        try (MockedConstruction<PrintWriter> writers = mockConstruction(PrintWriter.class,
                (writer, context) -> constructorArguments.add(context.arguments()))) {
            CrashReportWriter.write(exception);
            assertEquals(1, writers.constructed().size());
            PrintWriter writer = writers.constructed().get(0);
            verify(writer).println((Object) exception);
            verify(writer).close();
        }
        assertEquals(List.of(new File(folderWithReport, "crash-report.log"), "UTF-8"), constructorArguments.get(0));
    }

    @Test
    public void timestampIsNullWhenReportCannotBeAccessed() {
        useDataFolder(folderWithReport);
        try (MockedConstruction<File> files = mockConstruction(File.class,
                (file, context) -> when(file.exists()).thenThrow(new SecurityException("denied")))) {
            assertNull(CrashReportWriter.getTimestamp());
            assertFalse(files.constructed().isEmpty());
        }
    }

    @Test
    public void readReturnsEmptyStringWhenReportCannotBeAccessed() {
        useDataFolder(folderWithReport);
        try (MockedConstruction<File> files = mockConstruction(File.class,
                (file, context) -> when(file.exists()).thenThrow(new SecurityException("denied")))) {
            assertEquals("", CrashReportWriter.read());
            assertFalse(files.constructed().isEmpty());
        }
    }
}

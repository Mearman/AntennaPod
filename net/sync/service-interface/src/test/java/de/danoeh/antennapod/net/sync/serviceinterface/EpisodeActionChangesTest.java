package de.danoeh.antennapod.net.sync.serviceinterface;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class EpisodeActionChangesTest {
    @Test
    public void exposesActionsAndTimestamp() {
        EpisodeAction action = new EpisodeAction.Builder("podcast", "episode", EpisodeAction.DELETE).build();

        EpisodeActionChanges changes = new EpisodeActionChanges(Collections.singletonList(action), 99);

        assertEquals(1, changes.getEpisodeActions().size());
        assertSame(action, changes.getEpisodeActions().get(0));
        assertEquals(99, changes.getTimestamp());
    }

    @Test
    public void descriptionContainsActionsAndTimestamp() {
        EpisodeAction action = new EpisodeAction.Builder("podcast.example", "episode.example", EpisodeAction.DELETE)
                .build();

        String description = new EpisodeActionChanges(Collections.singletonList(action), 9876).toString();

        assertTrue(description.contains("episode.example"));
        assertTrue(description.contains("9876"));
    }
}

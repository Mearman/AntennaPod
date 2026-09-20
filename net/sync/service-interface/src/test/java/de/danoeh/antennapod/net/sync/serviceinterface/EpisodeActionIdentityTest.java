package de.danoeh.antennapod.net.sync.serviceinterface;

import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EpisodeActionIdentityTest {
    private static EpisodeAction.Builder builder() {
        return new EpisodeAction.Builder("podcast", "episode", EpisodeAction.PLAY)
                .timestamp(new Date(1609488000000L))
                .guid("guid")
                .started(1)
                .position(2)
                .total(3);
    }

    @Test
    public void actionsBuiltFromTheSameValuesHaveTheSameHashCode() {
        assertEquals(builder().build().hashCode(), builder().build().hashCode());
    }

    @Test
    public void hashCodeOfMinimalActionCanBeComputed() {
        EpisodeAction minimal = new EpisodeAction.Builder(null, null, null).build();

        assertEquals(minimal.hashCode(), new EpisodeAction.Builder(null, null, null).build().hashCode());
    }

    @Test
    public void actionIsEqualToItself() {
        EpisodeAction action = builder().build();

        assertTrue(action.equals(action));
    }

    @Test
    public void actionIsNotEqualToNullOrOtherTypes() {
        EpisodeAction action = builder().build();

        assertFalse(action.equals(null));
        assertFalse(action.equals("podcast"));
    }

    @Test
    public void descriptionContainsAllIdentifyingValues() {
        String description = builder().build().toString();

        assertTrue(description.contains("podcast='podcast'"));
        assertTrue(description.contains("episode='episode'"));
        assertTrue(description.contains("guid='guid'"));
        assertTrue(description.contains("action=PLAY"));
        assertTrue(description.contains("started=1"));
        assertTrue(description.contains("position=2"));
        assertTrue(description.contains("total=3"));
    }
}

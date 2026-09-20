package de.danoeh.antennapod.net.sync.service;

import androidx.core.util.Pair;
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class EpisodeActionFilterIgnoredActionsTest {
    private static final Date EARLIER = new Date(1609488000000L);
    private static final Date LATER = new Date(1609491600000L);

    private static EpisodeAction action(String episode, EpisodeAction.Action kind, Date timestamp) {
        return new EpisodeAction.Builder("podcast", episode, kind).timestamp(timestamp).build();
    }

    @Test
    public void remoteActionsThatAreNotPlaybackNeverOverrideLocalState() {
        List<EpisodeAction> remote = Arrays.asList(
                action("episode", EpisodeAction.NEW, LATER),
                action("episode", EpisodeAction.DOWNLOAD, LATER),
                action("episode", EpisodeAction.DELETE, LATER));

        Map<Pair<String, String>, EpisodeAction> result = EpisodeActionFilter
                .getRemoteActionsOverridingLocalActions(remote, Collections.emptyList());

        assertTrue(result.isEmpty());
    }

    @Test
    public void remotePlaybackIsKeptWhileOtherRemoteActionsForTheSameEpisodeAreIgnored() {
        EpisodeAction play = action("episode", EpisodeAction.PLAY, EARLIER);
        List<EpisodeAction> remote = Arrays.asList(play, action("episode", EpisodeAction.DELETE, LATER));

        Map<Pair<String, String>, EpisodeAction> result = EpisodeActionFilter
                .getRemoteActionsOverridingLocalActions(remote, Collections.emptyList());

        assertEquals(1, result.size());
        assertSame(play, result.get(new Pair<>("podcast", "episode")));
    }

    @Test
    public void localActionsOfOtherEpisodesDoNotBlockRemotePlayback() {
        EpisodeAction remotePlay = action("episode", EpisodeAction.PLAY, EARLIER);
        EpisodeAction otherLocalPlay = action("other-episode", EpisodeAction.PLAY, LATER);

        Map<Pair<String, String>, EpisodeAction> result = EpisodeActionFilter
                .getRemoteActionsOverridingLocalActions(Collections.singletonList(remotePlay),
                        Collections.singletonList(otherLocalPlay));

        assertSame(remotePlay, result.get(new Pair<>("podcast", "episode")));
    }

    @Test
    public void localActionsWithoutTimestampNeverBlockRemotePlayback() {
        EpisodeAction remotePlay = action("episode", EpisodeAction.PLAY, EARLIER);
        EpisodeAction localWithoutTimestamp = action("episode", EpisodeAction.PLAY, null);

        Map<Pair<String, String>, EpisodeAction> result = EpisodeActionFilter
                .getRemoteActionsOverridingLocalActions(Collections.singletonList(remotePlay),
                        Collections.singletonList(localWithoutTimestamp));

        assertSame(remotePlay, result.get(new Pair<>("podcast", "episode")));
    }

    @Test
    public void newestLocalActionDecidesWhetherRemotePlaybackWins() {
        EpisodeAction remotePlay = action("episode", EpisodeAction.PLAY, new Date(1609489800000L));
        List<EpisodeAction> local = Arrays.asList(
                action("episode", EpisodeAction.PLAY, LATER),
                action("episode", EpisodeAction.PLAY, EARLIER));

        Map<Pair<String, String>, EpisodeAction> result = EpisodeActionFilter
                .getRemoteActionsOverridingLocalActions(Collections.singletonList(remotePlay), local);

        assertTrue(result.isEmpty());
    }
}

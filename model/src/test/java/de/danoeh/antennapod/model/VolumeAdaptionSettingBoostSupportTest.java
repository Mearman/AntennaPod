package de.danoeh.antennapod.model;

import android.media.audiofx.AudioEffect;
import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowAudioEffect;

import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class VolumeAdaptionSettingBoostSupportTest {

    @Before
    public void setUp() {
        ShadowAudioEffect.reset();
        VolumeAdaptionSetting.setBoostSupported(null);
    }

    @After
    public void tearDown() {
        ShadowAudioEffect.reset();
        VolumeAdaptionSetting.setBoostSupported(null);
    }

    private static AudioEffect.Descriptor effectOfType(UUID type) {
        return new AudioEffect.Descriptor(type.toString(), UUID.randomUUID().toString(), "Insert", "Effect",
                "Implementor");
    }

    @Test
    public void isBoostSupported_noEffectsAvailable_returnsFalse() {
        assertFalse(VolumeAdaptionSetting.isBoostSupported());
    }

    @Test
    public void isBoostSupported_onlyUnrelatedEffectsAvailable_returnsFalse() {
        ShadowAudioEffect.addEffect(effectOfType(AudioEffect.EFFECT_TYPE_ENV_REVERB));
        assertFalse(VolumeAdaptionSetting.isBoostSupported());
    }

    @Test
    public void isBoostSupported_loudnessEnhancerAvailable_returnsTrue() {
        ShadowAudioEffect.addEffect(effectOfType(AudioEffect.EFFECT_TYPE_ENV_REVERB));
        ShadowAudioEffect.addEffect(effectOfType(AudioEffect.EFFECT_TYPE_LOUDNESS_ENHANCER));
        assertTrue(VolumeAdaptionSetting.isBoostSupported());
    }

    @Test
    public void isBoostSupported_resultIsCachedUntilReset() {
        ShadowAudioEffect.addEffect(effectOfType(AudioEffect.EFFECT_TYPE_LOUDNESS_ENHANCER));
        assertTrue(VolumeAdaptionSetting.isBoostSupported());

        ShadowAudioEffect.reset();
        assertTrue(VolumeAdaptionSetting.isBoostSupported());

        VolumeAdaptionSetting.setBoostSupported(null);
        assertFalse(VolumeAdaptionSetting.isBoostSupported());
    }
}

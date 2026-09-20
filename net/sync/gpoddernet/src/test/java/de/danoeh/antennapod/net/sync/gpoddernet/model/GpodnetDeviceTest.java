package de.danoeh.antennapod.net.sync.gpoddernet.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GpodnetDeviceTest {
    @Test
    public void knownDeviceTypesAreRecognised() {
        assertEquals(GpodnetDevice.DeviceType.DESKTOP, new GpodnetDevice("id", "c", "desktop", 0).getType());
        assertEquals(GpodnetDevice.DeviceType.LAPTOP, new GpodnetDevice("id", "c", "laptop", 0).getType());
        assertEquals(GpodnetDevice.DeviceType.MOBILE, new GpodnetDevice("id", "c", "mobile", 0).getType());
        assertEquals(GpodnetDevice.DeviceType.SERVER, new GpodnetDevice("id", "c", "server", 0).getType());
    }

    @Test
    public void unknownOrMissingDeviceTypeIsOther() {
        assertEquals(GpodnetDevice.DeviceType.OTHER, new GpodnetDevice("id", "c", "toaster", 0).getType());
        assertEquals(GpodnetDevice.DeviceType.OTHER, new GpodnetDevice("id", "c", null, 0).getType());
    }

    @Test
    public void deviceTypeNamesAreLowercaseForTheServerApi() {
        assertEquals("mobile", GpodnetDevice.DeviceType.MOBILE.toString());
        assertEquals("other", GpodnetDevice.DeviceType.OTHER.toString());
    }

    @Test
    public void deviceTypeNamesSurviveARoundTripThroughTheParser() {
        for (GpodnetDevice.DeviceType type : GpodnetDevice.DeviceType.values()) {
            assertEquals(type, new GpodnetDevice("id", "c", type.toString(), 0).getType());
        }
    }

    @Test
    public void deviceKeepsIdentityCaptionAndSubscriptionCount() {
        GpodnetDevice device = new GpodnetDevice("device-id", "My laptop", "laptop", 17);

        assertEquals("device-id", device.getId());
        assertEquals("My laptop", device.getCaption());
        assertEquals(17, device.getSubscriptions());
        assertTrue(device.toString().contains("device-id"));
        assertTrue(device.toString().contains("My laptop"));
    }
}

package test.top.trumeet.common.utils.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import top.trumeet.common.utils.DeviceFocusPolicy;

public class DeviceFocusPolicyTest {
    @Test
    public void hyperOsSystemUiUsesSystemRenderer() {
        assertEquals(DeviceFocusPolicy.Renderer.SYSTEM,
                DeviceFocusPolicy.rendererFor("miui.systemui.plugin", "Xiaomi", 3));
        assertEquals(DeviceFocusPolicy.Renderer.SYSTEM,
                DeviceFocusPolicy.rendererFor("com.android.systemui", "redmi", 1));
    }

    @Test
    public void nonXiaomiOrMissingProtocolUsesPortableRenderer() {
        assertEquals(DeviceFocusPolicy.Renderer.PORTABLE,
                DeviceFocusPolicy.rendererFor("com.android.systemui", "Google", 3));
        assertEquals(DeviceFocusPolicy.Renderer.PORTABLE,
                DeviceFocusPolicy.rendererFor("miui.systemui.plugin", "Google", 3));
        assertEquals(DeviceFocusPolicy.Renderer.PORTABLE,
                DeviceFocusPolicy.rendererFor("com.android.systemui", "Xiaomi", 0));
        assertEquals(DeviceFocusPolicy.Renderer.PORTABLE,
                DeviceFocusPolicy.rendererFor("com.google.android.systemui", "Xiaomi", 3));
        assertEquals(DeviceFocusPolicy.Renderer.PORTABLE,
                DeviceFocusPolicy.rendererFor(null, null, 3));
    }

    @Test
    public void packageAttributionRequiresXiaomiHardwareIdentity() {
        assertTrue(DeviceFocusPolicy.isXiaomiManufacturer("Xiaomi"));
        assertTrue(DeviceFocusPolicy.isXiaomiManufacturer("POCO"));
        assertFalse(DeviceFocusPolicy.isXiaomiManufacturer("Sony"));
        assertFalse(DeviceFocusPolicy.isXiaomiManufacturer("Google"));
        assertFalse(DeviceFocusPolicy.isXiaomiManufacturer(null));
    }
}

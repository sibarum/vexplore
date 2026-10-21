package dev.sibarum.vexplore.windows;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Only what is pure text; drives and folders are the machine's. */
class WindowsFilePlatformTest {

    private final WindowsFilePlatform platform = new WindowsFilePlatform();

    @Test
    void aNameLosesWhatWindowsForbids() {
        assertEquals("a_b_c_d_e", platform.safeName("a:b?c/d*e"));
        assertEquals("_.", platform.safeName("."));
    }
}

package com.example.fuel_split;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    @Test
    public void normalizeFriendCode_removesWhitespace() {
        assertEquals("AB12CD34", MainActivity.normalizeFriendCode(" AB12 CD34 \n"));
    }

    @Test
    public void normalizeFriendCode_handlesNull() {
        assertEquals("", MainActivity.normalizeFriendCode(null));
    }

    @Test
    public void shouldShowCopyToast_isFalseOnAndroid13AndAbove() {
        assertFalse(MainActivity.shouldShowCopyToast(android.os.Build.VERSION_CODES.TIRAMISU));
    }

    @Test
    public void shouldShowCopyToast_isTrueBeforeAndroid13() {
        assertTrue(MainActivity.shouldShowCopyToast(android.os.Build.VERSION_CODES.S_V2));
    }
}

package com.skt.nugu.sdk.platform.android;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    @Test
    public void addition_isCorrect() {
        for(int i = 0 ;  i < 5 ; i++) {
            System.out.println(((long) Math.pow(2, i) * 100L));
        }
        assertEquals(4, 2 + 2);
    }

}
package io.github.aw1y2z.sesame.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * {@link RandomUtil} 单测。
 * <p>重点覆盖两个曾经出过问题的边界：
 * <ul>
 *     <li>{@code nextInt(min, max)} 在 min &gt;= max 时的返回（历史上会抛 IllegalArgumentException）；</li>
 *     <li>{@code nextLong(min, max)} 取模为负导致结果小于 min（现在改用 floorMod）。</li>
 * </ul>
 */
public class RandomUtilTest {

    @Test
    public void nextInt_isWithinRangeAndHalfOpen() {
        for (int i = 0; i < 1000; i++) {
            int v = RandomUtil.nextInt(10, 20);
            assertTrue("值越界: " + v, v >= 10 && v < 20);
        }
    }

    @Test
    public void nextInt_degradesToMinWhenRangeInvalid() {
        assertEquals(5, RandomUtil.nextInt(5, 5));
        assertEquals(5, RandomUtil.nextInt(5, 1));
        assertEquals(-3, RandomUtil.nextInt(-3, -3));
    }

    @Test
    public void nextLong_neverBelowMin_evenWithNegativeRawValue() {
        for (int i = 0; i < 5000; i++) {
            long v = RandomUtil.nextLong(100, 200);
            assertTrue("nextLong 出现越界值: " + v, v >= 100 && v < 200);
        }
        assertEquals(7L, RandomUtil.nextLong(7, 7));
    }

    @Test
    public void delay_isWithinDocumentedJitterWindow() {
        for (int i = 0; i < 1000; i++) {
            int delay = RandomUtil.delay();
            assertTrue("delay 越界: " + delay, delay >= 100 && delay < 300);
        }
    }

    @Test
    public void randomStrings_haveRequestedLength() {
        assertEquals(4, RandomUtil.getRandom(4).length());
        assertTrue(RandomUtil.getRandom(4).matches("\\d{4}"));
        assertEquals(6, RandomUtil.getRandomString(6).length());
        assertTrue(RandomUtil.getRandomString(6).matches("[a-z0-9]{6}"));
        assertNotNull(RandomUtil.getRandomUUID());
        assertEquals(36, RandomUtil.getRandomUUID().length());
    }
}

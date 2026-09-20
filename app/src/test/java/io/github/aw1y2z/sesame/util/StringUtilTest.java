package io.github.aw1y2z.sesame.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/**
 * {@link StringUtil} 纯逻辑单测。
 * <p>这些方法被各 RpcCall 用来拼宿主请求体与写日志，拼错会直接导致整次调用失败，
 * 所以逐条覆盖边界（null / 空串 / 越界 / 非数字）。
 */
public class StringUtilTest {

    @Test
    public void isEmpty_onlyForNullAndEmpty() {
        assertTrue(StringUtil.isEmpty(null));
        assertTrue(StringUtil.isEmpty(""));
        assertFalse(StringUtil.isEmpty(" "));
        assertFalse(StringUtil.isEmpty("0"));
    }

    @Test
    public void escapeJson_escapesQuotesBackslashesAndControlChars() {
        assertEquals("", StringUtil.escapeJson(null));
        assertEquals("a\\\"b", StringUtil.escapeJson("a\"b"));
        assertEquals("a\\\\b", StringUtil.escapeJson("a\\b"));
        assertEquals("a\\nb", StringUtil.escapeJson("a\nb"));
        assertEquals("a\\rb", StringUtil.escapeJson("a\rb"));
        assertEquals("a\\tb", StringUtil.escapeJson("a\tb"));
        assertEquals("\\u0001", StringUtil.escapeJson("\u0001"));
        // 普通中文/emoji 不被改动
        assertEquals("芝麻粒🌱", StringUtil.escapeJson("芝麻粒🌱"));
    }

    @Test
    public void escapeJson_outputIsValidJsonStringBody() {
        // 拼进 JSON 后应当能被 Jackson 正常解析回原值
        String raw = "他说:\"今天\\明天\"\n第二行";
        String json = "{\"v\":\"" + StringUtil.escapeJson(raw) + "\"}";
        assertEquals("{\"v\":\"他说:\\\"今天\\\\明天\\\"\\n第二行\"}", json);
    }

    @Test
    public void truncate_keepsHeadAndReportsTotalLength() {
        assertEquals("", StringUtil.truncate(null, 5));
        assertEquals("abc", StringUtil.truncate("abc", 5));
        assertEquals("abcde", StringUtil.truncate("abcde", 5));
        assertEquals("abc…(共6字)", StringUtil.truncate("abcdef", 3));
        // maxLength <= 0 时原样返回，避免 substring 越界
        assertEquals("abcdef", StringUtil.truncate("abcdef", 0));
        assertEquals("abcdef", StringUtil.truncate("abcdef", -1));
    }

    @Test
    public void stripCountSuffix_removesTrailingCountOnly() {
        assertNull(StringUtil.stripCountSuffix(null));
        assertEquals("XX", StringUtil.stripCountSuffix("XX(2/10)"));
        assertEquals("XX", StringUtil.stripCountSuffix("XX"));
        // 后缀不在末尾时不处理
        assertEquals("XX(2/10)Y", StringUtil.stripCountSuffix("XX(2/10)Y"));
        assertEquals("XX(2)", StringUtil.stripCountSuffix("XX(2)"));
        // 结尾就是次数后缀（标题本身没有中文名）时会被整体剥掉
        assertEquals("", StringUtil.stripCountSuffix("(2/10)"));
    }

    @Test
    public void parseIntOrNull_isTotal() {
        assertNull(StringUtil.parseIntOrNull(null));
        assertNull(StringUtil.parseIntOrNull(""));
        assertNull(StringUtil.parseIntOrNull("abc"));
        assertNull(StringUtil.parseIntOrNull("999999999999"));
        assertEquals(Integer.valueOf(42), StringUtil.parseIntOrNull(" 42 "));
        assertEquals(Integer.valueOf(-7), StringUtil.parseIntOrNull("-7"));
    }

    @Test
    public void getSubString_betweenDelimiters() {
        assertEquals("d", StringUtil.getSubString("abc[d]e", "[", "]"));
        assertEquals("", StringUtil.getSubString("abc[d]e", "(", ")"));
        assertEquals("", StringUtil.getSubString("abc[d", "[", "]"));
        assertEquals("abcd", StringUtil.getSubString("abcd", "", ""));
    }

    @Test
    public void padding() {
        assertEquals("007", StringUtil.padLeft(7, 3, '0'));
        assertEquals("7  ", StringUtil.padRight(7, 3, ' '));
        assertEquals("ab", StringUtil.padLeft("abcdef".substring(0, 2), 2, '0'));
    }

    @Test
    public void joining() {
        assertEquals("", StringUtil.collectionJoinString(",", Collections.emptyList()));
        assertEquals("a,b", StringUtil.collectionJoinString(",", Arrays.asList("a", "b")));
        assertEquals("a|b", StringUtil.arrayJoinString("|", "a", "b"));
        assertEquals("a,b", StringUtil.arrayToString("a", "b"));
        // null 元素按空串拼接，不抛 NPE
        assertEquals("a,", StringUtil.arrayToString("a", null));
    }
}

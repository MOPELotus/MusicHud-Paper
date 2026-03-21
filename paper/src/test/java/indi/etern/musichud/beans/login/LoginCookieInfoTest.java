package indi.etern.musichud.beans.login;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class LoginCookieInfoTest {
    @Test
    void fromJsonReturnsUnloggedForNullOrBlankInput() {
        assertSame(LoginCookieInfo.UNLOGGED, LoginCookieInfo.fromJson(null));
        assertSame(LoginCookieInfo.UNLOGGED, LoginCookieInfo.fromJson(""));
        assertSame(LoginCookieInfo.UNLOGGED, LoginCookieInfo.fromJson("   "));
    }

    @Test
    void fromJsonReturnsUnloggedForJsonNull() {
        assertSame(LoginCookieInfo.UNLOGGED, LoginCookieInfo.fromJson("null"));
    }

    @Test
    void fromJsonParsesValidCookiePayload() {
        LoginCookieInfo parsed = LoginCookieInfo.fromJson("""
                {"type":"ANONYMOUS","rawCookie":"abc=123","generateTime":"2026-03-21T12:34:56+08:00[Asia/Shanghai]"}
                """);

        assertEquals(LoginType.ANONYMOUS, parsed.type());
        assertEquals("abc=123", parsed.rawCookie());
        assertEquals("2026-03-21T12:34:56+08:00[Asia/Shanghai]", parsed.generateTime().toString());
    }
}

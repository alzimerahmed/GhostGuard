package app.ghostguard.ui.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserActivitySanitizeUrlTest {
    @Test
    fun `https url passes through`() {
        assertEquals("https://example.com", BrowserActivity.sanitizeUrl("https://example.com"))
    }

    @Test
    fun `http url passes through`() {
        assertEquals("http://example.com", BrowserActivity.sanitizeUrl("http://example.com"))
    }

    @Test
    fun `file scheme is rejected`() {
        assertEquals("https://m.youtube.com", BrowserActivity.sanitizeUrl("file:///data/data/app.ghostguard/databases/x"))
    }

    @Test
    fun `javascript scheme is rejected`() {
        assertEquals("https://m.youtube.com", BrowserActivity.sanitizeUrl("javascript:alert(1)"))
    }

    @Test
    fun `content scheme is rejected`() {
        assertEquals("https://m.youtube.com", BrowserActivity.sanitizeUrl("content://media/x"))
    }

    @Test
    fun `custom app scheme is rejected`() {
        assertEquals("https://m.youtube.com", BrowserActivity.sanitizeUrl("snssdk1233://feed"))
    }

    @Test
    fun `uppercase scheme is normalized to lowercase`() {
        assertEquals("https://EXAMPLE.COM", BrowserActivity.sanitizeUrl("HTTPS://EXAMPLE.COM"))
    }

    @Test
    fun `bare host without scheme is rejected`() {
        assertEquals("https://m.youtube.com", BrowserActivity.sanitizeUrl("example.com"))
        assertEquals("https://m.youtube.com", BrowserActivity.sanitizeUrl("www.example.com/path"))
    }

    @Test
    fun `malformed url is rejected`() {
        assertEquals("https://m.youtube.com", BrowserActivity.sanitizeUrl("http://"))
    }
}

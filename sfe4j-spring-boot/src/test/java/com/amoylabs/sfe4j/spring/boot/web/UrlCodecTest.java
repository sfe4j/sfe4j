package com.amoylabs.sfe4j.spring.boot.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlCodecTest {

    @Test
    void unreservedCharactersAreKept() {
        assertEquals("aZ09-_.~", UrlCodec.encodeQueryValue("aZ09-_.~"));
    }

    @Test
    void separatorsAndReservedCharactersAreEncoded() {
        assertEquals("%2Fa%2Fb", UrlCodec.encodeQueryValue("/a/b"));
        assertEquals("we%20ird%26100%25%23dir", UrlCodec.encodeQueryValue("we ird&100%#dir"));
        assertEquals("a%2Bb", UrlCodec.encodeQueryValue("a+b"));
    }

    @Test
    void nonAsciiCharactersAreEncodedAsUtf8() {
        assertEquals("%E4%B8%AD%E6%96%87", UrlCodec.encodeQueryValue("中文"));
    }

    @Test
    void nullAndEmptyValuesAreSafe() {
        assertEquals("", UrlCodec.encodeQueryValue(null));
        assertEquals("", UrlCodec.encodeQueryValue(""));
    }

    @Test
    void linkBuilderKeepsTheContextPath() {
        LinkBuilder links = new LinkBuilder("/app");

        assertEquals("/app/file-explorer", links.entry());
        assertEquals("/app/file-explorer", links.listing(null));
        assertEquals("/app/file-explorer?dir=%2Fdata%2Fmy%20dir", links.listing("/data/my dir"));
        assertEquals("/app/file-viewer?file=%2Fdata%2Fa.txt", links.preview("/data/a.txt"));
        assertEquals("/app/file-downloader?file=%2Fdata%2Fa.txt", links.download("/data/a.txt"));
        assertEquals("/app/file-explorer/logout?dir=%2Fdata", links.logout("/data"));
    }

    @Test
    void linkBuilderWithoutContextPathProducesRootRelativeLinks() {
        LinkBuilder links = new LinkBuilder("");

        assertTrue(links.listing("/data").startsWith("/file-explorer?dir="));
    }
}

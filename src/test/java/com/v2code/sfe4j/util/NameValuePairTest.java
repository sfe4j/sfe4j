package com.v2code.sfe4j.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NameValuePairTest {

    @Test
    public void test() {
        NameValuePair nameValuePair = new NameValuePair("name", "value");
        assertEquals("name", nameValuePair.getName());
        assertEquals("value", nameValuePair.getValue());
    }
}

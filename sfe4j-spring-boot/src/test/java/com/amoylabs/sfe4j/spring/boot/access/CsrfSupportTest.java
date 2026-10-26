package com.amoylabs.sfe4j.spring.boot.access;

import com.amoylabs.sfe4j.core.exception.FileAccessErrorCode;
import com.amoylabs.sfe4j.core.exception.FileAccessException;
import com.amoylabs.sfe4j.spring.boot.HostCsrfToken;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsrfSupportTest {

    private final CsrfSupport csrfSupport = new CsrfSupport();

    @Test
    void sessionTokenIsIssuedAndVerified() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        String token = csrfSupport.token(request);
        assertTrue(token != null && token.length() >= 32);

        request.setParameter(CsrfSupport.PARAMETER_NAME, token);
        csrfSupport.verify(request);

        assertEquals(token, csrfSupport.token(request), "the token stays stable for the session");
        assertFalse(csrfSupport.isHostManaged(request));
    }

    @Test
    void wrongTokenIsRejected() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        csrfSupport.token(request);
        request.setParameter(CsrfSupport.PARAMETER_NAME, "forged");

        FileAccessException exception = assertThrows(FileAccessException.class, () -> csrfSupport.verify(request));
        assertEquals(FileAccessErrorCode.INVALID_REQUEST, exception.getCode());
    }

    @Test
    void missingSessionIsNeverTreatedAsValid() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(CsrfSupport.PARAMETER_NAME, "anything");

        assertThrows(FileAccessException.class, () -> csrfSupport.verify(request));
    }

    @Test
    void rotationInvalidatesThePreviousToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String token = csrfSupport.token(request);

        csrfSupport.rotate(request);
        String rotated = csrfSupport.token(request);
        assertNotEquals(token, rotated);

        request.setParameter(CsrfSupport.PARAMETER_NAME, token);
        assertThrows(FileAccessException.class, () -> csrfSupport.verify(request));

        request.setParameter(CsrfSupport.PARAMETER_NAME, rotated);
        csrfSupport.verify(request);
    }

    @Test
    void hostTokenIsPreferredWhenAvailable() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("_csrf", new HostCsrfToken("host-token"));

        assertTrue(csrfSupport.isHostManaged(request));
        assertEquals("host-token", csrfSupport.token(request));
        csrfSupport.verify(request);
    }
}

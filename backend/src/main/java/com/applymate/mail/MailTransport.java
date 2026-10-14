package com.applymate.mail;

/** Delivers one email. The SMTP implementation is swapped for a fake in tests. */
public interface MailTransport {

    /** Whether email is configured; when false, nothing is queued or sent. */
    boolean available();

    void send(String to, String subject, String body) throws Exception;
}

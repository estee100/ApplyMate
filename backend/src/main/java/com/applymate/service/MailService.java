package com.applymate.service;

import com.applymate.mail.EmailOutbox;
import com.applymate.mail.MailTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Outbound email. Messages go through the {@link EmailOutbox}: queued in the caller's
 * transaction and delivered by a worker with retries, so a request never waits on
 * SMTP and the forgot-password endpoint takes the same time whether or not an email
 * goes out.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final MailTransport transport;
    private final EmailOutbox outbox;
    private final boolean logLinks;

    public MailService(MailTransport transport, EmailOutbox outbox,
                       @Value("${app.mail.log-links-when-disabled:false}") boolean logLinks) {
        this.transport = transport;
        this.outbox = outbox;
        this.logLinks = logLinks;
        if (!transport.available()) {
            log.warn("Email is disabled (MAIL_ENABLED=false): password reset and reminder emails will not be sent.");
        }
    }

    public boolean enabled() {
        return transport.available();
    }

    /**
     * @param devLink a link to print to the log when email is off and link logging is on
     *                (local development only; never enable it in production)
     */
    public void send(String to, String subject, String body, String devLink) {
        send(to, subject, body, devLink, null);
    }

    /** As {@link #send(String, String, String, String)}, dropping duplicates of {@code dedupeKey}. */
    public boolean send(String to, String subject, String body, String devLink, String dedupeKey) {
        if (!enabled()) {
            if (logLinks && devLink != null) log.info("Email disabled; link for {}: {}", to, devLink);
            return false;
        }
        return outbox.enqueue(to, subject, body, dedupeKey);
    }
}

package com.applymate.service;

import com.applymate.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Outbound email. Sends on a background thread so a request never waits on SMTP, and
 * so the forgot-password endpoint takes the same time whether or not an email goes out.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final ObjectProvider<JavaMailSender> sender;
    private final AppProperties.Mail config;
    private final boolean logLinks;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public MailService(ObjectProvider<JavaMailSender> sender, AppProperties properties,
                       @Value("${app.mail.log-links-when-disabled:false}") boolean logLinks) {
        this.sender = sender;
        this.config = properties.mail();
        this.logLinks = logLinks;
        if (!config.enabled()) {
            log.warn("Email is disabled (MAIL_ENABLED=false): password reset and reminder emails will not be sent.");
        }
    }

    public boolean enabled() {
        return config.enabled() && sender.getIfAvailable() != null;
    }

    /**
     * @param devLink a link to print to the log when email is off and link logging is on
     *                (local development only; never enable it in production)
     */
    public void send(String to, String subject, String body, String devLink) {
        if (!enabled()) {
            if (logLinks && devLink != null) log.info("Email disabled; link for {}: {}", to, devLink);
            return;
        }
        executor.submit(() -> {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(config.from());
                message.setTo(to);
                message.setSubject(subject);
                message.setText(body);
                sender.getObject().send(message);
            } catch (Exception e) {
                // Never log the body: it may hold a reset link.
                log.error("Failed to send email '{}': {}", subject, e.getClass().getSimpleName());
            }
        });
    }
}

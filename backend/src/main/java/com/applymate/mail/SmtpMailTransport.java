package com.applymate.mail;

import com.applymate.config.AppProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpMailTransport implements MailTransport {

    private final ObjectProvider<JavaMailSender> sender;
    private final AppProperties.Mail config;

    public SmtpMailTransport(ObjectProvider<JavaMailSender> sender, AppProperties properties) {
        this.sender = sender;
        this.config = properties.mail();
    }

    @Override
    public boolean available() {
        return config.enabled() && sender.getIfAvailable() != null;
    }

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(config.from());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        sender.getObject().send(message);
    }
}

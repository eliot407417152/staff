package com.staff.demo.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 寄信。若沒有設定 spring.mail.host，信件內容只會印在 Console（方便本機測試）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MailService {

    private final ObjectProvider<JavaMailSender> senderProvider;

    @Value("${app.mail.from:}")
    private String from;

    /** 寄信失敗時會丟出例外 */
    public void send(String to, String subject, String text) {
        JavaMailSender sender = senderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("尚未設定 SMTP，郵件不會真的寄出，內容如下。\n收件人：{}\n主旨：{}\n{}", to, subject, text);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        if (from != null && !from.isBlank()) {
            message.setFrom(from);
        }
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        sender.send(message);
    }

    /** 寄信失敗只記錄、不影響主流程（用於通知類信件） */
    public void sendQuietly(String to, String subject, String text) {
        try {
            send(to, subject, text);
        } catch (Exception ex) {
            log.error("寄信失敗：{}", to, ex);
        }
    }
}

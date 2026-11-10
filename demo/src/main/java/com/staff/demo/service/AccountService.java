package com.staff.demo.service;

import com.staff.demo.dto.PasswordChangeForm;
import com.staff.demo.entity.Employee;
import com.staff.demo.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** 忘記密碼與變更密碼 */
@Service
@RequiredArgsConstructor
public class AccountService {

    // 排除容易看錯的字元（0 O 1 l I）
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final int PASSWORD_LENGTH = 12;
    private static final Duration RESET_COOLDOWN = Duration.ofMinutes(10);

    private final SecureRandom random = new SecureRandom();

    private final EmployeeRepository employeeRepo;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;

    /**
     * 忘記密碼：帳號與信箱都吻合才會產生新密碼並寄出。
     * 不吻合、帳號停用、冷卻時間內都「靜默略過」，讓外人無法藉此探測哪些帳號存在。
     * 寄信失敗會丟出例外並復原（密碼不會被改掉）。
     */
    @Transactional
    public void resetPassword(String username, String email) {
        Optional<Employee> found = employeeRepo.findByUsername(username == null ? "" : username.trim());
        if (found.isEmpty()) {
            return;
        }
        Employee e = found.get();
        if (!e.isEnabled() || e.getEmail() == null || email == null
                || !e.getEmail().equalsIgnoreCase(email.trim())) {
            return;
        }
        if (e.getPasswordResetAt() != null
                && e.getPasswordResetAt().plus(RESET_COOLDOWN).isAfter(LocalDateTime.now())) {
            return;
        }
        String raw = generatePassword();
        e.setPassword(passwordEncoder.encode(raw));
        e.setMustChangePassword(true);
        e.setPasswordResetAt(LocalDateTime.now());
        employeeRepo.save(e);

        mailService.send(e.getEmail(), "【員工系統】你的新密碼",
                e.getName() + " 您好，\n\n"
                        + "系統收到重設密碼的申請，以下是產生的臨時密碼：\n\n"
                        + "    " + raw + "\n\n"
                        + "請用這組密碼登入，並立即到「個人資料」變更成自己的密碼。\n"
                        + "如果這不是你本人的操作，請盡快聯絡管理員。");
    }

    /** 員工自行變更密碼 */
    @Transactional
    public void changePassword(String username, PasswordChangeForm f) {
        if (!f.getNewPassword().equals(f.getConfirmPassword())) {
            throw new IllegalArgumentException("兩次輸入的新密碼不一致");
        }
        Employee e = employeeRepo.findByUsername(username).orElseThrow();
        if (!passwordEncoder.matches(f.getCurrentPassword(), e.getPassword())) {
            throw new IllegalArgumentException("目前密碼不正確");
        }
        if (passwordEncoder.matches(f.getNewPassword(), e.getPassword())) {
            throw new IllegalArgumentException("新密碼不可與目前密碼相同");
        }
        e.setPassword(passwordEncoder.encode(f.getNewPassword()));
        e.setMustChangePassword(false);
        employeeRepo.save(e);
    }

    private String generatePassword() {
        String all = UPPER + LOWER + DIGITS;
        List<Character> chars = new ArrayList<>();
        chars.add(pick(UPPER));
        chars.add(pick(LOWER));
        chars.add(pick(DIGITS));
        while (chars.size() < PASSWORD_LENGTH) {
            chars.add(pick(all));
        }
        Collections.shuffle(chars, random);
        StringBuilder sb = new StringBuilder();
        chars.forEach(sb::append);
        return sb.toString();
    }

    private char pick(String source) {
        return source.charAt(random.nextInt(source.length()));
    }
}

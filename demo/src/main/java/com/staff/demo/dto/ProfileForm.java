package com.staff.demo.dto;

import com.staff.demo.entity.Employee;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** 員工「本人」可修改的個人資料。後台編輯員工的表單也沿用這些欄位。 */
@Getter
@Setter
public class ProfileForm {

    public static final String PHONE_REGEX = "^$|[0-9+\\-() #]{6,30}";

    @NotBlank(message = "姓名必填")
    @Size(max = 50, message = "姓名最多 50 字")
    private String name;

    /** 必填：忘記密碼時，新密碼會寄到這個信箱 */
    @NotBlank(message = "Email 必填（忘記密碼時，新密碼會寄到這個信箱）")
    @Email(message = "Email 格式不正確")
    @Size(max = 100, message = "Email 最多 100 字")
    private String email;

    @Pattern(regexp = PHONE_REGEX, message = "電話格式不正確")
    private String phone;

    @Past(message = "生日必須是過去的日期")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate birthday;

    @Size(max = 200, message = "地址最多 200 字")
    private String address;

    @Size(max = 50, message = "緊急聯絡人最多 50 字")
    private String emergencyContactName;

    @Pattern(regexp = PHONE_REGEX, message = "緊急聯絡電話格式不正確")
    private String emergencyContactPhone;

    public static ProfileForm of(Employee e) {
        ProfileForm f = new ProfileForm();
        f.fillFrom(e);
        return f;
    }

    protected void fillFrom(Employee e) {
        name = e.getName();
        email = e.getEmail();
        phone = e.getPhone();
        birthday = e.getBirthday();
        address = e.getAddress();
        emergencyContactName = e.getEmergencyContactName();
        emergencyContactPhone = e.getEmergencyContactPhone();
    }
}

package com.staff.demo.controller;

import com.staff.demo.dto.EmployeeForm;
import com.staff.demo.entity.Employee;
import com.staff.demo.repository.DepartmentRepository;
import com.staff.demo.service.EmployeeNoService;
import com.staff.demo.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

/** 後台員工管理：網頁（/admin/employees）與 API（/api/admin/employees） */
@Controller
@RequiredArgsConstructor
public class AdminEmployeeController {

    private final EmployeeService employeeService;
    private final EmployeeNoService employeeNoService;
    private final DepartmentRepository departmentRepo;

    // ===================== 網頁 =====================

    @GetMapping("/admin/employees")
    public String list(Model model) {
        model.addAttribute("employees", employeeService.findAll());
        return "admin/employees/list";
    }

    @GetMapping("/admin/employees/new")
    public String createForm(Model model) {
        model.addAttribute("form", new EmployeeForm());
        return formView(model, null);
    }

    @PostMapping("/admin/employees")
    public String create(@Valid @ModelAttribute("form") EmployeeForm form,
                         BindingResult br, Model model, RedirectAttributes ra) {
        if (!StringUtils.hasText(form.getPassword())) {
            br.rejectValue("password", "required", "新增員工時密碼必填");
        }
        if (!br.hasErrors()) {
            try {
                Employee e = employeeService.create(form);
                ra.addFlashAttribute("message",
                        "已新增員工「" + e.getName() + "」，員工編號：" + e.getEmployeeNo());
                return "redirect:/admin/employees";
            } catch (IllegalArgumentException ex) {
                br.reject("employee", ex.getMessage());
            }
        }
        return formView(model, null);
    }

    @GetMapping("/admin/employees/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Employee e = employeeService.get(id);
        model.addAttribute("form", EmployeeForm.fromEmployee(e));
        return formView(model, e);
    }

    @PostMapping("/admin/employees/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") EmployeeForm form,
                         BindingResult br, Authentication auth,
                         Model model, RedirectAttributes ra) {
        if (!br.hasErrors()) {
            try {
                employeeService.update(id, form, auth.getName());
                ra.addFlashAttribute("message", "員工資料已更新。");
                return "redirect:/admin/employees";
            } catch (IllegalArgumentException ex) {
                br.reject("employee", ex.getMessage());
            }
        }
        return formView(model, employeeService.get(id));
    }

    /** employee 為 null 代表新增 */
    private String formView(Model model, Employee employee) {
        model.addAttribute("employee", employee);
        model.addAttribute("departments", departmentRepo.findByActiveTrueOrderByCodeAsc());
        model.addAttribute("managers", employeeService.findActive());
        model.addAttribute("mode", employee == null ? "create" : "edit");
        model.addAttribute("action", employee == null ? "/admin/employees" : "/admin/employees/" + employee.getId());
        if (employee == null) {
            String example = employeeNoService.preview(employeeNoService.getRule(), LocalDate.now(), null);
            model.addAttribute("employeeNoText", "儲存後自動產生（範例：" + example + "）");
        } else {
            model.addAttribute("employeeNoText", employee.getEmployeeNo() == null ? "-" : employee.getEmployeeNo());
        }
        return "admin/employees/form";
    }

    // ===================== REST API（Swagger 會顯示） =====================

    private static final String TAG = "後台－員工管理";

    @Operation(summary = "員工列表", tags = TAG)
    @GetMapping("/api/admin/employees")
    @ResponseBody
    public List<EmployeeService.EmployeeView> apiList() {
        return employeeService.findAll().stream().map(employeeService::toView).toList();
    }

    @Operation(summary = "查詢單一員工", tags = TAG)
    @GetMapping("/api/admin/employees/{id}")
    @ResponseBody
    public EmployeeService.EmployeeView apiGet(@PathVariable Long id) {
        return employeeService.toView(employeeService.get(id));
    }

    @Operation(summary = "新增員工", tags = TAG,
            description = "員工編號由系統依編號規則自動產生，不需要傳入。密碼必填。managerId 為直屬主管（請假審核人）。")
    @PostMapping("/api/admin/employees")
    @ResponseBody
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeService.EmployeeView apiCreate(@Valid @RequestBody EmployeeForm form) {
        if (!StringUtils.hasText(form.getPassword())) {
            throw new IllegalArgumentException("新增員工時密碼必填");
        }
        return employeeService.toView(employeeService.create(form));
    }

    @Operation(summary = "修改員工", tags = TAG,
            description = "username 必填但不會被修改；password 留空代表不變更；管理員無法停用或降級自己。")
    @PutMapping("/api/admin/employees/{id}")
    @ResponseBody
    public EmployeeService.EmployeeView apiUpdate(@PathVariable Long id,
                                                  @Valid @RequestBody EmployeeForm form,
                                                  Authentication auth) {
        return employeeService.toView(employeeService.update(id, form, auth.getName()));
    }
}

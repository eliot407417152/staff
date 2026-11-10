package com.staff.demo.controller;

import com.staff.demo.dto.DepartmentForm;
import com.staff.demo.entity.Department;
import com.staff.demo.repository.DepartmentRepository;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/** 後台部門管理：網頁（/admin/departments）與 API（/api/admin/departments） */
@Controller
@RequiredArgsConstructor
public class AdminDepartmentController {

    private final DepartmentRepository repo;

    // ===================== 網頁 =====================

    @GetMapping("/admin/departments")
    public String list(Model model) {
        model.addAttribute("departments", repo.findAll(Sort.by("code")));
        model.addAttribute("form", new DepartmentForm());
        return "admin/departments";
    }

    @PostMapping("/admin/departments")
    public String add(@Valid @ModelAttribute("form") DepartmentForm form,
                      BindingResult br, Model model, RedirectAttributes ra) {
        String code = form.getCode() == null ? "" : form.getCode().trim().toUpperCase();
        if (!br.hasFieldErrors("code") && repo.existsByCode(code)) {
            br.rejectValue("code", "duplicate", "部門代碼已存在");
        }
        if (br.hasErrors()) {
            model.addAttribute("departments", repo.findAll(Sort.by("code")));
            return "admin/departments";
        }
        Department d = new Department();
        d.setCode(code);
        d.setName(form.getName().trim());
        repo.save(d);
        ra.addFlashAttribute("message", "已新增部門「" + d.getName() + "」。");
        return "redirect:/admin/departments";
    }

    /** 停用／啟用部門（停用後新增員工時不會出現在選單，但舊資料保留） */
    @PostMapping("/admin/departments/{id}/toggle")
    public String toggle(@PathVariable Long id) {
        Department d = repo.findById(id).orElseThrow();
        d.setActive(!d.isActive());
        repo.save(d);
        return "redirect:/admin/departments";
    }

    // ===================== REST API（Swagger 會顯示） =====================

    private static final String TAG = "後台－部門";

    public record DepartmentView(Long id, String code, String name, boolean active) {
        static DepartmentView of(Department d) {
            return new DepartmentView(d.getId(), d.getCode(), d.getName(), d.isActive());
        }
    }

    @Operation(summary = "部門列表", tags = TAG)
    @GetMapping("/api/admin/departments")
    @ResponseBody
    public List<DepartmentView> apiList() {
        return repo.findAll(Sort.by("code")).stream().map(DepartmentView::of).toList();
    }

    @Operation(summary = "新增部門", tags = TAG)
    @PostMapping("/api/admin/departments")
    @ResponseBody
    @ResponseStatus(HttpStatus.CREATED)
    public DepartmentView apiCreate(@Valid @RequestBody DepartmentForm form) {
        String code = form.getCode().trim().toUpperCase();
        if (repo.existsByCode(code)) {
            throw new IllegalArgumentException("部門代碼已存在");
        }
        Department d = new Department();
        d.setCode(code);
        d.setName(form.getName().trim());
        return DepartmentView.of(repo.save(d));
    }

    @Operation(summary = "停用／啟用部門", tags = TAG)
    @PatchMapping("/api/admin/departments/{id}/toggle")
    @ResponseBody
    public DepartmentView apiToggle(@PathVariable Long id) {
        Department d = repo.findById(id).orElseThrow();
        d.setActive(!d.isActive());
        return DepartmentView.of(repo.save(d));
    }
}

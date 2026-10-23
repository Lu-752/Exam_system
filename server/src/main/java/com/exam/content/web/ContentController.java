package com.exam.content.web;

import com.exam.common.ImportReq;
import com.exam.common.ImportResult;
import com.exam.common.R;
import com.exam.content.dto.QuestionUpsert;
import com.exam.content.dto.QuestionView;
import com.exam.content.entity.QuestionBank;
import com.exam.content.service.ContentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('bank:view','question:view')")
public class ContentController {

    private final ContentService service;

    // ---------- 题库 ----------

    @GetMapping("/question-banks")
    public R<List<ContentService.BankView>> banks(@RequestParam(required = false) String keyword) {
        return R.ok(service.banks(keyword));
    }

    @PostMapping("/question-banks")
    public R<QuestionBank> createBank(@RequestBody Map<String, Object> body) {
        return R.ok(service.createBank((String) body.get("name"), (String) body.get("level"),
                body.get("outlineId") == null ? null : Long.valueOf(body.get("outlineId").toString())));
    }

    @PutMapping("/question-banks/{id}")
    public R<QuestionBank> updateBank(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return R.ok(service.updateBank(id, (String) body.get("name"), (String) body.get("level"),
                body.get("outlineId") == null ? null : Long.valueOf(body.get("outlineId").toString())));
    }

    @DeleteMapping("/question-banks/{id}")
    public R<Void> deleteBank(@PathVariable Long id) {
        service.deleteBank(id);
        return R.ok();
    }

    // ---------- 试题 ----------

    @GetMapping("/questions")
    public R<List<QuestionView>> questions(@RequestParam(required = false) Long bankId,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String type) {
        return R.ok(service.questions(bankId, keyword, type));
    }

    @PostMapping("/questions")
    public R<QuestionView> createQuestion(@RequestBody QuestionUpsert req) {
        return R.ok(service.createQuestion(req));
    }

    @PostMapping("/questions/import")
    public R<ImportResult> importQuestions(@Valid @RequestBody ImportReq req) {
        return R.ok(service.importQuestions(req.bankId(), req.content()));
    }

    @GetMapping("/questions/import-template")
    public R<String> questionTemplate() {
        return R.ok(ContentService.TEMPLATE);
    }

    @DeleteMapping("/questions/{id}")
    public R<Void> deleteQuestion(@PathVariable Long id) {
        service.deleteQuestion(id);
        return R.ok();
    }
}

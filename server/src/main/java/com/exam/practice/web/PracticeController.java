package com.exam.practice.web;

import com.exam.common.R;
import com.exam.practice.service.PracticeService;
import com.exam.practice.service.PracticeService.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 练习中心（学员端） */
@RestController
@RequestMapping("/api/v1/student/practice")
@RequiredArgsConstructor
public class PracticeController {

    private final PracticeService service;

    public record AnswerReq(Long questionId, String answer) {
    }

    @GetMapping("/banks")
    public R<List<BankCard>> banks(Authentication auth) {
        return R.ok(service.banks((Long) auth.getPrincipal()));
    }

    @GetMapping("/questions")
    public R<List<PracticeQuestion>> questions(Authentication auth,
                                               @RequestParam(required = false) Long bankId,
                                               @RequestParam(defaultValue = "10") int size,
                                               @RequestParam(defaultValue = "false") boolean onlyWrong) {
        return R.ok(service.questions((Long) auth.getPrincipal(), bankId, size, onlyWrong));
    }

    @PostMapping("/answer")
    public R<AnswerResult> answer(@RequestBody AnswerReq req, Authentication auth) {
        return R.ok(service.answer((Long) auth.getPrincipal(), req.questionId(), req.answer()));
    }

    @GetMapping("/wrong")
    public R<List<WrongItem>> wrong(Authentication auth) {
        return R.ok(service.wrongBook((Long) auth.getPrincipal()));
    }

    @DeleteMapping("/wrong/{questionId}")
    public R<Void> clearWrong(@PathVariable Long questionId, Authentication auth) {
        service.clearWrong((Long) auth.getPrincipal(), questionId);
        return R.ok();
    }

    /** 练习概览 */
    @GetMapping("/summary")
    public R<Map<String, Object>> summary(Authentication auth) {
        List<BankCard> cards = service.banks((Long) auth.getPrincipal());
        int practiced = cards.stream().mapToInt(BankCard::practicedCount).sum();
        int correct = cards.stream().mapToInt(BankCard::correctCount).sum();
        double rate = practiced == 0 ? 0 : Math.round(correct * 1000.0 / practiced) / 10.0;
        return R.ok(Map.of(
                "practicedCount", practiced,
                "correctCount", correct,
                "correctRate", rate,
                "wrongCount", service.wrongBook((Long) auth.getPrincipal()).size()
        ));
    }
}

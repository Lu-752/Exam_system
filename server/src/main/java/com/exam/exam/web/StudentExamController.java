package com.exam.exam.web;

import com.exam.common.R;
import com.exam.exam.dto.ExamDtos.AttemptStart;
import com.exam.exam.dto.ExamDtos.EventAck;
import com.exam.exam.dto.ExamDtos.EventReq;
import com.exam.exam.dto.ExamDtos.PlanView;
import com.exam.exam.dto.ExamDtos.ResultView;
import com.exam.exam.dto.ExamDtos.SubmitReq;
import com.exam.exam.service.ExamService;
import com.exam.security.TokenStore;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 学员端考试：列表 / 开考 / 交卷 / 成绩 */
@RestController
@RequestMapping("/api/v1/student")
@RequiredArgsConstructor
public class StudentExamController {

    private final ExamService service;

    @GetMapping("/exams")
    public R<List<PlanView>> exams(Authentication auth) {
        return R.ok(service.listForStudent(userId(auth)));
    }

    @PostMapping("/exams/{planId}/start")
    public R<AttemptStart> start(@PathVariable Long planId, Authentication auth) {
        TokenStore.Session s = session(auth);
        return R.ok(service.start(planId, s.userId(), s.name()));
    }

    @PostMapping("/attempts/{attemptId}/submit")
    public R<ResultView> submit(@PathVariable Long attemptId, @RequestBody SubmitReq req, Authentication auth) {
        return R.ok(service.submit(attemptId, userId(auth), req));
    }

    /** 防作弊：切屏 / 失焦事件上报 */
    @PostMapping("/attempts/{attemptId}/events")
    public R<EventAck> events(@PathVariable Long attemptId, @RequestBody(required = false) EventReq req,
                              Authentication auth) {
        return R.ok(service.reportEvent(attemptId, userId(auth), req));
    }

    @GetMapping("/attempts/{attemptId}/result")
    public R<ResultView> result(@PathVariable Long attemptId, Authentication auth) {
        return R.ok(service.result(attemptId, userId(auth)));
    }

    private Long userId(Authentication auth) {
        return (Long) auth.getPrincipal();
    }

    private TokenStore.Session session(Authentication auth) {
        return (TokenStore.Session) auth.getDetails();
    }
}

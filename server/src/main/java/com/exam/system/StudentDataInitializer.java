package com.exam.system;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.exam.system.entity.SysStudent;
import com.exam.system.mapper.SysStudentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;

/**
 * 演示学员数据（幂等）。生产环境可用 profile 关闭。
 */
@Slf4j
@Order(2)
@Component
@RequiredArgsConstructor
public class StudentDataInitializer implements ApplicationRunner {

    private static final String[] ORGS = {"消防支队一大队", "电工车间 A 组", "危化品仓储中心", "高处作业培训基地", "综合安全科"};
    private static final String[] LEVELS = {"初级", "中级", "高级"};
    private static final String SURNAME = "赵钱孙李周吴郑王冯陈褚卫蒋沈韩杨朱秦尤许何吕施张";
    private static final String[] GIVEN = {"伟", "芳", "娜", "敏", "静", "强", "磊", "洋", "艳", "勇", "军", "杰", "娟", "涛", "明", "超", "秀英", "霞", "平", "刚"};

    private final SysStudentMapper mapper;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (mapper.selectCount(Wrappers.<SysStudent>lambdaQuery()) > 0) {
            return;
        }

        Random rnd = new Random(20261022L);
        for (int i = 1; i <= 26; i++) {
            SysStudent s = new SysStudent();
            s.setNo(String.format("S2026%04d", 1000 + i));
            s.setName(SURNAME.charAt(rnd.nextInt(SURNAME.length())) + GIVEN[rnd.nextInt(GIVEN.length)]);
            s.setGender(rnd.nextBoolean() ? "男" : "女");
            s.setOrgName(ORGS[rnd.nextInt(ORGS.length)]);
            s.setPhone("138" + (10000000 + rnd.nextInt(89999999)));
            s.setLevel(LEVELS[rnd.nextInt(LEVELS.length)]);
            s.setExamCount(rnd.nextInt(9));
            s.setStatus(rnd.nextInt(10) > 1 ? "ACTIVE" : "DISABLED");
            s.setCreatedAt(LocalDateTime.now().minusDays(rnd.nextInt(240)));
            s.setUpdatedAt(LocalDateTime.now());
            mapper.insert(s);
        }
        log.info("seeded 26 demo students");
    }
}

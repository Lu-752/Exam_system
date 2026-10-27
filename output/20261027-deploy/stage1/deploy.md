# 考试系统 部署文档

## 文档说明

### 编写目的

本文解决一件事：**把考试系统装到目标机器上，并让它稳定跑起来**。
内容包括环境准备、四种部署方式、首次验收、日常启停与备份、升级回滚、安全加固和故障排查。

不涉及功能怎么用——那部分见《考试系统操作手册》（19 页）与《考试系统演示材料》（16 页）。

### 与其它文档的分工

| 文档 | 回答什么问题 | 读者 |
|---|---|---|
| 本文档 | 怎么装、怎么启停、怎么备份升级 | 实施 / 运维 |
| 考试系统操作手册 | 每个功能怎么点、有哪些业务规则 | 管理员 / 学员 / 实施 |
| 考试系统演示材料 | 一条主线演示十三个操作动作 | 汇报 / 培训 |
| 发行包 README.txt | 三步上手，应急时看 | 所有人 |

### 术语

| 术语 | 含义 |
|---|---|
| 发行包 | `release.zip`，含 jar、Java 运行时、启停脚本、校验清单 |
| 运行时 | 包内 `runtime/`，用 jlink 从 JDK 21 裁剪，目标机无需另装 Java |
| dev / pg | 两个 Spring Profile：dev 用内置 H2，pg 连 PostgreSQL |
| Flyway | 数据库版本管理，启动时自动执行 `db/migration/V1~V15` |
| 演示数据 | 首次启动自动写入的题库、试卷、考试计划与管理员账号 |

## 部署前准备

### 硬件与操作系统

| 项 | 最低 | 建议 | 说明 |
|---|---|---|---|
| CPU | 2 核 | 4 核 | 单体应用，主要吃并发请求 |
| 内存 | 1 GB | 2 GB 起 | JVM 堆可通过 `-Xmx` 限制；人机对话场景 1 GB 足够 |
| 磁盘 | 1 GB 空闲 | 5 GB 起 | 发行包解压约 90 MB，其余留给数据库与日志 |
| 操作系统 | Windows 10 / Server 2016 | Windows Server 2019+ 或 Linux | 包内运行时为 **Windows x64**；Linux 需自备 JDK 21 |
| 浏览器 | Chrome / Edge 90+ | 同左 | 前端为 SPA，不支持 IE |

### 软件依赖

| 组件 | 是否需要安装 | 说明 |
|---|---|---|
| Java | **不需要**（Windows） | 包内 `runtime/` 已含 Java 21；Linux 需系统 JDK 21+ |
| 数据库 | 视方式而定 | 方式一无需；方式二/三/四需要 PostgreSQL |
| PostgreSQL | 方式二/四需要 | 交付验证版本 PostgreSQL 16；更低版本未验证 |
| Nginx | 可选 | 需要域名、HTTPS 或统一入口时使用，见第 7 章 |

### 网络与端口

| 端口 | 用途 | 开放范围 |
|---|---|---|
| 8080 | 应用 HTTP 端口（默认，可改） | 对使用者网段开放；或用 Nginx 反代后只对本机开放 |
| 5432 | PostgreSQL | 仅应用服务器可访问；不要对终端用户网段开放 |
| 22 | SSH（Linux 运维） | 运维网段 |

> 若使用域名 + HTTPS，对外只开放 443，8080 收到反代后面不直接暴露。

### 部署前信息收集

交付前先向客户确认并填好下表，部署时直接照抄，避免现场反复询问。

| 项 | 值 | 备注 |
|---|---|---|
| 服务器 IP / 主机名 |  |  |
| 操作系统与版本 |  | 决定用哪套脚本 |
| 计划使用的端口 | 8080 | 被占用则换 9090 等 |
| 数据库方式 | 内置 / 已有 PG / 自动装 PG | 见 4.1 选择表 |
| 数据库地址与库名 |  | 方式二/四必填 |
| 数据库账号与密码 |  | 建议专用账号，不用 postgres 超级用户 |
| 访问入口 | `http://IP:8080` 或域名 |  |
| 是否需要 HTTPS | 是 / 否 | 需要则准备证书 |
| 数据保留与备份位置 |  | 与业务方约定 |

### 部署检查表

按顺序勾完再开始，可省掉大部分返工。

- 目标机磁盘剩余 ≥ 1 GB，且解压路径不含空格与中文
- 目标机时间与时区正确（成绩与考试时间按 `Asia/Shanghai` 解析）
- 计划使用的端口未被占用（`netstat -ano | findstr :8080`）
- 已确认数据库方式，方式二/四已拿到可用账号
- 已取得发行包，并核对过 `BUILD-INFO.txt` 的版本与构建时间
- 已确认交付后的管理员初始口令由谁保管、在哪天修改

## 发行包说明

### 包结构

解压后得到 `release/` 目录，结构如下：

```
release/
├── exam-server-0.1.0.jar     可执行 jar（前端已打进去，无独立前端目录）
├── runtime/                  内置 Java 21 运行时（Windows x64，约 50 MB）
├── bin/
│   ├── start.bat             Windows 启动器（三选一菜单）
│   ├── stop.bat              Windows 停止器（按端口找 Java 进程）
│   ├── install-postgres.bat  方式三：自动安装并初始化 PostgreSQL
│   ├── verify.bat            Windows 完整性校验
│   ├── run.sh                Linux / macOS 启动器
│   ├── stop.sh               Linux / macOS 停止器
│   └── verify.sh             Linux / macOS 完整性校验
├── data/                     运行后生成：exam.mv.db（内置库数据）
├── logs/                     后台模式生成：app.log、app.port
├── BUILD-INFO.txt            版本 / 构建时间 / 平台 / 源码指纹
├── SHA256SUMS.txt            逐文件 SHA256 清单（222 项）
├── README.txt                快速上手
└── 安装与操作说明.md          安装与常见问题
```

### 完整性校验

压缩包在拷贝、上传、杀毒软件扫描过程中可能损坏，尤其 `runtime/` 里有上千个文件。**首次启动前必须校验**：

```
双击 bin\verify.bat                       Windows
bash bin/verify.sh                        Linux / macOS
```

脚本按 `SHA256SUMS.txt` 逐个比对，输出 `校验通过：222 个文件全部一致` 才算完整。
出现 `缺失` 或 `损坏` 时不要启动，重新解压或重新取包——半损坏的包会表现为「启动闪退」或「页面白屏」，很难现场定位。

### 读 BUILD-INFO.txt

这个文件用来回答两个问题：这包是哪天打的、里面的文件是不是这套源码。

| 字段 | 含义 | 怎么用 |
|---|---|---|
| 版本 | 0.1.0 | 与客户合同、交付记录核对 |
| 构建时间 | 精确到秒 | 判断是否拿到了最新包 |
| 目标平台 | Windows x64 | 与目标机架构核对 |
| 运行时 | Java 21.0.12.1 | 与开发环境一致的证据 |
| Git | 短哈希 + 提交标题 | 出问题时能定位到具体代码版本 |
| 包大小 / 文件数 | 88.3 MB / 224 | 解压后对得上说明没丢文件 |
| 源码指纹 | 关键文件 SHA256 前 16 位 | 与源码目录比对，确认未被替换 |

### 版本与体积参考

| 项 | 值 |
|---|---|
| 发行包 `release.zip` | 68.9 MB |
| 解压后 `release/` | 88.3 MB（jar 37.7 MB + runtime 50.3 MB） |
| 校验清单条目 | 222 个文件 |
| 数据库迁移脚本 | V1 ~ V15，首次启动全部执行 |

## 部署方式

### 先选方式

| 场景 | 推荐方式 | 数据存放 | 稳定程度 |
|---|---|---|---|
| 现场演示、功能验收、单机试用 | 方式一 | 内置 H2 文件 | 够用，不建议长期生产 |
| 客户已有 PostgreSQL | 方式二（Windows）/ 方式四（Linux） | 客户数据库 | 生产推荐 |
| 全新服务器、没装数据库 | 方式三（Windows 自动装） | 本机 PostgreSQL | 生产可用 |
| 客户要求跑在 Linux | 方式四 | PostgreSQL | 生产推荐 |

一句话原则：**只要不是纯演示，就别用内置库**。内置库是单文件、单进程，备份靠停服拷贝，并发与容量都受限。

### 方式一 · Windows 单机 + 内置库

适用于演示、验收、一台电脑跑通的场景。

1. 解压 `release.zip` 到 `D:\exam-sys`（路径不要有空格）
2. 双击 `bin\start.bat`，菜单选 `1`
3. 等待窗口出现 `Started ExamApplication` 与 `Tomcat started on port 8080`
4. 浏览器打开 `http://localhost:8080`

数据落在 `data\exam.mv.db`。首次启动会自动建表并写入演示数据。

需要注意：

- 该模式下 `spring.profiles.active=dev`，`/h2-console` 控制台是开着的，**不要长期对业务网段开放**
- 数据目录跟着启动目录：一定要用 `bin\start.bat` 启动（脚本会先 `cd` 到包根），不要在别处手敲 `java -jar`
- 停止：`bin\stop.bat`，或在前台窗口按 `Ctrl+C`

### 方式二 · Windows 连接已有 PostgreSQL

适用于客户已经有数据库、要求数据统一管理。

1. 在数据库侧准备库与账号：

```sql
CREATE ROLE exam LOGIN PASSWORD '换成强口令';
CREATE DATABASE exam_sys OWNER exam ENCODING 'UTF8';
```

2. 双击 `bin\start.bat`，菜单选 `2`
3. 依次填写主机、端口、库名、用户名、密码（回车取默认值）
4. 看到 `Successfully applied 15 migration` 说明表结构已自动建好

表结构由 Flyway 自动创建，**空库即可**，不需要手工执行建表脚本。

连接参数实际是以命令行方式传给应用的，等价于：

```
java -jar exam-server-0.1.0.jar --server.port=8080 --spring.profiles.active=pg ^
  --DB_URL=jdbc:postgresql://10.0.0.21:5432/exam_sys --DB_USER=exam --DB_PASSWORD=******
```

### 方式三 · Windows 自动安装 PostgreSQL

适用于服务器什么都没装，但要用真实数据库。

1. 双击 `bin\start.bat`，菜单选 `3`
2. 脚本调用 `bin\install-postgres.bat`：探测是否已装 → 未装则用 winget / chocolatey 静默安装 → 启动服务 → 建库 `exam_sys` 与角色 `exam`
3. 建库阶段可能要求输入 postgres 超级用户口令；希望无人值守时，先执行 `set PGPASSWORD=你的口令` 再启动
4. 随后自动以 pg 模式启动

需要联网（winget 源）。客户网络受限时改用「先手工装 PG，再走方式二」。

### 方式四 · Linux 服务器

发行包内运行时是 Windows 版，Linux 上改用系统 JDK 21 + `run.sh`，生产建议托管为 systemd 服务。

前置：安装 JDK 21 与 PostgreSQL 16。

```bash
# 1. 解压到 /opt
sudo mkdir -p /opt/exam-sys && sudo tar -xzf release.zip -C /tmp
sudo cp -r /tmp/release/* /opt/exam-sys/
sudo mkdir -p /opt/exam-sys/logs && sudo chown -R examsys:examsys /opt/exam-sys

# 2. 校验（Linux 上走 sha256sum）
cd /opt/exam-sys && bash bin/verify.sh

# 3. 建库
sudo -u postgres psql -c "CREATE ROLE exam LOGIN PASSWORD '换成强口令';"
sudo -u postgres psql -c "CREATE DATABASE exam_sys OWNER exam ENCODING 'UTF8';"
```

数据库口令不要写在命令行里（会出现在 `ps` 输出中），用环境变量文件：

```
# /opt/exam-sys/app.env    权限 600，属主 examsys
DB_URL=jdbc:postgresql://127.0.0.1:5432/exam_sys
DB_USER=exam
DB_PASSWORD=换成强口令
JAVA_OPTS=-Xms256m -Xmx1024m -Duser.timezone=Asia/Shanghai
```

systemd 单元：

```
# /etc/systemd/system/exam-sys.service
[Unit]
Description=Exam System
After=network.target postgresql.service
Wants=postgresql.service

[Service]
Type=simple
User=examsys
WorkingDirectory=/opt/exam-sys
EnvironmentFile=/opt/exam-sys/app.env
ExecStart=/usr/bin/java ${JAVA_OPTS} -jar /opt/exam-sys/exam-server-0.1.0.jar \
          --server.port=8080 --spring.profiles.active=pg
SuccessExitStatus=143
Restart=on-failure
RestartSec=5
LimitNOFILE=65535

[Install]
WantedBy=multi-user.target
```

启用与查看：

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now exam-sys
sudo systemctl status exam-sys
sudo journalctl -u exam-sys -f          # 跟踪日志
```

`SuccessExitStatus=143` 是必须的：Spring Boot 收到 SIGTERM 时优雅停机并返回 143，不加这条 systemd 会把正常停止记成失败。

### 修改端口

| 方式 | 命令 |
|---|---|
| Windows 启动器 | `bin\start.bat --port=9090` |
| Linux 启动脚本 | `./bin/run.sh --port=9090` |
| systemd | 改 `ExecStart` 里的 `--server.port`，`daemon-reload` 后重启 |
| 手工执行 | `java -jar exam-server-0.1.0.jar --server.port=9090` |

改完记得同步防火墙规则和反代配置。

## 首次启动与验收

### 启动成功的标志

日志里出现这两行即代表可以访问：

```
Tomcat started on port 8080 (http) with context path ''
Started ExamApplication in 12.345 seconds
```

后台模式（`-d`）看不到控制台，改看 `logs\app.log`，或直接请求健康检查：

```
curl http://127.0.0.1:8080/actuator/health     → {"status":"UP"}
```

### 数据库初始化说明

首次启动由 Flyway 自动执行 `V1 ~ V15` 共 15 个迁移脚本，创建全部表结构，随后初始化器写入：

- 管理员账号与角色权限菜单
- 演示大纲、题库、试题、试卷、考试计划
- 学员账号（绑定示例学员）

重复启动不会重复插入（初始化器有存在性判断），因此**重启是安全的**。

### 验收清单

装完按这张表走一遍，全部符合才算交付完成。

| 序号 | 检查项 | 预期结果 |
|---|---|---|
| 1 | `bin\verify.bat` | 校验通过，文件数一致 |
| 2 | 启动日志 | 出现 `Started ExamApplication` |
| 3 | `/actuator/health` | 返回 `{"status":"UP"}` |
| 4 | 打开首页 | 显示登录页，能加载图形验证码 |
| 5 | 管理员登录 | 进入首页，左侧菜单完整（学员中心 / 考试中心 / 系统管理） |
| 6 | 学员登录 | 只看到「学员中心」四项菜单 |
| 7 | 首页指标 | 学员数、题库量等有数值，不是全 0 或空白 |
| 8 | 新建一次考试 | 能建计划并开考、交卷、阅卷、出分（可用演示数据） |
| 9 | 重启应用 | 数据仍在（证明数据目录/数据库配置正确） |
| 10 | 停止脚本 | `bin\stop.bat` 能停掉，端口释放 |

![图 5-1　登录页：能正常加载图形验证码，说明前端与后端已连通](fig_login_fixed.png)

![图 5-2　首页概览：指标为真实统计值，不是空白或全 0](fig_dashboard.png)

### 交付前必做

1. **改掉默认口令**：管理员、学员账号的初始口令相同，交付当天必须改。入口：`系统管理 → 用户管理 → 重置密码`
2. **确认 profile**：生产必须是 `pg`，用 `dev` 会让 `/h2-console` 匿名可访问
3. **清理演示数据**（可选）：演示数据方便验收，正式投用前如需空库，方式一停服删 `data\` 后重启；方式二清空库后重启，会重新初始化
4. **记录交付信息**：版本号、构建时间、Git 哈希、数据库地址、端口、管理员口令保管人

## 日常运维

### 启停与日志

| 操作 | Windows | Linux |
|---|---|---|
| 前台启动 | `bin\start.bat` | `./bin/run.sh` |
| 后台启动 | `bin\start.bat -d` | `./bin/run.sh -d` 或 `systemctl start exam-sys` |
| 停止 | `bin\stop.bat` | `./bin/stop.sh` 或 `systemctl stop exam-sys` |
| 日志位置 | `logs\app.log` | `logs/app.log` 或 `journalctl -u exam-sys` |
| 端口记录 | `logs\app.port` | `logs/app.port` |

`bin\stop.bat` 不是按进程名乱杀：它从 `logs\app.port` 取端口，用 `netstat` 找到监听该端口的 PID，再确认进程是 java / javaw 才结束，避免误杀服务器上其它 Java 程序。

### 备份与恢复

**方式一（内置库）**：库就是 `data\exam.mv.db` 一个文件，但**必须停服后再拷**，运行中拷贝可能拿到不一致的页。

```bash
bin\stop.bat
xcopy /E /I /Y data  D:\backup\exam-data-20261027
bin\start.bat -d
```

恢复：停服 → 用备份文件覆盖 `data\exam.mv.db` → 启动。

**方式二/三/四（PostgreSQL）**：

```bash
# 备份（业务低峰执行）
pg_dump -h 127.0.0.1 -U exam -Fc -d exam_sys -f /backup/exam_sys_20261027.dump

# 恢复
pg_restore -h 127.0.0.1 -U exam -d exam_sys --clean --if-exists /backup/exam_sys_20261027.dump
```

建议：备份保留最近 7 天；每次升级前先备一份并记下版本号；恢复演练至少做一次，不要等出事才第一次用。

### 升级与回滚

程序升级（数据库不动）：

1. 停服
2. 用新包覆盖 `exam-server-*.jar`、`bin/`、`runtime/`，并保留 `data/`、`logs/`
3. 启服，确认 Flyway 只执行新增迁移（日志出现 `Migrating schema ... to version V16`）
4. 按验收清单抽查登录与一条业务链路

回滚：把旧 jar 覆盖回去再启动。**注意数据库不会自动回退**——若新版本已执行过新迁移，回滚前应先用备份恢复数据库，否则旧程序可能因表结构不认识而报错。

### 迁移到另一台机器

| 方式 | 做法 |
|---|---|
| 内置库 | 停服 → 整体拷贝整个 `release/` 目录（含 `data/`）→ 目标机启动 |
| PostgreSQL | 目标机装新包 → 按方式二/四连到同一个库，或 `pg_dump` + `pg_restore` 搬到新库 |

### 健康检查与监控

| 端点 | 是否需要登录 | 用途 |
|---|---|---|
| `/actuator/health` | 否 | 存活探针，返回 `{"status":"UP"}` |
| `/actuator/info` | 是 | 构建信息 |
| `/actuator/metrics` | 是 | 各项指标 |
| `/actuator/prometheus` | 是 | Prometheus 抓取格式 |

只有 `health` 是匿名的，其余需要登录令牌。接到监控系统时，如果抓取端不方便带令牌，建议在内网由 Nginx 侧单独放行或改为服务端探活。

## 安全与加固

### 必做项

| 项 | 做法 | 不做的后果 |
|---|---|---|
| 改默认口令 | 交付当天改掉管理员与学员初始口令 | 初始口令是公开的，等于没有密码 |
| 用 pg profile | 生产严禁 `--spring.profiles.active=dev` | H2 控制台 `/h2-console` 可被匿名访问，等于数据库裸奔 |
| 数据库账号最小权限 | 专用账号 `exam`，只授权到 `exam_sys` | 一旦口令泄露波及整个实例 |
| 口令不进命令行 | 用 `app.env` / 环境变量，不写 `--DB_PASSWORD=` | `ps`、日志、systemd 记录里都能看到 |
| 限制管理端来源 | 8080 只对使用者网段；或反代后只监听 127.0.0.1 | 后台暴露在公网 |
| 关闭调试级日志 | `--logging.level.com.exam=info` | 生产日志量暴增，且可能打印敏感数据 |

### Nginx 反向代理

前端是 Hash 路由（`#/...`），不需要为 SPA 配置 rewrite，直接转发即可。

```
server {
    listen 80;
    server_name exam.example.com;
    client_max_body_size 20m;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

HTTPS 在外层终止即可，应用本身不需要配证书。启用后把应用端口改为只监听本机：

```
java -jar exam-server-0.1.0.jar --server.address=127.0.0.1 --server.port=8080 ...
```

### 已知限制

这些是架构层面的约束，部署方案要绕开它们，而不是试图调参解决。

| 限制 | 影响 | 应对 |
|---|---|---|
| 登录令牌存在进程内 Map | 多实例部署时令牌不共享，重启后所有人需重新登录 | 单实例部署；确需多节点先改造成 Redis 存储 |
| 内置 H2 为单进程单文件 | 不能多实例挂同一份数据 | 生产改 PostgreSQL |
| CORS 仅放行 localhost | 前后端分域名部署会被浏览器拦截 | 用同源反代（推荐），或改 `SecurityConfig` 白名单 |
| 会话无状态、CSRF 关闭 | 依赖 Token 校验，不依赖 Cookie | 不要把令牌放进 Cookie，避免被 CSRF 利用 |
| 管理端未做请求频率限制 | 存在暴力破解登录的窗口 | 反代层加 `limit_req`，或前置 WAF |

## 故障排查

按现象查，先看日志再改配置。

| 现象 | 可能原因 | 处理 |
|---|---|---|
| 双击 start.bat 窗口一闪而过 | 路径含空格/中文，或 jar 缺失 | 在 cmd 里 `cd` 到目录手工运行 `start.bat` 看报错 |
| 提示「未找到可用的 Java」 | `runtime/` 解压不完整 | 跑 `bin\verify.bat`，重新解压完整包 |
| `Port 8080 was already in use` | 端口被占用 | `bin\stop.bat` 或换端口 `--port=9090` |
| 浏览器打不开，日志无 `Started` | 启动失败（数据库连不上、迁移报错） | 看日志最后 50 行，通常是连接串或口令问题 |
| 页面 404 / 白屏 | jar 未含前端静态资源 | 用官方发行包；自建包需确认 `BOOT-INF/classes/static/index.html` 存在 |
| 登录后左侧菜单为空 | 角色权限未初始化 | 确认库是空库首次启动；或删库重来让初始化器重建 |
| 方式二报 `Connection refused` | 数据库未启动 / 端口错 / 只监听 localhost | 用 `psql` 先手工连通，再看 `pg_hba.conf` |
| 报 `Flyway validate failed` | 库里有旧版本表结构或被手工改过 | 清空该库首次启动；或找开发确认迁移基线 |
| 时间差 8 小时 | 服务器时区不是东八区 | 启动加 `-Duser.timezone=Asia/Shanghai`，或改系统时区 |
| 交卷后学员看不到分数 | 成绩未发布（设计如此） | 管理员在「考试计划」里打开「发布成绩」 |
| 停止脚本报「未发现进程」 | 端口改了但 `logs\app.port` 未更新 | `bin\stop.bat 9090` 显式指定端口 |
| 内存溢出 `OutOfMemoryError` | 堆太小或并发过高 | 加 `-Xmx2048m`，并排查是否有大报表查询 |

## 附录

### 启动参数速查

| 参数 | 说明 | 示例 |
|---|---|---|
| `--server.port` | HTTP 端口 | `--server.port=9090` |
| `--server.address` | 监听地址 | `--server.address=127.0.0.1` |
| `--spring.profiles.active` | 运行模式 | `dev` 内置库 / `pg` PostgreSQL |
| `--DB_URL` | JDBC 连接串 | `jdbc:postgresql://127.0.0.1:5432/exam_sys` |
| `--DB_USER` / `--DB_PASSWORD` | 数据库账号口令 | 建议改用环境变量 |
| `--logging.file.name` | 日志文件路径 | `logs/app.log` |
| `--logging.level.com.exam` | 业务日志级别 | `info`（生产） |
| `-Xmx` | 最大堆 | `-Xmx1024m` |

### 环境变量速查

| 变量 | 默认值 | 说明 |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://127.0.0.1:5432/exam_sys` | 仅 pg profile 生效 |
| `DB_USER` | `exam` | 同上 |
| `DB_PASSWORD` | `exam` | 同上，生产必须覆盖 |

### 目录与文件清单

| 路径 | 是否随包 | 说明 |
|---|---|---|
| `exam-server-0.1.0.jar` | 是 | 应用本体，含前端 |
| `runtime/` | 是 | Java 运行时，勿删 |
| `bin/` | 是 | 启停与校验脚本 |
| `data/` | 否 | 运行后生成，内置库数据，需备份 |
| `logs/` | 否 | 运行后生成，日志与端口记录 |
| `BUILD-INFO.txt` | 是 | 构建信息与指纹 |
| `SHA256SUMS.txt` | 是 | 校验清单 |

### 常用命令速查

```
bin\start.bat                  前台启动
bin\start.bat -d               后台启动
bin\start.bat --port=9090      换端口启动
bin\stop.bat                   停止
bin\verify.bat                 完整性校验
curl http://127.0.0.1:8080/actuator/health     健康检查
```

Linux 对应 `./bin/run.sh`、`./bin/run.sh -d`、`./bin/stop.sh`、`bash bin/verify.sh`，
或统一用 `systemctl start|stop|status exam-sys`。

### 交付签署

| 项 | 内容 |
|---|---|
| 交付版本 | 0.1.0（构建时间见 BUILD-INFO.txt） |
| 部署方式 | 方式一 / 二 / 三 / 四（勾选） |
| 部署主机 |  |
| 访问地址 |  |
| 数据库 |  |
| 管理员口令保管人 |  |
| 验收结论 | 通过 / 有条件通过（问题：　　　　） |
| 实施人 / 日期 |  |
| 客户确认 / 日期 |  |

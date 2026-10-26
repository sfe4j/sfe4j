# SFE4J 项目导览

本文记录仓库源码的当前事实，供 AI 与维护者定位任务；协作规则以根目录 `AGENTS.md` 为准。下述观察来自静态阅读，不等同于完整安全审计或运行验证。

## 调用链与定位

| 关注点 | 源码位置（相对于仓库根目录） | 说明 |
| --- | --- | --- |
| 文件访问门面 | `sfe4j-core/src/main/java/com/amoylabs/sfe4j/core/access/` | 路径解析、保护规则、统一授权与文件操作 |
| 文件系统逻辑 | `sfe4j-core/src/main/java/com/amoylabs/sfe4j/core/service/FileExplorerService.java` | 页面元信息、目录树；读取委托给访问门面 |
| 文本读取 | `sfe4j-core/src/main/java/com/amoylabs/sfe4j/core/service/TextFileReader.java` | 有界读取与 UTF-8 解码，标记截断与解码问题 |
| 文件读取工具 | `sfe4j-core/src/main/java/com/amoylabs/sfe4j/core/util/FileUtils.java` | 读取工具，只在有界的预览读取路径使用 |
| 数据模型 | `sfe4j-core/src/main/java/com/amoylabs/sfe4j/core/vo/` | 文件、目录树、文件内容 |
| 配置模型 | `sfe4j-core/src/main/java/com/amoylabs/sfe4j/core/configuration/Sfe4jConfiguration.java` | 框架无关配置 |
| 访问与会话适配 | `sfe4j-spring-boot/src/main/java/com/amoylabs/sfe4j/spring/boot/access/` | session 授权存储、密码限速、CSRF、目录授权服务 |
| Web 基础设施 | `sfe4j-spring-boot/src/main/java/com/amoylabs/sfe4j/spring/boot/web/` | 错误处理、下载并发限制、URL 编解码、路径标签 |
| Web 入口 | `sfe4j-spring-boot/src/main/java/com/amoylabs/sfe4j/spring/boot/controller/` | 列表、预览、下载，全部经过访问门面 |
| 服务 Bean | `sfe4j-spring-boot/src/main/java/com/amoylabs/sfe4j/spring/boot/config/BeanConfig.java` | 注入配置到文件服务与访问门面 |
| 页面 | `sfe4j-spring-boot/src/main/resources/templates/` | `file-explorer.ftlh`、`file-viewer.ftlh`、`file-error.ftlh` |
| 自动配置 | `sfe4j-spring-boot-starter/src/main/java/com/amoylabs/sfe4j/spring/boot/starter/` | 属性绑定、映射与校验、组件扫描 |
| 运行示例 | `sfe4j-spring-boot-sample/src/main/` | 启动类、Ping 控制器、application YAML 与 profile |

启动时，starter 注册自动配置并启用 `Sfe4jProperties`，由 `Sfe4jProperties.toConfiguration()` 映射并校验为 `Sfe4jConfiguration`；`BeanConfig` 创建 `FileAccessService` 与 `FileExplorerService`，控制器经访问门面输出视图或流式下载响应。

## HTTP 与配置契约

- `GET /file-explorer?dir=...`：目录列表；未传目录时使用基础目录。
- `POST /file-explorer`：以 `dir`、`pwd` 参数验证目录密码，成功后 Post/Redirect/Get；授权只保存在 HTTP session，默认 30 分钟。
- `POST /file-explorer/logout`：仅清除 SFE4J 目录授权，不销毁宿主 session。
- `GET /file-viewer?file=...`：有界读取文本并交给自动转义的预览模板。
- `GET /file-downloader?file=...`：以固定缓冲区流式输出附件，并发超限返回 429。
- 属性前缀 `sfe4j`，支持 `title`、`description`、`quick-links`、`base-dir-path`、`restrict-to-base-dir`、`folder-passwords`、`access.*`、`preview.*`、`download.*`。
- 自动配置将空基础目录默认为 `/`、空限制开关默认为 `false`；标题和描述由服务回退到 `GlobalConstants`。数值配置超范围时启动失败，不静默降级。
- YAML 中路径类型 Map 键采用括号写法，例如 `"[/data/secret]"`。示例文件中的密码仅适合演示，不应当作部署凭据。

## 构建与运行事实

- 四模块版本分别声明，目前均为 `1.1.0`；模块间版本属性也分散在各自 POM 中，发布版本修改需逐处核对。
- `core` 依赖 Commons Lang；web 引入 Spring MVC、FreeMarker；starter 负责集成；sample 继承 Spring Boot parent 并引入 Boot Admin client。
- `.github/workflows/ci.yaml` 使用 JDK 25，分别执行 `mvn test` 和跳过测试的打包；手动输入 `build_image` 控制镜像构建与推送。
- Dockerfile 位于 `.cicd/Dockerfile`，复制 sample 的可执行 JAR，使用 Temurin 25 JRE；Jenkinsfile 依赖仓库外共享库。
- sample 的产物为 `sfe4j-spring-boot-sample/target/sfe4j-spring-boot-sample.jar`，默认端口 8080。
- core、web、starter 三个模块各自配置 JUnit 5 与 Surefire，均有真实执行的测试：core 覆盖路径/策略/文本读取，web 覆盖控制器、session、CSRF 与模板渲染，starter 覆盖属性绑定与启动校验。sample 仍只有 `@SpringBootTest contextLoads()`。
- 集成测试（真实 HTTP 下载、中断与句柄释放）与 Windows CI 尚未纳入，见开发计划的未执行项。

## 已观察到的待改进点

以下用于后续任务评估。前五项为 P0 已修复项，保留作为回归依据：

1. ~~**文件访问边界**：`restrict-to-base-dir` 仅在 `buildFileTree()` 使用。~~ 已由统一访问门面修复：列表、预览、下载共用 `FileAccessService`，路径归属按路径段比较并解析真实路径。
2. ~~**目录密码范围**：密码检查只在目录列表控制器。~~ 已修复：保护规则在词法路径与真实路径上分别取最长匹配，预览和下载同样要求授权。
3. ~~**预览输出**：`<#noautoesc>` 能够渲染 HTML。~~ 已修复：改为自动转义的 `<pre>`，文件名也经过转义。
4. ~~**资源与内存**：输入流未关闭，文件完整加载。~~ 已修复：预览最多读取 `preview.max-bytes`，下载改用固定缓冲区流式输出并随响应关闭。
5. ~~**异常与编码**：`readFile()` 遇错返回 `null`，文件名直接拼入响应头。~~ 已修复：统一错误码与错误页/JSON 契约，下载头由 `ContentDisposition` 构造，列表链接统一 URL 编码。

剩余项（P1/P2 范围）：

6. **跟随与日志能力**：大日志的 head/tail、编码选择、行号与 SSE 跟随尚未实现。
7. **规模化与集成**：多根、宿主认证 SPI、审计、服务端分页、中英文与移除统计脚本尚未实现。
8. **并发替换风险**：路径检查与打开之间仍存在 TOCTOU 窗口，未使用 `SecureDirectoryStream`；部署上需要只读挂载或隔离。

## 后续任务建议验证的场景

按任务范围选择：基础目录及子目录、相邻前缀目录、路径穿越、符号链接、Windows 路径、密码保护目录与子目录、嵌套密码优先级、直接访问预览/下载、文件不存在或不可读、中文与特殊字符文件名、HTML 内容及大文件。涉及文件的测试使用临时目录；不要依赖宿主机根目录或演示配置中的实际路径。

## 演进设计与开发计划

按优先级的待实施设计见 [设计索引](../design/README.md)，任务拆分与验收见 [开发计划](../development/implementation-plan.md)。这些文件描述目标方案，不代表当前功能已经实现。

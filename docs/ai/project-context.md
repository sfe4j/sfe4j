# SFE4J 项目导览

本文记录仓库源码的当前事实，供 AI 与维护者定位任务；协作规则以根目录 `AGENTS.md` 为准。下述观察来自静态阅读，不等同于完整安全审计或运行验证。

## 调用链与定位

| 关注点 | 源码位置（相对于仓库根目录） | 说明 |
| --- | --- | --- |
| 文件系统逻辑 | `sfe4j-core/src/main/java/com/amoylabs/sfe4j/core/service/FileExplorerService.java` | 页面元信息、目录树、读取文件 |
| 文件读取工具 | `sfe4j-core/src/main/java/com/amoylabs/sfe4j/core/util/FileUtils.java` | 将文件完整读入 byte 数组 |
| 数据模型 | `sfe4j-core/src/main/java/com/amoylabs/sfe4j/core/vo/` | 文件、目录树、文件内容 |
| 配置模型 | `sfe4j-core/src/main/java/com/amoylabs/sfe4j/core/configuration/Sfe4jConfiguration.java` | 框架无关配置 |
| Web 入口 | `sfe4j-spring-boot/src/main/java/com/amoylabs/sfe4j/spring/boot/controller/` | 列表、预览、下载 |
| 服务 Bean | `sfe4j-spring-boot/src/main/java/com/amoylabs/sfe4j/spring/boot/config/BeanConfig.java` | 注入配置到文件服务 |
| 页面 | `sfe4j-spring-boot/src/main/resources/templates/` | `file-explorer.ftlh`、`file-viewer.ftlh` |
| 自动配置 | `sfe4j-spring-boot-starter/src/main/java/com/amoylabs/sfe4j/spring/boot/starter/` | 属性绑定、默认值映射、组件扫描 |
| 运行示例 | `sfe4j-spring-boot-sample/src/main/` | 启动类、Ping 控制器、application YAML 与 profile |

启动时，starter 注册自动配置并启用 `Sfe4jProperties`，将属性映射为 `Sfe4jConfiguration`；`BeanConfig` 创建 `FileExplorerService`，控制器调用服务并输出视图或下载响应。

## HTTP 与配置契约

- `GET /file-explorer?dir=...`：目录列表；未传目录时使用基础目录。
- `POST /file-explorer`：以 `dir`、`pwd` 参数验证目录密码；授权状态保存在 HTTP session。
- `GET /file-viewer?file=...`：读取文件并将字符串传给预览模板。
- `GET /file-downloader?file=...`：读取文件，返回字节数组及附件响应头。
- 属性前缀 `sfe4j`，支持 `title`、`description`、`quick-links`、`base-dir-path`、`restrict-to-base-dir`、`folder-passwords`。
- 自动配置将空基础目录默认为 `/`、空限制开关默认为 `false`；标题和描述由服务回退到 `GlobalConstants`。
- YAML 中路径类型 Map 键采用括号写法，例如 `"[/data/secret]"`。示例文件中的密码仅适合演示，不应当作部署凭据。

## 构建与运行事实

- 四模块版本分别声明，目前均为 `1.0.3`；模块间版本属性也分散在各自 POM 中，发布版本修改需逐处核对。
- `core` 依赖 Commons Lang；web 引入 Spring MVC、FreeMarker；starter 负责集成；sample 继承 Spring Boot parent 并引入 Boot Admin client。
- `.github/workflows/ci.yaml` 使用 JDK 25，分别执行 `mvn test` 和跳过测试的打包；手动输入 `build_image` 控制镜像构建与推送。
- Dockerfile 位于 `.cicd/Dockerfile`，复制 sample 的可执行 JAR，使用 Temurin 25 JRE；Jenkinsfile 依赖仓库外共享库。
- sample 的产物为 `sfe4j-spring-boot-sample/target/sfe4j-spring-boot-sample.jar`，默认端口 8080。
- 当前只有 sample 的 `@SpringBootTest contextLoads()`，没有文件行为或访问控制回归测试，也没有独立前端构建步骤。

## 已观察到的待改进点

以下用于后续任务评估，不表示本次文档初始化已修复：

1. **文件访问边界**：`restrict-to-base-dir` 仅在 `buildFileTree()` 使用；`readFile()` 没有同等检查，预览和下载直接调用它。目录列表的父链比较使用 `File.equals`，未进行真实路径解析，需验证 `..` 和符号链接行为。
2. **目录密码范围**：密码匹配和 session 检查只在目录列表控制器中；预览和下载没有相应验证。匹配基于原始路径字符串，需关注路径规范化及多平台分隔符。
3. **预览输出**：`file-viewer.ftlh` 用 `<#noautoesc>` 输出文件内容，能够渲染 HTML；处理不可信文件时需评估脚本执行风险，并明确文本预览与 HTML 预览的产品要求。
4. **资源与内存**：`FileUtils.readToByteArray()` 未关闭输入流；预览和下载均完整加载文件，大文件可能导致较高内存占用。
5. **异常与编码**：`readFile()` 遇到 `IOException` 返回 `null`，控制器继续解引用；参数为空也缺少统一处理。预览使用平台默认字符集，下载文件名直接拼入响应头，列表链接中的路径也需核对 URL 编码。

## 后续任务建议验证的场景

按任务范围选择：基础目录及子目录、相邻前缀目录、路径穿越、符号链接、Windows 路径、密码保护目录与子目录、嵌套密码优先级、直接访问预览/下载、文件不存在或不可读、中文与特殊字符文件名、HTML 内容及大文件。涉及文件的测试使用临时目录；不要依赖宿主机根目录或演示配置中的实际路径。

## 演进设计与开发计划

按优先级的待实施设计见 [设计索引](../design/README.md)，任务拆分与验收见 [开发计划](../development/implementation-plan.md)。这些文件描述目标方案，不代表当前功能已经实现。

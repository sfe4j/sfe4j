# AI 协作规范

本文件适用于整个仓库，是 AI 编码工具的统一项目规范。开始任务前阅读本文件；需要实现细节时阅读 `docs/ai/project-context.md` 及相关源码。默认使用中文沟通，代码标识符保持英文。

## 项目与模块边界

SFE4J 是面向 Spring Boot 应用的文件浏览、预览和下载组件。当前项目版本为 1.1.0，POM 与 CI 使用 Java 25，Spring Boot 依赖版本为 4.1.0；修改前以实际 POM 为准。

依赖方向：`sample → starter → spring-boot → core`。

- `sfe4j-core`：文件操作、配置模型、VO；保持独立于 Spring 和 Servlet。
- `sfe4j-spring-boot`：MVC 控制器、Bean 配置、FreeMarker 模板与静态资源。
- `sfe4j-spring-boot-starter`：`sfe4j.*` 属性绑定、默认值、自动配置注册。
- `sfe4j-spring-boot-sample`：可运行示例、环境配置及当前唯一的启动上下文测试。
- 根 `pom.xml` 只聚合模块，子模块不继承它；不要假设根 properties、插件或依赖管理会传递到子模块。

## 常用命令

以下命令从仓库根目录执行，要求 JDK 25 和可用 Maven：

```sh
mvn test
mvn verify
mvn -pl sfe4j-spring-boot -am test
mvn -pl sfe4j-spring-boot-sample -am package
java -jar sfe4j-spring-boot-sample/target/sfe4j-spring-boot-sample.jar --logging.file.name=./target/sfe4j.log
```

根目录没有 Maven Wrapper，各子模块有 Wrapper，但脚本当前没有执行权限。没有全局 Maven 时，可从根目录尝试 `(cd sfe4j-core && sh mvnw -f ../pom.xml test)`；Wrapper 固定 Maven 3.8.1，首次使用可能下载发行包，不能默认视为已验证可用。

单独运行 sample 的 `spring-boot:run` 前，需要先 `mvn -pl sfe4j-spring-boot-starter -am install -DskipTests`，再 `mvn -f sfe4j-spring-boot-sample/pom.xml spring-boot:run -Dspring-boot.run.arguments=--logging.file.name=./target/sfe4j.log`。日志路径相对于应用工作目录。示例默认日志路径是 `/data/logs/app.log`，本地运行应覆盖为可写目录。

## 修改约定

- 先阅读相关类、POM、模板和配置，保持改动集中；不要顺手升级依赖或重构整个项目。
- 沿用当前 Java 风格：四空格缩进、花括号同行、显式 getter/setter。不要为局部变更引入 Lombok、前端打包体系或格式化全仓库。
- 保持现有公共 API、`sfe4j.*` 配置键、路由、模板模型字段和 context path 行为兼容；有意改变行为时说明影响。
- 新增配置项时同步检查 `Sfe4jProperties`、`Sfe4jConfiguration`、`Sfe4jAutoConfiguration` 的映射、示例 YAML 和 README 配置表。
- 改自动配置时检查 `META-INF/spring.factories` 与 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`。不要只修改一处而留下过期类名。
- 模板与脚本直接位于 `sfe4j-spring-boot/src/main/resources`；不要手改 vendored Bootstrap、jQuery、DataTables 文件来实现业务功能。
- 文件处理兼顾 Linux/macOS 和 Windows 的路径、空格、中文及特殊字符。
- 不提交真实密码、令牌、机器专属路径或生成产物；保留许可证。发布、部署不属于常规本地验证。

## 安全与测试

- 文件访问变更需一起审查目录列表、预览和下载三个入口；不能只在页面中隐藏链接。
- 基础目录限制需考虑规范化路径、`..`、相邻前缀目录及符号链接；密码保护需考虑子目录、重叠规则与会话状态。P0 已把这些边界纳入统一门面并配回归测试；残余风险（TOCTOU、集成与 Windows 验证）见 P0 设计第 10 节与项目导览。
- 区分文件文本和可信 HTML，审查模板转义、URL 编码与下载响应头；不要把原始文件内容当作可信页面输入。
- 文件 I/O 使用可靠的资源关闭方式，关注大文件内存占用、缺失文件和无权限文件的错误处理。
- 行为修改增加针对性回归测试；临时目录构造文件样本，不依赖开发机上的真实数据。core/web/starter 已各自声明 JUnit 5 与 Surefire 3.5.4，sample 也在 `<build>` 中钉住同一版本（它继承 Boot parent 会拿到 3.5.6）。单模块单独构建时要用 `-am`，否则 starter 会解析仓库里的旧 `sfe4j-core` jar。
- 基础目录与保护目录必须存在，数值配置越界即启动失败；因此新增或修改示例配置时，目录必须在目标环境真实存在，否则 sample 启动上下文测试会失败。
- 文档修改执行差异与路径核对即可。代码修改至少运行相关模块测试；跨模块或依赖变更尽量运行根目录 `mvn verify`。启动上下文通过不能代表文件访问和安全逻辑已被测试。
- 测试失败时报告实际命令、原因和未验证范围，区分环境/依赖解析失败与代码失败；不得把跳过测试的打包描述为测试通过。

## 交付说明

交付时简述改了什么、验证了什么、仍有哪些限制。项目结构、构建命令或关键行为变化时同步维护本文件和项目导览。工具入口文件仅引用本文件，避免复制规则后产生分歧。

# FAWEReplace

FAWEReplace 是一个面向大型世界清理任务的 Paper 插件，依托 FastAsyncWorldEdit (FAWE) 的异步能力实现高效的批量替换与实体移除。本项目目前内置一套面向“垃圾方块回收/服务器清理”的工作流：

- 多种方块的批量替换（通过配置分组，统一替换为目标方块）
- 按指定区域拆分为切片并异步处理，支持自定义并行度
- **懒加载切片调度**：无需一次性生成数百万个任务，可直接覆盖 16w×16w 等超大区域
- **断点续跑**：服务器中断后可根据进度记录自动恢复，继续清理尚未完成的切片
- 可选实体清理（支持自定义实体类型名单）
- 实时进度与日志输出，包含替换数量和实体统计

## 快速开始

1. 构建插件：

  ```bash
  ./gradlew clean build
  ```

  生成的 `build/libs/FAWEReplace-1.0-all.jar` 即可放入服务器 `plugins/` 目录。

2. 启动服务器后编辑 `plugins/FAWEReplace/config.yml`
3. 在游戏或控制台执行命令：
  - `/faweclean start` – 启动清理
  - `/faweclean stop` – 请求停止并保存进度
  - `/faweclean status` – 查看当前状态

命令权限节点：`faweclean.use`

## 配置总览

`config.yml` 的关键选项如下（详见文件内注释）：

| 节点 | 类型 | 说明 |
|------|------|------|
| `parallel` | int | 同时运行的切片线程数，建议不超过 CPU 物理核心数 |
| `world` | string | 目标世界名称 |
| `region.x/z/y` | int | 单个切片的尺寸。`region.y` 省略时会自动覆盖整个世界高度 |
| `target.start/end` | 坐标 | 需要清理的包围盒（含Y，可省略以全高度覆盖）|
| `tiling.enabled` | bool | 是否启用切片模式（懒加载 & 续跑仅在开启时生效）|
| `fast-mode` | bool | 传递给 FAWE，保持开启可获得最佳速度 |
| `progress-log-every` | int | 每处理多少个切片输出一次日志 |
| `resume.enabled` | bool | 打开后会把进度写入数据文件，可在服务器重启后继续 |
| `resume.file` | string | 进度文件名称，默认 `progress.yml` |
| `resume.save-every` | int | 每处理多少个切片写入一次进度文件 |
| `blocks` | list | 需要替换的方块与目标方块（使用 Bukkit `Material` 名称） |
| `entities.enabled/types` | bool + list | 是否移除区域内的指定实体 |
| `skip-ungenerated-chunks` | bool | 跳过未生成的区块，避免区块数据损坏导致的错误 |
| `memory-protection.enabled` | bool | **[新]** 启用内存保护，自动监控并防止 OutOfMemoryError |
| `memory-protection.min-free-memory-percent` | double | **[新]** 最小可用内存百分比（0.0-1.0），低于此值将暂停处理 |
| `memory-protection.wait-on-low-memory-ms` | long | **[新]** 内存不足时等待时间（毫秒） |
| `memory-protection.max-memory-retries` | int | **[新]** 内存不足时的最大重试次数 |
| `performance.delay-between-batches-ms` | long | **[新]** 批次间延迟（毫秒），用于减少服务器负载 |
| `performance.delay-between-chunks-ms` | long | **[新]** 区块间延迟（毫秒） |
| `performance.gc-every-chunks` | int | **[新]** 每处理 N 个区块执行一次垃圾回收 |

> 16w×16w 地图建议搭配 `region.x/z = 512`、并行度 4~8、`resume.enabled = true`。懒加载切片会在任务运行期间按需生成，不会抢占大量内存。

### ⚠️ 内存和性能优化（重要）

**如果您遇到内存不足或服务器卡顿问题**，请参阅 [MEMORY_PROTECTION.md](MEMORY_PROTECTION.md) 获取详细的配置指南。

**推荐配置（5GB 内存服务器）：**
```yaml
parallel: 2  # 降低并行度
memory-protection:
  enabled: true
  min-free-memory-percent: 0.20  # 保留 20% 可用内存
performance:
  delay-between-batches-ms: 100  # 添加延迟减少负载
  gc-every-chunks: 50            # 定期垃圾回收
```

## 断点续跑 & 日志

- 进度文件位于 `plugins/FAWEReplace/<resume.file>`（默认 `progress.yml`），包含当前区域、切片尺寸以及已完成切片数。
- `/faweclean stop` 会触发一次强制保存；运行中也会按照 `resume.save-every` 的频率自动保存。
- 任务完成后会自动删除进度文件，确保下次从头开始。
- 运行过程会把摘要写入 `plugins/FAWEReplace/clean-log.txt`，包含每次开始/停止/完成的统计信息。

## 架构要点

- **懒加载切片调度**：使用原子计数器按需生成切片坐标，避免预先排满队列；即使是百万级切片也不会增加启动延迟。
- **多线程执行**：通过 FAWE 的 `EditSession` 在工作线程中进行替换，并可同时执行实体清理（实体操作始终在主线程调度，避免线程安全问题）。
- **进度恢复**：将切片总数、下一个切片索引、已替换/移除的数量写入 YAML，重启后验证配置一致性后自动恢复。

## 构建与开发

- Java 17
- Gradle Wrapper 已内置，执行 `./gradlew clean build`
- 如需本地测试服务器，可运行 `./gradlew runServer`

主类位于 `src/main/java/io/cong/FAWEReplace/FAWEReplace.java`，所有逻辑包含在该文件中，可按需扩展（例如增加命令参数、更多统计信息等）。

## 常见问题

1. **如何从中断处继续？**
  - 保持 `resume.enabled = true`
  - 停止任务（命令或服务器关闭）后确保 `plugins/FAWEReplace/progress.yml` 存在
  - 再次 `/faweclean start`，插件会检测配置是否与进度文件一致并继续执行

2. **进度文件报“不匹配”警告**
  - 说明世界/范围/切片尺寸与保存时不一致，请确认修改配置或删除旧的 `progress.yml`

3. **如何扩展要清理的方块？**
  - 在 `blocks` 列表中新增 `{ origin: <Material>, target: <Material> }`
  - 插件会自动把相同目标的方块合并到同一个 FAWE 操作中，减少上下文切换

4. **实体清理会阻塞吗？**
  - 实体移除在主线程串行执行，但作用范围是当前切片的包围盒。若要避免主线程抖动，可减少实体种类或调低并行度。

## 许可证

项目暂未声明许可证。如需开源发布，请自行添加 `LICENSE`。
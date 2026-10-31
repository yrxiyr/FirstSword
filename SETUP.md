# 开发环境说明

工程：**The FirstMod**（mod id `thefirstmod`）
Minecraft **1.21.1** + **NeoForge 21.1.264**，基于官方 MDK（ModDevGradle）。

## 本机环境

| 组件 | 版本 | 位置 |
|---|---|---|
| JDK | Microsoft OpenJDK 21 | `C:\Dev\jdk-21` |
| Gradle | 8.14.2（wrapper 自带） | 项目内 `gradle/` |
| NeoForge | 21.1.264 | Gradle 依赖 |
| Parchment 映射 | 2024.11.17 | Gradle 依赖 |
| IntelliJ IDEA | 2025.3（含免费授权） | `C:\Dev\IntelliJ` |

## 网络 / 镜像

本机无法访问 `maven.neoforged.net`（连接超时），已做两处重定向到 NeoForged 官方
CDN 镜像 `https://neoforged.forgecdn.net/releases/`：

1. **全局**：`%USERPROFILE%\.gradle\init.d\neoforged-mirror.gradle`
   把任何指向 `maven.neoforged.net` 的仓库**整个域名**替换为 `neoforged.forgecdn.net`，
   对本机所有 Gradle 工程生效。注意必须整域名替换——NeoForge 同时用到
   `/releases` 和 `/mojang-meta` 两个路径前缀，只改 `/releases` 会漏掉
   `minecraft-dependencies` 等构件。
2. **本工程**：`settings.gradle` 的 `pluginManagement.repositories` 里显式加了镜像源。

> 注意：BMCLAPI 的 maven 镜像（`bmclapi2.bangbang93.com/maven`）也试过，但缺少
> `neoform-runtime`、`moddev-gradle` 等构件，构建会失败。forgecdn 是完整镜像。

Gradle 发行版、Maven Central、Mojang 资源、Parchment 均直连，无需代理。

## 常用命令

在项目根目录执行：

```powershell
.\gradlew build          # 编译并打包，产物在 build/libs/
.\gradlew runClient      # 启动带 mod 的客户端（首次会下载 Minecraft 资源和库，较慢）
.\gradlew runServer      # 启动服务端（--nogui）
.\gradlew runData        # 数据生成，输出到 src/generated/resources/
.\gradlew --refresh-dependencies build   # 依赖缓存异常时强制刷新
```

## 目录结构

```
TheFirstMod/
├─ gradle.properties          # 版本号 + mod 元数据（改这里）
├─ settings.gradle            # 仓库配置 + 镜像源
├─ build.gradle               # ModDevGradle 配置、run 配置
├─ src/main/java/com/thefirstmod/
│  ├─ TheFirstMod.java        # 主类：注册物品 + 创造模式标签页
│  └─ Config.java             # 配置（logDirtBlock / magicNumber 等示例项）
├─ src/main/resources/
│  ├─ assets/thefirstmod/
│  │  ├─ lang/                # en_us.json / zh_cn.json
│  │  ├─ models/item/         # 物品模型 JSON
│  │  └─ textures/item/       # 16×16 贴图 PNG
│  └─ data/thefirstmod/
│     └─ recipe/              # 合成配方（1.21 起是单数 recipe）
└─ src/main/templates/META-INF/neoforge.mods.toml   # 由 gradle 变量填充
```

`neoforge.mods.toml` 不直接编辑，它由 `gradle.properties` 里的变量在构建时展开生成。

## 当前 mod 信息

```properties
mod_id=thefirstmod
mod_name=The FirstMod
mod_group_id=com.thefirstmod
mod_authors=YourNameHere, OtherNameHere
```

## 当前内容

| id | 类型 | 说明 |
|---|---|---|
| `thefirstmod:first_sword` | 剑 | 中文名「初锋」，钻石剑基底，面板攻击力 14、攻速 -2.2 |
| `thefirstmod:first_tab` | 创造模式标签页 | 显示名 "The FirstMod"，排在战斗标签页之前 |
| `thefirstmod:rainbow_lightning` | 实体 | 点按右键召唤的彩虹闪电，范围伤害 + 击退，不点燃、不触发避雷针 |
| `thefirstmod:rainbow_meteor` | 实体 | 长按右键召唤的流星，垂直下落，落地范围伤害 |

右键使用初锋：在玩家周围 **30 格**范围内落 **3 波彩虹雷电，每波 36 道**，
冷却 1.5 秒（30 tick）。

- 第一波 36 道同时落下，之后每隔 3 tick（约 0.15 秒）补一波
- 每一道都在玩家周围重新挑随机落点，三波合计 108 道
- 落点距离在 `0 ~ radius` 之间**均匀取**（不是只在外圈），所以玩家脚边也会有雷，
  近战范围内不会出现空心的圈
- 半径改大到 30 格后密度会相应变稀，想要更密就加 `BOLTS_PER_WAVE`
- 每道存在 8 tick（0.4 秒），**恒定亮度，无淡出**
- 波数改 `FirstSwordItem.WAVE_COUNT`，每波道数改 `BOLTS_PER_WAVE`，
  间隔改 `RainbowLightning.STRIKE_DELAY`
- 每道雷各自结算范围伤害（15 点 + 击退 1.2），但**一发之内同一目标最多吃一次**
- **只伤害敌对生物**：用原版的 `Enemy` 接口判定（`Monster` 实现它，覆盖僵尸、
  骷髅、苦力怕、蜘蛛、史莱姆、恶魂、掠夺者等）。动物、村民、宠物、其他玩家都不会被劈到
- 实现上是 36 条并行链：每一道在 `tickCount == STRIKE_DELAY` 时生成自己的下一波，
  传递 `followUps`、锚点、施法者 UUID 和去重集合，不需要全局定时器

### 第二套攻击：长按 J 的流星群

右键专用于雷击。流星独立绑定 **J 键**（可在「选项 → 控制」里改），冷却 **32 秒**。

按住 J 蓄力 0.4~3 秒，松手释放。**大流星的落点由准星决定**：从视线方向打一条
32 格射线，砸在准星指到的那个方块上方；抬头看天时落在最远射程处的地面上。
小流星则围绕大流星的落点、在 18 格半径内按面积均匀散开。

**蓄力越久小流星越多：24 → 96 颗（满蓄 3 秒）。**

小流星不是一次性全落，而是在 **约 1.85 秒内陆续生成**（推迟 0~37 tick），
**大流星压轴**，固定 2 秒后才生成。延迟由 `RainbowMeteor` 里的静态队列
`PENDING` + `ServerTickEvent.Post` 驱动，服务端停止时清空。

> 密度 = 数量 ÷ 持续时间。这一版密度是上一版（64 颗 / 3.75 秒）的约 3 倍。

| | 大流星 | 小流星 |
|---|---|---|
| 数量 | 固定 1 颗 | 24~96 颗（随蓄力） |
| 伤害 | 64 | 8 |
| 伤害半径 | 6 格 | 2.5 格 |
| 击退 | 2.0 | 1.2 |
| 砸坑 | 半径 9 格、每列深 4~5 层（坑底起伏） | 半径 2 格、深 1 层 |
| 单颗破坏上限 | 1000 块（等于不限） | 40 块（约 31 块浅盘） |
| 留火 | **坑底 60% 的列铺火** | 30% 概率，1 处 |

- 从落点上空 50 格垂直落下，速度 2 格/tick
- **拖尾约 8 格长**：客户端每 tick 沿**飞行方向的反方向**撒彩色粒子。
  大流星垂直下落、尾迹竖直（10 个采样点、粒子尺寸 ×2）；
  小流星带随机水平漂移（0.35~0.75 格/tick），所以尾迹是斜的，
  落点仍然在预定位置——生成点会按漂移量反向偏移。离玩家超过 64 格不撒
- 落地爆粒子（大流星 12 组、小流星 2 组），不附带闪电
- 砸坑会跳过空气、液体、带方块实体的方块（箱子等）和不可破坏方块（基岩等），
  掉落物不掉落；火在破坏后重新找地表放置，保证下方有方块托着
- 只打 `Enemy`，同一次释放里每个目标最多吃一次（大小流星共享去重集合）

**蓄力反相**：蓄力超过 35% 后整屏颜色反转，类似时停。实现见 `ChargeOverlay`：
自定义 `RenderType`，混合设成 `dst = src * (1 - dst)`，src 取白就是完整反相。

实现要点：
- 按键在客户端：`KeyBindings.METEOR` + `MeteorKeyHandler` 累计蓄力，
  松手时 `PacketDistributor.sendToServer(new MeteorCastPayload(power))`
- 服务端在 `ModNetwork` 里收包，**重新校验**（手上拿着初锋、不在冷却）后调用 `RainbowMeteor.cast`
- 客户端只负责计时和发请求，生成与伤害全在服务端
- `RainbowMeteor` 只在服务端做位移和判定；**拖尾粒子在客户端 `tick()` 里本地生成**，
  不走网络同步
- 本体不渲染，注册了空渲染器 `MeteorRenderer`；颜色靠 `SynchedEntityData` 同步给客户端
- **踩坑**：`RenderGuiEvent.Post` 触发时 GUI 的顶点还在缓冲区里没提交。
  若在这里 `endBatch` 立即画，会被随后 GUI 的统一 flush 盖掉。正确做法是把顶点
  加进同一个 `bufferSource`，让帧末 flush 一起画（我们的层最后加入，自然在最上面）

> 注意：右键雷击和 J 流星共用同一个物品冷却（Minecraft 的冷却按物品记），
> 所以放完流星 2 秒内右键雷也按不出来，反之亦然。要彻底分离得自己存一份冷却状态。

> 颜色调校的物理约束：闪电走的 `RenderType.lightning()` 是加色混合
> （`blendFunc SRC_ALPHA, ONE`），四层同色在核心区叠加约 1.2 倍，任何通道超过 1
> 就被钳制成白。所以**极淡的颜色会整体发白、极浓的颜色会发闷**，取中间值最好：
> 芯是白热的，外圈带彩色光晕。
>
> 两个可调旋钮：
> - 色板在 `RainbowLightning.PALETTE`，往 `0xFF` 提更亮/更白，往 `0x40` 压更浓/更闷
> - 亮度整体在 `RainbowLightningRenderer.ALPHA`（当前 0.3），调低更实但更暗

伤害规则：
- 每道闪电对落点 3 格半径内的生物造成 **15 点伤害**（7.5 颗心）+ **1.2 强度击退**
  （原版普通攻击是 0.4）
- 一次右键里同一目标最多吃一次伤害，不会 16 道叠加
- 伤害源记在玩家头上，掉落物和经验归玩家
- 不会伤到施法者自己，也不会打到创造/旁观模式的玩家

实现要点：
- `RainbowLightning` 直接继承 `Entity`，**不继承原版 `LightningBolt`**。
  原版实体会点燃方块、给避雷针充能、清除铜锈，并播放音量 10000 的雷声；
  `visualOnly` 只能关掉伤害和点燃，其余关不掉。
- `RainbowLightningRenderer` 是原版 `LightningBoltRenderer` 的拷贝，把写死的
  `0.45/0.45/0.5` 换成实体自带的颜色，所以**不需要 Mixin**。
- 伤害在调用 `summon()` 时一次性结算，不做持续判定；同一个目标用 `Set` 去重。
- 颜色通过 `SynchedEntityData` 同步到客户端；形状种子用 public 的 `seed` 字段。

配方：2 钻石 + 1 木棍（与原版钻石剑同形）。也可以直接
`/give @s thefirstmod:first_sword`，或从创造模式 "The FirstMod" 标签页取。

需要再改包名时，用 IDEA 的 Refactor → Rename 改，再同步 `gradle.properties`
里的 `mod_group_id`。`mod_id` 必须保持 `thefirstmod`，与 `TheFirstMod.MODID` 一致。

## 常见问题

- **Gradle 卡在下载**：先确认是否在拉 `maven.neoforged.net` 的包；正常情况下已被 init script 改写。
- **`java` 找不到**：重新开一个 PowerShell 窗口，环境变量需要新会话才生效。
- **首次 runClient 很慢**：要下载约 400MB 的 Minecraft 资源与库，属正常。
- **配置缓存报错**：`org.gradle.configuration-cache` 已设为 `false`。ModDevGradle 2.0.91
  的 `createMinecraftArtifacts` 任务在配置缓存下会序列化失败，不要打开。

## 发布

```powershell
.\gradlew build
```

产物 `build/libs/thefirstmod-1.0.0.jar` 就是可分发的模组包，直接放进目标版本的
`mods/` 文件夹即可，不需要额外转换或混淆步骤（NeoForge 1.21 运行时就用官方映射）。

`build.gradle` 里已排除 `.cache/**`，避免数据生成缓存被打进 jar。

桌面上的 `TheFirstMod-1.0.0\` 是发布副本，含 jar 和安装说明。

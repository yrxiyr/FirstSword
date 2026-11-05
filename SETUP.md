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
| `thefirstmod:rainbow_meteor` | 实体 | 长按 J 召唤的流星，45° 斜落，落地范围伤害 |
| `thefirstmod:meteor_portal` | 实体 | 流星出生平面上的彩虹法阵，纯视觉 |
| `thefirstmod:blink_trail` | 实体 | K 瞬闪斩留下的斩痕，纯视觉 |
| `thefirstmod:slash_arc` | 实体 | 冲刺时的月牙刀光，程序化几何，纯视觉 |

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
| 伤害 | 96 | 8 |
| 伤害半径 | 18 格（半径外再高 12.6 / 深 9 的立体范围） | 2.5 格 |
| 击退 | 2.0 | 1.2 |
| 砸坑 | 半径 9 格、每列深 4~5 层（坑底起伏） | 半径 2 格、深 1 层 |
| 单颗破坏上限 | 1000 块（等于不限） | 40 块（约 31 块浅盘） |
| 留火 | **坑底 60% 的列铺火** | 30% 概率，1 处 |

- 从落点上空 50 格落下，竖直速度 2 格/tick
- **所有流星统一以 45° 夹角（与地面）斜插下来**，大流星也一样，不再垂直下落。
  水平漂移由 `DRIFT_SPEED = 2.0 / tan(45°) = 2.0` 格/tick 推出，
  夹角改 `IMPACT_ANGLE` 即可（90° 就是竖直，越小越躺平）
- 同一场雨里**所有流星共用一个方位角**，取玩家视线的水平方向，所以它们是平行落下的，
  看起来像同一场流星雨，而不是各飞各的；法阵也因此在准星正前方的空中
  （正上/正下方看时退化成一个随机方位）
- **拖尾约 8 格长**：客户端每 tick 沿**飞行方向的反方向**撒彩色粒子
  （大流星 16 个采样点、尺寸 3.6；小流星 9 个点、尺寸 2.0）。
  落点仍然在预定位置——生成点会按漂移量反向偏移（45° 下 50 格高偏 50 格）。
  离玩家超过 128 格不撒
- **拖尾亮度**：dust 粒子没有自发光通道，所以把色板颜色往白色插值
  `TRAIL_WHITEN = 0.55` 来提亮（0 = 原色，1 = 纯白）；
  火焰每 tick/隔 tick 一颗、电火花 50% 概率，密度比早期版本高
- 落地爆粒子（大流星 12 组、小流星 2 组），不附带闪电
- 砸坑会跳过空气、液体、带方块实体的方块（箱子等）和不可破坏方块（基岩等），
  掉落物不掉落；火在破坏后重新找地表放置，保证下方有方块托着
- 只打 `Enemy`。**小流星之间**共享一份去重集合，同一发里不会对同一个怪叠加几十次 8 点；
  **大流星单独一份**，所以被小流星打过的怪仍然会吃到大流星那 64 点。
  （踩坑：以前大小流星共用集合，而满蓄 96 颗小流星在 18 格内几乎覆盖了所有落点，
  怪全被小流星标记过，导致大流星一个也打不到）

**空中法阵**：`MeteorPortal` 实体 + `MeteorPortalRenderer`，纯视觉，不参与判定。

- 位置就是大流星的出生点（`落点 - 漂移 × 25 tick`），也就是准星前方 50 格、
  50 格高的空中；平面垂直于飞行方向，所以流星是「穿过法阵」飞出来的
- 飞行方向用 `SynchedEntityData` 存三个 float，客户端靠它算法阵平面
- 几何全程序化生成，**不用贴图**：外圈粗环、内圈细环（反向转）、24 根辐条、六芒星。
  颜色沿圆周走整个色相，色相和角度都随时间旋转
- 平面里取正交基：`right = (0,1,0) × dir`，`up = dir × right`；
  平面坐标 (u, v) 映射回实体本地坐标：`right * u + up * v`
- 自定义 `RenderType`：`POSITION_COLOR` + `ADDITIVE_TRANSPARENCY` + `NO_CULL` +
  `COLOR_WRITE`（**不写深度**），所以法阵只往画面上加光，不会挡住后面的东西
- 存活 60 tick（3 秒），前 8 tick 淡入、最后 14 tick 淡出，中间带呼吸
- 两个坑：
  - `Entity` 尺寸是 0 时，`shouldRenderAtSqrDistance` 默认按碰撞箱大小算，
    永远返回 false，必须自己重写
  - 同样因为尺寸是 0，`getBoundingBoxForCulling` 只是个点，48 格宽的法阵会被视锥剔除；
    要 `inflate(RADIUS)`

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

### 第三套攻击：K 瞬闪斩

按下 K，朝准星方向冲刺 8 格（0.1 秒），路径上的敌人被切开。冷却 8 秒。

| | 数值 |
|---|---|
| 总距离 | 8 格 |
| 持续时间 | 2 tick（0.1 秒） |
| 每 tick 位移 | 4 格 |
| 路径判定半径 | 2.4 格 |
| 路径伤害 | 30 |
| 落点补刀 | 20 点 / 半径 3.5 格 |
| 击退 | 1.4（沿冲刺方向） |
| 冷却 | 160 tick（8 秒） |

实现要点：

- **是速度推进，不是瞬移**。每 tick 覆盖一次玩家速度并置 `hurtMarked`，
  服务端把速度通过 `ClientboundSetEntityMotionPacket` 发给客户端，
  客户端拿同一份速度做本地模拟——所以镜头连续移动，没有瞬移的点一下黑屏感
- 竖直速度固定给 **0.1**（`FLOAT_UP`），让脚离地一点点。
  原版视角摇晃（`Player.bob`）只在 `onGround` 时累积，离地就没有那种一颠一颠的抖动；
  悬浮量很小，落地不会有坠伤
- **只取视线的水平分量**：玩家站在地面时脚底正贴着方块顶面，
  视线稍微向下，第一步就会钻进地里（早期版本实测位移恒为 0，就是这么来的）
- 伤害每 tick 结算一次玩家身边的范围，整次冲刺共享一个去重集合，
  所以同一个怪最多吃一次
- 命中时用 `ServerLevel.sendParticles` 在原版 `ParticleTypes.CRIT` 撒 16 颗白星粒子，
  给每次斩击一个明确的打击反馈；粒子只在 `hurt()` 真正生效时才生成，
  打在无敌帧上的攻击不会空放特效
- **砍到怪会刹车**：`hurt()` 之后看 `target.isAlive()`——还站着说明这一刀没能带走它，
  速度乘 `SLOW_ON_SURVIVE`（0.15）；被秒掉的乘 `SLOW_ON_KILL`（1.0，也就是不减速——
  一路秒杀还要一顿一顿的话手感很粘）。
  倍率存在 `Dash.speedScale` 里，`push()` 时乘到速度上。
  tick 内的顺序是「先结算伤害，再 push 速度」，所以同一 tick 命中就能当 tick 生效
- **收尾那一 tick 也必须 push 一次**。冲刺只有 2 tick，远端的怪往往是在最后一 tick 才被打到；
  如果末尾直接 `remove` 不再推速度，玩家会带着冲刺原速（4 格/tick）继续滑出去，
  看起来就是「只有贴脸砍才刹得住，远了还是冲过头」
- 伤害判定按「上一帧 → 当前帧」的**线段扫掠**，不是只在 tick 末放一个盒子：
  每 tick 跨 4 格，盒子之间的空隙会漏掉被跨过去的怪
- **刀光 `SlashArc`**：纯视觉实体，几何全程序化生成（`client.SlashArcRenderer`）。
  把平面内的极坐标 (角度, 半径) 映射成顶点，宽度按 `sin(pi*t)` 走——
  中间最厚、两个刀尖是尖的，配合加法混合就是一道发光的月牙。
  平面法线随机给一个向量，所以每道刀光朝向都不一样，看起来像是在乱砍；
  `yRot` 同时当平面内的起始角和色相偏移。存活 8 tick
- 刀光分两组，都在**收刀那一刻一次性生成**：
  - **路径**（`spawnPathSlashes`）：把 `start → 落点` 均匀铺满，
    间距 `PATH_SLASH_SPACING = 0.8` 格，位置带 ±0.4 的随机抖动，避免排成一条直线
  - **落点**（`spawnStopBurst`）：8 道，以冲刺方向为中线左右各铺 120°、
    距离 0.8~2.4 格，保证大半落在视野里
- 为什么不逐 tick 撒：冲刺只有 2 tick，逐 tick 一次只能出 4 道，分布太粗；
  等收刀时路径已经确定，一次性按最终落点铺满更均匀，也省得重复生成
- 色相 → RGB 抽到了 `client.RainbowHue`，法阵和刀光共用。
  和 `MeteorPortal` 一样，0 尺寸实体必须重写 `shouldRenderAtSqrDistance`
  和 `getBoundingBoxForCulling`，否则不渲染或被视锥剔除
- **击退方向要取反**：`LivingEntity.knockback` 内部是「减去」传入的方向向量，
  想把人沿冲刺方向推出去就要传 `-delta`
- 一格高的台阶（玩家跳跃高度只有 0.6）会挡住速度推进，所以卡住时抬高 1 格继续：
  抬升总量锁在 1.5 格以内（爬不了墙），且**提上去的位置必须先验证是空的**
- 冷却存在 `Map<UUID, Long>` 里，**不占物品冷却**。
  否则放完流星要等 32 秒才能位移，手感会很差。服务端在 `ServerStoppedEvent` 清空
- 斩痕 `BlinkTrail` 是纯视觉实体：起点是本体的位置，终点放在 `SynchedEntityData`，
  客户端每 tick 沿线段撒 dust + 偶尔一颗 `END_ROD`，强度按剩余时间平方衰减。
  冲刺期间每 tick 生成一段，连起来就是跟着人走的整条尾迹
- 客户端 `client.BlinkKeyHandler` 用 `isDown()` 的上升沿触发，只做「手上有没有初锋」的粗筛，
  距离/伤害/冷却全部在服务端 `ModNetwork.handleBlink` 里重新算

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

产物 `build/libs/thefirstmod-1.6.0.jar` 就是可分发的模组包，直接放进目标版本的
`mods/` 文件夹即可，不需要额外转换或混淆步骤（NeoForge 1.21 运行时就用官方映射）。

`build.gradle` 里已排除 `.cache/**`，避免数据生成缓存被打进 jar。

桌面上的 `TheFirstMod-1.6.0\` 是发布副本，含 jar 和安装说明。

# Too Many Tinkers

**Too Many Tinkers** 是一个面向 **Minecraft 1.12.2（Cleanroom）** 的匠魂（Tinkers' Construct）客户端渲染优化模组，专为包含大量匠魂材质与附属模组的大型整合包（如 GreedyCraft2）设计。

它保留匠魂原生工具、部件及附属模型的外观与功能契约，将原本在资源加载阶段全量预生成并拼入主图集的材质纹理与部件模型，改为**按需生成、二维纹理页管理与有界 LRU 缓存**，从而显著降低主图集压力、内存/显存占用与重载卡顿。

在 GreedyCraft2 整合包 (818种匠魂材料) 中，匠魂材质预烘焙所占内存由高达 2G 降低为 <0.05MB，显著降低了整合包的内存需求，并且游戏内匠魂工具渲染性能没有任何影响。

---

## 运行环境与依赖

- **游戏版本**：Minecraft `1.12.2`
- **加载器**：Cleanroom Loader (必须，不支持 Forge)
- **Java 版本**：仅支持 Java `25`

### 附属模组兼容

如**MoreTcon**等通过标准匠魂 API 注册材质与部件的模组，本 mod 可以直接接管其渲染路径。

拥有独立渲染路径的模组通过条件 Mixin 按需启用兼容与适配：

- **Construct's Armory**：为匠魂护甲动态合成纹理提供有界缓存（16 MiB / 256 项）并复用 `ResourceLocation` 槽位，避免穿戴渲染导致路径缓存与纹理无限增长。
- **Tinkers' Things 2**：在 Scout 护甲纹理查询中保留精确匹配约束，确保底图染色与遮罩流程正常执行。
- **Tinkers' Evolution**：为 `CosmicMaterialRenderInfo` 等依赖主图集采样的材质保留原生主图集注册与 UV，并接管代理模型缓存的生命周期清理。
- **Had Enough Items / JEI**：自动识别包含 TMT 托管纹理的物品模型，使其回退至标准物品渲染路径，绕过 HEI 批渲染限制。

---

## 核心优化机制

- **按需纹理生成与二维纹理页（`NativeMaterialTextures`）**
  - 拦截 `CustomTextureCreator` 在主图集预构建全部部件×材质纹理的流程，仅在渲染或模型请求时按需生成。
  - 将生成后的纹理动态打包至 `1024 × 1024` 的二维纹理页中，支持基于像素内容摘要（`PixelContentKey`）的纹理去重。
  - 采用有界内存与显存预算：GPU 纹理页上限 **64 MiB**、预处理缓存上限 **16 MiB**、动画帧缓冲上限 **32 MiB**，超出预算时按使用热度淘汰并在需要时重新驻留上传。
- **主图集契约保留（`NativeAtlasMaterial`）**
  - 对于无尽（Cosmic）等需要直接从主图集采样的特殊材质，继续注册到原版主图集 `TextureMap`，保证着色器特效正常显示。
- **按需部件模型烘焙（`TmtLazyMaterialModel`）**
  - 保留 `BakedMaterialModel` 接口，将逐材质预烘焙改为按需单材质烘焙，配合有界 `WeightedLruCache`（上限 4096 项 / 16 MiB 估算权重）管理模型生命周期。
- **工作台摆放模型缓存（`TableItemModels`）**
  - 缓存匠魂工作台等 `TableItem` 经过空间变换后的已烘焙顶点模型（上限 256 项 / 4 MiB），避免逐帧重复执行 `TRSRBakedModel` 矩阵变换。
- **资源与世界生命周期清理（`RenderLifecycle`）**
  - 在资源重载（`TextureStitchEvent.Pre`）与客户端退出世界（`WorldEvent.Unload`）时统一清理模型缓存并释放由 TMT 托管的 GPU 纹理资源。

---

## 诊断命令

模组内置了客户端指令 `/tmt`（权限等级 0），用于查看缓存状态与排查渲染或性能问题：

| 命令 | 说明 |
| :--- | :--- |
| `/tmt stats` | 在聊天栏及日志中输出材质纹理、模型、四边面、遮罩、工作台物品与护甲缓存的实时统计信息。 |
| `/tmt dump` | 导出当前客户端状态快照到 `<游戏目录>/tmt-diagnostics/<时间戳>.json`，包含堆内存信息、各类缓存统计、资源包、材质与工具/部件注册表、活跃模组及主手物品等信息。 |
| `/tmt profile start` | 开始记录客户端 `RenderTick` 帧间隔样本（最多保留最近 18,000 帧）。 |
| `/tmt profile stop` | 停止采样并自动生成包含帧间隔统计的诊断 JSON 报告。 |

---

## 从源码构建

本项目使用 Gradle + Unimined 构建，需提前准备 **JDK 25**。

```powershell
# Windows
.\gradlew.bat build --console=plain
```

```bash
# Linux / macOS
./gradlew build --console=plain
```

---

## 作者与许可证

- **作者**：smd & Shiver
- **许可证**：基于 [MIT License](LICENSE) 开源

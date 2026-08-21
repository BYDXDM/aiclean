# AIClean 项目总结

## 已完成的工作

### 1. 项目架构（参考 SD Maid SE）
- ✅ 模块化设计（Core / UI / DI 分层）
- ✅ Kotlin + Jetpack Compose
- ✅ Hilt 依赖注入
- ✅ MVVM 架构

### 2. 核心模块

#### 存储扫描器 (`core/scanner/`)
- `StorageScanner.kt` - 主扫描引擎
  - 扫描所有已安装应用的缓存
  - 扫描垃圾文件（tmp, temp, log, bak 等）
  - 查找重复文件（MD5 哈希比对）
  - 获取存储统计信息
  - 实时进度反馈

#### AI 集成 (`core/ai/`)
- `AIService.kt` - AI API 调用服务
  - 支持 OpenAI / DashScope / DeepSeek / Ollama
  - 智能分析应用缓存价值
  - 生成清理建议
  - 对话式查询接口
- `AIModels.kt` - 数据模型

#### 存储清理器 (`core/cleaner/`)
- `StorageCleaner.kt` - 清理执行器
  - 清理应用缓存
  - 清理垃圾文件
  - 清理重复文件
  - 返回清理结果统计
  - 支持 Root/Shizuku 权限提升

#### 设置管理 (`core/settings/`)
- `SettingsRepository.kt` - DataStore 偏好设置
  - API Key 存储
  - AI Provider 选择
  - Model / Base URL / Max Tokens / Temperature

#### Root/Shizuku 支持 (`core/root/` & `core/shizuku/`)
- `RootManager.kt` - Root 权限管理
  - 检测 Root 状态
  - 以 Root 执行命令
  - Root 方式清理缓存/删除文件
- `ShizukuManager.kt` - Shizuku 权限管理
  - Shizuku 权限请求与检查
  - 以 Shizuku 执行命令
  - Shizuku 方式清理缓存/删除文件

#### 定时任务 (`core/scheduler/`)
- `ScheduledTask.kt` - 定时任务数据模型
  - 支持每日/一次任务
  - 任务类型：清理缓存/垃圾/重复/全量

### 3. UI 界面

#### 首页 (`ui/screens/home/`)
- 存储概览卡片（使用率、缓存、垃圾、重复）
- 权限级别指示器（Normal/Root/Shizuku）
- 扫描进度显示
- AI 分析入口
- 快速操作按钮

#### 应用列表 (`ui/screens/apps/`)
- 显示所有应用及其缓存大小
- 支持多选清理
- AI 智能推荐标注
- 缓存价值颜色标识（红色 > 100MB）

#### 重复文件 (`ui/screens/duplicates/`)
- 显示所有重复文件分组
- 每组显示文件数量和浪费空间
- 支持选择性删除
- 智能保留第一个文件

#### 存储分析 (`ui/screens/storage/`)
- 圆形进度显示存储使用率
- 按类别统计（图片/视频/音频/文档/应用）
- Top 应用存储排行
- 可视化存储分布

#### 垃圾文件 (`ui/screens/junk/`)
- 显示所有垃圾文件
- 按类别筛选（Temp/Log/Cache）
- 文件类型图标
- 支持多选清理

#### 定时任务 (`ui/screens/scheduler/`)
- 添加定时清理任务
- 设置时间（24小时制）
- 每日/一次选项
- 任务开关控制
- 删除任务

#### 设置页面 (`ui/screens/settings/`)
- AI Provider 下拉选择
- API Key 输入（密码遮罩）
- Base URL / Model / Max Tokens 配置
- Temperature 滑块调节
- 帮助文档

### 4. 项目配置

- `build.gradle.kts` - 项目级构建配置
- `app/build.gradle.kts` - 应用级构建配置（含 Shizuku 依赖）
- `settings.gradle.kts` - Gradle 设置
- `gradle.properties` - Gradle 属性
- `proguard-rules.pro` - 混淆规则
- `.gitignore` - Git 忽略规则
- `.github/workflows/android.yml` - CI/CD 流水线
- `AndroidManifest.xml` - 应用清单（含 Shizuku Provider）
- `README.md` - 项目文档
- `LICENSE` - MIT 许可证

---

## 项目结构

```
aiclean/
├── .github/
│   └── workflows/
│       └── android.yml
├── app/
│   ├── src/main/
│   │   ├── java/com/example/aiclean/
│   │   │   ├── core/
│   │   │   │   ├── ai/
│   │   │   │   │   ├── AIModels.kt
│   │   │   │   │   └── AIService.kt
│   │   │   │   ├── cleaner/
│   │   │   │   │   └── StorageCleaner.kt
│   │   │   │   ├── root/
│   │   │   │   │   └── RootManager.kt
│   │   │   │   ├── scanner/
│   │   │   │   │   ├── ScanModels.kt
│   │   │   │   │   └── StorageScanner.kt
│   │   │   │   ├── scheduler/
│   │   │   │   │   └── ScheduledTask.kt
│   │   │   │   ├── settings/
│   │   │   │   │   └── SettingsRepository.kt
│   │   │   │   └── shizuku/
│   │   │   │       └── ShizukuManager.kt
│   │   │   ├── di/
│   │   │   │   └── AppModule.kt
│   │   │   ├── ui/
│   │   │   │   ├── components/
│   │   │   │   │   └── AccessLevelCard.kt
│   │   │   │   ├── navigation/
│   │   │   │   │   └── AppNavigation.kt
│   │   │   │   ├── screens/
│   │   │   │   │   ├── apps/
│   │   │   │   │   │   ├── AppsScreen.kt
│   │   │   │   │   │   └── AppsViewModel.kt
│   │   │   │   │   ├── duplicates/
│   │   │   │   │   │   ├── DuplicatesScreen.kt
│   │   │   │   │   │   └── DuplicatesViewModel.kt
│   │   │   │   │   ├── home/
│   │   │   │   │   │   ├── HomeScreen.kt
│   │   │   │   │   │   └── HomeViewModel.kt
│   │   │   │   │   ├── junk/
│   │   │   │   │   │   ├── JunkFilesScreen.kt
│   │   │   │   │   │   └── JunkFilesViewModel.kt
│   │   │   │   │   ├── scheduler/
│   │   │   │   │   │   ├── SchedulerScreen.kt
│   │   │   │   │   │   └── SchedulerViewModel.kt
│   │   │   │   │   ├── settings/
│   │   │   │   │   │   ├── SettingsScreen.kt
│   │   │   │   │   │   └── SettingsViewModel.kt
│   │   │   │   │   └── storage/
│   │   │   │   │       ├── StorageScreen.kt
│   │   │   │   │       └── StorageViewModel.kt
│   │   │   │   └── theme/
│   │   │   │       ├── Theme.kt
│   │   │   │       └── Type.kt
│   │   │   ├── AICleanApp.kt
│   │   │   └── MainActivity.kt
│   │   ├── res/
│   │   │   ├── drawable/
│   │   │   │   └── ic_launcher_foreground.xml
│   │   │   ├── mipmap-anydpi-v26/
│   │   │   │   ├── ic_launcher.xml
│   │   │   │   └── ic_launcher_round.xml
│   │   │   └── values/
│   │   │       ├── colors.xml
│   │   │       ├── strings.xml
│   │   │       └── themes.xml
│   │   └── AndroidManifest.xml
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
├── .gitignore
├── LICENSE
└── README.md
```

---

## 如何构建

### 前提条件
- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17
- Android SDK 34

### 步骤

1. **克隆项目**
   ```bash
   git clone <your-repo-url>
   cd aiclean
   ```

2. **用 Android Studio 打开项目**
   - File → Open → 选择 aiclean 目录
   - 等待 Gradle 同步完成

3. **构建 Debug APK**
   ```bash
   ./gradlew assembleDebug
   ```
   APK 位置: `app/build/outputs/apk/debug/app-debug.apk`

4. **构建 Release APK**
   ```bash
   ./gradlew assembleRelease
   ```
   APK 位置: `app/build/outputs/apk/release/app-release-unsigned.apk`

---

## 功能亮点

### AI 增强清理

1. **智能分析**
   - 分析每个应用的缓存价值
   - 基于使用频率、最后使用时间、缓存类型给出建议
   - 区分系统应用和用户应用

2. **个性化建议**
   - "微信缓存 2.3GB，其中 80% 是可再生的视频缓存"
   - "抖音已 30 天未使用，建议清理"
   - "系统缓存建议保留，可能影响性能"

3. **对话式查询**
   - "哪些应用占空间最多？"
   - "哪些应用我很久没用了？"
   - "清理后能释放多少空间？"

### 权限提升支持

| 权限级别 | 功能 | 说明 |
|---------|------|------|
| Normal | 基础清理 | 只能清理可访问的缓存 |
| Root | 完全清理 | 可清理所有应用缓存 |
| Shizuku | 高级清理 | 无需 Root 的高级权限 |

### 定时任务

- 每日自动清理（如凌晨 3 点）
- 一次任务（立即执行）
- 自定义任务名称
- 任务开关控制

### 对比传统清理工具

| 特性 | 传统清理工具 | AIClean |
|------|-------------|---------|
| 缓存清理 | ✅ 一键全清 | ✅ 智能推荐 |
| 重复文件 | ✅ 按名称/大小 | ✅ MD5 哈希 |
| 垃圾文件 | ✅ 扩展名匹配 | ✅ AI 识别 |
| 使用建议 | ❌ 无 | ✅ AI 分析 |
| 个性化 | ❌ 无 | ✅ 基于习惯 |
| 对话查询 | ❌ 无 | ✅ 支持 |
| 定时任务 | ❌ 无 | ✅ 支持 |
| Root 支持 | ✅ 部分 | ✅ 完全 |
| Shizuku | ❌ 无 | ✅ 支持 |

---

## 技术栈

- **语言**: Kotlin 1.9.20
- **UI**: Jetpack Compose + Material 3
- **架构**: MVVM + Clean Architecture
- **DI**: Hilt 2.48
- **异步**: Coroutines + Flow
- **网络**: OkHttp 4.12 + Retrofit 2.9
- **存储**: DataStore + Room (预留)
- **权限**: Shizuku 13.1.5
- **最低版本**: Android 8.0 (API 26)
- **目标版本**: Android 14 (API 34)

---

## 文件统计

- **Kotlin 文件**: 30 个
- **XML 资源**: 7 个
- **配置文件**: 6 个
- **文档**: 4 个

---

## 待优化

- [ ] Room 数据库存储扫描结果
- [ ] 清理历史记录
- [ ] 应用详情页面
- [ ] 更多 AI Provider 支持
- [ ] 多语言支持
- [ ] 深色模式优化
- [ ] 性能优化
- [ ] 单元测试

---

## 许可证

MIT License - 详见 [LICENSE](LICENSE)

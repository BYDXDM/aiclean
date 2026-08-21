# AIClean 快速开始

## 1️⃣ 获取 API Key

### 推荐：DeepSeek（便宜好用）
1. 访问 https://platform.deepseek.com
2. 注册账号
3. 创建 API Key
4. 充值（最低 ¥2）

### 备选：DashScope（通义千问）
1. 访问 https://dashscope.aliyun.com
2. 注册阿里云账号
3. 开通 DashScope 服务
4. 获取 API Key

### 备选：OpenAI
1. 访问 https://platform.openai.com
2. 注册账号
3. 创建 API Key
4. 绑定支付方式

---

## 2️⃣ 构建 APK

### 方式一：Android Studio（推荐）
1. 用 Android Studio 打开 `/var/minis/workspace/aiclean/`
2. 等待 Gradle 同步
3. 点击 Run 按钮

### 方式二：命令行
```bash
cd /var/minis/workspace/aiclean/
chmod +x gradlew
./gradlew assembleDebug
```

APK 输出: `app/build/outputs/apk/debug/app-debug.apk`

---

## 3️⃣ 安装到手机

1. 将 APK 传到手机
2. 安装（需要允许未知来源）
3. 打开 AIClean

---

## 4️⃣ 配置 API Key

1. 点击右上角齿轮图标进入设置
2. 选择 AI Provider（如 DeepSeek）
3. 输入你的 API Key
4. 点击 Save

---

## 5️⃣ 开始清理

1. 首页点击 "Scan" 扫描存储
2. 等待扫描完成
3. 点击 "AI Smart Analysis" 获取建议
4. 查看 AI 推荐的清理项
5. 选择要清理的应用
6. 点击 "Clean" 执行清理

---

## 常见问题

### Q: AI 分析不工作？
A: 检查 API Key 是否正确，网络是否连接

### Q: 扫描不到应用？
A: 需要授予存储权限

### Q: 清理后空间没变化？
A: 部分缓存需要重启应用才能释放

### Q: 支持哪些 AI？
A: 所有 OpenAI 兼容的 API 都支持

---

## 功能预览

### 首页
- 📊 存储概览（已用/可用空间）
- 📱 应用缓存统计
- 🗑️ 垃圾文件统计
- 📋 重复文件统计
- 🤖 AI 分析入口

### 应用列表
- 📱 所有已安装应用
- 📏 每个应用的缓存大小
- 🎯 AI 推荐标注
- ✅ 多选清理
- 🧹 一键清理

### 设置
- 🔑 API Key 配置
- 🤖 AI Provider 选择
- ⚙️ 模型参数调节
- 📖 帮助文档

---

## 技术支持

遇到问题？查看：
- [README.md](README.md) - 完整文档
- [PROJECT_SUMMARY.md](PROJECT_SUMMARY.md) - 项目总结

---

**享受 AI 驱动的智能清理体验！** 🚀

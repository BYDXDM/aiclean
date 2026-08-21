# AIClean - AI-Powered Phone Cleaner

AIClean is an intelligent Android phone cleaning app that uses AI to analyze and optimize your device storage. Unlike traditional cleaners that just delete everything, AIClean understands what's important and what's safe to remove.

## Features

### 🧹 Smart Cleaning
- **Cache Cleaner**: Clean app cache with AI-powered recommendations
- **Junk File Scanner**: Find and remove temporary files, logs, and leftovers
- **Duplicate Finder**: Detect and remove duplicate files
- **Storage Analyzer**: Visualize your storage usage

### 🤖 AI-Powered Analysis
- **Smart Recommendations**: AI analyzes your apps and suggests what to clean
- **Cache Value Assessment**: Understand which caches are valuable vs. wasteful
- **Conversational Interface**: Ask questions like "Which apps haven't I used in months?"
- **Personalized Tips**: Get tailored storage optimization advice

### 🔑 Bring Your Own API Key
Works with any OpenAI-compatible API:
- OpenAI (GPT-3.5, GPT-4)
- DashScope (通义千问)
- DeepSeek
- Ollama (Local LLMs)
- Any OpenAI-compatible endpoint

### 🔒 Privacy First
- Your API key stays on your device
- No data collection or tracking
- All scanning happens locally
- AI analysis only sends anonymized app metadata

## Screenshots

[Coming Soon]

## Requirements

- Android 8.0+ (API 26+)
- Storage permission for full scanning
- Internet connection for AI analysis (optional)

## Installation

1. Download the latest APK from Releases
2. Install on your Android device
3. Grant storage permissions when prompted
4. Configure your API key in Settings

## Setup

### 1. Get an API Key

Choose one of these providers:

**OpenAI**
- Sign up at https://platform.openai.com
- Create an API key
- Add credits to your account

**DashScope (通义千问)**
- Sign up at https://dashscope.aliyun.com
- Get your API key
- Free tier available

**DeepSeek**
- Sign up at https://platform.deepseek.com
- Get your API key
- Very affordable pricing

**Ollama (Local)**
- Install Ollama on your computer
- Run `ollama serve`
- Use `http://your-ip:11434/v1` as base URL

### 2. Configure in App

1. Open AIClean
2. Go to Settings
3. Select your AI provider
4. Enter your API key
5. Adjust model settings if needed
6. Save settings

### 3. Start Cleaning

1. Tap "Scan" on the home screen
2. Wait for the scan to complete
3. Tap "AI Smart Analysis" for recommendations
4. Review and select items to clean
5. Tap "Clean" to optimize your storage

## Architecture

```
app/
├── core/
│   ├── ai/           # AI integration (API calls, models)
│   ├── cleaner/      # Cleaning operations
│   ├── scanner/      # Storage scanning engine
│   └── settings/     # User preferences
├── ui/
│   ├── screens/      # Compose UI screens
│   ├── navigation/   # Navigation setup
│   └── theme/        # Material 3 theme
└── di/               # Hilt dependency injection
```

## Tech Stack

- **Kotlin** - Primary language
- **Jetpack Compose** - Modern UI toolkit
- **Hilt** - Dependency injection
- **Coroutines** - Async operations
- **Material 3** - Design system
- **OkHttp** - HTTP client
- **Gson** - JSON parsing
- **DataStore** - Preferences storage

## AI Integration

AIClean uses a flexible AI integration that:

1. **Scans your device** to collect app metadata
2. **Sends anonymized data** to your chosen AI provider
3. **Receives recommendations** on what's safe to clean
4. **Presents insights** in an easy-to-understand format

The AI analyzes:
- App usage patterns
- Cache types and sizes
- Last used timestamps
- System vs. user apps
- File categories

## Contributing

Contributions are welcome! Please feel free to submit issues and pull requests.

### Development Setup

1. Clone the repository
2. Open in Android Studio
3. Sync Gradle
4. Run on emulator or device

### Building

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease
```

## Privacy Policy

AIClean respects your privacy:

- ✅ API keys are stored locally only
- ✅ No analytics or tracking
- ✅ No data collection
- ✅ AI analysis is optional
- ✅ All scanning is local
- ✅ Open source code

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Inspired by [SD Maid SE](https://github.com/d4rken-org/sdmaid-se)
- Built with [Jetpack Compose](https://developer.android.com/jetpack/compose)
- Powered by [Material 3](https://m3.material.io/)

## Support

Having issues? Please open an issue on GitHub.

---

**Made with ❤️ and AI**

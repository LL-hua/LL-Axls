#!/bin/bash
echo "🚀 开始编译..."
gradle assembleRelease
if [ $? -eq 0 ]; then
    echo "✅ 编译成功！"
    echo "📦 包体积: $(du -h app/build/outputs/apk/release/app-release.apk | cut -f1)"
    echo "📍 路径: $(pwd)/app/build/outputs/apk/release/app-release.apk"
else
    echo "❌ 编译失败！"
fi

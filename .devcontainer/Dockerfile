FROM mcr.microsoft.com/devcontainers/java:1-17-bookworm

USER root

RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip wget ca-certificates \
    && rm -rf /var/lib/apt/lists/*

ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV ANDROID_HOME=/opt/android-sdk

RUN mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools" /tmp/android-tools \
    && wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/android-tools/tools.zip \
    && unzip -q /tmp/android-tools/tools.zip -d /tmp/android-tools \
    && mv /tmp/android-tools/cmdline-tools "$ANDROID_SDK_ROOT/cmdline-tools/latest" \
    && rm -rf /tmp/android-tools \
    && yes | "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null \
    && "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" \
        "platform-tools" \
        "platforms;android-34" \
        "build-tools;34.0.0" > /dev/null

ENV PATH="$PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools"

USER vscode

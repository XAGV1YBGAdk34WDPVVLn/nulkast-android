FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH="${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/build-tools/34.0.0"

RUN apt-get update && apt-get install -y --no-install-recommends \
    adb \
    fastboot \
    android-sdk-platform-tools \
    android-sdk-libsparse-utils \
    heimdall-flash \
    erofs-utils \
    e2fsprogs \
    squashfs-tools \
    abootimg \
    lz4 \
    zstd \
    unzip \
    zip \
    jq \
    file \
    git \
    git-lfs \
    curl \
    wget \
    ca-certificates \
    sqlite3 \
    usbutils \
    udev \
    openjdk-17-jdk-headless \
    python3 \
    python3-pip \
    python3-tornado \
    && curl -fsSL https://storage.googleapis.com/git-repo-downloads/repo -o /usr/local/bin/repo \
    && chmod a+rx /usr/local/bin/repo \
    && mkdir -p ${ANDROID_HOME}/cmdline-tools \
    && curl -fsSL https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o /tmp/cmdline-tools.zip \
    && unzip -q /tmp/cmdline-tools.zip -d ${ANDROID_HOME}/cmdline-tools \
    && mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest \
    && rm -f /tmp/cmdline-tools.zip \
    && yes | ${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager --licenses >/dev/null 2>&1 || true \
    && ${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager "platforms;android-34" "build-tools;34.0.0" \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY scripts/install_avbtool.py /tmp/install_avbtool.py
RUN python3 /tmp/install_avbtool.py && rm -f /tmp/install_avbtool.py

COPY cli.py client_sdk.py /app/
COPY server /app/server
COPY scripts /app/scripts

EXPOSE 8245
CMD ["python3", "-m", "server.app", "--port", "8245"]

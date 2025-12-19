FROM eclipse-temurin:23

# Support for both amd64 and arm64 architectures
ARG TARGETARCH

ARG USERNAME=vscode
ARG USER_UID=1000
ARG USER_GID=$USER_UID

# Install system dependencies
RUN apt-get update && apt-get install -y \
    curl \
    gnupg \
    wget \
    unzip \
    zip \
    vim \
    less \
    jq \
    htop \
    sudo \
    locales \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Set locale
RUN sed -i '/en_US.UTF-8/s/^# //g' /etc/locale.gen && locale-gen
ENV LANG=en_US.UTF-8
ENV LANGUAGE=en_US:en
ENV LC_ALL=en_US.UTF-8

# Install sbt
RUN echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | tee /etc/apt/sources.list.d/sbt.list && \
    curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x99E82A75642AC823" | apt-key add && \
    apt-get update && \
    apt-get install -y sbt && \
    rm -rf /var/lib/apt/lists/*

# Install Node.js 22
COPY --from=node:22 /usr/local/bin/node /usr/local/bin/node
COPY --from=node:22 /usr/local/lib/node_modules /usr/local/lib/node_modules
RUN ln -s /usr/local/lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm && \
    ln -s /usr/local/lib/node_modules/npm/bin/npx-cli.js /usr/local/bin/npx

# Install Scala CLI (architecture-aware)
RUN SCALA_CLI_ARCH=$(case ${TARGETARCH} in \
        "arm64") echo "aarch64-pc-linux" ;; \
        *) echo "x86_64-pc-linux" ;; \
    esac) && \
    curl -fL "https://github.com/VirtusLab/scala-cli/releases/latest/download/scala-cli-${SCALA_CLI_ARCH}.gz" | gunzip > /usr/local/bin/scala-cli && \
    chmod +x /usr/local/bin/scala-cli

# Install coursier (architecture-aware)
RUN CS_ARCH=$(case ${TARGETARCH} in \
        "arm64") echo "aarch64-pc-linux" ;; \
        *) echo "x86_64-pc-linux" ;; \
    esac) && \
    curl -fL "https://github.com/coursier/launchers/raw/master/cs-${CS_ARCH}.gz" | gzip -d > /usr/local/bin/cs && \
    chmod +x /usr/local/bin/cs

# Create non-root user (handle case where GID/UID already exists)
RUN if getent group $USER_GID > /dev/null 2>&1; then \
        groupmod -n $USERNAME $(getent group $USER_GID | cut -d: -f1); \
    else \
        groupadd --gid $USER_GID $USERNAME; \
    fi \
    && if getent passwd $USER_UID > /dev/null 2>&1; then \
        usermod -l $USERNAME -d /home/$USERNAME -m $(getent passwd $USER_UID | cut -d: -f1); \
    else \
        useradd --uid $USER_UID --gid $USER_GID -m $USERNAME; \
    fi \
    && echo $USERNAME ALL=\(root\) NOPASSWD:ALL > /etc/sudoers.d/$USERNAME \
    && chmod 0440 /etc/sudoers.d/$USERNAME

# Set up sbt directories and cache for vscode user
USER $USERNAME
RUN mkdir -p /home/$USERNAME/.sbt /home/$USERNAME/.cache/coursier

# Pre-install common Scala tools via coursier
RUN /usr/local/bin/cs install \
    scalafmt \
    scalafix \
    metals \
    bloop

# Add coursier bin to PATH
ENV PATH="/home/$USERNAME/.local/share/coursier/bin:$PATH"

USER root

WORKDIR /workspace

# Default command
CMD ["sleep", "infinity"]

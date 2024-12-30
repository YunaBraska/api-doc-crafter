# Base image for fetching and preparing the binary
FROM debian:stable-slim AS fetcher

# Install required tools
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl ca-certificates && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# Determine the architecture and fetch the latest release
ARG ARCH
RUN ARCH=$(if [ "$(uname -m)" = "aarch64" ]; then echo "arm64"; else echo "amd64"; fi) && \
    echo "Fetching release for ARCH: ${ARCH}" && \
    curl -s "https://api.github.com/repos/YunaBraska/api-doc-crafter/releases/latest" \
    | grep "browser_download_url.*api-doc-crafter-linux-${ARCH}-.*.native" \
    | cut -d '"' -f 4 \
    | xargs curl -L -o /api-doc-crafter.native && \
    chmod +x api-doc-crafter.native

# Minimal runtime image
FROM debian:stable-slim

# Copy the fetched binary from the build stage
COPY --from=fetcher /api-doc-crafter.native /usr/local/bin/api-doc-crafter.native

# Ensure the binary is executable
RUN chmod +x /usr/local/bin/api-doc-crafter.native

# Default entrypoint
ENTRYPOINT ["api-doc-crafter.native"]

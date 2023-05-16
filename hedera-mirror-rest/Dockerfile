FROM node:18.16.0-bullseye-slim
LABEL maintainer="mirrornode@hedera.com"

# Setup
ENV DEBIAN_FRONTEND=noninteractive
ENV NODE_ENV=production
EXPOSE 5551
HEALTHCHECK --interval=10s --retries=3 --start-period=25s --timeout=2s CMD wget -q -O- http://localhost:5551/health/liveness
WORKDIR /home/node/app/

# Install dependencies
COPY package*.json ./
RUN apt-get update && \
    apt-get install -y wget && \
    npm ci --only=production && \
    npm cache clean --force --loglevel=error && \
    chown -R node:node . && \
    rm -rf /var/lib/apt/lists/*
COPY --chown=node:node . ./

# Install OS updates
RUN apt-get update && \
    apt-get upgrade -y --no-install-recommends && \
    apt-get autoremove -y && \
    rm -rf /var/lib/apt/lists/*
USER node

# Run
ENTRYPOINT ["node", "--experimental-specifier-resolution=node", "server.js"]

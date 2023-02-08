FROM node:18.12.1-bullseye-slim
LABEL maintainer="mirrornode@hedera.com"

# Setup
ENV DEBIAN_FRONTEND=noninteractive
ENV NODE_ENV production
EXPOSE 3000
EXPOSE 8080
HEALTHCHECK --interval=10s --retries=3 --start-period=25s --timeout=2s CMD wget -q -O- http://localhost:3000/health/liveness
WORKDIR /home/node/app/
COPY . ./

# Install OS updates, required packages, and dependencies
RUN cd monitor_apis && \
    apt-get update && \
    apt-get upgrade -y && \
    apt-get install -y wget && \
    npm install pm2 -g && \
    npm ci --only=production && \
    npm cache clean --force --loglevel=error && \
    chown -R node:node . && \
    rm -rf /var/lib/apt/lists/*
USER node

# Run
ENTRYPOINT ["pm2-runtime", "pm2.json"]

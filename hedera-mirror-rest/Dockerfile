FROM node:18.12.1-alpine3.16
LABEL maintainer="mirrornode@hedera.com"

# Setup
ENV NODE_ENV production
EXPOSE 5551
HEALTHCHECK --interval=10s --retries=3 --start-period=25s --timeout=2s CMD wget -q -O- http://localhost:5551/health/liveness
WORKDIR /home/node/app/
COPY . ./

RUN apk upgrade --no-cache # install OS updates
# Build. Install packages to build native libraries
RUN apk add --no-cache --virtual builddeps python3 build-base && \
    npm ci --only=production && \
    npm cache clean --force --loglevel=error && \
    apk del builddeps
RUN chown -R node:node .

USER node

# Run
ENTRYPOINT ["node", "--experimental-specifier-resolution=node", "server.js"]

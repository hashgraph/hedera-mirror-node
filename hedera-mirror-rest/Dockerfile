FROM node:18.12.0-alpine3.16
LABEL maintainer="mirrornode@hedera.com"

# Setup
ENV NODE_ENV production
EXPOSE 5551
HEALTHCHECK --interval=10s --retries=3 --start-period=25s --timeout=2s CMD wget -q -O- http://localhost:5551/health/liveness
WORKDIR /home/node/app/
RUN chown -R node:node .
COPY --chown=node:node . ./
RUN apk update && apk upgrade && rm -rf /var/cache/apk/*    # install OS updates
USER node

# Build
RUN npm ci --only=production && npm cache clean --force --loglevel=error

# Run
ENTRYPOINT ["node", "--experimental-specifier-resolution=node", "server.js"]

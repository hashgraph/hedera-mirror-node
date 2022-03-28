FROM node:16.14.2-alpine3.15
LABEL maintainer="mirrornode@hedera.com"

# Setup
ENV NODE_ENV production
EXPOSE 5551
WORKDIR /home/node/app/
RUN chown -R node:node .
COPY --chown=node:node . ./
USER node

# Build
RUN npm ci --only=production && npm cache clean --force --loglevel=error

# Run
CMD ["node", "server.js"]

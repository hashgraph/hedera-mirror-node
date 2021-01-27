FROM node:14.15.4-alpine3.12
LABEL maintainer="mirrornode@hedera.com"

# Setup
WORKDIR /home/node/app/
RUN chown -R node:node .
COPY --chown=node:node . ./
USER node

# Build
ENV NODE_ENV production
RUN npm ci --only=production && npm cache clean --force --loglevel=error

# Run
EXPOSE 5551
CMD ["node", "server.js"]

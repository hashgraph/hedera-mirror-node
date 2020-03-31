FROM node:13.12.0-alpine3.11 as build
WORKDIR /usr/src/app
COPY package*.json ./
RUN npm install --no-package-lock

FROM node:13.12.0-alpine3.11
WORKDIR /usr/src/app
COPY --from=build /usr/src/app .
COPY . ./
USER node
CMD npm start

FROM node:11-alpine as build
WORKDIR /usr/src/app
COPY package*.json ./
RUN npm install

FROM node:11-alpine
WORKDIR /usr/src/app
COPY --from=build /usr/src/app .
COPY . ./
CMD npm start

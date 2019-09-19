FROM node:12.10.0-alpine as build
WORKDIR /usr/src/app
COPY package*.json ./
RUN npm install

FROM node:12.10.0-alpine
WORKDIR /usr/src/app
COPY --from=build /usr/src/app .
COPY . ./
CMD npm start

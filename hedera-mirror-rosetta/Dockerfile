FROM golang:1.18.0-alpine as build
ARG VERSION=development
WORKDIR /app
COPY . .
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -ldflags="-w -s -X main.Version=${VERSION}" -o hedera-mirror-rosetta

FROM scratch
EXPOSE 5700
USER 1000:1000
WORKDIR /app
COPY --from=build /app/hedera-mirror-rosetta .
CMD ["./hedera-mirror-rosetta"]

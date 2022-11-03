FROM golang:1.18.8-alpine3.16 as build
ARG VERSION=development
WORKDIR /app
COPY go.* ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -ldflags="-w -s -X main.Version=${VERSION}" -o hedera-mirror-rosetta

FROM alpine:3.16.2
EXPOSE 5700
HEALTHCHECK --interval=10s --retries=5 --start-period=30s --timeout=2s CMD wget -q -O- http://localhost:5700/health/liveness
WORKDIR /app
RUN apk update && apk upgrade && rm -rf /var/cache/apk/*     # install OS updates
USER 1000:1000
COPY --from=build /app/hedera-mirror-rosetta .

CMD ["./hedera-mirror-rosetta"]

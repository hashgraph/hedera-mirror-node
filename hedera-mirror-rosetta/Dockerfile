FROM golang:1.18.0-alpine as build
ARG VERSION=development
WORKDIR /app
COPY go.* ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -ldflags="-w -s -X main.Version=${VERSION}" -o hedera-mirror-rosetta

FROM alpine:3.15.4
EXPOSE 5700
HEALTHCHECK --interval=10s --retries=5 --start-period=30s --timeout=2s CMD wget -q -O- http://localhost:5700/health/liveness
USER 1000:1000
WORKDIR /app
COPY --from=build /app/hedera-mirror-rosetta .
CMD ["./hedera-mirror-rosetta"]

FROM golang:1.17.1 as build
WORKDIR /app
COPY . .
ARG VERSION=development
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -ldflags="-w -s -X main.Version=${VERSION}" -o hedera-mirror-rosetta

FROM scratch
WORKDIR /app
COPY --from=build /app/hedera-mirror-rosetta .
CMD ["./hedera-mirror-rosetta"]

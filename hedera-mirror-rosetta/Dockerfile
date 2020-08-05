FROM golang:1.13 as build
WORKDIR /tmp/src/hedera-mirror-rosetta
COPY . .
RUN go build -o main .

FROM ubuntu:latest
WORKDIR $GOPATH/src/hedera-mirror-rosetta
COPY --from=build /tmp/src/hedera-mirror-rosetta/main .
COPY . .
CMD ["./main"]
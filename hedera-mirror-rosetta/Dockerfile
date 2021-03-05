FROM golang:1.14 as build
WORKDIR /tmp/src/hedera-mirror-rosetta
COPY . .
RUN go build -o main ./cmd

FROM ubuntu:latest
WORKDIR $GOPATH/src/hedera-mirror-rosetta
COPY --from=build /tmp/src/hedera-mirror-rosetta/main .
COPY ./config/application.yml ./config/application.yml
CMD ["./main"]

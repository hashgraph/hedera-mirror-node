FROM golang:1.16 as build
WORKDIR /tmp/src/hedera-mirror-rosetta
COPY . .
RUN go build -o main ./cmd

FROM ubuntu:20.04
WORKDIR $GOPATH/src/hedera-mirror-rosetta
COPY --from=build /tmp/src/hedera-mirror-rosetta/main .
COPY ./config/application.yml ./config/application.yml
CMD ["./main"]

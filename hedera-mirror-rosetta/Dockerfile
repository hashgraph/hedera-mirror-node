FROM golang:1.13-alpine as build
WORKDIR /tmp/src/hedera-mirror-rosetta
COPY . .
RUN go build -o main .

FROM alpine
WORKDIR $GOPATH/src/hedera-mirror-rosetta
COPY --from=build /tmp/src/hedera-mirror-rosetta/main .
COPY . .
CMD ["./main"]
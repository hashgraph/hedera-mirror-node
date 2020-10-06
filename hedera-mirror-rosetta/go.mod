module github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta

go 1.13

require (
	github.com/caarlos0/env/v6 v6.3.0
	github.com/coinbase/rosetta-cli v0.5.7 // indirect
	github.com/coinbase/rosetta-sdk-go v0.4.9
	github.com/hashgraph/hedera-sdk-go v0.9.1
	github.com/jinzhu/gorm v1.9.16
	github.com/lib/pq v1.8.0 // indirect
	github.com/sqs/goreturns v0.0.0-20181028201513-538ac6014518 // indirect
	golang.org/x/crypto v0.0.0-20201002094018-c90954cbb977 // indirect
	golang.org/x/net v0.0.0-20200930145003-4acb6c075d10 // indirect
	golang.org/x/sys v0.0.0-20200930185726-fdedc70b468f // indirect
	google.golang.org/genproto v0.0.0-20201001141541-efaab9d3c4f7 // indirect
	google.golang.org/grpc v1.32.0 // indirect
	gopkg.in/yaml.v2 v2.3.0
)

replace github.com/hashgraph/hedera-sdk-go v0.9.1 => github.com/limechain/hedera-sdk-go v0.9.2-0.20200825132925-ccbc4019e257

module github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta

go 1.13

require (
	github.com/caarlos0/env/v6 v6.3.0
	github.com/coinbase/rosetta-sdk-go v0.3.4
	github.com/hashgraph/hedera-sdk-go v0.9.1
	github.com/jinzhu/gorm v1.9.15
	github.com/sqs/goreturns v0.0.0-20181028201513-538ac6014518 // indirect
	gopkg.in/yaml.v2 v2.2.2
)

replace github.com/hashgraph/hedera-sdk-go v0.9.1 => github.com/limechain/hedera-sdk-go v0.9.2-0.20200825132925-ccbc4019e257

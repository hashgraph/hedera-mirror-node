module github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta

go 1.13

require (
	github.com/coinbase/rosetta-sdk-go v0.3.4
	github.com/hashgraph/hedera-sdk-go v0.9.1
	github.com/jinzhu/gorm v1.9.15
	github.com/joho/godotenv v1.3.0
	github.com/sqs/goreturns v0.0.0-20181028201513-538ac6014518 // indirect
	golang.org/x/crypto v0.0.0-20200622213623-75b288015ac9
	gopkg.in/yaml.v2 v2.2.2
)

replace github.com/hashgraph/hedera-sdk-go v0.9.1 => github.com/limechain/hedera-sdk-go v0.9.2-0.20200813152811-0a57de9e27e1

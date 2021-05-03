module github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta

go 1.16

require (
	github.com/DATA-DOG/go-sqlmock v1.5.0
	github.com/caarlos0/env/v6 v6.5.0
	github.com/coinbase/rosetta-sdk-go v0.4.9
	github.com/hashgraph/hedera-sdk-go v0.9.4
	github.com/iancoleman/strcase v0.1.3
	github.com/jinzhu/gorm v1.9.16
	github.com/lib/pq v1.8.0 // indirect
	github.com/stretchr/testify v1.7.0
	gopkg.in/yaml.v2 v2.4.0
)

replace github.com/hashgraph/hedera-sdk-go v0.9.1 => github.com/limechain/hedera-sdk-go v0.9.2-0.20200825132925-ccbc4019e257

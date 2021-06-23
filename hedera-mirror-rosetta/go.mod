module github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta

go 1.16

require (
	github.com/DATA-DOG/go-sqlmock v1.5.0
	github.com/caarlos0/env/v6 v6.6.2
	github.com/coinbase/rosetta-sdk-go v0.6.10
	github.com/go-playground/validator/v10 v10.6.1
	github.com/hashgraph/hedera-sdk-go/v2 v2.1.7
	github.com/iancoleman/strcase v0.1.3
	github.com/lib/pq v1.10.2
	github.com/mgutz/ansi v0.0.0-20200706080929-d51e80ef957d // indirect
	github.com/ory/dockertest/v3 v3.7.0
	github.com/pkg/errors v0.9.1
	github.com/sirupsen/logrus v1.8.1
	github.com/stretchr/testify v1.7.0
	github.com/thanhpk/randstr v1.0.4
	github.com/x-cray/logrus-prefixed-formatter v0.5.2
	google.golang.org/protobuf v1.26.0
	gopkg.in/yaml.v2 v2.4.0
	gorm.io/driver/postgres v1.1.0
	gorm.io/gorm v1.21.11
)

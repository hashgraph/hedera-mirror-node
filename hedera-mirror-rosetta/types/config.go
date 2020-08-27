package types

type Config struct {
	Hedera Hedera `yaml:"hedera"`
}

type Hedera struct {
	Mirror Mirror `yaml:"mirror"`
}

type Mirror struct {
	Rosetta Rosetta `yaml:"rosetta"`
}

type Rosetta struct {
	ApiVersion  string `yaml:"apiVersion" env:"HEDERA_MIRROR_ROSETTA_API_VERSION"`
	Db          Db     `yaml:"db"`
	Network     string `yaml:"network" env:"HEDERA_MIRROR_ROSETTA_NETWORK"`
	NodeVersion string `yaml:"nodeVersion" env:"HEDERA_MIRROR_ROSETTA_NODE_VERSION"`
	Online      bool   `yaml:"online" env:"HEDERA_MIRROR_ROSETTA_ONLINE"`
	Port        string `yaml:"port" env:"HEDERA_MIRROR_ROSETTA_PORT"`
	Realm       string `yaml:"realm" env:"HEDERA_MIRROR_ROSETTA_REALM"`
	Shard       string `yaml:"shard" env:"HEDERA_MIRROR_ROSETTA_SHARD"`
	Version     string `yaml:"version" env:"HEDERA_MIRROR_ROSETTA_VERSION"`
}

type Db struct {
	Host     string `yaml:"host" env:"HEDERA_MIRROR_ROSETTA_DB_HOST"`
	Name     string `yaml:"name" env:"HEDERA_MIRROR_ROSETTA_DB_NAME"`
	Password string `yaml:"password" env:"HEDERA_MIRROR_ROSETTA_DB_PASSWORD"`
	Port     string `yaml:"port" env:"HEDERA_MIRROR_ROSETTA_DB_PORT"`
	Username string `yaml:"username" env:"HEDERA_MIRROR_ROSETTA_DB_USERNAME"`
}

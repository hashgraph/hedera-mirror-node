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
	ApiVersion  string `yaml:"apiVersion"`
	Db          Db     `yaml:"db"`
	Network     string `yaml:"network"`
	NodeVersion string `yaml:"nodeVersion"`
	Port        string `yaml:"port"`
	Realm       string `yaml:"realm"`
	Shard       string `yaml:"shard"`
	Version     string `yaml:"version"`
}

type Db struct {
	Host     string `yaml:"host"`
	Name     string `yaml:"name"`
	Password string `yaml:"password"`
	Port     string `yaml:"port"`
	Username string `yaml:"username"`
}

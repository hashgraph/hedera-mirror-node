package main

import (
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/config"
	"io/ioutil"
	"log"
	"os"
	"path/filepath"

	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/types"
	"gopkg.in/yaml.v2"
)

const (
	defaultConfigFile = "config/application.yml"
	mainConfigFile    = "application.yml"
)

func LoadConfig() *types.Config {
	var configuration types.Config
	GetConfig(&configuration, defaultConfigFile)
	GetConfig(&configuration, mainConfigFile)
	configuration.Hedera.Mirror.Rosetta.Db.Host = os.Getenv(config.EnvHederaMirrorRosettaDBHost)

	return &configuration
}

func GetConfig(config *types.Config, path string) {
	if _, err := os.Stat(path); os.IsNotExist(err) {
		return
	}

	filename, _ := filepath.Abs(path)
	yamlFile, err := ioutil.ReadFile(filename)
	if err != nil {
		log.Fatal(err)
	}

	err = yaml.Unmarshal(yamlFile, config)
	if err != nil {
		log.Fatal(err)
	}
}

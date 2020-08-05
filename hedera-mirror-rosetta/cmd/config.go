package main

import (
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
	var config types.Config
	GetConfig(&config, defaultConfigFile)
	GetConfig(&config, mainConfigFile)
	config.Hedera.Mirror.Rosetta.Db.Host = os.Getenv("HEDERA_MIRROR_ROSETTA_DB_HOST")

	return &config
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

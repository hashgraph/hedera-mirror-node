/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

package main

import (
	"github.com/caarlos0/env/v6"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/types"
	"gopkg.in/yaml.v2"
	"io/ioutil"
	"log"
	"os"
	"path/filepath"
)

const (
	defaultConfigFile = "config/application.yml"
	mainConfigFile    = "application.yml"
)

func LoadConfig() *types.Config {
	var configuration types.Config
	GetConfig(&configuration, defaultConfigFile)
	GetConfig(&configuration, mainConfigFile)

	extConfigFile := os.Getenv("HEDERA_MIRROR_ROSETTA_API_CONFIG")
	if "" != extConfigFile {
		GetConfig(&configuration, extConfigFile)
		log.Printf("Loaded external config file: %s\n", extConfigFile)
	}

	if err := env.Parse(&configuration); err != nil {
		panic(err)
	}

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

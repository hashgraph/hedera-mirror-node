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
	"io/ioutil"
	"os"
	"path/filepath"
	"reflect"
	"strings"

	"github.com/caarlos0/env/v6"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/types"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/pkg/errors"
	log "github.com/sirupsen/logrus"
	"gopkg.in/yaml.v2"
)

const (
	mainConfigFile = "application.yml"
)

// loadConfig loads configuration from yaml files and env variables
func loadConfig() (*types.Config, error) {
	var config = types.Config{
		Hedera: types.Hedera{
			Mirror: types.Mirror{
				Rosetta: types.Rosetta{
					ApiVersion: "1.4.10",
					Db: types.Db{
						Host:     "127.0.0.1",
						Name:     "mirror_node",
						Password: "mirror_rosetta_pass",
						Pool: types.Pool{
							MaxIdleConnections: 20,
							MaxLifetime:        30,
							MaxOpenConnections: 100,
						},
						Port:     5432,
						Username: "mirror_rosetta",
					},
					Log: types.Log{
						Level: "info",
					},
					Network:     "DEMO",
					Nodes:       types.NodeMap{},
					NodeVersion: "0",
					Online:      true,
					Port:        5700,
					Realm:       "0",
					Shard:       "0",
					Version:     "0.40.0-rc3",
				},
			},
		},
	}

	getConfig(&config, mainConfigFile)

	if envConfigFile, ok := os.LookupEnv("HEDERA_MIRROR_ROSETTA_API_CONFIG"); ok {
		getConfig(&config, envConfigFile)
	}

	if err := env.ParseWithFuncs(&config, map[reflect.Type]env.ParserFunc{
		reflect.TypeOf(types.NodeMap{}): parseNodesFromEnv,
	}); err != nil {
		return nil, err
	}

	var password = config.Hedera.Mirror.Rosetta.Db.Password
	config.Hedera.Mirror.Rosetta.Db.Password = "<omitted>"
	log.Infof("Using configuration: %+v", &config)
	config.Hedera.Mirror.Rosetta.Db.Password = password

	return &config, nil
}

func getConfig(config *types.Config, path string) bool {
	if _, err := os.Stat(path); err != nil {
		log.Errorf("Failed to locate the config file %s: %s", path, err)
		return false
	}

	filename, _ := filepath.Abs(path)

	// Disable gosec since we want to support loading config via env variable like SPRING_CONFIG_ADDITIONAL_LOCATION
	yamlFile, err := ioutil.ReadFile(filename) // #nosec
	if err != nil {
		log.Errorf("Failed to read the config file %s: %s", filename, err)
		return false
	}

	err = yaml.Unmarshal(yamlFile, config)
	if err != nil {
		log.Errorf("Failed to unmarshal the yaml config file %s: %s", filename, err)
		return false
	}

	log.Infof("Loaded external config file: %s", path)
	return true
}

func parseNodesFromEnv(v string) (interface{}, error) {
	nodeMap := make(types.NodeMap, 0)

	if len(v) == 0 {
		return nodeMap, nil
	}

	for _, kv := range strings.Split(v, ",") {
		parts := strings.Split(kv, "=")
		if len(parts) != 2 {
			return nil, errors.New("invalid value " + kv)
		}

		accountId, err := hedera.AccountIDFromString(parts[1])
		if err != nil {
			return nil, err
		}
		nodeMap[parts[0]] = accountId
	}

	return nodeMap, nil
}

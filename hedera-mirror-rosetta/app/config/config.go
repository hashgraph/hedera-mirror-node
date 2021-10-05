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

package config

import (
	"bytes"
	"os"
	"reflect"
	"strings"

	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/pkg/errors"
	log "github.com/sirupsen/logrus"
	"github.com/spf13/viper"
)

const (
	defaultConfig = `
hedera:
  mirror:
    rosetta:
      db:
        host: 127.0.0.1
        name: mirror_node
        password: mirror_rosetta_pass
        pool:
          maxIdleConnections: 20
          maxLifetime: 30
          maxOpenConnections: 100
        port: 5432
        statementTimeout: 20
        username: mirror_rosetta
      log:
        level: info
      network: DEMO
      nodes:
      nodeVersion: 0
      online: true
      port: 5700
      realm: 0
      shard: 0
`

	apiConfigEnvKey = "HEDERA_MIRROR_ROSETTA_API_CONFIG"
	configName      = "application"
	configTypeYaml  = "yml"
	envKeyDelimiter = "_"
	keyDelimiter    = "::"
)

// LoadConfig loads configuration from yaml files and env variables
func LoadConfig() (*Rosetta, error) {
	// NodeMap's key has '.', set viper key delimiter to avoid parsing it as a nested key
	v := viper.NewWithOptions(viper.KeyDelimiter(keyDelimiter))
	v.SetConfigType(configTypeYaml)

	// read the default
	if err := v.ReadConfig(bytes.NewBuffer([]byte(defaultConfig))); err != nil {
		return nil, err
	}

	// load configuration file from current directory
	v.SetConfigName(configName)
	v.AddConfigPath(".")
	if err := mergeExternalConfigFile(v); err != nil {
		return nil, err
	}

	if envConfigFile, ok := os.LookupEnv(apiConfigEnvKey); ok {
		v.SetConfigFile(envConfigFile)
		if err := mergeExternalConfigFile(v); err != nil {
			return nil, err
		}
	}

	// enable parsing env variables after the configuration files are loaded so viper knows all configuration keys
	// and can override the config accordingly
	v.AutomaticEnv()
	v.SetEnvKeyReplacer(strings.NewReplacer(keyDelimiter, envKeyDelimiter))

	var config Config
	if err := v.Unmarshal(&config, viper.DecodeHook(nodeMapDecodeHookFunc)); err != nil {
		return nil, err
	}

	var password = config.Hedera.Mirror.Rosetta.Db.Password
	config.Hedera.Mirror.Rosetta.Db.Password = "<omitted>"
	log.Infof("Using configuration: %+v", config.Hedera.Mirror.Rosetta)
	config.Hedera.Mirror.Rosetta.Db.Password = password

	return &config.Hedera.Mirror.Rosetta, nil
}

func mergeExternalConfigFile(v *viper.Viper) error {
	if err := v.MergeInConfig(); err != nil {
		if _, ok := err.(viper.ConfigFileNotFoundError); !ok {
			return err
		}

		log.Info("External configuration file not found")
		return nil
	}

	log.Infof("Loaded external config file: %s", v.ConfigFileUsed())
	return nil
}

func nodeMapDecodeHookFunc(from, to reflect.Type, data interface{}) (interface{}, error) {
	if to != reflect.TypeOf(NodeMap{}) {
		return data, nil
	}

	input, ok := data.(map[string]interface{})
	if !ok {
		return nil, errors.Errorf("Invalid data type for NodeMap")
	}

	nodeMap := make(NodeMap)
	for key, nodeAccountId := range input {
		nodeAccountIdStr, ok := nodeAccountId.(string)
		if !ok {
			return nil, errors.Errorf("Invalid data type for node account ID")
		}

		accountId, err := hedera.AccountIDFromString(nodeAccountIdStr)
		if err != nil {
			return nil, err
		}

		nodeMap[key] = accountId
	}

	return nodeMap, nil
}

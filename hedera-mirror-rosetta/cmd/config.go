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
	"bytes"
	"os"
	"reflect"
	"strings"

	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/types"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/pkg/errors"
	log "github.com/sirupsen/logrus"
	"github.com/spf13/viper"
)

const (
	configName      = "application"
	configTypeYaml  = "yml"
	envKeyDelimiter = "_"
	keyDelimiter    = "::"
)

var configPaths = []string{"/usr/etc/hedera-mirror-rosetta", "."}

// loadConfig loads configuration from yaml files and env variables
func loadConfig() (*types.Config, error) {
	// NodeMap's key has '.', set viper key delimiter to avoid parsing it as a nested key
	v := viper.NewWithOptions(viper.KeyDelimiter(keyDelimiter))
	v.SetConfigType(configTypeYaml)

	if envConfigFile, ok := os.LookupEnv("HEDERA_MIRROR_ROSETTA_API_CONFIG"); ok {
		v.SetConfigFile(envConfigFile)
	} else {
		// only set config name and config paths when no config file env variable is set
		v.SetConfigName(configName)
		for _, configPath := range configPaths {
			v.AddConfigPath(configPath)
		}
	}

	// read the default
	if err := v.ReadConfig(bytes.NewBuffer(defaultConfig)); err != nil {
		return nil, err
	}

	if err := v.MergeInConfig(); err != nil {
		if _, ok := err.(viper.ConfigFileNotFoundError); !ok {
			return nil, err
		}

		log.Info("External configuration file not found, load the default")
	}

	if v.ConfigFileUsed() != "" {
		log.Infof("Loaded external config file: %s", v.ConfigFileUsed())
	}

	// enable parsing env variables after the configuration files are loaded so viper knows all configuration keys
	// and can override the config accordingly
	v.AutomaticEnv()
	v.SetEnvKeyReplacer(strings.NewReplacer(keyDelimiter, envKeyDelimiter))

	var config types.Config
	if err := v.Unmarshal(&config, viper.DecodeHook(nodeMapDecodeHookFunc)); err != nil {
		return nil, err
	}

	var password = config.Hedera.Mirror.Rosetta.Db.Password
	config.Hedera.Mirror.Rosetta.Db.Password = "<omitted>"
	log.Infof("Using configuration: %+v", config.Hedera.Mirror.Rosetta)
	config.Hedera.Mirror.Rosetta.Db.Password = password

	return &config, nil
}

func nodeMapDecodeHookFunc(from, to reflect.Type, data interface{}) (interface{}, error) {
	if to != reflect.TypeOf(types.NodeMap{}) {
		return data, nil
	}

	input, ok := data.(map[string]interface{})
	if !ok {
		return nil, errors.Errorf("Invalid data type for NodeMap")
	}

	nodeMap := make(types.NodeMap)
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

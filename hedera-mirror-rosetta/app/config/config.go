/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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
	_ "embed"
	"fmt"
	"os"
	"reflect"
	"strings"

	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/mitchellh/mapstructure"
	"github.com/pkg/errors"
	log "github.com/sirupsen/logrus"
	"github.com/spf13/viper"
)

//go:embed application.yml
var defaultConfig string

const (
	apiConfigEnvKey = "HEDERA_MIRROR_ROSETTA_API_CONFIG"
	configName      = "application"
	configTypeYaml  = "yml"
	envKeyDelimiter = "_"
	keyDelimiter    = "::"
	nodesEnvKey     = "HEDERA_MIRROR_ROSETTA_NODES"
)

type fullConfig struct {
	Hedera struct {
		Mirror struct {
			Rosetta Config
		}
	}
}

// LoadConfig loads configuration from yaml files and env variables
func LoadConfig() (*Config, error) {
	nodeMap, err := loadNodeMapFromEnv()
	if err != nil {
		return nil, err
	}

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

	var config fullConfig
	compositeDecodeHookFunc := mapstructure.ComposeDecodeHookFunc(
		mapstructure.StringToTimeDurationHookFunc(),
		nodeMapDecodeHookFunc,
	)
	if err := v.Unmarshal(&config, viper.DecodeHook(compositeDecodeHookFunc)); err != nil {
		return nil, err
	}

	rosettaConfig := &config.Hedera.Mirror.Rosetta
	rosettaConfig.Network = strings.ToLower(rosettaConfig.Network)
	if len(nodeMap) != 0 {
		rosettaConfig.Nodes = nodeMap
	}

	var password = rosettaConfig.Db.Password
	rosettaConfig.Db.Password = "" // Don't print password
	log.Infof("Using configuration: %+v", rosettaConfig)
	rosettaConfig.Db.Password = password

	return rosettaConfig, nil
}

func loadNodeMapFromEnv() (NodeMap, error) {
	nodeValue := os.Getenv(nodesEnvKey)
	os.Unsetenv(nodesEnvKey)
	if nodeValue == "" {
		return nil, nil
	}

	nodeInfoMap := make(map[string]interface{})
	for _, node := range strings.Split(nodeValue, ",") {
		parts := strings.Split(strings.TrimSpace(node), ":")
		if len(parts) != 3 {
			return nil, errors.Errorf("Invalid node string %s", node)
		}

		nodeInfoMap[fmt.Sprintf("%s:%s", parts[0], parts[1])] = parts[2]
	}

	nodeMap, err := nodeMapDecodeHookFunc(reflect.TypeOf(nodeInfoMap), reflect.TypeOf(NodeMap{}), nodeInfoMap)
	return nodeMap.(NodeMap), err
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

	var zeroNodeMap NodeMap
	nodeMap := make(NodeMap)
	for key, nodeAccountId := range input {
		nodeAccountIdStr, ok := nodeAccountId.(string)
		if !ok {
			return zeroNodeMap, errors.Errorf("Invalid data type for node account ID")
		}

		accountId, err := hedera.AccountIDFromString(nodeAccountIdStr)
		if err != nil {
			return zeroNodeMap, err
		}

		nodeMap[key] = accountId
	}

	return nodeMap, nil
}

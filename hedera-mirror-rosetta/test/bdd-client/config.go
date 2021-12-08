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
	"reflect"

	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/bdd-client/client"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/mitchellh/mapstructure"
	"github.com/pkg/errors"
	log "github.com/sirupsen/logrus"
	"github.com/spf13/viper"
)

const (
	configName     = "application"
	configPrefix   = "hedera.mirror.rosetta.test"
	configTypeYaml = "yml"
	defaultConfig  = `
hedera:
  mirror:
    rosetta:
      test:
        log:
          level: debug
        operators:
          - id:
            privateKey:
        server:
           dataRetry:
             backOff: 1s
             max: 20
           offlineUrl: http://localhost:5701
           onlineUrl: http://localhost:5700
           httpTimeout: 25s
           submitRetry:
             backOff: 200ms
             max: 5
`
)

type config struct {
	Log       logConfig
	Operators []client.Operator
	Server    client.Server
}

type logConfig struct {
	Level string
}

func loadConfig() (*config, error) {
	viper.SetConfigType(configTypeYaml)

	// read the default
	if err := viper.ReadConfig(bytes.NewBuffer([]byte(defaultConfig))); err != nil {
		return nil, err
	}

	// load configuration file from the current director
	viper.SetConfigName(configName)
	viper.AddConfigPath(".")
	if err := viper.MergeInConfig(); err != nil {
		log.Infof("Failed to load external configuration file not found: %v", err)
		return nil, err
	}
	log.Infof("Loaded external configuration file %s", viper.ConfigFileUsed())

	v := viper.Sub(configPrefix)

	config := &config{}
	if err := v.Unmarshal(config, addDecodeHooks); err != nil {
		log.Errorf("Failed to unmarshal config %v", err)
		return nil, err
	}

	return config, nil
}

func addDecodeHooks(c *mapstructure.DecoderConfig) {
	hooks := []mapstructure.DecodeHookFunc{accountIdDecodeHook, privateKeyDecodeHook}
	if c.DecodeHook != nil {
		hooks = append([]mapstructure.DecodeHookFunc{c.DecodeHook}, hooks...)
	}
	c.DecodeHook = mapstructure.ComposeDecodeHookFunc(hooks...)
}

func accountIdDecodeHook(from, to reflect.Type, data interface{}) (interface{}, error) {
	if to != reflect.TypeOf(hedera.AccountID{}) {
		return data, nil
	}

	if accountIdStr, ok := data.(string); ok {
		return hedera.AccountIDFromString(accountIdStr)
	} else {
		return nil, errors.Errorf("Invalid data type for hedera.AccountID")
	}
}

func privateKeyDecodeHook(from, to reflect.Type, data interface{}) (interface{}, error) {
	if to != reflect.TypeOf(hedera.PrivateKey{}) {
		return data, nil
	}

	if keyStr, ok := data.(string); ok {
		return hedera.PrivateKeyFromString(keyStr)
	} else {
		return nil, errors.Errorf("Invalid data type for hedera.PrivateKey")
	}
}

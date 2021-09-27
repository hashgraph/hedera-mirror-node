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
	"testing"

	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/types"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/stretchr/testify/assert"
)

const (
	invalidYaml                   = "this is invalid"
	invalidYamlIncorrectAccountId = `
hedera:
  mirror:
    rosetta:
      nodes:
        "192.168.0.1:50211": 0.3`
	testConfigFilename = "application.yml"
	yml                = `
hedera:
  mirror:
    rosetta:
      db:
        port: 5431
        username: foobar`
	serviceEndpoint = "192.168.0.1:50211"
)

func TestLoadDefaultConfig(t *testing.T) {
	config, err := loadConfig()

	assert.NoError(t, err)
	assert.NotNil(t, config)
	assert.Equal(t, uint16(5432), config.Hedera.Mirror.Rosetta.Db.Port)
	assert.Equal(t, config.Hedera.Mirror.Rosetta.Db.Username, "mirror_rosetta")
}

func TestLoadCustomConfig(t *testing.T) {
	tests := []struct {
		name   string
		useEnv bool
	}{
		{
			"from search path",
			false,
		},
		{
			"from env variable",
			true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			tempDir, filePath := createYamlConfigFile(yml, !tt.useEnv, t)
			defer os.RemoveAll(tempDir)

			if tt.useEnv {
				os.Setenv("HEDERA_MIRROR_ROSETTA_API_CONFIG", filePath)
			}

			config, err := loadConfig()

			assert.NoError(t, err)
			assert.NotNil(t, config)
			assert.True(t, config.Hedera.Mirror.Rosetta.Online)
			assert.Equal(t, uint16(5431), config.Hedera.Mirror.Rosetta.Db.Port)
			assert.Equal(t, "foobar", config.Hedera.Mirror.Rosetta.Db.Username)

			// reset env
			if tt.useEnv {
				os.Unsetenv("HEDERA_MIRROR_ROSETTA_API_CONFIG")
			}
		})
	}
}

func TestLoadCustomConfigInvalidYaml(t *testing.T) {
	tests := []struct {
		name    string
		content string
	}{
		{"invalid yaml", invalidYaml},
		{"incorrect account id", invalidYamlIncorrectAccountId},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			tempDir, _ := createYamlConfigFile(tt.content, true, t)
			defer os.RemoveAll(tempDir)

			config, err := loadConfig()

			assert.Error(t, err)
			assert.Nil(t, config)
		})
	}
}

func TestNodeMapDecodeHookFunc(t *testing.T) {
	nodeMapTye := reflect.TypeOf(types.NodeMap{})
	tests := []struct {
		name        string
		from        reflect.Type
		data        interface{}
		expected    types.NodeMap
		expectError bool
	}{
		{
			name:     "valid data",
			from:     reflect.TypeOf(map[string]interface{}{}),
			data:     map[string]interface{}{serviceEndpoint: "0.0.3"},
			expected: types.NodeMap{serviceEndpoint: hedera.AccountID{Account: 3}},
		},
		{
			name:        "invalid data type",
			from:        reflect.TypeOf(map[int]string{}),
			data:        map[int]interface{}{1: "0.0.3"},
			expectError: true,
		},
		{
			name:        "invalid node account id",
			from:        reflect.TypeOf(map[string]interface{}{}),
			data:        map[string]interface{}{serviceEndpoint: "3"},
			expectError: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			actual, err := nodeMapDecodeHookFunc(tt.from, nodeMapTye, tt.data)

			if tt.expectError {
				assert.Error(t, err)
				assert.Nil(t, actual)
			} else {
				assert.NoError(t, err)
				assert.Equal(t, tt.expected, actual)
			}
		})
	}
}

func createYamlConfigFile(content string, chdir bool, t *testing.T) (string, string) {
	tempDir, err := ioutil.TempDir("", "rosetta")
	if err != nil {
		assert.Fail(t, "Unable to create temp dir", err)
	}
	if chdir && os.Chdir(tempDir) != nil {
		assert.Fail(t, "Unable to change directory")
	}

	customConfig := filepath.Join(tempDir, testConfigFilename)

	if err = ioutil.WriteFile(customConfig, []byte(content), 0644); err != nil {
		assert.Fail(t, "Unable to create custom config", err)
	}

	return tempDir, customConfig
}

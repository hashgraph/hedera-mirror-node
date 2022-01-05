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
	"io/ioutil"
	"os"
	"path/filepath"
	"reflect"
	"testing"

	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/stretchr/testify/assert"
	"gopkg.in/yaml.v2"
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
	yml1               = `
hedera:
  mirror:
    rosetta:
      db:
        port: 5431
        username: foobar`
	yml2 = `
hedera:
  mirror:
    rosetta:
      db:
        host: 192.168.120.51
        port: 12000`
	serviceEndpoint = "192.168.0.1:50211"
)

func TestLoadDefaultConfig(t *testing.T) {
	config, err := LoadConfig()

	assert.NoError(t, err)
	assert.Equal(t, getDefaultConfig(), config)
}

func TestLoadCustomConfig(t *testing.T) {
	tests := []struct {
		name         string
		fromCwdOrEnv bool
	}{
		{name: "from current directory", fromCwdOrEnv: true},
		{name: "from env var"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			tempDir, filePath := createYamlConfigFile(yml1, t)
			defer os.RemoveAll(tempDir)

			if tt.fromCwdOrEnv {
				os.Chdir(tempDir)
			} else {
				os.Setenv(apiConfigEnvKey, filePath)
			}

			config, err := LoadConfig()

			assert.NoError(t, err)
			assert.NotNil(t, config)
			assert.True(t, config.Online)
			assert.Equal(t, uint16(5431), config.Db.Port)
			assert.Equal(t, "foobar", config.Db.Username)

			// reset env
			os.Unsetenv(apiConfigEnvKey)
		})
	}
}

func TestLoadCustomConfigFromCwdAndEnvVar(t *testing.T) {
	// given
	tempDir1, _ := createYamlConfigFile(yml1, t)
	defer os.RemoveAll(tempDir1)
	os.Chdir(tempDir1)

	tempDir2, filePath2 := createYamlConfigFile(yml2, t)
	defer os.RemoveAll(tempDir2)
	os.Setenv(apiConfigEnvKey, filePath2)

	// when
	config, err := LoadConfig()

	// then
	expected := getDefaultConfig()
	expected.Db.Host = "192.168.120.51"
	expected.Db.Port = 12000
	expected.Db.Username = "foobar"
	assert.NoError(t, err)
	assert.Equal(t, expected, config)
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
			tempDir, filePath := createYamlConfigFile(tt.content, t)
			defer os.RemoveAll(tempDir)
			os.Setenv(apiConfigEnvKey, filePath)

			config, err := LoadConfig()

			assert.Error(t, err)
			assert.Nil(t, config)

			// reset env
			os.Unsetenv(apiConfigEnvKey)
		})
	}
}

func TestLoadCustomConfigByEnvVarFileNotFound(t *testing.T) {
	// given
	os.Setenv(apiConfigEnvKey, "/foo/bar/not_found.yml")

	// when
	config, err := LoadConfig()

	// then
	assert.Error(t, err)
	assert.Nil(t, config)
}

func TestNodeMapDecodeHookFunc(t *testing.T) {
	nodeMapTye := reflect.TypeOf(NodeMap{})
	tests := []struct {
		name        string
		from        reflect.Type
		data        interface{}
		expected    NodeMap
		expectError bool
	}{
		{
			name:     "valid data",
			from:     reflect.TypeOf(map[string]interface{}{}),
			data:     map[string]interface{}{serviceEndpoint: "0.0.3"},
			expected: NodeMap{serviceEndpoint: hedera.AccountID{Account: 3}},
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

func createYamlConfigFile(content string, t *testing.T) (string, string) {
	tempDir, err := ioutil.TempDir("", "rosetta")
	if err != nil {
		assert.Fail(t, "Unable to create temp dir", err)
	}

	customConfig := filepath.Join(tempDir, testConfigFilename)

	if err = ioutil.WriteFile(customConfig, []byte(content), 0644); err != nil {
		assert.Fail(t, "Unable to create custom config", err)
	}

	return tempDir, customConfig
}

type FullConfig struct {
	Hedera struct {
		Mirror struct {
			Rosetta Config
		}
	}
}

func getDefaultConfig() *Config {
	config := FullConfig{}
	yaml.Unmarshal([]byte(defaultConfig), &config)
	return &config.Hedera.Mirror.Rosetta
}

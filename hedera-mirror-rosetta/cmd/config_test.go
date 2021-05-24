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
	"os"
	"testing"

	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/types"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/stretchr/testify/assert"
)

func TestLoadDefaultConfig(t *testing.T) {
	wd, _ := os.Getwd()
	// change to project root so to load the default config
	os.Chdir("../")
	defer os.Chdir(wd)

	config, err := loadConfig()

	assert.NoError(t, err)
	assert.NotNil(t, config)
}

func TestParseNodesFromEnv(t *testing.T) {
	var tests = []struct {
		name     string
		input    string
		expected types.NodeMap
		wantErr  bool
	}{
		{
			name:  "ValidInput",
			input: "10.0.0.1:50211=0.0.3,10.0.0.2:50211=0.0.4,10.0.0.3:50211=0.0.5",
			expected: types.NodeMap{
				"10.0.0.1:50211": hedera.AccountID{Account: 3},
				"10.0.0.2:50211": hedera.AccountID{Account: 4},
				"10.0.0.3:50211": hedera.AccountID{Account: 5},
			},
			wantErr: false,
		},
		{
			name:     "EmptyInput",
			input:    "",
			expected: types.NodeMap{},
			wantErr:  false,
		},
		{
			name:     "ExtraEqualSign",
			input:    "10.0.0.1:50211=0.=0.3,10.0.0.2:50211=0.0.4,10.0.0.3:50211=0.0.5",
			expected: nil,
			wantErr:  true,
		},
		{
			name:     "InvalidAccountId",
			input:    "10.0.0.1:50211=3,10.0.0.2:50211=0.0.4,10.0.0.3:50211=0.0.5",
			expected: nil,
			wantErr:  true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			actual, err := parseNodesFromEnv(tt.input)
			if tt.wantErr {
				assert.Error(t, err)
				assert.Nil(t, actual)
			} else {
				assert.NoError(t, err)
				assert.EqualValues(t, tt.expected, actual)
			}
		})
	}
}

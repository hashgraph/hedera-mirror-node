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

package types

import (
	"testing"

	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/pkg/errors"
	"github.com/stretchr/testify/assert"
)

const (
	nodeEndpoint1 string = "10.0.0.1:50211"
	nodeEndpoint2 string = "10.0.0.2:50211"
	nodeEndpoint3 string = "10.0.0.3:50211"
)

var (
	nodeAccountId1 = hedera.AccountID{Account: 3}
	nodeAccountId2 = hedera.AccountID{Account: 4}
	nodeAccountId3 = hedera.AccountID{Account: 5}
)

func TestNodeMapUnmarshalYAML(t *testing.T) {
	var tests = []struct {
		name          string
		mockUnmarshal func(interface{}) error
		expected      NodeMap
		wantErr       bool
	}{
		{
			name: "ValidUnmarshal",
			mockUnmarshal: func(v interface{}) error {
				result, ok := v.(*map[string]string)
				if !ok {
					return errors.New("invalid input type")
				}

				(*result)[nodeEndpoint1] = nodeAccountId1.String()
				(*result)[nodeEndpoint2] = nodeAccountId2.String()
				(*result)[nodeEndpoint3] = nodeAccountId3.String()

				return nil
			},
			expected: NodeMap{
				nodeEndpoint1: nodeAccountId1,
				nodeEndpoint2: nodeAccountId2,
				nodeEndpoint3: nodeAccountId3,
			},
			wantErr: false,
		},
		{
			name: "UnmarshalError",
			mockUnmarshal: func(v interface{}) error {
				return errors.New("unknown error")
			},
			expected: nil,
			wantErr:  true,
		},
		{
			name: "InvalidAccountIDString",
			mockUnmarshal: func(v interface{}) error {
				result, ok := v.(*map[string]string)
				if !ok {
					return errors.New("invalid input type")
				}

				(*result)[nodeEndpoint1] = "0.a.3"
				(*result)[nodeEndpoint2] = "x"
				(*result)[nodeEndpoint3] = "10"

				return nil
			},
			expected: nil,
			wantErr:  true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			nodeMap := make(NodeMap)
			err := nodeMap.UnmarshalYAML(tt.mockUnmarshal)

			if tt.wantErr {
				assert.Error(t, err)
			} else {
				assert.NoError(t, err)
				assert.EqualValues(t, tt.expected, nodeMap)
			}
		})
	}
}

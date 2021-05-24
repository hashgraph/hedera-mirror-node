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

package construction

import (
	"encoding/json"
	"fmt"
	"testing"

	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/stretchr/testify/assert"
)

type K struct {
	Key publicKey `json:"key"`
}

func TestPublicKeyUnmarshalJSONSuccess(t *testing.T) {
	// given
	sk, err := hedera.GeneratePrivateKey()
	assert.NoError(t, err)

	expected := sk.PublicKey()
	input := fmt.Sprintf("{\"key\": \"%s\"}", expected.String())

	// when
	actual := &K{}
	err = json.Unmarshal([]byte(input), actual)

	// then
	assert.NoError(t, err)
	assert.Equal(t, expected, actual.Key.PublicKey)
}

func TestPublicKeyUnmarshalJSONInvalidInput(t *testing.T) {
	// given
	input := "foobar"

	// when
	actual := &K{}
	err := json.Unmarshal([]byte(input), actual)

	// then
	assert.Error(t, err)
}

type T struct {
	Token tokenID `json:"token"`
}

func TestTokenIDUnmarshalJSONSuccess(t *testing.T) {
	// given

	// when
	actual := &T{}
	err := json.Unmarshal([]byte(`{"token": "0.0.123"}`), actual)

	// then
	assert.NoError(t, err)
	assert.Equal(t, "0.0.123", actual.Token.String())
}

func TestTokenIDUnmarshalJSONInvalidTokenString(t *testing.T) {
	// given

	// when
	actual := &T{}
	err := json.Unmarshal([]byte(`{"token": "a.b.c"}`), actual)

	// then
	assert.Error(t, err)
}

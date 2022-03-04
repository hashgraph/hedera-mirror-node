/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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
	"encoding/json"
	"fmt"
	"testing"

	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/stretchr/testify/assert"
)

var (
	ecdsaSecp256k1PublicKeyProtoPrefix = []byte{0x3a, 0x21}
	ed25519PublicKeyProtoPrefix        = []byte{0x12, 0x20}

	ecdsaSecp256k1Sk, _ = hedera.PrivateKeyGenerateEcdsa()
	ed25519Sk, _        = hedera.PrivateKeyGenerateEd25519()
)

func TestPublicKeyIsEmpty(t *testing.T) {
	tests := []struct {
		name        string
		publicKey   hedera.PublicKey
		expectEmpty bool
	}{
		{
			name:        "Empty",
			publicKey:   hedera.PublicKey{},
			expectEmpty: true,
		},
		{
			name:      " NonEmpty",
			publicKey: ed25519Sk.PublicKey(),
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// given
			wrapped := PublicKey{PublicKey: tt.publicKey}

			if tt.expectEmpty {
				assert.True(t, wrapped.IsEmpty())
			} else {
				assert.False(t, wrapped.IsEmpty())
			}
		})
	}
}

func TestPublicKeyToAlias(t *testing.T) {
	tests := []struct {
		name        string
		publicKey   hedera.PublicKey
		protoPrefix []byte
	}{
		{
			name:        "EcdsaSecp256k1",
			publicKey:   ecdsaSecp256k1Sk.PublicKey(),
			protoPrefix: ecdsaSecp256k1PublicKeyProtoPrefix,
		},
		{
			name:        "Ed25519",
			publicKey:   ed25519Sk.PublicKey(),
			protoPrefix: ed25519PublicKeyProtoPrefix,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// given
			wrapped := PublicKey{PublicKey: tt.publicKey}
			expected := append([]byte{}, tt.protoPrefix...)
			expected = append(expected, wrapped.BytesRaw()...)

			// when
			actual, err := wrapped.ToAlias()

			// then
			assert.Nil(t, err)
			assert.Equal(t, expected, actual)
		})
	}
}

func TestPublicKeyToAliasEmptyKey(t *testing.T) {
	// given
	pk := hedera.PublicKey{}
	wrapped := PublicKey{PublicKey: pk}

	// when
	actual, err := wrapped.ToAlias()

	// then
	assert.NotNil(t, err)
	assert.Nil(t, actual)
}

type k struct {
	Key PublicKey `json:"key"`
}

func TestPublicKeyUnmarshalJSONSuccess(t *testing.T) {
	// given
	expected := ed25519Sk.PublicKey()
	input := fmt.Sprintf("{\"key\": \"%s\"}", expected.String())

	// when
	actual := &k{}
	err := json.Unmarshal([]byte(input), actual)

	// then
	assert.NoError(t, err)
	assert.Equal(t, expected, actual.Key.PublicKey)
}

func TestPublicKeyUnmarshalJSONInvalidInput(t *testing.T) {
	// given
	input := "foobar"

	// when
	actual := &k{}
	err := json.Unmarshal([]byte(input), actual)

	// then
	assert.Error(t, err)
}

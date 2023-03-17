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

package types

import (
	"encoding/json"
	"fmt"
	"testing"

	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/stretchr/testify/assert"
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
			name:      "NonEmpty",
			publicKey: ed25519PublicKey,
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
		name              string
		publicKey         hedera.PublicKey
		expectedAlias     []byte
		expectedCurveType types.CurveType
	}{
		{
			name:              "EcdsaSecp256k1",
			publicKey:         ecdsaSecp256k1PublicKey,
			expectedAlias:     ecdsaSecp256k1Alias,
			expectedCurveType: types.Secp256k1,
		},
		{
			name:              "Ed25519",
			publicKey:         ed25519PublicKey,
			expectedAlias:     ed25519Alias,
			expectedCurveType: types.Edwards25519,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// given
			wrapped := PublicKey{PublicKey: tt.publicKey}

			// when
			actualAlias, actualCurveType, err := wrapped.ToAlias()

			// then
			assert.Nil(t, err)
			assert.Equal(t, tt.expectedAlias, actualAlias)
			assert.Equal(t, tt.expectedCurveType, actualCurveType)
		})
	}
}

func TestPublicKeyToAliasEmptyKey(t *testing.T) {
	// given
	pk := hedera.PublicKey{}
	wrapped := PublicKey{PublicKey: pk}

	// when
	actualAlias, actualCurveType, err := wrapped.ToAlias()

	// then
	assert.NotNil(t, err)
	assert.Nil(t, actualAlias)
	assert.Equal(t, zeroCurveType, actualCurveType)
}

type k struct {
	Key PublicKey `json:"key"`
}

func TestPublicKeyUnmarshalJSONSuccess(t *testing.T) {
	// given
	expected := ed25519PublicKey
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

func TestNewPublicKeyFromAlias(t *testing.T) {
	tests := []struct {
		name              string
		alias             []byte
		expectedCurveType types.CurveType
		expectedPublicKey PublicKey
	}{
		{
			name:              "EcdsaSecp256k1",
			alias:             ecdsaSecp256k1Alias,
			expectedCurveType: types.Secp256k1,
			expectedPublicKey: PublicKey{ecdsaSecp256k1PublicKey},
		},
		{
			name:              "Ed25519",
			alias:             ed25519Alias,
			expectedCurveType: types.Edwards25519,
			expectedPublicKey: PublicKey{ed25519PublicKey},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			actualCurveType, actualPublicKey, err := NewPublicKeyFromAlias(tt.alias)

			assert.NoError(t, err)
			assert.Equal(t, tt.expectedCurveType, actualCurveType)
			assert.Equal(t, tt.expectedPublicKey, actualPublicKey)
		})
	}
}

func TestNewPublicKeyFromAliasFail(t *testing.T) {
	tests := []struct {
		name  string
		alias []byte
	}{
		{
			name:  "Empty alias",
			alias: []byte{},
		},
		{
			name:  "Nil alias",
			alias: nil,
		},
		{
			name:  "Invalid alias bytes",
			alias: []byte{1, 2, 3, 4},
		},
		{
			name:  "Invalid key type",
			alias: getKeyListAlias(),
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			curveType, publicKey, err := NewPublicKeyFromAlias(tt.alias)

			assert.Error(t, err)
			assert.Equal(t, zeroCurveType, curveType)
			assert.Equal(t, PublicKey{}, publicKey)
		})
	}
}

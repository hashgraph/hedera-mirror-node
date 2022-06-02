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
	"encoding/hex"
	"fmt"
	"testing"

	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/stretchr/testify/assert"
	"github.com/thanhpk/randstr"
)

var (
	ed25519PrivateKey, _   = hedera.PrivateKeyGenerateEd25519()
	ed25519PublicKey       = ed25519PrivateKey.PublicKey()
	secp256k1PrivateKey, _ = hedera.PrivateKeyGenerateEcdsa()
	secp256k1PublicKey     = secp256k1PrivateKey.PublicKey()

	zeroAccountId         = AccountId{}
	ed25519AliasAccountId = AccountId{
		aliasKey:  &ed25519PublicKey,
		alias:     ed25519PublicKey.BytesRaw(),
		curveType: types.Edwards25519,
	}
	secp256k1AliasAccountId = AccountId{
		aliasKey:  &secp256k1PublicKey,
		alias:     secp256k1PublicKey.BytesRaw(),
		curveType: types.Secp256k1,
	}
	nonAliasAccountId = AccountId{accountId: domain.MustDecodeEntityId(125)}

	ed25519AliasString   = hex.EncodeToString(ed25519PublicKey.BytesRaw())
	secp256k1AliasString = hex.EncodeToString(secp256k1PublicKey.BytesRaw())
)

func TestAccountIdGetAlias(t *testing.T) {
	tests := []struct {
		name     string
		input    AccountId
		expected []byte
	}{
		{
			name:     "ZeroAccount",
			input:    AccountId{},
			expected: nil,
		},
		{
			name:     "Alias",
			input:    ed25519AliasAccountId,
			expected: ed25519PublicKey.BytesRaw(),
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			assert.Equal(t, tt.expected, tt.input.GetAlias())
		})
	}
}

func TestAccountIdGetCurveType(t *testing.T) {
	tests := []struct {
		name     string
		input    AccountId
		expected types.CurveType
	}{
		{
			name:     "ZeroAccount",
			input:    zeroAccountId,
			expected: "",
		},
		{
			name:     "Ed25519Alias",
			input:    ed25519AliasAccountId,
			expected: types.Edwards25519,
		},
		{
			name:     "Secp256k1Alias",
			input:    secp256k1AliasAccountId,
			expected: types.Secp256k1,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			assert.Equal(t, tt.expected, tt.input.GetCurveType())
		})
	}
}

func TestAccountIdGetId(t *testing.T) {
	tests := []struct {
		name     string
		input    AccountId
		expected int64
	}{
		{
			name:     "ZeroAccount",
			input:    zeroAccountId,
			expected: 0,
		},
		{
			name:     "Alias",
			input:    ed25519AliasAccountId,
			expected: 0,
		},
		{
			name:     "Non-alias",
			input:    nonAliasAccountId,
			expected: 125,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			assert.Equal(t, tt.expected, tt.input.GetId())
		})
	}
}

func TestAccountIdHasAlias(t *testing.T) {
	tests := []struct {
		name   string
		input  AccountId
		truthy bool
	}{
		{
			name:  "ZeroAccount",
			input: zeroAccountId,
		},
		{
			name:   "Alias",
			input:  ed25519AliasAccountId,
			truthy: true,
		},
		{
			name:  "Non-alias",
			input: nonAliasAccountId,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if tt.truthy {
				assert.True(t, tt.input.HasAlias())
			} else {
				assert.False(t, tt.input.HasAlias())
			}
		})
	}
}

func TestAccountIdIsZero(t *testing.T) {
	tests := []struct {
		name   string
		input  AccountId
		truthy bool
	}{
		{
			name:   "ZeroAccount",
			input:  AccountId{},
			truthy: true,
		},
		{
			name:  "Alias",
			input: ed25519AliasAccountId,
		},
		{
			name:  "Non-alias",
			input: nonAliasAccountId,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if tt.truthy {
				assert.True(t, tt.input.IsZero())
			} else {
				assert.False(t, tt.input.IsZero())
			}
		})
	}
}

func TestAccountIdString(t *testing.T) {
	tests := []struct {
		name     string
		input    AccountId
		expected string
	}{
		{
			name:     "ZeroAccount",
			input:    zeroAccountId,
			expected: "0.0.0",
		},
		{
			name:     "Alias",
			input:    ed25519AliasAccountId,
			expected: "0x" + hex.EncodeToString(ed25519PublicKey.BytesRaw()),
		},
		{
			name:     "Non-alias",
			input:    nonAliasAccountId,
			expected: "0.0.125",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			assert.Equal(t, tt.expected, tt.input.String())
		})
	}
}

func TestAccountIdToRosetta(t *testing.T) {
	tests := []struct {
		name     string
		input    AccountId
		expected *types.AccountIdentifier
	}{
		{
			name:     "Alias",
			input:    ed25519AliasAccountId,
			expected: &types.AccountIdentifier{Address: "0x" + hex.EncodeToString(ed25519PublicKey.BytesRaw())},
		},
		{
			name:     "Non-alias",
			input:    nonAliasAccountId,
			expected: &types.AccountIdentifier{Address: "0.0.125"},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			assert.Equal(t, tt.expected, tt.input.ToRosetta())
		})
	}
}

func TestAccountIdToSdkAccountId(t *testing.T) {
	pubKey := ed25519PublicKey
	tests := []struct {
		name     string
		input    AccountId
		expected hedera.AccountID
	}{
		{
			name:     "AliasCopy",
			input:    ed25519AliasAccountId,
			expected: hedera.AccountID{AliasKey: &pubKey},
		},
		{
			name:     "AliasPointer",
			input:    ed25519AliasAccountId,
			expected: hedera.AccountID{AliasKey: &ed25519PublicKey},
		},
		{
			name:     "Non-alias",
			input:    nonAliasAccountId,
			expected: hedera.AccountID{Account: 125},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			assert.Equal(t, tt.expected, tt.input.ToSdkAccountId())
		})
	}
}

func TestNewAccountIdFromStringShardRealmAccount(t *testing.T) {
	tests := []struct {
		input     string
		expectErr bool
		expected  string
	}{
		{
			input:    "0.1.2",
			expected: "0.1.2",
		},
		{
			input:     "",
			expectErr: true,
		},
		{
			input:     "a.b.c",
			expectErr: true,
		},
		{
			input:     "0.1.2.3",
			expectErr: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.input, func(t *testing.T) {
			accountId, err := NewAccountIdFromString(tt.input, 0, 0)
			if !tt.expectErr {
				assert.Nil(t, err)
				assert.Equal(t, tt.expected, accountId.String())
				assert.Nil(t, accountId.GetNetworkAlias())
			} else {
				assert.NotNil(t, err)
				assert.Equal(t, zeroAccountId, accountId)
			}
		})
	}
}

func TestNewAccountIdFromStringAlias(t *testing.T) {
	tests := []struct {
		input            string
		expectErr        bool
		curveType        types.CurveType
		networkAlias     []byte
		sdkAccountString string
		shard            int64
		realm            int64
	}{
		{
			input:            ed25519AliasString,
			curveType:        types.Edwards25519,
			networkAlias:     concatBytes(ed25519PublicKeyProtoPrefix, ed25519PublicKey.BytesRaw()),
			sdkAccountString: "0.1." + ed25519PublicKey.String(),
			realm:            1,
		},
		{
			input:            secp256k1AliasString,
			curveType:        types.Secp256k1,
			networkAlias:     concatBytes(ecdsaSecp256k1PublicKeyProtoPrefix, secp256k1PublicKey.BytesRaw()),
			sdkAccountString: "0.0." + secp256k1PublicKey.String(),
		},
		{
			input:     "",
			expectErr: true,
		},
		{
			input:     randstr.Hex(10),
			expectErr: true,
		},
		{
			input:     "xyz",
			expectErr: true,
		},
		{
			input:     ed25519AliasString,
			shard:     -1,
			realm:     -1,
			expectErr: true,
		},
	}

	for _, tt := range tests {
		name := fmt.Sprintf("alias:'%s',shard:%d,reaml:%d", tt.input, tt.shard, tt.realm)
		t.Run(name, func(t *testing.T) {
			accountId, err := NewAccountIdFromString(tt.input, tt.shard, tt.realm)
			if !tt.expectErr {
				assert.Nil(t, err)
				assert.Equal(t, tt.curveType, accountId.GetCurveType())
				assert.Equal(t, tt.networkAlias, accountId.GetNetworkAlias())
				assert.Equal(t, tt.sdkAccountString, accountId.ToSdkAccountId().String())
			} else {
				assert.NotNil(t, err)
				assert.Equal(t, zeroAccountId, accountId)
			}
		})
	}
}

func TestNewAccountIdFromAlias(t *testing.T) {
	tests := []struct {
		input            []byte
		expectErr        bool
		curveType        types.CurveType
		networkAlias     []byte
		sdkAccountString string
	}{
		{
			input:            ed25519PublicKey.BytesRaw(),
			curveType:        types.Edwards25519,
			networkAlias:     concatBytes(ed25519PublicKeyProtoPrefix, ed25519PublicKey.BytesRaw()),
			sdkAccountString: "0.1." + ed25519PublicKey.String(),
		},
		{
			input:            secp256k1PublicKey.BytesRaw(),
			curveType:        types.Secp256k1,
			networkAlias:     concatBytes(ecdsaSecp256k1PublicKeyProtoPrefix, secp256k1PublicKey.BytesRaw()),
			sdkAccountString: "0.1." + secp256k1PublicKey.String(),
		},
		{
			input:     []byte{},
			expectErr: true,
		},
		{
			input:     randstr.Bytes(10),
			expectErr: true,
		},
	}

	for _, tt := range tests {
		t.Run(hex.EncodeToString(tt.input), func(t *testing.T) {
			accountId, err := NewAccountIdFromAlias(tt.input, 0, 1)
			if !tt.expectErr {
				assert.Nil(t, err)
				assert.Equal(t, tt.curveType, accountId.GetCurveType())
				assert.Equal(t, tt.networkAlias, accountId.GetNetworkAlias())
				assert.Equal(t, tt.sdkAccountString, accountId.ToSdkAccountId().String())
			} else {
				assert.NotNil(t, err)
				assert.Equal(t, zeroAccountId, accountId)
			}
		})
	}
}

func TestNewAccountIdFromEntityId(t *testing.T) {
	tests := []struct {
		input    domain.EntityId
		expected string
	}{
		{
			input:    domain.MustDecodeEntityId(150),
			expected: "0.0.150",
		},
		{
			input:    domain.MustDecodeEntityId(int64(281483566645258)),
			expected: "1.2.10",
		},
	}

	for _, tt := range tests {
		t.Run(tt.expected, func(t *testing.T) {
			accountId := NewAccountIdFromEntityId(tt.input)
			assert.Equal(t, tt.expected, accountId.String())
		})
	}
}

func TestNewAccountIdFromSdkAccountId(t *testing.T) {
	tests := []struct {
		input        hedera.AccountID
		curveType    types.CurveType
		expectErr    bool
		hasAlias     bool
		networkAlias []byte
	}{
		{input: hedera.AccountID{Account: 150}},
		{input: hedera.AccountID{Shard: 1, Realm: 2, Account: 150}},
		{input: hedera.AccountID{Shard: 1, Realm: 2, Account: 150}},
		{
			input:        hedera.AccountID{Realm: 2, AliasKey: &ed25519PublicKey},
			curveType:    types.Edwards25519,
			hasAlias:     true,
			networkAlias: concatBytes(ed25519PublicKeyProtoPrefix, ed25519PublicKey.BytesRaw()),
		},
		{
			input:        hedera.AccountID{Realm: 2, AliasKey: &secp256k1PublicKey},
			curveType:    types.Secp256k1,
			hasAlias:     true,
			networkAlias: concatBytes(ecdsaSecp256k1PublicKeyProtoPrefix, secp256k1PublicKey.BytesRaw()),
		},
		{input: hedera.AccountID{Shard: 1 << 15}, expectErr: true},
		{input: hedera.AccountID{Realm: 1 << 16}, expectErr: true},
		{input: hedera.AccountID{Account: 1 << 32}, expectErr: true},
		{input: hedera.AccountID{AliasKey: &hedera.PublicKey{}}, expectErr: true},
	}

	for _, tt := range tests {
		t.Run(tt.input.String(), func(t *testing.T) {
			accountId, err := NewAccountIdFromSdkAccountId(tt.input)
			if !tt.expectErr {
				assert.Nil(t, err)
				assert.Equal(t, tt.input.String(), accountId.ToSdkAccountId().String())
				assert.Equal(t, tt.curveType, accountId.GetCurveType())
				assert.Equal(t, tt.hasAlias, accountId.HasAlias())
				assert.Equal(t, tt.networkAlias, accountId.GetNetworkAlias())
			} else {
				assert.NotNil(t, err)
				assert.Equal(t, zeroAccountId, accountId)
			}
		})
	}
}

func concatBytes(s1, s2 []byte) []byte {
	r := make([]byte, len(s1))
	copy(r, s1)
	return append(r, s2...)
}

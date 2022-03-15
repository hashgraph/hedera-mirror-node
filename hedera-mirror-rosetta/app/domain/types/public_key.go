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
	"fmt"

	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/tools"
	"github.com/hashgraph/hedera-protobufs-go/services"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/pkg/errors"
	"google.golang.org/protobuf/proto"
)

const (
	ecdsaSecp256k1PublicKeySize = 33
	ed25519PublicKeySize        = 32
)

// PublicKey embed hedera.PublicKey and implement the Unmarshaler interface
type PublicKey struct {
	hedera.PublicKey
}

func (pk PublicKey) IsEmpty() bool {
	return len(pk.PublicKey.Bytes()) == 0
}

func (pk PublicKey) ToAlias() ([]byte, error) {
	rawKey := pk.PublicKey.BytesRaw()
	var key services.Key
	switch keySize := len(rawKey); keySize {
	case ed25519PublicKeySize:
		key = services.Key{Key: &services.Key_Ed25519{Ed25519: rawKey}}
	case ecdsaSecp256k1PublicKeySize:
		key = services.Key{Key: &services.Key_ECDSASecp256K1{ECDSASecp256K1: rawKey}}
	default:
		return nil, errors.New(fmt.Sprintf("Unknown public key type with %d raw bytes", keySize))
	}

	// HIP-32 defines the alias as "a byte array (protobuf bytes) that is formed by serializing a protobuf Key
	// that represents a primitive public key (not a threshold key or key list, etc.)".
	alias, err := proto.Marshal(&key)
	if err != nil {
		return nil, errors.New(fmt.Sprintf("Failed to marshal proto Key: %s", err))
	}
	return alias, nil
}

func (pk *PublicKey) UnmarshalJSON(data []byte) error {
	var err error
	pk.PublicKey, err = hedera.PublicKeyFromString(tools.SafeUnquote(string(data)))
	return err
}

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
	"encoding/base64"
	"strconv"

	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/config"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools"
)

const (
	MetadataKeyMetadatas     = "metadatas"
	MetadataKeySerialNumbers = "serial_numbers"
	MetadataKeyType          = "type"
)

type Amount interface {
	GetValue() int64
	ToRosetta() *types.Amount
}

type HbarAmount struct {
	Value int64
}

func (h *HbarAmount) GetValue() int64 {
	return h.Value
}

// ToRosetta returns Rosetta type Amount with hbar currency
func (h *HbarAmount) ToRosetta() *types.Amount {
	return &types.Amount{
		Value:    strconv.FormatInt(h.Value, 10),
		Currency: config.CurrencyHbar,
	}
}

// TokenAmount holds token amount unmarshalled from aggregated json string built by db query
type TokenAmount struct {
	Decimals      int64 `json:"decimals"`
	Metadatas     [][]byte
	SerialNumbers []int64         `json:"serial_numbers"`
	TokenId       domain.EntityId `json:"token_id"`
	Type          string          `json:"type"`
	Value         int64           `json:"value"`
}

func (t *TokenAmount) GetValue() int64 {
	return t.Value
}

func (t *TokenAmount) SetMetadatas(metadatas [][]byte) *TokenAmount {
	t.Metadatas = metadatas
	return t
}

func (t *TokenAmount) SetSerialNumbers(serialNumbers []int64) *TokenAmount {
	t.SerialNumbers = serialNumbers
	return t
}

// ToRosetta returns Rosetta type Amount with the token's currency
func (t *TokenAmount) ToRosetta() *types.Amount {
	amount := types.Amount{
		Value: strconv.FormatInt(t.Value, 10),
		Currency: &types.Currency{
			Symbol:   t.TokenId.String(),
			Decimals: int32(t.Decimals),
			Metadata: map[string]interface{}{MetadataKeyType: t.Type},
		},
	}
	if t.Type == domain.TokenTypeNonFungibleUnique {
		metadata := make(map[string]interface{})
		if len(t.SerialNumbers) > 0 {
			serialNumbers := make([]string, 0, len(t.SerialNumbers))
			for _, serialNumber := range t.SerialNumbers {
				serialNumbers = append(serialNumbers, strconv.FormatInt(serialNumber, 10))
			}
			metadata[MetadataKeySerialNumbers] = serialNumbers
		}

		if len(t.Metadatas) > 0 {
			nftMetadatas := make([]string, 0, len(t.Metadatas))
			for _, nftMetadata := range t.Metadatas {
				nftMetadatas = append(nftMetadatas, base64.StdEncoding.EncodeToString(nftMetadata))
			}
			metadata[MetadataKeyMetadatas] = nftMetadatas
		}

		amount.Metadata = metadata
	}

	return &amount
}

func NewTokenAmount(token domain.Token, amount int64) *TokenAmount {
	return &TokenAmount{
		Decimals: token.Decimals,
		TokenId:  token.TokenId,
		Type:     token.Type,
		Value:    amount,
	}
}

func NewAmount(amount *types.Amount) (Amount, *types.Error) {
	value, err := tools.ToInt64(amount.Value)
	if err != nil {
		return nil, errors.ErrInvalidOperationsAmount
	}

	currency := amount.Currency
	if currency.Decimals < 0 {
		return nil, errors.ErrInvalidCurrency
	}

	if currency.Symbol == config.CurrencyHbar.Symbol {
		return &HbarAmount{Value: value}, nil
	}

	tokenId, err := domain.EntityIdFromString(currency.Symbol)
	if err != nil {
		return nil, errors.ErrInvalidToken
	}

	tokenType, ok := currency.Metadata[MetadataKeyType].(string)
	if !ok {
		return nil, errors.ErrInvalidCurrency
	}

	tokenAmount := &TokenAmount{
		Decimals: int64(currency.Decimals),
		TokenId:  tokenId,
		Type:     tokenType,
		Value:    value,
	}

	if tokenType == domain.TokenTypeNonFungibleUnique {
		if err := parseNftMetadata(amount.Metadata, tokenAmount); err != nil {
			return nil, err
		}
	} else if tokenType != domain.TokenTypeFungibleCommon {
		return nil, errors.ErrInvalidCurrency
	}

	return tokenAmount, nil
}

func parseNftMetadata(metadata map[string]interface{}, tokenAmount *TokenAmount) *types.Error {
	if tokenAmount.Decimals != 0 {
		return errors.ErrInvalidCurrency
	}

	if tokenAmount.Value == 0 {
		return nil
	}

	// one metadata entry, either "serial_numbers" or "metadatas"
	if len(metadata) != 1 {
		return errors.ErrInvalidOperationsAmount
	}

	if serialNumbers, ok := metadata[MetadataKeySerialNumbers].([]string); ok {
		tokenAmount.SerialNumbers = make([]int64, 0, len(serialNumbers))
		for _, serialNumber := range serialNumbers {
			number, err := strconv.ParseInt(serialNumber, 10, 64)
			if err != nil {
				return errors.ErrInvalidOperationsTotalAmount
			}
			tokenAmount.SerialNumbers = append(tokenAmount.SerialNumbers, number)
		}
	} else if metadatas, ok := metadata[MetadataKeyMetadatas].([]string); ok {
		tokenAmount.Metadatas = make([][]byte, 0, len(metadatas))
		for _, metadata := range metadatas {
			metaBytes, err := base64.StdEncoding.DecodeString(metadata)
			if err != nil {
				return errors.ErrInvalidOperationsAmount
			}
			tokenAmount.Metadatas = append(tokenAmount.Metadatas, metaBytes)
		}
	} else {
		return errors.ErrInvalidOperationsAmount
	}

	expectedValue := int64(len(tokenAmount.SerialNumbers) + len(tokenAmount.Metadatas))
	if tokenAmount.Value != expectedValue && tokenAmount.Value != -expectedValue {
		return errors.ErrInvalidOperationsAmount
	}

	return nil
}

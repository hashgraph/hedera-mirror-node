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

	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/stretchr/testify/assert"
)

func exampleBlock() *Block {
	return &Block{
		Index:               2,
		Hash:                "somehash",
		ConsensusStartNanos: 10000000,
		ConsensusEndNanos:   12300000,
		ParentIndex:         1,
		ParentHash:          "someparenthash",
		Transactions: []*Transaction{
			{
				Hash:       "somehash",
				Operations: []*Operation{},
			},
		},
	}
}

func expectedBlock() *types.Block {
	return &types.Block{
		BlockIdentifier: &types.BlockIdentifier{
			Index: 2,
			Hash:  "0xsomehash",
		},
		ParentBlockIdentifier: &types.BlockIdentifier{
			Index: 1,
			Hash:  "0xsomeparenthash",
		},
		Timestamp: int64(10),
		Transactions: []*types.Transaction{
			{
				TransactionIdentifier: &types.TransactionIdentifier{Hash: "somehash"},
				Operations:            []*types.Operation{},
				Metadata:              nil,
			},
		},
	}
}

func TestToRosettaBlock(t *testing.T) {
	// when:
	rosettaBlockResult := exampleBlock().ToRosetta()

	// then:
	assert.Equal(t, expectedBlock(), rosettaBlockResult)
}

func TestGetTimestampMillis(t *testing.T) {
	// given:
	exampleBlock := exampleBlock()

	// when:
	resultMillis := exampleBlock.GetTimestampMillis()

	// then:
	assert.Equal(t, int64(10), resultMillis)
}

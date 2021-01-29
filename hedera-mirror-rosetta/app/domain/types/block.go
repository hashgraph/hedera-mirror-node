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
    rTypes "github.com/coinbase/rosetta-sdk-go/types"
    "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools/hex"
    "time"
)

// Block is domain level struct used to represent Block conceptual mapping in Hedera
type Block struct {
    Index               int64
    Hash                string
    ConsensusStartNanos int64
    ConsensusEndNanos   int64
    ParentIndex         int64
    ParentHash          string
    Transactions        []*Transaction
}

// ToRosetta returns Rosetta type Block from the current domain type Block
func (b *Block) ToRosetta() *rTypes.Block {
    transactions := make([]*rTypes.Transaction, len(b.Transactions))
    for i, t := range b.Transactions {
        transactions[i] = t.ToRosetta()
    }

    return &rTypes.Block{
        BlockIdentifier: &rTypes.BlockIdentifier{
            Index: b.Index,
            Hash:  hex.SafeAddHexPrefix(b.Hash),
        },
        ParentBlockIdentifier: &rTypes.BlockIdentifier{
            Index: b.ParentIndex,
            Hash:  hex.SafeAddHexPrefix(b.ParentHash),
        },
        Timestamp:    b.GetTimestampMillis(),
        Transactions: transactions,
    }
}

// GetTimestampMillis returns the block timestamp in milliseconds
func (b *Block) GetTimestampMillis() int64 {
    return b.ConsensusStartNanos / int64(time.Millisecond)
}

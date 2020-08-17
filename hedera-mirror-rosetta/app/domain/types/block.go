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

// FromRosettaBlock populates domain type Block from Rosetta type Block
func FromRosettaBlock(rBlock *rTypes.Block) (*Block, *rTypes.Error) {
	transactions := make([]*Transaction, len(rBlock.Transactions))
	for i, rosettaT := range rBlock.Transactions {
		t, err := FromRosettaTransaction(rosettaT)
		if err != nil {
			return nil, err
		}
		transactions[i] = t
	}

	return &Block{
		Index:               rBlock.BlockIdentifier.Index,
		Hash:                hex.SafeRemoveHexPrefix(rBlock.BlockIdentifier.Hash),
		ConsensusStartNanos: rBlock.BlockIdentifier.Index,
		ConsensusEndNanos:   rBlock.Timestamp,
		ParentIndex:         rBlock.ParentBlockIdentifier.Index,
		ParentHash:          hex.SafeRemoveHexPrefix(rBlock.ParentBlockIdentifier.Hash),
		Transactions:        transactions,
	}, nil
}

// ToRosettaBlock returns Rosetta type Block from the current domain type Block
func (b *Block) ToRosettaBlock() *rTypes.Block {
	transactions := make([]*rTypes.Transaction, len(b.Transactions))
	for i, t := range b.Transactions {
		transactions[i] = t.ToRosettaTransaction()
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

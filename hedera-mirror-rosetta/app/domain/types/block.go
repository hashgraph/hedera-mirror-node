package types

import (
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools/hex"
)

// Block is domain level struct used to represent Block conceptual mapping in Hedera
type Block struct {
	Hash           string
	ConsensusStart int64
	ConsensusEnd   int64
	ParentIndex    int64
	ParentHash     string
	Transactions   []*Transaction
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
		Hash:           hex.SafeRemoveHexPrefix(rBlock.BlockIdentifier.Hash),
		ConsensusStart: rBlock.BlockIdentifier.Index,
		ConsensusEnd:   rBlock.Timestamp,
		ParentIndex:    rBlock.ParentBlockIdentifier.Index,
		ParentHash:     hex.SafeRemoveHexPrefix(rBlock.ParentBlockIdentifier.Hash),
		Transactions:   transactions,
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
			Index: b.ConsensusStart,
			Hash:  hex.SafeAddHexPrefix(b.Hash),
		},
		ParentBlockIdentifier: &rTypes.BlockIdentifier{
			Index: b.ParentIndex,
			Hash:  hex.SafeAddHexPrefix(b.ParentHash),
		},
		Timestamp:    b.ConsensusEnd,
		Transactions: transactions,
	}
}

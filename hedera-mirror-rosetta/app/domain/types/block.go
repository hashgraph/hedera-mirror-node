package types

import (
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
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
func (b *Block) FromRosettaBlock(rBlock *rTypes.Block) {
	b.Hash = rBlock.BlockIdentifier.Hash
	b.ConsensusStart = rBlock.BlockIdentifier.Index
	b.ConsensusEnd = rBlock.Timestamp
	b.ParentIndex = rBlock.ParentBlockIdentifier.Index
	b.ParentHash = rBlock.ParentBlockIdentifier.Hash

	transactions := make([]*Transaction, len(rBlock.Transactions))
	for i, rosettaT := range rBlock.Transactions {
		t := &Transaction{}
		t.FromRosettaTransaction(rosettaT)
		transactions[i] = t
	}
	b.Transactions = transactions
}

// ToRosettaBlock returns Rosetta type Block from the current domain type Block
func (b *Block) ToRosettaBlock() *rTypes.Block {
	transactions := make([]*rTypes.Transaction, len(b.Transactions))
	for i, t := range b.Transactions {
		transactions[i] = t.ToRosettaTransaction()
	}

	return &rTypes.Block{
		BlockIdentifier:       &rTypes.BlockIdentifier{Index: b.ConsensusStart, Hash: b.Hash},
		ParentBlockIdentifier: &rTypes.BlockIdentifier{Index: b.ParentIndex, Hash: b.ParentHash},
		Timestamp:             b.ConsensusEnd,
		Transactions:          transactions,
	}
}

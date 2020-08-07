package types

import (
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
)

// Block is domain level struct used to represent Block conceptual mapping in Hedera
type Block struct {
	ID             int64
	Hash           string
	ParentID       int64
	ParentHash     string
	ConsensusStart int64
	ConsensusEnd   int64
	Transactions   []*Transaction
}

// FromRosettaBlock populates domain type Block from Rosetta type Block
func (b *Block) FromRosettaBlock(rBlock *rTypes.Block) {
	b.ID = rBlock.BlockIdentifier.Index
	b.Hash = rBlock.BlockIdentifier.Hash
	b.ParentID = rBlock.ParentBlockIdentifier.Index
	b.ParentHash = rBlock.ParentBlockIdentifier.Hash
	b.ConsensusEnd = rBlock.Timestamp

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
		BlockIdentifier:       &rTypes.BlockIdentifier{Index: b.ID, Hash: b.Hash},
		ParentBlockIdentifier: &rTypes.BlockIdentifier{Index: b.ParentID, Hash: b.ParentHash},
		Timestamp:             b.ConsensusEnd,
		Transactions:          transactions,
	}
}

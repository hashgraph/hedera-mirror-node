package types

import (
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
)

// Block is domain level struct used to represent Block conceptual mapping in Hedera
type Block struct {
	ID           int64
	Hash         string
	ParentID     int64
	ParentHash   string
	Timestamp    int64
	Transactions []Transaction
}

// FromRosettaBlock populates domain type Block from Rosetta type Block
func (b *Block) FromRosettaBlock(rBlock *rTypes.Block) {
	// TODO Implement
}

// ToRosettaBlock returns Rosetta type Block from the current domain type Block
func (b *Block) ToRosettaBlock() *rTypes.Block {
	return nil
}

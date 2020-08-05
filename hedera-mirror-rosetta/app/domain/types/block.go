package types

import (
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
)

type Block struct {
	ID           int64
	Hash         string
	ParentID     int64
	ParentHash   string
	Transactions []Transaction
}

func (b *Block) FromRosettaBlock(rBlock *rTypes.Block) {
	// TODO Implement
}

func (b *Block) ToRosettaBlock() *rTypes.Block {
	return nil
}

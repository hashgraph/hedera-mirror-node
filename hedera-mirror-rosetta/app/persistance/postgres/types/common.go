package types

type CryptoTransfer struct {
	EntityID           int64 `gorm:"type:bigint"`
	ConsensusTimestamp int64 `gorm:"type:bigint"`
	Amount             int64 `gorm:"type:bigint"`
}

// TableName - Set table name of the CryptoTransfers to be `crypto_transfer`
func (CryptoTransfer) TableName() string {
	return "crypto_transfer"
}

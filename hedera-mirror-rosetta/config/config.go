package config

import "github.com/coinbase/rosetta-sdk-go/types"

const (
	OperationTypeCryptoTransfer = "CRYPTOTRANSFER"
)

const (
	Blockchain       = "Hedera"
	CurrencySymbol   = "HBAR"
	CurrencyDecimals = 8
)

var (
	CurrencyHbar = &types.Currency{
		Symbol:   CurrencySymbol,
		Decimals: CurrencyDecimals,
		Metadata: map[string]interface{}{
			"issuer": Blockchain,
		},
	}
)

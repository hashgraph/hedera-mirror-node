package maphelper

import "github.com/coinbase/rosetta-sdk-go/types"

func GetStringValuesFromIntStringMap(mapping map[int]string) []string {
	values := make([]string, 0, len(mapping))

	for _, v := range mapping {
		values = append(values, v)
	}

	return values
}

func GetErrorValuesFromStringErrorMap(mapping map[string]*types.Error) []*types.Error {
	values := make([]*types.Error, 0, len(mapping))

	for _, v := range mapping {
		values = append(values, v)
	}

	return values
}

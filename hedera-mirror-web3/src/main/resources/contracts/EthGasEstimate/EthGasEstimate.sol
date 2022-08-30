// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

import "./HederaTokenService.sol";

contract EthGasEstimate is HederaTokenService {

    // External function for minting tokens
    function mint(address _tokenAddress, uint64 _amount, bytes[] memory _metadata) external
    returns (int responseCode, uint64 newTotalSupply, int64[] memory serialNumbers) {
        (responseCode, newTotalSupply, serialNumbers) = HederaTokenService.mintToken(_tokenAddress, _amount, _metadata);

        if(responseCode != 22) {
            revert();
        }
    }
}

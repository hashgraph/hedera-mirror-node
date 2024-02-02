// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.6.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./HederaTokenService.sol";
import "./IHederaTokenService.sol";
import "./HederaResponseCodes.sol";

contract HTSCalls is HederaTokenService {

    function transferNFTCall(address token, address sender, address receiver, int64 serialNum) external
    returns (int responseCode) {
        return HederaTokenService.transferNFT(token, sender, receiver, serialNum);
    }

    function mintTokenCall(address token, uint64 amount, bytes[] memory metadata) external
    returns (int responseCode, uint64 newTotalSupply, int64[] memory serialNumbers) {
        return HederaTokenService.mintToken(token, amount, metadata);
    }

    function burnTokenCall(address token, uint64 amount, int64[] memory serialNumbers) external
    returns (int responseCode, uint64 newTotalSupply) {
        return HederaTokenService.burnToken(token, amount, serialNumbers);
    }

    function mintAndTransferNonFungibleToken(uint64 amount, address tokenAddress, bytes[] memory metadata, address sender, address receiver) external {
        (int responseCode, uint64 newTotalSupply, int64[] memory serialNumbers) = HederaTokenService.mintToken(tokenAddress, amount, metadata);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ("NonFungible mint failed!");
        }

        responseCode = HederaTokenService.transferNFT(tokenAddress, sender, receiver, serialNumbers[0]);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ("NonFungible transfer failed!");
        }
    }
}
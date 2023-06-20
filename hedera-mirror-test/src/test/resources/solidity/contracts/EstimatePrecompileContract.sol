// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;
pragma experimental ABIEncoderV2;


import "./HederaTokenService.sol";
import "./HederaResponseCodes.sol";
import "@openzeppelin/contracts/token/ERC20/IERC20.sol";

contract EstimatePrecompileContract is HederaTokenService {

    // associate & dissociate
    function associateTokenExternal(address account, address token) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.associateToken(account, token);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert("Token association failed!");
        }
    }

    function nestedAssociateTokenExternal(address account, address token) external
    returns (int responseCode)
    {
        HederaTokenService.associateToken(account, token);
        responseCode = HederaTokenService.associateToken(account, token);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert("Token association failed!");
        }
    }

    function nestedDissociateTokenExternal(address account, address token) external
    returns (int responseCode)
    {
        HederaTokenService.dissociateToken(account, token);
        responseCode = HederaTokenService.dissociateToken(account, token);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert("Token dissociation failed!");
        }
    }

    function dissociateAndAssociateTokenExternal(address account, address token) external
    returns (int responseCode)
    {
        HederaTokenService.dissociateToken(account, token);
        responseCode = HederaTokenService.associateToken(account, token);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert("Nested token association failed!");
        }
    }

    function dissociateTokenExternal(address account, address token) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.dissociateToken(account, token);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert("Token dissociation failed!");
        }
    }
    //associate & dissociate - many
    function associateTokensExternal(address account, address[] memory tokens) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.associateTokens(account, tokens);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert("Token associate failed!");
        }
    }

    function dissociateTokensExternal(address account, address[] memory tokens) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.dissociateTokens(account, tokens);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert("Token dissociation failed!");
        }
    }

    //approve
    function approveExternal(address token, address spender, uint256 amount) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.approve(token, spender, amount);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert("Approval failed!");
        }
    }

    function approveNFTExternal(address token, address approved, uint256 serialNumber) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.approveNFT(token, approved, serialNumber);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert("NFT approval failed!");
        }
    }

    //transfer
    function transferFromExternal(address token, address from, address to, uint256 amount) external
    returns (int responseCode)
    {
        responseCode = this.transferFrom(token, from, to, amount);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert("Transfer failed");
        }
    }

    function transferFromNFTExternal(address token, address from, address to, uint256 serialNumber) external
    returns (int responseCode)
    {
        responseCode = this.transferFromNFT(token, from, to, serialNumber);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert("NFT transfer failed");
        }
    }

    function transferTokenExternal(address token, address sender, address receiver, int64 amount) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.transferToken(token, sender, receiver, amount);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert("Token transfer failed");
        }
    }

    function transferNFTExternal(address token, address sender, address receiver, int64 serialNumber) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.transferNFT(token, sender, receiver, serialNumber);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert("NFT transfer failed");
        }
    }

    //transfer-many
    function transferTokensExternal(address token, address[] memory accountIds, int64[] memory amounts) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.transferTokens(token, accountIds, amounts);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert("Token transfer failed");
        }
    }

    function transferNFTsExternal(address token, address[] memory sender, address[] memory receiver, int64[] memory serialNumber) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.transferNFTs(token, sender, receiver, serialNumber);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert("NFT transfer failed");
        }
    }
}

// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;
pragma experimental ABIEncoderV2;


import "./HederaTokenService.sol";
import "./HederaResponseCodes.sol";
import "@openzeppelin/contracts/token/ERC20/IERC20.sol";

contract ModificationPrecompileTestContract is HederaTokenService {

    function cryptoTransferExternal(IHederaTokenService.TransferList memory transferList, IHederaTokenService.TokenTransferList[] memory tokenTransfers) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.cryptoTransfer(transferList, tokenTransfers);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function mintTokenExternal(address token, int64 amount, bytes[] memory metadata) external
    returns (int responseCode, int64 newTotalSupply, int64[] memory serialNumbers)
    {
        (responseCode, newTotalSupply, serialNumbers) = HederaTokenService.mintToken(token, amount, metadata);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function burnTokenExternal(address token, int64 amount, int64[] memory serialNumbers) external
    returns (int responseCode, int64 newTotalSupply)
    {
        (responseCode, newTotalSupply) = HederaTokenService.burnToken(token, amount, serialNumbers);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function associateTokensExternal(address account, address[] memory tokens) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.associateTokens(account, tokens);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function associateTokenExternal(address account, address token) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.associateToken(account, token);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function dissociateTokensExternal(address account, address[] memory tokens) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.dissociateTokens(account, tokens);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function dissociateTokenExternal(address account, address token) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.dissociateToken(account, token);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function createFungibleTokenExternal(IHederaTokenService.HederaToken memory token,
        int64 initialTotalSupply,
        int32 decimals) external payable
    returns (int responseCode, address tokenAddress)
    {
        (responseCode, tokenAddress) = HederaTokenService.createFungibleToken(token, initialTotalSupply, decimals);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function createFungibleTokenWithCustomFeesExternal(IHederaTokenService.HederaToken memory token,
        int64 initialTotalSupply,
        int32 decimals,
        IHederaTokenService.FixedFee[] memory fixedFees,
        IHederaTokenService.FractionalFee[] memory fractionalFees) external payable
    returns (int responseCode, address tokenAddress)
    {
        (responseCode, tokenAddress) = HederaTokenService.createFungibleTokenWithCustomFees(token, initialTotalSupply, decimals, fixedFees, fractionalFees);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function createNonFungibleTokenExternal(IHederaTokenService.HederaToken memory token) external payable
    returns (int responseCode, address tokenAddress)
    {
        (responseCode, tokenAddress) = HederaTokenService.createNonFungibleToken(token);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function createNonFungibleTokenWithCustomFeesExternal(IHederaTokenService.HederaToken memory token,
        IHederaTokenService.FixedFee[] memory fixedFees,
        IHederaTokenService.RoyaltyFee[] memory royaltyFees) external payable
    returns (int responseCode, address tokenAddress)
    {
        (responseCode, tokenAddress) = HederaTokenService.createNonFungibleTokenWithCustomFees(token, fixedFees, royaltyFees);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function approveExternal(address token, address spender, uint256 amount) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.approve(token, spender, amount);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function transferFromExternal(address token, address from, address to, uint256 amount) external
    returns (int64 responseCode)
    {
        responseCode = HederaTokenService.transferFrom(token, from, to, amount);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function transferFromNFTExternal(address token, address from, address to, uint256 serialNumber) external
    returns (int64 responseCode)
    {
        responseCode = HederaTokenService.transferFromNFT(token, from, to, serialNumber);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function approveNFTExternal(address token, address approved, uint256 serialNumber) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.approveNFT(token, approved, serialNumber);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function freezeTokenExternal(address token, address account) external
    returns (int64 responseCode)
    {
        responseCode = HederaTokenService.freezeToken(token, account);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function unfreezeTokenExternal(address token, address account) external
    returns (int64 responseCode)
    {
        responseCode = HederaTokenService.unfreezeToken(token, account);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function grantTokenKycExternal(address token, address account) external
    returns (int64 responseCode)
    {
        responseCode = HederaTokenService.grantTokenKyc(token, account);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function revokeTokenKycExternal(address token, address account) external
    returns (int64 responseCode)
    {
        responseCode = HederaTokenService.revokeTokenKyc(token, account);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function setApprovalForAllExternal(address token, address operator, bool approved) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.setApprovalForAll(token, operator, approved);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function transferTokensExternal(address token, address[] memory accountIds, int64[] memory amounts) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.transferTokens(token, accountIds, amounts);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function transferNFTsExternal(address token, address[] memory sender, address[] memory receiver, int64[] memory serialNumber) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.transferNFTs(token, sender, receiver, serialNumber);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function transferTokenExternal(address token, address sender, address receiver, int64 amount) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.transferToken(token, sender, receiver, amount);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function transferNFTExternal(address token, address sender, address receiver, int64 serialNumber) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.transferNFT(token, sender, receiver, serialNumber);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function pauseTokenExternal(address token) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.pauseToken(token);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function unpauseTokenExternal(address token) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.unpauseToken(token);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function wipeTokenAccountExternal(address token, address account, int64 amount) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.wipeTokenAccount(token, account, amount);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function wipeTokenAccountNFTExternal(address token, address account, int64[] memory serialNumbers) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.wipeTokenAccountNFT(token, account, serialNumbers);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function deleteTokenExternal(address token) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.deleteToken(token);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function updateTokenKeysExternal(address token, IHederaTokenService.TokenKey[] memory keys) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.updateTokenKeys(token, keys);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function updateTokenExpiryInfoExternal(address token, IHederaTokenService.Expiry memory expiryInfo) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.updateTokenExpiryInfo(token, expiryInfo);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function updateTokenInfoExternal(address token, IHederaTokenService.HederaToken memory tokenInfo) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.updateTokenInfo(token, tokenInfo);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function getBalanceOfWithDirectRedirect(address token, address account) external
    returns (bytes memory result)
    {
        (int response, bytes memory result) = HederaTokenService.redirectForToken(token, abi.encodeWithSelector(IERC20.balanceOf.selector, account));
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Token redirect failed");
        }
        return result;
    }

    function callNotExistingPrecompile(address token) public {
        HederaTokenService.redirectForToken(token, abi.encodeWithSelector(bytes4(keccak256("notExistingPrecompile()"))));
    }
}
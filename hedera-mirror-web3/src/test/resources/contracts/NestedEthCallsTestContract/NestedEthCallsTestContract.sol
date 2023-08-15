pragma solidity ^0.8.0;

import "./HederaTokenService.sol";
import "./HederaResponseCodes.sol";
import "@openzeppelin/contracts/token/ERC20/IERC20.sol";

contract NestedEthCalls is HederaTokenService {

    //Update token key + get token info key
    function updateTokenKeysAndGetUpdatedTokenKey(address token, IHederaTokenService.TokenKey[] memory keys, uint keyType) external returns (IHederaTokenService.KeyValue memory) {
        int responseCode = HederaTokenService.updateTokenKeys(token, keys);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert("Could not update token keys.");
        }

        (int response, IHederaTokenService.KeyValue memory key) = HederaTokenService.getTokenKey(token, keyType);
        if (response != HederaResponseCodes.SUCCESS) {
            revert("Could not get token key.");
        }
        return key;
    }

    //Update + get token expiry info
    function updateTokenExpiryAndGetUpdatedTokenExpiry(address token, IHederaTokenService.Expiry memory expiryInfo) external returns (
        int64 second,
        address autoRenewAccount,
        int64 autoRenewPeriod
    ) {
        int responseCode = HederaTokenService.updateTokenExpiryInfo(token, expiryInfo);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert("Could not update token expiry info.");
        }

        (int response, IHederaTokenService.Expiry memory retrievedExpiry) = HederaTokenService.getTokenExpiryInfo(token);
        if (response != HederaResponseCodes.SUCCESS) {
            revert("Could not read token expiry info.");
        }

        // Split the Expiry struct into its individual components
        second = retrievedExpiry.second;
        autoRenewAccount = retrievedExpiry.autoRenewAccount;
        autoRenewPeriod = retrievedExpiry.autoRenewPeriod;
    }

    // Update token info that updates symbol + get token info symbol
    function updateTokenInfoAndGetUpdatedTokenInfoSymbol(address token, IHederaTokenService.HederaToken memory tokenInfo) external returns (string memory symbol) {
        int responseCode = HederaTokenService.updateTokenInfo(token, tokenInfo);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert("Could not update token info.");
        }

        (int response, IHederaTokenService.TokenInfo memory retrievedTokenInfo) = HederaTokenService.getTokenInfo(token);

        if (response != HederaResponseCodes.SUCCESS) {
            revert("Could not get token info.");
        }

        return retrievedTokenInfo.token.symbol;
    }

    // Update token info that updates name + get token info name
    function updateTokenInfoAndGetUpdatedTokenInfoName(address token, IHederaTokenService.HederaToken memory tokenInfo) external returns (string memory name) {
        int responseCode = HederaTokenService.updateTokenInfo(token, tokenInfo);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert("Could not update token info.");
        }

        (int response, IHederaTokenService.TokenInfo memory retrievedTokenInfo) = HederaTokenService.getTokenInfo(token);

        if (response != HederaResponseCodes.SUCCESS) {
            revert("Could not get token info.");
        }

        return retrievedTokenInfo.token.name;
    }

    // Update token info that updates memo + get token info memo
    function updateTokenInfoAndGetUpdatedTokenInfoMemo(address token, IHederaTokenService.HederaToken memory tokenInfo) external returns (string memory memo) {
        int responseCode = HederaTokenService.updateTokenInfo(token, tokenInfo);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert("Could not update token info.");
        }

        (int response, IHederaTokenService.TokenInfo memory retrievedTokenInfo) = HederaTokenService.getTokenInfo(token);

        if (response != HederaResponseCodes.SUCCESS) {
            revert("Could not get token info.");
        }

        return retrievedTokenInfo.token.memo;
    }

    // Update auto renew period + get token info auto renew period
    function updateTokenInfoAndGetUpdatedTokenInfoAutoRenewPeriod(address token, IHederaTokenService.HederaToken memory tokenInfo) external returns (int64 autoRenewPeriod) {
        int responseCode = HederaTokenService.updateTokenInfo(token, tokenInfo);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert("Could not update token info.");
        }

        (int response, IHederaTokenService.TokenInfo memory retrievedTokenInfo) = HederaTokenService.getTokenInfo(token);

        if (response != HederaResponseCodes.SUCCESS) {
            revert("Could not get token info.");
        }

        return retrievedTokenInfo.token.expiry.autoRenewPeriod;
    }

    // Delete token + get token info isDeleted
    function deleteTokenAndGetTokenInfoIsDeleted(address token) external returns (bool deleted) {
        int responseCode = HederaTokenService.deleteToken(token);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert("Could not delete token.");
        }

        (int response, IHederaTokenService.TokenInfo memory retrievedTokenInfo) = HederaTokenService.getTokenInfo(token);
        if (response != HederaResponseCodes.SUCCESS) {
            revert("Could not get token info.");
        }

        return retrievedTokenInfo.deleted;
    }

    // Create token for fungible token with/without default freeze status + name + symbol + getTokenDefaultFreezeStatus + getTokenDefaultKycStatus + isToken
    function createFungibleTokenAndGetIsTokenAndGetDefaultFreezeStatusAndGetDefaultKycStatus(IHederaTokenService.HederaToken memory token, int64 initialTotalSupply, int32 decimals) external payable returns (
        bool defaultKycStatus,
        bool defaultFreezeStatus,
        bool isToken) {
        (int256 responseCode, address tokenAddress) = HederaTokenService.createFungibleToken(token, initialTotalSupply, decimals);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert("Fungible token could not be created.");
        }

        (responseCode, defaultKycStatus) = HederaTokenService.getTokenDefaultKycStatus(tokenAddress);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert("Could not get token default kyc status.");
        }

        (responseCode, defaultFreezeStatus) = HederaTokenService.getTokenDefaultFreezeStatus(tokenAddress);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert("Could not get token default freeze status.");
        }

        (responseCode, isToken) = HederaTokenService.isToken(tokenAddress);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert("isToken(tokenAddress) returned an error.");
        }
    }

    // Create NFT with/without default freeze status + name + symbol + getTokenDefaultFreezeStatus + getTokenDefaultKycStatus + isToken
    function createNFTAndGetIsTokenAndGetDefaultFreezeStatusAndGetDefaultKycStatus(IHederaTokenService.HederaToken memory token) external payable returns (
        bool defaultKycStatus,
        bool defaultFreezeStatus,
        bool isToken) {
        (int256 responseCode, address tokenAddress) = HederaTokenService.createNonFungibleToken(token);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert("Fungible token could not be created.");
        }

        (responseCode, defaultKycStatus) = HederaTokenService.getTokenDefaultKycStatus(tokenAddress);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert("Could not get token default kyc status.");
        }

        (responseCode, defaultFreezeStatus) = HederaTokenService.getTokenDefaultFreezeStatus(tokenAddress);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert("Could not get token default freeze status.");
        }

        (responseCode, isToken) = HederaTokenService.isToken(tokenAddress);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert("isToken(tokenAddress) returned an error.");
        }
    }
}

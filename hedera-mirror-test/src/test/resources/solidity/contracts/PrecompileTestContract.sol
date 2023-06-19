// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./HederaTokenService.sol";
import "./HederaResponseCodes.sol";

contract PrecompileTestContract is HederaTokenService {
    function isTokenAddress(address token) external returns (bool) {
        (int256 response, bool tokenFlag) = HederaTokenService.isToken(token);

        if (response != HederaResponseCodes.SUCCESS) {
            revert("Token isTokenAddress failed!");
        }
        return tokenFlag;
    }

    function isTokenFrozen(address token, address account) external returns (bool) {
        (int256 response, bool frozen) = HederaTokenService.isFrozen(token, account);
        if (response != HederaResponseCodes.SUCCESS) {
            revert("Token isFrozen failed!");
        }
        return frozen;
    }

    function isKycGranted(address token, address account) external returns (bool){
        (int256 response, bool kycGranted) = HederaTokenService.isKyc(token, account);
        if (response != HederaResponseCodes.SUCCESS) {
            revert("Token isKyc failed!");
        }
        return kycGranted;
    }

    function getTokenDefaultFreeze(address token) external returns (bool) {
        (int256 response, bool frozen) = HederaTokenService.getTokenDefaultFreezeStatus(token);
        if (response != HederaResponseCodes.SUCCESS) {
            revert("getTokenDefaultFreezeStatus failed!");
        }
        return frozen;
    }

    function getTokenDefaultKyc(address token) external returns (bool) {
        (int256 response, bool kyc) = HederaTokenService.getTokenDefaultKycStatus(token);
        if (response != HederaResponseCodes.SUCCESS) {
            revert("getTokenDefaultKycStatus failed!");
        }
        return kyc;
    }

    function getCustomFeesForToken(address token) external returns (
        IHederaTokenService.FixedFee[] memory fixedFees,
        IHederaTokenService.FractionalFee[] memory fractionalFees,
        IHederaTokenService.RoyaltyFee[] memory royaltyFees
    )
    {
        int64 responseCode;
        (responseCode, fixedFees, fractionalFees, royaltyFees) = HederaTokenService.getTokenCustomFees(token);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function getInformationForToken(address token) external returns (IHederaTokenService.TokenInfo memory tokenInfo)
    {
        (int256 responseCode,IHederaTokenService.TokenInfo memory retrievedTokenInfo) = HederaTokenService.getTokenInfo(token);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
        tokenInfo = retrievedTokenInfo;
    }

    function getInformationForFungibleToken(address token) external returns (IHederaTokenService.FungibleTokenInfo memory fungibleTokenInfo)
    {
        (int256 responseCode,IHederaTokenService.FungibleTokenInfo memory retrievedTokenInfo) = HederaTokenService.getFungibleTokenInfo(token);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
        fungibleTokenInfo = retrievedTokenInfo;
    }

    function getInformationForNonFungibleToken(address token, int64 serialNumber) external returns (
        IHederaTokenService.NonFungibleTokenInfo memory nonFungibleTokenInfo
    )
    {
        (int256 responseCode,IHederaTokenService.NonFungibleTokenInfo memory retrievedTokenInfo) = HederaTokenService.getNonFungibleTokenInfo(token, serialNumber);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
        nonFungibleTokenInfo = retrievedTokenInfo;
    }

    function getType(address token) external returns (int256) {
        (int256 statusCode, int256 tokenType) = HederaTokenService.getTokenType(token);
        if (statusCode != HederaResponseCodes.SUCCESS) {
            revert("Token type appraisal failed!");
        }
        return tokenType;
    }

    function getExpiryInfoForToken(address token) external returns (IHederaTokenService.Expiry memory expiry)
    {
        (int256 responseCode,IHederaTokenService.Expiry memory retrievedExpiry) = HederaTokenService.getTokenExpiryInfo(token);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
        expiry = retrievedExpiry;
    }

    function getTokenKeyPublic(address token, uint256 keyType) public returns (IHederaTokenService.KeyValue memory)
    {
        (int256 responseCode,IHederaTokenService.KeyValue memory key) = HederaTokenService.getTokenKey(token, keyType);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
        return key;
    }
}

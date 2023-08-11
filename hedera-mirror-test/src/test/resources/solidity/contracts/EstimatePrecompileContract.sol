// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;
pragma experimental ABIEncoderV2;


import "./HederaTokenService.sol";
import "./HederaResponseCodes.sol";
import "./ExpiryHelper.sol";
import "./KeyHelper.sol";

contract EstimatePrecompileContract is HederaTokenService, ExpiryHelper, KeyHelper {

    string name = "tokenName";
    string symbol = "TKY";
    string memo = "memo";
    int64 initialTotalSupply = 1000;
    int64 maxSupply = 1000;
    int32 decimals = 8;
    bool freezeDefaultStatus = false;

    // Helper function to handle the common logic
    function handleResponseCode(int responseCode) internal pure {
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    // associate & dissociate
    function associateTokenExternal(address account, address token) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.associateToken(account, token);
        handleResponseCode(responseCode);
    }

    function nestedAssociateTokenExternal(address account, address token) external
    returns (int responseCode)
    {
        HederaTokenService.associateToken(account, token);
        responseCode = HederaTokenService.associateToken(account, token);
        handleResponseCode(responseCode);
    }

    function dissociateAndAssociateTokenExternal(address account, address token) external
    returns (int responseCode)
    {
        HederaTokenService.dissociateToken(account, token);
        responseCode = HederaTokenService.associateToken(account, token);
        handleResponseCode(responseCode);
    }

    function dissociateTokenExternal(address account, address token) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.dissociateToken(account, token);
        handleResponseCode(responseCode);
    }
    //associate & dissociate - many
    function associateTokensExternal(address account, address[] memory tokens) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.associateTokens(account, tokens);
        handleResponseCode(responseCode);
    }

    function dissociateTokensExternal(address account, address[] memory tokens) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.dissociateTokens(account, tokens);
        handleResponseCode(responseCode);
    }

    //approve
    function approveExternal(address token, address spender, uint256 amount) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.approve(token, spender, amount);
        handleResponseCode(responseCode);
    }

    function approveNFTExternal(address token, address approved, uint256 serialNumber) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.approveNFT(token, approved, serialNumber);
        handleResponseCode(responseCode);
    }

    //transfer
    function transferFromExternal(address token, address from, address to, uint256 amount) external
    returns (int responseCode)
    {
        responseCode = this.transferFrom(token, from, to, amount);
        handleResponseCode(responseCode);
    }

    function transferFromNFTExternal(address token, address from, address to, uint256 serialNumber) external
    returns (int responseCode)
    {
        responseCode = this.transferFromNFT(token, from, to, serialNumber);
        handleResponseCode(responseCode);
    }

    function transferTokenExternal(address token, address sender, address receiver, int64 amount) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.transferToken(token, sender, receiver, amount);
        handleResponseCode(responseCode);
    }

    function transferNFTExternal(address token, address sender, address receiver, int64 serialNumber) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.transferNFT(token, sender, receiver, serialNumber);
        handleResponseCode(responseCode);
    }

    //transfer-many
    function transferTokensExternal(address token, address[] memory accountIds, int64[] memory amounts) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.transferTokens(token, accountIds, amounts);
        handleResponseCode(responseCode);
    }

    function transferNFTsExternal(address token, address[] memory sender, address[] memory receiver, int64[] memory serialNumber) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.transferNFTs(token, sender, receiver, serialNumber);
        handleResponseCode(responseCode);
    }

    function cryptoTransferExternal(IHederaTokenService.TransferList memory transferList, IHederaTokenService.TokenTransferList[] memory tokenTransfers) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.cryptoTransfer(transferList, tokenTransfers);
        handleResponseCode(responseCode);
    }

    function mintTokenExternal(address token, int64 amount, bytes[] memory metadata) external
    returns (int responseCode, int64 newTotalSupply, int64[] memory serialNumbers)
    {
        (responseCode, newTotalSupply, serialNumbers) = HederaTokenService.mintToken(token, amount, metadata);
        handleResponseCode(responseCode);
    }

    function burnTokenExternal(address token, int64 amount, int64[] memory serialNumbers) external
    returns (int responseCode, int64 newTotalSupply)
    {
        (responseCode, newTotalSupply) = HederaTokenService.burnToken(token, amount, serialNumbers);
        handleResponseCode(responseCode);
    }

    //create operations
    function createFungibleTokenPublic(address treasury) public payable {
        IHederaTokenService.TokenKey[] memory keys = new IHederaTokenService.TokenKey[](5);
        keys[0] = getSingleKey(KeyType.ADMIN, KeyType.PAUSE, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));
        keys[1] = getSingleKey(KeyType.KYC, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));
        keys[2] = getSingleKey(KeyType.FREEZE, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));
        keys[3] = getSingleKey(KeyType.WIPE, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));
        keys[4] = getSingleKey(KeyType.SUPPLY, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));

        IHederaTokenService.Expiry memory expiry = IHederaTokenService.Expiry(
            0, treasury, 8000000
        );

        IHederaTokenService.HederaToken memory token = IHederaTokenService.HederaToken(
            name, symbol, treasury, memo, true, maxSupply, freezeDefaultStatus, keys, expiry
        );

        (int responseCode, address tokenAddress) =
                            HederaTokenService.createFungibleToken(token, initialTotalSupply, decimals);

        handleResponseCode(responseCode);
    }

    function createNonFungibleTokenPublic(address treasury) public payable {
        IHederaTokenService.TokenKey[] memory keys = new IHederaTokenService.TokenKey[](5);
        keys[0] = getSingleKey(KeyType.ADMIN, KeyType.PAUSE, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));
        keys[1] = getSingleKey(KeyType.KYC, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));
        keys[2] = getSingleKey(KeyType.FREEZE, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));
        keys[3] = getSingleKey(KeyType.SUPPLY, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));
        keys[4] = getSingleKey(KeyType.WIPE, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));

        IHederaTokenService.Expiry memory expiry = IHederaTokenService.Expiry(
            0, treasury, 8000000
        );

        IHederaTokenService.HederaToken memory token = IHederaTokenService.HederaToken(
            name, symbol, treasury, memo, true, maxSupply, freezeDefaultStatus, keys, expiry
        );

        (int responseCode, address tokenAddress) =
                            HederaTokenService.createNonFungibleToken(token);

        handleResponseCode(responseCode);
    }

    function createFungibleTokenWithCustomFeesPublic(address treasury, address fixedFeeTokenAddress) public payable {
        IHederaTokenService.TokenKey[] memory keys = new IHederaTokenService.TokenKey[](1);
        keys[0] = getSingleKey(KeyType.ADMIN, KeyType.ADMIN, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));

        IHederaTokenService.Expiry memory expiry = IHederaTokenService.Expiry(
            0, treasury, 8000000
        );

        IHederaTokenService.HederaToken memory token = IHederaTokenService.HederaToken(
            name, symbol, treasury, memo, true, maxSupply, false, keys, expiry
        );

        IHederaTokenService.FixedFee[] memory fixedFees = new IHederaTokenService.FixedFee[](1);
        fixedFees[0] = IHederaTokenService.FixedFee(1, fixedFeeTokenAddress, false, false, treasury);

        IHederaTokenService.FractionalFee[] memory fractionalFees = new IHederaTokenService.FractionalFee[](1);
        fractionalFees[0] = IHederaTokenService.FractionalFee(4, 5, 10, 30, false, treasury);

        (int responseCode, address tokenAddress) =
                            HederaTokenService.createFungibleTokenWithCustomFees(token, initialTotalSupply, decimals, fixedFees, fractionalFees);

        handleResponseCode(responseCode);
    }

    function createNonFungibleTokenWithCustomFeesPublic(address treasury, address fixedFeeTokenAddress) public payable {
        IHederaTokenService.TokenKey[] memory keys = new IHederaTokenService.TokenKey[](5);
        keys[0] = getSingleKey(KeyType.ADMIN, KeyType.PAUSE, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));
        keys[1] = getSingleKey(KeyType.KYC, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));
        keys[2] = getSingleKey(KeyType.FREEZE, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));
        keys[3] = getSingleKey(KeyType.SUPPLY, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));
        keys[4] = getSingleKey(KeyType.WIPE, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));

        IHederaTokenService.Expiry memory expiry = IHederaTokenService.Expiry(
            0, treasury, 8000000
        );

        IHederaTokenService.HederaToken memory token = IHederaTokenService.HederaToken(
            name, symbol, treasury, memo, true, maxSupply, freezeDefaultStatus, keys, expiry
        );

        IHederaTokenService.FixedFee[] memory fixedFees = new IHederaTokenService.FixedFee[](1);
        fixedFees[0] = IHederaTokenService.FixedFee(1, fixedFeeTokenAddress, false, false, treasury);

        IHederaTokenService.RoyaltyFee[] memory royaltyFees = new IHederaTokenService.RoyaltyFee[](1);
        royaltyFees[0] = IHederaTokenService.RoyaltyFee(4, 5, 10, fixedFeeTokenAddress, false, treasury);

        (int responseCode, address tokenAddress) =
                            HederaTokenService.createNonFungibleTokenWithCustomFees(token, fixedFees, royaltyFees);

        handleResponseCode(responseCode);
    }

    function wipeTokenAccountExternal(address token, address account, int64 amount) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.wipeTokenAccount(token, account, amount);
        handleResponseCode(responseCode);
    }

    function wipeTokenAccountNFTExternal(address token, address account, int64[] memory serialNumbers) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.wipeTokenAccountNFT(token, account, serialNumbers);
        handleResponseCode(responseCode);
    }

    function setApprovalForAllExternal(address token, address account, bool approved) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.setApprovalForAll(token, account, approved);
        handleResponseCode(responseCode);
    }

    function grantTokenKycExternal(address token, address account) external
    returns (int64 responseCode)
    {
        responseCode = HederaTokenService.grantTokenKyc(token, account);
        handleResponseCode(responseCode);
    }

    function revokeTokenKycExternal(address token, address account) external
    returns (int64 responseCode)
    {
        responseCode = HederaTokenService.revokeTokenKyc(token, account);
        handleResponseCode(responseCode);
    }

    function nestedGrantAndRevokeTokenKYCExternal(address token, address account) external
    returns (int64 responseCode)
    {
        HederaTokenService.grantTokenKyc(token, account);
        responseCode = HederaTokenService.revokeTokenKyc(token, account);
        handleResponseCode(responseCode);
    }

    function freezeTokenExternal(address token, address account) external
    returns (int64 responseCode)
    {
        responseCode = HederaTokenService.freezeToken(token, account);
        handleResponseCode(responseCode);
    }

    function unfreezeTokenExternal(address token, address account) external
    returns (int64 responseCode)
    {
        responseCode = HederaTokenService.unfreezeToken(token, account);
        handleResponseCode(responseCode);
    }

    function nestedFreezeUnfreezeTokenExternal(address token, address account) external
    returns (int64 responseCode)
    {
        HederaTokenService.freezeToken(token, account);
        responseCode = HederaTokenService.unfreezeToken(token, account);
        handleResponseCode(responseCode);
    }

    function freezeTokenTwiceExternal(address token, address account) external
    returns (int64 responseCode)
    {
        HederaTokenService.freezeToken(token, account);
        responseCode = HederaTokenService.freezeToken(token, account);
        handleResponseCode(responseCode);
    }

    function deleteTokenExternal(address token) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.deleteToken(token);
        handleResponseCode(responseCode);
    }

    function deleteTokenTwiceExternal(address token) external
    returns (int responseCode)
    {
        HederaTokenService.deleteToken(token);
        responseCode = HederaTokenService.deleteToken(token);
        handleResponseCode(responseCode);
    }

    function pauseTokenExternal(address token) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.pauseToken(token);
        handleResponseCode(responseCode);
    }

    function unpauseTokenExternal(address token) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.unpauseToken(token);
        handleResponseCode(responseCode);
    }

    function nestedPauseUnpauseTokenExternal(address token) external
    returns (int responseCode)
    {
        HederaTokenService.pauseToken(token);
        responseCode = HederaTokenService.unpauseToken(token);
        handleResponseCode(responseCode);
    }

    function updateTokenExpiryInfoExternal(address token, address treasury) external
    returns (int responseCode)
    {
        IHederaTokenService.Expiry memory expiry = IHederaTokenService.Expiry(
            0, treasury, 9000
        );
        responseCode = HederaTokenService.updateTokenExpiryInfo(token, expiry);
        handleResponseCode(responseCode);
    }

    function updateTokenInfoExternal(address token, address treasury) external
    returns (int responseCode)
    {
        IHederaTokenService.TokenKey[] memory keys = new IHederaTokenService.TokenKey[](5);
        keys[0] = getSingleKey(KeyType.ADMIN, KeyType.PAUSE, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));
        keys[1] = getSingleKey(KeyType.KYC, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));
        keys[2] = getSingleKey(KeyType.FREEZE, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));
        keys[3] = getSingleKey(KeyType.SUPPLY, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));
        keys[4] = getSingleKey(KeyType.WIPE, KeyValueType.INHERIT_ACCOUNT_KEY, bytes(""));

        IHederaTokenService.Expiry memory expiry = IHederaTokenService.Expiry(
            0, treasury, 9000
        );

        IHederaTokenService.HederaToken memory tokenInfo = IHederaTokenService.HederaToken(
            name, symbol, treasury, memo, true, maxSupply, freezeDefaultStatus, keys, expiry
        );

        responseCode = HederaTokenService.updateTokenInfo(token, tokenInfo);
        handleResponseCode(responseCode);
    }

    function updateTokenKeysExternal(address token) external
    returns (int responseCode)
    {
        IHederaTokenService.TokenKey[] memory keys = new IHederaTokenService.TokenKey[](4);
        keys[0] = getSingleKey(KeyType.KYC, KeyValueType.SECP256K1, abi.encodePacked(hex"02e35698a0273a8c6509ae4716c26a52eebca73e5de2c6677b189ef40f6fcd1fed"));
        keys[1] = getSingleKey(KeyType.FREEZE, KeyValueType.SECP256K1, abi.encodePacked(hex"02e35698a0273a8c6509ae4716c26a52eebca73e5de2c6677b189ef40f6fcd1fed"));
        keys[2] = getSingleKey(KeyType.SUPPLY, KeyValueType.SECP256K1, abi.encodePacked(hex"02e35698a0273a8c6509ae4716c26a52eebca73e5de2c6677b189ef40f6fcd1fed"));
        keys[3] = getSingleKey(KeyType.WIPE, KeyValueType.SECP256K1, abi.encodePacked(hex"02e35698a0273a8c6509ae4716c26a52eebca73e5de2c6677b189ef40f6fcd1fed"));

        responseCode = HederaTokenService.updateTokenKeys(token, keys);
        handleResponseCode(responseCode);
    }

    function getTokenExpiryInfoExternal(address token) external
    returns(int responseCode)
    {
        (responseCode, ) = HederaTokenService.getTokenExpiryInfo(token);
        handleResponseCode(responseCode);
    }

    function isTokenExternal(address token) external
    returns(int responseCode)
    {
        (responseCode, ) = HederaTokenService.isToken(token);
        handleResponseCode(responseCode);
    }

    function getTokenKeyExternal(address token, uint keyType) external
    returns(int responseCode)
    {
        (responseCode, ) = HederaTokenService.getTokenKey(token, keyType);
        handleResponseCode(responseCode);
    }

    function allowanceExternal(address token, address owner, address spender) external
    returns(int responseCode)
    {
        (responseCode, ) = HederaTokenService.allowance(token, owner, spender);
        handleResponseCode(responseCode);
    }

    function getApprovedExternal(address token, uint256 serialNumber) external
    returns(int responseCode)
    {
        (responseCode, ) = HederaTokenService.getApproved(token, serialNumber);
        handleResponseCode(responseCode);
    }

    function isApprovedForAllExternal(address token, address owner, address operator) external
    returns(int responseCode)
    {
        (responseCode, ) = HederaTokenService.isApprovedForAll(token, owner, operator);
        handleResponseCode(responseCode);
    }
}

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

    function cryptoTransferExternal(IHederaTokenService.TransferList memory transferList, IHederaTokenService.TokenTransferList[] memory tokenTransfers) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.cryptoTransfer(transferList, tokenTransfers);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert("Crypto transfer failed");
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

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }
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

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }
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

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }
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

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
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

    function setApprovalForAllExternal(address token, address account, bool approved) external
    returns (int responseCode)
    {
        responseCode = HederaTokenService.setApprovalForAll(token, account, approved);
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

    function nestedGrantAndRevokeTokenKYCExternal(address token, address account) external
    returns (int64 responseCode)
    {
        HederaTokenService.grantTokenKyc(token, account);
        responseCode = HederaTokenService.revokeTokenKyc(token, account);
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

    function nestedFreezeUnfreezeTokenExternal(address token, address account) external
    returns (int64 responseCode)
    {
        HederaTokenService.freezeToken(token, account);
        responseCode = HederaTokenService.unfreezeToken(token, account);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function freezeTokenTwiceExternal(address token, address account) external
    returns (int64 responseCode)
    {
        HederaTokenService.freezeToken(token, account);
        responseCode = HederaTokenService.freezeToken(token, account);
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

    function deleteTokenTwiceExternal(address token) external
    returns (int responseCode)
    {
        HederaTokenService.deleteToken(token);
        responseCode = HederaTokenService.deleteToken(token);
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

    function nestedPauseUnpauseTokenExternal(address token) external
    returns (int responseCode)
    {
        HederaTokenService.pauseToken(token);
        responseCode = HederaTokenService.unpauseToken(token);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function updateTokenExpiryInfoExternal(address token, address treasury) external
    returns (int responseCode)
    {
        IHederaTokenService.Expiry memory expiry = IHederaTokenService.Expiry(
            0, treasury, 9000
        );
        responseCode = HederaTokenService.updateTokenExpiryInfo(token, expiry);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
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
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
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
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function getTokenExpiryInfoExternal(address token) external
    returns(int responseCode)
    {
        (responseCode, ) = HederaTokenService.getTokenExpiryInfo(token);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function isTokenExternal(address token) external
    returns(int responseCode)
    {
        (responseCode, ) = HederaTokenService.isToken(token);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function getTokenKeyExternal(address token, uint keyType) external
    returns(int responseCode)
    {
        (responseCode, ) = HederaTokenService.getTokenKey(token, keyType);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function allowanceExternal(address token, address owner, address spender) external
    returns(int responseCode)
    {
        (responseCode, ) = HederaTokenService.allowance(token, owner, spender);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function getApprovedExternal(address token, uint256 serialNumber) external
    returns(int responseCode)
    {
        (responseCode, ) = HederaTokenService.getApproved(token, serialNumber);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function isApprovedForAllExternal(address token, address owner, address operator) external
    returns(int responseCode)
    {
        (responseCode, ) = HederaTokenService.isApprovedForAll(token, owner, operator);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }
}

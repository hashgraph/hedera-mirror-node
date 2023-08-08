
pragma solidity ^0.8.0;

import "./HederaTokenService.sol";
import "./HederaResponseCodes.sol";
import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/token/ERC721/IERC721.sol";


contract NestedEthCalls is HederaTokenService {


    // Mint fungible/non-fungible token + get token info total supply+ get balance of the treasury
    function mintTokenGetTotalSupplyAndBalanceOfTreasury(address token, int64 amount, bytes[] memory metadata, address treasury) external
    {
        uint256 balanceBeforeMint = 0;
        if(amount > 0 && metadata.length == 0) {
            balanceBeforeMint = IERC20(token).balanceOf(treasury);
        } else {
            balanceBeforeMint = IERC721(token).balanceOf(treasury);
        }

        (int responseCode, IHederaTokenService.TokenInfo memory retrievedTokenInfo) = HederaTokenService.getTokenInfo(token);
        if (responseCode != HederaResponseCodes.SUCCESS) revert();
        int totalSupplyBeforeMint = retrievedTokenInfo.totalSupply;

        (responseCode,,) = HederaTokenService.mintToken(token, amount, metadata);
        if (responseCode != HederaResponseCodes.SUCCESS) revert();

        (responseCode, retrievedTokenInfo) = HederaTokenService.getTokenInfo(token);

        if (responseCode != HederaResponseCodes.SUCCESS) revert ();

        int totalSupplyAfterMint = retrievedTokenInfo.totalSupply;
        if(amount > 0 && metadata.length == 0) {
            if(totalSupplyBeforeMint + amount != totalSupplyAfterMint) revert();
        } else {
            if(totalSupplyBeforeMint + int256(metadata.length) != totalSupplyAfterMint) revert();
        }

        if(amount > 0 && metadata.length == 0) {
            uint256 balanceAfterMint = IERC20(token).balanceOf(treasury);
            if(balanceAfterMint != balanceBeforeMint + uint256(int256(amount))) revert();
        } else {
            uint256 balanceAfterMint  = IERC721(token).balanceOf(treasury);
            if(balanceAfterMint != balanceBeforeMint + uint256(int256(metadata.length))) revert();
        }
    }

    // Burn fungible/non-fungible token + get token info total supply + get balance of the treasury
    function burnTokenGetTotalSupplyAndBalanceOfTreasury(address token, int64 amount, int64[] memory serialNumbers, address treasury) external
    {
        uint256 balanceBeforeBurn = 0;
        if(amount > 0 && serialNumbers.length == 0) {
            balanceBeforeBurn = IERC20(token).balanceOf(treasury);
        } else {
            balanceBeforeBurn = IERC721(token).balanceOf(treasury);
        }

        (int responseCode, IHederaTokenService.TokenInfo memory retrievedTokenInfo) = HederaTokenService.getTokenInfo(token);
        if (responseCode != HederaResponseCodes.SUCCESS) revert();
        int totalSupplyBeforeBurn = retrievedTokenInfo.totalSupply;

        (responseCode,) = HederaTokenService.burnToken(token, amount, serialNumbers);
        if (responseCode != HederaResponseCodes.SUCCESS) revert();

        (responseCode, retrievedTokenInfo) = HederaTokenService.getTokenInfo(token);

        if (responseCode != HederaResponseCodes.SUCCESS) revert ();

        int totalSupplyAfterBurn = retrievedTokenInfo.totalSupply;
        if(amount > 0 && serialNumbers.length == 0) {
            if(totalSupplyBeforeBurn - amount != totalSupplyAfterBurn) revert();
        } else {
            if(totalSupplyBeforeBurn - int256(serialNumbers.length) != totalSupplyAfterBurn) revert();
        }

        if(amount > 0 && serialNumbers.length == 0) {
            uint256 balanceAfterBurn = IERC20(token).balanceOf(treasury);
            if(balanceAfterBurn != balanceBeforeBurn - uint256(int256(amount))) revert();
        } else {
            uint256 balanceAfterBurn  = IERC721(token).balanceOf(treasury);
            if(balanceAfterBurn != balanceBeforeBurn - uint256(int256(serialNumbers.length))) revert();
        }
    }

    // Wipe + get token info total supply + get balance of the account which balance was wiped
    function wipeTokenGetTotalSupplyAndBalanceOfTreasury(address token, int64 amount, int64[] memory serialNumbers, address treasury) external
    {
        uint256 balanceBeforeWipe = 0;
        if(amount > 0 && serialNumbers.length == 0) {
            balanceBeforeWipe = IERC20(token).balanceOf(treasury);
        } else {
            balanceBeforeWipe = IERC721(token).balanceOf(treasury);
        }

        (int responseCode, IHederaTokenService.TokenInfo memory retrievedTokenInfo) = HederaTokenService.getTokenInfo(token);
        if (responseCode != HederaResponseCodes.SUCCESS) revert();

        int totalSupplyBeforeWipe = retrievedTokenInfo.totalSupply;


        if(amount > 0 && serialNumbers.length == 0) {
            responseCode = HederaTokenService.wipeTokenAccount(token, treasury, amount);

        } else {
            responseCode = HederaTokenService.wipeTokenAccountNFT(token, treasury, serialNumbers);
        }

        if (responseCode != HederaResponseCodes.SUCCESS) revert();

        (responseCode, retrievedTokenInfo) = HederaTokenService.getTokenInfo(token);

        if (responseCode != HederaResponseCodes.SUCCESS) revert();

        int totalSupplyAfterBurn = retrievedTokenInfo.totalSupply;
        if(amount > 0 && serialNumbers.length == 0) {
            if(totalSupplyBeforeWipe - amount != totalSupplyAfterBurn) revert();
        } else {
            if(totalSupplyBeforeWipe - int256(serialNumbers.length) != totalSupplyAfterBurn) revert();
        }

        if(amount > 0 && serialNumbers.length == 0) {
            uint256 balanceAfterWipe = IERC20(token).balanceOf(treasury);
            if(balanceAfterWipe != balanceBeforeWipe - uint256(int256(amount))) revert();
        } else {
            uint256 balanceAfterWipe  = IERC721(token).balanceOf(treasury);
            if(balanceAfterWipe != balanceBeforeWipe - uint256(int256(serialNumbers.length))) revert();
        }
    }

    // Pause fungible/non-fungible token + get token info pause status + unpause + get token info pause status
    function pauseTokenGetPauseStatusUnpauseGetPauseStatus(address token) external {
        int responseCode = HederaTokenService.pauseToken(token);
        if (responseCode != HederaResponseCodes.SUCCESS) revert();

        (int response, IHederaTokenService.TokenInfo memory retrievedTokenInfo) = HederaTokenService.getTokenInfo(token);
        if (response != HederaResponseCodes.SUCCESS) revert();
        if(!retrievedTokenInfo.pauseStatus) revert();

        responseCode = HederaTokenService.unpauseToken(token);
        if (responseCode != HederaResponseCodes.SUCCESS) revert();
        (response, retrievedTokenInfo) = HederaTokenService.getTokenInfo(token);
        if(response != HederaResponseCodes.SUCCESS) revert();
        if(retrievedTokenInfo.pauseStatus) revert();
    }

    // Freeze fungible/non-fungible token + get token info freeze status + unfreeze + get token info freeze status
    function freezeTokenGetPauseStatusUnpauseGetPauseStatus(address token, address account) external {
        int responseCode = HederaTokenService.freezeToken(token, account);
        if (responseCode != HederaResponseCodes.SUCCESS) revert();

        (int response, bool isFrozen) = HederaTokenService.isFrozen(token, account);
        if (response != HederaResponseCodes.SUCCESS) revert();
        if(!isFrozen) revert();

        responseCode = HederaTokenService.unfreezeToken(token, account);
        if (responseCode != HederaResponseCodes.SUCCESS) revert();

        (response, isFrozen) = HederaTokenService.isFrozen(token, account);
        if (response != HederaResponseCodes.SUCCESS) revert();
        if(isFrozen) revert();
    }

    // Associate fungible/non-fungible token transfer (should pass) + dissociate + transfer (should fail)
    function associateTokenDissociateFailTransfer(address token, address from, address to, uint256 amount, uint256 serialNumber) external {
        address[] memory tokens = new address[](1);
        tokens[0] = token;
        int responseCode = HederaTokenService.associateTokens(from, tokens);
        if (responseCode != HederaResponseCodes.SUCCESS) revert();

        responseCode = HederaTokenService.dissociateTokens(from, tokens);
        if (responseCode != HederaResponseCodes.SUCCESS) revert();

        if(amount > 0 && serialNumber == 0) {
            try IERC20(token).transferFrom(from, to, amount) {
            } catch {
                revert("IERC20 transfer failed");
            }
        } else {
            try IERC721(token).transferFrom(from, to, amount) {
            } catch {
                revert("IERC20 transfer failed");
            }
        }
    }

    // Approve fungible/non-fungible token + allowance
    function approveTokenGetAllowance(address token, address spender, uint256 amount, uint256 serialNumber) external {
        if(amount > 0 && serialNumber == 0) {
            int responseCode = HederaTokenService.approve(token, spender, amount);
            if (responseCode != HederaResponseCodes.SUCCESS) revert();
            if(IERC20(token).allowance(address(this), spender) != amount) revert();
        } else {
            int responseCode = HederaTokenService.approveNFT(token, spender, serialNumber);
            if (responseCode != HederaResponseCodes.SUCCESS) revert();
            if(IERC721(token).getApproved(serialNumber) != spender) revert();
        }
    }

    // Approve fungible/non-fungible token + transferFrom with spender + allowance + balance
    function approveTokenTransferFromGetAllowanceGetBalance(address token, address spender, uint256 amount, uint256 serialNumber) external {
        if(amount > 0 && serialNumber == 0) {
            int responseCode = HederaTokenService.approve(token, spender, amount);
            if (responseCode != HederaResponseCodes.SUCCESS) revert();
            uint256 balanceBeforeTransfer = IERC20(token).balanceOf(spender);
            if(IERC20(token).allowance(address(this), spender) != amount) revert();
            IERC20(token).transferFrom(address(this), spender, amount);
            if(IERC20(token).balanceOf(spender) != balanceBeforeTransfer + amount) revert();
        } else {
            HederaTokenService.transferNFT(token, IERC721(token).ownerOf(serialNumber), address(this), int64(int256(serialNumber)));
            int responseCode = HederaTokenService.approveNFT(token, spender, serialNumber);
            if (responseCode != HederaResponseCodes.SUCCESS) revert();
            if(IERC721(token).getApproved(serialNumber) != spender) revert();
            IERC721(token).transferFrom(address(this), spender, serialNumber);
            if(IERC721(token).ownerOf(serialNumber) != spender) revert();
        }
    }
}

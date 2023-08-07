
pragma solidity ^0.8.0;

import "./HederaTokenService.sol";
import "./HederaResponseCodes.sol";
import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/token/ERC721/IERC721.sol";


contract NestedEthCalls is HederaTokenService {


    // Mint fungible/non-fungible token + get token info total supply+ get balance of the treasury
    function mintTokenGetTotalSupplyAndBalanceOfTreasury(address token, int64 amount, bytes[] memory metadata, address treasury, bool isErc20) external
    {
        uint256 balanceBeforeMint = 0;
        if(isErc20) {
            balanceBeforeMint = IERC20(token).balanceOf(treasury);
        } else {
            balanceBeforeMint = IERC721(token).balanceOf(treasury);
        }

        (int responseCode, IHederaTokenService.TokenInfo memory retrievedTokenInfo) = HederaTokenService.getTokenInfo(token);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
        int totalSupplyBeforeMint = retrievedTokenInfo.totalSupply;

        (int response,,) = HederaTokenService.mintToken(token, amount, metadata);
        if (response != HederaResponseCodes.SUCCESS) {
            revert();
        }

        (responseCode, retrievedTokenInfo) = HederaTokenService.getTokenInfo(token);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }

        int totalSupplyAfterMint = retrievedTokenInfo.totalSupply;
        if(totalSupplyBeforeMint + amount != totalSupplyAfterMint) {
            revert();
        }

        uint256 balanceAfterMint = 0;
        if(isErc20) {
            balanceAfterMint = IERC20(token).balanceOf(treasury);
        } else {
            balanceAfterMint = IERC721(token).balanceOf(treasury);
        }

        if(balanceAfterMint != balanceBeforeMint + uint256(int256(amount))) {
            revert();
        }
    }


    function burnTokenGetTotalSupplyAndBalanceOfTreasury(address token, int64 amount, int64[] memory serialNumbers, address treasury, bool isErc20) external
    {
        uint256 balanceBeforeMint = 0;
        if(isErc20) {
            balanceBeforeMint = IERC20(token).balanceOf(treasury);
        } else {
            balanceBeforeMint = IERC721(token).balanceOf(treasury);
        }
        (int responseCode, IHederaTokenService.TokenInfo memory retrievedTokenInfo) = HederaTokenService.getTokenInfo(token);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
        int totalSupplyBeforeMint = retrievedTokenInfo.totalSupply;

        (int response,) = HederaTokenService.burnToken(token, amount, serialNumbers);
        if (response != HederaResponseCodes.SUCCESS) {
            revert();
        }

        (responseCode, retrievedTokenInfo) = HederaTokenService.getTokenInfo(token);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }
        int totalSupplyAfterMint = retrievedTokenInfo.totalSupply;
        if(totalSupplyBeforeMint + amount != totalSupplyAfterMint) {
            revert();
        }

        uint256 balanceAfterMint = 0;
        if(isErc20) {
            balanceAfterMint = IERC20(token).balanceOf(treasury);
        } else {
            balanceAfterMint = IERC721(token).balanceOf(treasury);
        }

        if(balanceAfterMint != balanceBeforeMint + uint256(int256(amount))) {
            revert();
        }
    }
}

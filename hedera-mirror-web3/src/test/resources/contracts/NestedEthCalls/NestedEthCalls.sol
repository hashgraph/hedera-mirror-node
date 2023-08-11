pragma solidity ^0.8.0;

import "./HederaTokenService.sol";
import "./HederaResponseCodes.sol";
import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/token/ERC721/IERC721.sol";

contract NestedEthCalls is HederaTokenService {

    // Approve fungible/non-fungible token + transfer with spender + allowance + balance
    function approveTokenTransferGetAllowanceGetBalance(address token, address spender, uint256 amount, uint256 serialNumber) external {
        if(amount > 0 && serialNumber == 0) {
            int responseCode = HederaTokenService.approve(token, spender, amount);
            if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to approve token for transfer");
            uint256 balanceBeforeTransfer = IERC20(token).balanceOf(spender);
            if(IERC20(token).allowance(address(this), spender) != amount) revert("Allowance mismatch before transfer");
            responseCode = HederaTokenService.transferToken(token, address(this), spender, int64(uint64(amount)));
            if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to transfer fungible token");
            if(IERC20(token).balanceOf(spender) != balanceBeforeTransfer + amount) revert("Balance mismatch after transfer");
            if(IERC20(token).allowance(address(this), spender) != 0) revert("Fungible token allowance mismatch after transfer");
        } else {
            HederaTokenService.transferNFT(token, IERC721(token).ownerOf(serialNumber), address(this), int64(int256(serialNumber)));
            int responseCode = HederaTokenService.approveNFT(token, spender, serialNumber);
            if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to approve NFT for transfer");
            responseCode = HederaTokenService.transferToken(token, address(this), spender, int64(uint64(serialNumber)));
            if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to transfer NFT");
            if(IERC721(token).ownerOf(serialNumber) != spender) revert("NFT ownership mismatch after transfer");
            if(IERC721(token).getApproved(serialNumber) == spender) revert("NFT allowance mismatch after transfer");
        }
    }

    // Approve fungible/non-fungible token + cryptoTransfer with spender + allowance + balance
    function approveTokenCryptoTransferGetAllowanceGetBalance(address token, address spender, uint256 amount, uint256 serialNumber, IHederaTokenService.TransferList memory transferList, IHederaTokenService.TokenTransferList[] memory tokenTransfers) external {
        if(amount > 0 && serialNumber == 0) {
            int responseCode = HederaTokenService.approve(token, spender, amount);
            if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to approve token for transfer");
            uint256 balanceBeforeTransfer = IERC20(token).balanceOf(spender);
            if(IERC20(token).allowance(address(this), spender) != amount) revert("Allowance mismatch before transfer");
            responseCode = HederaTokenService.cryptoTransfer(transferList, tokenTransfers);
            if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to transfer fungible token");
            if(IERC20(token).balanceOf(spender) != balanceBeforeTransfer + amount) revert("Balance mismatch after transfer");
            if(IERC20(token).allowance(address(this), spender) != 0) revert("Fungible token allowance mismatch after transfer");
        } else {
            HederaTokenService.transferNFT(token, IERC721(token).ownerOf(serialNumber), address(this), int64(int256(serialNumber)));
            int responseCode = HederaTokenService.approveNFT(token, spender, serialNumber);
            if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to approve NFT for transfer");
            responseCode = HederaTokenService.cryptoTransfer(transferList, tokenTransfers);
            if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to transfer NFT");
            if(IERC721(token).ownerOf(serialNumber) != spender) revert("NFT ownership mismatch after transfer");
            if(IERC721(token).getApproved(serialNumber) == spender) revert("NFT allowance mismatch after transfer");
        }
    }

    // Approve for all an nft + transferFrom with spender + isApprovedForAll
    function approveForAllTokenTransferFromGetAllowance(address token, address spender, uint256 serialNumber) external {
        HederaTokenService.transferNFT(token, IERC721(token).ownerOf(serialNumber), address(this), int64(int256(serialNumber)));
        int responseCode = HederaTokenService.setApprovalForAll(token, spender, true);
        if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to approve NFT for transfer");
        if(IERC721(token).getApproved(serialNumber) != spender) revert("NFT approval mismatch before transfer");
        IERC721(token).transferFrom(address(this), spender, serialNumber);
        if(IERC721(token).ownerOf(serialNumber) != spender) revert("NFT ownership mismatch after transfer");
        (int response, bool isApproved) = HederaTokenService.isApprovedForAll(token, address(this), spender);
        if(response != HederaResponseCodes.SUCCESS) revert("Failed to approve NFT");
        if(isApproved) revert("NFT allowance mismatch after transfer");
    }

    // Approve for all an nft + transfer with spender + isApprovedForAll
    function approveForAllTokenTransferGetAllowance(address token, address spender, uint256 serialNumber) external {
        HederaTokenService.transferNFT(token, IERC721(token).ownerOf(serialNumber), address(this), int64(int256(serialNumber)));
        int responseCode = HederaTokenService.setApprovalForAll(token, spender, true);
        if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to approve NFT for transfer");
        if(IERC721(token).getApproved(serialNumber) != spender) revert("NFT approval mismatch before transfer");
        responseCode = HederaTokenService.transferToken(token, address(this), spender, int64(uint64(serialNumber)));
        if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to transfer NFT");
        (int response, bool isApproved) = HederaTokenService.isApprovedForAll(token, address(this), spender);
        if(response != HederaResponseCodes.SUCCESS) revert("Failed to approve NFT");
        if(isApproved) revert("NFT allowance mismatch after transfer");
    }

    // Approve for all an nft + cryptoTransfer with spender + isApprovedForAll
    function approveForAllTokenTransferGetAllowance(address token, address spender, uint256 serialNumber, IHederaTokenService.TransferList memory transferList, IHederaTokenService.TokenTransferList[] memory tokenTransfers) external {
        HederaTokenService.transferNFT(token, IERC721(token).ownerOf(serialNumber), address(this), int64(int256(serialNumber)));
        int responseCode = HederaTokenService.approveNFT(token, spender, serialNumber);
        if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to approve NFT for transfer");
        responseCode = HederaTokenService.cryptoTransfer(transferList, tokenTransfers);
        if (responseCode != HederaResponseCodes.SUCCESS) revert("Failed to transfer NFT");
        if(IERC721(token).ownerOf(serialNumber) != spender) revert("NFT ownership mismatch after transfer");
        if(IERC721(token).getApproved(serialNumber) == spender) revert("NFT allowance mismatch after transfer");
    }

    // TransferFrom an nft + allowance + balance
    function transferFromNFTGetAllowance(address token, address spender, uint256 serialNumber) external {
        try IERC721(token).transferFrom(IERC721(token).ownerOf(serialNumber), spender, serialNumber) {
        } catch {
            revert("IERC721: failed to transfer");
        }
    }

    // Transfer fungible/non-fungible token + allowance + balance
    function transferFromGetAllowanceGetBalance(address token, address spender, uint256 amount, uint256 serialNumber) external {
        if(amount > 0 && serialNumber == 0) {
            try IERC20(token).transferFrom(address(this), spender, serialNumber) {
            } catch {
                revert("IERC20: failed to transfer");
            }
        } else {
            try IERC721(token).transferFrom(IERC721(token).ownerOf(serialNumber), spender, serialNumber) {
            } catch {
                revert("IERC721: failed to transfer");
            }
        }
    }

    // CryptoTransfer fungible/non-fungible token + allowance + balance
    function cryptoTransferFromGetAllowanceGetBalance(address token, address spender, uint256 amount, uint256 serialNumber, IHederaTokenService.TransferList memory transferList, IHederaTokenService.TokenTransferList[] memory tokenTransfers) external {
        if(amount > 0 && serialNumber == 0) {
            try IERC20(token).transferFrom(address(this), spender, serialNumber) {
            } catch {
                revert("IERC20: failed to transfer");
            }
        } else {
            try IERC721(token).transferFrom(IERC721(token).ownerOf(serialNumber), spender, serialNumber) {
            } catch {
                revert("IERC721: failed to transfer");
            }
        }
    }
}

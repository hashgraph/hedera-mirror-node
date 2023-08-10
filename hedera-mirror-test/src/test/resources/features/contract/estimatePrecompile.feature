@contractbase @fullsuite @acceptance  @estimateprecompile @web3
Feature: EstimateGas Contract Base Coverage Feature

  Scenario Outline: Validate EstimateGas with precompile
    Given I create estimate precompile contract with 0 balance
    Given I create erc test contract with 0 balance
    Given I successfully create Precompile contract with 0 balance
    Given I successfully create and verify a fungible token for estimateGas precompile tests
    Given I successfully create and verify a non fungible token for estimateGas precompile tests
    Given I mint and verify a new nft
    And I set lower deviation at 5% and upper deviation at 20%
#    Then I call estimateGas with redirectForToken function
    Then I call estimateGas with associate function for fungible token
    Then I call estimateGas with associate function for NFT
    Then I call estimateGas with dissociate token function without association for fungible token
    Then I call estimateGas with dissociate token function without association for NFT
    Then I call estimateGas with nested associate function that executes it twice for fungible token
    Then I call estimateGas with nested associate function that executes it twice for NFT
    Then I call estimateGas with dissociate token function for fungible token
    Then I call estimateGas with dissociate token function for NFT
    Then I call estimateGas with dissociate and associate nested function for fungible token
    Then I call estimateGas with dissociate and associate nested function for NFT
    Then I call estimateGas with approve function without association
    Then I call estimateGas with approveNFT function without association
    Then I call estimateGas with setApprovalForAll function without association
    Then I associate contracts with the tokens and approve the all nft serials
    Then I call estimateGas with approve function
    Then I call estimateGas with approveNFT function
    Then I call estimateGas with ERC approve function
    Then I call estimateGas with setApprovalForAll function
    Then I call estimateGas with transferFrom function without approval
    Then I call estimateGas with ERC transferFrom function without approval
    Then I call estimateGas with transferFromNFT function
    Then I call estimateGas with transferFrom function
    Then I call estimateGas with ERC transferFrom function
    Then I call estimateGas with transferFrom function with more than the approved allowance
    Then I call estimateGas with ERC transferFrom function with more than the approved allowance
    Then I call estimateGas with transferFromNFT with invalid serial number
    Then I call estimateGas with transferToken function
    Then I call estimateGas with transferNFT function
    Then I call estimateGas with ERC transfer function
    Then I create 2 more fungible tokens
    Then I create 2 more NFTs
    Then I call estimateGas with associateTokens function for fungible tokens
    Then I call estimateGas with associateTokens function for NFTs
    Then I call estimateGas with dissociateTokens function for fungible tokens
    Then I call estimateGas with dissociateTokens function for NFTs
    Then I call estimateGas with transferTokens function
    Then I call estimateGas with transferNFTs function
    Then I call estimateGas with cryptoTransfer function for hbars
    Then I call estimateGas with cryptoTransfer function for nft
    Then I call estimateGas with cryptoTransfer function for fungible tokens
    Then I call estimateGas with mintToken function for fungible token
    Then I call estimateGas with mintToken function for NFT
    Then I call estimateGas with burnToken function for fungible token
    Then I call estimateGas with burnToken function for NFT
    Then I call estimateGas with CreateFungibleToken function
    Then I call estimateGas with CreateNFT function
#    Then I call estimateGas with CreateFungibleToken function with custom fees
#    Then I call estimateGas with CreateNFT function with custom fees
    Then I call estimateGas with WipeTokenAccount function
    Then I call estimateGas with WipeTokenAccount function with invalid amount
    Then I call estimateGas with WipeNFTAccount function
    Then I call estimateGas with WipeNFTAccount function with invalid serial number
    Then I call estimateGas with GrantKYC function for fungible token
    Then I call estimateGas with GrantKYC function for NFT
    Then I create fungible and non-fungible token without KYC status
    Then I call estimateGas with GrantKYC function for fungible token without KYC status
    Then I call estimateGas with GrantKYC function for NFT without KYC status
    Then I call estimateGas with RevokeTokenKYC function for fungible token
    Then I call estimateGas with RevokeTokenKYC function for NFT
    Then I call estimateGas with RevokeTokenKYC function on a token without KYC
    Then I call estimateGas with Grant and Revoke KYC nested function
    Then I call estimateGas with Freeze function for fungible token
    Then I call estimateGas with Freeze function for NFT
    Then I call estimateGas with Unfreeze function for fungible token
    Then I call estimateGas with Unfreeze function for NFT
    Then I call estimateGas with nested Freeze and Unfreeze function for fungible token
    Then I call estimateGas with nested Freeze and Unfreeze function for NFT
    Then I call estimateGas with delete function for Fungible token
    Then I call estimateGas with delete function for NFT
    Then I call estimateGas with delete function for invalid token address
    Then I call estimateGas with updateTokenExpiryInfo function
    Then I call estimateGas with updateTokenInfo function
    Then I call estimateGas with updateTokenKeys function
    Then I call estimateGas with pause function for fungible token
    Then I call estimateGas with pause function for NFT
    Then I call estimateGas with unpause function for fungible token
    Then I call estimateGas with unpause function for NFT
    Then I call estimateGas for nested pause and unpause function
    Then I call estimateGas for nested pause, unpause NFT function
    Then I call estimateGas with getTokenExpiryInfo function
    Then I call estimateGas with isToken function
    Then I call estimateGas with getTokenKey function for supply
    Then I call estimateGas with getTokenKey function for KYC
    Then I call estimateGas with getTokenKey function for freeze
    Then I call estimateGas with getTokenKey function for admin
    Then I call estimateGas with getTokenKey function for wipe
    Then I call estimateGas with getTokenKey function for fee
    Then I call estimateGas with getTokenKey function for pause
    Then I call estimateGas with allowance function for fungible token
    Then I call estimateGas with allowance function for NFT
    Then I call estimateGas with ERC allowance function for fungible token
    Then I call estimateGas with getApproved function for NFT
    Then I call estimateGas with ERC getApproved function for NFT
    Then I call estimateGas with isApprovedForAll function
    Then I call estimateGas with ERC isApprovedForAll function
    Then I call estimateGas with name function for fungible token
    Then I call estimateGas with name function for NFT
    Then I call estimateGas with symbol function for fungible token
    Then I call estimateGas with symbol function for NFT
    Then I call estimateGas with decimals function for fungible token
    Then I call estimateGas with totalSupply function for fungible token
    Then I call estimateGas with totalSupply function for fungible token
    Then I call estimateGas with decimals function for fungible token
    Then I call estimateGas with totalSupply function for fungible token
    Then I call estimateGas with totalSupply function for NFT
    Then I call estimateGas with balanceOf function for fungible token
    Then I call estimateGas with balanceOf function for NFT
    Then I call estimateGas with ownerOf function for NFT
    Then I call estimateGas with tokenURI function for NFT
    Then I call estimateGas with getFungibleTokenInfo function
    Then I call estimateGas with getNonFungibleTokenInfo function
    Then I call estimateGas with getTokenInfo function for fungible
    Then I call estimateGas with getTokenInfo function for NFT
    Then I call estimateGas with getTokenDefaultFreezeStatus function for fungible token
    Then I call estimateGas with getTokenDefaultFreezeStatus function for NFT
    Then I call estimateGas with getTokenDefaultKycStatus function for fungible token
    Then I call estimateGas with getTokenDefaultKycStatus function for NFT
    Then I call estimateGas with isKyc function for fungible token
    Then I call estimateGas with isKyc function for NFT
    Then I call estimateGas with isFrozen function for fungible token
    Then I call estimateGas with isFrozen function for NFT
    Then I call estimateGas with getTokenType function for fungible token
    Then I call estimateGas with getTokenType function for NFT


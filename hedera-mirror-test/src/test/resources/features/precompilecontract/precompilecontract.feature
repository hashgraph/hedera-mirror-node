@contractbase @fullsuite
Feature: Precompile Contract Base Coverage Feature

    @release @acceptance
    Scenario Outline: Validate Precompile Contract
        Given I successfully create and verify a precompile contract from contract bytes
        Given I successfully create and verify a fungible token for precompile contract tests
        Given I successfully create and verify a non fungible token for precompile contract tests
        Then I mint and verify a nft
        Then Check if fungible token is token
        Then Check if non fungible token is token
        Then Invalid account is token should return an error
        Then Valid account is token should return an error
        Then Verify fungible token isn't frozen
        Then Verify non fungible token isn't frozen
        Then Check if can freeze token
        Then Check if can unfreeze token
        Then Check if fungible token is kyc granted
        Then Check if non fungible token is kyc granted
        Then Get token default freeze of fungible token
        Then Get token default freeze of non fungible token
        Then Get token default kyc of fungible token
        Then Get token default kyc of non fungible token
        Then Get information for token of fungible token
        Then Get information for token of non fungible token
        Then Get information for fungible token
        Then Get information for non fungible token
        Then Get type for fungible token
        Then Get type for non fungible token
        Then Get expiry token info for fungible token
        Then Get expiry token info for non fungible token
        Then Get token key for fungible token
        Then Get token key for non fungible token
        Examples:
            | httpStatusCode |
            | 200            |

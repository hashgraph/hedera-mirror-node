@tokenbase @fullsuite
Feature: HTS Base Coverage Feature

    @critical @release @acceptance
    Scenario Outline: Validate Base Token Flow - Create, Associate, Fund
        Given I successfully create a new token
        Then the mirror node REST API should return status <httpStatusCode>
        When I associate a new recipient account with token
        Then the mirror node REST API should return status <httpStatusCode>
        Then I transfer <amount> tokens to recipient
        Then the mirror node REST API should return status <httpStatusCode> for token fund flow
        Examples:
            | amount | httpStatusCode |
            | 2350   | 200            |

    @release @acceptance
    Scenario Outline: Validate Freeze and KYC Flow - Create, Unfreeze, GrantKyc
        Given I successfully create a new token with freeze status <initialFreezeStatus> and kyc status <initialKycStatus>
        When I associate a new recipient account with token
        And I set new account freeze status to <newFreezeStatus>
        Then the mirror node REST API should return status <httpStatusCode>
        And I set new account kyc status to <newKycStatus>
        Then the mirror node REST API should return status <httpStatusCode>
        Examples:
            | initialFreezeStatus | initialKycStatus | newFreezeStatus | newKycStatus | httpStatusCode |
            | 1                   | 2                | 2               | 1            | 200            |

    @acceptance
    Scenario Outline: Validate Token Modification Flow - Create, Associate, Transfer, Update, Burn, Mint and Wipe
        Given I successfully create a new token
        Then the mirror node REST API should return status <httpStatusCode>
        When I associate a new recipient account with token
        And I transfer <amount> tokens to recipient
        Then the mirror node REST API should return status <httpStatusCode> for token fund flow
        Then I update the token
        And the mirror node REST API should confirm token update
        Then I burn <modifySupplyAmount> from the token
        And the mirror node REST API should return status <httpStatusCode>
        Then I mint <modifySupplyAmount> from the token
        And the mirror node REST API should return status <httpStatusCode>
        Then I wipe <modifySupplyAmount> from the token
        And the mirror node REST API should return status <httpStatusCode>
        Examples:
            | amount | httpStatusCode | modifySupplyAmount |
            | 2350   | 200            | 100                |

    @acceptance
    Scenario Outline: Validate Token ramp down Flow - Create, Associate, Dissociate, Delete
        Given I successfully create a new token
        When I associate a new recipient account with token
        And the mirror node REST API should return status <httpStatusCode>
        Then I dissociate the account from the token
        And the mirror node REST API should return status <httpStatusCode>
        Then I delete the token
        And the mirror node REST API should return status <httpStatusCode>
        Examples:
            | httpStatusCode |
            | 200            |

    @critical @release @acceptance @nft
    Scenario Outline: Validate Base NFT Flow - Create, Associate, Mint, Transfer
        Given I successfully create a new nft
        Then the mirror node REST API should return status <httpStatusCode>
        When I associate a new recipient account with token 0
        And the mirror node REST API should return status <httpStatusCode>
        Then I mint a serial number from the token
        And the mirror node nft transactions REST APIs should return status <httpStatusCode> for nft 0 serial number 0 fund flow
        Then I transfer serial number of token to recipient
        And the mirror node REST API should return status <httpStatusCode> for nft 0 serial number 0 fund flow
        Examples:
            | httpStatusCode |
            | 200            |

    @acceptance @nft @exhaustive
    Scenario Outline: Validate Full NFT Flow - Create, Associate, Mint, Transfer, Burn, Wipe, Delete
        Given I successfully create a new nft with supplyType <supplyType>
        Then the mirror node REST API should return status <httpStatusCode>
        When I associate a new recipient account with token
        And the mirror node REST API should return status <httpStatusCode>
        Then I mint a serial number from the token
        And the mirror node nft transactions REST APIs should return status <httpStatusCode> for nft 0 serial number 0 fund flow
        Then I transfer serial number of token to recipient
        And the mirror node REST API should return status <httpStatusCode> for nft 0 serial number 0 fund flow
        Then I wipe serial number 0 from token 0
        And the mirror node nft transactions REST APIs should return status <httpStatusCode> for nft 0 serial number 0 fund flow
        Then I mint a serial number from the token
        And the mirror node nft transactions REST APIs should return status <httpStatusCode> for nft 0 serial number 1 fund flow
        Then I burn serial number 1 from token 0
        And the mirror node nft transactions REST APIs should return status <httpStatusCode> for nft 0 serial number 1 fund flow
        Then I mint a serial number from the token
        And the mirror node nft transactions REST APIs should return status <httpStatusCode> for nft 0 serial number 2 fund flow
        Then I delete the token
        And the mirror node nft transactions REST APIs should return status <httpStatusCode> for nft 0 serial number 2 fund flow
        Examples:
            | httpStatusCode | supplyType |
            | 200            | "INFINITE" |
            | 200            | "FINITE"   |

    @acceptance @nft
    Scenario Outline: Validate Update Treasury NFT Flow - Create, Associate, Mint, Update Treasury
        Given I successfully create a new nft
        Then the mirror node REST API should return status <httpStatusCode>
        When I associate a new recipient account with token
        And the mirror node REST API should return status <httpStatusCode>
        Then I mint a serial number from the token
        And the mirror node nft transactions REST APIs should return status <httpStatusCode> for nft 0 serial number 0 fund flow
        #TODO This test should be updated when services enables the ability to change NFT treasury accounts.
        Then I update the treasury of token 0 to recipient 0
        Examples:
            | httpStatusCode |
            | 200            |

@TokenBase @FullSuite
Feature: HTS Base Coverage Feature

    @Acceptance
    Scenario Outline: Validate Base Token Flow - Create, Associate, Transfer
        Given I successfully create a new token
        When I associate a new account with token
        Then I transfer <amount> tokens to recipient
        Examples:
            | amount |
            | 2350   |

    @Sanity @Acceptance
    Scenario Outline: Validate Freeze and KYC Flow - Create, Unfreeze, GrankKyc, Transfer
        Given I successfully create a new token with freeze status <initialFreezeStatus> and kyc status <initialKycStatus>
        When I associate a new account with token
        And I set new account freeze status to <newFreezeStatus>
        And I set new account kyc status to <newKycStatus>
        Then I transfer <amount> tokens to recipient
        Examples:
            | initialFreezeStatus | initialKycStatus | newFreezeStatus | newKycStatus | amount |
            | 1                   | 2                | 2               | 1            | 2350   |

    @Negative @Acceptance
    Scenario Outline: Validate Negative Association
        Given I successfully create a new token
        Then the network should observe an error associating a token <errorCode>
        Examples:
            | errorCode                             |
            | "TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT" |

    @Negative @Acceptance
    Scenario Outline: Validate token creation with bad symbols
        Given I provide a token symbol <tokenId>
        Then the network should observe an error creating a token <errorCode>
        Examples:
            | tokenId     | errorCode              |
            | ""          | "MISSING_TOKEN_SYMBOL" |
            | "q1MG"      | "INVALID_TOKEN_SYMBOL" |
            | "hashGRAPH" | "INVALID_TOKEN_SYMBOL" |

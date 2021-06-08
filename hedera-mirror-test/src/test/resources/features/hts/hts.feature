@tokenbase @fullsuite
Feature: HTS Base Coverage Feature

    @critical @release @acceptance
    Scenario Outline: Validate Base Token Flow - Create, Associate, Fund
        Given I successfully create a new token
        Then the mirror node REST API should return status <httpStatusCode>
        When I associate with token
        Then the mirror node REST API should return status <httpStatusCode>
        Then I transfer <amount> tokens to payer
        Then the mirror node REST API should return status <httpStatusCode> for token fund flow
        Examples:
            | amount | httpStatusCode |
            | 2350   | 200            |

    @release @acceptance
    Scenario Outline: Validate Freeze and KYC Flow - Create, Unfreeze, GrantKyc
        Given I successfully onboard a new token account with freeze status <initialFreezeStatus> and kyc status <initialKycStatus>
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
        Given I successfully onboard a new token account
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
        Given I successfully onboard a new token account
        When I associate a new recipient account with token
        And the mirror node REST API should return status <httpStatusCode>
        Then I dissociate the account from the token
        And the mirror node REST API should return status <httpStatusCode>
        Then I delete the token
        And the mirror node REST API should return status <httpStatusCode>
        Examples:
            | httpStatusCode |
            | 200            |

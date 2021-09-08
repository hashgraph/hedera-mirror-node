@accounts @fullsuite
Feature: Account Coverage Feature

    @critical @release @acceptance @balancecheck
    Scenario Outline: Validate account balance check scenario
        When I request balance info for this account
        Then the crypto balance should be greater than or equal to <threshold>
        Examples:
            | threshold  |
            | 4000000000 |

    @createcryptoaccount
    Scenario Outline: Create crypto account
        When I create a new account with balance <amount> tℏ
        Then the new balance should reflect cryptotransfer of <amount>
        Examples:
            | amount    |
            | 100000000 |

    @critical @release @acceptance @cryptotransfer
    Scenario Outline: Validate simple CryptoTransfer
        Given I send <amount> tℏ to <accountName>
        Then the mirror node REST API should return status <httpStatusCode> for the crypto transfer transaction
        And the new balance should reflect cryptotransfer of <amount>
        Examples:
            | amount | accountName | httpStatusCode |
            | 1      | "ALICE"     | 200            |

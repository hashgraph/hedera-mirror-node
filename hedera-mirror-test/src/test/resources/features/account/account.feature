@accounts @fullsuite
Feature: Account Coverage Feature

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
        Then the mirror node REST API should return OK for the crypto transfer transaction
        And the new balance should reflect cryptotransfer of <amount>
        Examples:
            | amount | accountName |
            | 1      | "ALICE"     |

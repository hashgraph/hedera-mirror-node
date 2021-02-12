@Accounts @FullSuite
Feature: Account Coverage Feature

    @BalanceCheck @Sanity @Acceptance @TokenSanity
    Scenario Outline: Validate account balance check scenario
        When I request balance info for this account
        Then the crypto balance should be greater than or equal to <threshold>
        Examples:
            | threshold |
            | 1000000   |

    @CreateCryptoAccount
    Scenario Outline: Create crypto account
        When I create a new account with balance <amount> tℏ
        Then the new balance should reflect cryptotransfer of <amount>
        Examples:
            | amount    |
            | 100000000 |

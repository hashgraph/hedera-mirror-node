@Accounts
Feature: Account Coverage Feature

    Background: User has sdk client
        Given User obtained SDK client for account feature

    @BalanceCheck
    # To check operator balance, set accountId to empty string
    Scenario Outline: Validate account balance check scenario
        Given I provided an account string of <accountId>
        When I request balance info for this account
        Then the result should be greater than or equal to <threshold>
        Examples:
            | accountId | threshold |
            | ""        | 1000000   |

    # Must be last scenario in file
    @ClientClose
    Scenario: Client close place holder

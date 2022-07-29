@network @fullsuite
Feature: Account Coverage Feature

    @critical @release @acceptance
    Scenario Outline: Get network stake
        When I query the network stake
        Then the mirror node REST API returns the network stake
        Examples:
            | maxStakingRewardRatePerHbar | nodeRewardFeeDenominator | nodeRewardFeeNumerator | stakeTotal         | stakingPeriod | stakingPeriodDuration | stakingPeriodsStored | stakingRewardFeeDenominator | stakingRewardFeeNumerator | stakingRewardRate | stakingStartThreshold |
            | 17808                       | 1                        | 1                      | 35000000000000000  |               | 1440                  |  365                 | 1                           | 1                         | 100000000000      | 25000000000000000     |

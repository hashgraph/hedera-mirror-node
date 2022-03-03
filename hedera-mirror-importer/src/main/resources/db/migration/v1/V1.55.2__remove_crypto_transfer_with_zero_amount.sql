-- remove crypto transfers with amount equals 0.

delete from crypto_transfer where amount = 0

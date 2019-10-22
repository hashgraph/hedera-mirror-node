--
 -- Add validDuration and maxFee columns to t_transactions
 --

 alter table t_transactions
     add column valid_duration bigint null;

 alter table t_transactions
     add column max_fee bigint null;
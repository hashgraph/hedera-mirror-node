-- clear token metadata and metadata_key accidentally set prior to services 0.49.0
with last_hapi_version as (
  select coalesce((
    select (hapi_version_major * 2^32)::bigint + hapi_version_minor
    from record_file
    order by consensus_end desc
    limit 1
  ), 0::bigint) as version
), lower_bound as (
  -- The exclusive lower bound when version is at least 0.47. It's not 0.48 because services release 0.48 reports HAPI
  -- version 0.47 in record files. The value is either the last consensus timestamp of services release 0.46 or 1ns
  -- before the first consensus timestamp in case the min version in record_file table is > 0.46
  select coalesce((
    select consensus_end
    from record_file
    where hapi_version_major = 0 and hapi_version_minor = 46
    order by consensus_end desc
    limit 1), (
    select consensus_start - 1
    from record_file
    order by consensus_end
    limit 1
  )) as timestamp
), timestamp_info as (
  select case when version < 47 then 'empty'::int8range
              -- upper bound is null when last version is in range [47, 49)
              when version < 49 then int8range((select timestamp from lower_bound), null, '()')
              -- when last version >= 49, the exclusive upper bound is either 1ns after the last 0.47 record file
              -- or the first consensus timestamp of the first record file. Note the fallback of the first consensus
              -- timestamp of the first record file effectively make the range empty, since the exclusive range end will
              -- be either before the exclusive range start or equal to 1ns after the exclusive range start
              else int8range((select timestamp from lower_bound), coalesce((
                  select consensus_end + 1
                  from record_file
                  where hapi_version_major = 0 and hapi_version_minor = 47
                  order by consensus_end desc
                  limit 1
                ), (
                  select consensus_start
                  from record_file
                  order by consensus_end
                  limit 1
                )), '()')
         end as affected_range
  from last_hapi_version
), clear_token_history as (
  update token_history
  set metadata = null,
      metadata_key = null
  from timestamp_info
  -- the two ranges overlap and the token timestamp range does not extend to the left of the affected range, i.e.
  -- the lower bound of the token timestamp range (when the token was created / updated), is in affected_range.
  -- this is faster since it can utilize the gist index on timestamp_range
  where timestamp_range && affected_range and timestamp_range &> affected_range
)
update token
set metadata = null,
    metadata_key = null
from timestamp_info
where affected_range @> lower(timestamp_range);

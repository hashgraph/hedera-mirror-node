---
--- Add new topic_message indexes to enable initial rest api for individual topic messages
---

create index if not exists topic_message__topic_num_realm_num_seqnum
    on topic_message (topic_num, realm_num, sequence_number);

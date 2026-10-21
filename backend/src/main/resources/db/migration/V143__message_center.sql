create table inbox_message (
    id varchar(36) primary key,
    category varchar(20) not null,
    title varchar(200) not null,
    content text not null,
    source varchar(16) not null,
    audience varchar(16) not null,
    sender_id bigint null,
    sender_name varchar(200) not null,
    created_at datetime(3) not null
);

create table message_recipient (
    message_id varchar(36) not null,
    user_id bigint not null,
    read_at datetime(3) null,
    deleted_at datetime(3) null,
    primary key (message_id, user_id),
    constraint fk_message_recipient_message foreign key (message_id) references inbox_message(id),
    constraint fk_message_recipient_user foreign key (user_id) references app_user(id)
);
create index idx_message_recipient_inbox on message_recipient(user_id, deleted_at, read_at, message_id);
create index idx_inbox_message_time on inbox_message(created_at, id);

create table ai_model_price_version_sequence (
  model_id bigint not null,
  price_type varchar(16) not null,
  last_version_no int not null,
  primary key (model_id, price_type)
);

insert into ai_model_price_version_sequence (model_id, price_type, last_version_no)
select model_id, 'COST', max(version_no)
  from ai_model_price_version
 group by model_id;

insert into ai_model_price_version_sequence (model_id, price_type, last_version_no)
select model_id, 'POINT', max(version_no)
  from ai_model_point_price_version
 group by model_id;

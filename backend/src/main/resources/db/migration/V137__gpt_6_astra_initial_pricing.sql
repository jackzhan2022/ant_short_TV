-- Snapshot the currently effective Terra prices; keep each model's price history independent.
insert into ai_model_price_version
  (model_id, version_no, status, effective_from, published_at, created_at)
select model.id, 1, 'PUBLISHED', now(), now(), now()
  from ai_model model
 where model.code = 'gpt-6-astra'
   and not exists (select 1 from ai_model_price_version where model_id = model.id)
   and exists (
     select 1 from ai_model_price_version price
       join ai_model source on source.id = price.model_id
      where source.code = 'gpt-5.6-terra-xiongxiong'
        and price.status = 'PUBLISHED' and price.effective_from <= now()
        and (price.effective_to is null or price.effective_to > now())
   );

insert ignore into ai_model_price_component
  (price_version_id, metric, unit_size, unit_price, currency, dimensions_json, dimensions_key, created_at)
select target.id, component.metric, component.unit_size, component.unit_price, component.currency,
       component.dimensions_json, component.dimensions_key, now()
  from ai_model_price_version target
  join ai_model model on model.id = target.model_id and model.code = 'gpt-6-astra'
  cross join ai_model_price_component component
 where target.version_no = 1
   and component.price_version_id = (
     select price.id from ai_model_price_version price
       join ai_model source on source.id = price.model_id
      where source.code = 'gpt-5.6-terra-xiongxiong'
        and price.status = 'PUBLISHED' and price.effective_from <= now()
        and (price.effective_to is null or price.effective_to > now())
      order by price.effective_from desc limit 1
   );

insert into ai_model_point_price_version
  (model_id, version_no, status, effective_from, published_at, created_at)
select model.id, 1, 'PUBLISHED', now(), now(), now()
  from ai_model model
 where model.code = 'gpt-6-astra'
   and not exists (select 1 from ai_model_point_price_version where model_id = model.id)
   and exists (
     select 1 from ai_model_point_price_version price
       join ai_model source on source.id = price.model_id
      where source.code = 'gpt-5.6-terra-xiongxiong'
        and price.status = 'PUBLISHED' and price.effective_from <= now()
        and (price.effective_to is null or price.effective_to > now())
   );

insert ignore into ai_model_point_price_component
  (price_version_id, metric, unit_size, point_rate, dimensions_json, dimensions_key, created_at)
select target.id, component.metric, component.unit_size, component.point_rate,
       component.dimensions_json, component.dimensions_key, now()
  from ai_model_point_price_version target
  join ai_model model on model.id = target.model_id and model.code = 'gpt-6-astra'
  cross join ai_model_point_price_component component
 where target.version_no = 1
   and component.price_version_id = (
     select price.id from ai_model_point_price_version price
       join ai_model source on source.id = price.model_id
      where source.code = 'gpt-5.6-terra-xiongxiong'
        and price.status = 'PUBLISHED' and price.effective_from <= now()
        and (price.effective_to is null or price.effective_to > now())
      order by price.version_no desc limit 1
   );

insert ignore into ai_model_price_version_sequence (model_id, price_type, last_version_no)
select price.model_id, 'COST', max(price.version_no)
  from ai_model_price_version price
  join ai_model model on model.id = price.model_id
 where model.code = 'gpt-6-astra'
 group by price.model_id;

insert ignore into ai_model_price_version_sequence (model_id, price_type, last_version_no)
select price.model_id, 'POINT', max(price.version_no)
  from ai_model_point_price_version price
  join ai_model model on model.id = price.model_id
 where model.code = 'gpt-6-astra'
 group by price.model_id;

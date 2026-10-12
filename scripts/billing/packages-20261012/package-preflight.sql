select 'packages',id,code,package_type,status from commercial_package order by id;
select 'versions',id,package_id,version_no,name,status,price,list_price,currency,billing_period,period_months,effective_from,effective_to from commercial_package_version order by id;
select 'entitlements',package_version_id,entitlement_type,numeric_value,text_value from commercial_entitlement order by package_version_id,id;
select 'definitions',code,name,category,status from commercial_entitlement_definition order by id;
select 'orders',status,count(*),sum(amount) from commercial_order group by status;
select 'ledger',entry_type,count(*),sum(amount) from point_ledger group by entry_type;
select 'account',count(*),sum(balance),sum(reserved_balance),sum(total_granted),sum(total_consumed) from team_point_account;
select 'subscriptions',status,count(*) from team_subscription group by status;

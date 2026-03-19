update warehouses
set price_per_hour_small = 4.00
where price_per_hour_small is null;

update warehouses
set price_per_hour_medium = 4.50
where price_per_hour_medium is null;

update warehouses
set price_per_hour_large = 5.50
where price_per_hour_large is null;

update warehouses
set price_per_hour_extra_large = 6.50
where price_per_hour_extra_large is null;

update warehouses
set pickup_fee = 14.00
where pickup_fee is null;

update warehouses
set dropoff_fee = 14.00
where dropoff_fee is null;

update warehouses
set insurance_fee = 7.50
where insurance_fee is null;

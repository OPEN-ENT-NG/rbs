UPDATE rbs.booking 
SET start_date = start_date at time zone 'Europe/Paris' at time zone 'utc', 
end_date=end_date at time zone 'Europe/Paris' at time zone 'utc';
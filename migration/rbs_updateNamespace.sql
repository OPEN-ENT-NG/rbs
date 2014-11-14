UPDATE rbs.resource_type_shares
SET action=replace(action, 'fr-wseduc', 'net-atos-entng')
WHERE action like 'fr-wseduc%';
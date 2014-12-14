INSERT INTO rbs.resource_type_shares (member_id, resource_id, action)
SELECT member_id, resource_id, 'net-atos-entng-rbs-controllers-ResourceTypeController|getModerators' AS action
FROM rbs.resource_type_shares 
WHERE action IN ('net-atos-entng-rbs-controllers-ResourceTypeController|getResourceType', 'net-atos-entng-rbs-controllers-ResourceTypeController|getModerators')
GROUP BY member_id, resource_id 
HAVING count(*) = 1;
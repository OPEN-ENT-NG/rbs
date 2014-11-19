begin transaction
match (a:Action) SET a.name = replace(a.name, 'fr.wseduc.rbs', 'net.atos.entng.rbs');
commit
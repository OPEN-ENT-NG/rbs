begin transaction
match (a:WorkflowAction{name:'net.atos.entng.rbs.controllers.ResourceController|create'})-[r]-() delete a,r;
commit
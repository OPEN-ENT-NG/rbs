var rbsBehaviours = {
	resources: {
	},
	workflow: {
	}
};

Behaviours.register('rbs', {
	behaviours: rbsBehaviours,
	resource: function(resource){
		var rightsContainer = resource;
		if(resource instanceof Subject && resource.category){
			rightsContainer = resource.category;
		}
		if(resource instanceof Message && resource.subject && resource.subject.category){
			rightsContainer = resource.subject.category;
		}
		if(!resource.myRights){
			resource.myRights = {};
		}

		for(var behaviour in rbsBehaviours.resources){
			if(model.me.hasRight(rightsContainer, rbsBehaviours.resources[behaviour]) || model.me.userId === resource.owner.userId){
				if(resource.myRights[behaviour] !== undefined){
					resource.myRights[behaviour] = resource.myRights[behaviour] && rbsBehaviours.resources[behaviour];
				}
				else{
					resource.myRights[behaviour] = rbsBehaviours.resources[behaviour];
				}
			}
		}
		return resource;
	},
	workflow: function(){
		var workflow = { };
		var rbsWorkflow = rbsBehaviours.workflow;
		for(var prop in rbsWorkflow){
			if(model.me.hasWorkflow(rbsWorkflow[prop])){
				workflow[prop] = true;
			}
		}

		return workflow;
	},
	resourceRights: function(){
		return ['read', 'contrib', 'publish', 'manager']
	}
});
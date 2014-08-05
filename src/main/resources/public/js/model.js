
function Booking() {

}

function Resource() {

}

Resource.prototype.create = function(cb) {
	var resource = this;
	http().postJson('/rbs/resources', this).done(function(r){
		resource.updateData(r);

		// Update collections
		model.resources.push(resource);
		if(typeof cb === 'function'){
			cb();
		}
	});
}

Resource.prototype.toJSON = function() {
	return {
		name : this.name,
		description : this.description,
		icon : this.icon,
		periodic_booking : this.periodic_booking,
		is_available : this.is_available,
		min_delay : this.min_delay,
		max_delay : this.max_delay,
		type_id : this.type_id
	}
};


function ResourceType(data) {
	if (data) {
		this.updateData(data);
	}

	var resourceType = this;

	this.collection(Resource, {
		behaviours: 'rbs'
	});
}

ResourceType.prototype.create = function(cb) {
	var resourceType = this;
	http().postJson('/rbs/type', this).done(function(t){
		resourceType.updateData(t);

		// Update collections
		model.resourceTypes.push(resourceType);
		if(typeof cb === 'function'){
			cb();
		}
	});
}

ResourceType.prototype.toJSON = function() {
	return {
		name : this.name,
		validation : this.validation,
		school_id : this.school_id
	}
};

model.build = function(){
	this.makeModels([ResourceType, Resource, Booking]);

	this.collection(ResourceType, {
		sync: function(){
			var collection = this;
			// Load the ResourceTypes
			http().get('/rbs/types').done(function(resourceTypes){
				if (model.resources === undefined) {
					// Load the Resources once
					this.one('sync', function(){
						model.buildResources();
					});
				}
				this.load(resourceTypes);
			}.bind(this));
		},
		behaviours: 'rbs'
	});

	this.collection(Booking, {
		behavious: 'rbs'
	});
};

model.buildResources = function() {
	this.collection(Resource, {
		sync: function(){
			var collection = this;
			// Load the Resources
			http().get('/rbs/resources').done(function(resources){
				var actions = (resources !== undefined ? resources.length : 0);
				collection.load(resources, function(resource){
					// Load the ResourceType's collection with associated Resource
					var resourceType = _.find(model.resourceTypes.all, function(rt){
						return rt.id === resource.type_id;
					});
					if (resourceType !== undefined) {
						resourceType.resources.all.push(resource);
					}
					actions--;
					if (actions === 0) {
						model.resourceTypes.trigger('sync');
					}
				});
			});
		},
		behaviours: 'rbs'
	});
	this.resources.sync();
}
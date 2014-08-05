
function Booking() {

}

function Resource() {

}

function ResourceType(data) {
	if (data) {
		this.updateData(data);
	}

	var resourceType = this;

	this.collection(Resource, {
		sync: function(){
			http().get('/rbs/type/' + resourceType._id + '/resources').done(function(resources){
				this.load(resources);
			}.bind(this))
		},
		removeSelection: function(){
			var counter = this.selection().length;
			this.selection().forEach(function(item){
				http().delete('/rbs/resource/' + item._id).done(function(){
					counter = counter - 1;
					if (counter === 0) {
						Collection.prototype.removeSelection.call(this);
						resourceType.resources.sync();
					}
				});
			});
		},
		behaviours: 'rbs'
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
};

model.buildResources = function() {
	this.collection(Resource, {
		sync: function(){
			var collection = this;
			// Load the Resources
			http().get('/rbs/resources').done(function(resources){
				collection.load(resources);
				// Load each ResourceType's collection with associated Resources
				var groupedResources  = _.groupBy(resources, function(resource) {
					return resource.type_id;
				});
				var actions = model.resourceTypes.length;
				model.resourceTypes.forEach(function(resourceType){
					resourceType.resources.all = [];
					if (_.has(groupedResources, resourceType.id)) {
						_.each(groupedResources[resourceType.id], function(res){
							resourceType.resources.all.push(res);
						});
					}
				});
				model.resourceTypes.trigger('sync');
			});
		},
		behaviours: 'rbs'
	});
	this.resources.sync();
}
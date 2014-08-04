
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
			http().get('/rbs/types').done(function(rts){
				var resourceTypes = Array();
				_.each(rts, function(rt){
					resourceTypes.push(new ResourceType(rt));
				});
				// Load the Resources
				http().get('/rbs/resources').done(function(resources){
					// Load each ResourceType's collection with associated Resources
					var groupedResources  = _.groupBy(resources, function(resource) {
						return resource.type_id;
					});
					var actions = resourceTypes.length;
					_.each(resourceTypes, function(resourceType){
						if (_.has(groupedResources, resourceType.id)) {
							resourceType.resources.addRange(groupedResources[resourceType.id], null, false);
						}
						collection.push(resourceType);
					});
				});
			}.bind(this));
		},
		behaviours: 'rbs'
	})
};
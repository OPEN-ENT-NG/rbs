
function Booking() {

}

function Resource() {

}

function ResourceType() {
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
	return resourceType;
}

model.build = function(){
	this.makeModels([ResourceType, Resource, Booking]);

	this.collection(ResourceType, {
		sync: function(){
			// Load the ResourceTypes
			http().get('/rbs/types').done(function(resourceTypes){
				this.load(resourceTypes);
				// Load the Resources
				http().get('/rbs/resources').done(function(resources){
					// Load each ResourceType's collection with associated Resources
					var groupedResources  = _.groupBy(resources, function(resource) {
						return resource.type_id;
					});
					_.each(resourceTypes, function(resourceType){
						if (_.has(groupedResources, resourceType.id)) {
							resourceType.resources.load(groupedResources[resourceType.id]);
						}
					});
				});
			}.bind(this));
		},
		behaviours: 'rbs'
	})
};
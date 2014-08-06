
function Booking() {

}

function Resource() {

}

Resource.prototype.save = function(cb) {
	if(this.id) {
		this.update(cb);
	}
	else {
		this.create(cb);
	}
}

Resource.prototype.update = function(cb) {
	var resource = this;
	var originalTypeId = this.type_id;
	this.type_id = this.type.id;

	http().putJson('/rbs/resource/' + this.id, this).done(function(){

		if (resource.type_id !== originalTypeId) {
			// Move between types collections
			var originalType = model.resourceTypes.find(function(t) { return t.id === originalTypeId; });
			originalType.resources.remove(resource, false);
			originalType.resources.trigger('sync');
			resource.type.resources.all.push(resource);
			resource.type.resources.trigger('sync');		
		}
		if(typeof cb === 'function'){
			cb();
		}
	});
}

Resource.prototype.create = function(cb) {
	var resource = this;
	this.type_id = this.type.id;

	http().postJson('/rbs/resources', this).done(function(r){
		resource.updateData(r);

		// Update collections
		resource.type.resources.all.push(resource);
		resource.type.resources.trigger('sync');
		model.resources.push(resource);
		if(typeof cb === 'function'){
			cb();
		}
	});
}

Resource.prototype.delete = function(cb) {
	var resource = this;

	http().delete('/rbs/resource/' + this.id).done(function(){
		model.resources.remove(resource);
		if(typeof cb === 'function'){
			cb();
		}
	});
}

Resource.prototype.toJSON = function() {
	var json = {
		name : this.name,
		periodic_booking : this.periodic_booking,
		is_available : this.is_available,
		type_id : this.type_id
	}
	if (this.icon) {
		json.icon = this.icon;
	}
	if (this.description) {
		json.description = this.description;
	}
	return json;
};


function ResourceType(data) {
	if (data) {
		this.updateData(data);
	}

	var resourceType = this;

	this.collection(Resource, {
		removeSelection : function() {
			var counter = this.selection().length;
			this.selection().forEach(function(item){
				http().delete('/rbs/resource/' + item.id).done(function(){
					counter = counter - 1;
					if (counter === 0) {
						Collection.prototype.removeSelection.call(this);
						model.resourceTypes.sync();
					}
				});
			});
		},
		behaviours: 'rbs'
	});
}

ResourceType.prototype.save = function(cb) {
	if(this.id) {
		this.update(cb);
	}
	else {
		this.create(cb);
	}
}

ResourceType.prototype.update = function(cb) {
	http().putJson('/rbs/type/' + this.id, this).done(function(){
		if(typeof cb === 'function'){
			cb();
		}
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

ResourceType.prototype.delete = function(cb) {

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
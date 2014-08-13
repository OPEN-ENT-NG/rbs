// custom loading
loader.loadFile('/rbs/public/js/additional.js');


model.colorMine = 'cyan';
model.colors = ['green', 'pink', 'purple', 'orange'];
model.STATE_CREATED = 1;
model.STATE_VALIDATED = 2;
model.STATE_REFUSED = 3;

model.times = [];
model.timeConfig = {
	interval: 5, // in minutes
	start_hour: 6,
	end_hour: 23,
	default_hour: 8
};

function Booking() {

}

Booking.prototype.save = function(cb) {
	if(this.id) {
		this.update(cb);
	}
	else {
		this.create(cb);
	}
};

Booking.prototype.update = function(cb) {
	this.start_date = this.startMoment.unix();
	this.end_date = this.endMoment.unix();
	this.resource_id = this.resource.id;

	var booking = this;
	http().putJson('/rbs/resource/' + this.resource_id + '/booking/' + this.id, this).done(function(){
		this.status = model.STATE_CREATED;
		if(typeof cb === 'function'){
			cb();
		}
	});
};

Booking.prototype.create = function(cb) {
	this.start_date = this.startMoment.unix();
	this.end_date = this.endMoment.unix();
	this.resource_id = this.resource.id;

	var booking = this;
	http().postJson('/rbs/resource/' + this.resource_id + '/booking', this).done(function(b){
		booking.updateData(b);

		// Update collections
		if (booking.resource.selected) {
			booking.resource.bookings.push(booking);
		}
		model.mine.bookings.push(booking);
		model.bookings.pushAll([booking]);
		if(typeof cb === 'function'){
			cb();
		}
	});
};

Booking.prototype.validate = function(cb) {
	this.status = model.STATE_VALIDATED;
	var data = {
		status: this.status
	};
	this.process(data, cb);
};

Booking.prototype.refuse = function(cb) {
	this.status = model.STATE_REFUSED;
	var data = {
		status: this.status,
		refusal_reason: this.refusal_reason
	};
	this.process(data, cb);
};

Booking.prototype.process = function(data, cb) {
	this.resource_id = this.resource.id;

	var booking = this;
	http().putJson('/rbs/resource/' + this.resource_id + '/booking/' + this.id + '/process', data).done(function(){
		if(typeof cb === 'function'){
			cb();
		}
	});
};

Booking.prototype.isPending = function() {
	return this.status === model.STATE_CREATED;
}

Booking.prototype.isValidated = function() {
	return this.status === model.STATE_VALIDATED;
}

Booking.prototype.isRefused = function() {
	return this.status === model.STATE_REFUSED;
}

Booking.prototype.toJSON = function() {
	var json = {
		start_date : this.start_date,
		end_date : this.end_date,
	}
	if (this.booking_reason) {
		json.booking_reason = this.booking_reason;
	}
	return json;
};


function Resource() {
	var resource = this;
	this.collection(Booking, {
		sync: function(cb){
			// Load the Bookings
			http().get('/rbs/resource/' + resource.id + '/bookings').done(function(bookings){
				_.each(bookings, function(booking){
					booking.resource = resource;
					booking.color = resource.type.color;
					booking.startMoment = moment(booking.start_date);
					booking.endMoment = moment(booking.end_date);
				});
				this.load(bookings);
				if(typeof cb === 'function'){
					cb();
				}
			}.bind(this));
		},
		behaviours: 'rbs'
	});
}

Resource.prototype.save = function(cb) {
	if(this.id) {
		this.update(cb);
	}
	else {
		this.create(cb);
	}
};

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
};

Resource.prototype.create = function(cb) {
	var resource = this;
	this.type_id = this.type.id;

	http().postJson('/rbs/resources', this).done(function(r){
		resource.updateData(r);

		// Update collections
		resource.type.resources.push(resource);
		if(typeof cb === 'function'){
			cb();
		}
	});
};

Resource.prototype.delete = function(cb) {
	var resource = this;

	http().delete('/rbs/resource/' + this.id).done(function(){
		var resourceType = resource.type;
		type.resources.remove(resource);
		if(typeof cb === 'function'){
			cb();
		}
	});
};

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

	// Resource collection embedded, not synced
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
};

ResourceType.prototype.update = function(cb) {
	http().putJson('/rbs/type/' + this.id, this).done(function(){
		if(typeof cb === 'function'){
			cb();
		}
	});
};

ResourceType.prototype.create = function(cb) {
	var resourceType = this;
	http().postJson('/rbs/type', this).done(function(t){
		resourceType.updateData(t);
		resourceType._id = resourceType.id;

		// Update collections
		model.resourceTypes.push(resourceType);
		if(typeof cb === 'function'){
			cb();
		}
	});
};

ResourceType.prototype.delete = function(cb) {

};

ResourceType.prototype.toJSON = function() {
	return {
		name : this.name,
		validation : this.validation,
		school_id : this.school_id
	}
};


function BookingsHolder(url, color) {
	this.color = color;
	var holder = this;
	this.collection(Booking, {
		sync: function(cb){
			// Load the Bookings
			http().get(url).done(function(bookings){
				var resourceIndex = {};
				model.resourceTypes.forEach(function(resourceType){
					resourceType.resources.forEach(function(resource){
						resourceIndex[resource.id] = resource;
					});
				});
				_.each(bookings, function(booking){
					booking.resource = resourceIndex[booking.resource_id];
					booking.startMoment = moment(booking.start_date);
					booking.endMoment = moment(booking.end_date);
					if (color) {
						booking.color = holder.color;
					}
					else {
						booking.color = booking.resource.type.color;
					}
 				});
				this.load(bookings);
				if(typeof cb === 'function'){
					cb();
				}
			}.bind(this));
		},
		behaviours: 'rbs'
	});
}


function SelectionHolder() {
	resourceTypes: {};
	resources: {};
}

SelectionHolder.prototype.record = function(restoreUnprocessed, resourceTypeCallback, resourceCallback) {
	this.restoreUnprocessed = (restoreUnprocessed === true ? true : undefined);
	this.mine = (model.mine.selected === true ? true : undefined);

	var typeRecords = {};
	var resourceRecords = {};

	model.resourceTypes.forEach(function(resourceType){
		if (resourceType.expanded === true) {
			typeRecords[resourceType.id] = true;
			if(typeof resourceTypeCallback === 'function'){
				resourceTypeCallback(resourceType);
			}
		}
		resourceType.resources.forEach(function(resource){
			if (resource.selected === true) {
				resourceRecords[resource.id] = true;
				if(typeof resourceCallback === 'function'){
					resourceCallback(resource);
				}
			}
		});
	});

	this.resourceTypes = typeRecords;
	this.resources = resourceRecords;
};

SelectionHolder.prototype.restore = function(mineCallback, unprocessedCallback, resourceCallback) {
	if (this.restoreUnprocessed === true) {
		// Restore unprocessed only, and keep record
		model.unprocessed.selected === true;
		if(typeof unprocessedCallback === 'function'){
			unprocessedCallback();
		}
		this.restoreUnprocessed = undefined;
		return;
	}

	// Deselect unprocessed
	model.unprocessed.selected = undefined;

	// Apply recorded selections
	if (this.mine === true) {
		model.mine.selected = true;
		if(typeof mineCallback === 'function'){
			mineCallback();
		}
		this.mine = undefined;
	}

	var typeRecords = this.resourceTypes;
	var resourceRecords = this.resources;

	model.resourceTypes.forEach(function(resourceType){
		if (typeRecords[resourceType.id]) {
			resourceType.expanded = true;
		}
		resourceType.resources.forEach(function(resource){
			if (resourceRecords[resource.id]) {
				resource.selected = true;
				if(typeof resourceCallback === 'function'){
					resourceCallback(resource);
				}
			}
		});
	});

	this.resourceTypes = {};
	this.resources = {};
};


model.build = function(){
	this.makeModels([ResourceType, Resource, Booking, BookingsHolder, SelectionHolder]);

	// ResourceTypes collection with embedded Resources
	this.collection(ResourceType, {
		sync: function(){
			var collection = this;
			// Record selections
			model.recordedSelections.record(model.unprocessed.selected);
			// Load the ResourceTypes
			http().get('/rbs/types').done(function(resourceTypes){
				var index = 0;
				_.each(resourceTypes, function(resourceType){
					resourceType.color = model.findColor(index);
					resourceType._id = resourceType.id;
					index++;
				});

				this.load(resourceTypes);

				// always Sync the Resources with the ResourceTypes
				this.one('sync', function(){
					collection.syncResources();
				});
				
			}.bind(this));
		},
		syncResources: function(){
			var collection = this;
			// Prepare ResourceType index and Clear Resources collections
			var resourceTypeIndex = {};
			collection.forEach(function(resourceType){
				resourceType.resources.all = [];
				resourceTypeIndex[resourceType.id] = resourceType;
			});
			// Load the Resources in each ResourceType
			http().get('/rbs/resources').done(function(resources){
				var actions = (resources !== undefined ? resources.length : 0);
				_.each(resources, function(resource){
					// Load the ResourceType's collection with associated Resource
					var resourceType = resourceTypeIndex[resource.type_id];
					if (resourceType !== undefined) {
						resource.type = resourceType;
						resourceType.resources.push(resource);
					}
					actions--;
					if (actions === 0) {
						collection.trigger('syncResources');
					}
				});
			});
		},
		deselectAllResources: function(){
			this.forEach(function(resourceType){
				resourceType.resources.deselectAll();
			});
		},
		behaviours: 'rbs'
	});

	// Bookings collection, not auto-synced
	this.collection(Booking, {
		pushAll: function(datas, trigger) {
			if (datas) {
				this.all = _.union(this.all, datas);
				if (trigger) {
					this.trigger('sync');
				}
			}
		},
		pullAll: function(datas, trigger) {
			if (datas) {
				this.all = _.difference(this.all, datas);
				if (trigger) {
					this.trigger('sync');	
				}
			}
		},
		clear: function(trigger) {
			this.all = [];
			if (trigger) {
				this.trigger('sync');	
			}
		},
		selectionResources : function() {
			//returning the new array systematically breaks the watcher
			//due to the reference always being updated
			var currentResourcesSelection = _.pluck(this.selection(), 'resource') || [];
			if(!this._selectionResources || this._selectionResources.length !== currentResourcesSelection.length){
				this._selectionResources = currentResourcesSelection;
			}
			return this._selectionResources;
		},
		behavious: 'rbs'
	});

	// Holders for special lists of Bookings
	this.mine = new BookingsHolder('/rbs/bookings', this.colorMine);
	this.unprocessed = new BookingsHolder('/rbs/bookings/unprocessed');
	this.recordedSelections = new SelectionHolder();

	model.loadTimes();
};

model.findColor = function(index) {
	return model.colors[index % model.colors.length];
};

model.loadTimes = function() {
	for(hour = model.timeConfig.start_hour; hour <= model.timeConfig.end_hour; hour++) {
		for (min = 0; min < 60; min = min + model.timeConfig.interval) {
			var hourMinutes = moment().hours(hour).minutes(min);
			hourMinutes.name = hourMinutes.format(' HH [h] mm ');
			model.times.push(hourMinutes);
		}
	}
};
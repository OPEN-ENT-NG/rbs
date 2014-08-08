
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
		resource.type.resources.all.push(resource);
		resource.type.resources.trigger('sync');
		model.resources.push(resource);
		if(typeof cb === 'function'){
			cb();
		}
	});
};

Resource.prototype.delete = function(cb) {
	var resource = this;

	http().delete('/rbs/resource/' + this.id).done(function(){
		model.resources.remove(resource);
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
	this.collection(Booking, {
		sync: function(){
			// Load the Bookings
			http().get(url).done(function(bookings){
				var resourceIndex = {};
				_.each(model.resources.all, function(resource){
					resourceIndex[resource.id] = resource;
				});
				_.each(bookings, function(booking){
					booking.resource = resourceIndex[booking.resource_id];
					booking.startMoment = moment(booking.start_date);
					booking.endMoment = moment(booking.end_date);
					if (color) {
						booking.color = color;
					}
				});
				this.load(bookings);
			}.bind(this));
		},
		behaviours: 'rbs'
	});
}


model.build = function(){
	this.makeModels([ResourceType, Resource, Booking, BookingsHolder]);

	this.collection(ResourceType, {
		sync: function(){
			var collection = this;
			// Load the ResourceTypes
			http().get('/rbs/types').done(function(resourceTypes){
				var index = 0;
				_.each(resourceTypes, function(resourceType){
					resourceType.color = model.findColor(index);
					resourceType._id = resourceType.id;
					index++;
				});
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

	this.mine = new BookingsHolder('/rbs/bookings', this.colorMine);
	this.unprocessed = new BookingsHolder('/rbs/bookings/unprocessed');

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
		behavious: 'rbs'
	});

	model.loadTimes();
};

model.buildResources = function() {
	this.collection(Resource, {
		sync: function(){
			var collection = this;
			// Load the Resources
			http().get('/rbs/resources').done(function(resources){
				var actions = (resources !== undefined ? resources.length : 0);
				var resourceTypeIndex = {};
				_.each(model.resourceTypes.all, function(resourceType){
					resourceTypeIndex[resourceType.id] = resourceType;
				});
				collection.load(resources, function(resource){
					// Load the ResourceType's collection with associated Resource
					var resourceType = resourceTypeIndex[resource.type_id];
					if (resourceType !== undefined) {
						resource.type = resourceType;
						resourceType.resources.all.push(resource);
					}
					actions--;
					if (actions === 0) {
						model.resourceTypes.trigger('sync');
					}
				});

				model.mine.bookings.sync();
			});
		},
		behaviours: 'rbs'
	});
	this.resources.sync();
	this.trigger('loadResources');
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
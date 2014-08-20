// custom loading
loader.loadFile('/rbs/public/js/additional.js');


model.colorMine = 'cyan';
model.colors = ['green', 'pink', 'purple', 'orange'];
model.STATE_CREATED = 1;
model.STATE_VALIDATED = 2;
model.STATE_REFUSED = 3;

model.times = [];
model.timeConfig = { // 5min slots from 6h00 to 23h55, default 8h00
	interval: 5, // in minutes
	start_hour: 6,
	end_hour: 23,
	default_hour: 8
};

model.periods = {
	periodicities: [1, 2, 3, 4], // weeks
	days: [
		0, // sunday
		1, // monday
		2, // tuesday
		3, // wednesday
		4, // thursday
		5, // friday
		6  // saturday
	],
	occurences: [] // loaded by function
};

model.periodsConfig = {
	occurences: {
		start: 1,
		end: 52,
		interval: 1
	}
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
	var url = '/rbs/resource/' + this.resource.id + '/booking/' + this.id;
	if (this.is_periodic === true) {
		url = url + '/periodic';
	}

	var booking = this;
	http().putJson(url, this).done(function(){
		this.status = model.STATE_CREATED;
		if(typeof cb === 'function'){
			cb();
		}
	});
};

Booking.prototype.create = function(cb) {
	var url = '/rbs/resource/' + this.resource.id + '/booking';
	if (this.is_periodic === true) {
		url = url + '/periodic';
	}

	var booking = this;
	http().postJson(url, this).done(function(b){
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
	var booking = this;
	http().putJson('/rbs/resource/' + this.resource.id + '/booking/' + this.id + '/process', data).done(function(){
		if(typeof cb === 'function'){
			cb();
		}
	});
};

Booking.prototype.delete = function(cb) {
	var booking = this;
	http().delete('/rbs/resource/' + this.resource.id + '/booking/' + this.id).done(function(){
		if(typeof cb === 'function'){
			cb();
		}
	});
};

Booking.prototype.showSlots = function() {
	this.slots = this._slots;
};

Booking.prototype.hideSlots = function() {
	this.slots = [];
	_.each(this._slots, function(slot){
		slot.selected = undefined;
	});
};

Booking.prototype.isPending = function() {
	return this.status === model.STATE_CREATED;
};

Booking.prototype.isValidated = function() {
	return this.status === model.STATE_VALIDATED;
};

Booking.prototype.isRefused = function() {
	return this.status === model.STATE_REFUSED;
};

Booking.prototype.daysToBitMask = function() {
	var bitmask = 0;
	_.each(this.periodDays, function(periodDay){
		if (periodDay.value === true) {
			bitmask = bitmask + Math.pow(2, periodDay.number);
		}
	});
	return bitmask;
};

Booking.prototype.toJSON = function() {
	var json = {
		start_date : this.startMoment.unix(),
		end_date : this.endMoment.unix()
	};

	if (this.is_periodic === true) {
		json.periodicity = this.periodicity;
		json.days = this.daysToBitMask();

		if (this.occurences !== undefined && this.occurences > 0) {
			json.occurences = this.occurences;
		}
		else {
			json.periodic_end_date = this.periodicEndMoment.unix();
		}
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
			http().get('/rbs/resource/' + resource.id + '/bookings').done(function(rows){
				// Resource
				var resourceIndex = {};
				resourceIndex[resource.id] = resource;

				// Parse
				var bookingIndex = model.parseBookingsAndSlots(rows, resourceIndex);
				// Load
				this.load(_.toArray(bookingIndex.bookings));
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
		resourceType.resources.remove(resource);
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
		removeSelection : function(cb) {
			var counter = this.selection().length;
			_.each(this.selection, function(resource){
				resource.delete(function(){
					counter = counter - 1;
					if (counter === 0) {
						Collection.prototype.removeSelection.call(this);
						if(typeof cb === 'function'){
							cb();
						}
					}
				});
			});
		},
		collapseAll : function() {
			this.forEach(function(resource){
				if (resource.expanded === true) {
					resource.expanded = undefined;
				}
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
	var resourceType = this;
	http().delete('/rbs/type/' + this.id).done(function(){
		if(typeof cb === 'function'){
			cb();
		}
	});
};

ResourceType.prototype.toJSON = function() {
	return {
		name : this.name,
		validation : this.validation,
		school_id : this.school_id
	}
};


function BookingsHolder(params) {
	this.url = params.url;
	if (params.color) {
		this.color = params.color;
	}
	var holder = this;

	this.collection(Booking, {
		sync: function(cb){
			// Load the Bookings
			http().get(holder.url).done(function(rows){
				// Prepare ressources
				var resourceIndex = {};
				model.resourceTypes.forEach(function(resourceType){
					resourceType.resources.forEach(function(resource){
						resourceIndex[resource.id] = resource;
					});
				});

				// Parse
				var bookingIndex = model.parseBookingsAndSlots(rows, resourceIndex, holder.color !== undefined ? holder.color : undefined);
				// Load
				this.load(_.toArray(bookingIndex.bookings));
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
		removeSelection : function(cb) {
			var counter = this.selection().length;
			this.selection().forEach(function(booking){
				booking.delete(function(){
					counter = counter - 1;
					if (counter === 0) {
						Collection.prototype.removeSelection.call(this);
						if(typeof cb === 'function'){
							cb();
						}
					}
				});
			});
		},
		behavious: 'rbs'
	});

	// Holders for special lists of Bookings
	this.mine = new BookingsHolder({
		url: '/rbs/bookings', 
		color: this.colorMine
	});
	this.unprocessed = new BookingsHolder({
		url: '/rbs/bookings/unprocessed'
	});
	this.recordedSelections = new SelectionHolder();

	model.loadTimes();
	model.loadPeriods();
};

model.refresh = function() {
	// Record selections
	model.recordedSelections.record(model.unprocessed.selected);
	// Clear bookings
	model.bookings.clear();
	// Launch resync
	model.resourceTypes.sync();
};

model.findColor = function(index) {
	return model.colors[index % model.colors.length];
};

model.parseBookingsAndSlots = function(rows, resourceIndex, color) {
	// Prepare bookings and slots
	var bookingIndex = {
		bookings: {},
		slots: {}
	};

	// Process
	_.each(rows, function(row) {
		if (row.parent_booking_id === null) {
			// Is a Booking
			bookingIndex.bookings[row.id] = row;
			row.resource = resourceIndex[row.resource_id];
			model.parseBooking(row, color !== undefined ? color :  row.resource.type.color);
		}
		else {
			// Is a Slot
			if (bookingIndex.slots[row.parent_booking_id] === undefined) {
				bookingIndex.slots[row.parent_booking_id] = [];
			}
			bookingIndex.slots[row.parent_booking_id].push(row);
			model.parseSlot(row);
		}
	});

	// Link bookings and slots
	_.each(bookingIndex.bookings, function(booking){
		if (booking.is_periodic === true) {
			booking._slots = bookingIndex.slots[booking.id];
		}
	});

	return bookingIndex;
};

model.parseBooking = function(booking, color) {
	booking.color = color;
	booking.startMoment = moment(booking.start_date);
	booking.endMoment = moment(booking.end_date);

	// periodic booking
	if (booking.is_periodic === true) {
		// parse bitmask		
		booking.periodDays = model.bitMaskToDays(booking.days);
		// date if not by occurences
		if (booking.occurences === undefined || booking.occurences < 1) {
			booking.periodicEndMoment =  moment(booking.periodic_end_date);
		}
	}
};

model.parseSlot = function(slot) {
	slot.startMoment = moment(slot.start_date);
	slot.endMoment = moment(slot.end_date);
};

model.bitMaskToDays = function(bitMask) {
	var periodDays = [];
	var sunday = undefined;
	_.each(model.periods.days, function(day){
		var mask = Math.pow(2, day);
		var value = false;
		if ((bitMask & mask) != 0) {
			value = true;
		}
		if (day == 0) {
			sunday = {number: 0, value: value};
		}
		else {
			periodDays.push({number: day, value: value});
		}
	});
	if (sunday !== undefined) {
		periodDays.push(sunday);
	}
	return periodDays;
};

model.loadTimes = function() {
	for(hour = model.timeConfig.start_hour; hour <= model.timeConfig.end_hour; hour++) {
		for (min = 0; min < 60; min = min + model.timeConfig.interval) {
			var hourMinutes = {
				hour: hour,
				min: min,
				name: ' ' + (hour < 10 ? ' ' + hour : hour) + ' h ' + (min < 10 ? '0' + min : min)

			};
			model.times.push(hourMinutes);
		}
	}
};

model.loadPeriods = function() {
	for (occurence = model.periodsConfig.occurences.start; occurence <= model.periodsConfig.occurences.end; occurence = occurence + model.periodsConfig.occurences.interval) {
		model.periods.occurences.push(occurence);
	}
}
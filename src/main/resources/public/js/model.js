// custom loading
loader.loadFile('/rbs/public/js/additional.js');

model.colors = ['cyan', 'green', 'orange', 'pink', 'yellow', 'purple', 'grey'];

model.STATE_CREATED = 1;
model.STATE_VALIDATED = 2;
model.STATE_REFUSED = 3;
model.STATE_PARTIAL = 4;

model.times = [];
model.timeConfig = { // 5min slots from 7h00 to 19h55, default 8h00
	interval: 5, // in minutes
	start_hour: 7,
	end_hour: 19,
	default_hour: 8
};

model.periods = {
	periodicities: [1, 2, 3, 4], // weeks
	days: [
		1, // monday
		2, // tuesday
		3, // wednesday
		4, // thursday
		5, // friday
		6, // saturday
		0 // sunday
	],
	occurrences: [] // loaded by function
};

model.periodsConfig = {
	occurrences: {
		start: 1,
		end: 52,
		interval: 1
	}
};


function Booking() {

}

Booking.prototype.save = function(cb, cbe) {
	if(this.id) {
		this.update(cb, cbe);
	}
	else {
		this.create(cb, cbe);
	}
};

Booking.prototype.calendarUpdate = function(cb, cbe) {
	if(this.id) {
		this.update(function(){
			model.refresh();
		}, function(error){
			// notify
			model.refresh();
		});
	}
	else {
		this.create(function(){
			model.refresh();
		}, function(error){
			// notify
			model.refresh();
		});
	}
};

Booking.prototype.update = function(cb, cbe) {
	var url = '/rbs/resource/' + this.resource.id + '/booking/' + this.id;
	if (this.is_periodic === true) {
		url = url + '/periodic';
	}

	var booking = this;
	http().putJson(url, this)
	.done(function(){
		this.status = model.STATE_CREATED;
		if(typeof cb === 'function'){
			cb();
		}
		this.trigger('change');
	}.bind(this))
	.error(function(e){
		if(typeof cbe === 'function'){
			cbe(model.parseError(e, booking, 'update'));
		}	
	});
};

Booking.prototype.create = function(cb, cbe) {
	var url = '/rbs/resource/' + this.resource.id + '/booking';
	if (this.is_periodic === true) {
		url = url + '/periodic';
	}

	var booking = this;
	http().postJson(url, this)
	.done(function(b){
		booking.updateData(b);

		// Update collections
		if (booking.resource.selected) {
			booking.resource.bookings.push(booking);
		}
		model.bookings.pushAll([booking]);
		if(typeof cb === 'function'){
			cb();
		}
	})
	.error(function(e){
		if(typeof cbe === 'function'){
			cbe(model.parseError(e, booking, 'create'));
		}	
	});
};

Booking.prototype.validate = function(cb, cbe) {
	this.status = model.STATE_VALIDATED;
	var data = {
		status: this.status
	};
	this.process(data, cb, cbe, 'validate');
};

Booking.prototype.refuse = function(cb, cbe) {
	this.status = model.STATE_REFUSED;
	var data = {
		status: this.status,
		refusal_reason: this.refusal_reason
	};
	this.process(data, cb, cbe, 'refuse');
};

Booking.prototype.process = function(data, cb, cbe, context) {
	var booking = this;
	http().putJson('/rbs/resource/' + this.resource.id + '/booking/' + this.id + '/process', data)
	.done(function(){
		if(typeof cb === 'function'){
			cb();
		}
	})
	.error(function(e){
		if(typeof cbe === 'function'){
			cbe(model.parseError(e, booking, context));
		}	
	});
};

Booking.prototype.delete = function(cb, cbe) {
	var booking = this;
	http().delete('/rbs/resource/' + this.resource.id + '/booking/' + this.id)
	.done(function(){
		if(typeof cb === 'function'){
			cb();
		}
	})
	.error(function(e){
		if(typeof cbe === 'function'){
			cbe(model.parseError(e, booking, 'delete'));
		}	
	});
};

Booking.prototype.showSlots = function() {
	this.slots = this._slots;
};

Booking.prototype.selectAllSlots = function() {
	_.each(this._slots, function(slot){
		slot.selected = true;
	});
};

Booking.prototype.deselectAllSlots = function() {
	_.each(this._slots, function(slot){
		slot.selected = undefined;
	});
};

Booking.prototype.hideSlots = function() {
	this.slots = [];
	_.each(this._slots, function(slot){
		slot.selected = undefined;
	});
};

Booking.prototype.isSlot = function() {
	return this.parent_booking_id !== null;
};

Booking.prototype.isBooking = function() {
	return this.parent_booking_id === null;
};

Booking.prototype.isNotPeriodicRoot = function() {
	return this.is_periodic !== true;
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

Booking.prototype.isPartial = function() {
	return this.status === model.STATE_PARTIAL;
};

Booking.prototype.toJSON = function() {
	var json = {};
	if(this.beginning){
		json = {
			start_date : this.beginning.unix(),
			end_date : this.end.unix()
		};
	}
	else{
		json = {
			start_date : this.startMoment.unix(),
			end_date : this.endMoment.unix()
		};
	}


	if (this.is_periodic === true) {
		json.periodicity = this.periodicity;
		json.days = _.pluck(_.sortBy(this.periodDays, function(day){ return day.number; }), 'value');

		if (this.occurrences !== undefined && this.occurrences > 0) {
			json.occurrences = this.occurrences;
		}
		else {
			json.periodic_end_date = this.periodicEndMoment.unix();
		}
	}

	if (_.isString(this.booking_reason)) {
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
				// Load
				this.load(rows);
				// Resource
				var resourceIndex = {};
				resourceIndex[resource.id] = resource;
				// Parse
				var bookingIndex = model.parseBookingsAndSlots(this.all, resourceIndex);
				// Callback
				if(typeof cb === 'function'){
					cb();
				}
			}.bind(this));
		},
		behaviours: 'rbs'
	});
}

Resource.prototype.save = function(cb, cbe) {
	if(this.id) {
		this.update(cb, cbe);
	}
	else {
		this.create(cb, cbe);
	}
};

Resource.prototype.update = function(cb, cbe) {
	var resource = this;
	var originalTypeId = this.type_id;
	this.type_id = this.type.id;

	http().putJson('/rbs/resource/' + this.id, this)
	.done(function(){
		if(typeof cb === 'function'){
			cb();
		}
	})
	.error(function(e){
		if(typeof cbe === 'function'){
			cbe(model.parseError(e, resource, 'update'));
		}	
	});
};

Resource.prototype.create = function(cb, cbe) {
	var resource = this;
	this.type_id = this.type.id;
	this.was_available = undefined;

	http().postJson('/rbs/resources', this)
	.done(function(r){
		// Update collections
		if(typeof cb === 'function'){
			cb();
		}
	})
	.error(function(e){
		if(typeof cbe === 'function'){
			cbe(model.parseError(e, resource, 'create'));
		}	
	});
};

Resource.prototype.delete = function(cb, cbe) {
	var resource = this;

	http().delete('/rbs/resource/' + this.id)
	.done(function(){
		var resourceType = resource.type;
		resourceType.resources.remove(resource);
		if(typeof cb === 'function'){
			cb();
		}
	})
	.error(function(e){
		if(typeof cbe === 'function'){
			cbe(model.parseError(e, resource, 'delete'));
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
	if (this.was_available !== undefined) {
		json.was_available = this.was_available;
	}
	if (_.isString(this.description)) {
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
		filterAvailable: function(periodic) {
			return this.filter(function(resource){
				return resource.is_available === true && (!periodic || resource.periodic_booking);
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

ResourceType.prototype.save = function(cb, cbe) {
	if(this.id) {
		this.update(cb, cbe);
	}
	else {
		this.create(cb, cbe);
	}
};

ResourceType.prototype.update = function(cb, cbe) {
	var resourceType = this;
	http().putJson('/rbs/type/' + this.id, this)
	.done(function(){
		if(typeof cb === 'function'){
			cb();
		}
	})
	.error(function(e){
		if(typeof cbe === 'function'){
			cbe(model.parseError(e, resourceType, 'update'));
		}	
	});
};

ResourceType.prototype.create = function(cb, cbe) {
	var resourceType = this;
	http().postJson('/rbs/type', this)
	.done(function(t){
		resourceType.updateData(t);
		resourceType._id = resourceType.id;

		// Update collections
		model.resourceTypes.push(resourceType);
		if(typeof cb === 'function'){
			cb();
		}
	})
	.error(function(e){
		if(typeof cbe === 'function'){
			cbe(model.parseError(e, resourceType, 'create'));
		}	
	});
};

ResourceType.prototype.delete = function(cb, cbe) {
	var resourceType = this;
	http().delete('/rbs/type/' + this.id)
	.done(function(){
		if(typeof cb === 'function'){
			cb();
		}
	})
	.error(function(e){
		if(typeof cbe === 'function'){
			cbe(model.parseError(e, resourceType, 'delete'));
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


function SelectionHolder() {
}

SelectionHolder.prototype.record = function(resourceTypeCallback, resourceCallback) {
	this.mine = (model.bookings.filters.mine === true ? true : undefined);
	this.unprocessed = (model.bookings.filters.unprocessed === true ? true : undefined);
	this.currentType = ((model.resourceTypes.current !== undefined && model.resourceTypes.current !== null) ? model.resourceTypes.current.id : undefined);

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

SelectionHolder.prototype.restore = function(resourceTypeCallback, resourceCallback) {
	var typeRecords = this.resourceTypes || {};
	var resourceRecords = this.resources || {};
	var holder = this;

	// Apply recorded booking filters
	model.bookings.filters.mine = (this.mine === true ? true : undefined);
	model.bookings.filters.unprocessed = (this.unprocessed === true ? true : undefined);

	model.resourceTypes.forEach(function(resourceType){
		if (typeRecords[resourceType.id] || holder.allResources === true) {
			resourceType.expanded = true;
		}
		if (resourceType.id === holder.currentType) {
			model.resourceTypes.current = resourceType;
			if (typeof resourceTypeCallback === 'function'){
				resourceTypeCallback(resourceType);
			}
		}
		resourceType.resources.forEach(function(resource){
			if (resourceRecords[resource.id] || holder.allResources === true) {
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
	model.me.workflow.load(['rbs']);
	this.makeModels([ResourceType, Resource, Booking, SelectionHolder]);
	Model.prototype.inherits(Booking, calendar.ScheduleItem);

	// ResourceTypes collection with embedded Resources
	this.collection(ResourceType, {
		sync: function(){
			var collection = this;
			// Load the ResourceTypes
			http().get('/rbs/types').done(function(resourceTypes){
				var index = 0;
				// Auto-associate colors to Types
				_.each(resourceTypes, function(resourceType){
					resourceType.color = model.findColor(index);
					resourceType._id = resourceType.id;
					index++;
				});

				// Fill the ResourceType collection and prepare the index
				this.all = [];
				var resourceTypeIndex = {};
				this.addRange(resourceTypes, function(resourceType){
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
							collection.trigger('sync');
						}
					});
				});
				
			}.bind(this));
		},
		filterAvailable: function(periodic) {
			return this.filter(function(resourceType){
				return resourceType.resources.some(function(resource){ 
					return resource.is_available === true && (!periodic || resource.periodic_booking);
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
		selectionForProcess: function() {
			return _.filter(this.selection(), function(booking){
				return booking.isNotPeriodicRoot();
			});
		},
		selectionForDelete: function() {
			return _.filter(this.selection(), function(booking){
				return booking.isBooking();
			});
		},
		selectAllBookings: function() {
			this.forEach(function(booking){
				if (booking.isBooking()) {
					booking.selected = true;
				}
				if (booking.expanded === true) {
					booking.selectAllSlots();
				}
			});
		},
		pushAll: function(datas, trigger) {
			if (datas) {
				this.all = _.union(this.all, datas);
				this.applyFilters();
				if (trigger) {
					this.trigger('sync');
				}
			}
		},
		pullAll: function(datas, trigger) {
			if (datas) {
				this.all = _.difference(this.all, datas);
				this.applyFilters();
				if (trigger) {
					this.trigger('sync');	
				}
			}
		},
		clear: function(trigger) {
			this.all = [];
			this.applyFilters();
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
		applyFilters: function() {
			if (this.filters.booking === true) {
				if (this.filters.dates !== undefined) {
					if (this.filters.mine === true) {
						if (this.filters.unprocessed === true) {
							this.filtered = _.filter(this.all, function(booking){
								return booking.isBooking()
								&& booking.owner === model.me.userId 
								&& (booking.status === model.STATE_CREATED || booking.status === model.STATE_PARTIAL)
								&& ((booking.is_periodic !== true 
										&& booking.startMoment.isBefore(model.bookings.filters.endMoment)
										&& booking.endMoment.isAfter(model.bookings.filters.startMoment))
									|| (booking.is_periodic === true 
										&& booking.startMoment.isBefore(model.bookings.filters.endMoment)
										&& (_.last(booking._slots)).endMoment.isAfter(model.bookings.filters.startMoment)));
							});
						}
						else {
							this.filtered = _.filter(this.all, function(booking){
								return booking.isBooking()
								&& booking.owner === model.me.userId
								&& ((booking.is_periodic !== true 
										&& booking.startMoment.isBefore(model.bookings.filters.endMoment)
										&& booking.endMoment.isAfter(model.bookings.filters.startMoment))
									|| (booking.is_periodic === true 
										&& booking.startMoment.isBefore(model.bookings.filters.endMoment)
										&& (_.last(booking._slots)).endMoment.isAfter(model.bookings.filters.startMoment)));
							});
						}
					}
					else {
						if (this.filters.unprocessed === true) {
							this.filtered = _.filter(this.all, function(booking){
								return booking.isBooking()
								&& (booking.status === model.STATE_CREATED || booking.status === model.STATE_PARTIAL)
								&& ((booking.is_periodic !== true 
										&& booking.startMoment.isBefore(model.bookings.filters.endMoment)
										&& booking.endMoment.isAfter(model.bookings.filters.startMoment))
									|| (booking.is_periodic === true 
										&& booking.startMoment.isBefore(model.bookings.filters.endMoment)
										&& (_.last(booking._slots)).endMoment.isAfter(model.bookings.filters.startMoment)));
							});
						}
						else {
							this.filtered = _.filter(this.all, function(booking){
								return booking.isBooking()
								&& ((booking.is_periodic !== true 
										&& booking.startMoment.isBefore(model.bookings.filters.endMoment)
										&& booking.endMoment.isAfter(model.bookings.filters.startMoment))
									|| (booking.is_periodic === true 
										&& booking.startMoment.isBefore(model.bookings.filters.endMoment)
										&& (_.last(booking._slots)).endMoment.isAfter(model.bookings.filters.startMoment)));
							});
						}				
					}
				}
				else {
					if (this.filters.mine === true) {
						if (this.filters.unprocessed === true) {
							this.filtered = _.filter(this.all, function(booking){
								return booking.isBooking()
								&& booking.owner === model.me.userId 
								&& (booking.status === model.STATE_CREATED || booking.status === model.STATE_PARTIAL);
							});
						}
						else {
							this.filtered = _.filter(this.all, function(booking){
								return booking.isBooking()
								&& booking.owner === model.me.userId;
							});
						}
					}
					else {
						if (this.filters.unprocessed === true) {
							this.filtered = _.filter(this.all, function(booking){
								return booking.isBooking()
								&& (booking.status === model.STATE_CREATED || booking.status === model.STATE_PARTIAL);
							});
						}
						else {
							this.filtered = _.filter(this.all, function(booking){
								return booking.isBooking();
							});
						}				
					}
				}
			}
			else {
				if (this.filters.mine === true) {
					if (this.filters.unprocessed === true) {
						this.filtered = _.filter(this.all, function(booking){
							return booking.owner === model.me.userId 
							&& (booking.status === model.STATE_CREATED || booking.status === model.STATE_PARTIAL);
						});
					}
					else {
						this.filtered = _.filter(this.all, function(booking){
							return booking.owner === model.me.userId;
						});
					}
				}
				else {
					if (this.filters.unprocessed === true) {
						this.filtered = _.filter(this.all, function(booking){
							return (booking.status === model.STATE_CREATED || booking.status === model.STATE_PARTIAL);
						});
					}
					else {
						this.filtered = this.all;
					}				
				}
			}
		},
		filters: {
			mine: undefined,
			unprocessed: undefined,
			booking: undefined,
			dates: undefined,
			startMoment: undefined,
			endMoment: undefined
		},
		filtered: [],
		behavious: 'rbs'
	});

	this.recordedSelections = new SelectionHolder();

	model.loadTimes();
	model.loadPeriods();
};

model.refresh = function() {
	// Record selections
	model.recordedSelections.record();
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
		// Resource
		row.resource = resourceIndex[row.resource_id];

		if (row.parent_booking_id === null) {
			// Is a Booking
			bookingIndex.bookings[row.id] = row;
			model.parseBooking(row, color !== undefined ? color :  row.resource.type.color);
			// Calendar locking
			if (row.owner !== model.me.userId) {
				row.locked = true;	
			}
		}
		else {
			// Is a Slot
			if (bookingIndex.slots[row.parent_booking_id] === undefined) {
				bookingIndex.slots[row.parent_booking_id] = [];
			}
			bookingIndex.slots[row.parent_booking_id].push(row);
			model.parseSlot(row);
			// Calendar locking
			row.locked = true;
		}
	});

	// Link bookings and slots
	_.each(bookingIndex.bookings, function(booking){
		if (booking.is_periodic === true) {
			// Link
			booking._slots = bookingIndex.slots[booking.id] || [];
			// Resolve booking status
			var status = _.countBy(booking._slots, function(slot) {
				// link (here to avoid another loop)
				slot.booking = booking;
				slot.color = booking.color;
				// index status
				return slot.status;
			});
			if (booking._slots.length === status[model.STATE_VALIDATED]) {
				booking.status = model.STATE_VALIDATED;
			}
			else if (booking._slots.length === status[model.STATE_REFUSED]) {
				booking.status = model.STATE_REFUSED;
			}
			else if (booking._slots.length === status[model.STATE_CREATED]) {
				booking.status = model.STATE_CREATED;
			}
			else {
				booking.status = model.STATE_PARTIAL;
			}
		}
	});

	return bookingIndex;
};

model.parseBooking = function(booking, color) {
	booking.color = color;
	booking.startMoment = moment.utc(booking.start_date);
	booking.endMoment = moment.utc(booking.end_date);

	// periodic booking
	if (booking.is_periodic === true) {
		// parse bitmask		
		booking.periodDays = model.bitMaskToDays(booking.days);
		// date if not by occurrences
		if (booking.occurrences === undefined || booking.occurrences < 1) {
			booking.periodicEndMoment =  moment.utc(booking.periodic_end_date);
		}
	}
};

model.parseSlot = function(slot) {
	slot.startMoment = moment.utc(slot.start_date);
	slot.endMoment = moment.utc(slot.end_date);
};

model.bitMaskToDays = function(bitMask) {
	var periodDays = [];
	var bits = [];
	if (bitMask !== undefined) {
		var bits = (bitMask + '').split("");
	}
	_.each(model.periods.days, function(day){
		if (bits[day] === '1') {
			periodDays.push({number: day, value: true});
		}
		else {
			periodDays.push({number: day, value: false});	
		}
	});
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
	for (occurrence = model.periodsConfig.occurrences.start; occurrence <= model.periodsConfig.occurrences.end; occurrence = occurrence + model.periodsConfig.occurrences.interval) {
		model.periods.occurrences.push(occurrence);
	}
}

model.parseError = function(e, object, context) {
	var error = {};
	try {
		error = JSON.parse(e.responseText);
	}
	catch (err) {
		error.error = "rbs.error.unknown";
	}
	error.status = e.status;	
	error.object = object;
	error.context = context;
	return error;
}
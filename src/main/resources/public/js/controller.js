routes.define(function($routeProvider){
    $routeProvider
        .when('/booking/:bookingId', {
            action: 'viewBooking'
        })
});

function RbsController($scope, template, model, date, route){
	
	route({
        viewBooking: function(param){
        	$scope.display.routed = true;
        	$scope.resourceTypes.one('sync', function(){
        		if ($scope.display.routed !== true) {
        			return;
        		}
        		var actions = 0;
        		var routedBooking = undefined;
        		$scope.resourceTypes.forEach(function(resourceType){
        			actions = actions + resourceType.resources.size();
        			resourceType.resources.forEach(function(resource){
        				resource.bookings.one('sync', function(){
        					if (routedBooking !== undefined) {
        						return;
        					}
        					routedBooking = resource.bookings.find(function(booking){
				        		return booking.id == param.bookingId;
				        	});
				        	if (routedBooking !== undefined) {
				        		// found
				        		$scope.viewBooking(routedBooking);
				        		$scope.display.routed = undefined;
				        		return;
				        	}
			        		actions--;
			        		if (actions === 0) {
			        			// error
			        			console.log("Booking not found (id: " + param.bookingId + ")");
			        			notify.error('rbs.route.booking.not.found');
			        			$scope.display.routed = undefined;
			        		}
        				});
        			});
        		});
	        });
        }
    });

	this.initialize = function() {
		$scope.template = template;
		$scope.me = model.me;
		$scope.date = date;

		$scope.display = {
			list: false // calendar by default
		};

		$scope.sort = {
			predicate: 'start_date',
			reverse: false
		}

		$scope.status = {
			STATE_CREATED: model.STATE_CREATED,
			STATE_VALIDATED: model.STATE_VALIDATED,
			STATE_REFUSED: model.STATE_REFUSED,
			STATE_PARTIAL: model.STATE_PARTIAL
		};
		$scope.today = moment().startOf('day');
		$scope.tomorrow = moment().add('day', 1).startOf('day');

		$scope.booking = {};
		$scope.initBookingDates(moment(), moment());

		$scope.resourceTypes = model.resourceTypes;
		$scope.bookings = model.bookings;
		$scope.times = model.times;
		$scope.periods = model.periods;

		$scope.currentResourceType = undefined;
		$scope.selectedBooking = undefined;
		$scope.editedBooking = new Booking();
		$scope.bookings.refuseReason = undefined;
		$scope.processBookings = [];
		$scope.currentErrors = [];

		template.open('main', 'main-view');
		template.open('bookings', 'main-calendar');

		// Will auto-select "Mine" bookings by default
		model.bookings.filters.mine = true;
		model.recordedSelections.mine = true;
		model.recordedSelections.allResources = true;
		model.bookings.filters.dates = true;
		model.bookings.filters.startMoment = moment().startOf('day');
		model.bookings.filters.endMoment = moment().add('month', 2).startOf('day');
		model.bookings.filters.startDate = model.bookings.filters.startMoment.toDate();
		model.bookings.filters.endDate = model.bookings.filters.endMoment.toDate();

		$scope.resourceTypes.on('sync', function(){
			// Restore previous selections
			model.recordedSelections.restore(
				function(resourceType){
					$scope.currentResourceType = resourceType;
				},
				function(resource){
					resource.bookings.sync(function(){
						$scope.bookings.pushAll(resource.bookings.all);
						// route
					});
				}
			);
			model.recordedSelections.allResources = false;
		});
	};

	// Navigation
	$scope.showList = function(refresh) {
		if (refresh === true) {
			$scope.initMain();
		}
		$scope.display.list = true;
		$scope.bookings.filters.booking = true;
		$scope.bookings.applyFilters();
		template.open('bookings', 'main-list');	
	};

	$scope.showCalendar = function(refresh) {
		if (refresh === true) {
			$scope.initMain();
		}
		$scope.display.list = false;
		$scope.bookings.filters.booking = undefined;
		$scope.bookings.applyFilters();
		template.open('bookings', 'main-calendar');
	};

	$scope.showManage = function() {
		$scope.currentResourceType = model.resourceTypes.first();
		$scope.resourceTypes.deselectAllResources();
		template.open('main', 'manage-view');
		template.open('resources', 'manage-resources');
	};

	$scope.initMain = function() {
		model.recordedSelections.allResources = true;
		$scope.currentResourceType = undefined;
		$scope.resetSort();
		model.refresh();
		template.open('main', 'main-view');
	};


	// Main view interaction
	$scope.switchExpand = function(resourceType) {
		if (resourceType.expanded !== true) {
			resourceType.expanded = true;
		}
		else {
			resourceType.expanded = undefined;
		}
	};

	$scope.switchSelect = function(resource) {
		if (resource.selected !== true) {
			resource.selected = true;
			if (resource.is_available !== true) {
				$scope.lastSelectedResource = resource;	
			}
			resource.bookings.sync(function(){
				$scope.bookings.pushAll(resource.bookings.all);
			});
		}
		else {
			resource.selected = undefined;
			if (resource.is_available !== true) {
				$scope.lastSelectedResource = undefined;
			}
			$scope.bookings.pullAll(resource.bookings.all);
		}
	};

	$scope.switchSelectMine = function() {
		if ($scope.bookings.filters.mine === true) {
			delete $scope.bookings.filters.mine;
		}
		else {
			$scope.bookings.filters.mine = true;
			delete $scope.bookings.filters.unprocessed;
		}
		$scope.bookings.applyFilters();
	};

	$scope.switchSelectUnprocessed = function() {
		if ($scope.bookings.filters.unprocessed === true) {
			delete $scope.bookings.filters.unprocessed;
		}
		else {
			$scope.bookings.filters.unprocessed = true;
			delete $scope.bookings.filters.mine;
		}
		$scope.bookings.applyFilters();
	}


	// Bookings
	$scope.viewBooking = function(booking) {
		if (booking.isSlot()) {
			// slot : view booking details and show slot
			$scope.selectedBooking = booking.booking;
			$scope.selectedBooking.displaySection = 3;
			if (booking.status === $scope.status.STATE_REFUSED) {
				booking.expanded = true;
			}
		}
		else {
			// booking
			$scope.selectedBooking = booking;
			$scope.selectedBooking.displaySection = 1;
		}

		template.open('lightbox', 'booking-details');
		$scope.display.showPanel = true;
	};

	$scope.closeBooking = function() {
		if ($scope.selectedBooking !== undefined && $scope.selectedBooking.is_periodic === true) {
			_.each($scope.selectedBooking._slots, function(slot){
				slot.expanded = false;
			});
		}
		$scope.selectedBooking = undefined;
		$scope.editedBooking = new Booking();
		$scope.processBookings = [];
		$scope.currentErrors = [];
		$scope.display.showPanel = false;
		template.close('lightbox');
	};

	$scope.expandPeriodicBooking = function(booking) {
		booking.expanded = true;
		booking.showSlots();
	};

	$scope.collapsePeriodicBooking = function(booking) {
		booking.expanded = undefined;
		booking.hideSlots();
	};

	$scope.switchSelectAllBookings = function() {
		if ($scope.display.selectAllBookings) {
			$scope.bookings.selectAllBookings();
		}
		else {
			$scope.bookings.deselectAll();
		}
	};

	$scope.switchSelectAllSlots = function(booking) {
		if (booking.is_periodic === true && booking.selected === true) {
			_.each(booking._slots, function(slot){
				slot.selected = true;
			});
		}
		else if (booking.is_periodic === true && booking.selected !== true) {
			_.each(booking._slots, function(slot){
				slot.selected = undefined;
			});
		}
	};

	$scope.switchExpandSlot = function(slot) {
		if (slot.expanded !== true) {
			slot.expanded = true;
		}
		else {
			slot.expanded = undefined;
		}
	};

	// Sort
	$scope.switchSortBy = function(predicate) {
		if (predicate === $scope.sort.predicate) {
			$scope.sort.reverse = ! $scope.sort.reverse;
		}
		else {
			$scope.sort.predicate = predicate;
			$scope.sort.reverse = false;
		}
	};

	$scope.resetSort = function() {
		$scope.sort.predicate = 'start_date';
		$scope.sort.reverse = false;
	}

	$scope.filterListByDates = function(filter) {
		if (filter === true) {
			$scope.bookings.filters.startMoment = moment($scope.bookings.filters.startDate);
			$scope.bookings.filters.endMoment = moment($scope.bookings.filters.endDate);
			$scope.bookings.filters.dates = true;
		}
		else {
			$scope.bookings.filters.dates = undefined;
		}
		$scope.bookings.applyFilters();
		$scope.bookings.trigger('change');
	};


	// General
	$scope.formatDate = function(date) {
		return $scope.formatMoment(moment(date));
	};

	$scope.formatDateLong = function(date) {
		return $scope.formatMomentLong(moment(date));
	};

	$scope.formatMoment = function(date) {
		return date.format('DD/MM/YYYY à H[h]mm');
	};

	$scope.formatMomentLong = function(date) {
		return date.format('dddd DD MMMM YYYY - HH[h]mm');
	};

	$scope.formatMomentDayLong = function(date) {
		return date.format('dddd DD MMMM YYYY');	
	};

	$scope.trimReason = function(reason) {
		return _.isString(reason) ? (reason.trim().length > 15 ? reason.substring(0, 12) + '...' : reason.trim()) : "";
	};

	$scope.countValidatedSlots = function(slots) {
		return _.filter(slots, function(slot) { return slot.isValidated(); }).length;
	};

	$scope.countRefusedSlots = function(slots) {
		return _.filter(slots, function(slot) { return slot.isRefused(); }).length;
	};


	// Booking edition
	$scope.canEditBookingSelection = function() {
		if ($scope.display.list === true) {
			var localSelection = _.filter($scope.bookings.selection(), function(booking) { return booking.isBooking(); });
			return (localSelection.length === 1 && localSelection[0].owner === model.me.userId && localSelection[0].resource.is_available === true);
		}
		else {
			$scope.bookings.selection().length === 1 && $scope.bookings.selection()[0].owner === model.me.userId && $scope.bookings.selection()[0].resource.is_available === true;
		}
	};

	$scope.canDeleteBookingSelection = function() {
		return _.every($scope.bookings.selection(), function(booking){ 
			return booking.isBooking() || (booking.isSlot() && booking.booking.selected === true); 
		});
	};

	$scope.newBooking = function(periodic) {
		$scope.display.processing = undefined;
		$scope.editedBooking = new Booking();
		$scope.editedBooking.showResource = true;

		// periodic booking
		$scope.editedBooking.is_periodic = false; // false by default
		if(periodic === 'periodic'){
			$scope.editedBooking.is_periodic = true;
			$scope.editedBooking.periodDays = model.bitMaskToDays(); // no days selected
			$scope.editedBooking.byOccurrences = true;
			$scope.editedBooking.periodicity = 1;
			$scope.editedBooking.occurrences = 1;
		}

		// resource
		if ($scope.lastSelectedResource) {
			$scope.editedBooking.resource = $scope.lastSelectedResource;
			$scope.editedBooking.type = $scope.lastSelectedResource.type;
		}
		else {
			$scope.editedBooking.type = _.first($scope.resourceTypes.filterAvailable());
			$scope.autoSelectResource();
		}

		// dates
		$scope.editedBooking.startMoment = moment();
		$scope.editedBooking.endMoment = moment();
		$scope.editedBooking.endMoment.hour($scope.editedBooking.startMoment.hour() + 1);
		// DEBUG
		var DEBUG_editedBooking = $scope.editedBooking;
		// /DEBUG
		$scope.initBookingDates($scope.editedBooking.startMoment, $scope.editedBooking.endMoment);

		template.open('lightbox', 'edit-booking');
		$scope.display.showPanel = true;
	};

	$scope.newBookingCalendar = function() {
		$scope.display.processing = undefined;
		$scope.editedBooking = new Booking();
		$scope.editedBooking.showResource = true;

		// resource
		if ($scope.lastSelectedResource) {
			$scope.editedBooking.resource = $scope.lastSelectedResource;
			$scope.editedBooking.type = $scope.lastSelectedResource.type;
		}
		else {
			$scope.editedBooking.type = _.first($scope.resourceTypes.filterAvailable());
			$scope.autoSelectResource();
		}

		// dates
		if (model.calendar.newItem !== undefined) {
			$scope.editedBooking.startMoment = model.calendar.newItem.beginning;
			$scope.editedBooking.startMoment.minutes(0);
			$scope.editedBooking.endMoment = model.calendar.newItem.end;
			$scope.editedBooking.endMoment.minutes(0);
		}
		else {
			$scope.editedBooking.startMoment = moment();
			$scope.editedBooking.endMoment = moment();
			$scope.editedBooking.endMoment.hour($scope.editedBooking.startMoment.hour() + 1);
		}
		// DEBUG
		var DEBUG_editedBooking = $scope.editedBooking;
		// /DEBUG
		$scope.initBookingDates($scope.editedBooking.startMoment, $scope.editedBooking.endMoment);
		$scope.$apply('editedBooking')
	};

	$scope.editBooking = function() {
		$scope.display.processing = undefined;
		$scope.currentErrors = [];

		if ($scope.selectedBooking !== undefined) {
			$scope.editedBooking = $scope.selectedBooking;
		}
		else {
			$scope.editedBooking = $scope.bookings.selection()[0];
			if (! $scope.editedBooking.isBooking()) {
				$scope.editedBooking = $scope.editedBooking.booking;
			}
		}
		$scope.editedBooking.showResource = true;

		// periodic booking
		if ($scope.editedBooking.is_periodic === true) {			
			if ($scope.editedBooking.occurrences !== undefined && $scope.editedBooking.occurrences > 0) {
				$scope.editedBooking.byOccurrences = true;
			}
			else {
				$scope.editedBooking.byOccurrences = false;
			}
		}
		// DEBUG
		var DEBUG_editedBooking = $scope.editedBooking;
		// /DEBUG
		$scope.initBookingDates($scope.editedBooking.startMoment, $scope.editedBooking.endMoment);

		template.open('lightbox', 'edit-booking');
		$scope.display.showPanel = true;
	};

	$scope.initBookingDates = function(startMoment, endMoment) {
		// DEBUG
		var DEBUG_booking = $scope.booking;
		// /DEBUG

		// hours minutes management
		$scope.booking.startTime = _.find(model.times, function(hourMinutes) { 
			return (startMoment.hour() <= hourMinutes.hour && startMoment.minutes() <= hourMinutes.min) });
		$scope.booking.endTime = _.find(model.times, function(hourMinutes) { 
			return (endMoment.hour() <= hourMinutes.hour && endMoment.minutes() <= hourMinutes.min) });

		if ($scope.booking.startTime === undefined || $scope.booking.endTime === undefined) {
			// hour slot does not fit today, set to first slot tomorrow
			startMoment.add('day', 1);
			endMoment.add('day', 1);
			$scope.booking.startTime = model.times[0];
			$scope.booking.endTime = _.find(model.times, function(hourMinutes) { 
				return ($scope.booking.startTime.hour < hourMinutes.hour) });
		}

		// dates management
		$scope.booking.startDate = startMoment.toDate();
		$scope.booking.startDate.setFullYear(startMoment.years());
		$scope.booking.startDate.setMonth(startMoment.months());
		$scope.booking.startDate.setDate(startMoment.date());
		$scope.booking.endDate = endMoment.toDate();
		$scope.booking.endDate.setFullYear(endMoment.years());
		$scope.booking.endDate.setMonth(endMoment.months());
		$scope.booking.endDate.setDate(endMoment.date());
		$scope.booking.periodicEndDate = endMoment.toDate();
		$scope.booking.endDate.setFullYear(endMoment.years());
		$scope.booking.endDate.setMonth(endMoment.months());
		$scope.booking.endDate.setDate(endMoment.date());

		// DEBUG
		var DEBUG_booking = $scope.booking;
		// /DEBUG
	};

	$scope.adjustStartTime = function() {
		if (($scope.editedBooking.is_periodic !== true 
				&& $scope.booking.endDate.getFullYear() == $scope.booking.startDate.getFullYear()
				&& $scope.booking.endDate.getMonth() == $scope.booking.startDate.getMonth()
				&& $scope.booking.endDate.getDate() == $scope.booking.startDate.getDate())
			|| ($scope.editedBooking.is_periodic === true && $scope.editedBooking.byOccurrences !== true
				&& $scope.booking.periodicEndDate.getFullYear() == $scope.booking.startDate.getFullYear()
				&& $scope.booking.periodicEndDate.getMonth() == $scope.booking.startDate.getMonth()
				&& $scope.booking.periodicEndDate.getDate() == $scope.booking.startDate.getDate())
			) {
			if ($scope.booking.endTime.hour <= $scope.booking.startTime.hour) {
				// next hour
				var endTime = _.find(model.times, function(hourMinutes) { 
					return ($scope.booking.startTime.hour < hourMinutes.hour && $scope.booking.startTime.min == hourMinutes.min) });
				$scope.booking.endTime = (endTime !== undefined ? endTime : $scope.booking.endTime = _.last(model.times));
			}
		}
	};

	$scope.adjustEndTime = function() {
		if (($scope.editedBooking.is_periodic !== true 
				&& $scope.booking.endDate.getFullYear() == $scope.booking.startDate.getFullYear()
				&& $scope.booking.endDate.getMonth() == $scope.booking.startDate.getMonth()
				&& $scope.booking.endDate.getDate() == $scope.booking.startDate.getDate())
			|| ($scope.editedBooking.is_periodic === true && $scope.editedBooking.byOccurrences !== true
				&& $scope.booking.periodicEndDate.getFullYear() == $scope.booking.startDate.getFullYear()
				&& $scope.booking.periodicEndDate.getMonth() == $scope.booking.startDate.getMonth()
				&& $scope.booking.periodicEndDate.getDate() == $scope.booking.startDate.getDate())
			) {
			if ($scope.booking.endTime.hour <= $scope.booking.startTime.hour) {
				// next hour
				var endTime = _.find(model.times, function(hourMinutes) { 
					return ($scope.booking.startTime.hour == hourMinutes.hour && $scope.booking.startTime.min < hourMinutes.min) });
				$scope.booking.endTime = (endTime !== undefined ? endTime : $scope.booking.endTime = _.last(model.times));
			}
		}
	};


	$scope.autoSelectResource = function() {
		$scope.editedBooking.resource = _.first($scope.editedBooking.type.resources.filterAvailable($scope.editedBooking.is_periodic));
	};

	$scope.saveBooking = function() {
		// Check
		$scope.currentErrors = [];
		if ($scope.checkSaveBooking()) {
			return;
		}

		// Save
		$scope.display.processing = true;
		
		// dates management
		$scope.editedBooking.startMoment = moment.utc([
			$scope.booking.startDate.getFullYear(),
			$scope.booking.startDate.getMonth(),
			$scope.booking.startDate.getDate(),
			$scope.booking.startTime.hour,
			$scope.booking.startTime.min]);

		if ($scope.editedBooking.is_periodic === true) {
			// periodic booking
			$scope.editedBooking.endMoment = moment.utc([
				$scope.booking.startDate.getFullYear(),
				$scope.booking.startDate.getMonth(),
				$scope.booking.startDate.getDate(),
				$scope.booking.endTime.hour,
				$scope.booking.endTime.min]);
			if ($scope.editedBooking.byOccurrences !== true) {
				$scope.editedBooking.occurrences = undefined;
				$scope.editedBooking.periodicEndMoment = moment.utc([
					$scope.booking.periodicEndDate.getFullYear(),
					$scope.booking.periodicEndDate.getMonth(),
					$scope.booking.periodicEndDate.getDate(),
					$scope.booking.endTime.hour,
					$scope.booking.endTime.min]);
			}
			$scope.resolvePeriodicMoments();
		}
		else {
			// non periodic
			$scope.editedBooking.endMoment = moment.utc([
				$scope.booking.endDate.getFullYear(),
				$scope.booking.endDate.getMonth(),
				$scope.booking.endDate.getDate(),
				$scope.booking.endTime.hour,
				$scope.booking.endTime.min]);		
		}

		$scope.editedBooking.save(function(){
			$scope.display.processing = undefined;
			$scope.$apply('editedBooking')
			$scope.closeBooking();
			model.refresh();
		}, function(e){
			$scope.display.processing = undefined;
			$scope.currentErrors.push(e);
			$scope.$apply('editedBooking');
		});
	};

	$scope.checkSaveBooking = function() {
		var hasErrors = false;
		if (($scope.booking.startDate.getFullYear() < $scope.today.year())
			|| ($scope.booking.startDate.getFullYear() == $scope.today.year() && $scope.booking.startDate.getMonth() < $scope.today.month())
			|| ($scope.booking.startDate.getFullYear() == $scope.today.year() && $scope.booking.startDate.getMonth() == $scope.today.month() && $scope.booking.startDate.getDate() < $scope.today.date())
			|| ($scope.booking.startDate.getFullYear() == $scope.today.year() && $scope.booking.startDate.getMonth() == $scope.today.month() && $scope.booking.startDate.getDate() == $scope.today.date() && $scope.booking.startTime.hour < moment().hour())) {
			$scope.currentErrors.push({error: 'rbs.booking.invalid.datetimes.past'});
			hasErrors = true;
		}
		if ($scope.editedBooking.is_periodic === true
			&& _.find($scope.editedBooking.periodDays, function(periodDay) { return periodDay.value === true; }) === undefined) {
			// Error
			$scope.currentErrors.push({error: 'rbs.booking.missing.days'});
			hasErrors = true;
		}
		return hasErrors;
	};

	$scope.resolvePeriodicMoments = function() {
		// find next selected day as real start date
		var selectedDays = _.groupBy(_.filter($scope.editedBooking.periodDays, function(periodDay){
			return periodDay.value === true;
		}), function(periodDay){
			return periodDay.number;
		});

		if (selectedDays[$scope.editedBooking.startMoment.day()] === undefined) {
			// search the next following day (higher number)
			for (var i = $scope.editedBooking.startMoment.day(); i < 7; i++){
				if (selectedDays[i] !== undefined) {
					$scope.editedBooking.startMoment = $scope.editedBooking.startMoment.day(i);
					$scope.editedBooking.endMoment = $scope.editedBooking.endMoment.day(i);
					return;
				}
			}
			// search the next following day (lower number)
			for (var i = 0; i < $scope.editedBooking.startMoment.day(); i++){
				if (selectedDays[i] !== undefined) {
					$scope.editedBooking.startMoment = $scope.editedBooking.startMoment.day(i + 7); // +7 for days in next week, not current
					$scope.editedBooking.endMoment = $scope.editedBooking.endMoment.day(i + 7);
					return;
				}
			}
		}
		// nothing to do
	};

	$scope.toggleShowBookingResource = function() {
		if ($scope.editedBooking.showResource == true) {
			$scope.editedBooking.showResource = undefined;
		}
		else {
			$scope.editedBooking.showResource = true;
		}

	};

	$scope.removeBookingSelection = function() {
		$scope.display.processing = undefined;
		if ($scope.selectedBooking !== undefined) {
			$scope.bookings.deselectAll();
			$scope.selectedBooking.selected = true;
		}

		// confirm message
		$scope.processBookings = $scope.bookings.selectionForDelete();
		template.open('lightbox', 'confirm-delete-booking');
		$scope.display.showPanel = true;
	};

	$scope.doRemoveBookingSelection = function() {
		$scope.display.processing = true;
		$scope.currentErrors = [];
		var actions = $scope.processBookings.length;
		_.each($scope.processBookings, function(booking){
			booking.delete(function(){
				actions--;
				if (actions === 0) {
					$scope.display.processing = undefined;
					$scope.bookings.deselectAll();
					$scope.closeBooking();
					model.refresh();
				}
			}, function(e){
				$scope.currentErrors.push(e);
				actions--;
				if (actions === 0) {
					$scope.display.processing = undefined;
					$scope.showActionErrors()
					model.refresh();
				}
			});
		});
	};


	// Booking Validation
	$scope.canProcessBookingSelection = function() {
		return _.every($scope.bookings.selection(), function(booking){ return booking.isPending(); });
	};

	$scope.validateBookingSelection = function() {
		$scope.display.processing = undefined;
		if ($scope.selectedBooking !== undefined) {
			$scope.bookings.deselectAll();
			if ($scope.selectedBooking.is_periodic === true) {
				$scope.selectedBooking.selectAllSlots();
				$scope.selectedBooking.selected = undefined;
			}
			else {
				$scope.selectedBooking.selected = true;
			}
		}

		$scope.processBookings = $scope.bookings.selectionForProcess();
		$scope.display.showPanel = true;
		template.open('lightbox', 'validate-booking');
	};

	$scope.refuseBookingSelection = function() {
		$scope.display.processing = undefined;
		if ($scope.selectedBooking !== undefined) {
			$scope.bookings.deselectAll();
			if ($scope.selectedBooking.is_periodic === true) {
				$scope.selectedBooking.selectAllSlots();
				$scope.selectedBooking.selected = undefined;
			}
			else {
				$scope.selectedBooking.selected = true;
			}
		}

		$scope.processBookings = $scope.bookings.selectionForProcess();
		$scope.display.showPanel = true;
		$scope.bookings.refuseReason = "";
		template.open('lightbox', 'refuse-booking');
	};

	$scope.doValidateBookingSelection = function() {
		$scope.display.processing = true;
		$scope.currentErrors = [];
		var actions = $scope.processBookings.length;
		_.each($scope.processBookings, function(booking){
			booking.validate(function(){
				actions--;
				if (actions === 0) {
					$scope.display.processing = undefined;
					$scope.bookings.deselectAll();
					$scope.closeBooking();
					model.refresh();
				}
			}, function(e){
				$scope.currentErrors.push(e);
				actions--;
				if (actions === 0) {
					$scope.display.processing = undefined;
					$scope.showActionErrors();
					model.refresh();
				}
			});
		});
	};

	$scope.doRefuseBookingSelection = function() {
		$scope.display.processing = true;
		$scope.currentErrors = [];
		var actions = $scope.processBookings.length;
		_.each($scope.processBookings, function(booking){
			booking.refusal_reason = $scope.bookings.refuseReason;
			booking.refuse(function(){
				actions--;
				if (actions === 0) {
					$scope.display.processing = undefined;
					$scope.bookings.deselectAll();
					$scope.bookings.refuseReason = undefined;
					$scope.closeBooking();
					model.refresh();
				}
			}, function(e){
				$scope.currentErrors.push(e);
				actions--;
				if (actions === 0) {
					$scope.display.processing = undefined;
					$scope.showActionErrors();
					model.refresh();
				}
			});
		});
	};


	// Management view interaction
	$scope.selectResourceType = function(resourceType) {
		$scope.currentResourceType.resources.deselectAll();
		$scope.currentResourceType.resources.collapseAll();
		$scope.currentResourceType = resourceType;
		$scope.resourceTypes.current = resourceType;
		if ($scope.editedResourceType !== undefined) {
			$scope.closeResourceType();
		}
	};

	$scope.swicthSelectAllRessources = function() {
		if ($scope.display.selectAllRessources) {
			$scope.currentResourceType.resources.selectAll();
		}
		else {
			$scope.currentResourceType.resources.deselectAll();
		}
	};

	$scope.switchExpandResource = function(resource) {
		if (resource.expanded === true) {
			resource.expanded = undefined;
		}
		else {
			resource.expanded = true;
		}
	}

	// Management view edition
	$scope.newResourceType = function() {
		$scope.display.processing = undefined;
		$scope.editedResourceType = new ResourceType();
		$scope.editedResourceType.validation = false;
		template.open('resources', 'edit-resource-type');
	};

	$scope.newResource = function() {
		$scope.display.processing = undefined;
		$scope.editedResource = new Resource();
		$scope.editedResource.type = $scope.currentResourceType;
		$scope.editedResource.is_available = true;
		$scope.editedResource.periodic_booking = true;
		template.open('resources', 'edit-resource');
	};

	$scope.editCurrentResourceType = function() {
		$scope.display.processing = undefined;
		$scope.editedResourceType = $scope.currentResourceType;
		template.open('resources', 'edit-resource-type');
	};

	$scope.editSelectedResource = function() {
		$scope.display.processing = undefined;
		$scope.editedResource = $scope.currentResourceType.resources.selection()[0];
		$scope.currentResourceType.resources.deselectAll();
		
		// Field to track Resource availability change
		if ($scope.editedResource.was_available === undefined) {
			$scope.editedResource.was_available = $scope.editedResource.is_available;
		}
		template.open('resources', 'edit-resource');
	};

	$scope.shareCurrentResourceType = function() {
		$scope.display.showPanel = true;
	};

	$scope.saveResourceType = function() {
		// Default to user's school UAI or 'null'
		if ($scope.editedResourceType.school_id === undefined) {
			$scope.editedResourceType.school_id = ((model.me.uai !== undefined && model.me.uai !== null) ? model.me.uai : 'null');
		}

		$scope.display.processing = true;
		$scope.currentErrors = [];
		$scope.editedResourceType.save(function(){
			$scope.display.processing = undefined;
			$scope.currentResourceType = $scope.editedResourceType;
			$scope.closeResourceType();
			model.refresh();
		}, function(e){
			$scope.currentErrors.push(e);
			$scope.display.processing = undefined;
			$scope.$apply();
		});
	};

	$scope.saveResource = function() {
		$scope.display.processing = true;
		if ($scope.editedResource.is_available === "true") {
			$scope.editedResource.is_available = true;
		}
		else if ($scope.editedResource.is_available === "false") {
			$scope.editedResource.is_available = false;
		}

		$scope.currentErrors = [];
		$scope.editedResource.save(function(){
			$scope.display.processing = undefined;
			$scope.closeResource();
			model.refresh();
		}, function(e){
			$scope.currentErrors.push(e);
			$scope.display.processing = undefined;
			$scope.$apply();
		});
	};

	$scope.deleteCurrentResourceType = function() {
		$scope.editedResourceType = $scope.currentResourceType;
		template.open('resources', 'confirm-delete-type');
	};

	$scope.deleteResourcesSelection = function() {
		$scope.currentResourceType.resourcesToDelete = $scope.currentResourceType.resources.selection();
		$scope.currentResourceType.resources.deselectAll();
		template.open('resources', 'confirm-delete-resource');
	};

	$scope.doDeleteResourceType = function() {
		$scope.display.processing = true;
		$scope.currentErrors = [];
		$scope.editedResourceType.delete(function(){
			$scope.display.processing = undefined;
			$scope.resourceTypes.remove($scope.editedResourceType);
			$scope.currentResourceType = $scope.resourceTypes.first();
			$scope.closeResourceType();
			model.refresh();
		}, function(e){
			$scope.currentErrors.push(e);
			$scope.display.processing = undefined;
		});
	};

	$scope.doDeleteResource = function() {
		$scope.display.processing = true;
		$scope.currentErrors = [];
		var actions = $scope.currentResourceType.resourcesToDelete.length;
		_.each($scope.currentResourceType.resourcesToDelete, function(resource){
			resource.delete(function(){
				actions--;
				if (actions === 0) {
					$scope.display.processing = undefined;
					$scope.closeResource();
					model.refresh();
				}
			}, function(e){
				$scope.currentErrors.push(e);
				actions--;
				if (actions === 0) {
					$scope.display.processing = undefined;
					$scope.showActionErrors();
					model.refresh();
				}
			});
		});
	};

	$scope.closeResourceType = function() {
		$scope.editedResourceType = undefined;
		$scope.currentErrors = [];
		if ($scope.display.showPanel === true) {
			$scope.display.showPanel = false;
			template.close('lightbox');
		}
		template.open('resources', 'manage-resources');
	};

	$scope.closeResource = function() {
		$scope.editedResource = undefined;
		$scope.currentErrors = [];
		if ($scope.display.showPanel === true) {
			$scope.display.showPanel = false;
			template.close('lightbox');
		}
		template.open('resources', 'manage-resources');
	};

	// Errors
	$scope.showActionErrors = function() {
		$scope.display.showPanel = true;
		template.open('lightbox', 'action-errors');
	};

	$scope.isErrorObjectResourceType = function(object) {
		return object instanceof ResourceType;
	};

	$scope.isErrorObjectResource = function(object) {
		return object instanceof Resource;
	};

	$scope.isErrorObjectBooking = function(object) {
		return object instanceof Booking;
	};

	$scope.closeActionErrors = function() {
		$scope.display.showPanel = false;
		template.close('lightbox');
	};

	// Special Workflow and Behaviours
	$scope.hasWorkflowOrAnyResourceHasBehaviour = function(workflowRight, ressourceRight) {
		var workflowRights = workflowRight.split('.');
		return (model.me.workflow[workflowRights[0]] !== undefined && model.me.workflow[workflowRights[0]][workflowRights[1]] === true)
			|| model.resourceTypes.find(function(resourceType){
				return (resourceType.resources.find(function(resource){
					return resource.myRights !== undefined && resource.myRights[ressourceRight] !== undefined;
				}) !== undefined);
			});
	};

	this.initialize();
}
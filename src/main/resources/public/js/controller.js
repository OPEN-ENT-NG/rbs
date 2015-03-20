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
        		$scope.initResourcesRouted(param.bookingId);
	        });
        }
    });

	this.initialize = function() {
		$scope.template = template;
		$scope.me = model.me;
		$scope.date = date;

		$scope.display = {
			list: false, // calendar by default
			create: false
		};

		$scope.sort = {
			predicate: 'start_date',
			reverse: false
		}
		
		// Used to display types for moderators (moderators of a type can update a resource, but cannot create one)
		$scope.keepProcessableResourceTypes = function(type) {
			return (type.myRights && type.myRights.process);
		};

		// Used to display types in which current user can create a resource
		$scope.keepManageableResourceTypes = function(type) {
			return (type.myRights && type.myRights.manage);
		};

		$scope.status = {
			STATE_CREATED: model.STATE_CREATED,
			STATE_VALIDATED: model.STATE_VALIDATED,
			STATE_REFUSED: model.STATE_REFUSED,
			STATE_SUSPENDED: model.STATE_SUSPENDED,
			STATE_PARTIAL: model.STATE_PARTIAL
		};
		$scope.today = moment().startOf('day');
		$scope.tomorrow = moment().add('day', 1).startOf('day');

		$scope.booking = {};
		$scope.initBookingDates(moment(), moment());

		$scope.resourceTypes = model.resourceTypes;
		$scope.bookings = model.bookings;
		$scope.periods = model.periods;
		$scope.structures = model.structures;

		$scope.currentResourceType = undefined;
		$scope.selectedBooking = undefined;
		$scope.editedBooking = new Booking();
		$scope.bookings.refuseReason = undefined;
		$scope.processBookings = [];
		$scope.currentErrors = [];

		template.open('main', 'main-view');
		template.open('top-menu', 'top-menu');
		template.open('editBookingErrors', 'edit-booking-errors');

		// Will auto-select all resources and "Mine" bookings filter by default
		//model.bookings.filters.mine = true;
		//model.recordedSelections.allResources = true;
		//model.recordedSelections.mine = true;

		// Will auto-select first resourceType resources and no filter by default
		model.recordedSelections.firstResourceType = true;

		model.bookings.filters.dates = true;
		model.bookings.filters.startMoment = moment().startOf('day');
		model.bookings.filters.endMoment = moment().add('month', 2).startOf('day');
		model.bookings.filters.startDate = model.bookings.filters.startMoment.toDate();
		model.bookings.filters.endDate = model.bookings.filters.endMoment.toDate();

		$scope.resourceTypes.on('sync', function(){
			// check create booking rights
			$scope.display.create = $scope.canCreateBooking();

			if($scope.display.list === true) {
				template.open('bookings', 'main-list');
			}
			else {
				template.open('bookings', 'main-calendar');
			}

			// Do not restore if routed
			if ($scope.display.routed === true) {
				return;
			}

			$scope.initResources();
		});
	};

	// Initialization
	$scope.initResources = function() {
		var remanentBookingId = ($scope.selectedBooking !== undefined ? $scope.selectedBooking.id : undefined);
		var remanentBooking = undefined;
		// Restore previous selections
		model.recordedSelections.restore(
			function(resourceType){
				$scope.currentResourceType = resourceType;
			},
			function(resource){
				resource.bookings.sync(function(){
					$scope.bookings.pushAll(resource.bookings.all);
					if (remanentBookingId !== undefined) {
						remanentBooking = resource.bookings.find(function(booking){
							return booking.id === remanentBookingId;
						});
						if (remanentBooking !== undefined) {
							$scope.viewBooking(remanentBooking);
						}
					}
				});
			}
		);
		model.recordedSelections.allResources = false;
	}

	$scope.initResourcesRouted = function(bookingId) {
		var actions = 0;
		var routedBooking = undefined;
		$scope.resourceTypes.forEach(function(resourceType){
			actions = actions + resourceType.resources.size();
			resourceType.resources.forEach(function(resource){
				resource.bookings.sync();
				resource.bookings.one('sync', function(){
					if (routedBooking !== undefined) {
						return;
					}
					routedBooking = resource.bookings.find(function(booking){
		        		return booking.id == bookingId;
		        	});
		        	if (routedBooking !== undefined) {
		        		// found
		        		$scope.bookings.pushAll(routedBooking.resource.bookings.all);
		        		routedBooking.resource.type.expanded = true;
		        		routedBooking.resource.selected = true;
		        		$scope.viewBooking(routedBooking);
		        		$scope.display.routed = undefined;
		        		model.recordedSelections.firstResourceType = undefined;
		        		return;
		        	}
	        		actions--;
	        		if (actions === 0) {
	        			// error
	        			console.log("Booking not found (id: " + bookingId + ")");
	        			notify.error('rbs.route.booking.not.found');
	        			$scope.display.routed = undefined;
	        			$scope.initResources();
	        		}
				});
			});
		});
	}

	// Navigation
	$scope.showList = function(refresh) {
		if (refresh === true) {
			$scope.initMain();
		}
		$scope.display.admin = false;
		$scope.display.list = true;
		$scope.bookings.filters.booking = true;
		$scope.bookings.applyFilters();
		template.open('bookings', 'main-list');	
	};

	$scope.showCalendar = function(refresh) {
		if (refresh === true) {
			$scope.initMain();
		}
		$scope.display.admin = false;
		$scope.display.list = false;
		$scope.bookings.filters.booking = undefined;
		$scope.bookings.applyFilters();
		template.open('bookings', 'main-calendar');
	};

	$scope.showManage = function() {
		$scope.display.list = undefined;
		$scope.display.admin = true;
		$scope.resourceTypes.deselectAllResources();
		
		var processableResourceTypes = _.filter($scope.resourceTypes.all, $scope.keepProcessableResourceTypes);
		if (processableResourceTypes && processableResourceTypes.length > 0) {
			$scope.currentResourceType = processableResourceTypes[0];
		}
		
		template.open('main', 'manage-view');
		template.open('resources', 'manage-resources');
	};

	$scope.initMain = function() {
		model.recordedSelections.firstResourceType = true;
		$scope.currentResourceType = undefined;
		$scope.resetSort();
		model.refresh();
		template.open('main', 'main-view');
	};


	// Main view interaction
	$scope.expandResourceType = function(resourceType) {
		resourceType.expanded = true;
		$scope.selectResources(resourceType);
	};

	$scope.collapseResourceType = function(resourceType) {
		resourceType.expanded = undefined;
		$scope.deselectResources(resourceType);
	};

	$scope.selectResources = function(resourceType) {
		resourceType.resources.forEach(function(resource) {
			if (resource.selected !== true) {
				resource.selected = true;
				resource.bookings.sync(function(){
					$scope.bookings.pushAll(resource.bookings.all);
				});
			}
		});
		$scope.lastSelectedResource = resourceType.resources.first();	
	};

	$scope.deselectResources = function(resourceType) {
		resourceType.resources.forEach(function(resource) {
			resource.selected = undefined;
			$scope.bookings.pullAll(resource.bookings.all);
		});
		$scope.lastSelectedResource = undefined;
	};

	$scope.switchSelectResources = function(resourceType) {
		if (resourceType.resources.every(function(resource) { return resource.selected })) {
			$scope.deselectResources(resourceType);
		}
		else {
			$scope.selectResources(resourceType);
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
		$scope.initModerators();

		template.open('lightbox', 'booking-details');
		$scope.display.showPanel = true;
	};

	$scope.closeBooking = function() {
		if ($scope.selectedBooking !== undefined && $scope.selectedBooking.is_periodic === true) {
			_.each($scope.selectedBooking._slots, function(slot){
				slot.expanded = false;
			});
		}
		if ($scope.display.list !== true) {
			// In calendar view, deselect all when closing lightboxes
			$scope.bookings.deselectAll();
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

	$scope.switchFilterListByDates = function(filter) {
		if ($scope.bookings.filters.dates !== true || filter === true) {
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

	$scope.formatMomentDayMedium = function(date) {
		return date.format('dddd DD MMM YYYY');	
	};

	$scope.formatHour = function(hour) {
		return date.format('HH[h]mm');
	};

	$scope.composeTitle = function(typeTitle, resourceTitle) {
		var title = typeTitle + ' - ' + resourceTitle;
		return _.isString(title) ? (title.trim().length > 65 ? title.substring(0, 62) + '...' : title.trim()) : "";
	};

	$scope.countValidatedSlots = function(slots) {
		return _.filter(slots, function(slot) { return slot.isValidated(); }).length;
	};

	$scope.countRefusedSlots = function(slots) {
		return _.filter(slots, function(slot) { return slot.isRefused(); }).length;
	};


	// Booking edition
	$scope.canCreateBooking = function() {
		if (undefined !== $scope.resourceTypes.find(function(resourceType){
			return resourceType.myRights !== undefined && resourceType.myRights.contrib !== undefined;
		})) {
			return true;
		}
		return false;
	}

	$scope.canEditBookingSelection = function() {
		if ($scope.display.list === true) {
			var localSelection = _.filter($scope.bookings.selection(), function(booking) { return booking.isBooking(); });
			return (localSelection.length === 1 && localSelection[0].owner === model.me.userId && localSelection[0].resource.is_available === true);
		}
		else {
			return $scope.bookings.selection().length === 1 && $scope.bookings.selection()[0].owner === model.me.userId && $scope.bookings.selection()[0].resource.is_available === true;
		}
	};

	$scope.canDeleteBookingSelection = function() {
		if ($scope.display.list === true) {
			return _.every($scope.bookings.selection(), function(booking){ 
				return booking.isBooking() || (booking.isSlot() && booking.booking.selected === true); 
			});
		}
		else{
			return true;
		}
	};

	$scope.newBooking = function(periodic) {
		$scope.display.processing = undefined;
		$scope.editedBooking = new Booking();
		$scope.initEditBookingDisplay();
		$scope.initModerators();
		
		// periodic booking
		$scope.editedBooking.is_periodic = false; // false by default
		if(periodic === 'periodic'){
			$scope.initPeriodic();
		}

		// resource
		if ($scope.lastSelectedResource) {
			$scope.editedBooking.resource = $scope.lastSelectedResource;
			if ($scope.editedBooking.resource.isBookable($scope.editedBooking.is_periodic)) {
				$scope.editedBooking.type = $scope.lastSelectedResource.type;
			}
			else {
				$scope.autoSelectTypeAndResource();
			}
		}
		else {
			$scope.autoSelectTypeAndResource();
		}

		// dates
		$scope.editedBooking.startMoment = moment();
		$scope.editedBooking.endMoment = moment();
		$scope.editedBooking.endMoment.hour($scope.editedBooking.startMoment.hour() + 1);
		$scope.editedBooking.startMoment.seconds(0);
		$scope.editedBooking.endMoment.seconds(0);
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
		$scope.initEditBookingDisplay();
		
		$scope.initModerators();

		// resource
		if ($scope.lastSelectedResource) {
			$scope.editedBooking.resource = $scope.lastSelectedResource;
			$scope.editedBooking.type = $scope.lastSelectedResource.type;
		}
		else {
			$scope.autoSelectTypeAndResource();
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
		$scope.editedBooking.startMoment.seconds(0);
		$scope.editedBooking.endMoment.seconds(0);
		// DEBUG
		var DEBUG_editedBooking = $scope.editedBooking;
		// /DEBUG
		$scope.initBookingDates($scope.editedBooking.startMoment, $scope.editedBooking.endMoment);
		$scope.$apply('editedBooking');
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
		$scope.initEditBookingDisplay();

		// periodic booking
		if ($scope.editedBooking.is_periodic === true) {			
			if ($scope.editedBooking.occurrences !== undefined && $scope.editedBooking.occurrences > 0) {
				$scope.editedBooking.byOccurrences = true;
			}
			else {
				$scope.editedBooking.byOccurrences = false;
			}
		}
		$scope.initBookingDates($scope.editedBooking.startMoment, $scope.editedBooking.endMoment);

		template.open('lightbox', 'edit-booking');
		$scope.display.showPanel = true;
	};

	$scope.initEditBookingDisplay = function() {
		$scope.editedBooking.display = {
			state: 0,
			STATE_RESOURCE: 0,
			STATE_BOOKING: 1,
			STATE_PERIODIC: 2
		};
	};

	$scope.initPeriodic = function() {
		$scope.editedBooking.is_periodic = true;
		$scope.editedBooking.periodDays = model.bitMaskToDays(); // no days selected
		$scope.editedBooking.byOccurrences = true;
		$scope.editedBooking.periodicity = 1;
		$scope.editedBooking.occurrences = 1;
		$scope.updatePeriodicSummary();
	};

	$scope.togglePeriodic = function() {
		if ($scope.editedBooking.is_periodic === true) {
			$scope.initPeriodic();
		};
		if ($scope.editedBooking.type === undefined || $scope.editedBooking.resource === undefined || (! $scope.editedBooking.resource.isBookable(true))) {
			$scope.autoSelectTypeAndResource();
			// Warn user ?
		}
	};

	$scope.initBookingDates = function(startMoment, endMoment) {
		// hours minutes management
		var minTime = moment(startMoment);
		minTime.set('hour', model.timeConfig.start_hour);
		var maxTime = moment(endMoment);
		maxTime.set('hour', model.timeConfig.end_hour);
		if(startMoment.isAfter(minTime) && startMoment.isBefore(maxTime)){
			$scope.booking.startTime = startMoment;
		}
		else{
			$scope.booking.startTime = minTime;
			if(startMoment.isAfter(maxTime)){
				startMoment.add('day', 1);
				endMoment.add('day', 1);
				maxTime.add('day', 1);
			}
		}
		if(endMoment.isBefore(maxTime)){
			$scope.booking.endTime = endMoment;
		}
		else{
			$scope.booking.endTime = maxTime;
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
	};

	$scope.autoSelectTypeAndResource = function() {
		$scope.editedBooking.type = _.first($scope.resourceTypes.filterAvailable());
		$scope.autoSelectResource();
	}	

	$scope.autoSelectResource = function() {
		$scope.editedBooking.resource = $scope.editedBooking.type === undefined ? undefined : _.first($scope.editedBooking.type.resources.filterAvailable($scope.editedBooking.is_periodic));
	};

	$scope.updatePeriodicSummary = function() {
		var days = _.filter($scope.editedBooking.periodDays, function(day){ return day.value });
		if (days.length == 0) {
			$scope.editedBooking.periodicSummary = "Sélectionnez au moins un jour de la semaine";
			return;
		}
		if (days.length == 7) {
			$scope.editedBooking.periodicSummary = lang.translate('rbs.period.days.all') + ", ";
		}
		else {
			// TODO: Days grouping etc
			$scope.editedBooking.periodicSummary = lang.translate('rbs.period.days.some') + ", ";
		}

		if ($scope.editedBooking.periodicity == 1) {
			$scope.editedBooking.periodicSummary += lang.translate('rbs.period.weeks.all') + ", ";
		}
		else {
			$scope.editedBooking.periodicSummary += lang.translate('rbs.period.weeks.partial') + lang.translate('rbs.period.weeks.' + $scope.editedBooking.periodicity) + ", ";
		}

		if ($scope.editedBooking.byOccurrences) {
			$scope.editedBooking.periodicSummary += lang.translate('rbs.period.occurences.for') + $scope.editedBooking.occurrences + lang.translate('rbs.period.occurences.slots');
		}
		else {
			$scope.editedBooking.periodicSummary += lang.translate('rbs.period.date.until') + $scope.formatMomentDayLong(moment($scope.editedBooking.periodicEndDate));	
		}
	};

	$scope.saveBooking = function() {
		// Check
		$scope.currentErrors = [];
		try {
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
				$scope.booking.startTime.hour(),
				$scope.booking.startTime.minute()]);
			if ($scope.editedBooking.is_periodic === true) {
				// periodic booking
				$scope.editedBooking.endMoment = moment.utc([
					$scope.booking.startDate.getFullYear(),
					$scope.booking.startDate.getMonth(),
					$scope.booking.startDate.getDate(),
					$scope.booking.endTime.hour(),
					$scope.booking.endTime.minute()]);
				if ($scope.editedBooking.byOccurrences !== true) {
					$scope.editedBooking.occurrences = undefined;
					$scope.editedBooking.periodicEndMoment = moment.utc([
					$scope.booking.periodicEndDate.getFullYear(),
					$scope.booking.periodicEndDate.getMonth(),
					$scope.booking.periodicEndDate.getDate(),
					$scope.booking.endTime.hour(),
					$scope.booking.endTime.minute()]);
				}
				$scope.resolvePeriodicMoments();
			}
			else {
				// non periodic
				$scope.editedBooking.endMoment = moment.utc([
					$scope.booking.endDate.getFullYear(),
					$scope.booking.endDate.getMonth(),
					$scope.booking.endDate.getDate(),
					$scope.booking.endTime.hour(),
					$scope.booking.endTime.minute()]);
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
		}
		catch (e) {
			$scope.display.processing = undefined;
			$scope.currentErrors.push({error: "rbs.error.technical"});
			$scope.$apply('editedBooking');
		}
	};

	$scope.checkSaveBooking = function() {
		var hasErrors = false;
		if (($scope.booking.startDate.getFullYear() < $scope.today.year())
			|| ($scope.booking.startDate.getFullYear() == $scope.today.year() && $scope.booking.startDate.getMonth() < $scope.today.month())
			|| ($scope.booking.startDate.getFullYear() == $scope.today.year() && $scope.booking.startDate.getMonth() == $scope.today.month() && $scope.booking.startDate.getDate() < $scope.today.date())
			|| ($scope.booking.startDate.getFullYear() == $scope.today.year() && $scope.booking.startDate.getMonth() == $scope.today.month() && $scope.booking.startDate.getDate() == $scope.today.date() && $scope.booking.startTime.hour() < moment().hour())) {
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
		// All slots for periodic bookings
		_.each($scope.bookings.selection(), function(booking){
			if (booking.isSlot() && booking.booking.selected !== true) {
				booking.booking.selected = true;
				booking.booking.selectAllSlots();
			}
		});

		// confirm message
		$scope.processBookings = $scope.bookings.selectionForProcess();
		template.open('lightbox', 'confirm-delete-booking');
		$scope.display.showPanel = true;
	};

	$scope.doRemoveBookingSelection = function() {
		$scope.display.processing = true;
		$scope.currentErrors = [];
		$scope.processBookings = $scope.bookings.selectionForDelete();
		try {
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
		}
		catch (e) {
			$scope.display.processing = undefined;
			$scope.currentErrors.push({error: "rbs.error.technical"});
		}
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
		try {
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
		}
		catch (e) {
			$scope.display.processing = undefined;
			$scope.currentErrors.push({error: "rbs.error.technical"});
		}
	};

	$scope.doRefuseBookingSelection = function() {
		$scope.display.processing = true;
		$scope.currentErrors = [];
		try {
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
		}
		catch (e) {
			$scope.display.processing = undefined;
			$scope.currentErrors.push({error: "rbs.error.technical"});
		}
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
		$scope.editedResourceType.structure = $scope.structures[0];
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
		$scope.editedResource.was_available = $scope.editedResource.is_available;
		
		$scope.editedResource.hasMaxDelay = ($scope.editedResource.max_delay !== undefined && $scope.editedResource.max_delay !== null);
		$scope.editedResource.hasMinDelay = ($scope.editedResource.min_delay !== undefined && $scope.editedResource.min_delay !== null);
		template.open('resources', 'edit-resource');
	};

	$scope.shareCurrentResourceType = function() {
		$scope.display.showPanel = true;
	};

	$scope.saveResourceType = function() {
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
	
	// Used when adding delays to resources
	$scope.delayDays = _.range(1, 31);
	$scope.daysToSeconds = function(nbDays) {
		return moment.duration(nbDays, 'days').asSeconds();
	};
	$scope.secondsToDays = function(nbSeconds) {
		return moment.duration(nbSeconds, 'seconds').asDays();
	}
	
	// Get Moderators
	$scope.initModerators = function() {
		if ($scope.resourceTypes.first().moderators === undefined) {
			$scope.resourceTypes.forEach(function(resourceType){
				resourceType.getModerators(function(){
					$scope.$apply('resourceTypes');
				});
			});
		}
	}

	$scope.nextWeekButton = function() {
		var next = moment(model.calendar.firstDay).add(7, 'day');
		updateCalendarSchedule(next);
	};

	$scope.previousWeekButton = function() {
		var prev = moment(model.calendar.firstDay).subtract(7, 'day');
		updateCalendarSchedule(prev);

	};
	$scope.nextWeekBookingButton = function() {
		var nextStart = moment(model.bookings.filters.startMoment).add(7, 'day');
		var nextEnd = moment(model.bookings.filters.endMoment).add(7, 'day');
		updateCalendarList(nextStart,nextEnd);
	};

	$scope.previousWeekBookingButton = function() {
		var prevStart = moment(model.bookings.filters.startMoment).subtract(7, 'day');
		var prevEnd = moment(model.bookings.filters.endMoment).subtract(7, 'day');
		updateCalendarList(prevStart,prevEnd);
	};
	var updateCalendarList = function(start, end){
		model.bookings.filters.startMoment.date(start.date());
		model.bookings.filters.startMoment.month(start.month());
		model.bookings.filters.startMoment.year(start.year());
		
		model.bookings.filters.endMoment.date(end.date());
		model.bookings.filters.endMoment.month(end.month());
		model.bookings.filters.endMoment.year(end.year());

		$scope.bookings.applyFilters();

	};

	var updateCalendarSchedule = function(date){
		model.calendar.firstDay.date(date.date());
		model.calendar.firstDay.month(date.month());
		model.calendar.firstDay.year(date.year());

		$('.hiddendatepickerform').datepicker('setValue', date.format("DD/MM/YYYY")).datepicker('update');
		$('.hiddendatepickerform').trigger({type: 'changeDate',date: date});
	};
	this.initialize();
}
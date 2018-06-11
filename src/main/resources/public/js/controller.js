routes.define(function($routeProvider){
	$routeProvider
			.when('/booking/:bookingId', {
				action: 'viewBooking'
			})
			.when('/booking/:bookingId/:start', {
				action: 'viewBooking'
			})
});

function RbsController($scope, template, model, date, route){

    route({
        viewBooking: function(param){
            if (param.start) {
                loadBooking(param.start, param.bookingId);
            } else {
                new Booking().retrieve(param.bookingId, function (date) {
                    loadBooking(date, param.bookingId);
                }, function (e){
                    $scope.currentErrors.push(e);
                    $scope.$apply();

                });
            }
        }
    });

    var loadBooking = function(date, id) {
        model.bookings.startPagingDate = moment(date).startOf('isoweek');
        //Paging end date
        model.bookings.endPagingDate = moment(date).add(7, 'day').startOf('day');
        $scope.display.routed = true;
        $scope.resourceTypes.one('sync', function(){
            $scope.initResourcesRouted(id, function() {
                updateCalendarScheduleBooking(moment(date), true);
            });
        });
    }

    this.initialize = function() {
		$scope.template = template;
		$scope.me = model.me;
		$scope.date = date;
		$scope.lang = lang;

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
		$scope.editedBooking = null;
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

		// fixme why to do that  Will auto-select first resourceType resources and no filter by default
		//model.recordedSelections.firstResourceType = true;
		model.recordedSelections.allResources = true;

		model.bookings.filters.dates = true;
		//Paging start date
		model.bookings.startPagingDate = moment().startOf('isoweek');
		//Paging end date
		model.bookings.endPagingDate = moment().add(7, 'day').startOf('day');
		//fixme Why started with today date ....
		model.bookings.filters.startMoment = moment().startOf('day');
		//fixme Why two month ?
		model.bookings.filters.endMoment = moment().add('month', 2).startOf('day');
		model.bookings.filters.startDate = model.bookings.filters.startMoment.toDate();
		model.bookings.filters.endDate = model.bookings.filters.endMoment.toDate();

		$scope.resourceTypes.on('sync', function(){
			// check create booking rights
			$scope.display.create = $scope.canCreateBooking();

			if ($scope.display.list === true) {
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

		//when date picker of calendar directive is used
		$scope.$watch(function() {
			return ($('.hiddendatepickerform')[0]) ? $('.hiddendatepickerform')[0].value : '';
		}, function (newVal, oldVal) {
			if(newVal !== oldVal) {
				//console.log('changed!', newVal, oldVal);
				if (!$scope.display.list && newVal && newVal!=='' && moment(model.bookings.startPagingDate).diff(moment(newVal, 'DD/MM/YYYY').startOf('isoweek')) !== 0) {
					model.bookings.startPagingDate = moment(newVal, 'DD/MM/YYYY').startOf('isoweek');
					model.bookings.endPagingDate = moment(newVal, 'DD/MM/YYYY').startOf('isoweek').add(7, 'day').startOf('day');
					$scope.bookings.sync();
				}
			}
		},true);
	};

	$scope.hasAnyBookingRight = function(booking){
		return booking.resource.myRights.process || booking.resource.myRights.manage || booking.owner === model.me.userId;
	}

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
		model.bookings.applyFilters();
	}

	$scope.initResourcesRouted = function(bookingId, cb) {
		var found = false;
		var actions = 0;
		var routedBooking = undefined;
        var countTotalTypes = $scope.resourceTypes.length();
		$scope.resourceTypes.forEach(function(resourceType){
            countTotalTypes--; // premier
            var countTotalResources = resourceType.resources.length();
			actions = actions + resourceType.resources.size();
			resourceType.resources.forEach(function(resource){
                countTotalResources--;
				resource.bookings.sync(function(){
                    if (routedBooking !== undefined || found) {
						return;
					}
					routedBooking = resource.bookings.find(function(booking){
						return booking.id == bookingId;
					});
					if (routedBooking !== undefined) {
						$scope.bookings.pushAll(routedBooking.resource.bookings.all);
						routedBooking.resource.type.expanded = true;
						routedBooking.resource.selected = true;
						$scope.viewBooking(routedBooking);
						$scope.display.routed = undefined;
						model.recordedSelections.firstResourceType = undefined;
						found = true;
					}
                    if( countTotalTypes == 0 && countTotalResources == 0 && typeof(cb) === 'function' ){
                        if (!found) {
                            // error
                            console.log("Booking not found (id: " + bookingId + ")");
                            notify.error('rbs.route.booking.not.found');
                            $scope.display.routed = undefined;
                            $scope.initResources();
                        }
                        cb();
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
		$scope.bookings.syncForShowList();
		$scope.bookings.applyFilters();
		template.open('bookings', 'main-list');
	};

	$scope.showCalendar = function(refresh) {
		if (refresh === true) {
			$scope.initMain();
		}
		$scope.display.admin = false;
		$scope.display.list = false;
		$scope.bookings.sync();
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
		//fixme Why model.recordedSelections.firstResourceType = true;
		model.recordedSelections.allResources = true;
		$scope.currentResourceType = undefined;
		$scope.resetSort();
		model.refresh($scope.display.list);
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
			}
		});
		$scope.lastSelectedResource = resourceType.resources.first();
		model.bookings.applyFilters();
	};

	$scope.deselectResources = function(resourceType) {
		resourceType.resources.forEach(function(resource) {
			resource.selected = undefined;
		});
		$scope.lastSelectedResource = undefined;
		model.bookings.applyFilters();
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
			model.bookings.applyFilters();
		}
		else {
			resource.selected = undefined;
			if (resource.is_available !== true) {
				$scope.lastSelectedResource = undefined;
			}
			model.bookings.applyFilters();
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
			//call back-end to obtain all periodic slots
			if (booking.booking.occurrences !== booking.booking._slots.length) {
				$scope.bookings.loadSlots(booking, function(){
					if (booking.status === $scope.status.STATE_REFUSED) {
						booking.expanded = true;
					}
					$scope.showBookingDetail(booking.booking, 3);
				});
			} else {
				if (booking.status === $scope.status.STATE_REFUSED) {
					booking.expanded = true;
				}
				$scope.showBookingDetail(booking.booking, 3);
			}

		} else {
			// booking
			$scope.showBookingDetail(booking, 1);
		}
	};

	$scope.showBookingDetail = function(booking, displaySection) {
		$scope.selectedBooking = booking;
		$scope.selectedBooking.displaySection = displaySection;
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
		$scope.editedBooking = null;
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
		return date.format('DD/MM/YYYY ')+lang.translate('rbs.booking.details.header.at')+date.format(' H[h]mm');
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

	$scope.formatHour = function(date) {
		return date.format('HH[h]mm');
	};

	$scope.formatBooking = function(date, time){
		return moment(date).format('DD/MM/YYYY') + ' ' +
				lang.translate('rbs.booking.details.header.at') + ' ' +
				time.format('HH[h]mm');
	};

	$scope.composeTitle = function(typeTitle, resourceTitle) {
		var title = typeTitle + ' - ' + resourceTitle;
		return _.isString(title) ? (title.trim().length > 50 ? title.substring(0, 47) + '...' : title.trim()) : "";
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
	};

	$scope.editPeriodicStartDate = function(){
		$scope.showDate = true;
		if(moment($scope.booking.periodicEndDate).unix() < moment($scope.booking.startDate).unix()){
			$scope.booking.periodicEndDate = $scope.booking.startDate
		}
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
	};

	$scope.autoSelectTypeAndResource = function() {
		$scope.editedBooking.type = _.first($scope.resourceTypes.filterAvailable());
		$scope.autoSelectResource();
	}

	$scope.autoSelectResource = function() {
		$scope.editedBooking.resource = $scope.editedBooking.type === undefined ? undefined : _.first($scope.editedBooking.type.resources.filterAvailable($scope.editedBooking.is_periodic));
	};

	$scope.updatePeriodicSummary = function() {
		$scope.editedBooking.periodicSummary = '';
		$scope.editedBooking.periodicShortSummary = '';
		$scope.editedBooking.periodicError = undefined;

		// Selected days
		var selected = 0;
		_.each($scope.editedBooking.periodDays, function(d){ selected += (d.value ? 1 : 0)});
		if (selected == 0) {
			// Error in periodic view
			$scope.editedBooking.periodicError = lang.translate('rbs.period.error.nodays');
			return;
		}
		if (selected == 7) {
			$scope.editedBooking.periodicSummary = lang.translate('rbs.period.days.all');
			$scope.editedBooking.periodicShortSummary = $scope.editedBooking.periodicSummary;
		}

		$scope.editedBooking.periodicSummary = $scope.summaryBuildDays($scope.editedBooking.periodDays);
		$scope.editedBooking.periodicShortSummary = lang.translate('rbs.period.days.some');

		// Weeks
		var summary = ", ";
		if ($scope.editedBooking.periodicity == 1) {
			summary += lang.translate('rbs.period.weeks.all') + ", ";
		}
		else {
			summary += lang.translate('rbs.period.weeks.partial') + ' ' + lang.translate('rbs.period.weeks.' + $scope.editedBooking.periodicity) + ", ";
		}

		// Occurences or date
		if ($scope.editedBooking.byOccurrences) {
			summary += lang.translate('rbs.period.occurences.for') + ' ' + $scope.editedBooking.occurrences + ' '
					+ lang.translate('rbs.period.occurences.slots.' + ($scope.editedBooking.occurrences > 1 ? 'many' : 'one'));
		}
		else {
			summary += lang.translate('rbs.period.date.until') + ' ' + $scope.formatMomentDayLong(moment($scope.booking.periodicEndDate));
		}

		$scope.editedBooking.periodicSummary += summary;
		$scope.editedBooking.periodicShortSummary += summary;
	};

	$scope.summaryBuildDays = function(days) {
		// No days or all days cases are already done here
		var summary = undefined;
		var startBuffer = undefined;
		var lastIndex = days.length - 1;
		if (_.first(days).value && _.last(days).value) {
			// Sunday and Monday are selected : summary will not start with monday, reverse-search the start day
			for (var k = days.length; k > 0; k--) {
				if (!days[k - 1].value) {
					lastIndex = k - 1;
					break;
				}
			}
			startBuffer = lastIndex + 1;
		}

		for (var i = 0; i <= lastIndex; i++) {
			if ((startBuffer === undefined) && days[i].value) {
				// No range in buffer, start the range
				startBuffer = i;
			}
			if(startBuffer !== undefined) {
				if (i == lastIndex && days[lastIndex].value) {
					// Day range complete (last index) write to summary
					summary = $scope.summaryWriteRange(summary, days[startBuffer], days[lastIndex]);
					break;
				}
				if (!days[i].value) {
					// Day range complete, write to summary
					summary = $scope.summaryWriteRange(summary, days[startBuffer], days[i - 1]);
					startBuffer = undefined;
				}
			}
		}
		return summary;
	};

	$scope.summaryWriteRange = function(summary, first, last) {
		if (first.number == last.number) {
			// One day range
			if (summary === undefined) {
				// Start the summary
				return lang.translate('rbs.period.days.one.start') + ' ' + lang.translate('rbs.period.days.' + first.number);
			}
			// Continue the summary
			return summary + lang.translate('rbs.period.days.one.continue') + ' ' + lang.translate('rbs.period.days.' + first.number);
		}
		if (first.number + 1 == last.number || first.number - 6 == last.number) {
			// Two day range
			if (summary === undefined) {
				// Start the summary
				return lang.translate('rbs.period.days.one.start') + ' '
						+ lang.translate('rbs.period.days.' + first.number) + ' '
						+ lang.translate('rbs.period.days.one.continue') + ' '
						+ lang.translate('rbs.period.days.' + last.number);
			}
			// Continue the summary
			return summary + lang.translate('rbs.period.days.one.continue') + ' '
					+ lang.translate('rbs.period.days.' + first.number) + ' '
					+ lang.translate('rbs.period.days.one.continue') + ' '
					+ lang.translate('rbs.period.days.' + last.number);
		}
		// Multi-day range
		if (summary === undefined) {
			// Start the summary
			return lang.translate('rbs.period.days.range.start') + ' '
					+ lang.translate('rbs.period.days.' + first.number) + ' '
					+ lang.translate('rbs.period.days.range.to') + ' '
					+ lang.translate('rbs.period.days.' + last.number);
		}
		// Continue the summary
		return summary + lang.translate('rbs.period.days.range.continue') + ' '
				+ lang.translate('rbs.period.days.' + first.number) + ' '
				+ lang.translate('rbs.period.days.range.to') + ' '
				+ lang.translate('rbs.period.days.' + last.number);
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
			$scope.editedBooking.startMoment = moment([
				$scope.booking.startDate.getFullYear(),
				$scope.booking.startDate.getMonth(),
				$scope.booking.startDate.getDate(),
				$scope.booking.startTime.hour(),
				$scope.booking.startTime.minute()]);
			if ($scope.editedBooking.is_periodic === true) {
				// periodic booking
				$scope.editedBooking.endMoment = moment([
					$scope.booking.startDate.getFullYear(),
					$scope.booking.startDate.getMonth(),
					$scope.booking.startDate.getDate(),
					$scope.booking.endTime.hour(),
					$scope.booking.endTime.minute()]);
				if ($scope.editedBooking.byOccurrences !== true) {
					$scope.editedBooking.occurrences = undefined;
					$scope.editedBooking.periodicEndMoment = moment([
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
				$scope.editedBooking.endMoment = moment([
					$scope.booking.endDate.getFullYear(),
					$scope.booking.endDate.getMonth(),
					$scope.booking.endDate.getDate(),
					$scope.booking.endTime.hour(),
					$scope.booking.endTime.minute()]);
			}

			$scope.editedBooking.save(function(){
				$scope.display.processing = undefined;
				$scope.closeBooking();
				model.refreshBookings($scope.display.list);
			}, function(e){
				notify.error(e.error);
				$scope.display.processing = undefined;
				$scope.currentErrors.push(e);
				$scope.$apply();
			});
		}
		catch (e) {
			$scope.display.processing = undefined;
			$scope.currentErrors.push({error: "rbs.error.technical"});
		}
	};

	$scope.checkSaveBooking = function() {
		var hasErrors = false;
		if (($scope.booking.startDate.getFullYear() < $scope.today.year())
				|| ($scope.booking.startDate.getFullYear() == $scope.today.year() && $scope.booking.startDate.getMonth() < $scope.today.month())
				|| ($scope.booking.startDate.getFullYear() == $scope.today.year() && $scope.booking.startDate.getMonth() == $scope.today.month() && $scope.booking.startDate.getDate() < $scope.today.date())
				|| ($scope.booking.startDate.getFullYear() == $scope.today.year() && $scope.booking.startDate.getMonth() == $scope.today.month() && $scope.booking.startDate.getDate() == $scope.today.date() && $scope.booking.startTime.hour() < moment().hour())) {
			$scope.currentErrors.push({error: 'rbs.booking.invalid.datetimes.past'});
			notify.error('rbs.booking.invalid.datetimes.past');
			hasErrors = true;
		}
		if($scope.booking.startDate.getFullYear() == $scope.booking.endDate.getFullYear()
            && $scope.booking.endTime.hour() == $scope.booking.startTime.hour()
			&& $scope.booking.endTime.minute() == $scope.booking.startTime.minute()){
            $scope.currentErrors.push({error: 'rbs.booking.invalid.datetimes.equals'});
            notify.error('rbs.booking.invalid.datetimes.equals');
            hasErrors = true;
		}
		if ($scope.editedBooking.is_periodic === true
				&& _.find($scope.editedBooking.periodDays, function(periodDay) { return periodDay.value === true; }) === undefined) {
			// Error
			$scope.currentErrors.push({error: 'rbs.booking.missing.days'});
			notify.error('rbs.booking.missing.days');
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

		var totalSelectionAsynchroneCall = 0;
		_.each($scope.bookings.selection(), function(booking) {
			if (booking.isSlot() && booking.booking.occurrences !== booking.booking._slots.length) {
				totalSelectionAsynchroneCall++;
			} else if (booking.isSlot() && booking.booking.selected !== true) {
				booking.booking.selected = true;
				booking.booking.selectAllSlots();
			} else if (booking.is_periodic) {
				booking.selectAllSlots();
			}
		});

		//if all slots are already completed
		if (totalSelectionAsynchroneCall === 0) {
			//confirm message
			$scope.showConfirmDeleteMessage();
		} else {
			// All slots for periodic bookings
			_.each($scope.bookings.selection(), function(booking){
				if (booking.isSlot() && booking.booking.occurrences !== booking.booking._slots.length) {
					//call back-end to obtain all periodic slots
					$scope.bookings.loadSlots(booking, function(){
						booking.booking.selected = true;
						booking.booking.selectAllSlots();
						totalSelectionAsynchroneCall--;
						if (totalSelectionAsynchroneCall === 0) {
							$scope.showConfirmDeleteMessage();
						}
					});
				}
			});
		}
	};

	$scope.showConfirmDeleteMessage = function() {
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
						model.refreshBookings($scope.display.list);
					}
				}, function(e){
					$scope.currentErrors.push(e);
					actions--;
					if (actions === 0) {
						$scope.display.processing = undefined;
						$scope.showActionErrors()
						model.refreshBookings($scope.display.list);
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
						model.refreshBookings($scope.display.list);
					}
				}, function(e){
					$scope.currentErrors.push(e);
					actions--;
					if (actions === 0) {
						$scope.display.processing = undefined;
						$scope.showActionErrors();
						model.refreshBookings($scope.display.list);
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
						model.refreshBookings($scope.display.list);
					}
				}, function(e){
					$scope.currentErrors.push(e);
					actions--;
					if (actions === 0) {
						$scope.display.processing = undefined;
						$scope.showActionErrors();
						model.refreshBookings($scope.display.list);
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
		template.open('resources', 'manage-resources');
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
        $scope.isCreation = true;
		$scope.display.processing = undefined;
		$scope.editedResource = new Resource();
		$scope.editedResource.type = $scope.currentResourceType;
		$scope.editedResource.is_available = true;
		$scope.editedResource.periodic_booking = true;
		template.open('resources', 'edit-resource');
	};

	$scope.editSelectedResource = function() {
        $scope.isCreation = false;
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
			model.refreshRessourceType();
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
			model.refreshRessourceType();
		}, function(e){
			$scope.currentErrors.push(e);
			$scope.display.processing = undefined;
			$scope.$apply();
		});
	};

	$scope.deleteResourcesSelection = function() {
		$scope.currentResourceType.resourcesToDelete = $scope.currentResourceType.resources.selection();
		$scope.currentResourceType.resources.deselectAll();
		template.open('resources', 'confirm-delete-resource');
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
					model.refreshRessourceType();
				}
			}, function(e){
				$scope.currentErrors.push(e);
				actions--;
				if (actions === 0) {
					$scope.display.processing = undefined;
					$scope.showActionErrors();
					model.refreshRessourceType();
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
		model.bookings.startPagingDate = moment(model.bookings.startPagingDate).add(7, 'day');
		model.bookings.endPagingDate = moment(model.bookings.endPagingDate).add(7, 'day');
		updateCalendarSchedule(next);
	};

	$scope.previousWeekButton = function() {
		var prev = moment(model.calendar.firstDay).subtract(7, 'day');
		model.bookings.startPagingDate = moment(model.bookings.startPagingDate).subtract(7, 'day');
		model.bookings.endPagingDate = moment(model.bookings.endPagingDate).subtract(7, 'day');
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

	$scope.editSelectedType = function(){
		$scope.display.processing = undefined;
		$scope.editedResourceType = model.resourceTypes.selection()[0];
		template.close('resources');
		$scope.$apply();
		template.open('resources', 'edit-resource-type');
	};

	$scope.removeSelectedTypes = function(){
		$scope.display.confirmRemoveTypes = true;
	};

	$scope.doRemoveTypes = function(){
		model.resourceTypes.removeSelectedTypes();
		$scope.display.confirmRemoveTypes = false;
		template.close('resources');
	};

    // display a warning when editing a resource and changing the resource type (not in creation mode).
    $scope.resourceTypeModified = function() {
        if($scope.currentResourceType != $scope.editedResource.type && !$scope.isCreation) {
            notify.info('rbs.type.info.change');
        }
    }

	var updateCalendarList = function(start, end){
		model.bookings.filters.startMoment.date(start.date());
		model.bookings.filters.startMoment.month(start.month());
		model.bookings.filters.startMoment.year(start.year());

		model.bookings.filters.endMoment.date(end.date());
		model.bookings.filters.endMoment.month(end.month());
		model.bookings.filters.endMoment.year(end.year());

		$scope.bookings.syncForShowList();
		$scope.bookings.applyFilters();

	};

    var updateCalendarScheduleBooking = function(date, skipSync){
        model.calendar.setDate(date);
        if( skipSync === undefined) {
            $scope.bookings.sync();
        }
	};

	var updateCalendarSchedule = function(newDate){
        model.calendar.firstDay.date(newDate.date());
        model.calendar.firstDay.month(newDate.month());
        model.calendar.firstDay.year(newDate.year());
        $scope.bookings.sync();
        $('.hiddendatepickerform').datepicker('setValue', newDate.format("DD/MM/YYYY")).datepicker('update');
        $('.hiddendatepickerform').trigger({type: 'changeDate', date: newDate});

    };
    this.initialize();

}

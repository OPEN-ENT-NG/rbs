function RbsController($scope, template, model, date){
	
	$scope.template = template;
	$scope.me = model.me;
	$scope.date = date;

	$scope.display = {
		list: true
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

	$scope.resourceTypes = model.resourceTypes;
	$scope.bookings = model.bookings;
	$scope.mine = model.mine;
	$scope.unprocessed = model.unprocessed;
	$scope.times = model.times;
	$scope.periods = model.periods;

	$scope.currentResourceType = undefined;
	$scope.selectedBooking = undefined;
	$scope.editedBooking = undefined;
	$scope.bookings.refuseReason = undefined;
	$scope.processBookings = [];
	$scope.currentErrors = [];

	template.open('main', 'main-view');
	template.open('bookings', 'main-calendar');

	// Will auto-select "Mine" bookings by default
	model.recordedSelections.mine = true;

	$scope.resourceTypes.on('sync', function(){
		// Restore previous selections
		model.recordedSelections.restore(
			function(){
				$scope.mine.bookings.sync(function(){
					$scope.bookings.pushAll($scope.mine.bookings.all);
				});
			},
			function(){
				$scope.unprocessed.bookings.sync(function(){
					$scope.bookings.pushAll($scope.unprocessed.bookings.all);
				});
			},
			function(resource){
				resource.bookings.sync(function(){
					$scope.bookings.pushAll(resource.bookings.all);
				});
			}
		);

		// Temporary : reselect first resourceType after model.refresh()
		if ($scope.currentResourceType !== undefined) {
			$scope.currentResourceType = model.resourceTypes.first();
		}
	});


	// Navigation
	$scope.showManage = function() {
		$scope.currentResourceType = model.resourceTypes.first();
		$scope.resourceTypes.deselectAllResources();
		template.open('main', 'manage-view');
		template.open('resources', 'manage-resources');
	};

	$scope.showMain = function() {
		$scope.currentResourceType = undefined;
		$scope.resetSort();
		model.refresh();
		template.open('main', 'main-view');
		template.open('bookings', 'main-list');
	};

	// Main view interaction
	$scope.displayList = function() {
		$scope.display.list === true
		template.open('bookings', 'main-list');	
	};

	$scope.displayCalendar = function() {
		$scope.display.list === false
		template.open('bookings', 'main-calendar');
	};

	$scope.switchExpand = function(resourceType) {
		if ($scope.unprocessed.selected === true) {
			$scope.unprocessedRestoreSelections();
		}
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
		if ($scope.unprocessed.selected === true) {
			$scope.unprocessedRestoreSelections();
		}
		if ($scope.mine.selected !== true) {
			$scope.mine.selected = true;
			$scope.bookings.pushAll($scope.mine.bookings.all);
		}
		else {
			$scope.mine.selected = undefined;
			$scope.bookings.pullAll($scope.mine.bookings.all);
		}
	};

	$scope.switchSelectUnprocessed = function() {
		if ($scope.unprocessed.selected !== true) {
			$scope.unprocessed.selected = true;
			// deselect other cases (false: without saving the unprocessed selection)
			model.recordedSelections.record(
				false,
				function(resourceType){
					resourceType.expanded = undefined;
				},
				function(resource){
					resource.selected = undefined;
				}
			);
			$scope.lastSelectedResource = undefined;

			$scope.bookings.clear();
			$scope.unprocessed.bookings.sync(function(){
				$scope.bookings.pushAll($scope.unprocessed.bookings.all);	
			});
		}
		else {
			$scope.unprocessedRestoreSelections();
		}
	}

	$scope.unprocessedRestoreSelections = function() {
		$scope.unprocessed.selected = undefined;
		$scope.bookings.clear();
		// restore previous selections
		model.recordedSelections.restore(
			function(){
				$scope.bookings.pushAll($scope.mine.bookings.all);
			},
			function(){
				$scope.bookings.pushAll($scope.unprocessed.bookings.all);
			},
			function(resource){
				$scope.bookings.pushAll(resource.bookings.all);
			}
		);
	}


	// Bookings
	$scope.viewBooking = function(booking) {
		$scope.selectedBooking = booking;

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
		$scope.editedBooking = undefined;
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

	$scope.filterList = function(booking) {
		return booking.isBooking();
	}

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
	}

	$scope.resetSort = function() {
		$scope.sort.predicate = 'start_date';
		$scope.sort.reverse = false;
	}


	// General
	$scope.formatDate = function(date) {
		return $scope.formatMoment(moment(date + 'Z'));
	};

	$scope.formatDateLong = function(date) {
		return $scope.formatMomentLong(moment(date + 'Z'));
	};

	$scope.formatMoment = function(date) {
		return date.format('DD/MM/YYYY à H[h]mm');
	};

	$scope.formatMomentLong = function(date) {
		return date.format('dddd DD MMMM YYYY - HH[h]mm');
	};

	$scope.trimReason = function(reason) {
		return _.isString(reason) ? (reason.trim().length > 23 ? reason.substring(0, 20) + '...' : reason.trim()) : "";
	};


	// Booking edition
	$scope.canEditBookingSelection = function() {
		var localSelection = _.filter($scope.bookings.selection(), function(booking) { return booking.isBooking(); });
		return (localSelection.length === 1 && localSelection[0].resource.is_available === true);
	};

	$scope.canDeleteBookingSelection = function() {
		return _.every($scope.bookings.selection(), function(booking){ 
			return booking.isBooking() || (booking.isSlot() && booking.booking.selected === true); 
		});
	};

	$scope.newBooking = function() {
		$scope.display.processing = undefined;
		$scope.editedBooking = new Booking();
		if ($scope.lastSelectedResource) {
			$scope.editedBooking.resource = $scope.lastSelectedResource;
			$scope.editedBooking.type = $scope.lastSelectedResource.type;
		}
		else {
			$scope.editedBooking.type = _.first($scope.resourceTypes.filterAvailable());
			$scope.autoSelectResource();
		}

		// default hours
		$scope.editedBooking.startTime = model.times[0];
		$scope.editedBooking.endTime = _.find(model.times, function(hourMinutes) { return $scope.editedBooking.startTime.hour < hourMinutes.hour });
		$scope.editedBooking.startDate = undefined;
		$scope.editedBooking.endDate = undefined;

		// periodic booking
		$scope.editedBooking.is_periodic = false; // false by default
		$scope.editedBooking.periodDays = model.bitMaskToDays(); // no days selected
		$scope.editedBooking.byOccurrences = true;
		$scope.editedBooking.periodicEndDate = undefined;

		// debug
		var DEBUG_editedBooking = $scope.editedBooking;
		// /debug

		template.open('lightbox', 'edit-booking');
		$scope.display.showPanel = true;
	};

	$scope.editBooking = function() {
		$scope.display.processing = undefined;
		if ($scope.selectedBooking !== undefined) {
			$scope.editedBooking = $scope.selectedBooking;
		}
		else {
			$scope.editedBooking = $scope.bookings.selection()[0];
			if (! $scope.editedBooking.isBooking()) {
				$scope.editedBooking = $scope.editedBooking.booking;
			}
		}

		// dates management
		$scope.editedBooking.startDate = $scope.editedBooking.startMoment.toDate();
		$scope.editedBooking.startDate.setFullYear($scope.editedBooking.startMoment.years());
		$scope.editedBooking.startDate.setMonth($scope.editedBooking.startMoment.months());
		$scope.editedBooking.startDate.setDate($scope.editedBooking.startMoment.date());
		$scope.editedBooking.endDate = $scope.editedBooking.endMoment.toDate();
		$scope.editedBooking.endDate.setFullYear($scope.editedBooking.endMoment.years());
		$scope.editedBooking.endDate.setMonth($scope.editedBooking.endMoment.months());
		$scope.editedBooking.endDate.setDate($scope.editedBooking.endMoment.date());

		$scope.editedBooking.startTime = _.find(model.times, function(hourMinutes) { 
			return ($scope.editedBooking.startMoment.hour() === hourMinutes.hour && $scope.editedBooking.startMoment.minutes() === hourMinutes.min) });
		$scope.editedBooking.endTime = _.find(model.times, function(hourMinutes) { 
			return ($scope.editedBooking.endMoment.hour() === hourMinutes.hour && $scope.editedBooking.endMoment.minutes() === hourMinutes.min) });

		// periodic booking
		if ($scope.editedBooking.is_periodic === true) {
			$scope.editedBooking.periodicEndDate = $scope.editedBooking.endDate;
			
			if ($scope.editedBooking.occurrences !== undefined && $scope.editedBooking.occurrences > 0) {
				$scope.editedBooking.byOccurrences = true;
			}
			else {
				$scope.editedBooking.byOccurrences = false;
			}
		}
		else {
			// prepare so periodic mode can be selected
			$scope.editedBooking.periodDays = model.bitMaskToDays(); // no days selected
			$scope.editedBooking.byOccurrences = true;
		}

		// debug
		var DEBUG_editedBooking = $scope.editedBooking;
		// /debug

		template.open('lightbox', 'edit-booking');
		$scope.display.showPanel = true;
	};

	$scope.autoSelectResource = function() {
		$scope.editedBooking.resource = _.first($scope.editedBooking.type.resources.filterAvailable());
	};

	$scope.saveBooking = function() {
		$scope.display.processing = true;
		
		// dates management
		$scope.editedBooking.startMoment = moment.utc([
			$scope.editedBooking.startDate.getFullYear(),
			$scope.editedBooking.startDate.getMonth(),
			$scope.editedBooking.startDate.getDate(),
			$scope.editedBooking.startTime.hour,
			$scope.editedBooking.startTime.min]);

		if ($scope.editedBooking.is_periodic === true) {
			// periodic booking
			$scope.editedBooking.endMoment = moment.utc([
				$scope.editedBooking.startDate.getFullYear(),
				$scope.editedBooking.startDate.getMonth(),
				$scope.editedBooking.startDate.getDate(),
				$scope.editedBooking.endTime.hour,
				$scope.editedBooking.endTime.min]);

			if ($scope.editedBooking.byOccurrences !== true) {
				$scope.editedBooking.occurrences = undefined;
				$scope.editedBooking.periodicEndMoment = moment.utc([
					$scope.editedBooking.periodicEndDate.getFullYear(),
					$scope.editedBooking.periodicEndDate.getMonth(),
					$scope.editedBooking.periodicEndDate.getDate(),
					$scope.editedBooking.endTime.hour,
					$scope.editedBooking.endTime.min]);
			}
		}
		else {
			// non periodic
			$scope.editedBooking.endMoment = moment.utc([
				$scope.editedBooking.endDate.getFullYear(),
				$scope.editedBooking.endDate.getMonth(),
				$scope.editedBooking.endDate.getDate(),
				$scope.editedBooking.endTime.hour,
				$scope.editedBooking.endTime.min]);		
		}

		$scope.currentErrors = [];
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
		// Default to user's classId
		if ($scope.editedResourceType.school_id === undefined) {
			$scope.editedResourceType.school_id = model.me.classId;			
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
		//$scope.display.showPanel = true;
	};

	$scope.deleteResourcesSelection = function() {
		template.open('resources', 'confirm-delete-resource');
		//$scope.display.showPanel = true;
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
		var actions = $scope.currentResourceType.resources.selection().length;
		_.each($scope.currentResourceType.resources.selection(), function(resource){
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
	}

	// Special Workflow and Behaviours
	$scope.hasWorkflowOrAnyResourceHasBehaviour = function(workflowRight, ressourceRight) {
		var workflowRights = workflowRight.split('.');
		return (model.me.workflow[workflowRights[0]] !== undefined && model.me.workflow[workflowRights[0]][workflowRights[1]] === true)
			|| resourceTypes.find(function(resourceType){
				return (resourceType.resources.find(function(resource){
					return resource.myRights !== undefined && resource.myRights[ressourceRight] !== undefined;
				}) !== undefined);
			});
	};
}
function RbsController($scope, template, model, date){
	
	$scope.template = template;
	$scope.me = model.me;
	$scope.date = date;

	$scope.display = {
		list: true
	};

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
	$scope.unprocessed.previousTypes = new Array();
	$scope.unprocessed.previousResources = new Array();
	$scope.times = model.times;
	$scope.periods = model.periods;

	$scope.currentResourceType = undefined;
	$scope.selectedBooking = undefined;
	$scope.editedBooking = undefined;
	$scope.bookings.refuseReason = undefined;

	template.open('main', 'main-view');
	template.open('bookings', 'main-list');

	var initialSyncMine = true;
	$scope.resourceTypes.on('syncResources', function(){
		if (initialSyncMine === true) {
			// Auto-select my bookings
			$scope.mine.bookings.sync(function(){
				$scope.mine.selected = true;
				$scope.bookings.pushAll($scope.mine.bookings.all);
			});
			initialSyncMine = undefined;
			return;
		}
		
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
			$scope.lastSelectedResource = resource;
			resource.bookings.sync(function(){
				$scope.bookings.pushAll(resource.bookings.all);
			});
		}
		else {
			resource.selected = undefined;
			$scope.lastSelectedResource = undefined;
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
		$scope.selectedBooking = undefined;
		$scope.editedBooking = undefined;
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
		if (booking.expanded === true && booking.selected === true) {
			_.each(booking.slots, function(slot){
				slot.selected = true;
			});
		}
		else if (booking.expanded === true && booking.selected !== true) {
			_.each(booking.slots, function(slot){
				slot.selected = undefined;
			});
		}
	};


	// General
	$scope.formatDate = function(date) {
		return moment(date).format('DD/MM/YYYY à H[h]mm');
	};

	$scope.formatDateLong = function(date) {
		return moment(date).format('dddd DD MMMM YYYY - HH[h]mm');
	};


	// Booking edition
	$scope.newBooking = function() {
		$scope.editedBooking = new Booking();
		if ($scope.lastSelectedResource) {
			$scope.editedBooking.resource = $scope.lastSelectedResource;
			$scope.editedBooking.type = $scope.lastSelectedResource.type;
		}
		else {
			$scope.editedBooking.type = $scope.resourceTypes.first();
			$scope.editedBooking.resource = $scope.editedBooking.type.resources.first();
		}

		// periodic booking
		$scope.editedBooking.is_periodic = false; // false by default
		$scope.editedBooking.periodDays = model.bitMaskToDays(0); // no days selected
		$scope.editedBooking.byOccurences = true;

		// debug
		var DEBUG_editedBooking = $scope.editedBooking;
		// /debug

		template.open('lightbox', 'edit-booking');
		$scope.display.showPanel = true;
	};

	$scope.editBooking = function() {
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
		$scope.editedBooking.startDate = moment($scope.editedBooking.startMoment).startOf('day').toDate();
		$scope.editedBooking.endDate = moment($scope.editedBooking.endMoment).startOf('day').toDate();
		$scope.editedBooking.startTime = _.find(model.times, function(hourMinutes) { 
			return ($scope.editedBooking.startMoment.hour === hourMinutes.hour && $scope.editedBooking.startMoment.min === hourMinutes.min) });
		$scope.editedBooking.endTime = _.find(model.times, function(hourMinutes) { 
			return ($scope.editedBooking.endMoment.hour === hourMinutes.hour && $scope.editedBooking.endMoment.min === hourMinutes.min) });

		// periodic booking
		if ($scope.editedBooking.is_periodic !== true) {
			$scope.editedBooking.periodDays = model.bitMaskToDays(0); // no days selected
		}

		if ($scope.editedBooking.occurences !== undefined && $scope.editedBooking.occurences > 0) {
			$scope.editedBooking.byOccurences = true;
		}
		else {
			$scope.editedBooking.byOccurences = false;
		}

		// debug
		var DEBUG_editedBooking = $scope.editedBooking;
		// /debug

		template.open('lightbox', 'edit-booking');
		$scope.display.showPanel = true;
	};

	$scope.saveBooking = function() {
		$scope.display.processing = true;
		
		// dates management
		$scope.editedBooking.startMoment = moment($scope.editedBooking.startDate).hour($scope.editedBooking.startTime.hour).minute($scope.editedBooking.startTime.min);
		$scope.editedBooking.endMoment = moment($scope.editedBooking.endDate).hour($scope.editedBooking.endTime.hour).minute($scope.editedBooking.endTime.min);;

		// periodic booking
		if ($scope.editedBooking.is_periodic === true) {
			if ($scope.editedBooking.byOccurences !== true) {
				$scope.editedBooking.periodicEndMoment = moment($scope.editedBooking.periodicEndDate);
			}
		}

		// debug
		var DEBUG_editedBooking = $scope.editedBooking;
		// /debug

		$scope.editedBooking.save(function(){
			$scope.display.processing = undefined;
			$scope.closeBooking();
			model.refresh();
		});
	};

	$scope.removeBookingSelection = function() {
		if ($scope.selectedBooking !== undefined) {
			$scope.bookings.deselectAll();
			$scope.selectedBooking.selected = true;
		}

		// confirm message
		template.open('lightbox', 'confirm-delete-booking');
		$scope.display.showPanel = true;
	};

	$scope.doRemoveBookingSelection = function() {
		$scope.display.processing = true;
		$scope.bookings.removeSelection(function(){
			$scope.display.processing = undefined;
			$scope.closeBooking();
			model.refresh();
		});
	};


	// Booking Validation
	$scope.canProcessBookingSelection = function() {
		return _.every($scope.bookings.selection(), function(booking){ return booking.isPending(); });
	};

	$scope.validateBookingSelection = function() {
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

		$scope.display.showPanel = true;
		template.open('lightbox', 'validate-booking');
	};

	$scope.refuseBookingSelection = function() {
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

		$scope.display.showPanel = true;
		$scope.bookings.refuseReason = "";
		template.open('lightbox', 'refuse-booking');
	};

	$scope.doValidateBookingSelection = function() {
		var actions = $scope.bookings.selection().length;
		_.each($scope.bookings.selection(), function(booking){
			booking.validate(function(){
				actions--;
				if (actions === 0) {
					$scope.bookings.deselectAll();
					$scope.closeBooking();
					model.refresh();
				}
			});
		});
	};

	$scope.doRefuseBookingSelection = function() {
		var actions = $scope.bookings.selection().length;
		_.each($scope.bookings.selection(), function(booking){
			booking.refusal_reason = $scope.bookings.refuseReason;
			booking.refuse(function(){
				actions--;
				if (actions === 0) {
					$scope.bookings.deselectAll();
					$scope.bookings.refuseReason = undefined;
					$scope.closeBooking();
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
		$scope.editedResourceType = new ResourceType();
		template.open('resources', 'edit-resource-type');
	};

	$scope.newResource = function() {
		$scope.editedResource = new Resource();
		$scope.editedResource.type = $scope.currentResourceType;
		template.open('resources', 'edit-resource');
	};

	$scope.editCurrentResourceType = function() {
		$scope.editedResourceType = $scope.currentResourceType;
		template.open('resources', 'edit-resource-type');
	};

	$scope.editSelectedResource = function() {
		$scope.editedResource = $scope.currentResourceType.resources.selection()[0];
		template.open('resources', 'edit-resource');
	};

	$scope.shareCurrentResourceType = function() {
		$scope.display.showPanel = true;
	};

	$scope.saveResourceType = function() {
		// Default to user's classId
		$scope.editedResourceType.school_id = model.me.classId;

		$scope.display.processing = true;
		$scope.editedResourceType.save(function(){
			$scope.display.processing = undefined;
			$scope.currentResourceType = $scope.editedResourceType;
			$scope.closeResourceType();
			model.refresh();
		});
	};

	$scope.saveResource = function() {
		$scope.display.processing = true;
		$scope.editedResource.save(function(){
			$scope.display.processing = undefined;
			$scope.closeResource();
			model.refresh();
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
		$scope.editedResourceType.delete(function(){
			$scope.display.processing = undefined;
			$scope.resourceTypes.remove($scope.editedResourceType);
			$scope.currentResourceType = $scope.resourceTypes.first();
			$scope.closeResourceType();
			model.refresh();
		});
	};

	$scope.doDeleteResource = function() {
		$scope.display.processing = true;
		$scope.currentResourceType.resources.removeSelection(function(){
			$scope.display.processing = undefined;
			$scope.closeResource();
			model.refresh();
		});
	};

	$scope.closeResourceType = function() {
		$scope.editedResourceType = undefined;
		template.open('resources', 'manage-resources');
	};

	$scope.closeResource = function() {
		$scope.editedResource = undefined;
		template.open('resources', 'manage-resources');
	};
}
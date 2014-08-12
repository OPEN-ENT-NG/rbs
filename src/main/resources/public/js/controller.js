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
		STATE_REFUSED: model.STATE_REFUSED
	};

	$scope.resourceTypes = model.resourceTypes;
	$scope.bookings = model.bookings;
	$scope.mine = model.mine;
	$scope.unprocessed = model.unprocessed;
	$scope.unprocessed.previousTypes = new Array();
	$scope.unprocessed.previousResources = new Array();
	$scope.times = model.times;

	$scope.currentResourceType = undefined;
	$scope.selectedBooking = undefined;
	$scope.editedBooking = undefined;

	template.open('main', 'main-view');
	template.open('bookings', 'main-list');

	$scope.resourceTypes.on('syncResources', function(){
		model.mine.bookings.sync();
		model.unprocessed.bookings.sync();
	});

	// Auto-select my bookings
	$scope.mine.bookings.one('sync', function(){
		$scope.mine.selected = true;
		$scope.bookings.pushAll($scope.mine.bookings.all);
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
		$scope.resourceTypes.deselectAllResources();
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
			// deselect other cases
			if ($scope.mine.selected === true) {
				$scope.unprocessed.previousMine = true;
				$scope.mine.selected = undefined;
			}
			$scope.resourceTypes.forEach(function(resourceType){
				if (resourceType.expanded === true) {
					$scope.unprocessed.previousTypes.push(resourceType);
					resourceType.expanded = undefined;
				}
				resourceType.resources.forEach(function(resource) {
					if (resource.selected) {
						$scope.unprocessed.previousResources.push(resource);
						$scope.switchSelect(resource);
					}
				});
			});
			$scope.bookings.clear();
			$scope.bookings.pushAll($scope.unprocessed.bookings.all);
		}
		else {
			$scope.unprocessedRestoreSelections();
		}
	}

	$scope.unprocessedRestoreSelections = function() {
		$scope.unprocessed.selected = undefined;
		$scope.bookings.clear();
		// reselect other cases
		if ($scope.unprocessed.previousMine === true) {
			$scope.mine.selected = true;
			$scope.bookings.pushAll($scope.mine.bookings.all);
		}
		$scope.unprocessed.previousMine = undefined;
		_.each($scope.unprocessed.previousTypes, function(resourceType){
			resourceType.expanded = true;
		});
		$scope.unprocessed.previousTypes = [];
		_.each($scope.unprocessed.previousResources, function(resource) {
			$scope.switchSelect(resource);
		});
		$scope.unprocessed.previousResources = [];
	}

	$scope.swicthSelectAllBookings = function() {
		if ($scope.display.selectAllBookings) {
			$scope.bookings.selectAll();
		}
		else {
			$scope.bookings.deselectAll();
		}
	};

	$scope.viewBooking = function(booking) {
		$scope.selectedBooking = booking;

		template.open('lightbox', 'booking-details');
		$scope.display.showPanel = true;
	};

	$scope.closeBooking = function() {
		$scope.selectedBooking = undefined;
		$scope.display.showPanel = false;
		template.close('lightbox');
	};

	$scope.formatDate = function(date) {
		return moment(date).format('DD / MM / YYYY à H [h] mm');
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
		template.open('lightbox', 'edit-booking');
		$scope.display.showPanel = true;
	};

	$scope.editBooking = function() {
		$scope.editedBooking = $scope.bookings.selection()[0];

		// dates management
		$scope.editedBooking.startDate = $scope.editedBooking.startMoment.startOf('day').toDate();
		$scope.editedBooking.endDate = $scope.editedBooking.endMoment.startOf('day').toDate();
		$scope.editedBooking.startTime = _.find(model.times, function(hourMinutes) { 
			return ($scope.editedBooking.startMoment.hour() === hourMinutes.hour() && $scope.editedBooking.startMoment.minute() === hourMinutes.minute()) });
		$scope.editedBooking.endTime = _.find(model.times, function(hourMinutes) { 
			return ($scope.editedBooking.endMoment.hour() === hourMinutes.hour() && $scope.editedBooking.endMoment.minute() === hourMinutes.minute()) });

		template.open('lightbox', 'edit-booking');
		$scope.display.showPanel = true;
	};

	$scope.saveBooking = function() {
		$scope.display.processing = true;
		
		// dates management
		$scope.editedBooking.startMoment = moment($scope.editedBooking.startDate).add($scope.editedBooking.startTime);
		$scope.editedBooking.endMoment = moment($scope.editedBooking.endDate).add($scope.editedBooking.endTime);

		$scope.editedBooking.save(function(){
			$scope.editedBooking.start_date = $scope.editedBooking.startTime.toDate();
			$scope.editedBooking.end_date = $scope.editedBooking.endTime.toDate();
			$scope.display.processing = undefined;
			$scope.closeBooking();
			$scope.resourceTypes.sync();
		});
	};

	$scope.removeBookingSelection = function() {
		if ($scope.selectedBooking !== undefined) {
			$scope.bookings.deselectAll();
			$scope.selectedBooking.selected = true;
		}

		// confirm message
	};


	// Booking Validation
	$scope.validateBookingSelection = function() {
		if ($scope.selectedBooking !== undefined) {
			$scope.bookings.deselectAll();
			$scope.selectedBooking.selected = true;
		}

		$scope.display.showPanel = true;
		template.open('lightbox', 'validate-booking');
	};

	$scope.refuseBookingSelection = function() {
		if ($scope.selectedBooking !== undefined) {
			$scope.bookings.deselectAll();
			$scope.selectedBooking.selected = true;
		}

		$scope.display.showPanel = true;
		$scope.refuseReason = "";
		template.open('lightbox', 'refuse-booking');
	};

	$scope.doValidateBookingSelection = function() {
		var actions = $scope.bookings.selection().length;
		_.each($scope.bookings.selection(), function(booking){
			booking.validate(function(){
				actions--;
				if (actions === 0) {
					$scope.resourceTypes.sync();
					$scope.bookings.deselectAll();
					$scope.closeBooking();
				}
			});
		});
	};

	$scope.doRefuseBookingSelection = function() {
		var actions = $scope.bookings.selection().length;
		_.each($scope.bookings.selection(), function(booking){
			booking.refusal_reason = $scope.refuseReason;
			booking.refuse(function(){
				actions--;
				if (actions === 0) {
					$scope.resourceTypes.sync();
					$scope.bookings.deselectAll();
					$scope.closeBooking();
				}
			});
		});
	};


	// Management view interaction
	$scope.selectResourceType = function(resourceType) {
		$scope.currentResourceType.resources.deselectAll();
		$scope.currentResourceType = resourceType;
	};

	$scope.swicthSelectAllRessources = function() {
		if ($scope.display.selectAllRessources) {
			$scope.currentResourceType.resources.selectAll();
		}
		else {
			$scope.currentResourceType.resources.deselectAll();
		}
	};

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
		$scope.editedResource.type = $scope.currentResourceType;
		template.open('resources', 'edit-resource');
	};

	$scope.deleteCurrentResourceType = function() {
		$scope.currentResourceType.resources.deselectAll();
		$scope.currentResourceType.delete();
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
		});
	};

	$scope.saveResource = function() {
		$scope.display.processing = true;
		$scope.editedResource.save(function(){
			$scope.display.processing = undefined;
			$scope.closeResource();
		});
	};

	$scope.closeResourceType = function() {
		$scope.editedResourceType = undefined;
		template.open('resources', 'manage-resources');
	};

	$scope.closeResource = function() {
		$scope.editedResource.type = undefined;
		$scope.editedResource = undefined;
		template.open('resources', 'manage-resources');
	};
}
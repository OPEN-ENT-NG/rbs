function RbsController($scope, template, model, date){
	
	$scope.template = template;
	$scope.me = model.me;
	$scope.date = date;

	$scope.display = {
		list: true
	};

	$scope.resourceTypes = model.resourceTypes;
	$scope.bookings = model.bookings;
	$scope.mine = model.mine;
	$scope.unprocessed = model.unprocessed;
	$scope.times = model.times;

	$scope.currentResourceType = undefined;

	template.open('main', 'main-view');
	template.open('bookings', 'main-list');

	model.one('loadResources', function(){
		$scope.resources = model.resources;
	});

	// Auto-select my bookings
	$scope.mine.bookings.one('sync', function(){
		$scope.mine.selected = true;
		$scope.bookings.pushAll($scope.mine.bookings.all);
	});


	// Navigation
	$scope.showManage = function() {
		$scope.currentResourceType = model.resourceTypes.first();
		model.resources.deselectAll();
		template.open('main', 'manage-view');
		template.open('resources', 'manage-resources');
	};

	$scope.showMain = function() {
		$scope.currentResourceType = undefined;
		model.resources.deselectAll();
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
		if (resourceType.expanded !== undefined) {
			resourceType.expanded = undefined;
		}
		else {
			resourceType.expanded = true;
		}
	};

	$scope.switchSelect = function(resource) {
		if (resource.selected !== true) {
			resource.selected = true;
			$scope.lastSelectedResource = resource;
		}
		else {
			resource.selected = undefined;
			$scope.lastSelectedResource = undefined;
		}
	};

	$scope.switchSelectMine = function() {
		if ($scope.mine.selected !== true) {
			$scope.mine.selected = true;
			$scope.bookings.pushAll($scope.mine.bookings.all);
		}
		else {
			$scope.mine.selected = undefined;
			$scope.bookings.pullAll($scope.mine.bookings.all);
		}
	}

	$scope.viewBooking = function(booking) {

	}

	// Booking edition
	$scope.newBooking = function() {
		$scope.editedBooking = new Booking();
		if ($scope.lastSelectedResource) {
			$scope.editedBooking.resource = $scope.lastSelectedResource;
			$scope.editedBooking.type = $scope.lastSelectedResource.type;
		}
		else {
			$scope.editedBooking.resource = $scope.resources.first();
			$scope.editedBooking.type = $scope.resources.first().type;
		}
		$scope.display.showPanel = true;
		template.open('lightbox', 'edit-booking');
	};

	$scope.editBooking = function() {
		$scope.editedBooking = $scope.bookings.selection()[0];

		// dates management
		var realStartDate = moment.unix($scope.editedBooking.start_date).toDate();
		var realEndDate = moment.unix($scope.editedBooking.end_date).toDate();
		$scope.editedBooking.start_date = realStartDate;
		$scope.editedBooking.end_date = realEndDate;

		$scope.display.showPanel = true;
		template.open('lightbox', 'edit-booking');
	};

	$scope.saveBooking = function() {
		$scope.display.processing = true;
		
		// dates management
		var realStartDate = moment($scope.editedBooking.start_date).add(moment($scope.editedBooking.start_date, 'HH:mm'));
		var realEndDate = moment($scope.editedBooking.end_date).add(moment($scope.editedBooking.end_date, 'HH:mm'));
		$scope.editedBooking.start_date = realStartDate.unix();
		$scope.editedBooking.end_date = realEndDate.unix();

		$scope.editedBooking.save(function(){
			$scope.display.processing = undefined;
			$scope.closeBooking();
		});
	};

	$scope.closeBooking = function() {
		$scope.display.showPanel = false;
		template.close('lightbox');
	};

	$scope.deleteBooking = function() {

	};

	$scope.validateBooking = function() {

	};

	$scope.refuseBooking = function() {

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
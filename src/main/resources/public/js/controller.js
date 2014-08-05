function RbsController($scope, template, model, date){
	
	$scope.template = template;
	$scope.me = model.me;
	$scope.date = date;

	$scope.display = {
		list: true
	};

	$scope.resourceTypes = model.resourceTypes;
	$scope.resources = model.resources;
	$scope.bookings = model.bookings;

	$scope.currentResourceType = {};

	template.open('main', 'main-view');
	template.open('bookings', 'main-list');

	$scope.showManage = function() {
		$scope.currentResourceType = model.resourceTypes.first();
		template.open('main', 'manage-view');
		template.open('resources', 'manage-resources');
	};

	$scope.showMain = function() {
		$scope.currentResourceType = {};
		template.open('main', 'main-view');
		template.open('bookings', 'main-list');
	};

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
		if (resource.selected !== undefined) {
			resource.selected = undefined;
		}
		else {
			resource.selected = true;
		}
	};

	$scope.selectResourceType = function(resourceType) {
		$scope.currentResourceType = resourceType;
	}

	$scope.newResourceType = function() {
		$scope.editedResourceType = new ResourceType();
		$scope.display.showPanel = true;
		template.open('lightbox', 'edit-resource-type');
	};

	$scope.newResource = function() {
		$scope.editedResource = new Resource();
		$scope.display.showPanel = true;
		template.open('lightbox', 'edit-resource');
	};

	$scope.saveResourceType = function() {
		// Default to user's classId
		$scope.editedResourceType.school_id = model.me.classId;

		$scope.display.processing = true;
		$scope.editedResourceType.create(function(){
			$scope.display.processing = undefined;
			$scope.closeResourceType();
		});
	}

	$scope.saveResource = function() {
		// Resolve typeId
		$scope.editedResource.type_id = $scope.selectedType.id;

		$scope.display.processing = true;
		$scope.editedResource.create(function(){
			$scope.display.processing = undefined;
			$scope.closeResource();
		});
	}

	$scope.closeResourceType = function() {
		$scope.editedResourceType = undefined;
		$scope.display.showPanel = undefined;
		template.close('lightbox');
	};

	$scope.closeResource = function() {
		$scope.editedResource = undefined;
		$scope.selectedType = undefined;
		$scope.display.showPanel = undefined;
		template.close('lightbox');
	};
}
function RbsController($scope, template, model, date){
	
	$scope.template = template;
	$scope.me = model.me;
	$scope.date = date;

	$scope.display = {
		list: true
	};

	$scope.resourceTypes = model.resourceTypes;

	// test
	$scope.bookings = {all: []};

	template.open('main', 'main-view');
	template.open('navigation', 'main-navigation');
	template.open('bookings', 'main-list');

	$scope.displayList = function() {
		$scope.display.list === true
		template.open('bookings', 'main-list');	
	};

	$scope.displayCalendar = function() {
		$scope.display.list === false
		template.open('bookings', 'main-calendar');
	};
}
module.directive('lightboxPlus', function($compile){
	return {
		restrict: 'E',
		transclude: true,
		scope: {
			show: '=',
			onClose: '&'
		},
		template: '<div>\
					<section class="lightbox-background"></section>\
					<section class="lightbox-view">\
						<div class="twelve cell" ng-transclude></div>\
						<div class="close-lightbox">\
						<i class="close-2x"></i>\
						</div>\
						<div class="clear"></div>\
					</section>\
				</div>',
		link: function(scope, element, attributes){
			element.find('.lightbox-background, i').on('click', function(){
				element.find('.lightbox-view').fadeOut();
				element.find('.lightbox-background').fadeOut();

				scope.$eval(scope.onClose);
				if(!scope.$$phase){
					scope.$parent.$apply();
				}
			});
			scope.$watch('show', function(newVal){
				if(newVal){
					var lightboxWindow = element.find('.lightbox-view');
					setTimeout(function(){
						lightboxWindow.fadeIn();
					}, 0);

					
					lightboxWindow.css({
						top: '10px'
					});
					
					/*
					lightboxWindow.css({
						top: Math.max(parseInt(($(window).height() / 2 - lightboxWindow.height() / 2)), 0) + 'px'
					});
					console.log("Lightbox-plus: Window height: " + $(window).height() + 'px');
					console.log("Lightbox-plus: Lightbox height: " + lightboxWindow.height() + 'px');
					console.log("Lightbox-plus: Computed top: " + Math.max(parseInt(($(window).height() / 2 - lightboxWindow.height() / 2)), 0) + 'px');
					*/
					var backdrop = element.find('.lightbox-background');
					setTimeout(function(){
						backdrop.fadeIn();
					}, 0);
				}
				else{
					element.find('.lightbox-view').fadeOut();
					element.find('.lightbox-background').fadeOut();
				}
			})
		}
	}
});

module.directive('datePickerRbs', function($compile){
	return {
		scope: {
			ngModel: '=',
			ngChange: '&',
			minDate: '=',
			past: '=',
			expObject: '=',
			exp: '='
		},
		transclude: true,
		replace: true,
		restrict: 'E',
		template: '<input ng-transclude type="text" data-date-format="dd/mm/yyyy"  />',
		link: function($scope, $element, $attributes){
			$scope.$watch('ngModel', function(newVal){
				if($scope.ngModel === null){
					$scope.ngModel = moment();
				}
				$element.val(moment($scope.ngModel).format('DD/MM/YYYY'));
				if($scope.past !== undefined && $scope.past === true){
					if($scope.minDate === undefined){
						$scope.minDate = moment();
					}
					if(moment($scope.minDate).unix() > moment($scope.ngModel).unix()){
						$element.val(moment($scope.minDate).format('DD/MM/YYYY'));
						$scope.ngModel = $scope.minDate;
					}
				}
				if($scope.exp !== undefined && $scope.exp === true){
					if(moment($scope.expObject).unix() < moment($scope.ngModel).unix()){
						$scope.expObject = $scope.ngModel;
					}
				}
			});
			loader.asyncLoad('/' + infraPrefix + '/public/js/bootstrap-datepicker.js', function(){
				$element.datepicker({
						dates: {
							months: moment.months(),
							monthsShort: moment.monthsShort(),
							days: moment.weekdays(),
							daysShort: moment.weekdaysShort(),
							daysMin: moment.weekdaysMin()
						}
					})
					.on('changeDate', function(){
						setTimeout(function(){
							var date = $element.val().split('/');
							var temp = date[0];
							date[0] = date[1];
							date[1] = temp;
							date = date.join('/');
							$scope.ngModel = new Date(date);
							$scope.$apply('ngModel');
							$scope.$parent.$eval($scope.ngChange);
							$scope.$parent.$apply();
						}, 10);

						$(this).datepicker('hide');
					});
				$element.datepicker('hide');
			});

			$element.on('focus', function(){
				var that = this;
				$(this).parents('form').on('submit', function(){
					$(that).datepicker('hide');
				});
				$element.datepicker('show');
			});

			$element.on('change', function(){
				var date = $element.val().split('/');
				var temp = date[0];
				date[0] = date[1];
				date[1] = temp;
				date = date.join('/');
				$scope.ngModel = new Date(date);
				$scope.$apply('ngModel');
				$scope.$parent.$eval($scope.ngChange);
				$scope.$parent.$apply();
			});
		}
	}
});
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
						<div class="twelve cell reduce-block-eight" ng-transclude></div>\
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
						top: '0px'
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

module.directive('behaviours', function($compile){
	return {
		restrict: 'E',
		template: '<div ng-transclude></div>',
		replace: false,
		transclude: true,
		scope: {
			resources: '='
		},
		link: function($scope, $element, $attributes){
			if(!$attributes.name){
				throw "Behaviour name is required";
			}
			var content = $element.children('div');
			$scope.$watch('resources', function(newVal){
				var hide = true;
				_.each($scope.resources, function(resource){
					var hide = ($scope.resource instanceof Array && _.find($scope.resource, function(resource){ return !resource.myRights || resource.myRights[$attributes.name] === undefined; }) !== undefined) ||
					($scope.resource instanceof Model && (!$scope.resource.myRights || !$scope.resource.myRights[$attributes.name]));
				});
				if(hide){
					content.remove();
				}
				else{
					$element.append(content);
				}

			});
		}
	}
});
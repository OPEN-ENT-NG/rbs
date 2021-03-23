import {_, moment, ng} from 'entcore';

declare let model: any;
declare let infraPrefix: any;

export const timePicker = ng.directive('timePickerRbs', function ($compile) {
    return {
        scope: {
            ngModel: '=',
            ngBegin: '=',
            ngEnd: '=',
            ngLimit: '='
        },
        transclude: true,
        replace: true,
        restrict: 'E',
        template: "<input type='text' />",
        link: function ($scope, $element, $attributes) {
            var hideFunction = function (e) {
                var timepicker = $element.data('timepicker');
                if (!timepicker || $element[0] === e.target || $('.bootstrap-timepicker-widget').find(e.target).length !== 0) {
                    return;
                }
                timepicker.hideWidget();
            };

            $('body, lightbox').on('click', hideFunction);
            $('body, lightbox').on('focusin', hideFunction);
            $element.timepicker({
                showMeridian: false,
                defaultTime: $scope.ngModel.isValid && $scope.ngModel.isValid() ? $scope.ngModel.format('HH:mm') : 'current',
                minuteStep: model.timeConfig.interval,
                minHour: model.timeConfig.start_hour,
                maxHour: model.timeConfig.end_hour
            });
            $scope.dateBkp = $scope.ngLimit && typeof $scope.ngLimit !== 'string' ? $scope.ngLimit.clone() : null;

            $scope.$watch('ngModel', function (newVal) {
                if (typeof newVal === 'string') {
                    $element.val(newVal);
                    $scope.ngModel = moment(newVal, 'HH:mm');
                    return;
                }

                $scope.ngModel = newVal;
                $element.val($scope.ngModel.format("HH:mm"));

                if (($scope.ngLimit !== undefined && !newVal.isSame($scope.ngLimit))
                    && (($scope.ngBegin === true && newVal.isAfter($scope.ngLimit)))
                ) {
                    $scope.ngLimit = moment(newVal);
                }
            });

            $element.on('change', function () {
                var time = $element.val().split(':');
                $scope.ngModel = $scope.dateBkp ? $scope.dateBkp.clone() : moment();
                $scope.ngModel.set('hour', time[0]);
                $scope.ngModel.set('minute', time[1]);
                $scope.$apply('ngModel');
                $scope.$parent.$eval($scope.ngChange);
                $scope.$parent.$apply();
            });

            $element.on('focus', function () {
                $element.timepicker('updateFromElementVal');
            });

            $element.on('show.timepicker', function () {
                var timepicker = $element.data('timepicker');
                if (!timepicker) {
                    return;
                }
                var lightbox = $element.parents().find('lightbox');
                if (lightbox) {
                    _.each(lightbox.find('.lightbox-view, .lightbox-background'), function (zone) {
                        $(zone).on('mousedown.timepicker, touchend.timepicker', function (e) {
                            if (!($element.parent().find(e.target).length ||
                                timepicker.$widget.is(e.target) ||
                                timepicker.$widget.find(e.target).length)) {
                                timepicker.hideWidget();
                            }
                        });
                    });
                }
            });

            $element.on('hide.timepicker', function () {
                var lightbox = $element.parents().find('lightbox');
                if (lightbox) {
                    _.each(lightbox.find('.lightbox-view, .lightbox-background'), function (zone) {
                        $(zone).off('mousedown.timepicker, touchend.timepicker');
                    });
                }
            });

            $element.on('$destroy', function () {
                $element.timepicker('remove');
            });
        }
    }
});

import {_, ng} from 'entcore';
import {ROOTS} from "../../core/const/roots.const";

interface IViewModel {
    $onInit(): any;
    $onDestroy(): any;

    editBooking(): void;
    removeBookingSelection(): void;
    validateBookingSelection(): void;
    refuseBookingSelection(): void;

    canEditBookingSelection(): boolean;
    removeBookingSelection(): void;
    canProcessBookingSelection(): boolean;

    // props
    bookings: any;
    display: any;
    onEditBooking(): any;
    onRemoveBooking(): any;
    onValidateBooking(): any;
    onRefuseBooking(): any;
}

export const bookingToggleButtonsSnackbar = ng.directive('bookingToggleButtonsSnackbar', function () {
    return {
        scope: {
            onEditBooking: '&',
            onRemoveBooking: '&',
            onValidateBooking: '&',
            onRefuseBooking: '&',
            bookings: '=',
            display: '='
        },
        restrict: 'E',
        templateUrl: `${ROOTS.directive}/booking-toggle-buttons-snackbar/booking-toggle-buttons-snackbar.html`,
        controllerAs: 'vm',
        bindToController: true,
        replace: false,
        controller: function () {
            const vm: IViewModel = <IViewModel>this;

            vm.$onInit = async () => {

            }

        },

        link: function ($scope) {
            const vm: IViewModel = $scope.vm;

            vm.editBooking = (): void => {
                $scope.$eval(vm.onEditBooking);
            };

            vm.removeBookingSelection = (): void => {
                $scope.$eval(vm.onRemoveBooking);
            };

            vm.validateBookingSelection = (): void => {
                $scope.$eval(vm.onValidateBooking);
            };

            vm.refuseBookingSelection = (): void => {
                $scope.$eval(vm.onRefuseBooking);
            };

            vm.canEditBookingSelection = (): boolean => {
                if (vm.display.list === true) {
                    let localSelection = _.filter(vm.bookings.selection(), function (booking) {
                        return booking.isBooking();
                    });
                    return (localSelection.length === 1 && localSelection[0].resource.is_available === true);
                } else {
                    return (vm.bookings.selection().length === 1 &&
                        vm.bookings.selection()[0].resource.is_available === true
                    );
                }
            };

            vm.canProcessBookingSelection = (): boolean => {
                return _.every(vm.bookings.selection(), function (booking) {
                    return booking.isPending();
                });
            };


            vm.$onDestroy = async () => {

            };
        }
    };
});
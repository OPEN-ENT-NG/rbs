import {_, ng, template, idiom as lang} from 'entcore';
import {ROOTS} from "../../core/const/roots.const";
import {RBS} from "../../models/models";
import {BOOKING_EVENTER} from "../../core/enum/booking-eventer.enum";
import {BookingEventService} from "../../services";

const {Booking, Resource, ResourceType} = RBS;

declare let model: any;

interface IViewModel {
    $onInit(): any;
    $onDestroy(): any;

    editBooking(): void;

    toggleLightbox(state: boolean): void;

    canEditBookingSelection(): boolean;
    removeBookingSelection(): void;

    selectBooking(currentBooking): any;

    validateBookings(): void;
    showActionErrors(): void;
    doRemoveBookingSelection(): void;
    doRemoveCurrentPeriodicBookingSelection(): void;
    doRemoveCurrentAndFuturBookingSelection(): void;
    doRefuseBookingSelection(): void;

    isErrorObjectResourceType(object): boolean;
    isErrorObjectResource(object): boolean;
    isErrorObjectBooking(obj): boolean;

    formatMoment(date: any): string;

    // props
    templatePathState: string;
    status: any;
    bookings: any;
    display: any;
    selectedBooking: any;
    isViewBooking: any;
    currentBookingSelected: any;
    processBookings: any;
    currentErrors: any;
    onShowActionErrors();
}

export const bookingStateAction = ng.directive('bookingStateAction', ['BookingEventService', '$timeout',
    (bookingEventService: BookingEventService, $timeout) => {
    return {
        scope: {
            onShowActionErrors: '&',
            status: '=',
            bookings: '=',
            templatePathState: '=',
            display: '=',
            selectedBooking: '=',
            isViewBooking: '=',
            currentBookingSelected: '=',
            processBookings: '=',
            currentErrors: '='
        },
        restrict: 'E',
        templateUrl: `${ROOTS.directive}/booking-state-action/booking-state-action.html`,
        controllerAs: 'vm',
        bindToController: true,
        replace: false,
        controller: function () {
            const vm: IViewModel = <IViewModel>this;

            vm.$onInit = async () => {
                template.open("booking-state-action", `${ROOTS.directiveTemplate}/booking-state-action/${vm.templatePathState}`);
            }

        },

        link: function ($scope) {
            const vm: IViewModel = $scope.vm;

            vm.toggleLightbox = (state: boolean): void => {
                if (!state) {
                    bookingEventService.sendState(BOOKING_EVENTER.CLOSE);
                    template.close("booking-state-action");
                }
            };

            vm.showActionErrors = (): void => {
                $scope.$eval(vm.onShowActionErrors);
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

            vm.selectBooking = (currentBooking: any): any => {
                if (currentBooking.is_periodic === true) {
                    return vm.selectedBooking._slots;
                } else {
                    return [vm.selectedBooking];
                }
            };

            vm.validateBookings = function () {
                vm.display.processing = true;
                vm.currentErrors = [];
                try {
                    var actions = vm.processBookings.length;
                    _.each(vm.processBookings, function (booking) {
                        booking.validate(
                            function () {
                                actions--;
                                if (actions === 0) {
                                    vm.display.processing = undefined;
                                    vm.bookings.deselectAll();
                                    vm.toggleLightbox(false);
                                    model.refreshBookings(vm.display.list);
                                }
                            },
                            function (e) {
                                vm.currentErrors.push(e);
                                actions--;
                                if (actions === 0) {
                                    vm.display.processing = undefined;
                                    vm.showActionErrors();
                                    model.refreshBookings(vm.display.list);
                                }
                            }
                        );
                    });
                } catch (e) {
                    vm.display.processing = undefined;
                    vm.currentErrors.push({error: 'rbs.error.technical'});
                }
            };

            vm.doRemoveBookingSelection = (): void => {
                vm.display.processing = true;
                vm.currentErrors = [];
                vm.processBookings = vm.bookings.selectionForDelete();
                if (!vm.processBookings.length) {
                    vm.processBookings = vm.selectBooking(vm.selectedBooking);
                }
                try {
                    var actions = vm.processBookings.length;
                    _.each(vm.processBookings, function (booking) {
                        // booking = semanticObject(booking, Booking);
                        booking.delete(
                            function () {
                                actions--;
                                if (actions === 0) {
                                    vm.display.processing = undefined;
                                    vm.bookings.deselectAll();
                                    vm.toggleLightbox(false);
                                    model.refreshBookings(vm.display.list);
                                }
                            },
                            function (e) {
                                vm.currentErrors.push(e);
                                actions--;
                                if (actions === 0) {
                                    vm.display.processing = undefined;
                                    vm.showActionErrors();
                                    model.refreshBookings(vm.display.list);
                                }
                            }
                        );
                    });
                } catch (e) {
                    vm.display.processing = undefined;
                    vm.currentErrors.push({error: 'rbs.error.technical'});
                }
            };

            vm.doRemoveCurrentPeriodicBookingSelection = () => {
                vm.display.processing = true;
                vm.currentErrors = [];
                try {
                    vm.currentBookingSelected.delete(
                        function () {
                            vm.display.processing = undefined;
                            vm.bookings.deselectAll();
                            vm.toggleLightbox(false);
                            model.refreshBookings(vm.display.list);
                        },
                        function (e) {
                            vm.currentErrors.push(e);
                            vm.display.processing = undefined;
                            vm.showActionErrors();
                            model.refreshBookings(vm.display.list);
                        }
                    );
                } catch (e) {
                    vm.display.processing = undefined;
                    vm.currentErrors.push({error: 'rbs.error.technical'});
                }
            };

            vm.doRemoveCurrentAndFuturBookingSelection = () => {
                vm.display.processing = true;
                vm.currentErrors = [];
                try {
                    vm.currentBookingSelected.deletePeriodicCurrentToFuture(
                        function () {
                            vm.display.processing = undefined;
                            vm.bookings.deselectAll();
                            vm.toggleLightbox(false);
                            model.refreshBookings(vm.display.list);
                        },
                        function (e) {
                            vm.currentErrors.push(e);
                            vm.display.processing = undefined;
                            vm.showActionErrors();
                            model.refreshBookings(vm.display.list);
                        }
                    );
                } catch (e) {
                    console.error(e);
                    vm.display.processing = undefined;
                    vm.currentErrors.push({error: 'rbs.error.technical'});
                }
            };

            vm.doRefuseBookingSelection = (): void => {
                vm.display.processing = true;
                vm.currentErrors = [];
                try {
                    var actions = vm.processBookings.length;
                    _.each(vm.processBookings, function (booking) {
                        booking.refusal_reason = vm.bookings.refuseReason;
                        booking.refuse(
                            function () {
                                actions--;
                                if (actions === 0) {
                                    vm.display.processing = undefined;
                                    vm.bookings.deselectAll();
                                    vm.bookings.refuseReason = undefined;
                                    vm.toggleLightbox(false);
                                    model.refreshBookings(vm.display.list);
                                }
                            },
                            function (e) {
                                vm.currentErrors.push(e);
                                actions--;
                                if (actions === 0) {
                                    vm.display.processing = undefined;
                                    vm.showActionErrors();
                                    model.refreshBookings(vm.display.list);
                                }
                            }
                        );
                    });
                } catch (e) {
                    vm.display.processing = undefined;
                    vm.currentErrors.push({error: 'rbs.error.technical'});
                }
            };

            vm.isErrorObjectResourceType = (object): boolean => {
                return object instanceof ResourceType;
            };

            vm.isErrorObjectResource = (object): boolean => {
                return object instanceof Resource;
            };

            vm.isErrorObjectBooking = (object): boolean => {
                return object instanceof Booking;
            };

            vm.formatMoment = function (date) {
                return (
                    date.format('DD/MM/YYYY ') +
                    lang.translate('rbs.booking.details.header.at') +
                    date.format(' H[h]mm')
                );
            };
            
            vm.$onDestroy = async () => {
                bookingEventService.unsubscribe();
            };
        }
    };
}]);
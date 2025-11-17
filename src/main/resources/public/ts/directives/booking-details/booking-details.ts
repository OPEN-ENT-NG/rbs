import {_, ng, idiom as lang} from 'entcore';
import {ROOTS} from "../../core/const/roots.const";
import moment from "../../moment";

declare let window: any;

interface IViewModel {
    $onInit(): any;
    $onDestroy(): any;

    formatMoment(date: any): string;
    formatMomentLong(date: any): string;
    formatDateLong(date: any): string;

    composeTitle(typeTitle: any, resourceTitle: any): string;

    countRefusedSlots(slots: any): any;
    countValidatedSlots(slots: any): any;

    canDeleteBookingDateCheck(date: any): boolean;
    hasResourceRight(resource: any, right: any): boolean;

    switchExpandSlot(slot: any): void;
    translate(text: string): string;

    refuseBookingSelection(): void;
    validateBookingSelection(): void;
    removeBookingSelection(): void;
    editBooking(): void;

    // props
    selectedBooking: any;
    currentBookingSelected: any;
    onRefuseBookingSelection(): void;
    onValidateBookingSelection(): void;
    onRemoveBookingSelection(): void;
    onEditBooking(): void;
}

export const bookingDetails = ng.directive('bookingDetails', function () {
    return {
        scope: {
            selectedBooking: '=',
            currentBookingSelected: '=',
            onRefuseBookingSelection: '&',
            onValidateBookingSelection: '&',
            onRemoveBookingSelection: '&',
            onEditBooking: '&',
        },
        restrict: 'E',
        templateUrl: `${ROOTS.directive}/booking-details/booking-details.html`,
        controllerAs: 'vm',
        bindToController: true,
        replace: false,
        controller: function () {
            const vm: IViewModel = <IViewModel>this;

            vm.$onInit = async () => {
                vm.selectedBooking.type = vm.selectedBooking.resource.type;
            }

        },

        link: function ($scope) {
            const vm: IViewModel = $scope.vm;

            vm.editBooking = (): void => {
                $scope.$eval(vm.onEditBooking);
            };

            vm.removeBookingSelection = (): void => {
                $scope.$eval(vm.onRemoveBookingSelection);
            };

            vm.validateBookingSelection = (): void => {
                $scope.$eval(vm.onValidateBookingSelection);
            };

            vm.refuseBookingSelection = (): void => {
                $scope.$eval(vm.onRefuseBookingSelection);
            };

            vm.formatMoment = (date: any): string => {
                return (
                    date.format('DD/MM/YYYY ') +
                    lang.translate('rbs.booking.details.header.at') +
                    date.format(' H[h]mm')
                );
            };

            vm.formatMomentLong = (date: any): string => {
                return date.locale(window.navigator.language).format('dddd DD MMMM YYYY - HH[h]mm');
            };

            vm.formatDateLong = (date): string => {
                return vm.formatMomentLong(moment(date));
            };

            vm.composeTitle = (typeTitle, resourceTitle): string => {
                let title = lang.translate('rbs.booking.no.resource');
                if (typeTitle && resourceTitle) {
                    title = typeTitle + ' - ' + resourceTitle;
                }

                return _.isString(title) ? title.trim().length > 50 ? title.substring(0, 47) + '...' : title.trim() : '';
            };

            vm.countRefusedSlots = (slots: any): any => {
                return _.filter(slots, function (slot) {
                    return slot.isRefused();
                }).length;
            };

            vm.countValidatedSlots = (slots: any): any => {
                return _.filter(slots, function (slot) {
                    return slot.isValidated();
                }).length;
            };

            vm.canDeleteBookingDateCheck = (date: any): boolean => {
                var itemDate = moment(date);
                return moment().diff(itemDate) <= 0;
            };

            vm.hasResourceRight = (resource: any, right: any): boolean => {
                return !(!resource.myRights || resource.myRights[right] === undefined);
            };

            vm.switchExpandSlot = (slot: any): void => {
                if (slot.expanded !== true) {
                    slot.expanded = true;
                } else {
                    slot.expanded = undefined;
                }
            };

            vm.translate = (text: string): string => {
                return lang.translate(text);
            };

            vm.$onDestroy = async () => {

            };
        }
    };
});
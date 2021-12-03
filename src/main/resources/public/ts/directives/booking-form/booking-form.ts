import {_, ng, moment, notify, idiom as lang} from 'entcore';
import {ROOTS} from "../../core/const/roots.const";
import {RBS} from "../../models/models";
import {DateUtils} from "../../utilities/date.util";
import {ArrayUtil} from "../../utilities/array.util";
import {BookingUtil} from "../../utilities/booking";
import {HandleUtil} from "../../utilities/handle.util";
import {BOOKING_EVENTER} from "../../core/enum/booking-eventer.enum";
import {BookingEventService} from "../../services";

const {Booking, Slot, SlotJson} = RBS;

declare let model: any;
declare let window: any;

interface IViewModel {
    $onInit(): any;
    $onDestroy(): any;

    toggleLightbox(state: boolean): void;
    composeTitle(typeTitle: any, resourceTitle: any): string;

    initEditBookingDisplay(): void;
    initModerators(): void;
    initPeriodic(): void;

    newBooking(): void;

    autoSelectTypeAndResource(): void;
    autoSelectResource(): void;

    getMomentFromDate(): void;

    initBookingDates(startMoment: any, endMoment: any): void;
    initQuantities();
    
    switchStructure(struct: any): void;
    switchSlotStart(slot: any): void;
    switchSlotEnd(slot: any): void;

    updatePeriodicSummary(): void;
    summaryBuildDays(days: any): any;
    summaryWriteRange(summary: any, first: any, last: any): string;
    
    // quantity/availability methods
    updateQuantitiesAvailable(): void;

    // utils
    formatTextBookingQuantity(): string;
    formatBooking(date: any, time: any): string;
    startDateModif(): any;
    editPeriodicStartDate(): void;
    checkDateFunction(): void;

    togglePeriodic(): void;

    formatMomentDayLong(date: any): string;

    isBookingQuantityWrong(booking: any): boolean;
    saveBookingSlotProfile(): void;

    resolveSlotsSelected(start: any, end: any): boolean;
    checkSaveBooking(): boolean;
    saveBooking(): void;
    resolvePeriodicMoments(): void;
    closeBooking(): void;

    editBooking(): void;
    newBookingCalendar(): void;
    initQuantities(): void;
    translate(text: string): string;


    tempQuantities: any;
    editedBooking: any;
    displayLightbox: boolean;
    selectedStructure: any;

    slotProfilesComponent: any;
    selectedSlotStart: any;
    selectedSlotEnd: any;

    saveTime: any;

    // quantity/availability variables
    tempPeriodicBookings: Array<any>;
    slotNotFound: boolean;

    /* props */
    today: any;
    structuresWithTypes: Array<any>;
    sharedStructure: any;
    periods: any;
    slots: any;
    
    resourceTypes: any;
    display: any;
    booking: any;
    currentErrors: Array<any>;
    showDaySelection: boolean;
    selectedBooking: any;
    bookings: any;
    processBookings: any;
}

export const bookingForm = ng.directive('bookingForm', ['BookingEventService', '$timeout',
    (bookingEventService: BookingEventService, $timeout) => {
    return {
        scope: {
            today: '=',
            periods: '=',
            slots: '=',
            display: "=",
            structuresWithTypes: '=',
            resourceTypes: "=",
            sharedStructure: '=',
            booking: "=",
            currentErrors: "=",
            showDaySelection: "=",
            selectedBooking: "=",
            bookings: "=",
            slotProfilesComponent: "=",
            processBookings: "="
        },
        restrict: 'E',
        templateUrl: `${ROOTS.directive}booking-form/booking-form.html`,
        controllerAs: 'vm',
        bindToController: true,
        controller: function ($scope) {
            const vm: IViewModel = <IViewModel>this;

            vm.$onInit = async () => {
                vm.tempQuantities = {
                    resourceQuantityAvailable: undefined, // Quantity available to use for bookings on a specific period
                    bookingQuantityAvailable: undefined // Previous - quantities already used by other bookings on this specific period
                };

                switch (window.bookingState) {
                    case BOOKING_EVENTER.CREATE:
                        vm.newBooking();
                        break;
                    case BOOKING_EVENTER.CREATE_CALENDAR:
                        vm.newBookingCalendar();
                        break;
                    case BOOKING_EVENTER.EDIT:
                        vm.editBooking();
                        break;
                }
            };

            vm.newBooking = (): void => {
                vm.display.processing = undefined; // add as props from controller
                vm.editedBooking = new Booking();
                vm.saveTime = undefined;
                vm.initEditBookingDisplay();
                vm.initModerators();

                // periodic booking
                vm.editedBooking.is_periodic = false; // false by default

                vm.selectedStructure = vm.structuresWithTypes[0]; // add as props from controller
                vm.autoSelectTypeAndResource();

                // dates
                vm.editedBooking.startMoment = moment();
                vm.editedBooking.endMoment = moment();
                vm.editedBooking.endMoment.hour(vm.editedBooking.startMoment.hour() + 1);
                vm.editedBooking.startMoment.seconds(0);
                vm.editedBooking.endMoment.seconds(0);

                vm.initBookingDates(vm.editedBooking.startMoment, vm.editedBooking.endMoment);

                // vm.initQuantities();
                // vm.bookings.syncForShowList();

                // vm.display.showPanel = true;
            };

            vm.newBookingCalendar = (): void => {
                vm.display.processing = undefined;
                vm.editedBooking = new Booking();
                vm.initEditBookingDisplay();

                vm.initModerators();

                vm.selectedStructure = vm.structuresWithTypes[0];
                vm.autoSelectTypeAndResource();

                // dates
                if (model.calendar.newItem !== undefined) {
                    vm.editedBooking.startMoment = model.calendar.newItem.beginning;
                    vm.editedBooking.startMoment.minutes(0);
                    vm.editedBooking.endMoment = model.calendar.newItem.end;
                    vm.editedBooking.endMoment.minutes(0);
                    vm.saveTime = {
                        startHour: model.calendar.newItem.beginning._d.getHours(),
                        endHour: model.calendar.newItem.end._d.getHours()
                    }
                } else {
                    vm.editedBooking.startMoment = moment();
                    vm.editedBooking.endMoment = moment();
                    vm.editedBooking.endMoment.hour(
                        vm.editedBooking.startMoment.hour() + 1
                    );
                }

                vm.editedBooking.startMoment.seconds(0);
                vm.editedBooking.endMoment.seconds(0);

                vm.initBookingDates(vm.editedBooking.startMoment, vm.editedBooking.endMoment);
                vm.initQuantities();
                vm.displayLightbox = true;
            };

            vm.initQuantities = (): void => {
                if (vm.editedBooking.resource.quantity === undefined) {
                    vm.editedBooking.resource.quantity = 1;
                }
                vm.editedBooking.quantity = 1;
                vm.updateQuantitiesAvailable();
            };

            vm.updateQuantitiesAvailable = (): void => {
                let booking = vm.editedBooking;
                updateEditedBookingMoments();

                if (booking.startMoment.isSame(booking.endMoment)) {
                    vm.tempQuantities.resourceQuantityAvailable = 0;
                    vm.tempQuantities.bookingQuantityAvailable = 0;
                }
                else {
                    if (booking.is_periodic) {
                        calculatePeriodicBookings();
                    }
                    updateResourceQuantityAvailableByPeriod(booking.resource, booking.startMoment, booking.endMoment);
                    updateBookingQuantityAvailableByPeriod(vm.tempQuantities.resourceQuantityAvailable, booking);
                }
            };

            const updateEditedBookingMoments = function() : void {
                vm.currentErrors = [];

                vm.editedBooking.startDate = vm.booking.startDate;
                vm.editedBooking.endDate = vm.booking.endDate;
                vm.editedBooking.startTime = vm.booking.startTime;
                vm.editedBooking.endTime = vm.booking.endTime;

                // Formats time if they are strings
                if (typeof vm.editedBooking.startTime === 'string') {
                    const time = vm.editedBooking.startTime.split(':');
                    vm.editedBooking.startTime = moment().set('hour', time[0]).set('minute', time[1]);
                }
                if (typeof vm.editedBooking.endTime === 'string') {
                    const time = vm.booking.endTime.split(':');
                    vm.editedBooking.endTime = moment().set('hour', time[0]).set('minute', time[1]);
                }

                try {
                    if (BookingUtil.checkEditedBookingMoments(vm.editedBooking, vm.today, vm.currentErrors)) {
                        return;
                    }
                    vm.editedBooking.startMoment = DateUtils.formatMoment(vm.editedBooking.startDate, vm.editedBooking.startTime);
                    vm.editedBooking.endMoment = DateUtils.formatMoment(vm.editedBooking.endDate, vm.editedBooking.endTime);
                }
                catch (e) {
                    vm.currentErrors.push({error: 'rbs.error.technical'});
                    throw e;
                }
            };

            const updateResourceQuantityAvailableByPeriod = (resource, startMoment, endMoment) : void => {
                vm.tempQuantities.resourceQuantityAvailable = vm.editedBooking.resource.quantity;

            };

            const updateBookingQuantityAvailableByPeriod = (quantity:number, booking) : void => {
                if (quantity <= 0) {
                    vm.tempQuantities.bookingQuantityAvailable = 0;
                }
                else {
                    vm.tempQuantities.bookingQuantityAvailable = quantity;
                    if (booking.is_periodic) {
                        vm.tempPeriodicBookings.forEach(function(slot) {
                            let localQuantity = quantity;
                            booking.resource.bookings.forEach(function (b) {
                                if (!b.is_periodic && b.id != booking.id && slot.startMoment < b.endMoment && slot.endMoment > b.startMoment) {
                                    localQuantity -= b.quantity;
                                }
                            });
                            if (localQuantity < vm.tempQuantities.bookingQuantityAvailable) {
                                vm.tempQuantities.bookingQuantityAvailable = localQuantity;
                            }
                        });
                    }
                    else {
                        booking.resource.bookings.forEach(function (b) {
                            if (!b.is_periodic && b.id != booking.id && booking.startMoment < b.endMoment && booking.endMoment > b.startMoment) {
                                vm.tempQuantities.bookingQuantityAvailable -= b.quantity;
                            }
                        });
                    }
                    if (vm.tempQuantities.bookingQuantityAvailable <= 0) {
                        vm.tempQuantities.bookingQuantityAvailable = 0;
                    }
                }
            };

            const calculatePeriodicBookings = function() : void {
                vm.tempPeriodicBookings = [];
                let currentMoment = moment(vm.editedBooking.startMoment);
                let currentMonday = currentMoment.subtract(currentMoment.weekday(), 'days');
                let diff = vm.editedBooking.endMoment.diff(vm.editedBooking.startMoment);
                let tempBook = new Booking();

                // Get days checked
                let days = [];
                for (let i = 0; i < vm.editedBooking.periodDays.length; i++) {
                    if (vm.editedBooking.periodDays[i].value) {
                        days.push(i);
                    }
                }

                // Get tempPeriodicBookings

                // todo this code triggers an infinite loop
                // if (vm.editedBooking.byOccurrences) {
                //     while(vm.tempPeriodicBookings.length < vm.editedBooking.occurrences) {
                //         days.forEach(function(day) {
                //             if (vm.tempPeriodicBookings.length < vm.editedBooking.occurrences) {
                //                 tempBook.startMoment = moment(currentMonday);
                //                 tempBook.endMoment = moment(currentMonday);
                //                 tempBook.startMoment.add(day, 'days');
                //                 tempBook.endMoment.add(day, 'days').add(diff);
                //                 tempBook.quantity = vm.editedBooking.quantity;
                //                 if (tempBook.startMoment >= vm.editedBooking.startMoment) {
                //                     vm.tempPeriodicBookings.push(tempBook);
                //                 }
                //                 tempBook = new Booking();
                //             }
                //         });
                //         currentMonday.add(vm.editedBooking.periodicity, 'weeks');
                //     }
                // }
                // else {
                //     while(currentMoment < moment(vm.booking.periodicEndDate)) {
                //         days.forEach(function(day) {
                //             if (currentMoment < moment(vm.booking.periodicEndDate)) {
                //                 tempBook.startMoment = currentMonday;
                //                 tempBook.endMoment = currentMonday;
                //                 tempBook.startMoment.add(day, 'days');
                //                 tempBook.endMoment.add(day, 'days');
                //                 tempBook.quantity = vm.editedBooking.quantity;
                //                 if (tempBook.startMoment >= vm.editedBooking.startMoment) {
                //                     vm.tempPeriodicBookings.push(tempBook);
                //                 }
                //                 currentMoment = tempBook.startMoment;
                //                 tempBook = new Booking();
                //             }
                //         });
                //         currentMonday.add(vm.editedBooking.periodicity, 'weeks');
                //     }
                // }
            };

            vm.initEditBookingDisplay = (): void => {
                vm.editedBooking.display = {
                    state: 0,
                    STATE_RESOURCE: 0,
                    STATE_BOOKING: 1,
                    STATE_PERIODIC: 2,
                };
            };

            vm.initModerators = (): void => {
                if (vm.resourceTypes.first() === undefined || vm.resourceTypes.first().moderators === undefined) {
                    vm.resourceTypes.forEach(function (resourceType) {
                        resourceType.getModerators(function () {
                            $scope.$apply('resourceTypes');
                        });
                    });
                }
            };

            vm.initBookingDates = (startMoment, endMoment): void => {
                // hours minutes management
                var minTime = moment(startMoment);
                minTime.set('hour', model.timeConfig.start_hour);
                var maxTime = moment(endMoment);
                maxTime.set('hour', model.timeConfig.end_hour);
                if (startMoment.isAfter(minTime) && startMoment.isBefore(maxTime)) {
                    vm.booking.startTime = startMoment;
                    if (vm.selectedSlotStart) {
                        vm.booking.startTime.set('hour', vm.selectedSlotStart.startHour.split(':')[0]);
                        vm.booking.startTime.set('minute', vm.selectedSlotStart.startHour.split(':')[1]);
                    }
                } else {
                    vm.booking.startTime = minTime;
                    if (startMoment.isAfter(maxTime)) {
                        startMoment.add('day', 1);
                        endMoment.add('day', 1);
                        maxTime.add('day', 1);
                    }
                }
                if (endMoment.isBefore(maxTime)) {
                    vm.booking.endTime = endMoment;
                    if (vm.selectedSlotStart) {
                        vm.booking.endTime.set('hour', vm.selectedSlotStart.endHour.split(':')[0]);
                        vm.booking.endTime.set('minute', vm.selectedSlotStart.endHour.split(':')[1]);
                    }
                } else {
                    vm.booking.endTime = maxTime;
                }

                // dates management

                // setter startDate booking
                vm.booking.startDate = startMoment.toDate();
                vm.booking.startDate.setFullYear(startMoment.years());
                vm.booking.startDate.setMonth(startMoment.months());
                vm.booking.startDate.setDate(startMoment.date());

                // setter endDate booking
                vm.booking.endDate = endMoment.toDate();
                vm.booking.endDate.setFullYear(endMoment.years());
                vm.booking.endDate.setMonth(endMoment.months());
                vm.booking.endDate.setDate(endMoment.date());

                // setter periodicEndDate
                vm.booking.periodicEndDate = endMoment.toDate();
            };

            vm.autoSelectTypeAndResource = (): void => {
                vm.editedBooking.type = undefined;
                vm.editedBooking.resource = undefined;
                vm.selectedSlotStart = undefined;
                vm.selectedSlotEnd = undefined;
                if (vm.selectedStructure.types.length > 0) {
                    vm.editedBooking.type = vm.selectedStructure.types[0];
                    vm.autoSelectResource();
                }
            };

            vm.autoSelectResource = (): void => {
                vm.editedBooking.resource =
                    vm.editedBooking.type === undefined ? undefined :
                        _.first(vm.editedBooking.type.resources.filterAvailable(vm.editedBooking.is_periodic));

                if (vm.editedBooking.type !== undefined && vm.editedBooking.type.slotprofile !== undefined) {
                    vm.slotProfilesComponent.getSlots(vm.editedBooking.type.slotprofile, function (data) {
                        if (data.slots.length > 0) {
                            vm.editedBooking.slotsLit = data;
                            vm.editedBooking.slotsLit.slots.sort(
                                ArrayUtil.sort_by('startHour', false, function (a) {
                                    return a.toUpperCase();
                                })
                            );
                            vm.selectedSlotStart = vm.editedBooking.slotsLit.slots[0];
                            vm.selectedSlotEnd = vm.editedBooking.slotsLit.slots[0];
                            vm.booking.startTime.set('hour', vm.selectedSlotStart.startHour.split(':')[0]);
                            vm.booking.startTime.set('minute', vm.selectedSlotStart.startHour.split(':')[1]);
                            vm.booking.endTime.set('hour', vm.selectedSlotEnd.endHour.split(':')[0]);
                            vm.booking.endTime.set('minute', vm.selectedSlotEnd.endHour.split(':')[1]);
                        } else {
                            vm.editedBooking.type.slotprofile = undefined;
                        }
                        $scope.$apply();
                    });
                } else if (vm.editedBooking.type !== undefined && vm.saveTime) {
                    vm.booking.startTime.set('hour', vm.saveTime.startHour -
                        parseInt(DateUtils.formatMoment(vm.booking.startDate, vm.booking.startTime).format('Z').split(':')[0]));

                    vm.booking.startTime.set('minute', 0);

                    vm.booking.endTime.set('hour', vm.saveTime.endHour -
                        parseInt(DateUtils.formatMoment(vm.booking.startDate, vm.booking.startTime).format('Z').split(':')[0]));

                    vm.booking.endTime.set('minute', 0);
                }
            };

            vm.editBooking = (): void => {
                vm.display.processing = undefined;
                vm.selectedSlotStart = undefined;
                vm.selectedSlotEnd = undefined;
                vm.slotNotFound = undefined;
                vm.currentErrors = [];
                vm.bookings.syncForShowList();

                if (vm.selectedBooking !== undefined) {
                    vm.editedBooking = vm.selectedBooking;
                } else {
                    vm.editedBooking = vm.bookings.selection()[0];
                    if (!vm.editedBooking.isBooking()) {
                        vm.editedBooking = vm.editedBooking.booking;
                    }
                }
                vm.initEditBookingDisplay();

                // periodic booking
                if (vm.editedBooking.is_periodic === true) {
                    if (vm.editedBooking.occurrences !== undefined && vm.editedBooking.occurrences > 0) {
                        vm.editedBooking.byOccurrences = true;
                    } else {
                        vm.editedBooking.byOccurrences = false;
                    }
                    vm.editedBooking._slots = _.sortBy(vm.editedBooking._slots, 'id');
                    vm.editedBooking.startMoment = vm.editedBooking._slots[0].startMoment;
                    vm.editedBooking.startMoment.date(vm.editedBooking.beginning.date());
                    vm.editedBooking.startMoment.month(vm.editedBooking.beginning.month());
                    vm.editedBooking.startMoment.year(vm.editedBooking.beginning.year());
                    vm.editedBooking.endMoment = vm.editedBooking._slots[0].endMoment;
                }

                vm.initBookingDates(vm.editedBooking.startMoment, vm.editedBooking.endMoment);
                // change periodicEndDate (will affect ocurence/recurence end_date)
                vm.booking.periodicEndDate = new Date(vm.editedBooking.end_date);

                vm.editedBooking.type = vm.editedBooking.resource.type;
                if (
                    vm.editedBooking.type !== undefined &&
                    vm.editedBooking.type.slotprofile !== undefined
                ) {
                    vm.slotProfilesComponent.getSlots(
                        vm.editedBooking.type.slotprofile,
                        function (data) {
                            if (data.slots.length > 0) {
                                vm.slots = data;
                                vm.editedBooking.slotsLit = data;
                                vm.slots.slots.sort(
                                    ArrayUtil.sort_by('startHour', false, function (a) {
                                        return a;
                                    })
                                );
                                vm.selectedSlotStart = vm.slots.slots
                                    .filter(function (slot) {
                                        return (
                                            slot.startHour.split(':')[0] ==
                                            vm.editedBooking.startMoment.hour() &&
                                            slot.startHour.split(':')[1] ==
                                            vm.editedBooking.startMoment.minute()
                                        );
                                    })
                                    .pop();
                                vm.selectedSlotEnd = vm.selectedSlotStart;
                                if (vm.selectedSlotStart === undefined) {
                                    vm.selectedSlotStart = vm.slots.slots[0];
                                    vm.selectedSlotEnd = vm.selectedSlotStart;
                                    vm.slotNotFound = true;
                                }
                            } else {
                                vm.editedBooking.type.slotprofile = undefined;
                            }
                        }
                    );
                }
                vm.updatePeriodicSummary();
            };

            vm.updatePeriodicSummary = (): void => {
                vm.editedBooking.periodicSummary = '';
                vm.editedBooking.periodicShortSummary = '';
                vm.editedBooking.periodicError = undefined;

                vm.editedBooking.periodicShortSummary = lang.translate(
                    'rbs.period.days.some'
                );

                if (vm.showDaySelection) {
                    // Selected days
                    var selected = 0;
                    _.each(vm.editedBooking.periodDays, function (d) {
                        selected += d.value ? 1 : 0;
                    });
                    if (selected == 0) {
                        // Error in periodic view
                        vm.editedBooking.periodicError = lang.translate(
                            'rbs.period.error.nodays'
                        );
                        return;
                    } else if (selected == 7) {
                        vm.editedBooking.periodicSummary = lang.translate(
                            'rbs.period.days.all'
                        );
                        vm.editedBooking.periodicShortSummary =
                            vm.editedBooking.periodicSummary;
                    } else {
                        vm.editedBooking.periodicSummary += vm.summaryBuildDays(vm.editedBooking.periodDays);
                    }
                }

                // Weeks
                var summary = ', ';
                if (vm.editedBooking.periodicity == 1) {
                    summary += lang.translate('rbs.period.weeks.all') + ', ';
                } else {
                    summary +=
                        lang.translate('rbs.period.weeks.partial') + ' ' +
                        lang.translate('rbs.period.weeks.' + vm.editedBooking.periodicity) +
                        ', ';
                }

                // Occurences or date
                if (vm.editedBooking.byOccurrences) {
                    summary +=
                        lang.translate('rbs.period.occurences.for') + ' ' +
                        vm.editedBooking.occurrences + ' ' +
                        lang.translate(
                            'rbs.period.occurences.slots.' +
                            (vm.editedBooking.occurrences > 1 ? 'many' : 'one')
                        );
                } else {
                    summary +=
                        lang.translate('rbs.period.date.until') + ' ' +
                        vm.formatMomentDayLong(moment(vm.booking.periodicEndDate));
                }

                vm.editedBooking.periodicSummary += summary;
                vm.editedBooking.periodicShortSummary += summary;
                vm.editedBooking.periodicSummary = vm.editedBooking.periodicSummary.toLowerCase();
                vm.editedBooking.periodicSummary = vm.editedBooking.periodicSummary.charAt(0).toUpperCase() +
                    vm.editedBooking.periodicSummary.slice(1);
            };

            vm.formatMomentDayLong = (date: any): string => {
                return date.locale(window.navigator.language).format('dddd DD MMMM YYYY');
            };
        },
        link: function ($scope) {
            const vm: IViewModel = $scope.vm;

            vm.translate = (text: string): string => {
                return lang.translate(text);
            }

            vm.toggleLightbox = (state: boolean): void => {
                if (!state) {
                    bookingEventService.sendState(BOOKING_EVENTER.CLOSE);
                }
            };

            vm.composeTitle = function (typeTitle, resourceTitle) {
                let title = lang.translate('rbs.booking.no.resource');
                if (typeTitle && resourceTitle) {
                    title = typeTitle + ' - ' + resourceTitle;
                }

                return _.isString(title) ? title.trim().length > 50 ? title.substring(0, 47) + '...' : title.trim() : '';
            };

            vm.switchStructure = (struct): void => {
                vm.selectedStructure = struct;
                vm.autoSelectTypeAndResource();
            };

            vm.switchSlotStart = (slot: any): void => {
                vm.selectedSlotStart = slot;
                vm.booking.startTime.set('hour', vm.selectedSlotStart.startHour.split(':')[0]);
                vm.booking.startTime.set('minute', vm.selectedSlotStart.startHour.split(':')[1]);
            };

            vm.switchSlotEnd = (slot: any): void => {
                vm.selectedSlotEnd = slot;
                vm.booking.endTime.set('hour', vm.selectedSlotEnd.endHour.split(':')[0]);
                vm.booking.endTime.set('minute', vm.selectedSlotEnd.endHour.split(':')[1]);
            };

            vm.formatTextBookingQuantity = () : string => {
                if (vm.tempQuantities.bookingQuantityAvailable <= 0) {
                    return lang.translate('rbs.booking.edit.quantity.none');
                }

                return vm.tempQuantities.bookingQuantityAvailable + lang.translate('rbs.booking.edit.quantity.on') +
                    vm.tempQuantities.resourceQuantityAvailable + lang.translate('rbs.booking.edit.quantity.availability');
            };

            vm.summaryBuildDays = (days): any => {
                // No days or all days cases are already done here
                var summary = undefined;
                var startBuffer = undefined;
                var lastIndex = days.length - 1;
                if (_.first(days).value && _.last(days).value) {
                    // Sunday and Monday are selected : summary will not start with monday, reverse-search the start day
                    for (var k = days.length; k > 0; k--) {
                        if (!days[k - 1].value) {
                            lastIndex = k - 1;
                            break;
                        }
                    }
                    startBuffer = lastIndex + 1;
                }

                for (var i = 0; i <= lastIndex; i++) {
                    if (startBuffer === undefined && days[i].value) {
                        // No range in buffer, start the range
                        startBuffer = i;
                    }
                    if (startBuffer !== undefined) {
                        if (i == lastIndex && days[lastIndex].value) {
                            // Day range complete (last index) write to summary
                            summary = vm.summaryWriteRange(
                                summary,
                                days[startBuffer],
                                days[lastIndex]
                            );
                            break;
                        }
                        if (!days[i].value) {
                            // Day range complete, write to summary
                            summary = vm.summaryWriteRange(
                                summary,
                                days[startBuffer],
                                days[i - 1]
                            );
                            startBuffer = undefined;
                        }
                    }
                }
                return summary;
            };

            vm.summaryWriteRange = function (summary, first, last) {
                if (first.number == last.number) {
                    // One day range
                    if (summary === undefined) {
                        // Start the summary
                        return (
                            ' ' +
                            lang.translate('rbs.period.days.one.start') +
                            ' ' +
                            lang.translate('rbs.period.days.' + first.number)
                        );
                    }
                    // Continue the summary
                    return (
                        summary +
                        lang.translate('rbs.period.days.one.continue') +
                        ' ' +
                        lang.translate('rbs.period.days.' + first.number)
                    );
                }
                if (first.number + 1 == last.number || first.number - 6 == last.number) {
                    // Two day range
                    if (summary === undefined) {
                        // Start the summary
                        return (
                            ' ' +
                            lang.translate('rbs.period.days.one.start') +
                            ' ' +
                            lang.translate('rbs.period.days.' + first.number) +
                            ' ' +
                            lang.translate('rbs.period.days.one.continue') +
                            ' ' +
                            lang.translate('rbs.period.days.' + last.number)
                        );
                    }
                    // Continue the summary
                    return (
                        summary +
                        lang.translate('rbs.period.days.one.continue') +
                        ' ' +
                        lang.translate('rbs.period.days.' + first.number) +
                        ' ' +
                        lang.translate('rbs.period.days.one.continue') +
                        ' ' +
                        lang.translate('rbs.period.days.' + last.number)
                    );
                }
                // Multi-day range
                if (summary === undefined) {
                    // Start the summary
                    return (
                        ' ' +
                        lang.translate('rbs.period.days.range.start') +
                        ' ' +
                        lang.translate('rbs.period.days.' + first.number) +
                        ' ' +
                        lang.translate('rbs.period.days.range.to') +
                        ' ' +
                        lang.translate('rbs.period.days.' + last.number)
                    );
                }
                // Continue the summary
                return (
                    summary +
                    lang.translate('rbs.period.days.range.continue') +
                    ' ' +
                    lang.translate('rbs.period.days.' + first.number) +
                    ' ' +
                    lang.translate('rbs.period.days.range.to') +
                    ' ' +
                    lang.translate('rbs.period.days.' + last.number)
                );
            };

            vm.startDateModif = () => {
                vm.booking.endDate = vm.booking.startDate;
                vm.showDaySelection = true;
            };

            vm.formatBooking = (date, time): string => {
                return (
                    moment(date).format('DD/MM/YYYY') +
                    ' ' +
                    lang.translate('rbs.booking.details.header.at') +
                    ' ' +
                    moment(time).format('HH[h]mm')
                );
            };

            vm.editPeriodicStartDate = () => {
                $scope.showDate = true; // todo useful ?
                if (moment(vm.booking.periodicEndDate).unix() < moment(vm.booking.startDate).unix()) {
                    vm.booking.periodicEndDate = vm.booking.startDate;
                }
            };

            vm.checkDateFunction = (): void => {
                vm.showDaySelection = moment(vm.booking.endDate).diff(moment(vm.booking.startDate), 'days') >= 0;
                if (moment(vm.booking.endDate).diff(moment(vm.booking.periodicEndDate), 'days') > 0) {
                    vm.booking.periodicEndDate = vm.booking.endDate;
                }
            };

            vm.togglePeriodic = (): void => {
                if (vm.editedBooking.is_periodic === true) {
                    vm.initPeriodic();
                }
                if (vm.editedBooking.type === undefined || vm.editedBooking.resource === undefined ||
                    !vm.editedBooking.resource.isBookable(true)) {
                    vm.selectedStructure = vm.structuresWithTypes[0];
                    vm.autoSelectTypeAndResource();
                    // Warn user ?
                }
            };

            vm.initPeriodic = () => {
                vm.editedBooking.is_periodic = true;
                vm.editedBooking.periodDays = model.bitMaskToDays(); // no days selected
                vm.editedBooking.byOccurrences = true;
                vm.editedBooking.periodicity = 1;
                vm.editedBooking.occurrences = 1;
                vm.updatePeriodicSummary();
            };

            vm.isBookingQuantityWrong = (booking: any): boolean => {
                return booking.quantity < 1 || booking.quantity === undefined || booking.quantity > vm.tempQuantities.bookingQuantityAvailable;
            };

            vm.saveBookingSlotProfile = (): void => {
                vm.currentErrors = [];
                vm.editedBooking.slots = [];
                var debut = vm.editedBooking.slotsLit.slots.indexOf(vm.selectedSlotStart);
                var fin = vm.editedBooking.slotsLit.slots.indexOf(vm.selectedSlotEnd);
                let multipleDaysPeriodic = undefined;
                try {
                    if (vm.resolveSlotsSelected(debut, fin)) {
                        return;
                    }
                    vm.editedBooking.slotsLit.slots.forEach(function (slot) {
                        if (slot.selected === true) {
                            vm.booking.startTime.set('hour', slot.startHour.split(':')[0]);
                            vm.booking.startTime.set('minute', slot.startHour.split(':')[1]);
                            vm.booking.endTime.set('hour', slot.endHour.split(':')[0]);
                            vm.booking.endTime.set('minute', slot.endHour.split(':')[1]);

                            // Save
                            vm.display.processing = true;

                            // dates management
                            vm.editedBooking.startMoment = moment([
                                vm.booking.startDate.getFullYear(),
                                vm.booking.startDate.getMonth(),
                                vm.booking.startDate.getDate(),
                                vm.booking.startTime.hour(),
                                vm.booking.startTime.minute()
                            ]);
                            if (vm.editedBooking.is_periodic === true) {
                                // periodic booking 1st slot less than a day
                                if (vm.showDaySelection === true) {
                                    if (vm.checkSaveBooking()) {
                                        return;
                                    }
                                }
                                vm.editedBooking.is_periodic = true;
                                vm.editedBooking.endMoment = moment([
                                    vm.booking.endDate.getFullYear(),
                                    vm.booking.endDate.getMonth(),
                                    vm.booking.endDate.getDate(),
                                    vm.booking.endTime.hour(),
                                    vm.booking.endTime.minute()
                                ]);

                                if (!vm.showDaySelection) {
                                    multipleDaysPeriodic = true;
                                    var periodDays = model.bitMaskToDays();
                                    var diffDays = vm.editedBooking.endMoment.dayOfYear() - vm.editedBooking.startMoment.dayOfYear();
                                    var start = moment([
                                        vm.editedBooking.startMoment.year(),
                                        vm.editedBooking.startMoment.month(),
                                        vm.editedBooking.startMoment.date(),
                                        vm.editedBooking.startMoment.hour(),
                                        vm.editedBooking.startMoment.minute()
                                    ]);

                                    var end = moment([
                                        vm.editedBooking.startMoment.year(),
                                        vm.editedBooking.startMoment.month(),
                                        vm.editedBooking.startMoment.date(),
                                        vm.editedBooking.endMoment.hour(),
                                        vm.editedBooking.endMoment.minute()
                                    ]);
                                    var nbDay = 0;
                                    for (var i = 0; i <= diffDays; i++) {
                                        var dow = start.day();
                                        if (i == 0 && vm.editedBooking.slotsLit.slots.indexOf(slot) >= debut) {
                                            if (start.year() > vm.today.year() ||
                                                (start.year() == vm.today.year() && start.month() > vm.today.month()) ||
                                                (start.year() == vm.today.year() && start.month() == vm.today.month() && start.date() > vm.today.date()) ||
                                                (start.year() == vm.today.year() && start.month() == vm.today.month() && start.date() == vm.today.date() && start.hour() >= moment().hour())
                                            ) {
                                                nbDay++;
                                                periodDays[(dow + i - 1) % 7].value = true;
                                            }
                                        } else if ((i == diffDays && vm.slots.slots.indexOf(slot) <= fin) || (i != 0 && i != diffDays)) {
                                            nbDay++;
                                            periodDays[(dow + i - 1) % 7].value = true;
                                        }

                                    }
                                } else {
                                    vm.resolvePeriodicMoments();
                                }

                                if (vm.editedBooking.byOccurrences !== true) {
                                    vm.editedBooking.occurrences = undefined;
                                    vm.editedBooking.periodicEndMoment = moment([
                                        vm.booking.periodicEndDate.getFullYear(),
                                        vm.booking.periodicEndDate.getMonth(),
                                        vm.booking.periodicEndDate.getDate(),
                                        vm.booking.endTime.hour(),
                                        vm.booking.endTime.minute()
                                    ]);
                                }

                                if (multipleDaysPeriodic && nbDay > 0) {
                                    var bookingPeriodicToSave = new Booking();
                                    bookingPeriodicToSave.periodicity = vm.editedBooking.periodicity;
                                    bookingPeriodicToSave.booking_reason = vm.editedBooking.booking_reason;
                                    bookingPeriodicToSave.resource = vm.editedBooking.resource;
                                    bookingPeriodicToSave.slots = [];
                                    if (vm.editedBooking.slotsLit.slots.indexOf(slot) >= debut) {
                                        if (start.year() > vm.today.year() ||
                                            (start.year() == vm.today.year() && start.month() > vm.today.month()) ||
                                            (start.year() == vm.today.year() && start.month() == vm.today.month() && start.date() > vm.today.date()) ||
                                            (start.year() == vm.today.year() && start.month() == vm.today.month() && start.date() == vm.today.date() && start.hour() >= moment().hour())
                                        ) {
                                            bookingPeriodicToSave.slots.push(new Slot(vm.editedBooking).toJson());
                                        } else {
                                            bookingPeriodicToSave.slots.push(SlotJson(start.add(1, 'd'), end.add(1, 'd')));
                                        }
                                    } else {
                                        bookingPeriodicToSave.slots.push(SlotJson(start.add(1, 'd'), end.add(1, 'd')));
                                    }


                                    bookingPeriodicToSave.is_periodic = true;
                                    bookingPeriodicToSave.periodDays = periodDays;
                                    if (vm.editedBooking.occurrences) {
                                        bookingPeriodicToSave.occurrences = vm.editedBooking.occurrences * nbDay;
                                    } else {
                                        bookingPeriodicToSave.periodicEndMoment = vm.editedBooking.periodicEndMoment;
                                    }

                                    bookingPeriodicToSave.save(
                                        function () {
                                            vm.display.processing = undefined;
                                            vm.closeBooking();
                                            model.refreshBookings(vm.display.list);
                                        },
                                        function (e) {
                                            notify.error(e.error);
                                            vm.display.processing = undefined;
                                            vm.currentErrors.push(e);
                                            $scope.$apply();
                                        }
                                    );
                                } else {
                                    vm.editedBooking.slots.push(new Slot(vm.editedBooking).toJson());
                                }
                            } else {
                                // non periodic
                                vm.editedBooking.endMoment = moment([
                                    vm.booking.endDate.getFullYear(),
                                    vm.booking.endDate.getMonth(),
                                    vm.booking.endDate.getDate(),
                                    vm.booking.endTime.hour(),
                                    vm.booking.endTime.minute()
                                ]);
                                var diffDays = vm.editedBooking.endMoment.dayOfYear() - vm.editedBooking.startMoment.dayOfYear();
                                if (diffDays == 0) {
                                    if (vm.checkSaveBooking()) {
                                        return;
                                    }
                                    vm.editedBooking.slots.push(new Slot(vm.editedBooking).toJson());
                                } else {
                                    for (var i = 0; i <= diffDays; i++) {
                                        var start = moment([
                                            vm.editedBooking.startMoment.year(),
                                            vm.editedBooking.startMoment.month(),
                                            vm.editedBooking.startMoment.date(),
                                            vm.editedBooking.startMoment.hour(),
                                            vm.editedBooking.startMoment.minute()
                                        ]);
                                        var end = moment([
                                            vm.editedBooking.endMoment.year(),
                                            vm.editedBooking.endMoment.month(),
                                            vm.editedBooking.endMoment.date(),
                                            vm.editedBooking.endMoment.hour(),
                                            vm.editedBooking.endMoment.minute()
                                        ]);

                                        if (i == 0 && vm.editedBooking.slotsLit.slots.indexOf(slot) >= debut) {
                                            if (start.year() > vm.today.year() ||
                                                (start.year() == vm.today.year() && start.month() > vm.today.month()) ||
                                                (start.year() == vm.today.year() && start.month() == vm.today.month() && start.date() > vm.today.date()) ||
                                                (start.year() == vm.today.year() && start.month() == vm.today.month() && start.date() == vm.today.date() && start.hour() >= moment().hour())
                                            ) {
                                                vm.editedBooking.slots.push(SlotJson(start.add(i, 'd'), end.subtract(diffDays - i, 'd')));
                                            } else {
                                                vm.currentErrors.push({
                                                    error: 'rbs.booking.invalid.datetimes.past',
                                                });
                                                notify.error(lang.translate('rbs.booking.slot.time') + slot.startHour + lang.translate('rbs.booking.slot.to')
                                                    + slot.endHour + lang.translate('rbs.booking.slot.day') + start.format('MM-DD-YYYY') + lang.translate('rbs.booking.slot.less'));
                                            }
                                        } else if (i == diffDays && vm.editedBooking.slotsLit.slots.indexOf(slot) <= fin) {
                                            vm.editedBooking.slots.push({
                                                start_date: start.add(i, 'd').unix(),
                                                end_date: end.subtract(diffDays - i, 'd').unix()
                                            });
                                        } else if (i != 0 && i != diffDays) {
                                            vm.editedBooking.slots.push(SlotJson(start.add(i, 'd'), end.subtract(diffDays - i, 'd')));
                                        }
                                    }
                                }
                            }
                        }
                    });
                    if (!multipleDaysPeriodic) {
                        vm.editedBooking.save(
                            function () {
                                vm.display.processing = undefined;
                                vm.closeBooking();
                                model.refreshBookings(vm.display.list);
                            },
                            function (e) {
                                notify.error(e.error);
                                vm.display.processing = undefined;
                                vm.currentErrors.push(e);
                                $scope.$apply();
                            }
                        );
                    }
                } catch (e) {
                    console.error(e);
                    vm.display.processing = undefined;
                    vm.currentErrors.push({error: 'rbs.error.technical'});
                }
            };

            vm.resolveSlotsSelected = (debut: any, fin: any): boolean => {
                let hasErrors = false;
                if (debut <= fin) {
                    for (let i = debut; i <= fin; i++) {
                        $scope.editedBooking.slotsLit.slots[i].selected = true;
                    }
                } else {
                    $scope.currentErrors.push({
                        error: 'rbs.booking.invalid.slots'
                    });
                    notify.error('rbs.booking.invalid.slots');
                    hasErrors = true;
                }
                return hasErrors;
            };

            vm.checkSaveBooking = (): boolean => {
                var hasErrors = false;
                if (
                    vm.booking.startDate.getFullYear() < vm.today.year() ||
                    (vm.booking.startDate.getFullYear() == vm.today.year() &&
                        vm.booking.startDate.getMonth() < vm.today.month()) ||
                    (vm.booking.startDate.getFullYear() == vm.today.year() &&
                        vm.booking.startDate.getMonth() == vm.today.month() &&
                        vm.booking.startDate.getDate() < vm.today.date()) ||
                    (vm.booking.startDate.getFullYear() == vm.today.year() &&
                        vm.booking.startDate.getMonth() == vm.today.month() &&
                        vm.booking.startDate.getDate() == vm.today.date() &&
                        vm.booking.startTime.hour() < moment().hour())
                ) {
                    vm.currentErrors.push({
                        error: 'rbs.booking.invalid.datetimes.past',
                    });
                    notify.error('rbs.booking.invalid.datetimes.past');
                    hasErrors = true;
                }
                if (vm.booking.startDate.getFullYear() == vm.booking.endDate.getFullYear()
                    && vm.booking.endTime.hour() == vm.booking.startTime.hour()
                    && vm.booking.endTime.minute() == vm.booking.startTime.minute()) {
                    vm.currentErrors.push({error: 'rbs.booking.invalid.datetimes.equals'});
                    notify.error('rbs.booking.invalid.datetimes.equals');
                    hasErrors = true;
                }
                if (vm.editedBooking.is_periodic === true && 
                    _.find(vm.editedBooking.periodDays, function (periodDay) {return periodDay.value === true;}) === undefined && 
                    vm.showDaySelection) {
                    // Error
                    vm.currentErrors.push({error: 'rbs.booking.missing.days'});
                    notify.error('rbs.booking.missing.days');
                    hasErrors = true;
                }
                return hasErrors;
            };

            vm.saveBooking = (): void => {
                // Check
                vm.currentErrors = [];

                // vm.editedBooking = semanticObject(vm.editedBooking, Booking);

                if (typeof vm.booking.startTime === 'string') {
                    const time = vm.booking.startTime.split(':');
                    vm.booking.startTime = moment().set('hour', time[0]).set('minute', time[1]);
                }

                if (typeof vm.booking.endTime === 'string') {
                    const time = vm.booking.endTime.split(':');
                    vm.booking.endTime = moment().set('hour', time[0]).set('minute', time[1]);
                }

                try {
                    // Save
                    vm.display.processing = true;

                    // dates management
                    vm.editedBooking.startMoment = moment([
                        vm.booking.startDate.getFullYear(),
                        vm.booking.startDate.getMonth(),
                        vm.booking.startDate.getDate(),
                        vm.booking.startTime.hour(),
                        vm.booking.startTime.minute()
                    ]);
                    if (vm.editedBooking.is_periodic === true) {
                        vm.editedBooking.endMoment = moment([
                            vm.booking.endDate.getFullYear(),
                            vm.booking.endDate.getMonth(),
                            vm.booking.endDate.getDate(),
                            vm.booking.endTime.hour(),
                            vm.booking.endTime.minute()
                        ]);
                        if (vm.editedBooking.byOccurrences !== true) {
                            vm.editedBooking.occurrences = undefined;
                            vm.editedBooking.periodicEndMoment = moment([
                                vm.booking.periodicEndDate.getFullYear(),
                                vm.booking.periodicEndDate.getMonth(),
                                vm.booking.periodicEndDate.getDate(),
                                vm.booking.endTime.hour(),
                                vm.booking.endTime.minute()
                            ]);
                        }
                        vm.resolvePeriodicMoments();
                    } else {
                        // non periodic
                        vm.editedBooking.endMoment = moment([
                            vm.booking.endDate.getFullYear(),
                            vm.booking.endDate.getMonth(),
                            vm.booking.endDate.getDate(),
                            vm.booking.endTime.hour(),
                            vm.booking.endTime.minute()
                        ]);
                    }
                    vm.editedBooking.slots = [new Slot(vm.editedBooking).toJson()];
                    vm.editedBooking.save(
                        function () {
                            vm.display.processing = undefined;
                            vm.closeBooking();
                            model.refreshBookings(vm.display.list);
                        },
                        function (e) {
                            HandleUtil.handleErrorType(e);
                            notify.error(e.error);
                            vm.display.processing = undefined;
                            vm.currentErrors.push(e);
                            $scope.$apply();
                        }
                    );
                } catch (e) {
                    vm.display.processing = undefined;
                    vm.currentErrors.push({error: 'rbs.error.technical'});
                    throw e;
                }
            };

            vm.resolvePeriodicMoments = (): void => {
                // find next selected day as real start date
                var selectedDays = _.groupBy(
                    _.filter(vm.editedBooking.periodDays, function (periodDay) {
                        return periodDay.value === true;
                    }),
                    function (periodDay) {
                        return periodDay.number;
                    }
                );
                //Periodic less than a day
                //if(vm.showDaySelection) {
                if (selectedDays[vm.editedBooking.startMoment.day()] === undefined) {
                    // search the next following day (higher number)
                    let i;
                    for (i = vm.editedBooking.startMoment.day(); i < 7; i++) {
                        if (selectedDays[i] !== undefined) {
                            vm.editedBooking.startMoment = vm.editedBooking.startMoment.day(
                                i
                            );
                            vm.editedBooking.endMoment = vm.editedBooking.endMoment.day(
                                i
                            );
                            return;
                        }
                    }
                    // search the next following day (lower number)
                    for (i = 0; i < vm.editedBooking.startMoment.day(); i++) {
                        if (selectedDays[i] !== undefined) {
                            vm.editedBooking.startMoment = vm.editedBooking.startMoment.day(
                                i + 7
                            ); // +7 for days in next week, not current
                            vm.editedBooking.endMoment = vm.editedBooking.endMoment.day(
                                i + 7
                            );
                            return;
                        }
                    }
                }
            };

            // must change a bit
            vm.closeBooking = (): void => {
                vm.slotNotFound = undefined;
                if (
                    vm.selectedBooking !== undefined &&
                    vm.selectedBooking.is_periodic === true
                ) {
                    _.each(vm.selectedBooking._slots, function (slot) {
                        slot.expanded = false;
                    });
                }
                if (vm.display.list !== true) {
                    // In calendar view, deselect all when closing lightboxes
                    vm.bookings.deselectAll();
                    vm.bookings.applyFilters();
                }
                vm.selectedBooking = undefined;
                vm.editedBooking = null;
                vm.processBookings = [];
                vm.currentErrors = [];
                vm.display.showPanel = false;
                vm.slots = undefined;
                vm.toggleLightbox(false);
                $scope.$apply();
            };

            vm.$onDestroy = async () => {
                bookingEventService.unsubscribe();
            };
        }
    };
}]);
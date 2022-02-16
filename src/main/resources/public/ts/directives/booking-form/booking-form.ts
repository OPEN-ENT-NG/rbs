import {_, ng, moment, notify, idiom as lang} from 'entcore';
import {ROOTS} from "../../core/const/roots.const";
import {RBS} from "../../models/models";
import {DateUtils} from "../../utilities/date.util";
import {ArrayUtil} from "../../utilities/array.util";
import {BookingUtil} from "../../utilities/booking.util";
import {HandleUtil} from "../../utilities/handle.util";
import {BOOKING_EVENTER} from "../../core/enum/booking-eventer.enum";
import {BookingEventService} from "../../services";
import {I18nUtils} from "../../utilities/i18n.util";
import {AvailabilityUtil} from "../../utilities/availability.util";
import {Availabilities} from "../../models/Availability";

const {Booking, Slot, SlotJson} = RBS;

declare let model: any;
declare let window: any;

interface IViewModel {
    editedBooking: any;
    displayLightbox: boolean;
    selectedStructure: any;

    slotProfilesComponent: any;
    selectedSlotStart: any;
    selectedSlotEnd: any;
    slotNotFound: boolean;

    saveTime: any;

    // Props
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
    bookingsConflictingResource: any;
    bookingsOkResource: any;

    $onInit(): any;
    // New booking
    newBooking(): Promise<void>;
    newBookingCalendar(): Promise<void>;
    // Init new booking
    initEditBookingDisplay(): void;
    initModerators(): void;
    autoSelectTypeAndResource(): Promise<void>;
    autoSelectResource(): Promise<void>;
    initBookingDates(startMoment: any, endMoment: any): void;
    // Edit booking
    editBooking(): Promise<void>;
    updatePeriodicSummary(): void;
    formatMomentDayLong(date: any): string;

    // Utils
    translate(text: string): string;
    toggleLightbox(state: boolean): void;
    composeTitle(typeTitle: any, resourceTitle: any): string;
    switchStructure(struct: any): Promise<void>;
    switchSlotStart(slot: any): void;
    switchSlotEnd(slot: any): void;
    summaryBuildDays(days: any): any;
    summaryWriteRange(summary: any, first: any, last: any): string;
    startDateModif(): any;
    formatBooking(date: any, time: any): string;
    editPeriodicStartDate(): void;
    checkDateFunction(): void;
    togglePeriodic(): Promise<void>;
    initPeriodic(): void;
    saveBookingSlotProfile(): void;
    resolveSlotsSelected(start: any, end: any): boolean;
    checkSaveBooking(): boolean;
    saveBooking(): void;
    resolvePeriodicMoments(): void;
    closeBooking(): void;
    // Quantity functions
    updateEditedBookingMoments(): void;
    updateEditedBookingSlots(): void;
    updateEditedBookingMomentsAndSlots(): void;
    getQuantityDispo(booking: any, bookingException?: any): number;
    isBookingQuantityWrong(booking: any): boolean;
    formatTextBookingQuantity(booking: any): string;
    onSyncAndTreatBookingsUsingResource(resource: any): Promise<void>;

    $onDestroy(): any;
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
            processBookings: "=",
            onSyncAndTreatBookingsUsingResource: '&',
            bookingsConflictingResource: '=',
            bookingsOkResource: '='
        },
        restrict: 'E',
        templateUrl: `${ROOTS.directive}booking-form/booking-form.html`,
        controllerAs: 'vm',
        bindToController: true,
        controller: function ($scope) {
            const vm: IViewModel = <IViewModel>this;

            vm.$onInit = async () => {
                switch (window.bookingState) {
                    case BOOKING_EVENTER.CREATE:
                        await vm.newBooking();
                        break;
                    case BOOKING_EVENTER.CREATE_CALENDAR:
                        await vm.newBookingCalendar();
                        break;
                    case BOOKING_EVENTER.EDIT:
                        await vm.editBooking();
                        break;
                }
            };

            // New booking

            vm.newBooking = async (): Promise<void> => {
                vm.display.processing = undefined; // add as props from controller
                vm.editedBooking = new Booking();
                vm.editedBooking.quantity = 1;
                vm.saveTime = undefined;
                vm.initEditBookingDisplay();
                vm.initModerators();

                // periodic booking
                vm.editedBooking.is_periodic = false; // false by default

                vm.selectedStructure = vm.structuresWithTypes[0]; // add as props from controller
                await vm.autoSelectTypeAndResource();

                // dates
                vm.editedBooking.startMoment = moment();
                vm.editedBooking.endMoment = moment();
                vm.editedBooking.endMoment.hour(vm.editedBooking.startMoment.hour() + 1);
                vm.editedBooking.startMoment.seconds(0);
                vm.editedBooking.endMoment.seconds(0);

                vm.bookings.syncForShowList();
                vm.initBookingDates(vm.editedBooking.startMoment, vm.editedBooking.endMoment);

                // vm.display.showPanel = true;
                $scope.$apply();
            };

            vm.newBookingCalendar = async (): Promise<void> => {
                vm.display.processing = undefined;
                vm.editedBooking = new Booking();
                vm.editedBooking.quantity = 1;
                vm.initEditBookingDisplay();

                vm.initModerators();

                vm.selectedStructure = vm.structuresWithTypes[0];
                await vm.autoSelectTypeAndResource();

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

                vm.bookings.syncForShowList();
                vm.initBookingDates(vm.editedBooking.startMoment, vm.editedBooking.endMoment);
                vm.displayLightbox = true;
                $scope.$apply();
            };

            // Init a new booking

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

            vm.autoSelectTypeAndResource = async (): Promise<void> => {
                vm.editedBooking.type = undefined;
                vm.editedBooking.resource = undefined;
                vm.selectedSlotStart = undefined;
                vm.selectedSlotEnd = undefined;
                let selectedType = undefined;
                if (vm.selectedStructure.types.length > 0) {
                    selectedType = vm.selectedStructure.types.find(t => !t.resources.isEmpty());
                    if (!selectedType) {
                        notify.error(lang.translate('rbs.booking.warning.no.resources'));
                    }
                    else {
                        vm.editedBooking.type = selectedType;
                        await vm.autoSelectResource();
                    }
                }
                else {
                    notify.error(lang.translate('rbs.booking.warning.no.types'));
                }
                $scope.$apply();
            };

            vm.autoSelectResource = async (): Promise<void> => {
                vm.display.processing = true;
                for (let resource of vm.editedBooking.type.resources.all) {
                    await resource.syncResourceAvailabilities();
                }

                let validResources = vm.editedBooking.type.resources.filterAvailable(vm.editedBooking.is_periodic);
                vm.editedBooking.resource = validResources.length > 0 ? validResources[0] : undefined;

                if (vm.editedBooking.type.slotprofile) {
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
                        }
                        else {
                            vm.editedBooking.type.slotprofile = undefined;
                        }
                        $scope.$apply();
                    });
                }
                else if (vm.saveTime) {
                    vm.booking.startTime.set('hour', vm.saveTime.startHour -
                        parseInt(DateUtils.formatMoment(vm.booking.startDate, vm.booking.startTime).format('Z').split(':')[0]));
                    vm.booking.startTime.set('minute', 0);

                    vm.booking.endTime.set('hour', vm.saveTime.endHour -
                        parseInt(DateUtils.formatMoment(vm.booking.startDate, vm.booking.startTime).format('Z').split(':')[0]));
                    vm.booking.endTime.set('minute', 0);
                }
                vm.display.processing = false;
                $scope.$apply();
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
                vm.booking.startDate.setFullYear(startMoment.year());
                vm.booking.startDate.setMonth(startMoment.month());
                vm.booking.startDate.setDate(startMoment.date());

                // setter endDate booking
                vm.booking.endDate = endMoment.toDate();
                vm.booking.endDate.setFullYear(endMoment.year());
                vm.booking.endDate.setMonth(endMoment.month());
                vm.booking.endDate.setDate(endMoment.date());

                // setter periodicEndDate
                vm.booking.periodicEndDate = endMoment.toDate();
            };

            // Edit booking

            vm.editBooking = async (): Promise<void> => {
                vm.display.processing = undefined;
                vm.selectedSlotStart = undefined;
                vm.selectedSlotEnd = undefined;
                vm.slotNotFound = undefined;
                vm.currentErrors = [];
                vm.bookings.syncForShowList();

                vm.editedBooking = vm.selectedBooking ? vm.selectedBooking : vm.bookings.selection()[0];
                if (!vm.editedBooking.isBooking()) { vm.editedBooking = vm.editedBooking.booking; }
                vm.initEditBookingDisplay();

                // periodic booking
                if (vm.editedBooking.is_periodic) {
                    vm.editedBooking.byOccurrences = vm.editedBooking.occurrences && vm.editedBooking.occurrences > 0;
                    vm.editedBooking._slots = _.sortBy(vm.editedBooking._slots, 'id');
                    vm.editedBooking.startMoment = vm.editedBooking._slots[0].startMoment;
                    vm.editedBooking.startMoment.date(vm.editedBooking.beginning.date());
                    vm.editedBooking.startMoment.month(vm.editedBooking.beginning.month());
                    vm.editedBooking.startMoment.year(vm.editedBooking.beginning.year());
                    vm.editedBooking.endMoment = vm.editedBooking._slots[0].endMoment;
                    vm.editedBooking.tempSlots = vm.editedBooking._slots;
                }

                vm.initBookingDates(vm.editedBooking.startMoment, vm.editedBooking.endMoment);
                // if (vm.editedBooking.is_periodic) {
                //     vm.booking.periodicEndDate = vm.editedBooking._slots[vm.editedBooking._slots.length-1].endMoment.toDate();
                // }

                // change periodicEndDate (will affect ocurence/recurence end_date)
                vm.booking.periodicEndDate = new Date(vm.editedBooking.end_date);

                vm.editedBooking.type = vm.editedBooking.resource.type;
                if (vm.editedBooking.type && vm.editedBooking.type.slotprofile) {
                    vm.slotProfilesComponent.getSlots(vm.editedBooking.type.slotprofile, function (data) {
                        if (data.slots.length > 0) {
                            vm.slots = data;
                            vm.editedBooking.slotsLit = data;
                            vm.slots.slots.sort(ArrayUtil.sort_by('startHour', false, (a) => a));
                            vm.selectedSlotStart = vm.slots.slots
                                .filter(function (slot) {
                                    return (
                                        slot.startHour.split(':')[0] == vm.editedBooking.startMoment.hour() &&
                                        slot.startHour.split(':')[1] == vm.editedBooking.startMoment.minute()
                                    );
                                })
                                .pop();
                            vm.selectedSlotEnd = vm.selectedSlotStart;
                            if (!vm.selectedSlotStart) {
                                vm.selectedSlotStart = vm.slots.slots[0];
                                vm.selectedSlotEnd = vm.selectedSlotStart;
                                vm.slotNotFound = true;
                            }
                        }
                        else {
                            vm.editedBooking.type.slotprofile = undefined;
                        }
                    });
                }

                await vm.editedBooking.resource.syncResourceAvailabilities();
                vm.updatePeriodicSummary();

                $scope.$apply();
            };

            vm.updatePeriodicSummary = (): void => {
                vm.editedBooking.periodicSummary = '';
                vm.editedBooking.periodicShortSummary = '';
                vm.editedBooking.periodicError = undefined;

                vm.editedBooking.periodicShortSummary = lang.translate('rbs.period.days.some');

                if (vm.showDaySelection) {
                    // Selected days
                    var selected = 0;
                    _.each(vm.editedBooking.periodDays, function (d) {
                        selected += d.value ? 1 : 0;
                    });
                    if (selected == 0) {
                        // Error in periodic view
                        vm.editedBooking.periodicError = lang.translate('rbs.period.error.nodays');
                        return;
                    } else if (selected == 7) {
                        vm.editedBooking.periodicSummary = lang.translate('rbs.period.days.all');
                        vm.editedBooking.periodicShortSummary = vm.editedBooking.periodicSummary;
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

            vm.switchStructure = async (struct): Promise<void> => {
                vm.selectedStructure = struct;
                await vm.autoSelectTypeAndResource();
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

            vm.togglePeriodic = async (): Promise<void> => {
                if (vm.editedBooking.is_periodic === true) {
                    vm.initPeriodic();
                }
                if (vm.editedBooking.type === undefined || vm.editedBooking.resource === undefined ||
                    !vm.editedBooking.resource.isBookable(true)) {
                    vm.selectedStructure = vm.structuresWithTypes[0];
                    await vm.autoSelectTypeAndResource();
                    // Warn user ?
                }
            };

            vm.initPeriodic = () => {
                vm.editedBooking.is_periodic = true;
                vm.editedBooking.periodDays = model.bitMaskToDays(); // no days selected
                vm.editedBooking.periodDays[vm.editedBooking.startMoment.day() - 1].value = true; // auto-checked selected day
                vm.editedBooking.byOccurrences = true;
                vm.editedBooking.periodicity = 1;
                vm.editedBooking.occurrences = 1;
                vm.updatePeriodicSummary();
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
                        vm.editedBooking.slotsLit.slots[i].selected = true;
                    }
                } else {
                    vm.currentErrors.push({
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
                        async function () {
                            // Deals with bookings validation system
                            await vm.editedBooking.resource.syncResourceAvailabilities();
                            await $scope.$eval(vm.onSyncAndTreatBookingsUsingResource)(vm.editedBooking.resource);
                            $scope.$apply();

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

            // Quantity functions

            vm.updateEditedBookingMoments = function() : void {
                vm.currentErrors = [];

                vm.editedBooking.startDate = vm.booking.startDate;
                vm.editedBooking.endDate = vm.booking.endDate;
                vm.editedBooking.startTime = vm.booking.startTime;
                vm.editedBooking.endTime = vm.booking.endTime;

                // Formats time if they are strings
                vm.editedBooking.startTime = DateUtils.formatTimeIfString(vm.editedBooking.startTime);
                vm.editedBooking.endTime = DateUtils.formatTimeIfString(vm.editedBooking.endTime);

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

            vm.updateEditedBookingSlots = function() : void {
                vm.editedBooking.tempSlots = [];
                let currentMoment = moment(vm.editedBooking.startMoment);
                let currentMonday = currentMoment.subtract(currentMoment.weekday(), 'days');
                let diff = vm.editedBooking.endMoment.diff(vm.editedBooking.startMoment);
                let tempBook = new Booking();
                let bookingQuantity = vm.editedBooking.quantity ? vm.editedBooking.quantity : 1;

                // Get days checked
                let days = [];
                for (let i = 0; i < vm.editedBooking.periodDays.length; i++) {
                    if (vm.editedBooking.periodDays[i].value) {
                        days.push(i);
                    }
                }

                // Get tempPeriodicBookings
                if (days.length > 0) {
                    let nbOccurrenceTot = 1;
                    if (!vm.editedBooking.byOccurrences) {
                        let startDate = vm.editedBooking.startMoment;
                        let endDate = moment(vm.booking.periodicEndDate);

                        let nbCheckedDays = vm.editedBooking.periodDays.filter(d => d).length;

                        let nbOccurrencePlainWeeks = endDate.diff(startDate, 'weeks') * nbCheckedDays;

                        let nbOccurrenceFinal = 0;
                        if (startDate.day() > endDate.day()) {
                            nbOccurrenceFinal += vm.editedBooking.periodDays.slice(startDate.day(), 7).filter(d => d).length;
                            nbOccurrenceFinal += vm.editedBooking.periodDays.slice(0, endDate.day()+1).filter(d => d).length;
                        }
                        else {
                            nbOccurrenceFinal += vm.editedBooking.periodDays.slice(startDate.day(), endDate.day()+1).filter(d => d).length;
                        }

                        nbOccurrenceTot = nbOccurrencePlainWeeks + nbOccurrenceFinal;
                    }
                    else {
                        nbOccurrenceTot = vm.editedBooking.occurrences;
                    }

                    // Create temporary slots thanks to nbOccurrenceTot
                    while(vm.editedBooking.tempSlots.length < nbOccurrenceTot) {
                        for (let day of days) {
                            if (vm.editedBooking.tempSlots.length < nbOccurrenceTot) {
                                tempBook.startMoment = moment(currentMonday);
                                tempBook.endMoment = moment(currentMonday);
                                tempBook.startMoment.add(day, 'days');
                                tempBook.endMoment.add(day, 'days').add(diff);
                                tempBook.quantity = bookingQuantity;
                                if (tempBook.startMoment >= vm.editedBooking.startMoment) {
                                    tempBook.resource = vm.editedBooking.resource;
                                    vm.editedBooking.tempSlots.push(tempBook);
                                }
                                tempBook = new Booking();
                            }
                        }
                        currentMonday.add(vm.editedBooking.periodicity, 'weeks');
                    }
                }
                else {
                    vm.currentErrors.push({error: 'rbs.period.error.nodays'});
                }
            };

            vm.updateEditedBookingMomentsAndSlots = function() : void {
                if (vm.editedBooking) {
                    vm.updateEditedBookingMoments();
                    if (vm.editedBooking.is_periodic) {
                        vm.updateEditedBookingSlots();
                    }
                }
            };

            vm.getQuantityDispo = (booking, bookingException?) : number => {
                if (!booking.resource || !booking.resource.quantity || booking.resource.quantity <= 0 ||
                    booking.isPast() || booking.startMoment.isSame(booking.endMoment)) {
                        return 0;
                }
                else {
                    let quantityDispo = booking.resource.quantity;
                    if (!booking.is_periodic) {
                        quantityDispo = AvailabilityUtil.getTimeslotQuantityAvailable(booking, booking.resource, null, bookingException);
                    }
                    else {
                        for (let slot of booking.tempSlots) {
                            let localQuantity = vm.getQuantityDispo(slot, bookingException); // Get the quantity dispo for each slot
                            if (localQuantity < quantityDispo) { quantityDispo = localQuantity; } // Keep the minimal quantity available
                        }
                    }
                    return quantityDispo > 0 ? quantityDispo : 0;
                }
            };

            vm.isBookingQuantityWrong = (booking: any): boolean => {
                return !booking.quantity || booking.quantity > vm.getQuantityDispo(booking, booking);
                // TODO check aussi validité des heures / dates
            };

            vm.formatTextBookingQuantity = (booking) : string => {
                if (vm.currentErrors.length > 0) {
                    return lang.translate(vm.currentErrors[0].error);
                }

                let timeslotQuantityDispo = vm.getQuantityDispo(booking, booking);
                let timeslotResourceQuantity = AvailabilityUtil.getResourceQuantityByTimeslot(booking, booking.resource);

                if (timeslotQuantityDispo <= 0) {
                    return lang.translate('rbs.booking.edit.quantity.none');
                }
                else {
                    let params = [timeslotQuantityDispo,  timeslotResourceQuantity];
                    return I18nUtils.getWithParams('rbs.booking.edit.quantity.availability', params);
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
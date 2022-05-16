import {BookingService, IBookingService} from "../services";
import {angular, idiom, model} from "entcore";
import {Booking, Bookings, IBookingResponse} from "../models/booking.model";
import {Resource} from "../models/resource.model";
import {ResourceType, Structure} from "../models/resource-type.model";
import {IAngularEvent} from "angular";
import {AvailabilityUtil} from "../utilities/availability.util";
import {Availabilities, Availability} from "../models/Availability";
import {Slot, SlotsForAPI} from "../models/slot.model";
import {safeApply} from "../utilities/safe-apply";
import moment from '../moment';
import {DateUtils} from "../utilities/date.util";
import {BOOKING_STATUS} from "../core/const/booking-status.const";
import {FORMAT} from "../core/const/date-format.const";
import {CalendarEvent} from "../models/calendarEvent.model";
import {AxiosResponse} from "axios";
import {RBS_CALENDAR_EVENTER} from "../core/enum/rbs-calendar-eventer.enum";
import {BookingDelayUtil} from "../utilities/booking-delay.util";

enum ALL_DAY {
    startHour = 7,
    endHour = 20,
    minutes = 0
}

const rbsViewRight: string = "rbs.view";

interface IViewModel {
    loading: boolean;
    hasBooking: boolean;
    bookingValid: boolean;
    hasBookingRight: boolean;
    editedBooking: Booking;
    bookings: Bookings;
    canViewBooking: boolean;
    hasResourceTypes: boolean;
    hasResourceRights: boolean;
    canEditEvent: boolean;

    userStructures: Array<Structure>;
    resourceTypes: Array<ResourceType>;
    resources: Array<Resource>;
    slots: Array<Slot>;
    initialCalendarEvent: CalendarEvent;
    calendarEvent: CalendarEvent;
    numberOfAvailableItems: number;
    numberOfTotalItems: number;
    minMaxDelaysCompliant: boolean;

    setHandler(): void;

    prepareBookingsToSave(): Array<Booking>;

    autoSelectStructure(): void;

    areStructuresValid(): boolean;

    autoSelectResourceType(): Promise<void>;

    autoSelectResource(): Promise<void>;

    handleCalendarEventChange(event: IAngularEvent, calendarEvent: CalendarEvent): void;

    userIsAdml(): boolean;

    prepareBookingStartAndEnd(booking?: Booking): void;

    availableResourceQuantity(): number;

    resourceQuantity(): number;

    calendarEventIsBeforeToday(): boolean;

    isResourceQuantityNotNull(): boolean;

    isResourceQuantityValid(): boolean;

    isResourceQuantityAllowingBooking(): boolean

    getRightList(resource: Resource): Availability[];

    displayTime(date): void;

    displayDate(date): void;

    bookingStatus(bookingStatus: number): string;

    formatBookingDates(bookingStartDate: string, bookingEndDate: string): string;

    resourceHasMinDelay(): boolean;

    resourceHasMaxDelay(): boolean;

    checkMinDelay(): boolean;

    checkMaxDelay(): boolean;

    displayDelayDays(delay: number): number;

    bookingDelaysValid(): boolean;

    eventHasBooking(calendarEvent?: CalendarEvent): boolean;

    hasRbsAccess(): boolean;

    hasSavedBookings(): boolean;

    hasAccessToSavedBookings(): boolean;

    recurrenceOrMultidaysAdded(): boolean;

    isOneDayEvent(calendarEvent: CalendarEvent): boolean;

    isBookingPossible(excludeBookingFormInfos?: boolean): boolean;
}

class ViewModel implements IViewModel {
    private scope: any;
    loading: boolean;
    hasBooking: boolean;
    bookingValid: boolean;
    hasBookingRight: boolean;
    editedBooking: Booking;
    bookings: Bookings;
    canViewBooking: boolean;
    hasResourceTypes: boolean;
    hasResourceRights: boolean;
    canEditEvent: boolean;

    userStructures: Array<Structure>;
    resourceTypes: Array<ResourceType>;
    resources: Array<Resource>;
    slots: Array<Slot>;
    initialCalendarEvent: CalendarEvent;
    calendarEvent: CalendarEvent;
    numberOfAvailableItems: number;
    numberOfTotalItems: number;
    minMaxDelaysCompliant: boolean;

    private bookingService;

    constructor(scope, bookingService: IBookingService) {
        this.loading = true;
        this.scope = scope;
        this.bookingService = bookingService;
        this.hasBooking = false;
        this.hasBookingRight = false;
        this.canViewBooking = false;
        this.hasResourceTypes = false;
        this.canEditEvent = false;
        this.bookings = new Bookings();

        this.editedBooking = new Booking();
        this.editedBooking.opened = true;
        this.editedBooking.quantity = 1;
        this.editedBooking.booking_reason = idiom.translate("rbs.calendar.sniplet.booking.reason");

        this.setHandler();
        this.loading = false;
    }

    /**
     * Get calendarEvent change and change form accordingly
     */
    setHandler(): void {
        this.scope.$on(RBS_CALENDAR_EVENTER.INIT_BOOKING_INFOS, (event: IAngularEvent, calendarEvent: CalendarEvent) => {
            this.handleCalendarEventChange(event, calendarEvent);
        });

        this.scope.$on(RBS_CALENDAR_EVENTER.UPDATE_BOOKING_INFOS, (event: IAngularEvent, calendarEvent: CalendarEvent) => {
            this.handleCalendarEventChange(event, calendarEvent);
        });

        this.scope.$on(RBS_CALENDAR_EVENTER.CLOSE_BOOKING_INFOS, (event: IAngularEvent) => {
            this.handleCalendarEventChange(event);
        });

        this.scope.$on(RBS_CALENDAR_EVENTER.CAN_EDIT_EVENT, (event: IAngularEvent, canEditEvent: boolean) => {
            this.canEditEvent = canEditEvent;
        });
    }

    /**
     * Updates this.bookings.all and transmits changes to Calendar
     */
    prepareBookingsToSave(): Array<Booking> {
        if(!this.bookings.all.find((booking: Booking) => booking.opened == true)){
            this.bookings.all.push(this.editedBooking);
        }

        return this.bookings.all;
    }

    /**
     * Gets all the user's structures + selects the first one.
     * Calls the method that does the same for resource types.
     */
    autoSelectStructure(): void {
        this.userStructures = this.getStructures();
        this.editedBooking.structure = this.userStructures[0];

        this.autoSelectResourceType();
    }

    /**
     * Gets the id + name if the structures the user is part of
     */
    private getStructures(): Array<Structure> {
        let structuresWithNames: Array<Structure> = [];

        if (this.areStructuresValid()) { // warning in html if structures are not valid
            for (let i = 0; i < model.me.structures.length; i++) {
                let newStructure: Structure = new Structure();
                newStructure.id = model.me.structures[i];
                newStructure.name = model.me.structureNames[i];
                structuresWithNames.push(newStructure);
            }
        }

        return structuresWithNames;
    }

    /**
     * Returns true if user has at least 1 structure and if there are the same number of structure ids and structure names
     */
    areStructuresValid(): boolean {
      return ((model.me.structures.length > 0)
            && (model.me.structures.length == model.me.structureNames.length));
    };

    /**
     * Gets all resource types for one structure + selects the first one.
     * Calls the method that does the same for resources.
     */
    async autoSelectResourceType(): Promise<void> {
        this.bookingService.getResourceTypes(this.editedBooking.structure.id)
            .then(async (resourcesTypes: Array<ResourceType>) => {
                this.resourceTypes = resourcesTypes;
                if (resourcesTypes && resourcesTypes.length > 0) {
                    this.resourceTypes = resourcesTypes;
                    this.editedBooking.type = this.resourceTypes[0];
                    this.hasResourceTypes = true;

                    if (this.resourceTypes.filter((type: ResourceType) => type.myRights.contrib).length > 0
                        || this.userIsAdml()) {
                        this.hasBookingRight = true;
                        try {
                            await this.autoSelectResource();
                        } catch (e) {
                            console.error(e);
                        }
                    }
                } else {
                    this.editedBooking.type = undefined;
                    this.resourceTypes = [];
                    this.editedBooking.resource = undefined;
                    this.resources = [];
                    safeApply(this.scope);
                }
            })
            .catch(e => {
                console.error(e);
            });
    }

    /**
     * Gets all resources for one resource type + selects the first one.
     */
    async autoSelectResource(): Promise<void> {
        if (this.editedBooking.type.myRights.contrib || this.userIsAdml()) { //user has booking creation right
            try {
                this.resources = await this.bookingService.getResources(this.editedBooking.type.id);

                if (this.resources && this.resources.length > 0) {
                    for (const resource of this.resources) {

                        // availabilities
                        resource.availabilities = new Availabilities();
                        resource.unavailabilities = new Availabilities();
                        await Promise.all([
                            resource.availabilities.sync(resource.id, false),
                            resource.unavailabilities.sync(resource.id, true),
                        ]);

                        // get and format resource bookings
                        resource.bookings = new Bookings();
                        resource.bookings.all = await this.bookingService.getBookings(resource.id);
                        resource.bookings.all.forEach((booking: Booking) => {
                            //set start and end moment so they can be use by the availability util
                            booking.startMoment = moment(moment.utc(booking.start_date).toISOString());
                            booking.endMoment = moment(moment.utc(booking.end_date).toISOString());
                        });

                    }
                    this.editedBooking.resource = this.resources[0];
                    this.prepareBookingStartAndEnd();

                } else {
                    this.editedBooking.resource = undefined;
                    this.resources = [];
                }
            } catch (e) {
                console.error(e);
            }
        } else { //same behaviour as RBS booking form: warning in place of resource selection
            this.resources = undefined;
        }

        safeApply(this.scope);
    }

    /**
     * Set form behaviour according to event from calendar
     * @param event the event sent
     * @param calendarEvent the data needed
     */
    handleCalendarEventChange(event: IAngularEvent, calendarEvent?: CalendarEvent): void {
        switch (event.name) {
            case RBS_CALENDAR_EVENTER.INIT_BOOKING_INFOS:
                this.loading = true;
                this.initialCalendarEvent = angular.copy(calendarEvent);
                this.calendarEvent = calendarEvent;
                if (this.calendarEvent._id && this.eventHasBooking()) {
                    this.canViewBooking = true;
                    this.calendarEvent.bookings.forEach((booking: IBookingResponse) => {
                        this.bookingService.getBooking(booking.id)
                            .then( async (databaseBooking: Booking) => {
                                if (databaseBooking.id == undefined) {
                                    databaseBooking = new Booking().build(booking);
                                    databaseBooking.hasBeenDeleted = true;
                                } else {
                                    databaseBooking.resource = await this.bookingService.getResource(databaseBooking.resourceId);
                                    databaseBooking.hasBeenDeleted = false;
                                }

                                this.hasResourceRights = true;
                                this.bookings.all.push(databaseBooking);
                            })
                            .catch((e) => {
                                let error: AxiosResponse = e.response;
                                if (error.status === 401) {
                                    this.hasResourceRights = false;
                                }
                                console.error(e);
                            });
                    })
                }
                this.autoSelectStructure();
                this.editedBooking.quantity = 1;
                this.loading = false;
                break;
            case RBS_CALENDAR_EVENTER.UPDATE_BOOKING_INFOS:
                this.calendarEvent = calendarEvent;
                this.prepareBookingStartAndEnd();
                safeApply(this.scope);
                break;
            case RBS_CALENDAR_EVENTER.CLOSE_BOOKING_INFOS:
                this.hasBooking = false;
                this.hasBookingRight = false;
                this.canViewBooking = false;
                this.canEditEvent = false;
                this.bookings.all = [];
                safeApply(this.scope);
                break;

        }
    }

    /**
     * Returns true if the user is ADML (Local Admin)
     */
    userIsAdml(): boolean {
        return model.me.functions.ADMIN_LOCAL
            && (model.me.functions.ADMIN_LOCAL.scope.find((structure: String) => structure == this.editedBooking.structure.id) != undefined);
    }

    /**
     * Returns true if the event takes place in the past
     */
    calendarEventIsBeforeToday(): boolean {
        let checkedStartMoment = moment(moment(this.calendarEvent.startMoment)
            .format(FORMAT.formattedDateTimeNoSeconds))
            .hours(this.calendarEvent.allday ? ALL_DAY.startHour :this.calendarEvent.startTime.getHours())
            .minutes(this.calendarEvent.allday ? ALL_DAY.startHour :this.calendarEvent.startTime.getMinutes());
        let startMomentIsBeforeNow: boolean = checkedStartMoment.isBefore(Date.now(), 'minutes');
        return (checkedStartMoment.isBefore(Date.now(), 'minutes'));
    }

    /**
     * Returns true if the resource quantity is defined and not 0
     */
    isResourceQuantityNotNull(): boolean {
        return (this.editedBooking.quantity && this.editedBooking.quantity > 0);
    }

    /**
     * Returns true if the resource has a set quantity that is available
     */
    isResourceQuantityValid(): boolean {
        return (this.isResourceQuantityNotNull() && this.editedBooking.quantity <= this.availableResourceQuantity()
            && this.availableResourceQuantity() > 0);
    }

    /**
     * Returns true if the resource can be booked but the quantity is null
     */
    isResourceQuantityAllowingBooking(): boolean {
        let bookingFormValid = (this.hasBooking && (this.areStructuresValid()
                && (this.resourceTypes && this.resourceTypes.length > 0)
                && (this.resources && this.resources.length > 0)));
        return this.isResourceQuantityValid() || !bookingFormValid;
    }

    /**
     * Returns the number of resources available for the event duration
     * @param booking the booking that must be checked
     */
    availableResourceQuantity(booking ?: Booking): number {
        let currentBooking: Booking = booking ? booking : this.editedBooking;

        this.prepareBookingForAvailabilityCheck(currentBooking);

        return (currentBooking.resource ? AvailabilityUtil.getTimeslotQuantityAvailable(currentBooking, currentBooking.resource, null,
            currentBooking) : 0);
    }

    /**
     * Returns the number of resources set for the event duration
     * @param booking the booking that must be checked
     */
    resourceQuantity(booking ?: Booking): number {
        let currentBooking: Booking = booking ? booking : this.editedBooking;

        this.prepareBookingForAvailabilityCheck(currentBooking);

        return AvailabilityUtil.getResourceQuantityByTimeslot(currentBooking, currentBooking.resource, null);
    }

    /**
     * Formats the start and end dates so they can be used by the availability util
     * @param currentBooking the formatted booking
     * @private
     */
    private prepareBookingForAvailabilityCheck(currentBooking: Booking): void {

        // set start and end moment so they can be saved correctly
        if (this.calendarEvent.startMoment instanceof Date || this.calendarEvent.endMoment instanceof Date) {
            currentBooking.startMoment = moment(this.calendarEvent.startMoment)
                .utc(true)
                .hours(this.calendarEvent.startTime.getHours())
                .minutes(this.calendarEvent.startTime.getMinutes());
            currentBooking.endMoment = moment(this.calendarEvent.endMoment)
                .utc(true)
                .hours(this.calendarEvent.endTime.getHours())
                .minutes(this.calendarEvent.endTime.getMinutes());
        } else {
            currentBooking.startMoment = moment(this.calendarEvent.startMoment)
                .hours(this.calendarEvent.allday ? ALL_DAY.startHour : this.calendarEvent.startTime.getHours())
                .minutes(this.calendarEvent.allday ? ALL_DAY.minutes : this.calendarEvent.startTime.getMinutes())
                .utc();
            currentBooking.endMoment = moment(this.calendarEvent.endMoment)
                .hours(this.calendarEvent.allday ? ALL_DAY.endHour : this.calendarEvent.endTime.getHours())
                .minutes(this.calendarEvent.allday ? ALL_DAY.minutes : this.calendarEvent.endTime.getMinutes())
                .utc();
        }
    }

    /**
     * Prepares the booking to get its availabilities
     * @param booking the booking that must be prepared
     */
    prepareBookingStartAndEnd(booking ?: Booking): Booking {
        let createdBooking: Booking = booking ? booking : this.editedBooking;

        // set start and end moment so they can be saved correctly
        createdBooking.startMoment = moment(moment(this.calendarEvent.startMoment).format(FORMAT.formattedDateTimeNoSeconds));
        createdBooking.endMoment= moment(moment(this.calendarEvent.endMoment).format(FORMAT.formattedDateTimeNoSeconds));

        // handle all day event
        if (this.calendarEvent.allday) {
            createdBooking.startMoment.hours(ALL_DAY.startHour).minutes(ALL_DAY.minutes).utc();
            createdBooking.endMoment.hours(ALL_DAY.endHour).minutes(ALL_DAY.minutes).utc();
        } else {
            createdBooking.startMoment.hours(this.calendarEvent.startTime.getHours())
                .minutes(this.calendarEvent.startTime.getMinutes());
            createdBooking.endMoment.hours(this.calendarEvent.endTime.getHours())
                .minutes(this.calendarEvent.endTime.getMinutes());
        }

        // handle slots
        if (createdBooking.type.slotProfile) {
            this.handleBookingSlots(createdBooking);
        }


        let bookingSlot: SlotsForAPI = {
            start_date : createdBooking.startMoment.utc().unix(),
            end_date : createdBooking.endMoment.utc().unix(),
            iana : moment.tz.guess()
        };

        createdBooking.slots = [bookingSlot];

        return createdBooking;
    }

    /**
     * Returns true if the resource has a minimum delay
     */
    resourceHasMinDelay(): boolean {
        return (this.editedBooking && this.editedBooking.resource && this.editedBooking.resource.minDelay != null);
    }

    /**
     * Returns true if the resource has a maximum delay
     */
    resourceHasMaxDelay(): boolean {
        return (this.editedBooking && this.editedBooking.resource && this.editedBooking.resource.maxDelay != null);
    }

    /**
     * Returns true if the minimum delay is compliant
     */
    checkMinDelay(): boolean {
        return BookingDelayUtil.checkMinDelay(this.editedBooking);
    }

    /**
     * Returns true if the maximum delay is compliant
     */
    checkMaxDelay(): boolean {
       return BookingDelayUtil.checkMaxDelay(this.editedBooking);
    }

    /**
     * Returns the number of days of the delay
     */
    displayDelayDays(delay: number): number {
        return BookingDelayUtil.displayDelayDays(delay);
    }

    /**
     * Returns true if the booking delays are compliant
     */
    bookingDelaysValid(): boolean {
        return ((this.resourceHasMinDelay()? this.checkMinDelay() : true)
            && (this.resourceHasMaxDelay()? this.checkMaxDelay() : true));
    }

    /**
     * Handles the case where the resource type uses booking slots
     * @param createdBooking the booking
     * @private
     */
    private handleBookingSlots(createdBooking: Booking): void {
        let eventStartTime = moment(this.calendarEvent.startTime).format(FORMAT.displayTime);
        let eventEndTime = moment(this.calendarEvent.endTime).format(FORMAT.displayTime);
        let closestStartTime: any = undefined;
        let closestEndTime: any = undefined;
        this.bookingService.getSlots(createdBooking.type.slotProfile)
            .then((slotList: Array<Slot>) => {
                slotList.forEach((slot: Slot) => {
                    if ((!closestEndTime || slot.startHour > closestStartTime.startHour)
                        && slot.startHour <= eventStartTime) closestStartTime = slot;
                    if ((!closestEndTime || slot.endHour < closestEndTime.endHour)
                        && slot.endHour >= eventEndTime) closestEndTime = slot;
                });

                createdBooking.startMoment.set('hour', closestStartTime.startHour.split(':')[0]);
                createdBooking.startMoment.set('minute', closestStartTime.startHour.split(':')[1]);
                createdBooking.endMoment.set('hour', closestEndTime.endHour.split(':')[0]);
                createdBooking.endMoment.set('minute', closestEndTime.endHour.split(':')[1]);
            })
            .catch((e) => {
                console.error(e);
            });
    }

    /**
     * Gets the availability or unavailability of the resource
     * @param resource the targeted resource
     */
    getRightList(resource: Resource): Availability[] {
        return AvailabilityUtil.getRightList(resource);
    }

    /**
     * Formats time the right way
     * @param date the target date
     */
    displayTime(date): void {
        return DateUtils.displayTime(date);
    };

    /**
     * Formats date the right way
     * @param date the target date
     */
    displayDate(date): void {
        return DateUtils.displayDate(date);
    };

    /**
     * Returns the string corresponding to the status number using i18n
     */
    bookingStatus(bookingStatus: number): string {
        return idiom.translate("rbs.errors.objects.booking") + " " + idiom.translate(BOOKING_STATUS[bookingStatus]);
    }

    /**
     * Returns the start and end dates both in format DD-MM-YYYY HH:mm
     */
    formatBookingDates(bookingStartDate: string, bookingEndDate: string): string {
        let bookingStart: string = bookingStartDate + "Z";
        let bookingEnd: string = bookingEndDate + "Z";

        return moment(bookingStart).format(FORMAT.displayDateTime)
            + " - " + moment(bookingEnd).format(FORMAT.displayDateTime);
    }

    /**
     * Returns true if the calendarEvent has a booking
     */
    eventHasBooking(calendarEvent?: CalendarEvent): boolean {
        let checkedEvent: CalendarEvent = calendarEvent ? calendarEvent : this.calendarEvent;

        return (checkedEvent.bookings && (checkedEvent.bookings.length > 0));
    }

    /**
     * Returns true if the user has access to RBS
     */
    hasRbsAccess(): boolean {
        return !!model.me.authorizedActions.find((action) => action.displayName == rbsViewRight);
    }

    /**
     * Returns true if the user has access to RBS
     */
    hasSavedBookings(): boolean {
        return (this.calendarEvent._id && this.calendarEvent.bookings && this.calendarEvent.bookings.length > 0);
    }

    /**
     * Returns true if the user has access to a saved booking info
     */
    hasAccessToSavedBookings(): boolean {
        return (this.hasRbsAccess() && this.hasResourceTypes && this.hasResourceRights) || !this.hasSavedBookings();
    }

    /**
     * Returns true if the calendarEvent has been edited with a recurrence or a multi-day change
     */
    recurrenceOrMultidaysAdded(): boolean {
        let hasNewRecurrence = !this.initialCalendarEvent.isRecurrent && this.calendarEvent.isRecurrent;
        let hasNewMultiDay = this.isOneDayEvent(this.initialCalendarEvent) && !this.isOneDayEvent(this.calendarEvent);
        return (this.calendarEvent._id && this.eventHasBooking() && !this.isBookingPossible() && (hasNewRecurrence || hasNewMultiDay));
    }

    /**
     * Returns true if the event ends the same day it begins
     */
    isOneDayEvent(calendarEvent: CalendarEvent): boolean {
        return (moment(calendarEvent.startMoment).isSame(moment(calendarEvent.endMoment), 'day'));
    }

    /**
     * Returns true if it is possible to book a resource
     * @param excludeBookingFormInfos is true if the booking form information should be ignored
     */
    isBookingPossible(excludeBookingFormInfos?: boolean): boolean {
        let isBookingValid: boolean = (!this.hasBooking
            || (!this.calendarEvent.isRecurrent && this.isOneDayEvent(this.calendarEvent)));

        let bookingFormValid: boolean = excludeBookingFormInfos? true
            :(this.areStructuresValid()
            && (this.resourceTypes && this.resourceTypes.length > 0)
            && (this.resources && this.resources.length > 0)
            && ((this.hasBooking && this.isResourceQuantityValid() && this.bookingDelaysValid()) || !this.hasBooking));

        return <boolean>((isBookingValid && bookingFormValid) || this.calendarEvent._id);
    }

}

export const calendarRbsBooking = {
    titre: 'calendar.booking',
    public: false,
    controller: {
        init: async function (): Promise<void> {
            idiom.addBundle('/rbs/i18n', async () => {
                this.vm = new ViewModel(this, new BookingService());
            });
        },
    }

};
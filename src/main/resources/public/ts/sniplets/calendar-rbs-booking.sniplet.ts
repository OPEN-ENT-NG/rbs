import {BookingService, IBookingService} from "../services";
import {idiom, model} from "entcore";
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

enum ALL_DAY {
    startHour = 7,
    endHour = 20,
    minutes = 0
}

interface IViewModel {
    loading: boolean;
    hasBooking: boolean;
    bookingValid: boolean;
    bookingPossible: boolean;
    hasBookingRight: boolean;
    editedBooking: Booking;
    bookings: Bookings;
    canViewBooking: boolean;


    userStructures: Array<Structure>;
    resourceTypes: Array<ResourceType>;
    resources: Array<Resource>;
    slots: Array<Slot>;
    calendarEvent: any;
    numberOfAvailableItems: number;
    numberOfTotalItems: number;

    setHandler(): void;

    // updateCalendarEvent(): void;

    prepareBookingsToSave(): Array<Booking>;

    autoSelectStructure(structure?: Structure): void;

    areStructuresValid(): boolean;

    autoSelectResourceType(): Promise<void>;

    autoSelectResource(): Promise<void>;

    handleCalendarEventChange(event: IAngularEvent, calendarEvent: any): void;

    userIsAdml(): boolean;

    prepareBookingStartAndEnd(booking?: Booking, bookingToSave?: boolean): void;

    availableResourceQuantity(): number;

    resourceQuantity(): number;

    calendarEventIsBeforeToday(): boolean;

    isResourceAvailable(): boolean;

    getRightList(resource: Resource): Availability[];

    displayTime(date): void;

    displayDate(date): void;

    bookingStatus(bookingStatus: number): string;

    formatBookingDates(bookingStartDate: string, bookingEndDate: string): string;

    eventHasBooking(calendarEvent?: any): boolean;

}

class ViewModel implements IViewModel {
    private scope: any;
    loading: boolean;
    hasBooking: boolean;
    bookingValid: boolean;
    hasBookingRight: boolean;
    bookingPossible: boolean;
    editedBooking: Booking;
    bookings: Bookings;
    canViewBooking: boolean;

    userStructures: Array<Structure>;
    resourceTypes: Array<ResourceType>;
    resources: Array<Resource>;
    slots: Array<Slot>;
    calendarEvent: any;
    numberOfAvailableItems: number;
    numberOfTotalItems: number;

    private bookingService;

    constructor(scope, bookingService: IBookingService) {
        this.loading = true;
        this.scope = scope;
        this.bookingService = bookingService;
        this.hasBooking = false;
        this.hasBookingRight = false;
        this.canViewBooking = false;
        this.bookings = new Bookings();

        this.editedBooking = new Booking();
        this.editedBooking.opened = true;
        this.editedBooking.quantity = 1;
        this.editedBooking.booking_reason = idiom.translate("rbs.calendar.sniplet.booking.reason");

        this.setHandler();
        // this.updateCalendarEvent();
        this.loading = false;
    }

    /**
     * Get calendarEvent change and change form accordingly
     */
    setHandler(): void {
        this.scope.$on("initBookingInfos", (event: IAngularEvent, calendarEvent: any) => {
            this.handleCalendarEventChange(event, calendarEvent);
        });

        this.scope.$on("updateBookingInfos", (event: IAngularEvent, calendarEvent: any) => {
            this.handleCalendarEventChange(event, calendarEvent);
        });

        this.scope.$on("bookingPossible", (event: IAngularEvent, bookingPossible: boolean) => {
            this.bookingPossible = bookingPossible;
        });

        this.scope.$on("closeBookingInfos", (event: IAngularEvent, calendarEvent: boolean) => {
            this.handleCalendarEventChange(event, calendarEvent);
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
    autoSelectStructure(structure?: Structure) {
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
        let areStructuresValid: boolean = (model.me.structures.length > 0)
            && (model.me.structures.length == model.me.structureNames.length);



        return areStructuresValid;
    };

    /**
     * Gets all resource types for one structure + selects the first one.
     * Calls the method that does the same for resources.
     */
    async autoSelectResourceType(): Promise<void> {
        this.bookingService.getResourceTypes(this.editedBooking.structure.id)
            .then(async (resourcesTypes: Array<ResourceType>) => {

                if (resourcesTypes && resourcesTypes.length > 0) {
                    this.resourceTypes = resourcesTypes;
                    this.editedBooking.type = this.resourceTypes[0];

                    if (this.resourceTypes.filter((type: ResourceType) => type.myRights.contrib).length > 0
                        || this.userIsAdml()) {
                        this.hasBookingRight = true;
                        await this.autoSelectResource();
                    } else {
                        this.hasBookingRight = false;
                    }
                }
            })
            .catch(e => {
                console.error(e);
            })
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
                    // this.updateCalendarEvent();

                } else {
                    this.editedBooking.resource = undefined;
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
    handleCalendarEventChange(event: IAngularEvent, calendarEvent: any):void {
        switch (event.name) {
            case "initBookingInfos":
                this.loading = true;
                this.calendarEvent = calendarEvent;
                if (this.calendarEvent._id && this.eventHasBooking()) {
                    this.canViewBooking = true;
                    this.calendarEvent.bookings.forEach((booking: IBookingResponse) => {
                        this.bookingService.getBooking(booking.id)
                            .then( async (databaseBooking: Booking) => {
                                if (databaseBooking.id == undefined) {
                                    databaseBooking = new Booking().build(booking);
                                    databaseBooking.canBeDisplayed = false;
                                } else {
                                    databaseBooking.resource = await this.bookingService.getResource(databaseBooking.resourceId);
                                    databaseBooking.canBeDisplayed = true;
                                }

                                this.bookings.all.push(databaseBooking);
                            })
                            .catch((e) => {
                                console.error(e);
                            });
                    })
                }
                this.autoSelectStructure();
                this.editedBooking.quantity = 1;
                this.loading = false;
                break;
            case "updateBookingInfos":
                this.calendarEvent = calendarEvent;
                this.prepareBookingStartAndEnd();
                this.isResourceAvailable();
                safeApply(this.scope);
                break;
            case "closeBookingInfos":
                this.hasBooking = false;
                this.hasBookingRight = false;
                this.canViewBooking = false;
                this.bookings.all = [];
                safeApply(this.scope);
                break;

        }
    }

    /**
     * Returns true if the user is ADML (Local Admin)
     */
    userIsAdml(): boolean {
        return model.me.functions.ADMIN_LOCAL && (model.me.functions.ADMIN_LOCAL.scope.find((structure: String) => structure == this.editedBooking.structure.id) != undefined);
    }

    /**
     * Returns true if the event takes place in the past
     */
    calendarEventIsBeforeToday(): boolean {
        return (moment(this.calendarEvent.startMoment).isBefore(Date.now()));
    }

    /**
     * Returns true if the resource has a set quantity that is available
     */
    isResourceAvailable(): boolean {
        return (this.editedBooking.quantity && this.editedBooking.quantity <= this.availableResourceQuantity()
            && this.availableResourceQuantity() > 0);
    }

    /**
     * Returns the number of resources available for the event duration
     * @param booking the booking that must be checked
     */
    availableResourceQuantity(booking ?: Booking): number {
        let currentBooking = booking ? this.prepareBookingStartAndEnd(booking)
            : this.prepareBookingStartAndEnd();

        this.prepareBookingForAvailabilityCheck(currentBooking);

        return (currentBooking.resource ? AvailabilityUtil.getTimeslotQuantityAvailable(currentBooking, currentBooking.resource, null,
            currentBooking) : 0);
    }

    /**
     * Returns the number of resources set for the event duration
     * @param booking the booking that must be checked
     */
    resourceQuantity(booking ?: Booking): number {
        let currentBooking = booking ? this.prepareBookingStartAndEnd(booking)
            : this.prepareBookingStartAndEnd();

        this.prepareBookingForAvailabilityCheck(currentBooking);

        return AvailabilityUtil.getResourceQuantityByTimeslot(currentBooking, currentBooking.resource, null);
    }

    /**
     * Formats the start and end dates so they can be used by the availability util
     * @param currentBooking the formatted booking
     * @private
     */
    private prepareBookingForAvailabilityCheck(currentBooking: Booking) {
        currentBooking.startMoment = moment(this.calendarEvent.startMoment)
            .hours(this.calendarEvent.allday ? ALL_DAY.startHour : this.calendarEvent.startTime.getHours())
            .minutes(this.calendarEvent.allday ? ALL_DAY.minutes : this.calendarEvent.startTime.getMinutes())
            .utc();
        currentBooking.endMoment = moment(this.calendarEvent.endMoment)
            .hours(this.calendarEvent.allday ? ALL_DAY.endHour : this.calendarEvent.endTime.getHours())
            .minutes(this.calendarEvent.allday ? ALL_DAY.minutes : this.calendarEvent.endTime.getMinutes())
            .utc();
    }

    /**
     * Prepares the booking to get its availabilities
     * @param booking the booking that must be prepared
     */
    prepareBookingStartAndEnd(booking ?: Booking, bookingToSave?: boolean): Booking {
        let createdBooking: Booking = booking ? booking : this.editedBooking;

        // set start and end moment so they can be saved correctly
        createdBooking.startMoment = moment(this.calendarEvent.startMoment.format("YYYY-MM-DD HH:mm"));
        createdBooking.endMoment= moment(this.calendarEvent.endMoment.format("YYYY-MM-DD HH:mm"));

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
     * Handles the case where the resource type uses booking slots
     * @param createdBooking the booking
     * @private
     */
    private handleBookingSlots(createdBooking: Booking) {
        let eventStartTime = moment(this.calendarEvent.startTime).format("HH:mm");
        let eventEndTime = moment(this.calendarEvent.endTime).format("HH:mm");
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
        return idiom.translate(BOOKING_STATUS[bookingStatus]);
    }

    /**
     * Returns the start and end dates both in format DD-MM-YYYY HH:mm
     */
    formatBookingDates(bookingStartDate: string, bookingEndDate: string): string {
        let bookingStart: string = bookingStartDate + "Z";
        let bookingEnd: string = bookingEndDate + "Z";

        return moment(bookingStart).format("DD/MM/YYYY HH:mm")
            + " - " + moment(bookingEnd).format("DD/MM/YYYY HH:mm");
    }

    /**
     * Returns true if the calendarEvent has a booking
     */
    eventHasBooking(calendarEvent?: any): boolean {
        let checkedEvent = calendarEvent ? calendarEvent : this.calendarEvent;

        return (checkedEvent.hasBooking && checkedEvent.bookings && (checkedEvent.bookings.length > 0));
    }

}

export const calendarRbsBooking = {
    titre: 'calendar.booking',
    public: false,
    that: null,
    controller: {
        init: async function (): Promise<void> {
            idiom.addBundle('/rbs/i18n', async () => {
                this.vm = new ViewModel(this, new BookingService());
            });
        },
    }

};
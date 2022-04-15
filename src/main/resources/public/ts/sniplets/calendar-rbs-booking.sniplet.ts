import {bookingService, BookingService, IBookingService} from "../services";
import {RbsController} from "../controllers/controller";
import {model, idiom, angular, notify, Behaviours} from "entcore";
import {Booking, Bookings} from "../models/booking.model";
import {Resource} from "../models/resource.model";
import {ResourceType, Structure} from "../models/resource-type.model";
import {lang, Moment} from "moment";
import {IAngularEvent} from "angular";
import {calendar} from "entcore/types/src/ts/calendar";
import {AvailabilityUtil} from "../utilities/availability.util";
import {Availabilities, Availability} from "../models/Availability";
import {Slot} from "../models/slot.model";
import {safeApply} from "../utilities/safe-apply";
import moment from '../moment';
import {DateUtils} from "../utilities/date.util";

enum ALL_DAY {
    startHour = 7,
    endHour = 20,
    minutes = 0
}

interface IViewModel {
    hasBooking: boolean;
    bookingValid: boolean;
    bookingPossible: boolean;
    hasBookingRight: boolean;
    editedBooking: Booking;
    loading: boolean;

    userStructures: Array<Structure>;
    resourceTypes: Array<ResourceType>;
    resources: Array<any>;
    slots: Array<Slot>;
    calendarEvent: any;
    numberOfAvailableItems: number;
    numberOfTotalItems: number;

    setHandler(): void;
    updateCalendarEvent(): void;
    autoSelectStructure(structure ?: Structure): void;
    areStructuresValid(): boolean;
    autoSelectResourceType(): Promise<void>;
    autoSelectResource():  Promise<void>;
    handleCalendarEventChange(event: IAngularEvent, calendarEvent: any): void;
    userIsAdml(): boolean;
    prepareBookingStartAndEnd(): void;
    availableResourceQuantity(): number;
    resourceQuantity(): number;
    calendarEventIsBeforeToday(): boolean;
    isResourceAvailable(): boolean;
    getRightList(resource: Resource): Availability[];
    displayTime(date) : void;
    displayDate(date): void;

}

class ViewModel implements IViewModel {
    private scope: any;
    loading: boolean;
    hasBooking: boolean;
    bookingValid: boolean;
    hasBookingRight: boolean;
    bookingPossible: boolean;
    editedBooking: Booking;

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

        this.editedBooking = new Booking();
        this.editedBooking.quantity = 1;

        // this.updateCalendarEvent();
        this.setHandler();
        this.updateCalendarEvent();
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

        this.scope.$on("bookingPossible", (event: IAngularEvent, bookingPossible:boolean) => {
            console.log("rbs booking possible", bookingPossible);
            this.bookingPossible = bookingPossible;
        });
    }

    /**
     * Updates calendarEvent changes back
     */
    updateCalendarEvent(): void {
        this.scope.$emit("hasBooking",  this.hasBooking);
        this.bookingValid = this.areStructuresValid() && (this.resourceTypes && this.resourceTypes.length > 0)
            && (this.resources && this.resources.length > 0) && this.isResourceAvailable();
        this.scope.$emit("isBookingValid", this.bookingValid);
    }

    /**
     * Gets all the user's structures + selects the first one.
     * Calls the method that does the same for resource types.
     */
    autoSelectStructure(structure ?: Structure) {
        this.userStructures = this.getStructures();
        this.editedBooking.structure = this.userStructures[0];

        this.autoSelectResourceType();
    }

    /**
     * Gets the id + name if the structures the user is part of
     */
    private getStructures(): Array<Structure> {
        let structuresWithNames: Array<Structure> = [];

        if (this.areStructuresValid()) { //warning in html if structures are not valid
            for(let i=0; i < model.me.structures.length; i++) {
                let newStructure = new Structure();
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
        let areStructuresValid = (model.me.structures.length > 0)
            && (model.me.structures.length == model.me.structureNames.length);

        // this.scope.$emit("structuresValid",  areStructuresValid); //inform calendar of structure acceptability

        return areStructuresValid;
    };

    /**
     * Gets all resource types for one structure + selects the first one.
     * Calls the method that does the same for resources.
     */
    async autoSelectResourceType(): Promise<void> {
        this.bookingService.getResourceTypes(this.editedBooking.structure.id)
            .then(async (resourcesTypes: Array<ResourceType>) => {
                // let areTypesValid = resourcesTypes && resourcesTypes.length > 0;
                // this.scope.$emit("typesValid",  areTypesValid); //inform calendar of types acceptability

                if (resourcesTypes && resourcesTypes.length > 0) {
                    this.resourceTypes = resourcesTypes;
                    this.editedBooking.type = this.resourceTypes[0];

                    if(this.resourceTypes.filter((type: ResourceType) => type.myRights.contrib).length > 0
                        || this.userIsAdml()) {
                        await this.autoSelectResource();
                    } else {
                        this.hasBookingRight = false;
                    }
                }
            })
            .catch (e => {
                console.error(e);
            })
    }

    /**
     * Gets all resources for one resource type + selects the first one.
     */
    async autoSelectResource(): Promise<void> {
        if (this.editedBooking.type.myRights.contrib || this.userIsAdml()) { //user has booking creation right
            this.hasBookingRight = true;
            try {
                this.resources = await this.bookingService.getResources(this.editedBooking.type.id);
                // let areResourcesValid = this.resources && this.resources.length > 0;
                // this.scope.$emit("resourcesValid",  areResourcesValid); //inform calendar of types acceptability


                if (this.resources && this.resources.length > 0) {
                    for (const resource of this.resources) {

                        // availabilities
                        resource.availabilities = new Availabilities();
                        await resource.availabilities.sync(resource.id, false);
                        resource.unavailabilities = new Availabilities();
                        await resource.unavailabilities.sync(resource.id, true);

                        // get and format resource bookings
                        resource.bookings = new Bookings();
                        resource.bookings.all = await this.bookingService.getBookings(resource.id);
                        resource.bookings.all.forEach((booking : Booking) =>  {
                            //set start and end moment so they can be use by the availability util
                            booking.startMoment = moment(moment.utc(booking.start_date).toISOString());
                            booking.endMoment = moment(moment.utc(booking.end_date).toISOString());
                        });

                    }
                    this.editedBooking.resource = this.resources[0];

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
    handleCalendarEventChange(event: IAngularEvent, calendarEvent: any): void {
        switch (event.name) {
            case "initBookingInfos":
                this.loading = true;
                this.calendarEvent = calendarEvent;
                this.autoSelectStructure();
                this.editedBooking.quantity = 1;
                this.loading = false;
                break;
            case "updateBookingInfos":
                this.calendarEvent = calendarEvent;
                safeApply(this.scope);
                break;

        }
    }

    /**
     * Returns true if the user is ADML (Local Admin)
     */
    userIsAdml(): boolean {
        return model.me.functions.ADMIN_LOCAL && (model.me.functions.ADMIN_LOCAL.scope.find((structure : String) => structure == this.editedBooking.structure.id) != undefined);
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
        // let isResourceAvailable = (this.editedBooking.quantity && this.editedBooking.quantity <= this.availableResourceQuantity()
        //     && this.availableResourceQuantity() > 0);
        // this.scope.$emit("resourceAvailable",  isResourceAvailable); //inform calendar of resources availability

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

        return AvailabilityUtil.getTimeslotQuantityAvailable(currentBooking, currentBooking.resource, null,
            currentBooking);
    }

    /**
     * Returns the number of resources set for the event duration
     * @param booking the booking that must be checked
     */
    resourceQuantity(booking ?: Booking): number {
        let currentBooking = booking ? this.prepareBookingStartAndEnd(booking)
            : this.prepareBookingStartAndEnd();

        return AvailabilityUtil.getResourceQuantityByTimeslot(currentBooking, currentBooking.resource, null);
    }

    /**
     * Prepares the booking to get its availabilities
     * @param booking the booking that must be prepared
     */
    prepareBookingStartAndEnd(booking ?: Booking): Booking {
        let createdBooking: Booking = booking ? booking : this.editedBooking;

        // set start and end moment so they can be used by the availability util
        createdBooking.startMoment = moment(this.calendarEvent.startMoment)
            .hours(this.calendarEvent.allday ? ALL_DAY.startHour : this.calendarEvent.startTime.getHours())
            .minutes(this.calendarEvent.allday ? ALL_DAY.minutes : this.calendarEvent.startTime.getMinutes())
            .utc();
        createdBooking.endMoment = moment(this.calendarEvent.endMoment)
            .hours(this.calendarEvent.allday ? ALL_DAY.endHour : this.calendarEvent.endTime.getHours())
            .minutes(this.calendarEvent.allday ? ALL_DAY.minutes : this.calendarEvent.endTime.getMinutes())
            .utc();


        // handle slots
        if (createdBooking.type.slotProfile) {
            let eventStartTime = moment(this.calendarEvent.startTime).format("HH:mm");
            let eventEndTime = moment(this.calendarEvent.endTime).format("HH:mm");
            let closestStartTime = undefined;
            let closestEndTime = undefined;
            this.bookingService.getSlots(createdBooking.type.slotProfile)
                .then(slotList => {
                    slotList.forEach((slot: Slot) => {
                        if ((!closestEndTime || slot.startHour > closestStartTime.startHour)
                            && slot.startHour <= eventStartTime) closestStartTime = slot;
                        if ((!closestEndTime || slot.endHour < closestEndTime.endHour)
                            && slot.endHour >= eventEndTime) closestEndTime = slot;
                    });

                    createdBooking.startMoment.set('hour', closestStartTime.startHour.split(':')[0]);
                    createdBooking.startMoment.set('minute', closestStartTime.startHour.split(':')[1]);
                    createdBooking.endMoment.set('hour', closestEndTime.startHour.split(':')[0]);
                    createdBooking.endMoment.set('minute', closestEndTime.startHour.split(':')[1]);
                });
        }

        return createdBooking;
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
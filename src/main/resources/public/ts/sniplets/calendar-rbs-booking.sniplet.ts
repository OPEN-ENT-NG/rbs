import {bookingService, BookingService, IBookingService} from "../services";
import {RbsController} from "../controllers/controller";
import {model, idiom, angular, notify, moment, Behaviours} from "entcore";
import {Booking, Bookings} from "../models/booking.model";
import {Resource} from "../models/resource.model";
import {ResourceType, Structure} from "../models/resource-type.model";
import {lang, Moment} from "moment";
import {IAngularEvent} from "angular";
import {calendar} from "entcore/types/src/ts/calendar";
import {AvailabilityUtil} from "../utilities/availability.util";
import {Availabilities} from "../models/Availability";
import {Slot} from "../models/slot.model";

console.log("bookingsniplet");

interface IViewModel {
    openedBooking : Booking;
    loading: boolean;

    userStructures : Array<Structure>;
    resourceTypes : Array<ResourceType>;
    resources : Array<any>;
    slots : Array<Slot>;
    calendarEvent : any;
    numberOfAvailableItems : number;
    numberOfTotalItems : number;

    areStructuresValid() : boolean;
    onGetStructures(): Array<Structure>;
    autoSelectResourceType() : Promise<void>;
    autoSelectResource() :  Promise<void>;
    prepareBookingStartAndEnd() : void;
    availableResourceQuantity() : number;
    resourceQuantity() : number;
    calendarEventIsBeforeToday() : boolean;
    isResourceAvailable() : boolean;

}

class ViewModel implements IViewModel {
    private scope: any;
    loading: boolean;
    openedBooking: Booking;

    userStructures: Array<Structure>;
    resourceTypes : Array<ResourceType>;
    resources : Array<Resource>;
    slots : Array<Slot>;
    calendarEvent : any;
    numberOfAvailableItems : number;
    numberOfTotalItems : number;

    private bookingService;

    constructor(scope, bookingService: IBookingService) {
        this.loading = true;
        this.scope = scope;
        this.bookingService = bookingService;

        console.log("scope angular: ", scope);
        this.openedBooking = new Booking();

        this.calendarEvent = angular.element(document.getElementById("event-form")).scope().calendarEvent;

        this.userStructures = this.onGetStructures();
        this.openedBooking.structure = this.userStructures[0];
        console.log(this.openedBooking.structure);

        this.autoSelectResourceType();

        this.openedBooking.quantity = 1;

        this.scope.$apply();
        this.loading = false;
    }

    // async build() {
    //     try {
    //         await this.autoSelectResourceType();
    //         this.scope.$apply();
    //         this.loading = false;
    //     } catch (e) {
    //         console.error(e);
    //     }
    // };

    /**
     * Returns true if user has at least 1 structure and if there are the same number of structure ids and structure names
     */
    areStructuresValid() : boolean {
        return ((model.me.structures.length > 0)
            && (model.me.structures.length == model.me.structureNames.length));
    };

    /**
     * Gets the id + name if the structures the user is part of
     */
    onGetStructures(): Array<Structure> {
        let structuresWithNames : Array<Structure> = [];

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
     * Gets all resource types for one structure + selects the first one.
     * Calls the method that does the same for resources.
     */
    async autoSelectResourceType(): Promise<void> {
        this.bookingService.getResourceTypes(this.openedBooking.structure.id)
            .then(async (resourcesTypes: Array<ResourceType>) => {
                if (resourcesTypes && resourcesTypes.length > 0) {
                    resourcesTypes.forEach((type : ResourceType) => {
                        Behaviours.applicationsBehaviours.rbs.resourceRights(type);
                        console.log(type);
                    });
                    this.resourceTypes = resourcesTypes.filter((type : ResourceType) => type.myRights.contrib);
                    console.log("types", this.resourceTypes);
                    this.openedBooking.type = this.resourceTypes[0];

                    await this.autoSelectResource();
                } else {
                    this.openedBooking.type = undefined;
                    this.scope.$apply();
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
        try {
            this.resources = await this.bookingService.getResources(this.openedBooking.type.id);
            if (this.resources && this.resources.length > 0) {
                for (const resource of this.resources) {

                    //availabilities
                    resource.availabilities = new Availabilities();
                    resource.availabilities.sync(resource.id, false);
                    resource.unavailabilities = new Availabilities();
                    resource.unavailabilities.sync(resource.id, true);

                    // resource.bookings;
                    resource.bookings = new Bookings();
                    resource.bookings.all = await this.bookingService.getBookings(resource.id);
                }
                console.log("resources", this.resources);
                this.openedBooking.resource = this.resources[0];

                this.numberOfAvailableItems = this.availableResourceQuantity();
                this.numberOfTotalItems = this.resourceQuantity();
            } else {
                this.openedBooking.resource = undefined;
            }
        } catch (e) {
            console.error(e);
        }

        this.scope.$apply();
    }

    calendarEventIsBeforeToday() : boolean {
        console.log(this.calendarEvent);
        console.log(moment(this.calendarEvent.startMoment).isBefore(Date.now()));
        return (moment(this.calendarEvent.startMoment).isBefore(Date.now()));
    }

    /**
     * Returns true if the resource has a set quantity that is available
     */
    isResourceAvailable() : boolean {
        return (this.openedBooking.quantity && this.openedBooking.quantity <= this.availableResourceQuantity()
            && this.availableResourceQuantity() > 0);
    }

    /**
     * Returns the number of resources available for the event duration
     * @param booking the booking that must be checked
     */
    availableResourceQuantity(booking ? : Booking): number {
        let currentBooking = booking ? this.prepareBookingStartAndEnd(booking)
            : this.prepareBookingStartAndEnd();

        return AvailabilityUtil.getTimeslotQuantityAvailable(currentBooking, currentBooking.resource, null,
            currentBooking);
    }

    /**
     * Returns the number of resources set for the event duration
     * @param booking the booking that must be checked
     */
    resourceQuantity(booking ? : Booking) : number {
        let currentBooking = booking ? this.prepareBookingStartAndEnd(booking)
            : this.prepareBookingStartAndEnd();

        return AvailabilityUtil.getResourceQuantityByTimeslot(currentBooking, currentBooking.resource, null);
    }



    /**
     * Prepares the booking to get its availabilities
     * @param booking the booking that must be prepared
     */
    prepareBookingStartAndEnd(booking ? : Booking) : Booking {
        this.calendarEvent = angular.element(document.getElementById("event-form")).scope().calendarEvent;
        console.log(this.calendarEvent);

        let createdBooking : Booking = booking ? booking : this.openedBooking;

        createdBooking.startDate = this.calendarEvent.startMoment.toDate();
        createdBooking.endDate = this.calendarEvent.endMoment.toDate();

        //handle slots
        // if ($scope.selectedSlotStart) {
        // $scope.booking.startTime.set('hour', $scope.selectedSlotStart.startHour.split(':')[0]);
        //     $scope.booking.startTime.set('minute',$scope.selectedSlotStart.startHour.split(':')[1]);
        //     $scope.booking.end.set('hour', $scope.selectedSlotStart.endHour.split(':')[0]);
        //     $scope.booking.endTime.set('minute',$scope.selectedSlotStart.endHour.split(':')[1]);
        // }
        if(createdBooking.type.slotProfile) {
            let eventStartTime = moment(this.calendarEvent.startTime).format("HH:mm");
            let eventEndTime = moment(this.calendarEvent.endTime).format("HH:mm");
            console.log(eventStartTime);
            console.log(eventEndTime);
            let closestStartTime = undefined;
            let closestEndTime = undefined;
            this.bookingService.getSlots(createdBooking.type.slotProfile)
                .then(slotList => {
                    console.log(slotList);
                    slotList.forEach((slot : Slot) => {
                        if((!closestEndTime || slot.startHour > closestStartTime.startHour)
                            && slot.startHour <= eventStartTime) closestStartTime = slot;
                        if((!closestEndTime || slot.endHour < closestEndTime.endHour)
                            && slot.endHour >= eventEndTime) closestEndTime = slot;
                    });
                    console.log("slot start", closestStartTime);
                    console.log("slot end", closestEndTime);

                    // this.openedBooking.startDate;

                    // slots.reduce(
                    //     (closestSlot, currentSlot) => {
                    //         (currentSlot.startHour < closestSlot.startHour
                    //             && currentSlot.startHour >= eventStartTime) ? closestSlot = currentSlot
                    //     },
                    //     undefined //initialValue
                    // );
                });
        }

        return createdBooking;
    }


    // this.scope.$watch('calendarEvent', function(){
    //     console.log("watched");
    // });
}

export const calendarRbsBooking = {
    titre: 'calendar.booking',
    public: false,
    that: null,
    // controllerAs: vm,
    controller: {
        init: async function (): Promise<void> {
            // this.vm = new ViewModel(this, new BookingService());
            this.vm = new ViewModel(this, new BookingService());

//         this.source.asObservable().subscribe((calendarEvent) => {
//             this.vm.calendarEvent = calendarEvent;
//             this.vm.getAvailability();
//         })
        },

        // updateInfos: function (): void {
        //     this.$on("initBookingInfos", (event : IAngularEvent, calendarEvent) => {
        //         console.log("$on calevent", calendarEvent);
        //         this.vm.calendarEvent = calendarEvent;
        //         if(this.vm.openedBooking.resource) {
        //             try {
        //                 this.vm.getAvailability();
        //             } catch (e) {
        //                 console.error(e);
        //             }
        //         }
        //     });
        // },
    }

};
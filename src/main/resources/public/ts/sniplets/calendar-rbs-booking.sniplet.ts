import {bookingService, BookingService, IBookingService} from "../services";
import {RbsController} from "../controllers/controller";
import {model, idiom, angular, notify} from "entcore";
import {Booking, Slot} from "../models/booking.model";
import {Resource} from "../models/resource.model";
import {ResourceType, Structure} from "../models/resource-type.model";
import {lang, Moment} from "moment";
import {IAngularEvent} from "angular";
import {calendar} from "entcore/types/src/ts/calendar";
import {AvailabilityUtil} from "../utilities/availability.util";
import {Availabilities} from "../models/Availability";

console.log("bookingsniplet");

interface IViewModel {
    openedBooking : Booking;
    loading: boolean;

    userStructures : Array<Structure>;
    resourceTypes : Array<ResourceType>;
    resources : Array<any>;
    slots : Array<Slot>;
    calendarEvent : any;

    areStructuresValid() : boolean;
    onGetStructures(): Array<Structure>;
    autoSelectResourceType() : Promise<void>;
    autoSelectResource() :  Promise<void>;
    prepareBookingForAvailabilityCheck(calendarEvent : any, booking : Booking) : Booking;
    getAvailability();
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


    private bookingService;

    constructor(scope, bookingService: IBookingService) {
        this.loading = true;
        this.scope = scope;
        this.bookingService = bookingService;

        console.log("scope angular: ", scope);
        this.openedBooking = new Booking();

        let eventForm : any = angular.element(document.getElementById("event-form")).scope();
        this.calendarEvent = eventForm.calendarEvent;

        this.userStructures = this.onGetStructures();
        this.openedBooking.structure = this.userStructures[0];
        console.log(this.openedBooking.structure);

        this.autoSelectResourceType();

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
                console.log("types", resourcesTypes);
                this.resourceTypes = resourcesTypes;
                if (resourcesTypes && resourcesTypes.length > 0) {
                    this.openedBooking.type = resourcesTypes[0];

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
                this.resources.forEach((resource : Resource) => {
                    //availabilities
                    resource.availabilities = new Availabilities();
                    resource.availabilities.sync(resource.id, false);
                    resource.unavailabilities = new Availabilities();
                    resource.unavailabilities.sync(resource.id, true);

                    // resource.bookings;
                    this.findResourceBookings(resource);
                });
                console.log("resources", this.resources);
                this.openedBooking.resource = this.resources[0];

                await this.getAvailability();

                // if(this.calendarEvent) {
                //     await this.getAvailability();
                // }

            } else {
                this.openedBooking.resource = undefined;
            }
        } catch (e) {
            console.error(e);
        }

        this.scope.$apply();
    }

    private findResourceBookings(resource: Resource) {
        resource.bookings.sync(function () {
            if (routedBooking !== undefined || found) {
                return;
            }
            routedBooking = resource.bookings.find(function (booking) {
                return booking.id == bookingId;
            });
            if (routedBooking !== undefined) {
                $scope.bookings.pushAll(routedBooking.resource.bookings.all);
                var struct = $scope.structures.find(s => s.id === routedBooking.resource.type.structure.id);
                struct.expanded = true;
                routedBooking.resource.type.expanded = true;
                routedBooking.resource.selected = true;
                $scope.viewBooking(routedBooking);
                $scope.display.routed = undefined;
                model.recordedSelections.firstResourceType = undefined;
                updateCalendarSchedule(model.bookings.startPagingDate);
                found = true;
            }
            if (
                countTotalTypes == 0 &&
                countTotalResources == 0 &&
                typeof cb === 'function'
            ) {
                if (!found) {
                    // error
                    console.error('Booking not found (id: ' + bookingId + ')');
                    notify.error('rbs.route.booking.not.found');
                    $scope.display.routed = undefined;
                    $scope.initResources();
                }
                cb();
            }
        });
    }

    /**
     * Prepares the booking to get its availabilities
     * @param calendarEvent the event the booking is linked to
     * @param booking the booking
     */
    prepareBookingForAvailabilityCheck(calendarEvent, booking ? : Booking) : Booking {
            // end_date: Date;
            // iana: String;
            // start_date: Date;
        let createdBooking : Booking = booking ? booking : this.openedBooking;

        if(booking) {
            createdBooking.resource = booking.resource;
            createdBooking.type = booking.type;
            createdBooking.quantity = booking.quantity;
        }

        createdBooking.display = {
            state: 0,
            STATE_RESOURCE: 0,
            STATE_BOOKING: 1,
            STATE_PERIODIC: 2,
        };

        //start infos
        createdBooking.beginning = calendarEvent.startMoment;
        createdBooking.startMoment = calendarEvent.startMoment;
        createdBooking.startDate = calendarEvent.startMoment.toDate();
        createdBooking.startTime = calendarEvent.startMoment;

        //end infos
        createdBooking.end = calendarEvent.endMoment;
        createdBooking.endMoment = calendarEvent.endMoment;
        createdBooking.endDate = calendarEvent.endMoment.toDate();
        createdBooking.endTime = calendarEvent.endMoment;

        //handle slots
        // if ($scope.selectedSlotStart) {
        // $scope.booking.startTime.set('hour', $scope.selectedSlotStart.startHour.split(':')[0]);
        //     $scope.booking.startTime.set('minute',$scope.selectedSlotStart.startHour.split(':')[1]);
        //     $scope.booking.end.set('hour', $scope.selectedSlotStart.endHour.split(':')[0]);
        //     $scope.booking.endTime.set('minute',$scope.selectedSlotStart.endHour.split(':')[1]);
        // }

        return createdBooking;
    }

    getAvailability(): void {
        console.log("avaiability resource", this.openedBooking.resource);
        // await this.bookingService.getAvailability(this.openedBooking.resource.id);
        let bookingToProcess = this.prepareBookingForAvailabilityCheck(this.calendarEvent);
        console.log("booking for availability", bookingToProcess);
        console.log("Availability", AvailabilityUtil.getTimeslotQuantityAvailable(bookingToProcess, this.openedBooking.resource, null, bookingToProcess));
        try {
            console.log("availabilitysuccess");
            console.log(this.calendarEvent);
        } catch (e) {
            console.error(e);
        }

        this.scope.$apply();
    }

    // this.$watch('calendarEvent', function(){
    //     this.getAvailability()
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

            // try {
            //     await vm.build();
            //     if(this.source){
            //         this.source.asObservable().subscribe((calendarEvent) => {
            //             this.vm.calendarEvent = calendarEvent;
            //             this.vm.getAvailability();
            //
            //             console.log(this.vm.openedBooking);
            //             console.log("calendarevent", calendarEvent);
            //         })
            //     }
            //
            // } catch (e) {
            //     console.error(e);
            // }
            //
            // this.updateInfos();
            // // this.vm = new ViewModel(this, bookingEventService);
            // this.source;
            // console.log(this.source);
        },

        // updateInfos: function (): void {
        //     // console.log(this.source);
        //
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

        // destroy: async function (): Promise<void> {
        //     // this.vm = new ViewModel(this, new BookingService());
        //     const vm: ViewModel = new ViewModel(this, new BookingService());
        //     this.vm = vm;
        //     try {
        //         await vm.build();
        //         this.calendarEvent$
        //     } catch (e) {
        //         console.error(e);
        //     }
        //
        //     this.updateInfos();
        //     // this.vm = new ViewModel(this, bookingEventService);
        //     this.source;
        //     console.log(this.source);
        // },
    }

};
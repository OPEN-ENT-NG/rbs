import {bookingService, BookingService, IBookingService} from "../services";
import {RbsController} from "../controllers/controller";
import {model, idiom} from "entcore";
import {Booking, Resource, ResourceType, Structure} from "../models/booking-form.model";
import {lang, Moment} from "moment";
import {IAngularEvent} from "angular";
import {calendar} from "entcore/types/src/ts/calendar";
import {AvailabilityUtil} from "../utilities/availability.util";
import {Availabilities} from "../models/Availability";

console.log("bookingsniplet");

interface IViewModel {
    lang: typeof idiom;
    openedBooking : Booking;
    loading: boolean;

    userStructures : Array<Structure>;
    resourceTypes : Array<ResourceType>;
    resources : Array<any>;
    slotProfiles : any;
    slots : any;

    areStructuresValid() : boolean;
    onGetStructures(): Array<Structure>;
    autoSelectResourceType() : Promise<void>;
    autoSelectResource() :  Promise<void>;
    createBooking(calendarEvent : any, booking : Booking) : Booking;
}

class ViewModel implements IViewModel {
    private scope: any;
    loading: boolean;
    lang: typeof  idiom;
    openedBooking: Booking;

    userStructures: Array<Structure>;
    resourceTypes : Array<ResourceType>;
    resources : Array<any>;
    slotProfiles : any;
    slots : any;


    private bookingService;

    constructor(scope, bookingService: IBookingService) {
        this.loading = true;
        this.lang = idiom;
        this.scope = scope;
        this.bookingService = bookingService;
        console.log("scope angular: ", scope);
        this.openedBooking = new Booking();


        this.userStructures = this.onGetStructures();
        this.openedBooking.structure = this.userStructures[0];
        console.log(this.openedBooking.structure);

        // this.resourceTypes = this.bookingService.getResourceType();
        // console.log("resourceTypes", this.resourceTypes);
        // this.getResourceTypesAndResources();

    }


    async build() {
        this.resourceTypes = await this.bookingService.getResourceTypes(this.openedBooking.structure.id);
        if(this.resourceTypes.length){
            this.openedBooking.type = this.resourceTypes[0];
            console.log("resourceTypes", this.resourceTypes);
            // this.slotProfiles = await this.bookingService.getSlotProfiles(this.openedBooking.structure.id);
            // console.log("profiles",this.slotProfiles);
            // this.slots = await this.bookingService.getSlots();

            this.resources = await this.bookingService.getResources(this.openedBooking.type.id);
            // this.resources = await this.bookingService.getResources(1);
            if(this.resources.length){
                this.openedBooking.resource = this.resources[0];
                console.log("resources", this.resources);
            } else {
                this.loading = false;
            }
        } else {
            this.loading = false;
        }

        this.scope.$apply();

        this.loading = false;
    }


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

    async autoSelectResourceType(): Promise<void> {
        try {
            this.resourceTypes = await this.bookingService.getResourceTypes(this.openedBooking.structure.id);
            // this.slotProfiles = await this.bookingService.getSlotProfiles(this.openedBooking.structure.id);
            // console.log("profiles",this.slotProfiles);
            // this.slots = await this.bookingService.getSlots();
            if (this.resourceTypes.length) {
                this.openedBooking.type = this.resourceTypes[0];
                // this.scope.$apply();

                this.autoSelectResource();
            } else {
                this.openedBooking.type = undefined;
            }
        } catch (e) {
            console.error(e);
        }
    }

    async autoSelectResource(): Promise<void> {
        try {
            this.resources = await this.bookingService.getResources(this.openedBooking.type.id);
            console.log("resources", this.resources);
            if (this.resources.length) {
                this.resources.forEach((resource : Resource) => {
                    resource.availabilities = new Availabilities();
                    resource.unavailabilities = new Availabilities();
                });
                this.openedBooking.resource = this.resources[0];

            } else {
                this.openedBooking.resource = undefined;
            }
        } catch (e) {
            console.error(e);
        }
        console.log(this.scope.source);
        // this.scope.$apply();
    }

    createBooking(calendarEvent, booking ? : Booking) : Booking {
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

    async getAvailability(calendarEvent): Promise<void> {
        // await this.bookingService.getAvailability(this.openedBooking.resource.id);
        let bookingToProcess = this.createBooking(calendarEvent);
        console.log("cest parti");
        console.log(bookingToProcess);
        console.log("Availability",AvailabilityUtil.getTimeslotQuantityAvailable(bookingToProcess, this.openedBooking.resource, null, bookingToProcess));
        try {
            console.log("availabilitysuccess");
            console.log(calendarEvent);
        } catch (e) {
            console.error(e);
        }

        this.scope.$apply();
    }
}

export const calendarRbsBooking = {
    titre: 'calendar.booking',
    public: false,
    that: null,
    // controllerAs: vm,
    // controller: RbsController,
    controller: {
        init: async function (): Promise<void> {
            // this.vm = new ViewModel(this, new BookingService());
            const vm: ViewModel = new ViewModel(this, new BookingService());
            this.vm = vm;
            try {
                await vm.build();
            } catch (e) {
                console.error(e);
            }

            this.updateInfos();
            // this.vm = new ViewModel(this, bookingEventService);
            this.source;
            console.log(this.source);
        },

        updateInfos: function (): void {
            console.log(this.source);

            this.$on("initBookingInfos", (event : IAngularEvent, calendarEvent) => {
                console.log(calendarEvent);
                // try {
                    this.vm.getAvailability(calendarEvent);
                // } catch (e) {
                //     console.error(e);
                // }
            });
            // this.vm.$emit("hello i am a snipplet", () => {
            //     this.booking = "hello i am a booking";
            // });
        }

    }

};
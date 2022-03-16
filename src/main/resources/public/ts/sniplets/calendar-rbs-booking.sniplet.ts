import {BookingService, IBookingService} from "../services";
import {RbsController} from "../controllers/controller";
import {model, idiom} from "entcore";
import {Booking, ResourceType, Structure} from "../models/booking-form.model";



interface IViewModel {
    lang: typeof idiom;
    // test: string;
    openedBooking : Booking;
    loading: boolean;

    userStructures: Array<Structure>;
    resourceTypes : Array<ResourceType>;
    resources : Array<any>;

    // testMethod: void;
    areStructuresValid() : boolean;
    onGetStructures(): Array<Structure>;
    onTestInformation(name : any): void;
}

class ViewModel implements IViewModel {
    private scope: any;
    loading: boolean;
    lang: typeof  idiom;
    openedBooking: Booking;

    userStructures: Array<Structure>;
    resourceTypes : Array<ResourceType>;
    resources : Array<any>;


    private test: string;

    private bookingService;

    constructor(scope, bookingService: IBookingService) {
        this.loading = true;
        this.lang = idiom;
        this.test = "coucou je suis un sniplet";
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
            this.openedBooking.resourceType = this.resourceTypes[0];
            console.log("resourceTypes", this.resourceTypes);
        }

        this.resources = await this.bookingService.getResources(this.openedBooking.structure.id);
        if(this.resources.length){
            this.openedBooking.resource = this.resources[0];
            console.log("resources", this.resources);
        }
        this.scope.$apply();

        this.loading = false;
    }



    // private getResourceTypesAndResources() {
    //     // try {
    //         this.bookingService.getResourceType(this.openedBooking.structure.id)
    //             .then(res => {
    //                 this.resourceTypes = res;
    //                 this.openedBooking.resourceType = this.resourceTypes[0];
    //                 console.log("resourceTypes2", this.resourceTypes);
    //
    //                 // this.bookingService.
    //             })
    //             .catch(err => console.error(err));
    //     // } catch (e) {
    //     //     console.log(e);
    //     // }
    //     // console.log("resourceTypes", this.resourceTypes);
    // }

    onTestInformation(name : any): void {
        console.log("entered method", this.bookingService);
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
            await vm.build();
            // this.vm = new ViewModel(this, bookingEventService);
            this.source;
            console.log(this.source);
        },

        testMethod: function (): void {
            console.log(this.source);
            // this.vm.$emit("hello i am a snipplet", () => {
            //     this.booking = "hello i am a booking";
            // });
        }

    }

};
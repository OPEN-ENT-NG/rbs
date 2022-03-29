import {Availabilities} from "./Availability";
import {Bookings} from "./booking.model";
import {IResourceTypeResponse, ResourceType} from "./resource-type.model";

export interface IResourceResponse {
    id: number;
    color: String;
    created: String;
    description: String; //HTML
    icon: any;
    is_available: boolean;
    max_delay: number;
    min_delay: number;
    modified: String;
    name: String;
    owner: String;
    periodic_booking: boolean;
    quantity: number;
    shared: Array<any>;
    type_id: number;
    validation: boolean;
    visibility: boolean;
}

export class Resource {
    id: number;
    color: String;
    created: String;
    description: String; //HTML
    icon: any;
    isAvailable: boolean;
    maxDelay: number;
    minDelay: number;
    modified: String;
    name: String;
    owner: String;
    periodicBooking: boolean;
    quantity: number;
    shared: Array<any>;
    typeId: number;
    validation: boolean;
    visibility: boolean;

    availabilities: Availabilities;
    bookings: Bookings;
    // myRights: any;
    // notified: false
    // selected: true
    // type: ResourceType;
    unavailabilities: Availabilities;

    build(data: IResourceResponse): Resource {

        this.id = data.id;
        this.color = data.color;
        this.created = data.created;
        this.description = data.description;
        this.icon = data.icon;
        this.isAvailable = data.is_available;
        this.maxDelay = data.max_delay;
        this.minDelay = data.min_delay;
        this.modified = data.modified;
        this.name = data.name;
        this.owner = data.owner;
        this.periodicBooking = data.periodic_booking;
        this.quantity = data.quantity;
        this.shared = data.shared;
        this.typeId = data.type_id;
        this.validation = data.validation;
        this.visibility = data.visibility;

        return this;
    }
}

// export class Resource {
//     color: String;
//     created: String;
//     description: String; //HTML
//     icon: any;
//     id: number;
//     is_available: boolean;
//     max_delay: number;
//     min_delay: number;
//     modified: String;
//     name: String;
//     owner: String;
//     periodic_booking: boolean;
//     quantity: number;
//     shared: Array<any>;
//     type_id: number;
//     validation: boolean;
//     // visibility: null;
//
//     availabilities: Availabilities;
//     bookings: Bookings;
//     myRights: any;
//     notified: false
//     selected: true
//     type: ResourceType;
//     unavailabilities: Availabilities;
// }